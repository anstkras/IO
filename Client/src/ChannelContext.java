package io.client;

import sun.misc.Unsafe;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.TimeUnit;

public final class ChannelContext implements Closeable {
    private static final int BUFFER_SIZE = 32768;
    private static final Unsafe UNSAFE;
    private static final Lookup LOOKUP;
    private static final MethodHandle MH_INVOKER_INVOKE;

    public final IOClient client;
    private final AsynchronousSocketChannel channel;
    private final ByteBuffer readBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private final ByteBuffer writeBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

    private int readPosition = 0, readRequested = 0, readLength = 0;
    private int writeStartPos = -1;

    public ChannelContext(IOClient client, AsynchronousSocketChannel channel) {
        this.client = client;
        this.channel = channel;
        readBuffer.limit(0);
    }

    @Override
    public void close() throws IOException {
        channel.close();
        UNSAFE.invokeCleaner(readBuffer);
        UNSAFE.invokeCleaner(writeBuffer);
        // TODO implement game over screen
    }

    public void connect(SocketAddress address) {
        channel.connect(address, null, new CompletionHandler<Void, Object>() {
            @Override
            public void completed(Void result, Object attachment) {
                writeInt(HandshakeProtocol.SIGNATURE);
                client.handshakeProtocol.handshakeOp1.execute(ChannelContext.this);
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                try {
                    close();
                } catch (IOException e) {
                    exc.addSuppressed(e);
                }
                exc.printStackTrace();
            }
        });
    }

    public int readInt() {
        return readBuffer.getInt();
    }

    public boolean readBoolean() {
        int value = readUnsignedByte();
        switch (value) {
            case 0:
                return false;
            case 1:
                return true;
            default:
                throw new IllegalArgumentException("Unknown boolean value: " + value);
        }
    }

    public char readChar() {
        return readBuffer.getChar();
    }

    public long readLong() {
        return readBuffer.getLong();
    }

    public float readFloat() {
        return readBuffer.getFloat();
    }

    public double readDouble() {
        return readBuffer.getDouble();
    }

    public byte readByte() {
        return readBuffer.get();
    }

    public byte[] readBytes(int length) {
        byte[] bytes = new byte[length];
        readBuffer.get(bytes);
        return bytes;
    }

    public byte[] readBytesWithLength() {
        return readBytes(readUnsignedShort());
    }

    public short readShort() {
        return readBuffer.getShort();
    }

    public int readUnsignedByte() {
        return Byte.toUnsignedInt(readByte());
    }

    public int readUnsignedShort() {
        return Short.toUnsignedInt(readShort());
    }

    public String readUTF16String(int length) { // length is the symbols count
        char[] string = new char[length];
        for (int i = 0; i < length; i++) {
            string[i] = readChar();
        }
        return new String(string);
    }

    public String readStringWithLength() {
        return readUTF16String(readUnsignedShort());
    }

    public void writeByte(byte b) {
        writeBuffer.put(b);
    }

    public void writeChar(char ch) {
        writeBuffer.putChar(ch);
    }

    public void writeInt(int i) {
        writeBuffer.putInt(i);
    }

    public void writeShort(short s) {
        writeBuffer.putShort(s);
    }

    public void writeLong(long l) {
        writeBuffer.putLong(l);
    }

    public void writeBoolean(boolean b) {
        writeByte((byte) (b ? 1 : 0));
    }

    public void writeFloat(float f) {
        writeBuffer.putFloat(f);
    }

    public void writeDouble(double d) {
        writeBuffer.putDouble(d);
    }

    public void writeBytes(byte[] bytes) {
        writeBuffer.put(bytes);
    }

    public void writeBytesWithLength(byte[] bytes) {
        writeShort((short) bytes.length);
        writeBytes(bytes);
    }

    public void writeUTF16String(String string) {
        for (int i = 0; i < string.length(); i++) {
            writeChar(string.charAt(i));
        }
    }

    public void writeStringWithLength(String string) {
        writeShort((short) string.length());
        writeUTF16String(string);
    }

    public void startCountLength() {
        if (writeStartPos != -1) {
            throw new IllegalStateException("Calling startCountLength() more than one time in a row");
        }
        writeStartPos = writeBuffer.position();
        writeBuffer.position(writeStartPos + 2);
    }

    // use only after startCountLength
    public void writeLength() {
        if (writeStartPos == -1) {
            throw new IllegalStateException("Calling writeLength() is allowed only after calling startCountLength()");
        }
        int endPos = writeBuffer.position();
        writeBuffer.position(writeStartPos)
                .putShort((short) (endPos - writeStartPos))
                .position(endPos);
        writeStartPos = -1;
    }

    static {
        try {
            UNSAFE = (Unsafe) MethodHandles.privateLookupIn(Unsafe.class, MethodHandles.lookup()).findStaticVarHandle(Unsafe.class,
                    "theUnsafe", Unsafe.class).get();
            Field implLookupField = Lookup.class.getDeclaredField("IMPL_LOOKUP");
            LOOKUP = (Lookup) UNSAFE.getObject(UNSAFE.staticFieldBase(implLookupField), UNSAFE.staticFieldOffset(implLookupField));
            MH_INVOKER_INVOKE = LOOKUP.findStatic(Class.forName("sun.nio.ch.Invoker"), "invoke",
                    MethodType.methodType(void.class, AsynchronousChannel.class, CompletionHandler.class, Object.class,
                            Object.class, Throwable.class));
        } catch (Throwable exc) {
            throw new InternalError(exc);
        }
    }

