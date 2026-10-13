/*
 * The MIT License
 *
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
package org.niis.xroad.edc.extension.webhook;

import ee.ria.xroad.common.identifier.SecurityServerId;

import org.eclipse.edc.connector.core.profile.DataspaceProfileContextRegistryImpl;
import org.eclipse.edc.jsonld.spi.JsonLdNamespace;
import org.eclipse.edc.protocol.spi.DataspaceProfileContext;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.protocol.spi.ProtocolVersion;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.GlobalConfInitException;
import org.niis.xroad.globalconf.model.GlobalConfInitState;
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class XRoadProtocolWebhookResolverTest {

    private static final String PROFILE = "http-dsp-profile-2025-1";
    private static final String BASE_URL = "https://ss0.example.org:8183/api/dsp";
    private static final SecurityServerId.Conf SERVER_ID = SecurityServerId.Conf.create("DEV", "COM", "222", "ss0");

    private final GlobalConfProvider globalConfProvider = mock();
    private final ServerConfProvider serverConfProvider = mock();
    private final DataspaceProfileContextRegistry profileRegistry = new DataspaceProfileContextRegistryImpl();
    private final List<String> warnings = new ArrayList<>();

    private XRoadProtocolWebhookResolver resolver;

    @BeforeEach
    void setUp() {
        profileRegistry.registerDefault(new DataspaceProfileContext(PROFILE,
                new ProtocolVersion("2025-1", "/" + PROFILE, "HTTPS"),
                () -> "https://localhost/" + PROFILE,
                claimToken -> "participant",
                new JsonLdNamespace("https://w3id.org/dspace/2025/1/"),
                List.of(),
                List.of()));
        when(serverConfProvider.getIdentifier()).thenReturn(SERVER_ID);
        resolver = new XRoadProtocolWebhookResolver(globalConfProvider, serverConfProvider, profileRegistry, new Monitor() {
            @Override
            public void warning(String message, Throwable... errors) {
                warnings.add(message);
            }
        });
    }

    @Test
    void publishedBaseUrlYieldsCallbackWithContextIdAndProfile() {
        publish(BASE_URL);

        var webhook = resolver.getWebhook("DEV:COM:222", PROFILE);

        assertThat(webhook).isNotNull();
        assertThat(webhook.url()).isEqualTo(BASE_URL + "/DEV:COM:222/" + PROFILE);
        assertThat(warnings).isEmpty();
    }

    @Test
    void contextIdIsPassedThroughUnchanged() {
        publish(BASE_URL);

        var webhook = resolver.getWebhook("DEV:COM:A%2BB", PROFILE);

        assertThat(webhook).isNotNull();
        assertThat(webhook.url()).isEqualTo(BASE_URL + "/DEV:COM:A%2BB/" + PROFILE);
    }

    @Test
    void percentInPublishedBaseUrlIsKeptVerbatim() {
        publish("https://ss0.example.org:8183/api/dsp%s");

        var webhook = resolver.getWebhook("DEV:COM:222", PROFILE);

        assertThat(webhook).isNotNull();
        assertThat(webhook.url()).isEqualTo("https://ss0.example.org:8183/api/dsp%s/DEV:COM:222/" + PROFILE);
    }

    @Test
    void noPublishedValueYieldsNoWebhookAndOneWarning() {
        when(globalConfProvider.getServerSystemValues(SERVER_ID)).thenReturn(Optional.empty());

        assertThat(resolver.getWebhook("DEV:COM:222", PROFILE)).isNull();

        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains(SERVER_ID.toShortString()).contains("no DSP base URL");
    }

    @Test
    void unsetOwnerYieldsNoWebhookAndOneWarning() {
        when(serverConfProvider.getIdentifier())
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.MALFORMED_SERVERCONF).build());

        assertThat(resolver.getWebhook("DEV:COM:222", PROFILE)).isNull();

        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains("owner is not set");
    }

    @Test
    void uninitialisedGlobalConfYieldsNoWebhookAndOneWarning() {
        when(globalConfProvider.getServerSystemValues(SERVER_ID))
                .thenThrow(new GlobalConfInitException(GlobalConfInitState.UNINITIALIZED));

        assertThat(resolver.getWebhook("DEV:COM:222", PROFILE)).isNull();

        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains("not initialised").contains(SERVER_ID.toShortString());
    }

    @Test
    void unrelatedServerConfFailureIsNotSwallowed() {
        when(serverConfProvider.getIdentifier()).thenThrow(XrdRuntimeException.systemInternalError("boom"));

        assertThatThrownBy(() -> resolver.getWebhook("DEV:COM:222", PROFILE)).isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void unknownProfileYieldsNoWebhook() {
        publish(BASE_URL);

        assertThat(resolver.getWebhook("DEV:COM:222", "unknown-profile")).isNull();
    }

    @Test
    void valueAppearingBetweenCallsIsReflectedWithoutReset() {
        when(globalConfProvider.getServerSystemValues(SERVER_ID)).thenReturn(Optional.empty());
        assertThat(resolver.getWebhook("DEV:COM:222", PROFILE)).isNull();

        publish(BASE_URL);

        var webhook = resolver.getWebhook("DEV:COM:222", PROFILE);
        assertThat(webhook).isNotNull();
        assertThat(webhook.url()).isEqualTo(BASE_URL + "/DEV:COM:222/" + PROFILE);
    }

    @Test
    void valueChangingBetweenCallsIsReflectedWithoutReset() {
        publish(BASE_URL);
        assertThat(resolver.getWebhook("DEV:COM:222", PROFILE).url()).startsWith(BASE_URL + "/");

        publish("https://ss0-new.example.org:9183/api/dsp");

        assertThat(resolver.getWebhook("DEV:COM:222", PROFILE).url())
                .isEqualTo("https://ss0-new.example.org:9183/api/dsp/DEV:COM:222/" + PROFILE);
    }

    private void publish(String dspBaseUrl) {
        when(globalConfProvider.getServerSystemValues(SERVER_ID))
                .thenReturn(Optional.of(new ServerSystemValues("did:web:ss0.example.org%3A7183:v1:system", dspBaseUrl)));
    }
}
