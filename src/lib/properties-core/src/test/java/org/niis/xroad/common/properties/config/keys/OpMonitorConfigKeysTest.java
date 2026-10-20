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

package org.niis.xroad.common.properties.config.keys;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.ConfigCatalogue;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpMonitorConfigKeysTest {

    private static final List<String> KEPT_KEYS = List.of(
            "xroad.op-monitor.rpc.listen-address",
            "xroad.op-monitor.rpc.port",
            "xroad.op-monitor.rpc.max-inbound-message-size",
            "xroad.common-rpc.channel.op-monitor.host",
            "xroad.common-rpc.channel.op-monitor.port",
            "xroad.common-rpc.channel.op-monitor.deadline-after",
            "xroad.common-rpc.channel.op-monitor.max-inbound-message-size");

    private final List<String> catalogueKeys = ConfigCatalogue.from(ConfigKeyProviders.allProviders()).stream()
            .map(ConfigCatalogue.Entry::key)
            .toList();

    @Test
    void keptKeysAreInTheCatalogue() {
        assertThat(catalogueKeys).containsAll(KEPT_KEYS);
    }
}
