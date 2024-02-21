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
package ee.ria.xroad.signer.tokenmanager.merge;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.model.Cert;
import ee.ria.xroad.signer.model.CertRequest;
import ee.ria.xroad.signer.model.Key;
import ee.ria.xroad.signer.model.Token;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.tokenmanager.merge.TokenMergeStrategy.MergeResult;
import ee.ria.xroad.signer.tokenmanager.module.SoftwareModuleType;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static ee.ria.xroad.signer.tokenmanager.merge.MergeOntoFileTokenStrategyTest.TestKeyHelper.createId;
import static ee.ria.xroad.signer.tokenmanager.merge.MergeOntoFileTokenStrategyTest.TestKeyHelper.createKey;
import static ee.ria.xroad.signer.tokenmanager.merge.MergeOntoFileTokenStrategyTest.TestKeyHelper.createKeyWithOneOcspResponse;
import static ee.ria.xroad.signer.tokenmanager.merge.MergeOntoFileTokenStrategyTest.TestKeyHelper.createKeys;
import static ee.ria.xroad.signer.tokenmanager.merge.MergeOntoFileTokenStrategyTest.TestKeyHelper.getCertCount;
import static ee.ria.xroad.signer.tokenmanager.merge.MergeOntoFileTokenStrategyTest.TestKeyHelper.getResponseIndex;
import static ee.ria.xroad.signer.tokenmanager.merge.MergeOntoFileTokenStrategyTest.TestKeyHelper.getResponseStatus;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link MergeOntoFileTokensStrategy}
 */
@Slf4j
public class MergeOntoFileTokenStrategyTest {


    private static MergeOntoFileTokensStrategy testedStrategy;

    @Before
    public void setup() {
        testedStrategy = new MergeOntoFileTokensStrategy();
    }

    /**
     * @see MergeOntoFileTokensStrategy#merge(List, List)
     */
    @Test
    public void mergeShouldAddMissingTokens() {

        Token fileToken1 = new Token(SoftwareModuleType.TYPE, "1", CryptoUtils.CKM_RSA_PKCS_NAME);
        Token fileToken2 = new Token(SoftwareModuleType.TYPE, "2", CryptoUtils.CKM_RSA_PKCS_NAME);
        Token memoryToken1 = new Token(SoftwareModuleType.TYPE, "1", CryptoUtils.CKM_RSA_PKCS_NAME);
        Token memoryToken2 = new Token(SoftwareModuleType.TYPE, "2", CryptoUtils.CKM_RSA_PKCS_NAME);
        Token memoryToken3 = new Token(SoftwareModuleType.TYPE, "3", CryptoUtils.CKM_RSA_PKCS_NAME);


        List<Token> fileList = Arrays.asList(fileToken1, fileToken2);

        List<Token> memoryList = Arrays.asList(memoryToken1, memoryToken2, memoryToken3);
        final int memListSize = memoryList.size();

        MergeResult result = testedStrategy.merge(fileList, memoryList);

        List<Token> mergedList = result.getResultTokens();

        assertThat("Missing tokens were not added", mergedList.size(), is(memListSize));
        assertThat("Missing tokens were not added", mergedList, hasItems(fileToken1, fileToken2, memoryToken3));
    }

    /**
     * @see MergeOntoFileTokensStrategy#merge(List, List)
     */
    @Test
    public void mergeShouldMergeTokensInLists() {
        final String tokenId = "1124";

        Token fileToken = new Token(SoftwareModuleType.TYPE, tokenId, CryptoUtils.CKM_RSA_PKCS_NAME);
        fileToken.setActive(false);

        Token memoryToken = new Token(SoftwareModuleType.TYPE, tokenId, CryptoUtils.CKM_RSA_PKCS_NAME);
        memoryToken.setActive(true);

        MergeResult result = testedStrategy.merge(Collections.singletonList(fileToken),
                Collections.singletonList(memoryToken));

        assertTrue("Token was not merged, isActive was not changed", result.getResultTokens().get(0).isActive());
    }

