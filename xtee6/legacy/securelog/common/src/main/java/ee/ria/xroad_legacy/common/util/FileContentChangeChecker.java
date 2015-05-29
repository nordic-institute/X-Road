package ee.ria.xroad_legacy.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import static ee.ria.xroad_legacy.common.util.CryptoUtils.MD5_ID;
import static ee.ria.xroad_legacy.common.util.CryptoUtils.hexDigest;
import static org.apache.commons.io.IOUtils.toByteArray;

/**
 * A checksum based file modification checker.
 */
public class FileContentChangeChecker {

    private final String fileName;
    private final String checksum;

    public FileContentChangeChecker(String fileName) throws Exception {
        this.fileName = fileName;

        File file = getFile();
        this.checksum = calculateConfFileChecksum(file);
    }

    public boolean hasChanged() throws Exception {
        File file = getFile();
        return !calculateConfFileChecksum(file).equals(checksum);
    }

    protected File getFile() {
        return new File(fileName);
    }

    protected InputStream getInputStream(File file) throws Exception {
        return new FileInputStream(file);
    }

    private String calculateConfFileChecksum(File file) throws Exception {
        try (InputStream in = getInputStream(file)) {
            return hexDigest(MD5_ID, toByteArray(in));
        }
    }
}
