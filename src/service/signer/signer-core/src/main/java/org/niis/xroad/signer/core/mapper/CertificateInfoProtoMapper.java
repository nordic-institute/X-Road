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
package org.niis.xroad.signer.core.mapper;

import ee.ria.xroad.common.util.CryptoUtils;

import com.google.protobuf.ByteString;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.mapper.GenericUniDirectionalMapper;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.core.certmanager.OcspResponseManager;
import org.niis.xroad.signer.core.model.RuntimeCert;
import org.niis.xroad.signer.protocol.dto.CertificateInfoProto;

import static ee.ria.xroad.common.ErrorCodes.translateException;

@ApplicationScoped
@RequiredArgsConstructor
public class CertificateInfoProtoMapper implements GenericUniDirectionalMapper<RuntimeCert, CertificateInfoProto> {
    private final OcspResponseManager ocspResponseManager;

    public CertificateInfo toTargetDTO(RuntimeCert source) {
        return new CertificateInfo(toTarget(source));
    }

    @Override
    public CertificateInfoProto toTarget(RuntimeCert source) {
        try {
            var builder = CertificateInfoProto.newBuilder()
                    .setActive(source.active())
                    .setSavedToConfiguration(!source.isTransientCert())
                    .setId(source.externalId());

            if (source.memberId() != null) {
                builder.setMemberId(ClientIdMapper.toDto(source.memberId()));
            }
            if (source.status() != null) {
                builder.setStatus(source.status());
            }
            if (source.certificate() != null) {
                builder.setCertificateBytes(ByteString.copyFrom(source.certificate().getEncoded()));

                var certHash = CryptoUtils.calculateCertHexHash(source.certificate());
                ocspResponseManager.getOcspResponse(certHash).ifPresent(ocspResponse ->
                        builder.setOcspBytes(ByteString.copyFrom(ocspResponse)));

            }

            if (source.ocspVerifyBeforeActivationError() != null) {
                builder.setOcspVerifyBeforeActivationError(source.ocspVerifyBeforeActivationError());
            }
            if (source.renewedCertHash() != null) {
                builder.setRenewedCertHash(source.renewedCertHash());
            }
            if (source.renewalError() != null) {
                builder.setRenewalError(source.renewalError());
            }
            if (source.nextAutomaticRenewalTime() != null) {
                com.google.protobuf.Timestamp nextRenewalTimestamp = com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(source.nextAutomaticRenewalTime().getEpochSecond())
                        .setNanos(source.nextAutomaticRenewalTime().getNano())
                        .build();
                builder.setNextAutomaticRenewalTime(nextRenewalTimestamp);
            }
            return builder.build();
        } catch (Exception e) {
            throw translateException(e);
        }
    }
}
