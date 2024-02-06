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

package org.niis.xroad.edc.extension.messagelog;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.niis.xroad.edc.spi.messagelog.XRoadMessageLog;

@Extension(value = XRoadMessageLogExtension.NAME)
@Provides(XRoadMessageLog.class)
public class XRoadMessageLogExtension implements ServiceExtension {

    static final String NAME = "X-Road Messagelog";

    @Setting
    private static final String XROAD_MESSAGELOG_GRPC_ENABLED = "xroad.messagelog.grpc.enabled";
    @Setting
    private static final String XROAD_MESSAGELOG_GRPC_SERVER_HOST = "xroad.messagelog.grpc.host";
    @Setting
    private static final String XROAD_MESSAGELOG_GRPC_SERVER_PORT = "xroad.messagelog.grpc.host";
    @Setting
    private static final String XROAD_MESSAGELOG_GRPC_SERVER_TIMEOUT = "xroad.messagelog.grpc.timeout";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    @SuppressWarnings("checkstyle:magicnumber")
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        XRoadMessageLogImpl messageLog;
        boolean isGrpcEnabled = context.getSetting(XROAD_MESSAGELOG_GRPC_ENABLED, false);
        if (isGrpcEnabled) {
            String host = context.getSetting(XROAD_MESSAGELOG_GRPC_SERVER_HOST, "127.0.0.1");
            int port = context.getSetting(XROAD_MESSAGELOG_GRPC_SERVER_PORT, 5561);
            int timeout = context.getSetting(XROAD_MESSAGELOG_GRPC_SERVER_TIMEOUT, 10_000);
            messageLog = new XRoadMessageLogImpl(monitor, host, port, timeout);
        } else {
            messageLog = new XRoadMessageLogImpl(monitor);
        }

        context.registerService(XRoadMessageLog.class, messageLog);
    }

}
