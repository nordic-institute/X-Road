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
package ee.ria.xroad.proxy.conf;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.signature.MessageSigner;
import ee.ria.xroad.proxy.signedmessage.SignerSigningKey;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class SigningCtxProvider {
    private static DefaultSigningCtxProvider ctxProvider = new DefaultSigningCtxProvider();

    private static MessageSigner signer;

    public static SigningCtx getSigningCtx(ClientId clientId) {
        return ctxProvider.getSigningCtx(clientId);
    }


    public static void setSigningCtxProvider(DefaultSigningCtxProvider provider) {
        log.warn("Setting signing context provider to '{}'", provider.getClass().getName());
        ctxProvider = provider;
    }

    public static class DefaultSigningCtxProvider {
        public SigningCtx getSigningCtx(ClientId clientId) {
            log.debug("Retrieving signing info for member '{}'", clientId);

            var signingInfo = KeyConf.getSigningInfo(clientId);

            return getSigningCtx(signingInfo);
        }

        private SigningCtx getSigningCtx(SigningInfo signingInfo) {
            return new SigningCtxImpl(signingInfo.getClientId(),
                    new SignerSigningKey(signingInfo.getKeyId(), signingInfo.getSignMechanismName(), signer), signingInfo.getCert());
        }
    }

    @Deprecated
    public static void setSigner(MessageSigner signer) {
        SigningCtxProvider.signer = signer;
    }
}
