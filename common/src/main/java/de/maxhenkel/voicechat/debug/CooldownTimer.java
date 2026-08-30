package de.maxhenkel.voicechat.debug;

import java.util.concurrent.ConcurrentHashMap;

public class CooldownTimer {

    private static final int MAX_ENTRIES = 10_000;
    private static ConcurrentHashMap<String, Long> cooldowns;

    static {
        cooldowns = new ConcurrentHashMap<>();
    }

    public static void run(String id, long time, Runnable runnable) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.putIfAbsent(id, now);
        if (last == null) {
            runnable.run();
            prune(now);
            return;
        }
        if (now - last > time) {
            cooldowns.put(id, now);
            runnable.run();
            prune(now);
        }
    }

    private static void prune(long now) {
        if (cooldowns.size() > MAX_ENTRIES) {
            cooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 100_000L);
        }
    }

    public static void run(String id, Runnable runnable) {
        run(id, 10_000L, runnable);
    }

}
