package ee.ria.xroad.asyncdb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.concurrent.Callable;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import static org.apache.commons.lang3.StringUtils.join;

/**
 * Utility methods specific to asynchronous messages database.
 */
public final class AsyncDBUtil {
    private AsyncDBUtil() {
    }

    /**
     * Returns all the directories under current directory that are not hidden.
     * @param file - directory under which to look for subdirectories
     * @return - list of directory names
     */
    public static String[] getDirectoriesList(File file) {
        return file.list((dir, name) ->
                new File(dir, name).isDirectory()
                && !name.startsWith("."));
    }

    /**
     * Performs task under a lock which applies both to different operations and
     * different threads.
     *
     * @param task - action to be performed
     * @param lockFilePath - path to file used for process locking
     * @param lockable - object to be synchronized between threads
     * @param <T> - type of result
     * @return - object returned as a result of operation
     * @throws Exception - when locked operation fails or cannot be performed
     */
    public static <T> T performLocked(Callable<T> task, String lockFilePath,
            Object lockable) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(lockFilePath, "rw");
                FileOutputStream fos = new FileOutputStream(raf.getFD())) {
            synchronized (lockable) {
                FileLock lock = fos.getChannel().lock();
                try {
                    return task.call();
                } finally {
                    lock.release();
                }
            }
        }
    }

    /**
     * Returns path to file that locks all the message queues.
     *
     * @return - absolute path to global lock file
     */
    public static String getGlobalLockFilePath() {
        return makePath(SystemProperties.getAsyncDBPath(),
                AsyncDB.GLOBAL_LOCK_FILE_NAME);
    }

    /**
     * Creates file path from separate parts
     *
     * @param parts - parts to create file path from
     * @return - file path created
     */
    public static String makePath(String ...parts) {
        return join(parts, File.separator);
    }

    /**
     * Makes {@link java.io.File} object from path parts
     *
     * @param parts - path parts
     * @return - file consisting of path parts
     */
    public static File makeFile(String ...parts) {
        return new File(makePath(parts));
    }

    /**
     * Returns queue name in hex format for specific provider.
     *
     * @param provider - provider to get queue name for
     * @return - queue name in hex format
     * @throws Exception - when queue name cannot be created
     */
    public static String getQueueName(ClientId provider) throws Exception {
        return CryptoUtils.hexDigest(CryptoUtils.MD5_ID,
                provider.toShortString());
    }
}
