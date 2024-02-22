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

import org.apache.commons.lang3.SystemUtils;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link FileWatcherRunner}.
 *
 * If the tests fail, you could be experiencing inode watch exhaustion.
 * This would show up as errors in the log such as:
 * <pre>
 * 11:49:24.920 [pool-1-thread-1] ERROR e.r.x.c.util.filewatcher.FileWatcher -
 * Stopped watching containing directory: /tmp/junit1947693984851435332 due to an error!
 * java.io.IOException: User limit of inotify watches reached
 * </pre>
 * To fix the problem, increase you OS limit of inode watches
 * (e.g. <code>max_user_watches</code>)
 */
public class FileWatcherRunnerTest {

    private FileWatcherRunner runner;
    private static final int TIMEOUT = 1000;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        //TODO Java does not have native macos filewatcher implementation, it does not work well with this test.
        Assume.assumeTrue("OS not supported.", SystemUtils.IS_OS_LINUX);

        runner = null;
    }

    /**
     * tear down tests
     */
    @After
    public void tearDown() {
        if (runner != null) {
            runner.stop();
        }
    }

    @Test
    public void shouldDetectFileOverride() throws IOException, InterruptedException {

        // setup
        File shouldChangeFile = temporaryFolder.newFile("testFile_tracked_1");
        File overridingFile = temporaryFolder.newFile("testFile_override");

        FileWatchListener shouldBeCalledListener = mock(FileWatchListener.class);

        final CountDownLatch startupWaitLatch = new CountDownLatch(1);

        this.runner = FileWatcherRunner.create()
                .watchForChangesIn(shouldChangeFile.toPath())
                .listenToCreate()
                .andOnChangeNotify(shouldBeCalledListener)
                // make sure we don't do file change tests before the watcher is running
                .andOnStartupNotify(startupWaitLatch::countDown)
                .buildAndStartWatcher();

        verify(shouldBeCalledListener, never()).fileModified();
        startupWaitLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);

        // exercise
        Files.move(overridingFile.toPath(), shouldChangeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // verify
        // if listener was not called, check if this was caused by "out of inode watchers" (see class javadoc)
        verify(shouldBeCalledListener, timeout(TIMEOUT)).fileModified();
    }

    @Test
    public void shouldDetectFileChange() throws IOException, InterruptedException {

        File shouldChangeFile = temporaryFolder.newFile("testFile_tracked_2");

        FileWatchListener shouldBeCalledListener = mock(FileWatchListener.class);

        final CountDownLatch startupWaitLatch = new CountDownLatch(1);

        this.runner = FileWatcherRunner.create()
                .watchForChangesIn(shouldChangeFile.toPath())
                .listenToModify()
                .andOnChangeNotify(shouldBeCalledListener)
                // make sure we don't do file change tests before the watcher is running
                .andOnStartupNotify(startupWaitLatch::countDown)
                .buildAndStartWatcher();

        verify(shouldBeCalledListener, never()).fileModified();
        startupWaitLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);

        // exercise
        final boolean changeSucceeded = shouldChangeFile.setLastModified(System.currentTimeMillis());
        assertTrue("test setup fail: could not change last modified time", changeSucceeded);

        // verify
        // if listener was not called, check if this was caused by "out of inode watchers" (see class javadoc)
        verify(shouldBeCalledListener, timeout(TIMEOUT)).fileModified();
    }

    @Test
    public void shouldDetectFileDelete() throws IOException, InterruptedException {

        // setup
        File shouldChangeFile = temporaryFolder.newFile("testFile_tracked_3");

        FileWatchListener shouldBeCalledListener = mock(FileWatchListener.class);

        final CountDownLatch startupWaitLatch = new CountDownLatch(1);

        this.runner = FileWatcherRunner.create()
                .watchForChangesIn(shouldChangeFile.toPath())
                .listenToDelete()
                .andOnChangeNotify(shouldBeCalledListener)
                // make sure we don't do file change tests before the watcher is running
                .andOnStartupNotify(startupWaitLatch::countDown)
                .buildAndStartWatcher();

        verify(shouldBeCalledListener, never()).fileModified();
        startupWaitLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);

        // exercise
        Files.delete(shouldChangeFile.toPath());

        // verify
        // if listener was not called, check if this was caused by "out of inode watchers" (see class javadoc)
        verify(shouldBeCalledListener, timeout(TIMEOUT)).fileModified();
    }
}
