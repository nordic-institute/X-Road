package ee.ria.xroad.signer.tokenmanager.token;

import iaik.pkcs.pkcs11.TokenInfo;

import java.util.Map;

/**
 * Fills out the token information for a hardware token.
 */
public final class HardwareTokenInfo {

    private static final long CK_UNAVAILABLE_INFORMATION = -1;

    private HardwareTokenInfo() {
    }

    static void fillInTokenInfo(TokenInfo info, Map<String, String> data) {
        data.clear();

        data.put("Type", "Hardware");
        data.put("Manufacturer ID", info.getManufacturerID());
        data.put("Model", info.getModel());
        data.put("Firmware Version",
                info.getFirmwareVersion().toString());
        data.put("Serial Number", info.getSerialNumber());
        data.put("Label", info.getLabel());

        if (info.getFreePrivateMemory() != CK_UNAVAILABLE_INFORMATION) {
            data.put("Free Private Memory",
                Long.toString(info.getFreePrivateMemory()));
        }

        if (info.getFreePublicMemory() != CK_UNAVAILABLE_INFORMATION) {
            data.put("Free Public Memory",
                Long.toString(info.getFreePublicMemory()));
        }

        if (info.getTotalPrivateMemory() != CK_UNAVAILABLE_INFORMATION) {
            data.put("Total Private Memory",
                Long.toString(info.getTotalPrivateMemory()));
        }

        if (info.getTotalPublicMemory() != CK_UNAVAILABLE_INFORMATION) {
            data.put("Total Public Memory",
                Long.toString(info.getTotalPublicMemory()));
        }

        data.put("Clock on token", Boolean.toString(info.isClockOnToken()));
        data.put("Protected authentication path",
                Boolean.toString(info.isProtectedAuthenticationPath()));
        data.put("Has RNG", Boolean.toString(info.isRNG()));
        data.put("User PIN initialized",
                Boolean.toString(info.isUserPinInitialized()));
        data.put("User PIN count low",
                Boolean.toString(info.isUserPinCountLow()));
        data.put("User PIN final try",
                Boolean.toString(info.isUserPinFinalTry()));
        data.put("User PIN locked",
                Boolean.toString(info.isUserPinLocked()));
        data.put("User PIN to be changed",
                Boolean.toString(info.isUserPinToBeChanged()));
        data.put("Token write protected",
                Boolean.toString(info.isWriteProtected()));
        data.put("Min PIN length", Long.toString(info.getMinPinLen()));
        data.put("Max PIN length", Long.toString(info.getMaxPinLen()));
    }

}