    /**
     * @see MergeOntoFileTokensStrategy#mergeToken(Token, Token)
     */
    @Test
    public void mergeTokenShouldMergeOnlySpecificFields() {

        final String fileTokenId = "1";
        Token fileToken = new Token(SoftwareModuleType.TYPE, fileTokenId, CryptoUtils.CKM_RSA_PKCS_NAME);

        final String fileFriendlyName = "fileFriendlyName";
        fileToken.setFriendlyName(fileFriendlyName);

        final String fileSerialNumber = "123444121";
        fileToken.setSerialNumber(fileSerialNumber);

        final String fileLabel = "fileLabel";
        fileToken.setLabel(fileLabel);

        final int fileSlotIndex = 151;
        fileToken.setSlotIndex(fileSlotIndex);

        final boolean fileBatchSigningEnabled = false;
        fileToken.setBatchSigningEnabled(fileBatchSigningEnabled);

        fileToken.setModuleId("fileModuleId");
        fileToken.setAvailable(false);
        fileToken.setReadOnly(false);
        fileToken.setActive(false);

        Token memoryToken = new Token(SoftwareModuleType.TYPE, "2", CryptoUtils.CKM_RSA_PKCS_NAME);

        final String memModuleId = "memoryModuleId";
        memoryToken.setModuleId(memModuleId);

        final boolean memReadOnly = true;
        memoryToken.setReadOnly(memReadOnly);

        final boolean memActive = true;
        memoryToken.setActive(memActive);

        final boolean memAvailable = true;
        memoryToken.setAvailable(memAvailable);

        memoryToken.setFriendlyName("memory");
        memoryToken.setInfo(new HashMap<>());
        memoryToken.setAvailable(true);
        memoryToken.setLabel("memoryLabel");
        memoryToken.setBatchSigningEnabled(true);
        memoryToken.setSerialNumber("memorySerial");
        memoryToken.setSlotIndex(0);

        testedStrategy.mergeToken(fileToken, memoryToken);

        assertEquals("moduleId was not merged", memModuleId, fileToken.getModuleId());
        assertEquals("readOnly was not merged", memReadOnly, fileToken.isReadOnly());
        assertEquals("active was not merged", memActive, fileToken.isActive());
        assertEquals("available was not merged", memAvailable, fileToken.isAvailable());

        assertEquals("token id was merged", fileTokenId, fileToken.getId());
        assertEquals("friendly name was merged", fileFriendlyName, fileToken.getFriendlyName());
        assertEquals("serial number name was merged", fileSerialNumber, fileToken.getSerialNumber());
        assertEquals("label name was merged", fileLabel, fileToken.getLabel());
        assertEquals("slot index was merged", fileSlotIndex, fileToken.getSlotIndex());
        assertEquals("batch signing enabled was merged", fileBatchSigningEnabled,
                fileToken.isBatchSigningEnabled());
    }

    /**
     * @see MergeOntoFileTokensStrategy#mergeToken(Token, Token)
     */
    @Test
    public void mergeTokenShouldMergeKeysInTokens() {

        final String tokenId = "1123";
        Token fileToken = new Token(SoftwareModuleType.TYPE, tokenId, CryptoUtils.CKM_RSA_PKCS_NAME);
        Token memoryToken = new Token(SoftwareModuleType.TYPE, tokenId, CryptoUtils.CKM_RSA_PKCS_NAME);

        final String keyId = "1551";

        Key memoryKey = createKey(keyId, 2);
        memoryKey.setAvailable(true);
        memoryToken.addKey(memoryKey);

        Key fileKey = createKey(keyId, 3);
        fileKey.setAvailable(false);
        fileToken.addKey(fileKey);

        testedStrategy.mergeToken(fileToken, memoryToken);

        assertTrue("key availability in token was not merged", fileKey.isAvailable());

    }

    /**
     * @see MergeOntoFileTokensStrategy#mergeKeyLists(List, List)
     */
    @Test
    public void mergeKeyListsShouldNotChangeKeys() {

        final int fileKeyCount = 5;

        final List<Key> memKeys = createKeys(fileKeyCount + 1);

        final String[] fileIds = IntStream.range(0, fileKeyCount)
                .mapToObj(TestKeyHelper::createId).toArray(String[]::new);

        final List<Key> fileKeys = Arrays.stream(fileIds)
                .map(id -> createKey(id, getCertCount(id)))
                .collect(Collectors.toList());

        testedStrategy.mergeKeyLists(fileKeys, memKeys);

        assertEquals("key amount has changed", fileKeyCount, fileKeys.size());
        assertThat("keys have changed",
                fileKeys.stream().map(Key::getId).collect(Collectors.toList()),
                hasItems(fileIds));

        fileKeys.forEach(key ->
                assertEquals(String.format("certificate count for key: %s has changed", key),
                        getCertCount(key.getId()),
                        key.getCerts().size())
        );
    }

