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
package ee.ria.xroad.signer.tokenmanager;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.signer.model.Cert;
import ee.ria.xroad.signer.model.Token;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.tokenmanager.merge.TokenMergeAddedCertificatesListener;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Class for testing {@link TokenManager} merging of configuration files
 */
@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class TokenManagerMergeTest {

    private static final String ROOT = "./build/resources/test/mergetesting/";
    private static final Path ORIGINAL_FILE_PATH = Paths.get(ROOT + "keyconf_base_no_duplicate_keyIds.xml");
    private static final Path ADDED_KEY_FILE_PATH = Paths.get(ROOT + "keyconf_added_key.xml");
    private static final Path ADDED_KEY_CERT_FILE_PATH = Paths.get(ROOT + "keyconf_added_cert.xml");

    private File testingFile;

    @Rule
    public final ProvideSystemProperty slaveProperty
            = new ProvideSystemProperty(SystemProperties.NODE_TYPE, SystemProperties.NodeType.SLAVE.toString());


    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

    @Captor
    private ArgumentCaptor<List<Cert>> certListArgumentCaptor;

    /**
     * Set up the original key conf file for testing and init the {@link TokenManager}
     *
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {

        testingFile = temporaryFolder.newFile("keyconf-testing.xml");
        System.setProperty(SystemProperties.KEY_CONFIGURATION_FILE, testingFile.getPath());

        Files.copy(ORIGINAL_FILE_PATH, testingFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        TokenManager.init();
    }

    @Test
    public void shouldMergeKeyAvailability() throws Exception {

        final String testKeyId = "636f6e73756d6574";
        KeyInfo beforeKeyInfo = TokenManager.getKeyInfo(testKeyId);
        assertNotNull("test setup failure", beforeKeyInfo);
        assertFalse(TokenManager.isKeyAvailable(testKeyId));

        TokenManager.setKeyAvailable(testKeyId, true);
        Files.copy(ADDED_KEY_FILE_PATH, testingFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        TokenManager.merge(addedCerts -> {
        });

        assertTrue("key availability was not merged", TokenManager.isKeyAvailable(testKeyId));
    }

    @Test
    public void shouldNotMergeEmptyFile() throws IOException {

        List<Token> beforeTokens = TokenConf.getInstance().getTokens();

        final int beforeSize = beforeTokens.size();
        final int beforeCertCount = TokenManager.getAllCerts().size();

        File emptyFile = temporaryFolder.newFile();
        Files.copy(emptyFile.toPath(), testingFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        TokenManager.merge(addedCerts -> {
        });

        List<Token> afterTokens = TokenConf.getInstance().getTokens();
        assertEquals("token amount changed", beforeSize, afterTokens.size());
        assertEquals("cert amount changed", beforeCertCount, TokenManager.getAllCerts().size());

    }

    /**
     * Test that a key added in the file appears in tokens after merge.
     *
     * @throws IOException
     */
    @Test
    public void shouldAddCertFromFile() throws IOException {

        assertTrue("test setup failure", Files.exists(ADDED_KEY_FILE_PATH));

        final int beforeCertCount = TokenManager.getAllCerts().size();

        Files.copy(ADDED_KEY_FILE_PATH, testingFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        TokenMergeAddedCertificatesListener listenerMock = mock(TokenMergeAddedCertificatesListener.class);

        TokenManager.merge(listenerMock);

        assertEquals("cert amount should be original + 1", beforeCertCount + 1, TokenManager.getAllCerts().size());
        verify(listenerMock, times(1)).mergeDone(certListArgumentCaptor.capture());
        assertThat("Added certs count mismatch", certListArgumentCaptor.getValue().size(), is(1));

    }

    @Test
    public void shouldAddCertToCorrectKey() throws IOException {

        assertTrue("test setup failure", Files.exists(ADDED_KEY_CERT_FILE_PATH));

        final String testKeyId = "70726f6475636572";
        KeyInfo beforeKeyInfo = TokenManager.getKeyInfo(testKeyId);
        assertNotNull("test setup failure", beforeKeyInfo);

        final int beforeCount = beforeKeyInfo.getCerts().size();

        Files.copy(ADDED_KEY_CERT_FILE_PATH, testingFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        TokenManager.merge(addedCerts -> {
        });

        assertEquals("cert amount for key should be original + 1",
                beforeCount + 1, TokenManager.getKeyInfo(testKeyId).getCerts().size());
    }

    @Test
    public void shouldAddOcspResponse() throws IOException {

        assertTrue("test setup failure", Files.exists(ADDED_KEY_FILE_PATH));

        final String testKeyId = "70726f6475636572";
        KeyInfo beforeKeyInfo = TokenManager.getKeyInfo(testKeyId);
        assertNotNull("test setup failure", beforeKeyInfo);

        final String testCertId = "06700c12f395183c779884fcd49d4ca55fa485aa65617da5b75d84927bec2c91";
        final String testCertSha1Hash = "e82e0b2b184d4387c2afd83708d4cfeaeb872cf7";
        CertificateInfo beforeCertInfo = TokenManager.getCertificateInfo(testCertId);
        assertNotNull("test setup failure", beforeCertInfo);

        // assert no ocsp response exists before test
        assertArrayEquals("test setup failure", new byte[0], beforeCertInfo.getOcspBytes());

        OCSPResp shouldMatchResponse = mock(OCSPResp.class);
        final byte[] shouldMatchOcspResponseBytes = "some example string  11 2 34".getBytes();
        when(shouldMatchResponse.getEncoded()).thenReturn(shouldMatchOcspResponseBytes);
        TokenManager.setOcspResponse(testCertSha1Hash, shouldMatchResponse);

        final int beforeCertCount = TokenManager.getAllCerts().size();

        Files.copy(ADDED_KEY_CERT_FILE_PATH, testingFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        TokenManager.merge(addedCerts -> {
        });

        // make sure the merge actually reads the file, otherwise the ocsp response will of course be there
        assertEquals("merge did not add expected cert", beforeCertCount + 1, TokenManager.getAllCerts().size());

        assertArrayEquals("ocsp response bytes does not match",
                shouldMatchOcspResponseBytes,
                TokenManager.getCertificateInfo(testCertId).getOcspBytes());
    }
}
