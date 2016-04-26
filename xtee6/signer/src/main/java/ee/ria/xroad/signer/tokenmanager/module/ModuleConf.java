/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.configuration.ConversionException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang.StringUtils;

import ee.ria.xroad.common.util.FileContentChangeChecker;

import static ee.ria.xroad.common.SystemProperties.getDeviceConfFile;

/**
 * Encapsulates module data read form the external configuration file.
 *
 * Each module specifies an UID, the pkcs#11 library path and other options
 * specific to that module.
 */
@Slf4j
public final class ModuleConf {

    /** The type used to identify software keys in key configuration. */
    private static final String SOFTKEY_TYPE = "softToken";

    // Maps Type (UID) to ModuleType
    private static final Map<String, ModuleType> MODULES = new HashMap<>();

    private static FileContentChangeChecker changeChecker = null;

    private ModuleConf() {
    }

    /**
     * @return all modules
     */
    public static Collection<ModuleType> getModules() {
        return MODULES.values();
    }

    /**
     * @return true, if the configuration file has changed (modified on disk)
     */
    public static boolean hasChanged() {
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
    public static void reload() {
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

        HierarchicalINIConfiguration conf =
                new HierarchicalINIConfiguration(fileName);
        for (String uid : conf.getSections()) {
            if (StringUtils.isBlank(uid)) {
                log.error("No UID specified for module, skipping...");
                continue;
            }

            try {
                parseSection(uid, conf.getSection(uid));
            } catch (ConversionException e) {
                log.error("Parse section failed with {}", e);
            }
        }
    }

    private static void parseSection(String uid, SubnodeConfiguration section) {
        if (!section.getBoolean("enabled", true)) {
            if (SOFTKEY_TYPE.equalsIgnoreCase(uid)) {
                MODULES.remove(SoftwareModuleType.TYPE);
            }
            return;
        }

        String library = section.getString("library");
        if (StringUtils.isBlank(library)) {
            log.error("No pkcs#11 library specified for module ("
                    + "{}), skipping...", uid);
            return;
        }

        boolean verifyPin = getBoolean(section, "sign_verify_pin", false);
        boolean batchSigning =
                getBoolean(section, "batch_signing_enabled", true);
        boolean readOnly = getBoolean(section, "read_only", false);

        log.trace("Read module configuration (UID = {}, library = {}, "
                + "pinVerificationPerSigning = {}, batchSigning = {})",
                    new Object[] {uid, library, verifyPin, batchSigning});

        if (MODULES.containsKey(uid)) {
            log.warn("Module information already defined for {}, skipping...",
                    uid);
            return;
        }

        MODULES.put(uid, new HardwareModuleType(uid, library, verifyPin,
                batchSigning, readOnly));
    }

    private static boolean getBoolean(SubnodeConfiguration section,
            String key, boolean defaultValue) {
        try {
            return section.getBoolean(key, defaultValue);
        } catch (ConversionException e) {
            throw new ConversionException(String.format(
                    "Invalid value of '%s' for module (%s), skipping...",
                    key, section.getSubnodeKey()), e);
        }
    }
}
