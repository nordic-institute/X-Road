package ee.ria.xroad.common.conf.globalconf;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;


class LocationVersionResolverTest {
    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void enforcedVersion() throws Exception {
        var initialLocation = getConfigurationLocation();
        LocationVersionResolver resolver = LocationVersionResolver.fixed(initialLocation, 8);

        ConfigurationLocation resolvedLocation = resolver.toVersionedLocation();

        assertThat(resolvedLocation).isNotNull();
        assertThat(resolvedLocation.getDownloadURL()).isEqualTo(initialLocation.getDownloadURL() + "?version=8");
        wm.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void presetVersionPrevailsWhenVersionNotEnforced() throws Exception {
        ConfigurationLocation location = getConfigurationLocation(wm.getRuntimeInfo().getHttpBaseUrl() + "?version=1");
        LocationVersionResolver resolver = LocationVersionResolver.range(location, 2, 4);

        ConfigurationLocation resolvedLocation = resolver.toVersionedLocation();

        assertThat(resolvedLocation).isNotNull();
        assertThat(resolvedLocation.getDownloadURL()).isEqualTo(location.getDownloadURL());
        wm.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void enforcedVersionPrevailsPresetVersion() throws Exception {
        ConfigurationLocation location = getConfigurationLocation(wm.getRuntimeInfo().getHttpBaseUrl() + "?version=1");
        LocationVersionResolver resolver = LocationVersionResolver.fixed(location, 8);

        ConfigurationLocation resolvedLocation = resolver.toVersionedLocation();

        assertThat(resolvedLocation).isNotNull();
        assertThat(resolvedLocation.getDownloadURL()).isEqualTo(wm.getRuntimeInfo().getHttpBaseUrl() + "?version=8");
        wm.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void chooseFirstAvailableVersion() throws Exception {
        var initialLocation = getConfigurationLocation();
        wm.stubFor(get(anyUrl()).withQueryParam("version", equalTo("4")) .willReturn(ok()));
        LocationVersionResolver resolver = LocationVersionResolver.range(initialLocation, 2, 4);

        ConfigurationLocation resolvedLocation = resolver.toVersionedLocation();

        assertThat(resolvedLocation).isNotNull();
        assertThat(resolvedLocation.getDownloadURL()).isEqualTo(initialLocation.getDownloadURL() + "?version=4");
        wm.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    void fallbackToPreviousVersion() throws Exception {
        var initialLocation = getConfigurationLocation();
        wm.stubFor(get(anyUrl()).withQueryParam("version", equalTo("4")) .willReturn(notFound()));
        wm.stubFor(get(anyUrl()).withQueryParam("version", equalTo("3")) .willReturn(ok()));
        LocationVersionResolver resolver = LocationVersionResolver.range(initialLocation, 2, 4);

        ConfigurationLocation resolvedLocation = resolver.toVersionedLocation();

        assertThat(resolvedLocation).isNotNull();
        assertThat(resolvedLocation.getDownloadURL()).isEqualTo(initialLocation.getDownloadURL() + "?version=3");
        wm.verify(1, getRequestedFor(urlEqualTo("/?version=4")));
        wm.verify(1, getRequestedFor(urlEqualTo("/?version=3")));
        wm.verify(2, anyRequestedFor(anyUrl()));
    }

    @Test
    void fallbackToDefaultVersion() throws Exception {
        var initialLocation = getConfigurationLocation();
        wm.stubFor(get(anyUrl()).withQueryParam("version", equalTo("4")) .willReturn(notFound()));
        wm.stubFor(get(anyUrl()).withQueryParam("version", equalTo("3")) .willReturn(notFound()));
        LocationVersionResolver resolver = LocationVersionResolver.range(initialLocation, 2, 4);

        ConfigurationLocation resolvedLocation = resolver.toVersionedLocation();

        assertThat(resolvedLocation).isNotNull();
        assertThat(resolvedLocation.getDownloadURL()).isEqualTo(initialLocation.getDownloadURL() + "?version=2");
        wm.verify(1, getRequestedFor(urlEqualTo("/?version=4")));
        wm.verify(1, getRequestedFor(urlEqualTo("/?version=3")));
        wm.verify(2, anyRequestedFor(anyUrl()));
    }

    @Test
    void resolvedUrlIsCached() throws Exception {
        var initialLocation = getConfigurationLocation();
        wm.stubFor(get(anyUrl()).withQueryParam("version", equalTo("4")) .willReturn(notFound()));
        wm.stubFor(get(anyUrl()).withQueryParam("version", equalTo("3")) .willReturn(notFound()));
        LocationVersionResolver resolver = LocationVersionResolver.range(initialLocation, 2, 4);

        ConfigurationLocation resolvedLocation1 = resolver.toVersionedLocation();
        ConfigurationLocation resolvedLocation2 = resolver.toVersionedLocation();

        assertThat(resolvedLocation1).isNotNull();
        assertThat(resolvedLocation1.getDownloadURL()).isEqualTo(initialLocation.getDownloadURL() + "?version=2");
        assertThat(resolvedLocation1).isEqualTo(resolvedLocation2);
        wm.verify(2, anyRequestedFor(anyUrl()));
    }

    private ConfigurationLocation getConfigurationLocation() {
        return getConfigurationLocation(wm.getRuntimeInfo().getHttpBaseUrl());
    }

    private ConfigurationLocation getConfigurationLocation(String url) {
        return new ConfigurationLocation("EE", url, List.of());
    }
}
