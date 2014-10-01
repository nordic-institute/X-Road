package ee.cyber.sdsb.asyncdb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.StringUtils;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.util.CryptoUtils;

public class AsyncDBUtil {

    public static String[] getDirectoriesList(File file) {
        return file.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                // Allowing only non-temporary directories
                return new File(dir, name).isDirectory()
                        && !name.startsWith(".");
            }
        });
    }

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

    public static String getGlobalLockFilePath() {
        return makePath(SystemProperties.getAsyncDBPath(),
                AsyncDB.GLOBAL_LOCK_FILE_NAME);
    }

    public static String makePath(String ...parts) {
        return StringUtils.join(parts, File.separator);
    }

    public static File makeFile(String ...parts) {
        return new File(makePath(parts));
    }

    public static String getQueueName(ClientId provider) throws Exception {
        return CryptoUtils.hexDigest(CryptoUtils.MD5_ID,
                provider.toShortString());
    }
}
