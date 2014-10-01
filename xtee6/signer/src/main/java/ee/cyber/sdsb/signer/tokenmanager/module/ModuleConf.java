package ee.cyber.sdsb.signer.tokenmanager.module;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.configuration.ConversionException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang.StringUtils;

import ee.cyber.sdsb.common.conf.ConfConstants;
import ee.cyber.sdsb.common.util.FileContentChangeChecker;

import static ee.cyber.sdsb.common.SystemProperties.getDeviceConfFile;

/**
 * Encapsulates module data read form the external configuration file.
 *
 * Each module specifies an UID, the pkcs#11 library path and other options
 * specific to that module.
 */
@Slf4j
public class ModuleConf {

    // Maps Type (UID) to ModuleType
    private static final Map<String, ModuleType> modules = new HashMap<>();

    private static FileContentChangeChecker changeChecker = null;

    public static Collection<ModuleType> getModules() {
        return modules.values();
    }

    public static boolean hasChanged() {
        try {
            if (changeChecker == null) {
                changeChecker =
                        new FileContentChangeChecker(getDeviceConfFile());
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to create content change checker", e);
        }

        try {
            return changeChecker != null ? changeChecker.hasChanged() : true;
        } catch (Exception e) {
            return true;
        }
    }

    public static void reload() {
        try {
            reload(getDeviceConfFile());
        } catch (Exception e) {
            log.error("Failed to load module conf", e);
        }
    }

    private static void reload(String fileName) throws Exception {
        log.trace("Loading module configuration from '{}'", fileName);

        modules.clear();
        modules.put(SoftwareModuleType.TYPE, new SoftwareModuleType());

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
                log.error(e.getMessage());
            }
        }
    }

    private static void parseSection(String uid, SubnodeConfiguration section) {
        if (!section.getBoolean("enabled", true)) {
            if (ConfConstants.SOFTKEY_TYPE.equalsIgnoreCase(uid)) {
                modules.remove(SoftwareModuleType.TYPE);
            }
            return;
        }

        String library = section.getString("library");
        if (StringUtils.isBlank(library)) {
            log.error("No pkcs#11 library specified for module (" +
                    "{}), skipping...", uid);
            return;
        }

        boolean verifyPin = getBoolean(section, "sign_verify_pin", false);
        boolean batchSigning =
                getBoolean(section, "batch_signing_enabled", true);
        boolean readOnly = getBoolean(section, "read_only", false);

        log.trace("Read module configuration (UID = {}, library = {}, " +
                "pinVerificationPerSigning = {}, batchSigning = {})",
                    new Object[] {uid, library, verifyPin, batchSigning});

        if (modules.containsKey(uid)) {
            log.warn("Module information already defined for {}, skipping...",
                    uid);
            return;
        }

        modules.put(uid, new HardwareModuleType(uid, library, verifyPin,
                batchSigning, readOnly));
    }

    private static boolean getBoolean(SubnodeConfiguration section,
            String key, boolean defaultValue) {
        try {
            return section.getBoolean(key, defaultValue);
        } catch (ConversionException e) {
            throw new ConversionException(String.format(
                    "Invalid value of '%s' for module (%s), skipping...",
                    key, section.getSubnodeKey()));
        }
    }
}
