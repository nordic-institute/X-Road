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
package org.niis.xroad.proxy.controlplane;

import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.rpc.client.XRoadRpcChannelProperties;

/** gRPC channel configuration for the asset access service connection. */
/**
 * The control plane's agreement-grant lookup lives on the same gRPC server as asset access, so host and port are
 * shared; only the deadline differs, because this lookup runs on the data-flow start path where the token is
 * optional and must fail fast.
 */
public class AgreementGrantRpcChannelProperties extends XRoadRpcChannelProperties {

    public AgreementGrantRpcChannelProperties(XRoadConfig config) {
        super(config,
                CommonRpcConfigKeys.CHANNEL_ASSET_ACCESS_HOST,
                CommonRpcConfigKeys.CHANNEL_ASSET_ACCESS_PORT,
                CommonRpcConfigKeys.CHANNEL_AGREEMENT_GRANT_DEADLINE_AFTER);
    }

    public AgreementGrantRpcChannelProperties() {
        this(null);
    }
}
