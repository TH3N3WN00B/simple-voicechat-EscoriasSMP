package de.maxhenkel.voicechat.voice.server;

import de.maxhenkel.voicechat.Voicechat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

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

    public static void init(Plugin plugin) {
        // The spatial index is refreshed lazily from the online player list on demand.
        // Kept for API stability with upstream.
    }

    private volatile PlayerSpatialIndex index;
    private final ReentrantLock indexLock = new ReentrantLock();
    private volatile long lastIndexRefresh = 0L;

    private ServerPlayerManager() {
    }

    public static Collection<Player> getPlayersInRange(World level, Location pos, double range, @Nullable Predicate<Player> filter) {
        return INSTANCE.getPlayersInRangeInternal(level, pos, range, filter);
    }

    private Collection<Player> getPlayersInRangeInternal(World world, Location pos, double range, @Nullable Predicate<Player> filter) {
        if (!Voicechat.SERVER_CONFIG.threadedServerSupport.get()) {
            return getPlayersInRangeDirect(world, pos, range, filter);
        }
        if (range > MAX_INDEXED_RANGE) {
            return getPlayersInRangeFromPlayerList(world, pos, range, filter);
        }
        PlayerSpatialIndex spatialIndex = refreshIndex();
        List<Player> nearbyPlayers = new ArrayList<>();
        int radiusCells = (int) Math.floor(range / CELL_SIZE) + 1;
        int centerCellX = (int) Math.floor(pos.getX() / CELL_SIZE);
        int centerCellZ = (int) Math.floor(pos.getZ() / CELL_SIZE);
        for (int dx = -radiusCells; dx <= radiusCells; dx++) {
            for (int dz = -radiusCells; dz <= radiusCells; dz++) {
                List<UUID> cellPlayers = spatialIndex.getCell(cellKey(centerCellX + dx, centerCellZ + dz));
                if (cellPlayers == null) {
                    continue;
                }
                for (int i = 0; i < cellPlayers.size(); i++) {
                    Player player = Bukkit.getPlayer(cellPlayers.get(i));
                    if (player == null || !world.equals(player.getWorld())) {
                        continue;
                    }
                    if (isInRange(player.getLocation(), pos, range) && (filter == null || filter.test(player))) {
                        nearbyPlayers.add(player);
                    }
                }
            }
        }
        return nearbyPlayers;
    }

    private static Collection<Player> getPlayersInRangeFromPlayerList(World world, Location pos, double range, @Nullable Predicate<Player> filter) {
        List<Player> nearbyPlayers = new ArrayList<>();
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (!world.equals(player.getWorld())) {
                continue;
            }
            if (isInRange(player.getLocation(), pos, range) && (filter == null || filter.test(player))) {
                nearbyPlayers.add(player);
            }
        }
        return nearbyPlayers;
    }

    private PlayerSpatialIndex refreshIndex() {
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
            PlayerSpatialIndex newIndex = buildIndex();
            index = newIndex;
            return newIndex;
        } finally {
            indexLock.unlock();
        }
    }

    private static PlayerSpatialIndex buildIndex() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Map<Long, List<UUID>> cells = new HashMap<>();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            Location location = player.getLocation();
            int cellX = (int) Math.floor(location.getX() / CELL_SIZE);
            int cellZ = (int) Math.floor(location.getZ() / CELL_SIZE);
            cells.computeIfAbsent(cellKey(cellX, cellZ), k -> new ArrayList<>(4)).add(player.getUniqueId());
        }
        return new PlayerSpatialIndex(cells);
    }

    private static long cellKey(int cellX, int cellZ) {
        return (((long) cellX) << 32) | (cellZ & 0xFFFFFFFFL);
    }

    private static Collection<Player> getPlayersInRangeDirect(World world, Location pos, double range, @Nullable Predicate<Player> filter) {
        List<Player> nearbyPlayers = new ArrayList<>();
        List<Player> worldPlayers = world.getPlayers();
        for (int i = 0; i < worldPlayers.size(); i++) {
            Player player = worldPlayers.get(i);
            if (isInRange(player.getLocation(), pos, range) && (filter == null || filter.test(player))) {
                nearbyPlayers.add(player);
            }
        }
        return nearbyPlayers;
    }

    public static boolean isInRange(Location pos1, Location pos2, double range) {
        return (square(pos1.getX() - pos2.getX()) + square(pos1.getY() - pos2.getY()) + square(pos1.getZ() - pos2.getZ())) <= square(range);
    }

    private static double square(double value) {
        return value * value;
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