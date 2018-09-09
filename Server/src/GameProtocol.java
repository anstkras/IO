package io.server;

public class GameProtocol {
    private final IOServer server;

    public GameProtocol(IOServer server) {
        this.server = server;
    }

    public void write(ChannelContext ctx) {
        ctx.startCountLength();

        ctx.writeInt(ctx.player.addedPlayers.size());
        for (Player player : ctx.player.addedPlayers) {
            player.writeUserName(ctx);
            player.write(ctx);
        }
        ctx.player.addedPlayers.clear();

        ctx.writeInt(ctx.player.updatedPlayers.size());
        for (Player player : ctx.player.updatedPlayers) {
            player.write(ctx);
        }
        ctx.player.updatedPlayers.clear();

        // the end
        byte[] cellsMask = ctx.player.updatedCells.toByteArray();
        ctx.writeBytesWithLength(cellsMask);
        int bit = 0;
        while ((bit = ctx.player.updatedCells.nextSetBit(bit)) != -1) {
            ctx.writeByte((byte) ctx.player.arena.cell(bit % Arena.WIDTH, bit / Arena.WIDTH));
            bit++;
        }
        ctx.player.updatedCells.clear();

        byte[] trailsMask = ctx.player.updatedTrails.toByteArray();
        ctx.writeBytesWithLength(trailsMask);
        bit = 0;
        while ((bit = ctx.player.updatedTrails.nextSetBit(bit)) != -1) {
            ctx.writeByte((byte) ctx.player.arena.trail(bit % Arena.WIDTH, bit / Arena.WIDTH));
            bit++;
        }
        ctx.player.updatedTrails.clear();

        ctx.writeInt(ctx.player.removedPlayers.size());
        for (Player player: ctx.player.removedPlayers) {
            ctx.writeByte((byte) player.color);
        }
        ctx.player.removedPlayers.clear();

        ctx.writeLength();
        ctx.player.writing = true;
        write.execute(ctx);
    }

    public final ChannelContext.WriteOp write = new ChannelContext.WriteOp((ctx) -> ctx.player.writing = false);
    public final ChannelContext.ReadOp read = new ChannelContext.ReadOp(2 * Short.BYTES + Byte.BYTES, ctx -> {
        ctx.player.nextDirection(Direction.VALUES[ctx.readUnsignedByte()]);
        this.read.execute(ctx);
    });
}
