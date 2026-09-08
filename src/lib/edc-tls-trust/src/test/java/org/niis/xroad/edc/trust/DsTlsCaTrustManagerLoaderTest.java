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
package org.niis.xroad.edc.trust;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;

import java.security.cert.CertificateException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DsTlsCaTrustManagerLoaderTest {

    private static final String INSTANCE_IDENTIFIER = "TEST";

    @Mock
    private GlobalConfProvider globalConfProvider;

    @Test
    void anEmptyListIsASuccessfulLoadThatRejectsEverything() throws Exception {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getApprovedDsTlsCas(INSTANCE_IDENTIFIER)).thenReturn(List.of());

        var loaded = new DsTlsCaTrustManagerLoader(globalConfProvider).load();

        assertThat(loaded.material()).isSameAs(RejectAllTrustManager.INSTANCE);
        assertThat(loaded.fingerprint()).isEqualTo(DsTlsCaTrustManagerLoader.REJECT_ALL_FINGERPRINT);
    }

    @Test
    void anUnreadableGlobalConfMakesTheLoaderThrowRatherThanFailClosedItself() {
        when(globalConfProvider.getInstanceIdentifier()).thenThrow(new IllegalStateException("globalconf unreachable"));

        assertThatThrownBy(() -> new DsTlsCaTrustManagerLoader(globalConfProvider).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("globalconf unreachable");
    }

    @Test
    void aNonEmptyListBuildsATrustManagerThatAcceptsAChainFromTheListedCa() throws Exception {
        var ca = TestCa.selfSigned("Listed DS TLS CA");
        var leaf = ca.issueLeaf("ds.example");
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getApprovedDsTlsCas(INSTANCE_IDENTIFIER))
                .thenReturn(List.of(dsTlsCaInfo("listed", ca)));

        var loaded = new DsTlsCaTrustManagerLoader(globalConfProvider).load();

        assertThatCode(() -> loaded.material().checkServerTrusted(leaf.chain(), "RSA"))
                .doesNotThrowAnyException();
    }

    @Test
    void aNonEmptyListRejectsAChainFromAnUnlistedCa() throws Exception {
        var listedCa = TestCa.selfSigned("Listed DS TLS CA");
        var unlistedCa = TestCa.selfSigned("Unlisted CA");
        var unlistedLeaf = unlistedCa.issueLeaf("ds.example");
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getApprovedDsTlsCas(INSTANCE_IDENTIFIER))
                .thenReturn(List.of(dsTlsCaInfo("listed", listedCa)));

        var loaded = new DsTlsCaTrustManagerLoader(globalConfProvider).load();

        assertThatThrownBy(() -> loaded.material().checkServerTrusted(unlistedLeaf.chain(), "RSA"))
                .isInstanceOf(CertificateException.class);
    }

    @Test
    void theFingerprintChangesWhenTheListChanges() throws Exception {
        var firstCa = TestCa.selfSigned("First CA");
        var secondCa = TestCa.selfSigned("Second CA");
        var loader = new DsTlsCaTrustManagerLoader(globalConfProvider);
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);

        when(globalConfProvider.getApprovedDsTlsCas(INSTANCE_IDENTIFIER)).thenReturn(List.of(dsTlsCaInfo("a", firstCa)));
        var firstLoad = loader.load();

        when(globalConfProvider.getApprovedDsTlsCas(INSTANCE_IDENTIFIER)).thenReturn(List.of(dsTlsCaInfo("a", secondCa)));
        var secondLoad = loader.load();

        assertThat(firstLoad.fingerprint()).isNotEqualTo(secondLoad.fingerprint());
    }

    @Test
    void theFingerprintIsStableForTheSameList() throws Exception {
        var ca = TestCa.selfSigned("Stable CA");
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getApprovedDsTlsCas(INSTANCE_IDENTIFIER)).thenReturn(List.of(dsTlsCaInfo("a", ca)));
        var loader = new DsTlsCaTrustManagerLoader(globalConfProvider);

        assertThat(loader.load().fingerprint()).isEqualTo(loader.load().fingerprint());
    }

    private static ApprovedDsTlsCaInfo dsTlsCaInfo(String name, TestCa ca) {
        return new ApprovedDsTlsCaInfo(name, ca.certificate(), List.of(), null, null, null);
    }
}
