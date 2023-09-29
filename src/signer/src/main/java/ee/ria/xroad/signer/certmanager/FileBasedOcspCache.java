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
package ee.ria.xroad.signer.certmanager;

import ee.ria.xroad.common.ocsp.OcspCache;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.OCSPResp;

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

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.SystemProperties.getOcspCachePath;

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
            log.warn("Failed to delete {}: {}", file, e);
        }
    }

    private static String getFileNameWithoutExtension(File file) {
        return file.getName().split("[.]")[0];
    }
}
