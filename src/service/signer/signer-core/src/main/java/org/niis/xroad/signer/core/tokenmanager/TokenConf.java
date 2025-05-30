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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.AbstractXmlConf;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.signer.core.model.Cert;
import org.niis.xroad.signer.core.model.CertRequest;
import org.niis.xroad.signer.core.model.Key;
import org.niis.xroad.signer.core.model.Token;
import org.niis.xroad.signer.core.tokenmanager.module.SoftwareModuleType;
import org.niis.xroad.signer.core.tokenmanager.token.TokenType;
import org.niis.xroad.signer.core.util.SignerUtil;
import org.niis.xroad.signer.keyconf.CertRequestType;
import org.niis.xroad.signer.keyconf.CertificateType;
import org.niis.xroad.signer.keyconf.DeviceType;
import org.niis.xroad.signer.keyconf.KeyConfType;
import org.niis.xroad.signer.keyconf.KeyType;
import org.niis.xroad.signer.keyconf.ObjectFactory;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import java.util.GregorianCalendar;
import java.util.List;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.EncoderUtils.decodeBase64;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;
import static java.util.Objects.requireNonNull;

/**
 * Holds the current keys & certificates in XML.
 */
@Slf4j
public final class TokenConf extends AbstractXmlConf<KeyConfType> {
    private static final JAXBContext JAXB_CONTEXT = createJAXBContext();

    /**
     * Specialized exception instead of a generic exception for TokenConf errors.
     */
    public static class TokenConfException extends Exception {

        public TokenConfException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static TokenConf instance;

    /**
     * @return the singleton instance
     */
    public static TokenConf getInstance() {
        if (instance == null) {
            instance = new TokenConf();
        }

        return instance;
    }

    private TokenConf() {
        super(new ObjectFactory().createKeyConf(new KeyConfType()), null);
    }

    /**
     * @return all tokens
     */
    public List<Token> getTokens() {
        return getTokensFrom(confType);

    }

    private List<Token> getTokensFrom(KeyConfType keyConfType) {
        requireNonNull(keyConfType);

        return keyConfType.getDevice().stream()
                .map(TokenConf::from)
                .collect(Collectors.toList());
    }

    /**
     * @param tokenType the token type
     * @return token for a specific type or null, if not found
     */
    public Token getToken(TokenType tokenType) {
        for (DeviceType d : confType.getDevice()) {
            if (tokenType.getModuleType().equals(SoftwareModuleType.TYPE)
                    && tokenType.getModuleType().equals(d.getDeviceType())) {
                return from(d);
            }

            Token token = from(d);

            if (token.matches(tokenType)) {
                return token;
            }
        }

        return null;
    }

    /**
     * @param id the token id
     * @return true if configuration contains token with given token id
     */
    public boolean hasToken(String id) {
        return confType.getDevice().stream().anyMatch(t -> t.getId().equals(id));
    }

    /**
     * Loads the tokens from the XML file.
     *
     * @throws Exception if an error occurs
     */
    public synchronized void load() throws Exception {
        load(getConfFileName());
    }

    /**
     * Saves the tokens to the XML file.
     *
     * @param tokens the tokens to save
     * @throws Exception if an error occurs
     */
    synchronized void save(List<Token> tokens) throws Exception {
        confType.getDevice().clear();

        // Only save the token if it has keys which have certificates or
        // certificate requests
        tokens.stream().filter(TokenConf::hasKeysWithCertsOfCertRequests)
                .forEach(token -> confType.getDevice().add(from(token)));

        save();
    }

    /**
     * Retrieves, <b>but does not load into memory</b> the tokens in the configuration file.
     *
     * @return
     */
    public List<Token> retrieveTokensFromConf() throws TokenConfException {

        try {
            doValidateConfFile();

            LoadResult<KeyConfType> newKeyConfig = doLoadConfFile();

            return getTokensFrom(newKeyConfig.getConfType());

        } catch (Exception e) {
            throw new TokenConfException("Error while loading or validating key config", e);
        }
    }

    private static boolean hasKeysWithCertsOfCertRequests(Token token) {
        return token.getKeys().stream()
                .anyMatch(TokenConf::hasCertsOrCertRequests);
    }

    private static boolean hasCertsOrCertRequests(Key key) {
        return key.getCerts().stream().map(Cert::isSavedToConfiguration)
                .findFirst().orElse(!key.getCertRequests().isEmpty());
    }

    // ------------------ Conversion helpers ----------------------------------

    private static DeviceType from(Token token) {
        DeviceType deviceType = new DeviceType();
        deviceType.setDeviceType(token.getType());
        deviceType.setFriendlyName(token.getFriendlyName());
        deviceType.setId(token.getId());
        deviceType.setPinIndex(token.getSlotIndex());
        deviceType.setTokenId(token.getSerialNumber());
        deviceType.setSlotId(token.getLabel());

        token.getKeys().stream().filter(TokenConf::hasCertsOrCertRequests)
                .forEach(key -> {
                    deviceType.getKey().add(from(key));
                });

        return deviceType;
    }

    private static KeyType from(Key key) {
        KeyType keyType = new KeyType();
        keyType.setFriendlyName(key.getFriendlyName());
        keyType.setLabel(key.getLabel());
        keyType.setKeyId(key.getId());
        keyType.setUsage(key.getUsage());
        keyType.setSignMechanismName(key.getSignMechanismName().name());

        if (key.getPublicKey() != null) {
            keyType.setPublicKey(decodeBase64(key.getPublicKey()));
        }

        for (Cert cert : key.getCerts()) {
            keyType.getCert().add(from(cert));
        }

        for (CertRequest certRequest : key.getCertRequests()) {
            keyType.getCertRequest().add(from(certRequest));
        }

        return keyType;
    }

