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
package org.niis.xroad.signer.core.tokenmanager;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.niis.xroad.serverconf.impl.entity.ClientIdEntity;
import org.niis.xroad.serverconf.impl.entity.KeyConfCertRequestEntity;
import org.niis.xroad.serverconf.impl.entity.KeyConfCertificateEntity;
import org.niis.xroad.serverconf.impl.entity.KeyConfDeviceEntity;
import org.niis.xroad.serverconf.impl.entity.KeyConfKeyEntity;
import org.niis.xroad.serverconf.impl.entity.type.KeyUsage;
import org.niis.xroad.signer.core.model.Cert;
import org.niis.xroad.signer.core.model.CertRequest;
import org.niis.xroad.signer.core.model.Key;
import org.niis.xroad.signer.core.model.Token;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.security.cert.X509Certificate;
import java.time.Instant;

@UtilityClass
class TokenTestUtils {

    public static Token createFullTestToken(String tokenId, Long internalId, String friendlyName, String serialNumber,
                                            boolean readOnly, boolean active, int numKeys, int numCertsPerKey, int numCertRequestsPerKey) {
        Token token = new Token("test-type", tokenId, internalId);
        token.setFriendlyName(friendlyName);
        token.setSerialNumber(serialNumber);
        token.setReadOnly(readOnly);
        token.setActive(active);
        token.setSlotIndex(1);
        token.setLabel("TestLabel");

        for (int i = 0; i < numKeys; i++) {
            Key key = createFullTestKey(token, "key-" + tokenId + "-" + i, "KeyFriendlyName" + i,
                    numCertsPerKey, numCertRequestsPerKey);
            token.addKey(key);
        }
        return token;
    }

    public static Key createFullTestKey(Token parentToken, String keyId, String keyFriendlyName, int numCerts, int numCertRequests) {
        Key key = new Key(parentToken, keyId, SignMechanism.CKM_RSA_PKCS);
        key.setFriendlyName(keyFriendlyName);
        key.setLabel("KeyLabel-" + keyId);
        key.setPublicKey("UEsDBBQAAAAIAAAAAAAAAAAAAAAAAAAAAAAA"); // dummy base64 public key
        key.setUsage(KeyUsageInfo.SIGNING);
        key.setAvailable(false);

        for (int i = 0; i < numCerts; i++) {
            Cert cert = createTestCert("cert-" + keyId + "-" + i,
                    ClientId.Conf.create("INSTANCE", "CLASS", "MEMBER" + i));
            key.addCert(cert);
        }

        for (int i = 0; i < numCertRequests; i++) {
            CertRequest certRequest = createTestCertRequest("req-" + keyId + "-" + i, "ReqSubject" + i,
                    ClientId.Conf.create("INSTANCE", "CLASS", "REQ_MEMBER" + i));
            key.addCertRequest(certRequest);
        }
        return key;
    }

    @SneakyThrows
    public static Cert createTestCert(String certId, ClientId.Conf memberId) {
        Cert cert = new Cert(certId);

        X509Certificate certificate = TestCertUtil.getProducer().certChain[0];
        cert.setCertificate(certificate.getEncoded());
        cert.setActive(true);
        cert.setStatus("good");
        cert.setMemberId(memberId);
        cert.setSavedToConfiguration(true);
        cert.setNextAutomaticRenewalTime(Instant.now().plusSeconds(3600));
        return cert;
    }

    public static CertRequest createTestCertRequest(String reqId, String subjectName, ClientId.Conf memberId) {
        return new CertRequest(reqId, memberId, subjectName, "alt-" + subjectName, "certprofile-default");
    }

