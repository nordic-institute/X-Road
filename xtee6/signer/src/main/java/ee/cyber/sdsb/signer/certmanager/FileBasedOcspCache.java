package ee.cyber.sdsb.signer.certmanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.common.ocsp.OcspCache;

import static ee.cyber.sdsb.common.ErrorCodes.translateException;
import static ee.cyber.sdsb.common.SystemProperties.getOcspCachePath;
import static ee.cyber.sdsb.common.ocsp.OcspVerifier.isExpired;

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
    public OCSPResp get(Object key) {
        OCSPResp response = null;
        if (containsKey(key)) { // is the OCSP response in memory?
            response = super.get(key);
            if (response != null) {
                return response;
            }
        }

        File file = getOcspResponseFile(getOcspCachePath(), key);
        try {
            response = loadResponseFromFile(file);
            if (response != null) {
                if (!isExpired(response)) {
                    super.put(key.toString(), response); // store in memory
                    return response;
                }

                log.trace("Cached OCSP response for certificate '{}' " +
                        "has expired, deleting the file '{}'", key, file);
                delete(file);
            }
        } catch (Exception e) {
            // Failed to load OCSP response from file
            throw translateException(e);
        }

        return null;
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

    void saveResponseToFile(File file, OCSPResp ocspResponse)
            throws IOException {
        createIntermediateDirectories(file);

        try (OutputStream os = new FileOutputStream(file)) {
            os.write(ocspResponse.getEncoded());
        }

        log.trace("Saved OCSP response to file '{}'", file);
    }

    OCSPResp loadResponseFromFile(File file) throws IOException {
        if (!file.exists()) {
            return null;
        }

        if (file.length() == 0L) {
            log.error("Cannot load OCSP response from file '{}': " +
                    "file is empty", file);
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
}
