package io.client;

import java.util.BitSet;

public class GameProtocol {
    public final ChannelContext.WriteOp write;
    public final ChannelContext.ReadLengthOp read;
    private final GameProtocol gameProtocol = this;

    public GameProtocol(IOClient client) {
        write = new ChannelContext.WriteOp(ctx -> client.player.writing = false);
        read = new ChannelContext.ReadLengthOp((ctx, length) -> {
            Player player = client.player;
            synchronized (client.lock) {
                int addedPlayersSize = ctx.readInt();
                for (int i = 0; i < addedPlayersSize; i++) {
                    client.arena.addPlayer(new Player(ctx));
                }

                int updatedPlayerSize = ctx.readInt();
                for (int i = 0; i < updatedPlayerSize; i++) {
                    int color = ctx.readUnsignedByte();
                    client.arena.playerByColor(color).update(ctx);
                }

                BitSet updatedCells = BitSet.valueOf(ctx.readBytesWithLength());
                int bit = 0;
                int width = client.arena.width;
                while ((bit = updatedCells.nextSetBit(bit)) != -1) {
                    int color = ctx.readUnsignedByte();
                    client.arena.cell(bit % width, bit / width, color);
                    bit++;
                }

                BitSet trails = BitSet.valueOf(ctx.readBytesWithLength());
                bit = 0;
                while ((bit = trails.nextSetBit(bit)) != -1) {
                    int color = ctx.readUnsignedByte();
                    client.arena.trail(bit % width, bit / width, color);
                    bit++;
                }

                int removedPlayersSize = ctx.readInt();
                for (int i = 0; i < removedPlayersSize; i++) {
                    int color = ctx.readUnsignedByte();
                    client.arena.removePlayer(color);
                }
            }
            gameProtocol.read.execute(ctx);
            if (!player.writing) {
                ctx.writeByte((byte) player.nextDirection());
                player.writing = true;
                write.execute(ctx);
            }
        });
    }
}