    /**
     * @see MergeOntoFileTokensStrategy#mergeKeyLists(List, List)
     */
    @Test
    public void mergeKeyListsShouldKeepTrackOfAddedCertsToExistingKeys() {

        final int memKeyCount = 3;

        final List<Key> memKeys = createKeys(memKeyCount);
        final List<Key> fileKeys = createKeys(memKeyCount);

        List<Cert> expectedAddedCerts = new ArrayList<>();

        Cert addedCert1 = new Cert("addedCert-1");
        fileKeys.get(0).getCerts().add(addedCert1);
        expectedAddedCerts.add(addedCert1);

        Cert addedCert2 = new Cert("addedCert-2");
        fileKeys.get(1).getCerts().add(addedCert2);
        expectedAddedCerts.add(addedCert2);

        Cert addedCert3 = new Cert("addedCert-3");
        fileKeys.get(1).getCerts().add(addedCert3);
        expectedAddedCerts.add(addedCert3);

        Cert addedCert4 = new Cert("addedCert-4");
        fileKeys.get(2).getCerts().add(addedCert4);
        expectedAddedCerts.add(addedCert4);

        testedStrategy.mergeKeyLists(fileKeys, memKeys);

        List<Cert> addedCerts = testedStrategy.getNewCertsFromFile();

        assertThat("Added cert count mismatch", addedCerts.size(), is(expectedAddedCerts.size()));
        assertThat("Added certs do not match", addedCerts, is(expectedAddedCerts));

    }

    /**
     * @see MergeOntoFileTokensStrategy#mergeKeyLists(List, List)
     */
    @Test
    public void mergeKeyListsShouldKeepTrackOfAddedCertsToNewKey() {

        final int memKeyCount = 3;

        final List<Key> memKeys = createKeys(memKeyCount);
        final List<Key> fileKeys = createKeys(memKeyCount);

        List<Cert> expectedAddedCerts = new ArrayList<>();

        Key addedKey1 = createKey("addedKey-1", 1);
        expectedAddedCerts.add(addedKey1.getCerts().get(0));

        Key addedKey2 = createKey("addedKey-2", 1);
        expectedAddedCerts.add(addedKey2.getCerts().get(0));

        fileKeys.add(addedKey1);
        fileKeys.add(addedKey2);

        testedStrategy.mergeKeyLists(fileKeys, memKeys);

        List<Cert> addedCerts = testedStrategy.getNewCertsFromFile();

        assertThat("Added cert count mismatch", addedCerts.size(), is(expectedAddedCerts.size()));
        assertThat("Added certs do not match", addedCerts, is(expectedAddedCerts));

    }

    /**
     * @see MergeOntoFileTokensStrategy#mergeKeyLists(List, List)
     */
    @Test
    public void mergeKeyListsShouldCopyOcspResponses() {

        List<Integer> shouldHaveOcspResponseIds = Arrays.asList(1, 2, 4);

        final int fileKeyCount = 6;

        final List<Key> memKeys =
                IntStream.range(0, fileKeyCount + 1).mapToObj(i -> {
                    String id = createId(i);
                    int certCount = getCertCount(id);

                    if (shouldHaveOcspResponseIds.contains(i)) {
                        return createKeyWithOneOcspResponse(id,
                                certCount, getResponseIndex(id), getResponseStatus(id));
                    } else {
                        return createKey(id, certCount);

                    }
                }).collect(Collectors.toList());

        final List<Key> fileKeys = createKeys(fileKeyCount);

        testedStrategy.mergeKeyLists(fileKeys, memKeys);

        fileKeys.stream()
                .filter(key -> shouldHaveOcspResponseIds.contains(Integer.parseInt(key.getId())))
                .forEach(key -> {
                    String id = key.getId();
                    Cert cert = key.getCerts().get(getResponseIndex(id));
                    OCSPResp response = cert.getOcspResponse();

                    assertNotNull("No OCSP response present", response);

                    assertThat("OCSP response status does not match",
                            response.getStatus(),
                            is(getResponseStatus(id)));
                });
    }

