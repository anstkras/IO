package io.server;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Player {
    public final int color;
    public final String username;
    public final Arena arena;
    public final ChannelContext ctx;

    private int cellX;
    private int fracX;
    private int cellY;
    private int fracY;
    private Direction direction;
    private Direction nextDirection = null;
    private boolean moving = false;
    private final Set<Short> cells = new HashSet<>();
    private final Set<Short> trails = new LinkedHashSet<>();

    public boolean writing = false;
    public boolean dead = false;
    public final Set<Player> updatedPlayers = Collections.newSetFromMap(new IdentityHashMap<>());
    public final List<Player> addedPlayers = new ArrayList<>();
    public final List<Player> removedPlayers = new ArrayList<>();
    public final BitSet updatedCells = new BitSet(Arena.HEIGHT * Arena.WIDTH);
    public final BitSet updatedTrails = new BitSet(Arena.HEIGHT * Arena.WIDTH);

    public Player(ChannelContext ctx, Arena arena, int color, String username) {
        this.ctx = ctx;
        this.arena = arena;
        this.color = color;
        this.username = username;
        // TODO check correctness
        direction = Direction.VALUES[IOServer.RANDOM.nextInt(Direction.VALUES.length)];
        cellX = (3 + IOServer.RANDOM.nextInt(Arena.WIDTH - 6)); // TODO delegate to arena
        cellY = (3 + IOServer.RANDOM.nextInt(Arena.HEIGHT - 6));
    }

    public void tick() {
        boolean isIntegral = fracX == 0 && fracY == 0;
        if (isIntegral && nextDirection != null) {
            direction = nextDirection;
            nextDirection = null;
        }

        fracX += direction.xDirection * 2;
        if (Math.abs(fracX) >= IOServer.CELL_SIZE) {
            fracX -= IOServer.CELL_SIZE * Integer.signum(fracX);
            cellX += direction.xDirection;
        }
        fracY += direction.yDirection * 2;
        if (Math.abs(fracY) >= IOServer.CELL_SIZE) {
            fracY -= IOServer.CELL_SIZE * Integer.signum(fracY);
            cellY += direction.yDirection;
        }

        int nextX = nextX();
        int nextY = nextY();
        if (nextX < 0 || nextY < 0 || nextX >= Arena.WIDTH || nextY >= Arena.HEIGHT) {
            dead = true;
            return;
        }

        int prevTrail = arena.trail(nextX, nextY);
        if (isIntegral && prevTrail == color) {
            dead = true;
            return;
        }
        if (prevTrail != color && prevTrail != 0) {
            // TODO Kill other player
        }
        if (arena.cell(nextX, nextY) == color) {
            if (!trails.isEmpty()) {
                boolean[][] visited = new boolean[Arena.HEIGHT][Arena.WIDTH];
                List<Short> path = new ArrayList<>(Arena.HEIGHT * Arena.WIDTH);
                for (short trail : trails) {
                    int x0 = Util.firstFromShort(trail);
                    int y0 = Util.secondFromShort(trail);
                    arena.trail(x0, y0, 0);
                    arena.cell(x0, y0, color);
                    cells.add(trail);

                    visited[y0][x0] = true;
                    for (Direction direction : Direction.VALUES) {
                        int x = x0 + direction.xDirection;
                        int y = y0 + direction.yDirection;
                        if (dfs(x, y, visited, path)) {
                            for (short cell : path) {
                                arena.cell(Util.firstFromShort(cell), Util.secondFromShort(cell), color);
                                cells.add(cell);
                            }
                        }
                        path.clear();
                    }
                }
                trails.clear();
            }
            return;
        }

        arena.trail(nextX, nextY, color);
        trails.add(Util.shortFromBytes(nextX, nextY));
    }

    public boolean dfs(int x0, int y0, boolean[][] visited, List<Short> path) {
        if (x0 < 0 || y0 < 0 || x0 >= Arena.WIDTH || y0 >= Arena.HEIGHT) {
            return false;
        }
        if (visited[y0][x0]) {
            return true;
        }

        visited[y0][x0] = true;
        if (arena.cell(x0, y0) == color || arena.trail(x0, y0) == color) {
            return true;
        }

        path.add(Util.shortFromBytes(x0, y0));
        for (Direction direction : Direction.VALUES) {
            int x = x0 + direction.xDirection;
            int y = y0 + direction.yDirection;
            if (!dfs(x, y, visited, path)) {
                return false;
            }
        }

        return true;
    }

    public int cellX() {
        return cellX;
    }

    public int cellY() {
        return cellY;
    }

    public Set<Short> cells() {
        return Collections.unmodifiableSet(cells);
    }

    public Set<Short> trail() {
        return Collections.unmodifiableSet(trails);
    }

    public int fracX() {
        return fracX;
    }

    public int fracY() {
        return fracY;
    }

    public int nextX() {
        return cellX + Integer.signum(fracX);
    }

    public int nextY() {
        return cellY + Integer.signum(fracY);
    }

    public int direction() {
        return direction.ordinal();
    }

    public void nextDirection(Direction direction) {
        nextDirection = (direction == this.direction) ? null : direction;
    }

    public boolean isMoving() {
        return moving;
    }

    public void setMoving() {
        moving = true;
    }

    public void writeUserName(ChannelContext ctx) {
        ctx.writeStringWithLength(username);
    }

    public void write(ChannelContext ctx) {
        ctx.writeByte((byte) color);
        ctx.writeByte((byte) cellX);
        ctx.writeByte((byte) fracX);
        ctx.writeByte((byte) cellY);
        ctx.writeByte((byte) fracY);
        ctx.writeByte((byte) direction());
    }

    public void cell(int x, int y) {
        arena.cell(x, y, color);
        cells.add(Util.shortFromBytes(x, y));
    }
}
