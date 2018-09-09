package io.client;

public final class HandshakeProtocol {
    private final IOClient client;
    public static final int SIGNATURE = 0xdf32a68c;

    public final ChannelContext.ReadLengthOp handshakeOp3;
    public final ChannelContext.ReadOp errOp;
    public final ChannelContext.WriteOp handshakeOp2;
    public final ChannelContext.WriteOp handshakeOp1;
    public final ChannelContext.WriteOp handshakeOp4;

    public HandshakeProtocol(IOClient client) {
        this.client = client;

        handshakeOp4 = new ChannelContext.WriteOp(ctx -> {
            client.gameProtocol.read.execute(ctx);
        });

        handshakeOp3 = new ChannelContext.ReadLengthOp((ctx, length) -> {
            client.arena = new Arena(ctx.readUnsignedByte(), ctx.readUnsignedByte());
            for (int y = 0; y < client.arena.height; y++) {
                for (int x = 0; x < client.arena.width; x++) {
                    client.arena.cell(x, y, ctx.readUnsignedByte());
                    client.arena.trail(x, y, ctx.readUnsignedByte());
                }
            }

            client.player = new Player("username", 2, ctx);
            client.arena.addPlayer(client.player);
            int playersCount = ctx.readInt();
            for (int i = 0; i < playersCount; i++) {
                client.arena.addPlayer(new Player(ctx));
            }

            ctx.writeByte((byte) 1);
            handshakeOp4.execute(ctx);
        });

        errOp = new ChannelContext.ReadOp(0, ctx -> {
            String message = ctx.readUTF16String(ctx.remaining() / 2);
            System.err.println(message); // TODO implement message in gui
            System.exit(1);
        });

        handshakeOp2 = ChannelContext.WriteOp.delegate(new ChannelContext.ReadOp(Short.BYTES, ctx -> {
            int errLength = ctx.readUnsignedShort();
            if (errLength != 0) {
                errOp.execute(ctx, errLength * 2);
                return;
            }

            handshakeOp3.execute(ctx);
        }));

        handshakeOp1 = ChannelContext.WriteOp.delegate(new ChannelContext.ReadOp(Integer.BYTES, ctx -> {
            int signature = ctx.readInt();
            if (signature != SIGNATURE) {
                ctx.close();
                return;
            }

            String name = "username"; // TODO implement username and color choice in gui
            ctx.writeShort((short) (1 + name.length() * 2));
            int color = 2;
            ctx.writeByte((byte) color);
            ctx.writeUTF16String(name);

            handshakeOp2.execute(ctx);
        }));
    }
}

