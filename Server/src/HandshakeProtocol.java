package io.server;

import io.server.ChannelContext.ReadLengthOp;

import java.util.Collection;

public final class HandshakeProtocol {
    public static final int SIGNATURE = 0xdf32a68c;
    public final IOServer server;

    public final ChannelContext.ReadOp handshakeOp1;
    public final ChannelContext.WriteOp handshakeOp2;
    public final ChannelContext.WriteOp handshakeOp3;

    public HandshakeProtocol(IOServer server) {
        this.server = server;

        handshakeOp3 = ChannelContext.WriteOp.delegate(new ChannelContext.ReadOp(Byte.BYTES, (ctx) -> {
            ctx.readByte();
            ctx.player.setMoving();
            server.gameProtocol.read.execute(ctx);
        }));
        handshakeOp2 = ChannelContext.WriteOp.delegate(new ReadLengthOp((ctx, length) -> {
            if (length < 3 || length > 33 || (length & 1) == 0) {
                throw new IllegalArgumentException("Invalid length of color and username");
            }

            int color = ctx.readUnsignedByte();
            if (color == 0) {
                throw new IllegalArgumentException("Invalid color");
            }
            String username = ctx.readUTF16String((length - 1) >> 1);
            if (!isValidUsername(username)) {
                throw new IllegalArgumentException("Invalid username");
            }

            Player player = new Player(ctx, server.arena, color, username);
            switch (server.arena.addPlayer(player)) {
                case SUCCESS: {
                    ctx.writeShort((short) 0);
                    ctx.startCountLength();
                    ctx.writeByte((byte) Arena.WIDTH);
                    ctx.writeByte((byte) Arena.HEIGHT);
                    for (int y = 0; y < Arena.HEIGHT; y++) {
                        for (int x = 0; x < Arena.WIDTH; x++) {
                            ctx.writeByte((byte) server.arena.cell(x, y));
                            ctx.writeByte((byte) server.arena.trail(x, y));
                        }
                    }

                    ctx.writeByte((byte) player.cellX());
                    ctx.writeByte((byte) player.fracX());
                    ctx.writeByte((byte) player.cellY());
                    ctx.writeByte((byte) player.fracY());
                    ctx.writeByte((byte) player.direction());

                    Collection<Player> players = server.arena.players();
                    ctx.writeInt(players.size() - 1);
                    for (Player p : players) {
                        if (p == player) {
                            continue;
                        }
                        p.writeUserName(ctx);
                        p.write(ctx);
                    }
                    ctx.writeLength();

                    ctx.player = player;
                    handshakeOp3.execute(ctx);
                    break;
                }
                case DUPLICATE_USERNAME: {
                    ctx.writeStringWithLength("Choose another username, please");
                    ChannelContext.WriteOp.AND_CLOSE.execute(ctx);
                    break;
                }
                case DUPLICATE_COLOR: {
                    ctx.writeStringWithLength("Choose another color, please");
                    ChannelContext.WriteOp.AND_CLOSE.execute(ctx);
                    break;
                }
            }
        }));
        handshakeOp1 = new ChannelContext.ReadOp(Integer.BYTES, (ctx) -> {
            int signature = ctx.readInt();
            if (signature != SIGNATURE) {
                ctx.close();
                return;
            }
            ctx.writeInt(SIGNATURE);
            handshakeOp2.execute(ctx);
        });
    }

    public static boolean isValidUsername(String username) { // TODO extend allowed symbols
        for (int i = 0; i < username.length(); i++) {
            char ch = username.charAt(i);
            if (ch >= 127 || ch <= 32) {
                return false;
            }
        }
        return true;
    }
}
