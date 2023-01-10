package org.niis.xroad.cs.admin.core.entity.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.niis.xroad.cs.admin.core.entity.AnchorUrlCertEntity;
import org.niis.xroad.cs.admin.core.entity.AnchorUrlEntity;
import org.niis.xroad.cs.admin.core.entity.TrustedAnchorEntity;

import java.time.Instant;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class TrustedAnchorMapperTest {
    TrustedAnchorMapper mapper = Mappers.getMapper(TrustedAnchorMapper.class);

    @Test
    void shouldMapAllFields() {
        TrustedAnchorEntity source = createTrustedAnchorEntity();

        var target = mapper.toTarget(source);

        assertThat(target)
                .usingRecursiveComparison()
                .isEqualTo(source);

        assertThat(target).hasNoNullFieldsOrProperties()
                .usingRecursiveAssertion()
                .allFieldsSatisfy(field -> field != null);
    }

    private static TrustedAnchorEntity createTrustedAnchorEntity() {
        var source = new TrustedAnchorEntity();
        source.setAnchorUrls(Set.of(createAnchorUrlEntity()));
        source.setInstanceIdentifier("INSTANCE");
        source.setTrustedAnchorHash("trusted anchor hash");
        source.setTrustedAnchorFile("trusted anchor file".getBytes(UTF_8));
        source.setGeneratedAt(Instant.now());
        return source;
    }

    private static AnchorUrlEntity createAnchorUrlEntity() {
        var anchorUrl = new AnchorUrlEntity();
        anchorUrl.setUrl("http://url");
        anchorUrl.setAnchorUrlCerts(Set.of(createAnchorUrlCertEntity()));
        return anchorUrl;
    }

    private static AnchorUrlCertEntity createAnchorUrlCertEntity() {
        var anchorUrlCert = new AnchorUrlCertEntity();
        anchorUrlCert.setCert("anchor url cert".getBytes(UTF_8));
        return anchorUrlCert;
    }

}
