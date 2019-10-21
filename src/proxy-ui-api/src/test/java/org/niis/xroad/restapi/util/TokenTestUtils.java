/**
 * The MIT License
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
package org.niis.xroad.restapi.util;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Test utils for working with tokens
 */
public final class TokenTestUtils {

    private TokenTestUtils() {
        // noop
    }

    /**
     * Creates TokenInfo object with some default values:
     * - id = "id"
     * - readOnly = false
     * - available = true
     * - active = true
     * - serialNumber = "serial-number"
     * - label = "label"
     * - slotIndex = 123
     * - tokenStatus = OK
     * - keyInfos = empty
     * - tokenInfo map = empty
     * @param friendlyName
     * @return
     */
    public static TokenInfo createTestTokenInfo(String friendlyName) {
        TokenInfo tokenInfo = new TokenInfo(TokenInfo.SOFTWARE_MODULE_TYPE,
                friendlyName,
                "id",
                false,
                true,
                true,
                "serial-number",
                "label",
                123,
                TokenStatusInfo.OK,
                new ArrayList<>(),
                new HashMap<>());
        return tokenInfo;
    }

    /**
     * Creates KeyInfo object with some default values:
     * - id = "id"
     * - other defaults from {@link TokenTestUtils#createTestKeyInfo(String)}
     */
    public static KeyInfo createTestKeyInfo() {
        KeyInfo keyInfo = createTestKeyInfo("id");
        return keyInfo;
    }

    /**
     * Creates KeyInfo object with some default values:
     * - available = true
     * - usage = SIGNING
     * - friendlyName = "friendly-name"
     * - id = "id"
     * - label = "label"
     * - publicKey = "public-key"
     * - certs = empty
     * - certRequests = empty
     * - signMechanismName = "sign-mechanism-name"
     * @param id id
     */
    public static KeyInfo createTestKeyInfo(String id) {
        KeyInfo keyInfo = new KeyInfo(true,
                KeyUsageInfo.SIGNING,
                "friendly-name",
                id,
                "label",
                "public-key",
                new ArrayList<>(),
                new ArrayList<>(),
                "sign-mechanism-name");
        return keyInfo;
    }
}
