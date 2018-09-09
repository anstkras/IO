package io.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

public final class IOClient extends Application {
    public static final int CELL_SIZE = 30;

    public final ChannelContext ctx;
    public final HandshakeProtocol handshakeProtocol = new HandshakeProtocol(this);
    public final GameProtocol gameProtocol = new GameProtocol(this);
    public Canvas canvas;
    public Player player;
    public Arena arena;

    public final Object lock = new Object();
    private final LinkedList<KeyCode> directionKeys = new LinkedList<>();
    private long startTime = System.currentTimeMillis();
    private int fpsCount = 0;

    public IOClient() throws IOException {
        ctx = new ChannelContext(this, AsynchronousSocketChannel.open());
        ctx.connect(new InetSocketAddress("localhost", 7247));
    }

    public static void main(String[] args) {
        System.setProperty("quantum.multithreaded", "false");

        Application.launch(IOClient.class, args);
    }

    public void draw(long now) {
        int w = (int) canvas.getWidth();
        int h = (int) canvas.getHeight();
        var g = canvas.getGraphicsContext2D();
        synchronized (lock) {
            if (arena == null || player == null) {
                g.setFill(Color.BLACK);
                g.fillRect(0, 0, w, h);
                g.setFill(Color.WHITE);
                g.setTextAlign(TextAlignment.CENTER);
                g.setFont(new Font("Sans Serif", 48));
                g.fillText("Loading...", w / 2, h / 2);
                return;
            }
            g.setFill(Color.rgb(223, 243, 247));
            g.fillRect(0, 0, w, h);

            Map<Short, Player> playersNextByCell = new HashMap<>(arena.players.size());
            for (Player player : arena.players) {
                playersNextByCell.put(Util.shortFromBytes(player.nextX(), player.nextY()), player);
            }

            // TODO Background and foreground background color 0xF0FAFC

            int x0 = w / 2 - player.displayX() - CELL_SIZE / 2;
            int y0 = h / 2 - player.displayY() - CELL_SIZE / 2;

            g.setFill(Color.rgb(128, 150, 158));
            g.fillRect(x0 - CELL_SIZE, y0 - CELL_SIZE, (arena.width + 2) * CELL_SIZE, CELL_SIZE);
            g.fillRect(x0 - CELL_SIZE, y0 - CELL_SIZE, CELL_SIZE, (arena.height + 2) * CELL_SIZE);
            g.fillRect(x0 + arena.width * CELL_SIZE, y0 - CELL_SIZE, CELL_SIZE, (arena.height + 2) * CELL_SIZE);
            g.fillRect(x0 - CELL_SIZE, y0 + arena.height * CELL_SIZE, (arena.width + 2) * CELL_SIZE, CELL_SIZE);

            int startY = Math.max(-y0 / CELL_SIZE, 0);
            int endY = Math.min((-y0 + h) / CELL_SIZE + 1, arena.height);
            int startX = Math.max(-x0 / CELL_SIZE, 0);
            int endX = Math.min((-x0 + w) / CELL_SIZE + 1, arena.width);
            for (int y = startY; y < endY; y++) {
                for (int x = startX; x < endX; x++) {
                    if (arena.cell(x, y) != 0) {
                        g.setFill(Color.rgb(12, 43, 212));
                        g.fillRect(x0 + x * CELL_SIZE, y0 + y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                        g.setFill(Color.rgb(6, 21, 106));
                        g.fillRect(x0 + x * CELL_SIZE, y0 + (y + 1) * CELL_SIZE, CELL_SIZE, 6);
                    }

                    int trail = arena.trail(x, y);
                    if (trail != 0) {
                        Player player = playersNextByCell.get(Util.shortFromBytes(x, y));
                        if (player == null || player.color != trail) {
                            g.setFill(Color.rgb(12, 43, 212, 0.4)); // TODO implement different colors
                            g.fillRect(x0 + x * CELL_SIZE, y0 + y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                        }
                    }
                }
            }

            g.setTextAlign(TextAlignment.CENTER);
            g.setFont(new Font("Sans Serif", 12.0D));
            for (Player player : arena.players) {
                int x = player.displayX();
                int cellX = player.cellX();
                int y = player.displayY();
                int cellY = player.cellY();
                if (cellY < startY || cellY >= endY || cellX < startX || cellX >= endX) {
                    continue;
                }
                g.setFill(Color.rgb(24, 86, 255));
                g.fillRect(x0 + x, y0 + y - 2, CELL_SIZE, CELL_SIZE);
                g.setFill(Color.rgb(6, 21, 106));
                g.fillRect(x0 + x, y0 + y + CELL_SIZE - 2, CELL_SIZE, 6);
                g.fillText(player.username, x0 + x + CELL_SIZE / 2, y0 + y - 4.0);
            }

            g.setFill(Color.RED);
            double diff = (System.currentTimeMillis() - startTime) / 1000.0D;
            fpsCount++;
            String fpsMessage = fpsCount + " frames, " + String.format("%.02f", diff) + "s, " + String.format("%.02f", fpsCount / Math.max(1.0D, diff)) + " fps";
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(fpsMessage, 2, 10);
        }
    }

    private Direction keyToDirection(KeyCode key) {
        switch (key) {
            case A:
            case LEFT:
                return Direction.LEFT;
            case W:
            case UP:
                return Direction.UP;
            case D:
            case RIGHT:
                return Direction.RIGHT;
            case S:
            case DOWN:
                return Direction.DOWN;
            default:
                return null;
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Platform.setImplicitExit(true);
        primaryStage.addEventHandler(KeyEvent.KEY_PRESSED, (key) -> {
            KeyCode code = key.getCode();
            Direction direction = keyToDirection(code);
            if (direction != null && !directionKeys.contains(code)) {
                directionKeys.add(code);
                synchronized (lock) {
                    player.nextDirection(direction);
                }
            }
        });
        primaryStage.addEventHandler(KeyEvent.KEY_RELEASED, (key) -> {
            KeyCode code = key.getCode();
            Direction direction = keyToDirection(code);
            if (direction != null && directionKeys.remove(code) && !directionKeys.isEmpty()) {
                synchronized (lock) {
                    player.nextDirection(keyToDirection(directionKeys.getLast()));
                }
            }
        });

        canvas = new Canvas() {
            @Override
            public boolean isResizable() {
                return true;
            }
        };
        Pane pane = new BorderPane(canvas);
        pane.setMinWidth(640);
        pane.setMinHeight(480);
        canvas.widthProperty().bind(pane.widthProperty());
        canvas.heightProperty().bind(pane.heightProperty());

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                draw(now);
            }
        }.start();

        primaryStage.setTitle("IO Client v1.0 by anstkras and sashok724 (c) (r) (tm)");
        primaryStage.setScene(new Scene(pane));
        primaryStage.show();
        primaryStage.setMinWidth(primaryStage.getWidth());
        primaryStage.setMinHeight(primaryStage.getHeight());
    }
}