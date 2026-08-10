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
package org.niis.xroad.common.rpc.client;

import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.XRoadConfig;

/** Base {@link RpcChannelProperties} implementation backed by the XRoadConfig DSL. */
public class XRoadRpcChannelProperties implements RpcChannelProperties {

    private final XRoadConfig config;
    private final ConfigKey<String> hostKey;
    private final ConfigKey<Integer> portKey;
    private final ConfigKey<Integer> deadlineAfterKey;

    public XRoadRpcChannelProperties(XRoadConfig config,
                                     ConfigKey<String> hostKey,
                                     ConfigKey<Integer> portKey,
                                     ConfigKey<Integer> deadlineAfterKey) {
        this.config = config;
        this.hostKey = hostKey;
        this.portKey = portKey;
        this.deadlineAfterKey = deadlineAfterKey;
    }

    @Override
    public String host() {
        return config.value(hostKey);
    }

    @Override
    public int port() {
        return config.value(portKey);
    }

    @Override
    public int deadlineAfter() {
        return config.value(deadlineAfterKey);
    }
}