    private static Token from(DeviceType type) {
        Token token = new Token(type.getDeviceType(), type.getId());
        token.setFriendlyName(type.getFriendlyName());
        token.setSlotIndex(type.getPinIndex() != null ? type.getPinIndex() : 0);
        token.setSerialNumber(type.getTokenId());
        token.setLabel(type.getSlotId());

        // software token forgets batch signing setting
        if (SoftwareModuleType.TYPE.equals(token.getType())) {
            token.setBatchSigningEnabled(true);
        }

        for (KeyType keyType : type.getKey()) {
            token.addKey(from(token, keyType, toSignMechanism(type.getSignMechanismName())));
        }

        return token;
    }

    private static Key from(Token device, KeyType keyType, SignMechanism defaultSigneMechanism) {
        Key key = new Key(device, keyType.getKeyId(), toSignMechanism(keyType.getSignMechanismName(), defaultSigneMechanism));
        key.setFriendlyName(keyType.getFriendlyName());
        key.setLabel(keyType.getLabel());
        key.setUsage(keyType.getUsage());

        if (keyType.getPublicKey() != null) {
            key.setPublicKey(encodeBase64(keyType.getPublicKey()));
        }

        for (CertificateType certType : keyType.getCert()) {
            key.addCert(from(certType));
        }

        for (CertRequestType certRequestType : keyType.getCertRequest()) {
            key.addCertRequest(from(certRequestType));
        }

        return key;
    }

    private static SignMechanism toSignMechanism(String signMechanism, SignMechanism defaultSignMechanism) {
        return SignMechanism.valueOf(StringUtils.firstNonBlank(signMechanism, defaultSignMechanism.name()));
    }

    private static SignMechanism toSignMechanism(String signMechanism) {
        return toSignMechanism(signMechanism, SignMechanism.CKM_RSA_PKCS);
    }

    private static Cert from(CertificateType type) {
        Cert cert = new Cert(getCertId(type));
        cert.setMemberId(type.getMemberId());
        cert.setActive(type.isActive());
        cert.setStatus(type.getStatus());
        cert.setSavedToConfiguration(true);
        cert.setCertificate(type.getContents());
        cert.setOcspVerifyBeforeActivationError(type.getOcspVerifyBeforeActivationError());
        cert.setRenewedCertHash(type.getRenewedCertHash());
        cert.setRenewalError(type.getRenewalError());
        if (type.getNextRenewalTime() != null) {
            cert.setNextAutomaticRenewalTime(type.getNextRenewalTime().toGregorianCalendar().toInstant());
        }

        return cert;
    }

    private static CertificateType from(Cert cert) {
        CertificateType type = new CertificateType();
        type.setMemberId(cert.getMemberId());
        type.setActive(cert.isActive());
        type.setId(cert.getId());
        type.setStatus(cert.getStatus());
        type.setContents(cert.getBytes());
        type.setOcspVerifyBeforeActivationError(cert.getOcspVerifyBeforeActivationError());
        type.setRenewedCertHash(cert.getRenewedCertHash());
        type.setRenewalError(cert.getRenewalError());
        type.setNextRenewalTime(getNextRenewalTimeXmlGregorianCalendar(cert));

        return type;
    }

    private static XMLGregorianCalendar getNextRenewalTimeXmlGregorianCalendar(Cert cert) {
        if (cert.getNextAutomaticRenewalTime() == null) {
            return null;
        }
        GregorianCalendar nextRenewalTimeGregorianCalendar = new GregorianCalendar();
        nextRenewalTimeGregorianCalendar.setTimeInMillis(cert.getNextAutomaticRenewalTime().toEpochMilli());
        try {
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(nextRenewalTimeGregorianCalendar);
        } catch (DatatypeConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private static CertRequest from(CertRequestType type) {
        return new CertRequest(getCertReqId(type), type.getMemberId(),
                type.getSubjectName(), type.getSubjectAlternativeName(), type.getCertificateProfile());
    }

    private static CertRequestType from(CertRequest certRequest) {
        CertRequestType type = new CertRequestType();
        type.setId(certRequest.getId());
        type.setMemberId(certRequest.getMemberId());
        type.setSubjectName(certRequest.getSubjectName());
        type.setSubjectAlternativeName(certRequest.getSubjectAltName());
        type.setCertificateProfile(certRequest.getCertificateProfile());

        return type;
    }

    private static String getCertId(CertificateType type) {
        if (type.getId() != null) {
            return type.getId();
        } else {
            try {
                return calculateCertHexHash(type.getContents());
            } catch (Exception e) {
                log.error("Failed to calculate certificate hash for {}",
                        type, e);

                return SignerUtil.randomId();
            }
        }
    }

    private static String getCertReqId(CertRequestType type) {
        return ObjectUtils.defaultIfNull(type.getId(), SignerUtil.randomId());
    }

    private static String getConfFileName() {
        return SystemProperties.getKeyConfFile();
    }

    @Override
    protected JAXBContext getJAXBContext() {
        return JAXB_CONTEXT;
    }

    private static JAXBContext createJAXBContext() {
        try {
            return JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

}
