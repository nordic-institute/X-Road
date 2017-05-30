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
package ee.ria.xroad.confproxy;

import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import akka.actor.ActorSystem;

import com.typesafe.config.ConfigFactory;

import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.confproxy.util.ConfProxyHelper;
import ee.ria.xroad.signer.protocol.SignerClient;

import static ee.ria.xroad.common.SystemProperties.CONF_FILE_CONFPROXY;

/**
 * Main program for the configuration proxy.
 */
@Slf4j
public final class ConfProxyMain {

    static {
        SystemPropertiesLoader.create().withCommonAndLocal()
            .with(CONF_FILE_CONFPROXY, "configuration-proxy")
            .load();
    }

    private static ActorSystem actorSystem;

    /**
     * Unavailable utility class constructor.
     */
    private ConfProxyMain() { }

    /**
     * Configuration proxy program entry point.
     * @param args program args
     * @throws Exception in case configuration proxy fails to start
     */
    public static void main(final String[] args) throws Exception {
        try {
            setup();
            execute(args);
        } catch (Exception e) {
            log.error("Configuration proxy failed to start", e);
            throw e;
        } finally {
            shutdown();
        }
    }

    /**
     * Initialize configuration proxy components.
     * @throws Exception if initialization fails
     */
    private static void setup() throws Exception {
        log.trace("startup()");

        actorSystem = ActorSystem.create("ConfigurationProxy",
                ConfigFactory.load().getConfig("configuration-proxy"));

        SignerClient.init(actorSystem);
    }

    /**
     * Executes all configuration proxy instances in sequence.
     * @param args program arguments
     * @throws Exception if not able to get list of available instances
     */
    private static void execute(final String[] args) throws Exception {
        List<String> instances;

        if (args.length > 0) {
            instances = Arrays.asList(args);
        } else {
            instances = ConfProxyHelper.availableInstances();
        }

        for (String instance: instances) {
            try {
                ConfProxy proxy = new ConfProxy(instance);
                proxy.execute();
            } catch (Exception ex) {
                log.error("Error when executing configuration-proxy '{}': {}",
                        instance, ex);
            }
        }
    }

    /**
     * Shutdown configuration proxy components.
     */
    private static void shutdown() {
        log.trace("shutdown()");

        actorSystem.shutdown();
    }
}
