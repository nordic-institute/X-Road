/**
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
package ee.ria.xroad.proxymonitor;

import ee.ria.xroad.proxy.addon.AddOn;
import ee.ria.xroad.proxymonitor.util.MonitorClient;
import ee.ria.xroad.proxymonitor.util.ProxyMonitorAgent;

import akka.actor.ActorSystem;
import akka.actor.Props;
import lombok.extern.slf4j.Slf4j;

/**
 *  ProxyMonitor initialization
 */
@Slf4j
public class ProxyMonitor implements AddOn {

    private static final String CONFIG_PROPERTY_PORT = "xroad.monitor.port";
    private static final int DEFAULT_PORT = 2552;

    private static volatile MonitorClient monitorClient;

    @Override
    public void init(final ActorSystem system) {
        monitorClient = new MonitorClient(
                system.actorSelection(getMonitorAddress() + "/user/MetricsProviderActor"));
        system.actorOf(Props.create(ProxyMonitorAgent.class), "ProxyMonitorAgent");
    }

    public static MonitorClient getClient() {
        return monitorClient;
    }

    static void setTestClient(MonitorClient testMonitorClient) {
        ProxyMonitor.monitorClient = testMonitorClient;
    }

    private String getMonitorAddress() {
        int port = DEFAULT_PORT;
        try {
            port = Integer.parseUnsignedInt(System.getProperty(CONFIG_PROPERTY_PORT));
        } catch (NumberFormatException e) {
            log.warn(String.format("Could not load configuration property %s - using the default port",
                    CONFIG_PROPERTY_PORT));
        }
        return String.format("akka://xroad-monitor@127.0.0.1:%d", port);
    }
}
