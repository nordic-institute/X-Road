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
package org.niis.xroad.edc.spi;

import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;

@Settings
public record XRoadPublicApiConfiguration(
        @Setting(key = "web.http." + XROAD_PUBLIC_API_CONTEXT + ".port", defaultValue = XROAD_PUBLIC_API_DEFAULT_PORT + "",
                description = "Port for " + XROAD_PUBLIC_API_CONTEXT + " api context")
        int port,
        @Setting(key = "web.http." + XROAD_PUBLIC_API_CONTEXT + ".path", defaultValue = XROAD_PUBLIC_API_DEFAULT_PATH,
                description = "Path for " + XROAD_PUBLIC_API_CONTEXT + " api context")
        String path,
        @Setting(key = "web.http." + XROAD_PUBLIC_API_CONTEXT + ".needClientAuth", defaultValue = "false",
                description = "mTLS conf for " + XROAD_PUBLIC_API_CONTEXT + " api context")
        boolean needClientAuth
) {
    public static final String XROAD_PUBLIC_API_CONTEXT = "xroad.public";
    public static final int XROAD_PUBLIC_API_DEFAULT_PORT = 9294;
    public static final String XROAD_PUBLIC_API_DEFAULT_PATH = "/xroad/public";
}
