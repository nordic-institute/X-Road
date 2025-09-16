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
package org.niis.xroad.signer.core.tokenmanager.token;

import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import iaik.pkcs.pkcs11.Mechanism;
import iaik.pkcs.pkcs11.Module;
import iaik.pkcs.pkcs11.TokenException;
import iaik.pkcs.pkcs11.TokenInfo;
import iaik.pkcs.pkcs11.objects.Key;
import iaik.pkcs.pkcs11.objects.PrivateKey;
import iaik.pkcs.pkcs11.objects.PublicKey;
import iaik.pkcs.pkcs11.objects.X509PublicKeyCertificate;
import iaik.pkcs.pkcs11.parameters.RSAPkcsParameters.MessageGenerationFunctionType;
import iaik.pkcs.pkcs11.parameters.RSAPkcsPssParameters;
import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;
import jakarta.xml.bind.DatatypeConverter;
import lombok.NoArgsConstructor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.core.tokenmanager.module.ModuleConf;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.niis.xroad.common.core.exception.ErrorCode.HW_MODULE_INTERNAL_ERROR;

/**
 * Utility methods for hardware token.
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class HardwareTokenUtil {

    private static final int MAX_OBJECTS = 64;

    /**
     * Returns the module instance.
     *
     * @param libraryPath the pkcs11 library path
     * @return the module instance
     */
    public static Module moduleGetInstance(String libraryPath) throws IOException {
        return Module.getInstance(libraryPath);
    }

    static PrivateKey findPrivateKey(ManagedPKCS11Session session, String keyId, Set<Long> allowedMechanisms) {
        var template = new PrivateKey();
        template.getId().setByteArrayValue(toBinaryKeyId(keyId));

        setAllowedMechanisms(template, allowedMechanisms);

        return find(template, session);
    }

    static List<PrivateKey> findPrivateKeys(ManagedPKCS11Session session, Set<Long> allowedMechanisms) {
        var template = new PrivateKey();
        template.getSign().setBooleanValue(true);

        setAllowedMechanisms(template, allowedMechanisms);

        return find(template, session, MAX_OBJECTS);
    }

    static List<PublicKey> findPublicKeys(ManagedPKCS11Session session, Set<Long> allowedMechanisms) {
        var template = new PublicKey();
        template.getVerify().setBooleanValue(true);

        setAllowedMechanisms(template, allowedMechanisms);

        return find(template, session, MAX_OBJECTS);
    }

    static PublicKey findPublicKey(ManagedPKCS11Session session, String keyId, Set<Long> allowedMechanisms) {
        var template = new PublicKey();
        template.getId().setByteArrayValue(toBinaryKeyId(keyId));

        setAllowedMechanisms(template, allowedMechanisms);

        return find(template, session);
    }

    static List<X509PublicKeyCertificate> findPublicKeyCertificates(ManagedPKCS11Session session) {
        return find(new X509PublicKeyCertificate(), session, MAX_OBJECTS);
    }

    public static void setAllowedMechanisms(Key key, Set<Long> mechanisms) {
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
    static <T extends iaik.pkcs.pkcs11.objects.Object> List<T> find(T template, ManagedPKCS11Session session, int maxObjectCount) {
        try {
            iaik.pkcs.pkcs11.objects.Object[] tmpArray;

            List<T> foundObjects = new ArrayList<>();

            var rawSession = session.get();
            rawSession.findObjectsInit(template);
            do {
                tmpArray = rawSession.findObjects(maxObjectCount);
                for (iaik.pkcs.pkcs11.objects.Object object : tmpArray) {
                    foundObjects.add((T) object);
                }
            } while (tmpArray.length != 0);

            rawSession.findObjectsFinal();
            return foundObjects;
        } catch (TokenException tokenException) {
            throw XrdRuntimeException.systemException(HW_MODULE_INTERNAL_ERROR).cause(tokenException).build();
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends iaik.pkcs.pkcs11.objects.Object> T find(T template, ManagedPKCS11Session session) {
        try {
            T foundObject = null;

            var rawSession = session.get();
            rawSession.findObjectsInit(template);

            iaik.pkcs.pkcs11.objects.Object[] tmpArray = rawSession.findObjects(1);
            if (tmpArray.length > 0) {
                foundObject = (T) tmpArray[0];
            }

            rawSession.findObjectsFinal();
            return foundObject;
        } catch (TokenException tokenException) {
            throw XrdRuntimeException.systemException(HW_MODULE_INTERNAL_ERROR).cause(tokenException).build();
        }
    }

    static Map<SignAlgorithm, Mechanism> createSignMechanisms(SignMechanism signMechanismName) {
        Map<SignAlgorithm, Mechanism> mechanismsByHashAlgorithmId = new HashMap<>();

        switch (signMechanismName.name()) {
            case PKCS11Constants.NAME_CKM_RSA_PKCS -> {
                Mechanism mechanism = Mechanism.get(ModuleConf.getSupportedSignMechanismCode(signMechanismName));

                mechanismsByHashAlgorithmId.put(SignAlgorithm.SHA1_WITH_RSA, mechanism);
                mechanismsByHashAlgorithmId.put(SignAlgorithm.SHA256_WITH_RSA, mechanism);
                mechanismsByHashAlgorithmId.put(SignAlgorithm.SHA384_WITH_RSA, mechanism);
                mechanismsByHashAlgorithmId.put(SignAlgorithm.SHA512_WITH_RSA, mechanism);
            }
            case PKCS11Constants.NAME_CKM_RSA_PKCS_PSS -> {
                mechanismsByHashAlgorithmId.put(SignAlgorithm.SHA256_WITH_RSA_AND_MGF1,
                        createRsaPkcsPssMechanism(PKCS11Constants.CKM_SHA256));
                mechanismsByHashAlgorithmId.put(SignAlgorithm.SHA384_WITH_RSA_AND_MGF1,
                        createRsaPkcsPssMechanism(PKCS11Constants.CKM_SHA384));
                mechanismsByHashAlgorithmId.put(SignAlgorithm.SHA512_WITH_RSA_AND_MGF1,
                        createRsaPkcsPssMechanism(PKCS11Constants.CKM_SHA512));
            }
            case PKCS11Constants.NAME_CKM_ECDSA -> {
                Mechanism mechanism = Mechanism.get(ModuleConf.getSupportedSignMechanismCode(signMechanismName));
                mechanismsByHashAlgorithmId.put(SignAlgorithm.SHA1_WITH_ECDSA, mechanism);
                mechanismsByHashAlgorithmId.put(SignAlgorithm.SHA256_WITH_ECDSA, mechanism);
                mechanismsByHashAlgorithmId.put(SignAlgorithm.SHA384_WITH_ECDSA, mechanism);
                mechanismsByHashAlgorithmId.put(SignAlgorithm.SHA512_WITH_ECDSA, mechanism);
            }
            default -> throw new IllegalArgumentException("Not supported sign mechanism: " + signMechanismName.name());
        }

        return Map.copyOf(mechanismsByHashAlgorithmId);
    }

    private static Mechanism createRsaPkcsPssMechanism(long hashMechanism) {
        Mechanism mechanism = Mechanism.get(PKCS11Constants.CKM_RSA_PKCS_PSS);

        Mechanism hashAlgorithm = Mechanism.get(hashMechanism);
        long maskGenerationFunction;
        long saltLength;

        if (hashMechanism == PKCS11Constants.CKM_SHA512) {
            maskGenerationFunction = MessageGenerationFunctionType.SHA512;
            saltLength = Digests.SHA512_DIGEST_LENGTH;
        } else if (hashMechanism == PKCS11Constants.CKM_SHA384) {
            maskGenerationFunction = MessageGenerationFunctionType.SHA384;
            saltLength = Digests.SHA384_DIGEST_LENGTH;
        } else if (hashMechanism == PKCS11Constants.CKM_SHA256) {
            maskGenerationFunction = MessageGenerationFunctionType.SHA256;
            saltLength = Digests.SHA256_DIGEST_LENGTH;
        } else {
            throw new IllegalArgumentException("Not supported hash mechanism");
        }

        mechanism.setParameters(new RSAPkcsPssParameters(hashAlgorithm, maskGenerationFunction, saltLength));

        return mechanism;
    }

    private static byte[] toBinaryKeyId(String keyId) {
        return DatatypeConverter.parseHexBinary(keyId);
    }
}
