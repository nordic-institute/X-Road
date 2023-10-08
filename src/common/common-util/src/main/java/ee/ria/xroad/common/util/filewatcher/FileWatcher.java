/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.util.filewatcher;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static java.util.Objects.requireNonNull;

/**
 * A watcher that waits for a file to be modified or created and then notifies the attached {@link FileWatchListener}
 */
@Slf4j
public class FileWatcher implements Runnable {

    private final Path targetPath;
    private final Path watchedDirectory;
    private final FileWatchListener listener;
    private final Set<WatchEvent.Kind<Path>> events;
    private final FileWatcherStartupListener startupListener;

    private volatile boolean shouldWatch = true;

    /**
     * Indicate that the watcher should stop watching when it next wakes up due to a file event.
     * This could take some time so you might want to interrupt the thread. @see {@link FileWatcherRunner}
     */
    public void stop() {
        shouldWatch = false;
    }


    /**
     * @param targetPath the Path to watch
     * @param listener the listener to trigger on events.
     * @param events the events to listen to
     * @param startupListener the {@link FileWatcherStartupListener} to notify when the watcher is up and running.
     * @throws NullPointerException if any of the parameters are <code>null</code>
     */
    public FileWatcher(Path targetPath, FileWatchListener listener, Set<WatchEvent.Kind<Path>> events,
                       FileWatcherStartupListener startupListener) {

        this.targetPath = requireNonNull(targetPath);
        this.watchedDirectory = targetPath.getParent();
        this.listener = requireNonNull(listener);
        this.events = requireNonNull(events);
        this.startupListener = requireNonNull(startupListener);
    }

    private void watchForever() throws IOException {

        WatchService watchService = FileSystems.getDefault().newWatchService();
        watchedDirectory.register(watchService, events.toArray(new WatchEvent.Kind<?>[events.size()]));

        startupListener.startedUp();

        while (shouldWatch) {


            WatchKey watchKey;
            try {
                // wait until signaled
                watchKey = watchService.take();
            } catch (InterruptedException ex) {
                // got interrupted, probably shutting down
                log.debug("Watching file was interrupted. Ending watch.");
                Thread.currentThread().interrupt();
                return;
            }

            for (WatchEvent<?> genericEvent : watchKey.pollEvents()) {
                WatchEvent.Kind<?> kind = genericEvent.kind();

                // Might get an overflow event whatever we register to listen to, check for it.
                if (kind == OVERFLOW) {
                    continue;
                }

                // The events should always be of type WatchEvent<Path> when a path registers to a watch service
                @SuppressWarnings("unchecked") WatchEvent<Path> pathWatchEvent = (WatchEvent<Path>) genericEvent;
                Path relativeEventPath = pathWatchEvent.context();

                Path absoluteEventPath = watchedDirectory.resolve(relativeEventPath);

                // the watch service events are for the entire directory,
                // check if the event is for the file we are interested in
                if (targetPath.equals(absoluteEventPath)) {
                    log.debug("Detected a change in the watched file:{}. Event type was: {}",
                            absoluteEventPath, pathWatchEvent.kind());
                    listener.fileModified();
                }

            }

            final boolean valid = watchKey.reset();
            if (!valid) {
                log.info("Can no longer watch the directory. Maybe it was destroyed? Ending watch.");
                break;
            }
        }
    }

    /**
     * Start watching the file. This method blocks waiting for changes to the file. You probably want
     * to use {@link FileWatcherRunner}. Returns immediately if not able to watch the file due to an I/O error.
     */
    @Override
    public void run() {
        log.info("Starting to watch for modifications to file: {}", this.targetPath);
        try {
            watchForever();
        } catch (IOException e) {
            log.error("Stopped watching containing directory: {} due to an error!", this.watchedDirectory, e);
        }
        log.info("Stopped watching for modifications to file: {}", this.targetPath);
    }
}
