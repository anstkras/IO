package io.client;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Arena {
    public final int width;
    public final int height;
    private final int[][] cells;
    private final int[][] trails;
    private final Map<Integer, Player> playersByColor = new HashMap<>();
    public final Collection<Player> players = Collections.unmodifiableCollection(playersByColor.values());

    public Arena(int width, int height) {
        this.width = width;
        this.height = height;
        cells = new int[height][width];
        trails = new int[height][width];
    }

    public void cell(int x, int y, int color) {
        cells[y][x] = color;
    }

    public void trail(int x, int y, int color) {
        trails[y][x] = color;
    }

    public int cell(int x, int y) {
        return cells[y][x];
    }

    public int trail(int x, int y) {
        return trails[y][x];
    }

    public void addPlayer(Player player) {
        playersByColor.put(player.color, player);
    }

    public void removePlayer(int color) {
        playersByColor.remove(color);
    }

    public Player playerByColor(int color) {
        return playersByColor.get(color);
    }
}
