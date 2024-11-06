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
package ee.ria.xroad.signer.tokenmanager.module;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.tokenmanager.token.AbstractTokenWorker;
import ee.ria.xroad.signer.tokenmanager.token.SoftwareTokenType;
import ee.ria.xroad.signer.tokenmanager.token.SoftwareTokenWorker;
import ee.ria.xroad.signer.tokenmanager.token.TokenType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Worker for software module. Always lists only one software token.
 */
public class SoftwareModuleWorker extends AbstractModuleWorker {

    private static final List<TokenType> TOKENS = List.of(new SoftwareTokenType(SystemProperties.getSoftwareTokenSignMechanism()));

    public SoftwareModuleWorker(ModuleType moduleType) {
        super(moduleType);
    }

    @Override
    protected List<TokenType> listTokens() throws Exception {
        return TOKENS;
    }

    @Override
    protected AbstractTokenWorker createWorker(TokenInfo tokenInfo, TokenType tokenType) {
        initTokenInfo(tokenInfo);

        return new SoftwareTokenWorker(tokenInfo, tokenType);
    }

    private void initTokenInfo(TokenInfo tokenInfo) {
        Map<String, String> info = new HashMap<>();
        info.put("Type", "Software");

        TokenManager.setTokenInfo(tokenInfo.getId(), info);
    }

}
