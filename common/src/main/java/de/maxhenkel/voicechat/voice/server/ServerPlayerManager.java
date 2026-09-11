package de.maxhenkel.voicechat.voice.server;

import de.maxhenkel.voicechat.Voicechat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

public class ServerPlayerManager {

    private static final long INDEX_REFRESH_INTERVAL_MS = 50L;
    private static final double CELL_SIZE = 48D;
    private static final double MAX_INDEXED_RANGE = CELL_SIZE * 4D;

    public static final ServerPlayerManager INSTANCE = new ServerPlayerManager();

    public static void init() {
        // The spatial index is refreshed lazily from the player list on demand.
        // Kept for API stability with upstream.
    }

    private volatile PlayerSpatialIndex index;
    private final ReentrantLock indexLock = new ReentrantLock();
    private volatile long lastIndexRefresh = 0L;

    private ServerPlayerManager() {
    }

    public static Collection<ServerPlayer> getPlayersInRange(ServerLevel level, Vec3 pos, double range, @Nullable Predicate<ServerPlayer> filter) {
        return INSTANCE.getPlayersInRangeInternal(level, pos, range, filter);
    }

    private Collection<ServerPlayer> getPlayersInRangeInternal(ServerLevel level, Vec3 pos, double range, @Nullable Predicate<ServerPlayer> filter) {
        if (!Voicechat.SERVER_CONFIG.threadedServerSupport.get()) {
            return getPlayersInRangeDirect(level, pos, range, filter);
        }
        if (range > MAX_INDEXED_RANGE) {
            return getPlayersInRangeFromPlayerList(level, pos, range, filter);
        }
        PlayerSpatialIndex spatialIndex = refreshIndex(level);
        List<ServerPlayer> nearbyPlayers = new ArrayList<>();
        PlayerList playerList = level.getServer().getPlayerList();
        int radiusCells = (int) Math.floor(range / CELL_SIZE) + 1;
        int centerCellX = (int) Math.floor(pos.x / CELL_SIZE);
        int centerCellZ = (int) Math.floor(pos.z / CELL_SIZE);
        for (int dx = -radiusCells; dx <= radiusCells; dx++) {
            for (int dz = -radiusCells; dz <= radiusCells; dz++) {
                List<UUID> cellPlayers = spatialIndex.getCell(cellKey(centerCellX + dx, centerCellZ + dz));
                if (cellPlayers == null) {
                    continue;
                }
                for (int i = 0; i < cellPlayers.size(); i++) {
                    ServerPlayer player = playerList.getPlayer(cellPlayers.get(i));
                    if (player == null || player.level() != level) {
                        continue;
                    }
                    if (isInRange(player.position(), pos, range) && (filter == null || filter.test(player))) {
                        nearbyPlayers.add(player);
                    }
                }
            }
        }
        return nearbyPlayers;
    }

    private static Collection<ServerPlayer> getPlayersInRangeFromPlayerList(ServerLevel level, Vec3 pos, double range, @Nullable Predicate<ServerPlayer> filter) {
        List<ServerPlayer> nearbyPlayers = new ArrayList<>();
        PlayerList playerList = level.getServer().getPlayerList();
        List<ServerPlayer> players = new ArrayList<>(playerList.getPlayers());
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (player.level() != level) {
                continue;
            }
            if (isInRange(player.position(), pos, range) && (filter == null || filter.test(player))) {
                nearbyPlayers.add(player);
            }
        }
        return nearbyPlayers;
    }

    private PlayerSpatialIndex refreshIndex(ServerLevel level) {
        PlayerSpatialIndex current = index;
        long now = System.currentTimeMillis();
        if (current != null && now - lastIndexRefresh < INDEX_REFRESH_INTERVAL_MS) {
            return current;
        }
        if (!indexLock.tryLock()) {
            return current;
        }
        try {
            now = System.currentTimeMillis();
            if (now - lastIndexRefresh < INDEX_REFRESH_INTERVAL_MS) {
                return index;
            }
            lastIndexRefresh = now;
            PlayerSpatialIndex newIndex = buildIndex(level);
            index = newIndex;
            return newIndex;
        } finally {
            indexLock.unlock();
        }
    }

    private static PlayerSpatialIndex buildIndex(ServerLevel level) {
        List<ServerPlayer> players = new ArrayList<>(level.getServer().getPlayerList().getPlayers());
        Map<Long, List<UUID>> cells = new HashMap<>();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            Vec3 position = player.position();
            int cellX = (int) Math.floor(position.x / CELL_SIZE);
            int cellZ = (int) Math.floor(position.z / CELL_SIZE);
            cells.computeIfAbsent(cellKey(cellX, cellZ), k -> new ArrayList<>(4)).add(player.getUUID());
        }
        return new PlayerSpatialIndex(cells);
    }

    private static long cellKey(int cellX, int cellZ) {
        return (((long) cellX) << 32) | (cellZ & 0xFFFFFFFFL);
    }

    private static Collection<ServerPlayer> getPlayersInRangeDirect(ServerLevel level, Vec3 pos, double range, @Nullable Predicate<ServerPlayer> filter) {
        List<ServerPlayer> nearbyPlayers = new ArrayList<>();
        List<ServerPlayer> levelPlayers = level.players();
        for (int i = 0; i < levelPlayers.size(); i++) {
            ServerPlayer player = levelPlayers.get(i);
            if (isInRange(player.position(), pos, range) && (filter == null || filter.test(player))) {
                nearbyPlayers.add(player);
            }
        }
        return nearbyPlayers;
    }

    public static boolean isInRange(Vec3 pos1, Vec3 pos2, double range) {
        // Manual squared euclidean distance. Functionally identical to
        // Vec3.distanceToSqr (which is already allocation-free in MC), kept inline
        // to save the virtual call on a per-candidate inner loop.
        double dx = pos1.x - pos2.x;
        double dy = pos1.y - pos2.y;
        double dz = pos1.z - pos2.z;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    private static final class PlayerSpatialIndex {

        private final Map<Long, List<UUID>> cells;

        private PlayerSpatialIndex(Map<Long, List<UUID>> cells) {
            this.cells = cells;
        }

        @Nullable
        private List<UUID> getCell(long key) {
            return cells.get(key);
        }
    }

}