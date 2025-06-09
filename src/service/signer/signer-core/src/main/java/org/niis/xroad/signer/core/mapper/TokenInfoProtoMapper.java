package org.niis.xroad.signer.core.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.mapper.GenericUniDirectionalMapper;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.core.model.RuntimeKey;
import org.niis.xroad.signer.core.model.RuntimeToken;
import org.niis.xroad.signer.protocol.dto.KeyInfoProto;
import org.niis.xroad.signer.protocol.dto.TokenInfoProto;

import java.util.Collection;
import java.util.List;

import static java.util.Collections.unmodifiableMap;
import static java.util.Optional.ofNullable;

@ApplicationScoped
@RequiredArgsConstructor
public class TokenInfoProtoMapper implements GenericUniDirectionalMapper<RuntimeToken, TokenInfoProto> {
    private final KeyInfoProtoMapper keyInfoProtoMapper;

    public TokenInfo toTargetDTO(RuntimeToken source) {
        return new TokenInfo(toTarget(source));
    }

    @Override
    public TokenInfoProto toTarget(RuntimeToken source) {
        var messageBuilder = TokenInfoProto.newBuilder()
                .setType(source.type())
                .setId(source.externalId())
                .setAvailable(source.isAvailable())
                .setActive(source.isActive())
                .setStatus(source.getStatus())
                .addAllKeyInfo(getKeysAsDTOs(source.keys()))
                .putAllTokenInfo(unmodifiableMap(source.getTokenInfo()));

        ofNullable(source.serialNumber()).ifPresent(messageBuilder::setSerialNumber);
        ofNullable(source.friendlyName()).ifPresent(messageBuilder::setFriendlyName);
        ofNullable(source.label()).ifPresent(messageBuilder::setLabel);

        source.getTokenDefinition().ifPresent(tokenType -> messageBuilder.setReadOnly(tokenType.readOnly()));

        return messageBuilder.build();
    }

    private List<KeyInfoProto> getKeysAsDTOs(Collection<RuntimeKey> keys) {
        return keys.stream()
                .map(keyInfoProtoMapper::toTarget)
                .toList();
    }

}
