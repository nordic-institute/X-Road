package ee.cyber.sdsb.signer.tokenmanager.token;

import iaik.pkcs.pkcs11.Module;
import iaik.pkcs.pkcs11.Session;
import iaik.pkcs.pkcs11.TokenException;
import iaik.pkcs.pkcs11.TokenInfo;
import iaik.pkcs.pkcs11.objects.RSAPrivateKey;
import iaik.pkcs.pkcs11.objects.RSAPublicKey;
import iaik.pkcs.pkcs11.objects.X509PublicKeyCertificate;
import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.DatatypeConverter;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.signer.protocol.dto.TokenStatusInfo;
import ee.cyber.sdsb.signer.tokenmanager.module.ModuleInstanceProvider;
import ee.cyber.sdsb.signer.util.SignerUtil;

/**
 * Utility methods for hardware token.
 */
public final class HardwareTokenUtil {

    private static final int MAX_OBJECTS = 64;

    private HardwareTokenUtil() {
    }

    /**
     * Returns the module instance. The module provider class can be specified
     * by system parameter 'ee.cyber.sdsb.signer.module-instance-provider'.
     * @param libraryPath the pkcs11 library path
     * @return the module instance
     * @throws Exception if an error occurs
     */
    public static Module moduleGetInstance(String libraryPath)
            throws Exception {
        String providerClass = System.getProperty(
                SystemProperties.SIGNER_MODULE_INSTANCE_PROVIDER);
        if (providerClass != null) {
            Class<?> cl = Class.forName(providerClass);
            if (ModuleInstanceProvider.class.isAssignableFrom(cl)) {
                ModuleInstanceProvider provider =
                        (ModuleInstanceProvider) cl.newInstance();
                return provider.getInstance(libraryPath);
            } else {
                throw new RuntimeException(
                        "Invalid module provider class (" + cl
                                + "), must be subclass of "
                                + ModuleInstanceProvider.class);
            }
        }

        return Module.getInstance(libraryPath);
    }

    static void login(Session session, char[] password) throws Exception {
        try {
            session.login(Session.UserType.USER, password);
        } catch (PKCS11Exception ex) {
            if (ex.getErrorCode()
                    != PKCS11Constants.CKR_USER_ALREADY_LOGGED_IN) {
                throw ex;
            }
        }
    }

    static void logout(Session session) throws Exception {
        try {
            session.logout();
        } catch (PKCS11Exception ex) {
            if (ex.getErrorCode() != PKCS11Constants.CKR_USER_NOT_LOGGED_IN) {
                throw ex;
            }
        }
    }

    static RSAPrivateKey findPrivateKey(Session session, String keyId)
            throws Exception {
        RSAPrivateKey template = new RSAPrivateKey();
        template.getId().setByteArrayValue(toBinaryKeyId(keyId));
        return find(template, session);
    }

    static List<RSAPrivateKey> findPrivateKeys(Session session)
            throws Exception {
        RSAPrivateKey template = new RSAPrivateKey();
        template.getSign().setBooleanValue(true);
        return find(template, session, MAX_OBJECTS);
    }

    static List<RSAPublicKey> findPublicKeys(Session session)
            throws Exception {
        RSAPublicKey template = new RSAPublicKey();
        template.getVerify().setBooleanValue(true);
        return find(template, session, MAX_OBJECTS);
    }

    static RSAPublicKey findPublicKey(Session session, String keyId)
            throws Exception {
        RSAPublicKey template = new RSAPublicKey();
        template.getId().setByteArrayValue(toBinaryKeyId(keyId));
        return find(template, session);
    }

    static List<X509PublicKeyCertificate> findPublicKeyCertificates(
            Session session) throws Exception {
        return find(new X509PublicKeyCertificate(), session, MAX_OBJECTS);
    }

    static byte[] generateX509PublicKey(RSAPublicKey rsaPublicKey)
            throws Exception {
        BigInteger modulus = new BigInteger(1,
                rsaPublicKey.getModulus().getByteArrayValue());
        BigInteger publicExponent = new BigInteger(1,
                rsaPublicKey.getPublicExponent().getByteArrayValue());
        return CryptoUtils.generateX509PublicKey(modulus, publicExponent);
    }

    static void setPrivateKeyAttributes(RSAPrivateKey keyTemplate) {
        keyTemplate.getSensitive().setBooleanValue(Boolean.TRUE);
        keyTemplate.getToken().setBooleanValue(Boolean.TRUE);
        keyTemplate.getPrivate().setBooleanValue(Boolean.TRUE);

        keyTemplate.getSign().setBooleanValue(Boolean.TRUE);
        keyTemplate.getDecrypt().setBooleanValue(Boolean.TRUE);
    }

    static void setPublicKeyAttributes(RSAPublicKey keyTemplate) {
        keyTemplate.getModulusBits().setLongValue(SignerUtil.KEY_SIZE);
        byte[] publicExponentBytes = {0x01, 0x00, 0x01}; // 2^16 + 1
        keyTemplate.getPublicExponent().setByteArrayValue(publicExponentBytes);
        keyTemplate.getToken().setBooleanValue(Boolean.TRUE);

        keyTemplate.getVerify().setBooleanValue(Boolean.TRUE);
        keyTemplate.getEncrypt().setBooleanValue(Boolean.TRUE);
    }

    static TokenStatusInfo getTokenStatus(TokenInfo tokenInfo, long errorCode) {
        if (tokenInfo.isUserPinLocked()
                || errorCode == PKCS11Constants.CKR_PIN_LOCKED) {
            return TokenStatusInfo.USER_PIN_LOCKED;
        }

        if (tokenInfo.isUserPinFinalTry()) {
            return TokenStatusInfo.USER_PIN_FINAL_TRY;
        }

        if (tokenInfo.isUserPinCountLow()) {
            return TokenStatusInfo.USER_PIN_COUNT_LOW;
        }

        if (errorCode == PKCS11Constants.CKR_PIN_INCORRECT) {
            return TokenStatusInfo.USER_PIN_INCORRECT;
        }

        if (errorCode == PKCS11Constants.CKR_PIN_INVALID) {
            return TokenStatusInfo.USER_PIN_INVALID;
        }

        if (errorCode == PKCS11Constants.CKR_PIN_EXPIRED) {
            return TokenStatusInfo.USER_PIN_EXPIRED;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    static <T extends iaik.pkcs.pkcs11.objects.Object> List<T> find(
            T template, Session session, int maxObjectCount)
                    throws TokenException {
        iaik.pkcs.pkcs11.objects.Object[] tmpArray;

        List<T> foundObjects = new ArrayList<>();

        session.findObjectsInit(template);
        do {
            tmpArray = session.findObjects(maxObjectCount);
            for (iaik.pkcs.pkcs11.objects.Object object: tmpArray) {
                foundObjects.add((T) object);
            }
        } while (tmpArray.length != 0);

        session.findObjectsFinal();
        return foundObjects;
    }

    @SuppressWarnings("unchecked")
    static <T extends iaik.pkcs.pkcs11.objects.Object> T find(
            T template, Session session) throws TokenException {
        T foundObject = null;

        session.findObjectsInit(template);

        iaik.pkcs.pkcs11.objects.Object[] tmpArray = session.findObjects(1);
        if (tmpArray.length > 0) {
            foundObject = (T) tmpArray[0];
        }

        session.findObjectsFinal();
        return foundObject;
    }

    private static byte[] toBinaryKeyId(String keyId) {
        return DatatypeConverter.parseHexBinary(keyId);
    }
}
