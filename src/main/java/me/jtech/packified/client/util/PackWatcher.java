package me.jtech.packified.client.util;

import me.jtech.packified.client.windows.LogWindow;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class PackWatcher implements Runnable {
    private final WatchService watchService;
    private final Map<WatchKey, Path> keys = new HashMap<>();
    public final Path rootPath;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile boolean running = true;
    private volatile boolean invalidated = false;

    public PackWatcher(Path rootPath) throws IOException {
        this.rootPath = rootPath;
        this.watchService = FileSystems.getDefault().newWatchService();
        registerAll(rootPath);
    }

    public void start() {
        executor.submit(this);
    }

    public void stop() throws IOException {
        running = false;
        watchService.close();
        executor.shutdownNow();
    }

    public boolean isInvalidated() {
        if (invalidated) {
            LogWindow.addDebugInfo("PackWatcher: Detected changes in the pack directory.");
        }
        return invalidated;
    }

    public void resetInvalidated() {
        invalidated = false;
    }

    private void registerAll(final Path start) throws IOException {
        Files.walk(start)
                .filter(Files::isDirectory)
                .forEach(this::register);
    }

    private void register(Path dir) {
        try {
            WatchKey key = dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            keys.put(key, dir);
        } catch (IOException ignored) {}
    }

    @Override
    public void run() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.poll(500, TimeUnit.MILLISECONDS); // poll every 0.5s
            } catch (InterruptedException | ClosedWatchServiceException e) {
                break;
            }

            if (key == null) continue;

            Path dir = keys.get(key);
            if (dir == null) continue;

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path name = ev.context();
                Path child = dir.resolve(name);

                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    if (Files.isDirectory(child)) {
                        try {
                            registerAll(child);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                invalidated = true;
            }

            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);
            }
        }
    }
}

