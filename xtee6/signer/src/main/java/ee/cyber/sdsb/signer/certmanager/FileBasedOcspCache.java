package ee.cyber.sdsb.signer.certmanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.common.ocsp.OcspCache;

import static ee.cyber.sdsb.common.ErrorCodes.translateException;
import static ee.cyber.sdsb.common.SystemProperties.getOcspCachePath;

/**
 * OCSP cache that holds the OCSP responses on disk.
 */
@Slf4j
public class FileBasedOcspCache extends OcspCache {

    /** The OCSP response file extension. */
    private static final String OCSP_FILE_EXTENSION = ".ocsp";

    /**
     * Returns the OCSP response for the given certificate or null, if the
     * response is not available.
     * @param certHash for which to get the response
     * @return the OCSP response object or null, if no response is available
     */
    @Override
    protected OCSPResp getResponse(Object key, Date atDate) {
        OCSPResp response = null;
        if (cache.containsKey(key)) { // is the OCSP response in memory?
            response = super.getResponse(key, atDate);
            if (response != null) {
                return response;
            }
        }

        File file = getOcspResponseFile(getOcspCachePath(), key);
        try {
            response = loadResponseFromFileIfNotExpired(file, atDate);
        } catch (Exception e) {
            // Failed to load OCSP response from file
            throw translateException(e);
        }

        return response;
    }

    /**
     * Updates or saves the response in memory and on disk.
     * Any existing response for that certificate is overwritten.
     * @param certHash the certificate to which this response belongs
     * @param response the OCSP response
     */
    @Override
    public OCSPResp put(String key, OCSPResp value) {
        OCSPResp response = super.put(key, value);
        try {
            File file = getOcspResponseFile(getOcspCachePath(), key);
            saveResponseToFile(file, value);
        } catch (IOException e) {
            // Failed to save OCSP response to file
            throw translateException(e);
        }

        return response;
    }

    void reloadFromDisk() throws Exception {
        Path path = Paths.get(getOcspCachePath());

        try (DirectoryStream<Path> stream =
                Files.newDirectoryStream(path, this::isOcspFile)) {
            for (Path entry : stream) {
                loadResponseFromFileIfNotExpired(entry.toFile(), new Date());
            }
        }
    }

    boolean isOcspFile(Path p) {
        return Files.isRegularFile(p)
                && p.toString().endsWith(OCSP_FILE_EXTENSION);
    }

    void saveResponseToFile(File file, OCSPResp ocspResponse)
            throws IOException {
        createIntermediateDirectories(file);

        try (OutputStream os = new FileOutputStream(file)) {
            os.write(ocspResponse.getEncoded());
        }

        log.trace("Saved OCSP response to file '{}'", file);
    }

    OCSPResp loadResponseFromFileIfNotExpired(File file, Date atDate)
            throws Exception {
        OCSPResp response = loadResponseFromFile(file);
        if (response != null) {
            String key = getFileNameWithoutExtension(file);
            if (!isExpired(response, atDate)) {
                log.trace("Loaded OCSP response for cert hash {}", key);

                super.put(key, response); // store in memory
            } else {
                log.trace("Cached OCSP response for certificate '{}' "
                        + "has expired, deleting the file '{}'", key, file);
                delete(file);
                return null;
            }
        }

        return response;
    }

    OCSPResp loadResponseFromFile(File file) throws IOException {
        if (!file.exists()) {
            return null;
        }

        if (file.length() == 0L) {
            log.error("Cannot load OCSP response from file '{}': "
                    + "file is empty", file);
            delete(file);
            return null;
        }

        log.trace("Retrieving OCSP response from file '{}'", file);
        try (InputStream is = new FileInputStream(file)) {
            return new OCSPResp(IOUtils.toByteArray(is));
        }
    }

    private static void createIntermediateDirectories(File file)
            throws IOException {
        File path = file.getParentFile();
        if (!path.exists() && !path.mkdirs()) {
            throw new IOException("Could not create path " + path);
        }
    }

    private static File getOcspResponseFile(String path, Object certHash) {
        return new File(path, certHash + OCSP_FILE_EXTENSION);
    }

    private static void delete(File file) {
        try {
            Files.delete(file.toPath());
        } catch (Exception e) {
            log.warn("Failed to delete {}: {}", file, e.getMessage());
        }
    }

    private static String getFileNameWithoutExtension(File file) {
        return file.getName().split("[.]")[0];
    }
}