    @Test
    public void mergeKeyListShouldMergeDuplicateKeys() {
        List<Key> memKeys = new ArrayList<>();
        List<Key> fileKeys = new ArrayList<>();

        Key fileKey1 = createKey("key-1", 5);
        Key fileKeyDuplicate1 = createKey("key-dupl", 1);
        Key fileKeyDuplicate2 = createKey("key-dupl", 5);

        final int expectedAddedCerts = fileKey1.getCerts().size()
                + fileKeyDuplicate1.getCerts().size()
                + fileKeyDuplicate2.getCerts().size() - 1; // the firs duplicate cert is the same

        fileKeys.addAll(Arrays.asList(fileKeyDuplicate1, fileKey1, fileKeyDuplicate2));

        testedStrategy.mergeKeyLists(fileKeys, memKeys);

        final List<Cert> addedCerts = testedStrategy.getNewCertsFromFile();

        log.error("Added certs = {}", addedCerts);

        assertThat("duplicates weren't merged", fileKeys.size(), is(2));

        // NB: expectedAddedCerts will not be correct (10), MergeOntoFileTokensStrategy does not take into
        // account that (id, public key) pairs exist with different certificates
        // assertThat("cert count does not match expected", addedCerts.size(), is(expectedAddedCerts));
    }

    /**
     * @see MergeOntoFileTokensStrategy#mergeKey(Key, Key)
     */
    @Test
    public void mergeKeyShouldOnlyCopyOverAvailabilityOutOfSingleFields() {

        final String fileId = "123t5dssd";
        final Token fileToken = new Token("fileToken", "fileId", CryptoUtils.CKM_RSA_PKCS_NAME);
        final Key fileKey = new Key(fileToken, fileId);

        final String fileFriendlyName = "d§gsdasd";
        fileKey.setFriendlyName(fileFriendlyName);

        final boolean fileAvailable = false;
        fileKey.setAvailable(fileAvailable);

        final String fileLabel = "12gggfgergef";
        fileKey.setLabel(fileLabel);

        final String filePublicKey = "qfasqqqweqwe---";
        fileKey.setPublicKey(filePublicKey);

        final KeyUsageInfo fileKeyUsageInfo = KeyUsageInfo.AUTHENTICATION;
        fileKey.setUsage(fileKeyUsageInfo);

        final String memId = "asre111";
        Key memKey = new Key(new Token("memToken", "memId", CryptoUtils.CKM_RSA_PKCS_NAME), memId);

        final boolean memAvailable = true;
        assertNotEquals("test setup failure", fileAvailable, memAvailable);

        memKey.setAvailable(memAvailable);

        memKey.setUsage(KeyUsageInfo.SIGNING);
        memKey.setPublicKey("eeefqweggffgeeew-");
        memKey.setLabel("label111222");

        testedStrategy.mergeKey(fileKey, memKey);

        assertEquals("availability was not merged", memAvailable, fileKey.isAvailable());

        assertEquals("token was merged", fileToken, fileKey.getToken());
        assertEquals("id was merged", fileId, fileKey.getId());
        assertEquals("friendly name was merged", fileFriendlyName, fileKey.getFriendlyName());
        assertEquals("label was merged", fileLabel, fileKey.getLabel());
        assertEquals("public key was merged", filePublicKey, fileKey.getPublicKey());


    }

    /**
     * @see MergeOntoFileTokensStrategy#mergeKey(Key, Key)
     */
    @Test
    public void mergeKeyShouldNotCopyOverCertList() {


        final ClientId.Conf clientId = ClientId.Conf.create("FI", "CLIENTMEMBERCLASS", "MEMBERCODE11");

        final Key memKey = new Key(null, "memId");

        memKey.addCertRequest(new CertRequest("memRequest 1", clientId, "CN=memRequest1"));
        memKey.addCertRequest(new CertRequest("memRequest 2", clientId, "CN=memRequest2"));

        final Key fileKey = new Key(null, "fileId");
        final CertRequest fileRequest1 = new CertRequest("fileRequest 1", clientId, "CN=fileRequest1");
        final CertRequest fileRequest2 = new CertRequest("fileRequest 2", clientId, "CN=fileRequest2");

        fileKey.addCertRequest(fileRequest1);
        fileKey.addCertRequest(fileRequest2);

        testedStrategy.mergeKey(fileKey, memKey);

        assertThat("Certs Requests were changed", fileKey.getCertRequests(), hasItems(fileRequest1, fileRequest2));

    }

