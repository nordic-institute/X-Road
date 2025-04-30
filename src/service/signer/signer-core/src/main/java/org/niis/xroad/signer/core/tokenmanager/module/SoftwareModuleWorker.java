/*
 * The MIT License
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
package org.niis.xroad.signer.core.tokenmanager.module;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.core.config.SignerProperties;
import org.niis.xroad.signer.core.tokenmanager.TokenManager;
import org.niis.xroad.signer.core.tokenmanager.token.AbstractTokenWorker;
import org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenType;
import org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenWorker;
import org.niis.xroad.signer.core.tokenmanager.token.TokenType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Worker for software module. Always lists only one software token.
 */
public class SoftwareModuleWorker extends AbstractModuleWorker {
    private final List<TokenType> tokenTypes;

    public SoftwareModuleWorker(ModuleType moduleType, SignerProperties signerProperties, TokenManager tokenManager) {
        super(moduleType, signerProperties, tokenManager);
        this.tokenTypes = List.of(new SoftwareTokenType(
                Map.of(
                        KeyAlgorithm.EC, SignMechanism.valueOf(signerProperties.softTokenEcSignMechanism()),
                        KeyAlgorithm.RSA, SignMechanism.valueOf(signerProperties.softTokenRsaSignMechanism())
                )
        ));
    }

    @Override
    protected List<TokenType> listTokens() throws Exception {
        return tokenTypes;
    }

    @Override
    protected AbstractTokenWorker createWorker(TokenInfo tokenInfo, TokenType tokenType) {
        initTokenInfo(tokenInfo);

        return new SoftwareTokenWorker(tokenInfo, tokenType, signerProperties, tokenManager);
    }

    private void initTokenInfo(TokenInfo tokenInfo) {
        Map<String, String> info = new HashMap<>();
        info.put("Type", "Software");

        tokenManager.setTokenInfo(tokenInfo.getId(), info);
    }

}
