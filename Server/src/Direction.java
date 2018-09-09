package io.server;

public enum Direction {
    LEFT(-1, 0), RIGHT(1, 0), UP(0, -1), DOWN(0, 1);
    public static final Direction[] VALUES = values();
    public final int xDirection, yDirection;

    Direction(int x, int y) {
        this.xDirection = x;
        this.yDirection = y;
    }
}