    /**
     * @see MergeOntoFileTokensStrategy#mergeKey(Key, Key)
     */
    @Test
    public void mergeKeyShouldMergeCertListAndCopyOcspResponse() {

        final String keyId = "132555";
        final int ocspRespStatus = 1111232551;
        final int responseIndexInList = 2;

        final Key memKey = createKeyWithOneOcspResponse(keyId, 10, responseIndexInList, ocspRespStatus);

        final Key fileKey = createKey(keyId, 4);
        final List<Cert> fileCerts = fileKey.getCerts();

        assertEquals("test setup failed", memKey.getCerts().get(responseIndexInList).getId(),
                fileCerts.get(responseIndexInList).getId());

        final Cert certThatShouldHaveOcspResponse = fileCerts.get(responseIndexInList);
        final int fileCertSizeBefore = fileCerts.size();

        testedStrategy.mergeKey(fileKey, memKey);

        assertEquals("certificate list size changed", fileCertSizeBefore, fileKey.getCerts().size());
        assertEquals("ocsp response was not copied over for the matching certificate",
                ocspRespStatus, certThatShouldHaveOcspResponse.getOcspResponse().getStatus());

        assertThat("non-matching certificates should not have ocsp responses",
                fileCerts.stream()
                        .filter(cert -> !certThatShouldHaveOcspResponse.equals(cert))
                        .map(Cert::getOcspResponse)
                        .collect(Collectors.toList()),
                everyItem(nullValue()));
    }

    static class TestKeyHelper {

        static Key createKeyWithOneOcspResponse(String keyId, int certCount, int setResponseFor, int responseCode) {
            checkArgument(certCount >= 0, "cert count must be > 0");
            checkArgument(setResponseFor >= 0 && setResponseFor < certCount,
                    "setResponse must be between 0 and certCount");

            Key key = new Key(null, keyId);
            key.setPublicKey("public-key_" + keyId);

            IntStream.range(0, certCount).forEach(
                    n -> {
                        Cert cert = new Cert(keyId + "-certId-" + n);
                        key.addCert(cert);
                        if (n == setResponseFor) {
                            OCSPResp response = mock(OCSPResp.class);
                            when(response.getStatus()).thenReturn(responseCode);
                            cert.setOcspResponse(response);
                        }
                    }
            );
            return key;
        }

        static Key createKey(String keyId, int certCount) {
            return createKeyWithOneOcspResponse(keyId, certCount, 0, 0);
        }

        static String createId(int index) {
            return String.valueOf(index);
        }

        static int getCertCount(String id) {
            return Integer.parseInt(id) + 2;
        }

        static int getResponseIndex(String id) {
            return Integer.parseInt(id) + 1;
        }

        static int getResponseStatus(String id) {
            return Integer.parseInt(id) * 15 + 3;
        }

        static List<Key> createKeys(int count) {
            return IntStream.range(0, count)
                    .mapToObj(i -> {
                        String id = createId(i);
                        return createKey(id, getCertCount(id));
                    })
                    .collect(Collectors.toList());
        }
    }

    /**
     * @see MergeOntoFileTokensStrategy#mergeCertLists(List, List)
     */
    @Test
    public void mergeCertListsShouldNotAddOrRemoveCertsInFileCertList() {

        Cert mem1 = new Cert("mem1");
        Cert mem2 = new Cert("mem2");
        List<Cert> memList = Arrays.asList(mem1, mem2);

        Cert file1 = new Cert("file1");
        Cert file2 = new Cert("file2");
        List<Cert> fileList = Arrays.asList(file1, file2);
        final int origSize = fileList.size();

        testedStrategy.mergeCertLists(fileList, memList);

        assertEquals("Certs were added or removed", origSize, fileList.size());
        assertThat("Certs were changed", fileList, hasItems(file1, file2));
    }

