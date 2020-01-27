/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.util.CryptoUtils;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.repository.InternalTlsCertificateRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.niis.xroad.restapi.service.InternalTlsCertificateService.KEY_CERT_GENERATION_FAILED;

/**
 * test InternalTlsCertificateService
 */
public class InternalTlsCertificateServiceTest {

    public static final String MOCK_SUCCESS_SCRIPT = "src/test/resources/script/success.sh";
    public static final String MOCK_FAIL_SCRIPT = "src/test/resources/script/fail.sh";
    public static final String NON_EXISTING_SCRIPT = "/path/to/non/existing/script.sh";
    public static final String SCRIPT_ARGS = "args";

    private InternalTlsCertificateService internalTlsCertificateService = new InternalTlsCertificateService(
            new InternalTlsCertificateRepository(),
            new ExternalProcessRunner(),
            MOCK_SUCCESS_SCRIPT,
            SCRIPT_ARGS);

    @Before
    public void setup() throws Exception {
        internalTlsCertificateService.setInternalTlsCertificateRepository(new InternalTlsCertificateRepository() {
            @Override
            public X509Certificate getInternalTlsCertificate() {
                try (InputStream stream = getClass().getClassLoader().getResourceAsStream("internal.crt")) {
                    return CryptoUtils.readCertificate(stream);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    @Test
    public void getExportedInternalTlsCertificate() throws Exception {
        byte[] certFileData = internalTlsCertificateService.exportInternalTlsCertificate();
        assertTrue(certFileData.length > 100);

        // compare certs.tar.gz exported from old UI to the one from service
        InputStream exportedTarStream = getClass().getClassLoader()
                .getResourceAsStream("exported-example-certs.tar.gz");
        Map<String, byte[]> exportedExampleFiles = extractTarGZ(IOUtils.toByteArray(exportedTarStream));
        Map<String, byte[]> filesFromService = extractTarGZ(
                internalTlsCertificateService.exportInternalTlsCertificate());
        assertEquals(exportedExampleFiles.size(), filesFromService.size());
        // check that we have same file names, and file bytes
        for (String fileName : exportedExampleFiles.keySet()) {
            assertTrue(filesFromService.containsKey(fileName));
            assertTrue(Arrays.equals(exportedExampleFiles.get(fileName), filesFromService.get(fileName)));
        }
    }

    /**
     * read a tar.gz, return each file in a map: filename -> bytes
     * @param tarBytes
     * @return
     */
    private Map<String, byte[]> extractTarGZ(byte[] tarBytes) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(tarBytes);
             GzipCompressorInputStream gzipIn = new GzipCompressorInputStream(byteArrayInputStream);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {
            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry) tarIn.getNextEntry()) != null) {
                if (entry.isFile()) {
                    if (entry.getSize() > Integer.MAX_VALUE) {
                        throw new IllegalStateException("can work with so large files: " + entry.getSize());
                    }
                    byte data[] = new byte[(int) entry.getSize()];
                    IOUtils.readFully(tarIn, data);
                    files.put(entry.getName(), data);
                }
            }
        }
        return files;
    }

    @Test
    public void generateInternalTlsKeyAndCertificate() {
        try {
            internalTlsCertificateService.generateInternalTlsKeyAndCertificate();
        } catch (Exception e) {
            fail("should not throw exceptions");
        }
    }

    @Test
    public void generateInternalTlsKeyAndCertificateFail() {
        internalTlsCertificateService = new InternalTlsCertificateService(
                new InternalTlsCertificateRepository(),
                new ExternalProcessRunner(),
                MOCK_FAIL_SCRIPT,
                SCRIPT_ARGS);
        try {
            internalTlsCertificateService.generateInternalTlsKeyAndCertificate();
        } catch (DeviationAwareRuntimeException e) {
            assertEquals(KEY_CERT_GENERATION_FAILED, e.getErrorDeviation().getCode());
            assertEquals("FAIL", e.getErrorDeviation().getMetadata().get(0)); // has the output of the script
        }
    }

    @Test
    public void generateInternalTlsKeyAndCertificateNotExecutable() {
        internalTlsCertificateService = new InternalTlsCertificateService(
                new InternalTlsCertificateRepository(),
                new ExternalProcessRunner(),
                NON_EXISTING_SCRIPT,
                SCRIPT_ARGS);
        try {
            internalTlsCertificateService.generateInternalTlsKeyAndCertificate();
        } catch (DeviationAwareRuntimeException e) {
            assertEquals(KEY_CERT_GENERATION_FAILED, e.getErrorDeviation().getCode());
            assertNotNull(e.getErrorDeviation().getMetadata()); // includes message from IOException
        }
    }
}