    private static <V, A> void invoke(AsynchronousChannel channel, CompletionHandler<V, A> handler, A attachment, V value,
                                      Throwable exc) throws Throwable {
        MH_INVOKER_INVOKE.invokeExact(channel, handler, attachment, value, exc);
    }

    public int remaining() {
        return readBuffer.remaining();
    }

    public static final class ReadOp implements Op, CompletionHandler<Integer, ChannelContext> {
        private final int minimal;
        private final Callback callback;

        public ReadOp(int minimal, Callback callback) {
            this.minimal = minimal;
            this.callback = callback;
        }

        @Override
        public void execute(ChannelContext ctx) {
            execute(ctx, 0);
        }

        @Override
        public void completed(Integer result, ChannelContext ctx) {
            if (result < 0) {
                int neededBytes = ctx.readRequested - (ctx.readBuffer.position() - ctx.readPosition);
                failed(new EOFException("Still need " + neededBytes + " bytes to read"), ctx);
                return;
            }
            if (ctx.readBuffer.position() - ctx.readPosition < ctx.readRequested) {
                ctx.channel.read(ctx.readBuffer, 5L, TimeUnit.SECONDS, ctx, this);
                return;
            }
            ctx.readBuffer.limit(ctx.readBuffer.position()).position(ctx.readPosition);

            // Execute callback
            ctx.readPosition = 0;
            ctx.readRequested = 0;
            try {
                callback.call(ctx);
            } catch (Throwable exc) {
                failed(exc, ctx);
            }
        }

        @Override
        public void failed(Throwable exc, ChannelContext ctx) {
            ctx.readPosition = 0;
            ctx.readRequested = 0;
            try {
                ctx.close();
            } catch (Throwable e) {
                exc.addSuppressed(e);
            }
            exc.printStackTrace(); // TODO logging
        }

        public void execute(ChannelContext ctx, int minimal) {
            minimal = Integer.max(minimal, this.minimal);
            if (ctx.readBuffer.remaining() >= minimal) {
                ctx.readPosition = ctx.readBuffer.position();
                ctx.readRequested = 0;
                ctx.readBuffer.position(ctx.readBuffer.limit());
                try {
                    invoke(ctx.channel, this, ctx, 0, null);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                return;
            }

            if (ctx.readBuffer.capacity() - ctx.readBuffer.position() >= minimal) {
                ctx.readPosition = ctx.readBuffer.position();
                ctx.readBuffer.position(ctx.readBuffer.limit()).limit(ctx.readBuffer.capacity());
            } else if (minimal <= ctx.readBuffer.capacity()) {
                ctx.readBuffer.compact();
                ctx.readPosition = 0;
            } else {
                throw new IllegalArgumentException("Data size is larger than buffer size: " + minimal);
            }
            ctx.readRequested = minimal;
            ctx.channel.read(ctx.readBuffer, 5L, TimeUnit.SECONDS, ctx, this);
        }
    }

    public static final class WriteOp implements Op, CompletionHandler<Integer, ChannelContext> {
        public static final WriteOp AND_CLOSE = new WriteOp(ChannelContext::close);
        private final Callback callback;

        public WriteOp(Callback callback) {
            this.callback = callback;
        }

        @Override
        public void execute(ChannelContext ctx) {
            ctx.writeBuffer.flip();

            // Verify has write buffer
            if (!ctx.writeBuffer.hasRemaining()) {
                try {
                    invoke(ctx.channel, this, ctx, 0, null);
                } catch (Throwable exc) {
                    throw new AssertionError("Impossible", exc);
                }
                return;
            }

            // Write buffer to channel
            ctx.channel.write(ctx.writeBuffer, 5L, TimeUnit.SECONDS, ctx, this);
        }

        @Override
        public void completed(Integer result, ChannelContext ctx) {
            // Verify is fully written
            if (ctx.writeBuffer.hasRemaining()) {
                ctx.channel.write(ctx.writeBuffer, 5L, TimeUnit.SECONDS, ctx, this);
                return;
            }

            // Execute callback
            ctx.writeBuffer.clear();
            try {
                callback.call(ctx);
            } catch (Throwable exc) {
                failed(exc, ctx);
            }
        }

        @Override
        public void failed(Throwable exc, ChannelContext ctx) {
            try {
                ctx.close();
            } catch (Throwable e) {
                exc.addSuppressed(e);
            }
            exc.printStackTrace(); // TODO logging
        }

        public static WriteOp delegate(Op delegate) {
            return new WriteOp(delegate::execute);
        }
    }

    public static final class ReadLengthOp implements Op, Callback {
        private final Callback callback;
        private final ReadOp readOp = new ReadOp(0, this);

        public ReadLengthOp(Callback callback) {
            this.callback = callback;
        }

        @Override
        public void call(ChannelContext ctx) throws Exception { // TODO do private
            if (ctx.readLength <= 0) {
                int length = ctx.readUnsignedShort();
                if (length <= 0) {
                    callback.call(ctx, length);
                } else {
                    ctx.readLength = length;
                    readOp.execute(ctx, length);
                }
                return;
            }

            int length = ctx.readLength;
            ctx.readLength = 0;
            callback.call(ctx, length);
        }

        @Override
        public void execute(ChannelContext ctx) {
            readOp.execute(ctx, Short.BYTES);
        }

        @FunctionalInterface
        public interface Callback {
            void call(ChannelContext ctx, int length) throws Exception;
        }
    }

    @FunctionalInterface
    public interface Callback {
        void call(ChannelContext ctx) throws Exception;
    }

    @FunctionalInterface
    public interface Op {
        void execute(ChannelContext ctx);
    }
}
