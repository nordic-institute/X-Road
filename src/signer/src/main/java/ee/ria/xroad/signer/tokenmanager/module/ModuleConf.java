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
package ee.ria.xroad.signer.tokenmanager.module;

import ee.ria.xroad.common.util.FileContentChangeChecker;

import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.SubnodeConfiguration;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.commons.lang3.StringUtils;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.SystemProperties.getDeviceConfFile;

/**
 * Encapsulates module data read form the external configuration file.
 *
 * Each module specifies an UID, the pkcs#11 library path and other options specific to that module.
 */
@Slf4j
public final class ModuleConf {

    /** The type used to identify software keys in key configuration. */
    private static final String SOFTKEY_TYPE = "softToken";

    // Maps Type (UID) to ModuleType.
    private static final Map<String, ModuleType> MODULES = new HashMap<>();

    private static final String DEFAULT_TOKEN_ID_FORMAT = "{moduleType}{slotIndex}{serialNumber}{label}";

    // Maps mechanism name to mechanism code of supported sign mechanisms.
    private static final Map<String, Long> SUPPORTED_SIGN_MECHANISMS = createSupportedSignMechanismsMap();

    // Maps mechanism name to mechanism code of supported key allowed mechanisms.
    private static final Map<String, Long> SUPPORTED_KEY_ALLOWED_MECHANISMS = createSupportedKeyAllowedMechanismMap();

    private static final String DEFAULT_SIGN_MECHANISM_NAME = PKCS11Constants.NAME_CKM_RSA_PKCS;

    // Module configuration fields.
    private static final String ENABLED_PARAM = "enabled";
    private static final String LIBRARY_PARAM = "library";
    private static final String LIBRARY_CANT_CREATE_OS_THREADS_PARAM = "library_cant_create_os_threads";
    private static final String OS_LOCKING_OK_PARAM = "os_locking_ok";
    private static final String SIGN_VERIFY_PIN_PARAM = "sign_verify_pin";
    private static final String BATCH_SIGNING_ENABLED_PARAM = "batch_signing_enabled";
    private static final String READ_ONLY_PARAM = "read_only";
    private static final String TOKEN_ID_FORMAT_PARAM = "token_id_format";
    private static final String SIGN_MECHANISM_PARAM = "sign_mechanism";
    private static final String PUB_KEY_ATTRIBUTE_ENCRYPT_PARAM = "pub_key_attribute_encrypt";
    private static final String PUB_KEY_ATTRIBUTE_VERIFY_PARAM = "pub_key_attribute_verify";
    private static final String PUB_KEY_ATTRIBUTE_VERIFY_RECOVER_PARAM = "pub_key_attribute_verify_recover";
    private static final String PUB_KEY_ATTRIBUTE_WRAP_PARAM = "pub_key_attribute_wrap";
    private static final String PUB_KEY_ATTRIBUTE_TRUSTED_PARAM = "pub_key_attribute_trusted";
    private static final String PUB_KEY_ATTRIBUTE_ALLOWED_MECHANISMS_PARAM = "pub_key_attribute_allowed_mechanisms";
    private static final String PRIV_KEY_ATTRIBUTE_SENSITIVE_PARAM = "priv_key_attribute_sensitive";
    private static final String PRIV_KEY_ATTRIBUTE_DECRYPT_PARAM = "priv_key_attribute_decrypt";
    private static final String PRIV_KEY_ATTRIBUTE_SIGN_PARAM = "priv_key_attribute_sign";
    private static final String PRIV_KEY_ATTRIBUTE_SIGN_RECOVER_PARAM = "priv_key_attribute_sign_recover";
    private static final String PRIV_KEY_ATTRIBUTE_UNWRAP_PARAM = "priv_key_attribute_unwrap";
    private static final String PRIV_KEY_ATTRIBUTE_EXTRACTABLE_PARAM = "priv_key_attribute_extractable";
    private static final String PRIV_KEY_ATTRIBUTE_ALWAYS_SENSITIVE_PARAM = "priv_key_attribute_always_sensitive";
    private static final String PRIV_KEY_ATTRIBUTE_NEVER_EXTRACTABLE_PARAM = "priv_key_attribute_never_extractable";
    private static final String PRIV_KEY_ATTRIBUTE_WRAP_WITH_TRUSTED_PARAM = "priv_key_attribute_wrap_with_trusted";
    private static final String PRIV_KEY_ATTRIBUTE_ALLOWED_MECHANISMS_PARAM = "priv_key_attribute_allowed_mechanisms";
    private static final String SLOT_IDS_PARAM = "slot_ids";

    private static FileContentChangeChecker changeChecker = null;

