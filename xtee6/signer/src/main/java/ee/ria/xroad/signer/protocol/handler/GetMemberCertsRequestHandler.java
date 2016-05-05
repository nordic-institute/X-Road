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
package ee.ria.xroad.signer.protocol.handler;

import java.util.List;
import java.util.stream.Collectors;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.message.GetMemberCerts;
import ee.ria.xroad.signer.protocol.message.GetMemberCertsResponse;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

/**
 * Handles requests for member certificates.
 */
public class GetMemberCertsRequestHandler
        extends AbstractRequestHandler<GetMemberCerts> {

    @Override
    protected Object handle(GetMemberCerts message) throws Exception {
        List<CertificateInfo> memberCerts = TokenManager.listTokens().stream()
                .flatMap(t -> t.getKeyInfo().stream())
                .filter(k -> k.getUsage() == KeyUsageInfo.SIGNING)
                .flatMap(k -> k.getCerts().stream())
                .filter(c -> containsMember(c.getMemberId(),
                        message.getMemberId()))
                .collect(Collectors.toList());

        return new GetMemberCertsResponse(memberCerts);
    }

    private static boolean containsMember(ClientId first, ClientId second) {
        if (first == null || second == null) {
            return false;
        }

        return first.equals(second) || second.subsystemContainsMember(first);
    }

}