    public static KeyConfDeviceEntity createFullTestDeviceEntity(String deviceId, Long internalId, String friendlyName, String serialNumber,
                                                                 int numKeys, int numCertsPerKey, int numCertRequestsPerKey) {
        KeyConfDeviceEntity deviceEntity = new KeyConfDeviceEntity();
        deviceEntity.setId(internalId);
        deviceEntity.setDeviceId(deviceId); // Mapped to Token.id by TokenMapper
        deviceEntity.setDeviceType("test-type");
        deviceEntity.setFriendlyName(friendlyName);
        deviceEntity.setTokenId(serialNumber); // Mapped to Token.serialNumber
        deviceEntity.setSlotId("TestLabel");   // Mapped to Token.label
        deviceEntity.setPinIndex(1);           // Mapped to Token.slotIndex
        deviceEntity.setSignMechanismName(SignMechanism.CKM_RSA_PKCS);


        for (int i = 0; i < numKeys; i++) {
            KeyConfKeyEntity keyEntity = createFullTestKeyEntity("key-" + deviceId + "-" + i, "KeyFriendlyName" + i,
                    numCertsPerKey, numCertRequestsPerKey);
            keyEntity.setDeviceId(internalId); // Set back-reference
            deviceEntity.getKeys().add(keyEntity);
        }
        return deviceEntity;
    }

    public static KeyConfKeyEntity createFullTestKeyEntity(String keyId, String keyFriendlyName, int numCerts, int numCertRequests) {
        Long internalId = 1L; // Simulate internal ID generation
        KeyConfKeyEntity keyEntity = new KeyConfKeyEntity();
        keyEntity.setId(internalId);
        keyEntity.setKeyId(keyId);
        keyEntity.setFriendlyName(keyFriendlyName);
        keyEntity.setLabel("KeyLabel-" + keyId);
        keyEntity.setPublicKey("UEsDBBQAAAAIAAAAAAAAAAAAAAAAAAAAAAAA");
        keyEntity.setUsage(KeyUsage.SIGNING);
        keyEntity.setSignMechanismName(SignMechanism.CKM_RSA_PKCS);

        for (int i = 0; i < numCerts; i++) {
            ClientIdEntity memberClientIdEntity = ClientIdEntity.create(ClientId.Conf.create("INSTANCE", "CLASS", "MEMBER" + i));
            KeyConfCertificateEntity certEntity = createTestCertEntity("cert-" + keyId + "-" + i, memberClientIdEntity);
            certEntity.setKeyId(internalId); // Set back-reference
            keyEntity.getCertificates().add(certEntity);
        }

        for (int i = 0; i < numCertRequests; i++) {
            ClientIdEntity reqMemberClientIdEntity = ClientIdEntity.create(ClientId.Conf.create("INSTANCE", "CLASS", "REQ_MEMBER" + i));
            KeyConfCertRequestEntity certRequestEntity = createTestCertRequestEntity("req-" + keyId + "-" + i,
                    "ReqSubject" + i, reqMemberClientIdEntity);
            certRequestEntity.setKeyId(internalId); // Set back-reference
            keyEntity.getCertRequests().add(certRequestEntity);
        }
        return keyEntity;
    }

    @SneakyThrows
    public static KeyConfCertificateEntity createTestCertEntity(String certId, ClientIdEntity memberIdEntity) {
        KeyConfCertificateEntity certEntity = new KeyConfCertificateEntity();
        certEntity.setCertId(certId);

        X509Certificate certificate = TestCertUtil.getProducer().certChain[0];
        certEntity.setContents(certificate.getEncoded());
        certEntity.setActive(true);
        certEntity.setStatus("good");
        certEntity.setMemberId(memberIdEntity);
        certEntity.setNextRenewalTime(Instant.now().plusSeconds(3600));
        return certEntity;
    }

    public static KeyConfCertRequestEntity createTestCertRequestEntity(String reqId, String subjectName, ClientIdEntity memberIdEntity) {
        KeyConfCertRequestEntity reqEntity = new KeyConfCertRequestEntity();
        reqEntity.setCertRequestId(reqId);
        reqEntity.setMemberId(memberIdEntity);
        reqEntity.setSubjectName(subjectName);
        reqEntity.setSubjectAlternativeName("alt-" + subjectName);
        reqEntity.setCertificateProfile("certprofile-default");
        return reqEntity;
    }
}
