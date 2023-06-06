/**
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

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;


/**
 * A class that runs {@link FileWatcher} in a thread and stops is upon request.
 */
public final class FileWatcherRunner implements AutoCloseable {

    private final ExecutorService executor;
    private final FileWatcher watcher;


    public static Builder create() {
        return new Builder();
    }

    /**
     * A builder to use when constructing a {@link FileWatcherRunner} to set up a thread to watch for file changes.
     */
    public static final class Builder {
        private Path watchPath;
        private FileWatchListener watchListener;
        private Set<WatchEvent.Kind<Path>> eventTypes = new HashSet<>();
        private FileWatcherStartupListener startupListener;

        public Builder watchForChangesIn(Path file) {
            this.watchPath = requireNonNull(file);
            return this;
        }

        private Builder() {
        }

        /**
         * Specify the {@link FileWatchListener} to notify when the file changes.
         *
         * @param listener the listener to add.
         * @throws  NullPointerException if listener is null
         * @return this builder
         */
        public Builder andOnChangeNotify(FileWatchListener listener) {
            this.watchListener =  requireNonNull(listener);
            return this;
        }

        /**
         * Specify the {@link FileWatcherStartupListener} to notify when the watcher is up and running
         *
         * @param listener the start up listener to add.
         * @throws  NullPointerException if listener is null
         * @return this builder
         */
        public Builder andOnStartupNotify(FileWatcherStartupListener listener) {
            this.startupListener =  requireNonNull(listener);
            return this;
        }

        /**
         * Listen to create events. You have to listen to at least one type of event.
         *
         * @return this builder
         */
        public Builder listenToCreate() {
            eventTypes.add(StandardWatchEventKinds.ENTRY_CREATE);
            return this;
        }

        /**
         * Listen to file modifications. Note: new file creation might trigger both create and modify events.
         * You have to listen to at least one type of event.
         *
         * @return this builder
         */
        public Builder listenToModify() {
            eventTypes.add(StandardWatchEventKinds.ENTRY_MODIFY);
            return this;
        }

        /**
         * Listen to file deletion events. You have to listen to at least one type of event.
         *
         * @return this builder
         */
        public Builder listenToDelete() {
            eventTypes.add(StandardWatchEventKinds.ENTRY_DELETE);
            return this;
        }

        /**
         * Build the {@link FileWatcherRunner} and start a thread that watches it
         *
         * @return the already running watcher that can be stopped.
         * @throws IllegalArgumentException if the file to watch was not specified or the listener was not specified or
         * at least one type of event (create, modify, delete) is listened to.
         */
        public FileWatcherRunner buildAndStartWatcher() {
            requireNonNull(watchPath);

            this.startupListener = (this.startupListener != null) ? this.startupListener : () -> { };

            // AbstractPoller.register will throw if there are no events to listen to, so throw early
            checkArgument(eventTypes.size() > 0, "You have to listen to at least one type of event");
            FileWatcher watcher = new FileWatcher(this.watchPath, this.watchListener, eventTypes, this.startupListener);
            return new FileWatcherRunner(watcher).start();
        }
    }

    private FileWatcherRunner(FileWatcher watcher) {
        this.watcher = requireNonNull(watcher);
        this.executor = Executors.newSingleThreadExecutor();
    }

    private FileWatcherRunner start() {
        executor.execute(watcher);
        return this;
    }

    /**
     * Stop the running watcher
     */
    public void stop() {
        // flag the watcher to stop. this will only stop the watcher if it is not waiting for events
        watcher.stop();
        // use shutdown to interrupt the thread because it's probably waiting for events.
        executor.shutdownNow();
    }

    @Override
    public void close() throws Exception {
        stop();
    }

}
