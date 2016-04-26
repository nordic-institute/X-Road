/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.monitor;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.codahale.metrics.JmxReporter;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.monitor.common.SystemMetricNames;
import lombok.extern.java.Log;

import java.util.concurrent.TimeUnit;

/**
 * Main class for monitor application
 */
@Log
public final class MonitorMain {

    private static final String CONFIG_FILENAME = "/etc/xroad/conf.d/monitor.ini";
    private static final String CONFIG_PROPERTY_PORT = "xroad.monitor.port";
    private static final String CONFIG_SECTION = "monitor";
    private static final String AKKA_PORT = "akka.remote.netty.tcp.port";

    private static ActorSystem actorSystem;

    /**
     * Main entry point
     *
     * @param args
     */
    public static void main(String args[]) {

        registerShutdownHook();
        
        loadConfiguration();

        initAkka();

        startReporters();
    }

    private MonitorMain() {
    }

    private static void loadConfiguration() {
        SystemPropertiesLoader.create().with(CONFIG_FILENAME, CONFIG_SECTION).load();
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                shutdownAkka();
            }
        });
    }

    private static void shutdownAkka() {
        if (actorSystem != null) {
            actorSystem.shutdown();
            actorSystem = null;
        }
    }

    private static void initAkka() {
        actorSystem = ActorSystem.create("xroad-monitor", loadAkkaConfiguration());
        actorSystem.actorOf(Props.create(MetricsProviderActor.class), "MetricsProviderActor");
        actorSystem.actorOf(Props.create(SystemMetricsSensor.class), "SystemMetricsSensor");
        actorSystem.actorOf(Props.create(DiskSpaceSensor.class), "DiskSpaceSensor");
        actorSystem.actorOf(Props.create(ExecListingSensor.class), "ExecListingSensor");
    }

    private static Config loadAkkaConfiguration() {
        Config externalConfig = ConfigFactory.empty();
        try {
            int port = Integer.parseUnsignedInt(System.getProperty(CONFIG_PROPERTY_PORT));
            externalConfig = ConfigFactory.parseString(String.format("%s = %d", AKKA_PORT, port));
        } catch (NumberFormatException e) {
            log.warning(String.format("Could not load configuration property %s - using the default port",
                    CONFIG_PROPERTY_PORT));
        }
        Config defaultConfig = ConfigFactory.load();
        Config mergedConfig = externalConfig.withFallback(defaultConfig);
        return ConfigFactory.load(mergedConfig);
    }

    private static void startReporters() {
        JmxReporter jmxReporter = JmxReporter.forRegistry(MetricRegistryHolder.getInstance().getMetrics())
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter((name, metric) ->
                        !Lists.newArrayList(SystemMetricNames.PROCESSES,
                                SystemMetricNames.PACKAGES).contains(name))
                .build();

        jmxReporter.start();
    }


}
