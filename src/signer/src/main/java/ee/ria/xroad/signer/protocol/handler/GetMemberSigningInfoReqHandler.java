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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconfextension.GlobalConfExtensions;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.ocsp.OcspVerifier;
import ee.ria.xroad.common.ocsp.OcspVerifierOptions;
import ee.ria.xroad.signer.protocol.AbstractRpcHandler;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.mapper.ClientIdMapper;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.signer.proto.GetMemberSigningInfoReq;
import org.niis.xroad.signer.proto.GetMemberSigningInfoResp;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_MEMBER;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static ee.ria.xroad.signer.protocol.dto.CertificateInfo.STATUS_REGISTERED;

/**
 * Handles requests for member signing info.
 */
@Slf4j
@Component
public class GetMemberSigningInfoReqHandler extends AbstractRpcHandler<GetMemberSigningInfoReq, GetMemberSigningInfoResp> {

    @Data
    private static class SelectedCertificate {
        private final KeyInfo key;
        private final CertificateInfo cert;
    }

    @Override
    protected GetMemberSigningInfoResp handle(GetMemberSigningInfoReq request) throws Exception {
        var memberId = ClientIdMapper.fromDto(request.getMemberId());
        List<KeyInfo> memberKeys = TokenManager.getKeyInfo(memberId);

        if (memberKeys.isEmpty()) {
            throw CodedException.tr(X_UNKNOWN_MEMBER, "member_certs_not_found",
                    "Could not find any certificates for member '%s'. "
                            + "Are you sure tokens containing the certificates are logged in?", memberId);
        }

        SelectedCertificate memberCert = selectMemberCert(memberKeys, memberId);

        if (memberCert == null) {
            throw CodedException.tr(X_INTERNAL_ERROR, "member_has_no_suitable_certs",
                    "Member '%s' has no suitable certificates", memberId);
        }

        return GetMemberSigningInfoResp.newBuilder()
                .setKeyId(memberCert.getKey().getId())
                .setCert(memberCert.getCert().asMessage())
                .setSignMechanismName(memberCert.getKey().getSignMechanismName())
                .build();
    }

    private SelectedCertificate selectMemberCert(List<KeyInfo> memberKey, ClientId memberId) {
        for (KeyInfo keyInfo : memberKey) {
            for (CertificateInfo certInfo : keyInfo.getCerts()) {
                if (TokenManager.certBelongsToMember(certInfo, memberId)
                        && isSuitableCertificate(memberId.getXRoadInstance(), certInfo)) {
                    log.info("Found suitable certificate for member '{}' under key {}", memberId, keyInfo.getId());

                    return new SelectedCertificate(keyInfo, certInfo);
                }
            }
        }

        return null;
    }

    // Checks that the certificate is active and valid at current time.
    private boolean isSuitableCertificate(String instanceIdentifier, CertificateInfo cert) {
        if (!cert.isActive() || !cert.getStatus().equals(STATUS_REGISTERED)) {
            return false;
        }

        try {
            checkValidity(instanceIdentifier, cert.getCertificateBytes(), cert.getOcspBytes());

            return true;
        } catch (Exception e) {
            log.error("Certificate not suitable", e);

            return false;
        }
    }

    private void checkValidity(String instanceIdentifier, byte[] certBytes, byte[] ocspBytes) throws Exception {
        X509Certificate subject = readCertificate(certBytes);
        subject.checkValidity();
        verifyOcspResponse(instanceIdentifier, ocspBytes, subject, new OcspVerifierOptions(
                GlobalConfExtensions.getInstance().shouldVerifyOcspNextUpdate()));
    }

    private void verifyOcspResponse(String instanceIdentifier, byte[] ocspBytes, X509Certificate subject,
                                    OcspVerifierOptions verifierOptions) throws Exception {
        if (ocspBytes == null) {
            throw new Exception("OCSP response for certificate " + subject.getSubjectX500Principal().getName()
                    + " not found");
        }

        OCSPResp ocsp = new OCSPResp(ocspBytes);
        X509Certificate issuer = GlobalConf.getCaCert(instanceIdentifier, subject);
        OcspVerifier verifier = new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(), verifierOptions);
        verifier.verifyValidityAndStatus(ocsp, subject, issuer);
    }
}
