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
package org.niis.xroad.restapi.service;

import org.junit.jupiter.api.Test;
import org.niis.xroad.restapi.openapi.model.ConfigurablePropertyRestartPolicy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestartTargetTest {

    @Test
    void servicesPolicyKeepsTheNames() {
        var target = RestartTarget.of("xroad-proxy", "xroad-proxy-ui-api");

        assertThat(target.policy()).isEqualTo(ConfigurablePropertyRestartPolicy.SERVICES);
        assertThat(target.services()).containsExactly("xroad-proxy", "xroad-proxy-ui-api");
    }

    @Test
    void servicesPolicyWithoutNamesIsRejected() {
        assertThatThrownBy(RestartTarget::of).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void policyOtherThanServicesWithNamesIsRejected() {
        var names = List.of("xroad-proxy");

        assertThatThrownBy(() -> new RestartTarget(ConfigurablePropertyRestartPolicy.NEXT_RUN, names))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RestartTarget(ConfigurablePropertyRestartPolicy.ALL_SERVICES, names))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void policiesWithoutNamesHaveNoServices() {
        assertThat(RestartTarget.allServices().services()).isEmpty();
        assertThat(RestartTarget.nextRun().policy()).isEqualTo(ConfigurablePropertyRestartPolicy.NEXT_RUN);
        assertThat(RestartTarget.nextRun().services()).isEmpty();
    }
}