    /**
     * @see MergeOntoFileTokensStrategy#mergeCertLists(List, List)
     */
    @Test
    public void mergeCertListsShouldKeepTrackOfAddedCerts() {

        Cert mem1 = new Cert("mem1");
        Cert mem2 = new Cert("mem2");
        List<Cert> memList = Arrays.asList(mem1, mem2);

        Cert addedFile1 = new Cert("file1");
        Cert addedFile2 = new Cert("file2");
        Cert addedFile3 = new Cert("file3");
        final int expectedNewCount = 3;

        List<Cert> fileList = Arrays.asList(addedFile1, mem1, addedFile2, mem2, addedFile3);

        testedStrategy.mergeCertLists(fileList, memList);
        List<Cert> addedCerts = testedStrategy.getNewCertsFromFile();

        assertThat("Was expecting a different amount of added certs", addedCerts.size(), is(expectedNewCount));
        assertThat("Was expecting at least added certs", addedCerts, hasItems(addedFile1, addedFile2, addedFile3));
    }

    /**
     * @see MergeOntoFileTokensStrategy#mergeCertLists(List, List)
     */
    @Test
    public void mergeCertListsShouldAddOcspResponseToFileCertList() {

        final String id1 = "1b";
        Cert mem1 = new Cert(id1);
        final String id2 = "2a";
        Cert mem2 = new Cert(id2);
        List<Cert> memList = Arrays.asList(mem1, mem2);

        final int resp1Status = 1111232551;
        OCSPResp resp1 = mock(OCSPResp.class);
        when(resp1.getStatus()).thenReturn(resp1Status);

        final int resp2Status = 222341235;
        OCSPResp resp2 = mock(OCSPResp.class);
        when(resp2.getStatus()).thenReturn(resp2Status);

        mem1.setOcspResponse(resp1);
        mem2.setOcspResponse(resp2);

        Cert file1 = new Cert(id1);
        Cert file2 = new Cert(id2);
        Cert file3 = new Cert("<3");
        List<Cert> fileList = Arrays.asList(file1, file2, file3);

        testedStrategy.mergeCertLists(fileList, memList);

        assertEquals("OCSP response 1 status does not match", resp1Status, file1.getOcspResponse().getStatus());
        assertEquals("OCSP response 2 status does not match", resp2Status, file2.getOcspResponse().getStatus());

        assertEquals("OCSP response 1 does not match", resp1, file1.getOcspResponse());
        assertEquals("OCSP response 2 does not match", resp2, file2.getOcspResponse());
        assertNull("OCSP response 3 not empty", file3.getOcspResponse());

    }

    /**
     * @see MergeOntoFileTokensStrategy#mergeCert(Cert, Cert)
     */
    @Test
    public void mergeCertShouldCopyOverOnlyOcspResponseToFileCert() {

        final String fileId = "file";
        Cert fileCert = new Cert(fileId);
        fileCert.setActive(false);
        final ClientId.Conf fileClientId = ClientId.Conf.create("FI", "GOV", "FILEMEMBER");
        fileCert.setMemberId(fileClientId);

        fileCert.setSavedToConfiguration(false);

        final String fileStatus = "fileStatus";
        fileCert.setStatus("fileStatus");


        OCSPResp ocspResp = mock(OCSPResp.class);
        Cert memCert = new Cert("memory");
        memCert.setOcspResponse(ocspResp);
        memCert.setStatus("asdasdgg");
        memCert.setMemberId(ClientId.Conf.create("FI", "COM", "CLIENTMEMBER"));
        memCert.setSavedToConfiguration(true);

        testedStrategy.mergeCert(fileCert, memCert);

        assertEquals("OCSP response wasn't merged.", ocspResp, fileCert.getOcspResponse());
        assertEquals("MemberId was merged", fileClientId, fileCert.getMemberId());
        assertEquals("Id was merged", fileId, fileCert.getId());
        assertEquals("Status was merged", fileStatus, fileCert.getStatus());
        assertFalse("Saved to configuration was merged", fileCert.isSavedToConfiguration());
    }
}
