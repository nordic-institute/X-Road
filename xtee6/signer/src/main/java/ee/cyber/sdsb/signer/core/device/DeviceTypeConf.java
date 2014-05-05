package ee.cyber.sdsb.signer.core.device;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.ConfConstants;

/**
 * Encapsulates device data read form the external configuration file.
 *
 * Each device specifies an UID, the pkcs#11 library path and other options
 * specific to that device.
 */
public class DeviceTypeConf {

    private static final Logger LOG =
            LoggerFactory.getLogger(DeviceTypeConf.class);

    // Maps Type to Device
    private static final Map<String, DeviceType> devices = new HashMap<>();

    /**
     * Returns the module configuration for the given device identifier.
     */
    public static DeviceType getDevice(String deviceId) {
        return devices.get(deviceId);
    }

    public static boolean hasDevice(String deviceId) {
        return devices.containsKey(deviceId);
    }

    public static Collection<DeviceType> getDevices() {
        return devices.values();
    }

    public static void reload() {
        try {
            reload(SystemProperties.getDeviceConfFile());
        } catch (Exception e) {
            LOG.error("Failed to load module conf", e);
        }
    }

    private static void reload(String fileName) throws Exception {
        LOG.trace("Loading device configuration from '{}'", fileName);

        devices.clear();
        devices.put(SoftwareDeviceType.TYPE, new SoftwareDeviceType());

        HierarchicalINIConfiguration conf =
                new HierarchicalINIConfiguration(fileName);
        for (String uid : conf.getSections()) {
            if (StringUtils.isBlank(uid)) {
                LOG.error("No UID specified for module, skipping...");
                continue;
            }

            SubnodeConfiguration section = conf.getSection(uid);

            if (!section.getBoolean("enabled", true)) {
                if (ConfConstants.SOFTKEY_TYPE.equalsIgnoreCase(uid)) {
                    devices.remove(SoftwareDeviceType.TYPE);
                }
                continue;
            }

            String library = section.getString("library");
            if (library == null || StringUtils.isBlank(library)) {
                LOG.error("No pkcs#11 library specified for module (" +
                        "UID = '{}'), skipping...", uid);
                continue;
            }

            boolean verifyPin = section.getBoolean("sign_verify_pin", false);

            boolean batchSigning =
                    section.getBoolean("batch_signing_enabled", true);

            LOG.trace("Read device configuration (UID = {}, library = {}, " +
                    "pinVerificationPerSigning = {}, batchSigning = {})",
                        new Object[] {uid, library, verifyPin, batchSigning});

            if (devices.containsKey(uid)) {
                LOG.warn("Module information already defined for {}", uid);
                continue;
            }

            SscdDeviceType device =
                    new SscdDeviceType(uid, library, verifyPin, batchSigning);
            devices.put(uid, device);
        }
    }

}
