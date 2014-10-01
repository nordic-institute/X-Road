package ee.cyber.sdsb.signer.tokenmanager;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.AbstractXmlConf;
import ee.cyber.sdsb.common.conf.keyconf.CertRequestType;
import ee.cyber.sdsb.common.conf.keyconf.CertificateType;
import ee.cyber.sdsb.common.conf.keyconf.DeviceType;
import ee.cyber.sdsb.common.conf.keyconf.KeyConfType;
import ee.cyber.sdsb.common.conf.keyconf.KeyType;
import ee.cyber.sdsb.common.conf.keyconf.ObjectFactory;
import ee.cyber.sdsb.signer.model.Cert;
import ee.cyber.sdsb.signer.model.CertRequest;
import ee.cyber.sdsb.signer.model.Key;
import ee.cyber.sdsb.signer.model.Token;
import ee.cyber.sdsb.signer.tokenmanager.module.SoftwareModuleType;
import ee.cyber.sdsb.signer.tokenmanager.token.TokenType;
import ee.cyber.sdsb.signer.util.SignerUtil;

import static ee.cyber.sdsb.common.util.CryptoUtils.*;

@Slf4j
public class TokenConf extends AbstractXmlConf<KeyConfType> {

    private static TokenConf instance;

    public static TokenConf getInstance() {
        if (instance == null) {
            instance = new TokenConf();
        }

        return instance;
    }

    public static void reload() {
        try {
            getInstance().load();
        } catch (Exception e) {
            log.error("Failed to load token conf", e);
        }
    }

    private TokenConf() {
        super(ObjectFactory.class,
                new ObjectFactory().createKeyConf(new KeyConfType()), null);
    }

    public List<Token> getTokens() {
        List<Token> tokens = new ArrayList<>();
        for (DeviceType token : confType.getDevice()) {
            tokens.add(from(token));
        }

        return tokens;
    }

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

    public boolean hasToken(String id) {
        for (DeviceType token : confType.getDevice()) {
            if (token.getId().equals(id)) {
                return true;
            }
        }

        return false;
    }

    public synchronized void load() throws Exception {
        load(getConfFileName());
    }

    public synchronized void save(List<Token> tokens) throws Exception {
        confType.getDevice().clear();

        for (Token token : tokens) {
            // Only save the token if it has keys which have certificates or
            // certificate requests
            if (hasKeysWithCertsOfCertRequests(token)) {
                confType.getDevice().add(from(token));
            }
        }

        save();
    }

    private static boolean hasKeysWithCertsOfCertRequests(Token token) {
        for (Key key : token.getKeys()) {
            if (hasCertsOrCertRequests(key)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasCertsOrCertRequests(Key key) {
        for (Cert cert : key.getCerts()) {
            if (cert.isSavedToConfiguration()) {
                return true;
            }
        }

        return !key.getCertRequests().isEmpty();
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

        for (Key key : token.getKeys()) {
            if (hasCertsOrCertRequests(key)) {
                deviceType.getKey().add(from(key));
            }
        }

        return deviceType;
    }

    private static KeyType from(Key key) {
        KeyType keyType = new KeyType();
        keyType.setFriendlyName(key.getFriendlyName());
        keyType.setKeyId(key.getId());
        keyType.setUsage(key.getUsage());

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

        for (KeyType keyType : type.getKey()) {
            token.addKey(from(token, keyType));
        }

        return token;
    }

    private static Key from(Token device, KeyType keyType) {
        Key key = new Key(device, keyType.getKeyId());
        key.setFriendlyName(keyType.getFriendlyName());
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

    private static Cert from(CertificateType type) {
        Cert cert = new Cert(getCertId(type));
        cert.setMemberId(type.getMemberId());
        cert.setActive(type.isActive());
        cert.setStatus(type.getStatus());
        cert.setSavedToConfiguration(true);
        cert.setCertificate(type.getContents());

        return cert;
    }

    private static CertificateType from(Cert cert) {
        CertificateType type = new CertificateType();
        type.setMemberId(cert.getMemberId());
        type.setActive(cert.isActive());
        type.setId(cert.getId());
        type.setStatus(cert.getStatus());
        type.setContents(cert.getBytes());

        return type;
    }

    private static CertRequest from(CertRequestType type) {
        return new CertRequest(getCertReqId(type), type.getMemberId(),
                type.getSubjectName());
    }

    private static CertRequestType from(CertRequest certRequest) {
        CertRequestType type = new CertRequestType();
        type.setId(certRequest.getId());
        type.setMemberId(certRequest.getMemberId());
        type.setSubjectName(certRequest.getSubjectName());

        return type;
    }

    private static String getCertId(CertificateType type) {
        if (type.getId() != null) {
            return type.getId();
        } else {
            try {
                 return calculateCertHexHash(type.getContents());
            } catch (Exception e) {
                log.error("Failed to calculate certificate hash for {}",  type);
                return SignerUtil.randomId();
            }
        }
    }

    private static String getCertReqId(CertRequestType type) {
        return type.getId() != null ? type.getId() : SignerUtil.randomId();
    }

    private static String getConfFileName() {
        return SystemProperties.getKeyConfFile();
    }
}
