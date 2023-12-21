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
package ee.ria.xroad.signer.tokenmanager.token;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;
import ee.ria.xroad.signer.tokenmanager.module.ModuleInstanceProvider;
import ee.ria.xroad.signer.tokenmanager.module.PrivKeyAttributes;
import ee.ria.xroad.signer.tokenmanager.module.PubKeyAttributes;

import iaik.pkcs.pkcs11.Mechanism;
import iaik.pkcs.pkcs11.Module;
import iaik.pkcs.pkcs11.Session;
import iaik.pkcs.pkcs11.TokenException;
import iaik.pkcs.pkcs11.TokenInfo;
import iaik.pkcs.pkcs11.objects.Key;
import iaik.pkcs.pkcs11.objects.RSAPrivateKey;
import iaik.pkcs.pkcs11.objects.RSAPublicKey;
import iaik.pkcs.pkcs11.objects.X509PublicKeyCertificate;
import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import jakarta.xml.bind.DatatypeConverter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for hardware token.
 */
public final class HardwareTokenUtil {

    private static final int MAX_OBJECTS = 64;

    private HardwareTokenUtil() {
    }

    /**
     * Returns the module instance. The module provider class can be specified
     * by system parameter 'ee.ria.xroad.signer.module-instance-provider'.
     * @param libraryPath the pkcs11 library path
     * @return the module instance
     * @throws Exception if an error occurs
     */
    public static Module moduleGetInstance(String libraryPath) throws Exception {
        String providerClass = System.getProperty(SystemProperties.SIGNER_MODULE_INSTANCE_PROVIDER);
        if (providerClass != null) {
            Class<?> cl = Class.forName(providerClass);

            if (ModuleInstanceProvider.class.isAssignableFrom(cl)) {
                ModuleInstanceProvider provider = (ModuleInstanceProvider) cl.newInstance();

                return provider.getInstance(libraryPath);
            } else {
                throw new RuntimeException("Invalid module provider class (" + cl + "), must be subclass of "
                        + ModuleInstanceProvider.class);
            }
        }

        return Module.getInstance(libraryPath);
    }

