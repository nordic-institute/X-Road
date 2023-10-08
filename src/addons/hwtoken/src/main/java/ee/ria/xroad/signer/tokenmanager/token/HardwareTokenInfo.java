/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA), Nordic Institute
 * for Interoperability Solutions (NIIS), Population Register Centre (VRK) Copyright (c) 2015-2017
 * Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
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