    private static Map<String, Long> createSupportedSignMechanismsMap() {
        Map<String, Long> mechanisms = new HashMap<>();
        mechanisms.put(PKCS11Constants.NAME_CKM_RSA_PKCS, PKCS11Constants.CKM_RSA_PKCS);
        mechanisms.put(PKCS11Constants.NAME_CKM_RSA_PKCS_PSS, PKCS11Constants.CKM_RSA_PKCS_PSS);

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

        return mechanisms;
    }

    private ModuleConf() {
    }

    /**
     * @return sign mechanism code, null in case not supported sign mechanism
     */
    public static Long getSupportedSignMechanismCode(String signMechanismName) {
        return SUPPORTED_SIGN_MECHANISMS.get(signMechanismName);
    }

    /**
     * @return all modules
     */
    static Collection<ModuleType> getModules() {
        return MODULES.values();
    }

    /**
     * @return true, if the configuration file has changed (modified on disk)
     */
    static boolean hasChanged() {
        try {
            if (changeChecker == null) {
                changeChecker = new FileContentChangeChecker(getDeviceConfFile());

                return true;
            }

            return changeChecker.hasChanged();
        } catch (Exception e) {
            log.error("Failed to create content change checker or calculate check sum", e);

            return true;
        }
    }

    /**
     * Reloads the modules from the configuration.
     */
    static void reload() {
        try {
            reload(getDeviceConfFile());
        } catch (Exception e) {
            log.error("Failed to load module conf", e);
        }
    }

    private static void reload(String fileName) throws Exception {
        log.trace("Loading module configuration from '{}'", fileName);

        MODULES.clear();
        MODULES.put(SoftwareModuleType.TYPE, new SoftwareModuleType());

        INIConfiguration conf = new INIConfiguration();
        conf.setListDelimiterHandler(new DefaultListDelimiterHandler(','));
        try (Reader reader = Files.newBufferedReader(Paths.get(fileName), StandardCharsets.UTF_8)) {
            conf.read(reader);
        }

        for (String uid : conf.getSections()) {
            if (StringUtils.isBlank(uid)) {
                log.error("No UID specified for module, skipping...");

                continue;
            }

            try {
                parseSection(uid, conf.getSection(uid));
            } catch (ConfigurationRuntimeException e) {
                log.error("Parse section failed with", e);
            }
        }
    }

    private static void parseSection(String uid, SubnodeConfiguration section) {
        boolean enabled = section.getBoolean(ENABLED_PARAM, true);

        if (SOFTKEY_TYPE.equalsIgnoreCase(uid)) {
            if (!enabled) {
                MODULES.remove(SoftwareModuleType.TYPE);
            }

            return;
        }

        if (!enabled) {
            return;
        }

        String library = section.getString(LIBRARY_PARAM);

        if (StringUtils.isBlank(library)) {
            log.error("No pkcs#11 library specified for module ({}), skipping...", uid);

            return;
        }

        Boolean libraryCantCreateOsThreads = getBoolean(section, LIBRARY_CANT_CREATE_OS_THREADS_PARAM, null);
        Boolean osLockingOk = getBoolean(section, OS_LOCKING_OK_PARAM, null);

        boolean verifyPin = getBoolean(section, SIGN_VERIFY_PIN_PARAM, false);
        boolean batchSigning = getBoolean(section, BATCH_SIGNING_ENABLED_PARAM, true);
        boolean readOnly = getBoolean(section, READ_ONLY_PARAM, false);
        String tokenIdFormat = section.getString(TOKEN_ID_FORMAT_PARAM);

        if (StringUtils.isBlank(tokenIdFormat)) {
            tokenIdFormat = DEFAULT_TOKEN_ID_FORMAT;
        }

        String signMechanismName = section.getString(SIGN_MECHANISM_PARAM);

        if (StringUtils.isBlank(signMechanismName)) {
            signMechanismName = DEFAULT_SIGN_MECHANISM_NAME;
        }

        Long signMechanism = getSupportedSignMechanismCode(signMechanismName);

        if (signMechanism == null) {
            log.error("Not supported sign mechanism ({}) specified for module ({}), skipping...",
                    signMechanismName, uid);

            return;
        }

        PubKeyAttributes pubKeyAttributes = loadPubKeyAttributes(section);
        PrivKeyAttributes privKeyAttributes = loadPrivKeyAttributes(section);

        log.debug("Read module configuration (UID = {}, library = {}, library_cant_create_os_threads = {}"
                + ", os_locking_ok = {}, token_id_format = {}, pin_verification_per_signing = {}, batch_signing = {}"
                + ", sign_mechanism = {}, pub_key_attributes = {}, priv_key_attributes = {})",
                uid, library, libraryCantCreateOsThreads, osLockingOk, tokenIdFormat, verifyPin, batchSigning,
                signMechanismName, pubKeyAttributes, privKeyAttributes);

        if (MODULES.containsKey(uid)) {
            log.warn("Module information already defined for {}, skipping...", uid);

            return;
        }

        List<String> slotIdStrings = Arrays.asList(getStringArray(section, SLOT_IDS_PARAM));
        Set<Long> slotIds = slotIdStrings.stream().map(String::trim).map(Long::parseLong).collect(Collectors.toSet());

        MODULES.put(uid, new HardwareModuleType(uid, library, libraryCantCreateOsThreads, osLockingOk, tokenIdFormat,
                verifyPin, batchSigning, readOnly, signMechanismName, privKeyAttributes, pubKeyAttributes, slotIds));
    }

