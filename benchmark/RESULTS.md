# Impacto de rendimiento — Simple Voice Chat (rama 26.2)

Benchmark de micro-operaciones, versión **vieja** (git HEAD, commit `4575dc010`)
vs **nueva** (working tree con las optimizaciones).

- Método: best-of-rounds ns/op, OLD/NEW intercaladas por ronda (sin sesgo de orden
  ni térmico), single-thread, JDK 21.0.12, 9 rondas de ~20–200k iteraciones c/u.
- Todas las medidas pasan checks de corrección (salidas idénticas).
- Repositorio del harness: `benchmark/` (Java plano, `./run.ps1`).

## Resultados (estado final, tras revertir las regresiones)

| Benchmark | old ns/op | new ns/op | Speedup | Veredicto |
|---|---|---|---|---|
| `Secret.encrypt(1200B)` | ~2000 | ~510 | **3.9x** | Ganancia real |
| `Secret.decrypt(1200B)` | ~1650 | ~420 | **3.9x** | Ganancia real |
| `AudioUtils.bytesToShorts(1920B)` | ~173 | ~169 | ~1.0x | Revertido (ByteBuffer) |
| `AudioUtils.shortsToBytes(960)` | ~150 | ~154 | ~1.0x | Revertido (ByteBuffer) |
| `PacketFactory.create(0x1)` | ~25 | ~3.4 | **7.4x** | Ganancia real |
| `PacketFactory.create(mixed)` | ~29 | ~4.7 | **6.2x** | Ganancia real |
| `PacketFactory.getPacketType` | ~5.7 | ~3.1 | **1.8x** | Ganancia menor |
| `getStereoVolume` (por frame) | ~32 | ~32 | ~1.0x | Revertido (new float[]) |
| `getDistanceVolume` (por frame) | ~5.9 | ~5.8 | ~1.0x | Neutro (se mantiene) |
| `ServerPlayerManager.isInRange` | ~4.0 | ~4.0 | ~1.0x | Neutro (se mantiene) |

## Resultados intermedios (antes del revert)

Cuando el working tree aún contenía bit-shift + ThreadLocal scratch:

| Benchmark | old ns/op | new ns/op | Speedup |
|---|---|---|---|
| `AudioUtils.bytesToShorts` | ~155 | ~360 | **0.43x** (regresión) |
| `AudioUtils.shortsToBytes` | ~155 | ~365 | **0.43x** (regresión) |
| `getStereoVolume` | ~32 | ~42 | **0.77x** (regresión) |

## Análisis

### Ganancias reales (mantenidas)
- **Capa criptográfica (~4x):** pool de `Cipher` por hilo elimina la sincronización
  sobre el registro de proveedores de JCA en cada `Cipher.getInstance()`; el IV por
  contador por hilo elimina el candado global de `SecureRandom`. El bench
  single-thread *subestima* el beneficio real: con 4 hilos de `sendExecutor` +
  `ProcessThread`, la contención de candados es la mayor parte de la ganancia.
- **Factory de paquetes (6-7x):** `getDeclaredConstructor().newInstance()` por
  paquete recibido constituye la mayor parte del costo; el `switch` lo elimina.
- **`getPacketType` (~1.8x):** recorrido de `HashMap` por tipo → cadena `instanceof`.

Impacto aproximado en un servidor de 20 jugadores con broadcast completo
(todos se oyen, 50 paquetes/s c/u):
| Path | old | new | ahorro |
|---|---|---|---|
| Recepción (1000 paquetes/s: decrypt + create) | ~1.7 ms/s | ~0.4 ms/s | ~1.3 ms/s |
| Envío fan-out (19 000 paquetes/s: encrypt + getPacketType) | ~38 ms/s | ~9.8 ms/s | ~28 ms/s (~2.8% de un core) |

### Regresiones encontradas y revertidas
- **`AudioUtils.bytesToShorts`/`shortsToBytes` — regresión 2.3x.** El bucle manual
  por bits no se vectoriza (el narrowing int→short bloquea la auto-vectorización de
  C2), mientras que `ByteBuffer.wrap(...).asShortBuffer()` + `get()`/`putShort()`
  delegan a `Unsafe.copyMemory` (copia nativa bulk). Revertido al ByteBuffer
  original, con comentario documentando por qué.
- **`getStereoVolume` — regresión ~1.3x.** C2 ya eliminaba las alocaciones antiguas
  por escalar-replacement (los Vec3/Vec2/float[] no escapan del método), así que
  "quitar alocaciones" no aportaba y el `ThreadLocal.get()` del scratch costaba
  ~10 ns por frame. Revertido a `new float[]{...}`, que queda efectivamente
  libre de alocaciones en runtime igualmente.

### Neutros (se mantienen por higiene)
- **`getDistanceVolume` / `isInRange`:** `Vec3.distanceTo`/`distanceToSqr` de MC no
  alocan temporales (es aritmética directa), por lo que la versión manual mide
  idéntico. Los comentarios del código nuevo se ajustaron para no afirmar una
  "evitación de alocaciones" que en la implementación de MC no existía.

## Cómo reproducir

```
cd benchmark && ./run.ps1
```
Requiere JDK ≥ 17 (hardcodeado JDK 21 en `run.ps1`).