package io.client;

public final class Util {
    private Util() {
    }

    public static int firstFromShort(short s) {
        return (s & 0xFF00) >> Byte.SIZE;
    }

    public static int secondFromShort(short s) {
        return s & 0xFF;
    }

    public static short shortFromBytes(int first, int second) {
        return (short) (((first & 0xFF) << Byte.SIZE) | (second & 0xFF));
    }
}
