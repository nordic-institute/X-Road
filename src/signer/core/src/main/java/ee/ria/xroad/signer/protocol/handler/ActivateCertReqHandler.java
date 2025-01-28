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
package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.cert.CertChainVerifier;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.certmanager.OcspResponseManager;
import ee.ria.xroad.signer.protocol.AbstractRpcHandler;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.message.GetOcspResponses;
import ee.ria.xroad.signer.protocol.message.GetOcspResponsesResponse;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.rpc.common.Empty;
import org.niis.xroad.signer.proto.ActivateCertReq;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static ee.ria.xroad.common.util.CertUtils.getSha1Hashes;
import static ee.ria.xroad.common.util.CertUtils.isSelfSigned;
import static ee.ria.xroad.common.util.EncoderUtils.decodeBase64;
import static ee.ria.xroad.signer.util.ExceptionHelper.certWithIdNotFound;

/**
 * Handles certificate activations and deactivations.
 */
@RequiredArgsConstructor
@ApplicationScoped
public class ActivateCertReqHandler
        extends AbstractRpcHandler<ActivateCertReq, Empty> {

    private final OcspResponseManager ocspResponseManager;
    private final GlobalConfProvider globalConfProvider;

    @Override
    protected Empty handle(ActivateCertReq request) throws Exception {
        if (request.getActive()) {
            CertificateInfo certificateInfo = TokenManager.getCertificateInfo(request.getCertIdOrHash());
            if (certificateInfo == null) {
                throw certWithIdNotFound(request.getCertIdOrHash());
            }
            X509Certificate x509Certificate = CryptoUtils.readCertificate(certificateInfo.getCertificateBytes());
            if (!isSelfSigned(x509Certificate)) {
                CertChain certChain = globalConfProvider.getCertChain(globalConfProvider.getInstanceIdentifier(), x509Certificate);
                GetOcspResponses message = new GetOcspResponses(getSha1Hashes(certChain.getAllCertsWithoutTrustedRoot()));
                GetOcspResponsesResponse result = ocspResponseManager.handleGetOcspResponses(message);
                List<OCSPResp> ocspResponses = new ArrayList<>();
                for (String encodedResponse : result.getBase64EncodedResponses()) {
                    if (encodedResponse != null) {
                        ocspResponses.add(new OCSPResp(decodeBase64(encodedResponse)));
                    } else {
                        throw new IllegalStateException("OCSP Response was null");
                    }
                }
                new CertChainVerifier(globalConfProvider, certChain).verify(ocspResponses, new Date());
            }
        }

        TokenManager.setCertActive(request.getCertIdOrHash(), request.getActive());

        return Empty.getDefaultInstance();
    }
}
