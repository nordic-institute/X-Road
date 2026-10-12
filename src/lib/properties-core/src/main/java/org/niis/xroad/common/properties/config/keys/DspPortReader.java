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

import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.XRoadConfig;

import static org.niis.xroad.common.core.exception.ErrorCode.VALIDATION_ERROR;

/**
 * Reads the stored DID and DSP ports ({@link DspConfigKeys#DID_PORT}, {@link DspConfigKeys#DSP_PORT}) and
 * checks that each is a valid TCP port. The check lives here, not in a config validator, so that a bad
 * stored value is reported to the caller instead of failing the whole process at start.
 */
public final class DspPortReader {

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private final XRoadConfig config;

    /**
     * The DID port and the DSP port.
     *
     * @param didPort the port of the identity hub's DID endpoint
     * @param dspPort the port of the control plane's DSP endpoint
     */
    public record DspPorts(int didPort, int dspPort) {
    }

    /**
     * @param config the resolver holding the stored overrides and the packaged defaults of {@link DspConfigKeys}
     */
    public DspPortReader(XRoadConfig config) {
        this.config = config;
    }

    /**
     * @return both ports, each between 1 and 65535
     * @throws XrdRuntimeException if either stored port is out of range
     */
    public DspPorts read() {
        return new DspPorts(port(DspConfigKeys.DID_PORT), port(DspConfigKeys.DSP_PORT));
    }

    private int port(ConfigKey<Integer> key) {
        var port = config.value(key);
        if (port < MIN_PORT || port > MAX_PORT) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR,
                    "%s '%d' must be between %d and %d", key.key(), port, MIN_PORT, MAX_PORT);
        }
        return port;
    }
}
