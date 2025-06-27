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

package org.niis.xroad.signer.test.glue;

import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.common.test.glue.BaseStepDefs;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.test.SignerClientHolder;
import org.niis.xroad.signer.test.container.SignerIntTestSetup;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

public class BaseSignerStepDefs extends BaseStepDefs {
    private static final String KEY_FRIENDLY_NAME_MAPPING = "tokenFriendlyNameToIdMapping";

    @Autowired
    protected SignerClientHolder clientHolder;
    @Autowired
    protected SignerIntTestSetup signerIntTestSetup;

    protected Map<String, String> getTokenFriendlyNameToIdMapping() {
        Map<String, String> map = scenarioContext.getStepData(KEY_FRIENDLY_NAME_MAPPING);
        if (map == null) {
            map = new HashMap<>();
            scenarioContext.putStepData(KEY_FRIENDLY_NAME_MAPPING, map);
        }
        return map;
    }

    protected TokenInfo getTokenInfoByFriendlyName(String friendlyName)  {
        return getTokenInfoByFriendlyName(friendlyName, NodeProperties.NodeType.PRIMARY);
    }

    protected TokenInfo getTokenInfoByFriendlyName(String friendlyName, NodeProperties.NodeType nodeType)  {
        var tokenInfo = clientHolder.get(nodeType).getToken(getTokenFriendlyNameToIdMapping().get(friendlyName));
        testReportService.attachJson("TokenInfo", tokenInfo);
        return tokenInfo;
    }

    protected KeyInfo findKeyInToken(String friendlyName, String keyName) {
        return findKeyInToken(friendlyName, keyName, NodeProperties.NodeType.PRIMARY);
    }

    protected KeyInfo findKeyInToken(String friendlyName, String keyName, NodeProperties.NodeType nodeType) {
        var foundKeyInfo = getTokenInfoByFriendlyName(friendlyName, nodeType).getKeyInfo().stream()
                .filter(keyInfo -> keyName.equals(keyInfo.getFriendlyName()))
                .findFirst()
                .orElseThrow();
        testReportService.attachJson("Key [" + keyName + "]", foundKeyInfo);
        return foundKeyInfo;
    }

}
