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
package org.niis.xroad.ds.issuance.identity;

import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.niis.xroad.ds.issuance.identity.StatusListCredentialFixture.statusListCredential;

class XRoadStatusListCredentialPublisherTest {

    private static final Duration CHECK_INTERVAL = Duration.ofMillis(20);

    private final Monitor monitor = mock();

    @Test
    void refusesToCreateOrPublishWhileUnbound() {
        var holder = new BoundIssuerIdentityHolder(() -> Optional::empty, monitor, CHECK_INTERVAL);
        var publisher = new XRoadStatusListCredentialPublisher(holder);

        var publishResult = publisher.publish(statusListCredential("credential-1"));

        assertThat(publishResult.failed()).isTrue();
        assertThatThrownBy(() -> publisher.publicUrl("credential-1")).isInstanceOf(EdcException.class);
    }

    @Test
    void publishesUnderTheBoundStatuslistBaseUrlAfterTheFirstBoundLoad() {
        var bound = identity("DID-PUBLISH", "https://issuer.example/statuslist");
        var holder = new BoundIssuerIdentityHolder(() -> () -> Optional.of(bound), monitor, CHECK_INTERVAL);
        var publisher = new XRoadStatusListCredentialPublisher(holder);
        holder.start();

        var publishResult = publisher.publish(statusListCredential("credential-1"));

        assertThat(publishResult.succeeded()).isTrue();
        assertThat(publisher.publicUrl("credential-1")).isEqualTo("https://issuer.example/statuslist/credential-1");
    }

    private static BoundIssuerIdentity identity(String did, String statuslistBaseUrl) {
        return new BoundIssuerIdentity(did, "issuer.example", 7183, "https://issuer.example/issuance", statuslistBaseUrl);
    }
}
