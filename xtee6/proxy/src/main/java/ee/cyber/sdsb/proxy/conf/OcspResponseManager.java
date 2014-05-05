package ee.cyber.sdsb.proxy.conf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.SystemProperties;


/**
 * This class is responsible for managing the OCSP responses for certificates.
 *
 * Certificates are identified by their SHA-1 fingerprint calculated over
 * the entire certificate.
 *
 * When an OCSP response is added to the manager, it is first cached in memory
 * (overwriting any existing response) and then attempted to be written to disk
 * (overwriting any existing response file).
 *
 * When an OCSP response is queried from the manager, first the cache is checked
 * for the response. If the response exists in the memory cache, it is returned.
 * If the response does not exist in the memory cache, the response will be
 * loaded from disk, if it exists and is cached in memory as well.
 */
public class OcspResponseManager {

    private static final Logger LOG =
            LoggerFactory.getLogger(OcspResponseManager.class);

    /** The OCSP response file extension. */
    private static final String OCSP_FILE_EXTENSION = ".ocsp";

    /** The minimum number of seconds the OCSP should still be fresh. */
    private static final int MIN_FRESHNESS_SECONDS = 60;

    /** Maps a certificate hash to an OCSP response. */
    private Map<String, OCSPResp> responseCache = new HashMap<>();

    /** Holds the directory name where OCSP response files are stored. */
    // TODO: Better default value?
    private String outputPath = SystemProperties.getOcspCachePath();

    /**
     * Sets the output path where the OCSP response files are stored.
     * If the path does not exist, it is created when when the next
     * OCSP response is saved to disk.
     * @param outputPath the path
     */
    public final void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    /**
     * Clears the cached OCSP responses from memory.
     */
    public final void clearCache() {
        responseCache.clear();
    }

    /**
     * Returns the OCSP response for the given certificate or null, if the
     * response is not available.
     * @param certHash for which to get the response
     * @return the OCSP response object or null, if no response is available
     */
    public synchronized OCSPResp getResponse(String certHash) throws Exception {
        if (responseCache.containsKey(certHash)) {
            LOG.debug("Retrieving OCSP response for '{}'", certHash);

            OCSPResp response = responseCache.get(certHash);
            if (isExpired(response)) {
                LOG.info("Cached OCSP response for certificate " +
                        "'{}' has expired", certHash);
                responseCache.remove(certHash);
                return null;
            }

            return response;
        }

        File file = getOcspResponseFile(outputPath, certHash);
        try {
            OCSPResp response = loadResponseFromFile(file);
            if (response != null) {
                if (!isExpired(response)) {
                    responseCache.put(certHash, response);
                    return response;
                }

                LOG.info("Cached OCSP response for certificate '{}' " +
                        "has expired, deleting the file '{}'", certHash, file);
                if (!file.delete()) { // Delete it, since it is too old now
                    LOG.warn("Failed to delete OCSP response '{}'", file);
                }
            }
        } catch (IOException e) {
            // Failed to load OCSP response from file
            throw ErrorCodes.translateException(e);
        }

        return null;
    }

    /**
     * Updates or saves the response in memory and on disk.
     * Any existing response for that certificate is overwritten.
     * @param certHash the certificate to which this response belongs
     * @param response the OCSP response
     */
    public synchronized void setResponse(String certHash, OCSPResp response)
            throws Exception {
        LOG.debug("Setting OCSP response for '{}'", certHash);

        responseCache.put(certHash, response);
        try {
            File file = getOcspResponseFile(outputPath, certHash);
            saveResponseToFile(file, response);
        } catch (IOException e) {
            // Failed to save OCSP response to file
            throw ErrorCodes.translateException(e);
        }
    }

    protected void saveResponseToFile(File file, OCSPResp ocspResponse)
            throws IOException {
        createIntermediateDirectories(file);

        try (OutputStream os = new FileOutputStream(file)) {
            os.write(ocspResponse.getEncoded());
        }

        LOG.debug("Successfully saved OCSP response to file '{}'", file);
    }

    protected OCSPResp loadResponseFromFile(File file) throws IOException {
        if (!file.exists()) {
            LOG.error("Cannot load OCSP response from file '{}': " +
                    "file does not exist", file);
            return null;
        }

        LOG.debug("Retrieving OCSP response from file '{}'", file);
        try (InputStream is = new FileInputStream(file)) {
            return new OCSPResp(IOUtils.toByteArray(is));
        }
    }

    private static boolean isExpired(OCSPResp response) throws Exception {
        BasicOCSPResp basicResp = (BasicOCSPResp) response.getResponseObject();
        SingleResp singleResp = basicResp.getResponses()[0];
        // Make sure the OCSP is still fresh for the next specified seconds.
        Date allowedThisUpdate =
                new DateTime().minusSeconds(MIN_FRESHNESS_SECONDS).toDate();
        return singleResp.getThisUpdate().before(allowedThisUpdate);
    }

    private static void createIntermediateDirectories(File file)
            throws IOException {
        File path = file.getParentFile();
        if (!path.exists() && !path.mkdirs()) {
            throw new IOException("Could not create path " + path);
        }
    }

    private static File getOcspResponseFile(String path, String certHash) {
        return new File(path, certHash + OCSP_FILE_EXTENSION);
    }
}
