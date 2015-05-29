package ee.ria.xroad.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import static ee.ria.xroad.common.util.CryptoUtils.MD5_ID;
import static ee.ria.xroad.common.util.CryptoUtils.hexDigest;
import static org.apache.commons.io.IOUtils.toByteArray;

/**
 * A checksum based file modification checker.
 */
public class FileContentChangeChecker {

    private final String fileName;

    private String checksum;
    private String previousChecksum;

    /**
     * Calculates hash of the input file.
     * @param fileName the input file
     * @throws Exception if an error occurs
     */
    public FileContentChangeChecker(String fileName) throws Exception {
        this.fileName = fileName;

        File file = getFile();
        this.checksum = calculateConfFileChecksum(file);
    }

    /**
     * @return true, if the file has changed
     * @throws Exception if an error occurs
     */
    public boolean hasChanged() throws Exception {
        File file = getFile();

        previousChecksum = checksum;
        checksum = calculateConfFileChecksum(file);
        return !checksum.equals(previousChecksum);
    }

    protected File getFile() {
        return new File(fileName);
    }

    protected InputStream getInputStream(File file) throws Exception {
        return new FileInputStream(file);
    }

    protected String calculateConfFileChecksum(File file) throws Exception {
        try (InputStream in = getInputStream(file)) {
            return hexDigest(MD5_ID, toByteArray(in));
        }
    }
}
