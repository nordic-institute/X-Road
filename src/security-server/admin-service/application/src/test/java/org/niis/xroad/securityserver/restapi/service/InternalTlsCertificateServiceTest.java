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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.niis.xroad.securityserver.restapi.repository.InternalTlsCertificateRepository;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.TestUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

/**
 * test InternalTlsCertificateService
 */
public class InternalTlsCertificateServiceTest {

    public static final String SUCCESS = "SUCCESS";
    public static final String ERROR = "ERROR";

    public static final String MOCK_SUCCESS_SCRIPT = "src/test/resources/script/success.sh";
    public static final String MOCK_FAIL_SCRIPT = "src/test/resources/script/fail.sh";
    public static final String NON_EXISTING_SCRIPT = "/path/to/non/existing/script.sh";
    public static final String SCRIPT_ARGS = "args";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private InternalTlsCertificateService internalTlsCertificateService = new InternalTlsCertificateService(
            new InternalTlsCertificateRepository(),
            new ExternalProcessRunner() {
                @Override
                public ProcessResult execute(String command, String... args) throws ProcessNotExecutableException,
                        ProcessFailedException {
                    if (command.equals(MOCK_SUCCESS_SCRIPT)) {
                        return new ProcessResult(command, 0, Collections.singletonList(SUCCESS));
                    }
                    if (command.equals(MOCK_FAIL_SCRIPT)) {
                        throw new ProcessFailedException("Mock error msg");
                    }
                    if (command.equals(NON_EXISTING_SCRIPT)) {
                        throw new ProcessNotExecutableException(new IOException(ERROR));
                    }
                    throw new RuntimeException("TEST command not supported");
                }
            },
            new ClearCacheService() {
                @Override
                public boolean executeClearConfigurationCache() {
                    return true;
                }
            }, null, SCRIPT_ARGS, mock(AuditDataHelper.class));

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
            @Override
            public Collection<X509Certificate> getInternalTlsCertificateChain() {
                try (InputStream stream = getClass().getClassLoader().getResourceAsStream("internal.crt")) {
                    return CryptoUtils.readCertificates(stream);
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
     *
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
        internalTlsCertificateService.setGenerateCertScriptPath(MOCK_SUCCESS_SCRIPT);
        try {
            internalTlsCertificateService.generateInternalTlsKeyAndCertificate();
        } catch (Exception e) {
            fail("should not throw exceptions");
        }
    }

    @Test
    public void generateInternalTlsKeyAndCertificateFail() throws Exception {
        internalTlsCertificateService.setGenerateCertScriptPath(MOCK_FAIL_SCRIPT);
        try {
            internalTlsCertificateService.generateInternalTlsKeyAndCertificate();
        } catch (DeviationAwareRuntimeException e) {
            Assert.assertEquals(DeviationCodes.ERROR_KEY_CERT_GENERATION_FAILED, e.getErrorDeviation().getCode());
        }
    }

    @Test
    public void generateInternalTlsKeyAndCertificateNotExecutable() throws Exception {
        internalTlsCertificateService.setGenerateCertScriptPath(NON_EXISTING_SCRIPT);
        try {
            internalTlsCertificateService.generateInternalTlsKeyAndCertificate();
        } catch (DeviationAwareRuntimeException e) {
            Assert.assertEquals(DeviationCodes.ERROR_KEY_CERT_GENERATION_FAILED, e.getErrorDeviation().getCode());
        }
    }

    @Test
    public void importInternalTlsCertificate() throws Exception {
        prepareTlsImportForTesting();
        byte[] internalCertBytes = TestUtils.getTestResourceFileAsBytes("internal-new.crt");
        try {
            internalTlsCertificateService.importInternalTlsCertificate(internalCertBytes);
        } catch (Exception e) {
            fail("should not throw exceptions");
        }
    }

    @Test
    public void importValidInternalTlsCertificateChain() throws Exception {
        prepareTlsImportForTesting();
        byte[] internalCertBytes = TestUtils.getTestResourceFileAsBytes("validchain.crt");
        try {
            internalTlsCertificateService.importInternalTlsCertificate(internalCertBytes);
        } catch (Exception e) {
            fail("should not throw exceptions");
        }
    }

    @Test(expected = CertificateAlreadyExistsException.class)
    public void importDuplicateInternalTlsCertificate() throws Exception {
        prepareTlsImportForTesting();
        byte[] internalCertBytes = TestUtils.getTestResourceFileAsBytes("internal.crt");
        internalTlsCertificateService.importInternalTlsCertificate(internalCertBytes);
    }

    @Test(expected = DeviationAwareRuntimeException.class)
    public void importInvalidInternalTlsCertificateChain() throws Exception {
        prepareTlsImportForTesting();
        byte[] internalCertBytes = TestUtils.getTestResourceFileAsBytes("invalidchain.crt");
        internalTlsCertificateService.importInternalTlsCertificate(internalCertBytes);
    }

    @Test(expected = KeyNotFoundException.class)
    public void importInternalTlsCertificateWrongKey() throws Exception {
        prepareTlsImportForTesting();
        byte[] internalCertBytes = TestUtils.getTestResourceFileAsBytes("google-cert.pem");
        internalTlsCertificateService.importInternalTlsCertificate(internalCertBytes);
    }

    @Test(expected = InvalidCertificateException.class)
    public void importInternalTlsInvalidCertificate() throws Exception {
        prepareTlsImportForTesting();
        byte[] certFileData = CertificateTestUtils.getInvalidCertBytes();
        internalTlsCertificateService.importInternalTlsCertificate(certFileData);
    }

    /**
     * Creates a random temp folder structure to mimic the real xroad conf path
     *
     * @throws Exception
     */
    private void prepareTlsImportForTesting() throws Exception {
        File tempSslFolder = tempFolder.newFolder("ssl");
        File tempKeyFile = tempFolder.newFile(InternalSSLKey.PK_FILE_NAME);
        File tempCertFile = tempFolder.newFile(InternalSSLKey.CRT_FILE_NAME);
        String confPath = tempSslFolder.getParent() + "/";
        internalTlsCertificateService.setInternalCertPath(confPath + InternalSSLKey.CRT_FILE_NAME);
        internalTlsCertificateService.setInternalKeyPath(confPath + InternalSSLKey.PK_FILE_NAME);
        internalTlsCertificateService.setInternalKeystorePath(confPath + InternalSSLKey.KEY_FILE_NAME);
        File internalKeyFile = TestUtils.getTestResourceFile("internal.key");
        File internalCertFile = TestUtils.getTestResourceFile("internal.crt");
        FileUtils.copyFile(internalKeyFile, tempKeyFile);
        FileUtils.copyFile(internalCertFile, tempCertFile);
    }
}
