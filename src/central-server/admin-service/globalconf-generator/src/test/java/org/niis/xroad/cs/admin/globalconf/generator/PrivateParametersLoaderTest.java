package org.niis.xroad.cs.admin.globalconf.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.AnchorUrl;
import org.niis.xroad.cs.admin.api.domain.AnchorUrlCert;
import org.niis.xroad.cs.admin.api.domain.TrustedAnchor;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.api.service.TrustedAnchorService;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateParametersLoaderTest {
    public static final String ANCHOR_URL = "http://anchor.url";
    public static final byte[] ANCHOR_URL_CERT = "anchor-url-cert".getBytes(UTF_8);
    @Mock
    SystemParameterService systemParameterService;
    @Mock
    TrustedAnchorService trustedAnchorService;

    @InjectMocks
    PrivateParametersLoader privateParametersLoader;

    @Test
    void shouldLoadParameters() {
        when(systemParameterService.getParameterValue(SystemParameterService.INSTANCE_IDENTIFIER, null))
                .thenReturn("XRD");
        when(trustedAnchorService.findAll()).thenReturn(List.of(createTrustedAnchor()));

        var parameters = privateParametersLoader.load();

        assertThat(parameters.getInstanceIdentifier()).isEqualTo("XRD");
        assertThat(parameters.getConfigurationAnchors()).singleElement().satisfies(configurationAnchor -> {
           assertThat(configurationAnchor.getInstanceIdentifier()).isEqualTo("INSTANCE");
           assertThat(configurationAnchor.getGeneratedAt()).isEqualTo(Instant.EPOCH);
           assertThat(configurationAnchor.getSources()).singleElement().satisfies(
                   source -> {
                       assertThat(source.getDownloadURL()).isEqualTo(ANCHOR_URL);
                       assertThat(source.getVerificationCerts())
                               .singleElement()
                               .isEqualTo(ANCHOR_URL_CERT);
                   }
           );
        });



    }

    private static TrustedAnchor createTrustedAnchor() {
        var anchor = new TrustedAnchor();
        anchor.setInstanceIdentifier("INSTANCE");
        anchor.setGeneratedAt(Instant.EPOCH);
        anchor.setAnchorUrls(Set.of(createAnchorUrl()));
        return anchor;
    }

    private static AnchorUrl createAnchorUrl() {
        var anchorUrl = new AnchorUrl();
        anchorUrl.setUrl(ANCHOR_URL);
        anchorUrl.setAnchorUrlCerts(Set.of(createAnchorUrlCert()));
        return anchorUrl;
    }

    private static AnchorUrlCert createAnchorUrlCert() {
        var anchorUrlCert = new AnchorUrlCert();
        anchorUrlCert.setCert(ANCHOR_URL_CERT);
        return anchorUrlCert;
    }
}
