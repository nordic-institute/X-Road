/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.signer.tokenmanager.token;

import java.util.HashMap;
import java.util.Map;

import akka.actor.Props;

import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

/**
 * Software token implementation.
 */
public class SoftwareToken extends AbstractToken {

    private static final String DISPATCHER = "token-worker-dispatcher";

    private final SoftwareTokenType tokenType;

    /**
     * Constructs new software token.
     * @param tokenInfo the token info
     * @param tokenType the token type
     */
    public SoftwareToken(TokenInfo tokenInfo, SoftwareTokenType tokenType) {
        super(tokenInfo);

        this.tokenType = tokenType;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();

        initTokenInfo(tokenInfo);
    }

    @Override
    protected Props createWorker() {
        return Props.create(SoftwareTokenWorker.class,
                tokenInfo, tokenType).withDispatcher(DISPATCHER);
    }

    @Override
    protected Props createSigner() {
        return Props.create(TokenSigner.class);
    }

    private void initTokenInfo(TokenInfo tokenInfo) {
        Map<String, String> info = new HashMap<>();
        info.put("Type", "Software");

        TokenManager.setTokenInfo(tokenInfo.getId(), info);
    }

}
