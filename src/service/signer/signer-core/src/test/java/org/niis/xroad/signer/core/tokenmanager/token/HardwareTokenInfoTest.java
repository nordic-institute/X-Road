/*
 * The MIT License
 *
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

import iaik.pkcs.pkcs11.TokenInfo;
import iaik.pkcs.pkcs11.Version;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HardwareTokenInfoTest {

    @Test
    void testFillInTokenInfo() {
        TokenInfo tokenInfoMock = mock(TokenInfo.class);
        Version versionMock = mock(Version.class);

        when(tokenInfoMock.getManufacturerID()).thenReturn("Test Manufacturer");
        when(tokenInfoMock.getModel()).thenReturn("Model X");

        when(versionMock.toString()).thenReturn("1.2");
        when(tokenInfoMock.getFirmwareVersion()).thenReturn(versionMock);
        when(tokenInfoMock.getSerialNumber()).thenReturn("12345678");
        when(tokenInfoMock.getLabel()).thenReturn("Token Label");

        when(tokenInfoMock.getFreePrivateMemory()).thenReturn(100L);
        when(tokenInfoMock.getFreePublicMemory()).thenReturn(200L);
        when(tokenInfoMock.getTotalPrivateMemory()).thenReturn(300L);
        when(tokenInfoMock.getTotalPublicMemory()).thenReturn(400L);

        when(tokenInfoMock.isClockOnToken()).thenReturn(true);
        when(tokenInfoMock.isProtectedAuthenticationPath()).thenReturn(false);
        when(tokenInfoMock.isRNG()).thenReturn(true);
        when(tokenInfoMock.isUserPinInitialized()).thenReturn(true);
        when(tokenInfoMock.isUserPinCountLow()).thenReturn(false);
        when(tokenInfoMock.isUserPinFinalTry()).thenReturn(true);
        when(tokenInfoMock.isUserPinLocked()).thenReturn(false);
        when(tokenInfoMock.isUserPinToBeChanged()).thenReturn(true);
        when(tokenInfoMock.isWriteProtected()).thenReturn(false);

        when(tokenInfoMock.getMinPinLen()).thenReturn(4L);
        when(tokenInfoMock.getMaxPinLen()).thenReturn(12L);

        Map<String, String> tokenInfo = new HashMap<>();
        HardwareTokenInfo.fillInTokenInfo(tokenInfoMock, tokenInfo);

        assertEquals("Hardware", tokenInfo.get("Type"));
        assertEquals("Test Manufacturer", tokenInfo.get("Manufacturer ID"));
        assertEquals("Model X", tokenInfo.get("Model"));
        assertEquals("1.2", tokenInfo.get("Firmware Version")); // Version.toString()
        assertEquals("12345678", tokenInfo.get("Serial Number"));
        assertEquals("Token Label", tokenInfo.get("Label"));

        assertEquals("100", tokenInfo.get("Free Private Memory"));
        assertEquals("200", tokenInfo.get("Free Public Memory"));
        assertEquals("300", tokenInfo.get("Total Private Memory"));
        assertEquals("400", tokenInfo.get("Total Public Memory"));

        assertEquals("true", tokenInfo.get("Clock on token"));
        assertEquals("false", tokenInfo.get("Protected authentication path"));
        assertEquals("true", tokenInfo.get("Has RNG"));
        assertEquals("true", tokenInfo.get("User PIN initialized"));
        assertEquals("false", tokenInfo.get("User PIN count low"));
        assertEquals("true", tokenInfo.get("User PIN final try"));
        assertEquals("false", tokenInfo.get("User PIN locked"));
        assertEquals("true", tokenInfo.get("User PIN to be changed"));
        assertEquals("false", tokenInfo.get("Token write protected"));
        assertEquals("4", tokenInfo.get("Min PIN length"));
        assertEquals("12", tokenInfo.get("Max PIN length"));
    }

}
