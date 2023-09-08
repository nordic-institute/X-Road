package org.niis.xroad.signer.test.glue;

import ee.ria.xroad.signer.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import org.niis.xroad.common.test.glue.BaseStepDefs;

import java.util.HashMap;
import java.util.Map;

public class BaseSignerStepDefs extends BaseStepDefs {
    private static final String KEY_FRIENDLY_NAME_MAPPING = "tokenFriendlyNameToIdMapping";

    protected Map<String, String> getTokenFriendlyNameToIdMapping() {
        Map<String, String> map = scenarioContext.getStepData(KEY_FRIENDLY_NAME_MAPPING);
        if (map == null) {
            map = new HashMap<>();
            scenarioContext.putStepData(KEY_FRIENDLY_NAME_MAPPING, map);
        }
        return map;
    }

    protected TokenInfo getTokenInfoByFriendlyName(String friendlyName) throws Exception {
        var tokenInfo = SignerProxy.getToken(getTokenFriendlyNameToIdMapping().get(friendlyName));
        testReportService.attachJson("TokenInfo", tokenInfo);
        return tokenInfo;
    }

    protected KeyInfo findKeyInToken(String friendlyName, String keyName) throws Exception {
        var foundKeyInfo = getTokenInfoByFriendlyName(friendlyName).getKeyInfo().stream()
                .filter(keyInfo -> keyInfo.getFriendlyName().equals(keyName))
                .findFirst()
                .orElseThrow();
        testReportService.attachJson("Key [" + keyName + "]", foundKeyInfo);
        return foundKeyInfo;
    }

}
