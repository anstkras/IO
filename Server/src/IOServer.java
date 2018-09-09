package io.server;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class IOServer implements Closeable, Runnable, CompletionHandler<AsynchronousSocketChannel, Void> {
    public static final long TICK_RATE = 20L;
    public static final int CELL_SIZE = 30;
    public static final Random RANDOM = new Random();

    // Instance
    public final Arena arena = new Arena(this);
    public final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> newThread(r, "AIO Thread"));
    private final AsynchronousServerSocketChannel server;
    public final HandshakeProtocol handshakeProtocol = new HandshakeProtocol(this);
    public final GameProtocol gameProtocol = new GameProtocol(this);
    private long ticksCount = 0L;

    public IOServer() throws IOException {
        AsynchronousChannelGroup group = AsynchronousChannelGroup.withThreadPool(scheduler);
        server = AsynchronousServerSocketChannel.open(group);
        server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
    }

    @Override
    public void run() {
        scheduler.execute(() -> System.out.println("IO server (c) (tm) (r) v1.0 by anstkras and sashok724"));
        scheduler.scheduleAtFixedRate(this::tick, 0L, TICK_RATE, TimeUnit.MILLISECONDS);
        try {
            server.bind(new InetSocketAddress(7247));
            server.accept(null, this);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        server.close();
    }

    @Override
    public void completed(AsynchronousSocketChannel result, Void attachment) {
        handshakeProtocol.handshakeOp1.execute(new ChannelContext(result));
        server.accept(null, this);
    }

    @Override
    public void failed(Throwable exc, Void attachment) {
        try {
            close();
        } catch (IOException e) {
            exc.addSuppressed(e);
        }
        exc.printStackTrace(); // TODO logging
    }

    private void tick() {
        long start = System.nanoTime();
        try {
            arena.tick();
        } catch (Throwable exc) {
            System.err.println("Unrecoverable exception occurred");
            exc.printStackTrace();
            scheduler.shutdown();
        } finally {
            long diff = System.nanoTime() - start;
            if (diff > 10_000_000L) {
                System.err.println("Tick took more than 10ms: " + diff + "ns");
            }
        }
    }

    public static void main(String... args) throws Throwable {
        IOServer server = new IOServer();
        server.run();
    }

    private static Thread newThread(Runnable r, String name) {
        Thread thread = new Thread(r, name);
        thread.setDaemon(false);
        return thread;
    }
}
