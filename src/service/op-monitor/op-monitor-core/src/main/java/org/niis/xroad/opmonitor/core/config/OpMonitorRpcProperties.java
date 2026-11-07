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

package org.niis.xroad.opmonitor.core.config;

import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.rpc.XRoadRpcProperties;

import java.nio.file.Path;
import java.util.Locale;

import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.RPC_FILE_IDENTITY_CERTIFICATE_PATH;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.RPC_FILE_IDENTITY_PRIVATE_KEY_PATH;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.RPC_FILE_IDENTITY_TRUSTED_CA_PATH;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.RPC_IDENTITY_SOURCE;

/** RPC properties of op-monitor: the only component that can take its TLS identity from files. */
public class OpMonitorRpcProperties extends XRoadRpcProperties {

    private final XRoadConfig xRoadConfig;

    public OpMonitorRpcProperties(XRoadConfig xRoadConfig) {
        super(xRoadConfig);
        this.xRoadConfig = xRoadConfig;
    }

    @Override
    public IdentitySource identitySource() {
        return IdentitySource.valueOf(xRoadConfig.value(RPC_IDENTITY_SOURCE).toUpperCase(Locale.ROOT));
    }

    @Override
    public FileIdentity fileIdentity() {
        return new FileIdentity(
                Path.of(xRoadConfig.value(RPC_FILE_IDENTITY_PRIVATE_KEY_PATH)),
                Path.of(xRoadConfig.value(RPC_FILE_IDENTITY_CERTIFICATE_PATH)),
                Path.of(xRoadConfig.value(RPC_FILE_IDENTITY_TRUSTED_CA_PATH)));
    }
}
