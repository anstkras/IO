package io.client;

import javafx.application.Application;

// this is the only way to fix the bug with uncontrolled framerate on Linux
// https://bugs.openjdk.java.net/browse/JDK-8181764

public class IOClientLauncher {
    public static void main(String... args) {
        System.setProperty("quantum.multithreaded", "false");
        Application.launch(IOClient.class, args);
    }
}