    static void login(Session session, char[] password) throws Exception {
        try {
            session.login(Session.UserType.USER, password);
        } catch (PKCS11Exception ex) {
            if (ex.getErrorCode() != PKCS11Constants.CKR_USER_ALREADY_LOGGED_IN) {
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

    static RSAPrivateKey findPrivateKey(Session session, String keyId, Set<Long> allowedMechanisms) throws Exception {
        RSAPrivateKey template = new RSAPrivateKey();
        template.getId().setByteArrayValue(toBinaryKeyId(keyId));

        setAllowedMechanisms(template, allowedMechanisms);

        return find(template, session);
    }

    static List<RSAPrivateKey> findPrivateKeys(Session session, Set<Long> allowedMechanisms) throws Exception {
        RSAPrivateKey template = new RSAPrivateKey();
        template.getSign().setBooleanValue(true);

        setAllowedMechanisms(template, allowedMechanisms);

        return find(template, session, MAX_OBJECTS);
    }

    static List<RSAPublicKey> findPublicKeys(Session session, Set<Long> allowedMechanisms) throws Exception {
        RSAPublicKey template = new RSAPublicKey();
        template.getVerify().setBooleanValue(true);

        setAllowedMechanisms(template, allowedMechanisms);

        return find(template, session, MAX_OBJECTS);
    }

    static RSAPublicKey findPublicKey(Session session, String keyId, Set<Long> allowedMechanisms) throws Exception {
        RSAPublicKey template = new RSAPublicKey();
        template.getId().setByteArrayValue(toBinaryKeyId(keyId));

        setAllowedMechanisms(template, allowedMechanisms);

        return find(template, session);
    }

    static List<X509PublicKeyCertificate> findPublicKeyCertificates(Session session) throws Exception {
        return find(new X509PublicKeyCertificate(), session, MAX_OBJECTS);
    }

    static byte[] generateX509PublicKey(RSAPublicKey rsaPublicKey) throws Exception {
        BigInteger modulus = new BigInteger(1, rsaPublicKey.getModulus().getByteArrayValue());
        BigInteger publicExponent = new BigInteger(1, rsaPublicKey.getPublicExponent().getByteArrayValue());

        return CryptoUtils.generateX509PublicKey(modulus, publicExponent);
    }

    static void setPrivateKeyAttributes(RSAPrivateKey keyTemplate, PrivKeyAttributes attributes) {
        // Private key is a token object (not a session object).
        keyTemplate.getToken().setBooleanValue(Boolean.TRUE);
        // This is a private object.
        keyTemplate.getPrivate().setBooleanValue(Boolean.TRUE);

        if (attributes.getSensitive() != null) {
            keyTemplate.getSensitive().setBooleanValue(attributes.getSensitive());
        }

        if (attributes.getDecrypt() != null) {
            keyTemplate.getDecrypt().setBooleanValue(attributes.getDecrypt());
        }

        if (attributes.getSign() != null) {
            keyTemplate.getSign().setBooleanValue(attributes.getSign());
        }

        if (attributes.getSignRecover() != null) {
            keyTemplate.getSignRecover().setBooleanValue(attributes.getSignRecover());
        }

        if (attributes.getUnwrap() != null) {
            keyTemplate.getUnwrap().setBooleanValue(attributes.getUnwrap());
        }

        if (attributes.getExtractable() != null) {
            keyTemplate.getExtractable().setBooleanValue(attributes.getExtractable());
        }

        if (attributes.getAlwaysSensitive() != null) {
            keyTemplate.getAlwaysSensitive().setBooleanValue(attributes.getAlwaysSensitive());
        }

        if (attributes.getNeverExtractable() != null) {
            keyTemplate.getNeverExtractable().setBooleanValue(attributes.getNeverExtractable());
        }

        if (attributes.getWrapWithTrusted() != null) {
            keyTemplate.getWrapWithTrusted().setBooleanValue(attributes.getWrapWithTrusted());
        }

        if (attributes.getAllowedMechanisms() != null) {
            setAllowedMechanisms(keyTemplate, attributes.getAllowedMechanisms());
        }
    }

    static void setPublicKeyAttributes(RSAPublicKey keyTemplate, PubKeyAttributes attributes) {
        keyTemplate.getModulusBits().setLongValue((long) SystemProperties.getSignerKeyLength());

        byte[] publicExponentBytes = {0x01, 0x00, 0x01}; // 2^16 + 1
        keyTemplate.getPublicExponent().setByteArrayValue(publicExponentBytes);

        // Public key is a token object (not a session object).
        keyTemplate.getToken().setBooleanValue(Boolean.TRUE);

        if (attributes.getEncrypt() != null) {
            keyTemplate.getEncrypt().setBooleanValue(attributes.getEncrypt());
        }

        if (attributes.getVerify() != null) {
            keyTemplate.getVerify().setBooleanValue(attributes.getVerify());
        }

        if (attributes.getWrap() != null) {
            keyTemplate.getWrap().setBooleanValue(attributes.getWrap());
        }

        if (attributes.getVerifyRecover() != null) {
            keyTemplate.getVerifyRecover().setBooleanValue(attributes.getVerifyRecover());
        }

        if (attributes.getTrusted() != null) {
            keyTemplate.getTrusted().setBooleanValue(attributes.getTrusted());
        }

        if (attributes.getAllowedMechanisms() != null) {
            setAllowedMechanisms(keyTemplate, attributes.getAllowedMechanisms());
        }
    }

    private static void setAllowedMechanisms(Key key, Set<Long> mechanisms) {
        if (mechanisms.isEmpty()) {
            return;
        }

        Mechanism[] allowedMechanisms = new Mechanism[mechanisms.size()];
        int index = 0;

        for (Long m : mechanisms) {
            allowedMechanisms[index] = new Mechanism(m);
            index++;
        }

        key.getAllowedMechanisms().setMechanismAttributeArrayValue(allowedMechanisms);
    }

    static TokenStatusInfo getTokenStatus(TokenInfo tokenInfo, long errorCode) {
        if (tokenInfo.isUserPinLocked() || errorCode == PKCS11Constants.CKR_PIN_LOCKED) {
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
