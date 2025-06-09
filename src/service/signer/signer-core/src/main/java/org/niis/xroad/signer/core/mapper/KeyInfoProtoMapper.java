package org.niis.xroad.signer.core.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.mapper.GenericUniDirectionalMapper;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.core.model.CertRequestData;
import org.niis.xroad.signer.core.model.RuntimeCert;
import org.niis.xroad.signer.core.model.RuntimeKey;
import org.niis.xroad.signer.protocol.dto.CertRequestInfoProto;
import org.niis.xroad.signer.protocol.dto.CertificateInfoProto;
import org.niis.xroad.signer.protocol.dto.KeyInfoProto;

import java.util.Collection;
import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor
public class KeyInfoProtoMapper implements GenericUniDirectionalMapper<RuntimeKey, KeyInfoProto> {
    private final CertificateInfoProtoMapper certificateInfoProtoMapper;
    private final CertRequestInfoProtoMapper certRequestInfoProtoMapper;

    public KeyInfo toTargetDTO(RuntimeKey source) {
        return new KeyInfo(toTarget(source));
    }

    @Override
    public KeyInfoProto toTarget(RuntimeKey source) {
        var builder = KeyInfoProto.newBuilder()
                .setId(source.externalId())
                .setAvailable(source.isAvailable())
                .addAllCerts(getCertsAsDTOs(source.certs()))
                .addAllCertRequests(getCertRequestsAsDTOs(source.certRequests()))
                .setSignMechanismName(source.signMechanismName().name());

        if (source.usage() != null) {
            builder.setUsage(source.usage());
        }

        if (source.friendlyName() != null) {
            builder.setFriendlyName(source.friendlyName());
        }

        if (source.label() != null) {
            builder.setLabel(source.label());
        }

        if (source.publicKey() != null) {
            builder.setPublicKey(source.publicKey());
        }

        return builder.build();
    }

    private List<CertificateInfoProto> getCertsAsDTOs(Collection<RuntimeCert> certs) {
        return certs.stream()
                .map(certificateInfoProtoMapper::toTarget)
                .toList();
    }

    private List<CertRequestInfoProto> getCertRequestsAsDTOs(Collection<CertRequestData> certRequests) {
        return certRequests.stream()
                .map(certRequestInfoProtoMapper::toTarget)
                .toList();
    }

}
