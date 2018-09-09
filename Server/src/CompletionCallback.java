package io.server;

import java.nio.channels.CompletionHandler;

public final class CompletionCallback<V, A> implements CompletionHandler<V, A> {
    private final Callback<V, A> callback;

    public CompletionCallback(Callback<V, A> callback) {
        this.callback = callback;
    }

    @Override
    public void completed(V result, A attachment) {
        callback.invoke(result, attachment, null);
    }

    @Override
    public void failed(Throwable exc, A attachment) {
        callback.invoke(null, attachment, exc);
    }

    @FunctionalInterface
    public interface Callback<V, A> {
        void invoke(V result, A attachment, Throwable exc);
    }
}
