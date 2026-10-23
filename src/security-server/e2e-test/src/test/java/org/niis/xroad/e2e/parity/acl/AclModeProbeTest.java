/*
 * The MIT License
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

package org.niis.xroad.e2e.parity.acl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AclModeProbeTest {

    @Test
    void storedFalseIsLegacy() {
        assertThat(AclModeProbe.modeOf("false")).isEqualTo(AclMode.LEGACY);
        assertThat(AclModeProbe.modeOf(" FALSE\n")).isEqualTo(AclMode.LEGACY);
    }

    @Test
    void storedTrueOrNoValueIsDsp() {
        assertThat(AclModeProbe.modeOf("true")).isEqualTo(AclMode.DSP);
        assertThat(AclModeProbe.modeOf("")).isEqualTo(AclMode.DSP);
        assertThat(AclModeProbe.modeOf(null)).isEqualTo(AclMode.DSP);
    }

    @Test
    void anyOtherStoredValueIsRejected() {
        assertThatThrownBy(() -> AclModeProbe.modeOf("maybe")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void matchingModeReportsWhichModeRuns() {
        assertThat(AclModeProbe.verify(AclMode.LEGACY, "false")).contains("LEGACY").contains("false");
        assertThat(AclModeProbe.verify(AclMode.DSP, "")).contains("DSP").contains("none (default)");
    }

    @Test
    void legacyRequestedButDspRunningFails() {
        assertThatThrownBy(() -> AclModeProbe.verify(AclMode.LEGACY, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LEGACY").hasMessageContaining("DSP");
        assertThatThrownBy(() -> AclModeProbe.verify(AclMode.LEGACY, ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void dspRequestedButLegacyRunningFails() {
        assertThatThrownBy(() -> AclModeProbe.verify(AclMode.DSP, "false"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DSP").hasMessageContaining("LEGACY");
    }
}