    private static PubKeyAttributes loadPubKeyAttributes(SubnodeConfiguration section) {
        PubKeyAttributes attributes = new PubKeyAttributes();

        // Default values for backward compatibility are used.
        attributes.setEncrypt(getBoolean(section, PUB_KEY_ATTRIBUTE_ENCRYPT_PARAM, true));
        attributes.setVerify(getBoolean(section, PUB_KEY_ATTRIBUTE_VERIFY_PARAM, true));
        attributes.setVerifyRecover(getBoolean(section, PUB_KEY_ATTRIBUTE_VERIFY_RECOVER_PARAM, null));
        attributes.setWrap(getBoolean(section, PUB_KEY_ATTRIBUTE_WRAP_PARAM, null));
        attributes.setTrusted(getBoolean(section, PUB_KEY_ATTRIBUTE_TRUSTED_PARAM, null));
        attributes.setAllowedMechanisms(loadAllowedKeyUsageMechanisms(
                section, PUB_KEY_ATTRIBUTE_ALLOWED_MECHANISMS_PARAM));

        return attributes;
    }

    private static PrivKeyAttributes loadPrivKeyAttributes(SubnodeConfiguration section) {
        PrivKeyAttributes attributes = new PrivKeyAttributes();

        // Default values for backward compatibility are used.
        attributes.setSensitive(getBoolean(section, PRIV_KEY_ATTRIBUTE_SENSITIVE_PARAM, true));
        attributes.setDecrypt(getBoolean(section, PRIV_KEY_ATTRIBUTE_DECRYPT_PARAM, true));
        attributes.setSign(getBoolean(section, PRIV_KEY_ATTRIBUTE_SIGN_PARAM, true));
        attributes.setSignRecover(getBoolean(section, PRIV_KEY_ATTRIBUTE_SIGN_RECOVER_PARAM, null));
        attributes.setUnwrap(getBoolean(section, PRIV_KEY_ATTRIBUTE_UNWRAP_PARAM, null));
        attributes.setExtractable(getBoolean(section, PRIV_KEY_ATTRIBUTE_EXTRACTABLE_PARAM, null));
        attributes.setAlwaysSensitive(getBoolean(section, PRIV_KEY_ATTRIBUTE_ALWAYS_SENSITIVE_PARAM, null));
        attributes.setNeverExtractable(getBoolean(section, PRIV_KEY_ATTRIBUTE_NEVER_EXTRACTABLE_PARAM, null));
        attributes.setWrapWithTrusted(getBoolean(section, PRIV_KEY_ATTRIBUTE_WRAP_WITH_TRUSTED_PARAM, null));
        attributes.setAllowedMechanisms(loadAllowedKeyUsageMechanisms(
                section, PRIV_KEY_ATTRIBUTE_ALLOWED_MECHANISMS_PARAM));

        return attributes;
    }

    private static Set<Long> loadAllowedKeyUsageMechanisms(SubnodeConfiguration section, String key) {
        Set<Long> allowedMechanism = new HashSet<>();
        String[] mechanisms = getStringArray(section, key);

        for (String m : mechanisms) {
            Long mechamism = SUPPORTED_KEY_ALLOWED_MECHANISMS.get(StringUtils.strip(m));

            if (mechamism != null) {
                allowedMechanism.add(mechamism);
            } else {
                throw new ConfigurationRuntimeException(String.format(
                        "Unsupported value '%s' of '%s' for module (%s), skipping...",
                        m, key, section.getRootElementName()));
            }
        }

        return allowedMechanism;
    }

    private static Boolean getBoolean(SubnodeConfiguration section, String key, Boolean defaultValue) {
        try {
            return section.getBoolean(key, defaultValue);
        } catch (ConversionException e) {
            throw new ConversionException(String.format("Invalid value of '%s' for module (%s), skipping...",
                    key, section.getRootElementName()), e);
        }
    }

    private static String[] getStringArray(SubnodeConfiguration section, String key) {
        try {
            return section.getStringArray(key);
        } catch (ConversionException e) {
            throw new ConversionException(String.format("Invalid value of '%s' for module (%s), skipping...",
                    key, section.getRootElementName()), e);
        }
    }
}
