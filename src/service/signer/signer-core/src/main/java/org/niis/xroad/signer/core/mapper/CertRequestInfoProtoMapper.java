package org.niis.xroad.signer.core.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.niis.xroad.common.core.mapper.GenericUniDirectionalMapper;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.signer.api.dto.CertRequestInfo;
import org.niis.xroad.signer.core.model.CertRequestData;
import org.niis.xroad.signer.protocol.dto.CertRequestInfoProto;

import static java.util.Optional.ofNullable;

@ApplicationScoped
public class CertRequestInfoProtoMapper implements GenericUniDirectionalMapper<CertRequestData, CertRequestInfoProto> {

    public CertRequestInfo toTargetDTO(CertRequestData source) {
        return new CertRequestInfo(toTarget(source));
    }

    @Override
    public CertRequestInfoProto toTarget(CertRequestData source) {
        final CertRequestInfoProto.Builder builder = CertRequestInfoProto.newBuilder()
                .setId(source.externalId())
                .setSubjectName(source.subjectName());

        ofNullable(source.subjectAltName()).ifPresent(builder::setSubjectAltName);
        ofNullable(source.certificateProfile()).ifPresent(builder::setCertificateProfile);
        ofNullable(source.memberId()).map(ClientIdMapper::toDto).ifPresent(builder::setMemberId);
        return builder.build();
    }
}
