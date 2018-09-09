package io.client;

public final class Player {
    public final int color;
    public final String username;

    public boolean writing = false;
    private int cellX;
    private int fracX;
    private int cellY;
    private int fracY;
    private Direction direction;
    private Direction nextDirection = null;

    public Player(String username, int color, ChannelContext ctx) {
        this.username = username;
        this.color = color;
        update(ctx);
    }

    public Player(ChannelContext ctx) {
        this(ctx.readStringWithLength(), ctx.readUnsignedByte(), ctx);
    }

    public int fracX() {
        return fracX;
    }

    public int fracY() {
        return fracY;
    }

    public int cellX() {
        return cellX;
    }

    public int cellY() {
        return cellY;
    }

    public int displayX() {
        return cellX * IOClient.CELL_SIZE + fracX;
    }

    public int displayY() {
        return cellY * IOClient.CELL_SIZE + fracY;
    }

    public int nextX() {
        return cellX + Integer.signum(fracX);
    }

    public int nextY() {
        return cellY + Integer.signum(fracY);
    }

    public Direction direction() {
        return direction;
    }

    public int nextDirection() {
        return (nextDirection == null ? direction : nextDirection).ordinal();
    }

    public void nextDirection(Direction direction) {
        nextDirection = (direction == this.direction) ? null : direction;
    }

    public void update(ChannelContext ctx) {
        cellX = ctx.readUnsignedByte();
        fracX = ctx.readByte();
        cellY = ctx.readUnsignedByte();
        fracY = ctx.readByte();
        direction = Direction.VALUES[ctx.readUnsignedByte()];
    }
}