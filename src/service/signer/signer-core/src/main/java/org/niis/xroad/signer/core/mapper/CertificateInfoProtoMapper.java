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
