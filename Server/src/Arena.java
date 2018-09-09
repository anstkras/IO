package io.server;

import java.io.IOException;
import java.util.*;

public final class Arena {
    public static final int WIDTH = 90;
    public static final int HEIGHT = 70;

    private final IOServer server;
    private final int[][] cells = new int[HEIGHT][WIDTH];
    private final int[][] trails = new int[HEIGHT][WIDTH];
    private final Map<Integer, Player> playersByColor = new HashMap<>();
    private final Map<String, Player> playersByUsername = new HashMap<>();
    private final Collection<Player> players = Collections.unmodifiableCollection(playersByColor.values());

    public Arena(IOServer server) {
        this.server = server;
    }

    public AddPlayerResult addPlayer(Player player) {
        if (playersByColor.containsKey(player.color)) {
            return AddPlayerResult.DUPLICATE_COLOR;
        }
        if (playersByUsername.containsKey(player.username)) {
            return AddPlayerResult.DUPLICATE_USERNAME;
        }

        for (Player p : players) {
            p.addedPlayers.add(player);
        }
        playersByColor.put(player.color, player);
        playersByUsername.put(player.username, player);
        for (int xOffset = - 1; xOffset <= 1; xOffset++) {
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                int x = player.cellX() + xOffset;
                int y = player.cellY() + yOffset;
                player.cell(x, y);
            }
        }
        return AddPlayerResult.SUCCESS;
    }

    public void removePlayer(Player player) {
        playersByColor.remove(player.color);
        playersByUsername.remove(player.username);
        for (Player p : players) {
            p.removedPlayers.add(player);
        }

        for (short cell : player.cells()) {
            cells[Util.secondFromShort(cell)][Util.firstFromShort(cell)] = 0;
        }
        for (short trail : player.trail()) {
            trails[Util.secondFromShort(trail)][Util.firstFromShort(trail)] = 0;
        }
    }

    public Collection<Player> players() {
        return players;
    }

    public enum AddPlayerResult {
        SUCCESS,
        DUPLICATE_USERNAME,
        DUPLICATE_COLOR
    }

    public int cell(int x, int y) {
        return cells[y][x];
    }

    public void cell(int x, int y, int color) {
        if (cells[y][x] != color) {
            cells[y][x] = color;
            for (Player p : players) {
                p.updatedCells.set(y * WIDTH + x);
            }
        }
    }

    public int trail(int x, int y) {
        return trails[y][x];
    }

    public void trail(int x, int y, int color) {
        if (trails[y][x] != color) {
            trails[y][x] = color;
            for (Player p : players) {
                p.updatedTrails.set(y * WIDTH + x);
            }
        }
    }

    public void tick() {
        List<Player> deadPlayers = new ArrayList<>(players.size());
        for (Player player : players) {
            if (!player.isMoving()) {
                continue;
            }

            player.tick();
            if (player.dead) {
                deadPlayers.add(player);
                continue;
            }

            for (Player p : players) {
                p.updatedPlayers.add(player);
            }
            if (!player.writing) {
                server.gameProtocol.write(player.ctx);
            }
        }
        for (Player player : deadPlayers) {
            try {
                player.ctx.close();
            } catch (IOException e) {
                e.printStackTrace(); // TODO logging
            }
        }
    }
}
