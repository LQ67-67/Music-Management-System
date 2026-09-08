package com.example.musiclibrary.util;

import javafx.application.Platform;
import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs database/IO work off the JavaFX application thread so slow queries
 * never freeze the UI. Callbacks always fire back on the FX thread.
 */
public final class Async {

    private static final Logger LOGGER = Logger.getLogger(Async.class.getName());

    private Async() {
    }

    /**
     * @param name      thread name for diagnostics
     * @param work      blocking work executed on a background thread
     * @param onSuccess receives the result on the FX thread
     * @param onError   receives the failure on the FX thread (after logging)
     */
    public static <T> void run(String name, Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            LOGGER.log(Level.SEVERE, "Background task failed: " + name, ex);
            onError.accept(ex);
        });
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}
