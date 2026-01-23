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
package org.niis.xroad.signer.core.tokenmanager.module;

import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.signer.core.config.ModuleProperties;
import org.niis.xroad.signer.core.config.SignerProperties;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encapsulates module data read form configuration.
 * <p>
 * Each module specifies an UID, the pkcs#11 library path and other options specific to that module.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class ModuleConf {

    // Maps Type (UID) to ModuleType.
    private static final Map<String, ModuleType> MODULES = new HashMap<>();

    // Maps mechanism name to mechanism code of supported sign mechanisms.
    private static final Map<SignMechanism, Long> SUPPORTED_SIGN_MECHANISMS = createSupportedSignMechanismsMap();

    // Maps mechanism name to mechanism code of supported key allowed mechanisms.
    private static final Map<String, Long> SUPPORTED_KEY_ALLOWED_MECHANISMS = createSupportedKeyAllowedMechanismMap();

    // Module configuration fields.
    private static final String PUB_KEY_ATTRIBUTE_ALLOWED_MECHANISMS_PARAM = "pub_key_attribute_allowed_mechanisms";
    private static final String PRIV_KEY_ATTRIBUTE_ALLOWED_MECHANISMS_PARAM = "priv_key_attribute_allowed_mechanisms";

    private final SignerProperties signerProperties;

    private static Map<SignMechanism, Long> createSupportedSignMechanismsMap() {
        Map<SignMechanism, Long> mechanisms = new HashMap<>();
        mechanisms.put(SignMechanism.CKM_RSA_PKCS, PKCS11Constants.CKM_RSA_PKCS);
        mechanisms.put(SignMechanism.CKM_RSA_PKCS_PSS, PKCS11Constants.CKM_RSA_PKCS_PSS);
        mechanisms.put(SignMechanism.CKM_ECDSA, PKCS11Constants.CKM_ECDSA);

        return mechanisms;
    }

    private static Map<String, Long> createSupportedKeyAllowedMechanismMap() {
        Map<String, Long> mechanisms = new HashMap<>();
        mechanisms.put(PKCS11Constants.NAME_CKM_RSA_PKCS, PKCS11Constants.CKM_RSA_PKCS);
        mechanisms.put(PKCS11Constants.NAME_CKM_SHA256_RSA_PKCS, PKCS11Constants.CKM_SHA256_RSA_PKCS);
        mechanisms.put(PKCS11Constants.NAME_CKM_SHA384_RSA_PKCS, PKCS11Constants.CKM_SHA384_RSA_PKCS);
        mechanisms.put(PKCS11Constants.NAME_CKM_SHA512_RSA_PKCS, PKCS11Constants.CKM_SHA512_RSA_PKCS);
        mechanisms.put(PKCS11Constants.NAME_CKM_RSA_PKCS_PSS, PKCS11Constants.CKM_RSA_PKCS_PSS);
        mechanisms.put(PKCS11Constants.NAME_CKM_SHA256_RSA_PKCS_PSS, PKCS11Constants.CKM_SHA256_RSA_PKCS_PSS);
        mechanisms.put(PKCS11Constants.NAME_CKM_SHA384_RSA_PKCS_PSS, PKCS11Constants.CKM_SHA384_RSA_PKCS_PSS);
        mechanisms.put(PKCS11Constants.NAME_CKM_SHA512_RSA_PKCS_PSS, PKCS11Constants.CKM_SHA512_RSA_PKCS_PSS);
        mechanisms.put(PKCS11Constants.NAME_CKM_ECDSA, PKCS11Constants.CKM_ECDSA);
        mechanisms.put(PKCS11Constants.NAME_CKM_ECDSA_SHA256, PKCS11Constants.CKM_ECDSA_SHA256);
        mechanisms.put(PKCS11Constants.NAME_CKM_ECDSA_SHA384, PKCS11Constants.CKM_ECDSA_SHA384);
        mechanisms.put(PKCS11Constants.NAME_CKM_ECDSA_SHA512, PKCS11Constants.CKM_ECDSA_SHA512);

        return mechanisms;
    }

    /**
     * @return sign mechanism code, null in case not supported sign mechanism
     */
    public static Long getSupportedSignMechanismCode(SignMechanism signMechanismName) {
        return SUPPORTED_SIGN_MECHANISMS.get(signMechanismName);
    }

    /**
     * @return all modules
     */
    public Collection<ModuleType> getModules() {
        return MODULES.values();
    }

    private final AtomicBoolean firstLoad = new AtomicBoolean(true);

    /**
     * @return true for the first call, false for the rest (configuration values can't change at runtime)
     */
    public boolean hasChanged() {
        return firstLoad.getAndSet(false);
    }

    /**
     * Reloads the modules from the configuration.
     */
    void reload() {
        try {
            log.trace("Loading module configuration from properties");

            MODULES.clear();
            MODULES.put(SoftwareModuleType.TYPE, new SoftwareModuleType());

            if (signerProperties.modulesConfig() == null || !signerProperties.modulesConfig().isEmpty()) {
                signerProperties.modulesConfig().forEach(this::parseConfiguration);
            } else {
                log.warn("Module configuration not found");
            }
        } catch (Exception e) {
            log.error("Failed to load module conf", e);
        }
    }

    private void parseConfiguration(String module, ModuleProperties properties) {
        boolean enabled = properties.enabled();

        if (SoftwareModuleType.TYPE.equalsIgnoreCase(module)) {
            if (!enabled) {
                MODULES.remove(SoftwareModuleType.TYPE);
            }
            return;
        }

        if (!enabled) {
            return;
        }

        String library = properties.library();

        if (StringUtils.isBlank(library)) {
            log.error("No pkcs#11 library specified for module ({}), skipping...", module);
            return;
        }

        Boolean libraryCantCreateOsThreads = properties.libraryCantCreateOsThreads().orElse(null);
        Boolean osLockingOk = properties.osLockingOk().orElse(null);

        boolean verifyPin = properties.signVerifyPin();
        boolean batchSigning = properties.batchSigningEnabled();
        boolean readOnly = properties.readOnly();
        String tokenIdFormat = properties.tokenIdFormat();

        var rsaSignMechanismName = SignMechanism.valueOf(properties.rsaSignMechanism()
                .orElse(properties.signMechanism()));

        var ecSignMechanismName = SignMechanism.valueOf(properties.ecSignMechanism());

        var rsaSignMechanism = getSupportedSignMechanismCode(rsaSignMechanismName);
        var ecSignMechanism = getSupportedSignMechanismCode(ecSignMechanismName);

        if (rsaSignMechanism == null) {
            log.error("Not supported sign mechanism ({}) specified for module ({}), skipping...", rsaSignMechanismName, module);
            return;
        }

        if (ecSignMechanism == null) {
            log.error("Not supported sign mechanism ({}) specified for module ({}), skipping...", ecSignMechanismName, module);
            return;
        }

        PubKeyAttributes pubKeyAttributes = loadPubKeyAttributes(module, properties);
        PrivKeyAttributes privKeyAttributes = loadPrivKeyAttributes(module, properties);

        log.debug("Read module configuration (UID = {}, library = {}, library_cant_create_os_threads = {}"
                        + ", os_locking_ok = {}, token_id_format = {}, pin_verification_per_signing = {}, batch_signing = {}"
                        + ", rsa_sign_mechanism = {}, ec_sign_mechanism = {},pub_key_attributes = {}, priv_key_attributes = {})",
                module, library, libraryCantCreateOsThreads, osLockingOk, tokenIdFormat, verifyPin, batchSigning,
                rsaSignMechanismName, ecSignMechanismName, pubKeyAttributes, privKeyAttributes);

        if (MODULES.containsKey(module)) {
            log.warn("Module information already defined for {}, skipping...", module);
            return;
        }

        Set<Long> slotIds = properties.slotIds().orElse(Set.of());

        MODULES.put(module, new HardwareModuleType(
                module, library, libraryCantCreateOsThreads,
                osLockingOk, tokenIdFormat, verifyPin,
                batchSigning, readOnly, rsaSignMechanismName,
                ecSignMechanismName, privKeyAttributes, pubKeyAttributes,
                slotIds));
    }

    private PubKeyAttributes loadPubKeyAttributes(String module, ModuleProperties properties) {
        PubKeyAttributes attributes = new PubKeyAttributes();

        // Default values for backward compatibility are used.
        attributes.setEncrypt(properties.pubKeyAttributeEncrypt());
        attributes.setVerify(properties.pubKeyAttributeVerify());
        properties.pubKeyAttributeVerifyRecover().ifPresent(attributes::setVerifyRecover);
        properties.pubKeyAttributeWrap().ifPresent(attributes::setWrap);
        properties.pubKeyAttributeTrusted().ifPresent(attributes::setTrusted);

        attributes.setAllowedMechanisms(properties.pubKeyAttributeAllowedMechanisms()
                .map(mechanisms -> loadAllowedKeyUsageMechanisms(mechanisms, PUB_KEY_ATTRIBUTE_ALLOWED_MECHANISMS_PARAM, module))
                .orElse(Set.of()));

        return attributes;
    }

    private PrivKeyAttributes loadPrivKeyAttributes(String module, ModuleProperties properties) {
        PrivKeyAttributes attributes = new PrivKeyAttributes();

        // Default values for backward compatibility are used.
        attributes.setSensitive(properties.privKeyAttributeSensitive());
        attributes.setDecrypt(properties.privKeyAttributeDecrypt());
        attributes.setSign(properties.privKeyAttributeSign());
        properties.privKeyAttributeSignRecover().ifPresent(attributes::setSignRecover);
        properties.privKeyAttributeUnwrap().ifPresent(attributes::setUnwrap);
        properties.privKeyAttributeExtractable().ifPresent(attributes::setExtractable);
        properties.privKeyAttributeAlwaysSensitive().ifPresent(attributes::setAlwaysSensitive);
        properties.privKeyAttributeNeverExtractable().ifPresent(attributes::setNeverExtractable);
        properties.privKeyAttributeWrapWithTrusted().ifPresent(attributes::setWrapWithTrusted);

        attributes.setAllowedMechanisms(properties.privKeyAttributeAllowedMechanisms()
                .map(mechanisms -> loadAllowedKeyUsageMechanisms(mechanisms, PRIV_KEY_ATTRIBUTE_ALLOWED_MECHANISMS_PARAM, module))
                .orElse(Set.of()));

        return attributes;
    }

    private static Set<Long> loadAllowedKeyUsageMechanisms(List<String> mechanisms, String key, String module) {
        Set<Long> allowedMechanism = new HashSet<>();

        for (String m : mechanisms) {
            Long mechanism = SUPPORTED_KEY_ALLOWED_MECHANISMS.get(StringUtils.strip(m));

            if (mechanism != null) {
                allowedMechanism.add(mechanism);
            } else {
                throw new ConfigurationRuntimeException(
                        "Unsupported value '%s' of '%s' for module (%s), skipping...".formatted(m, key, module));
            }
        }

        return allowedMechanism;
    }

}
