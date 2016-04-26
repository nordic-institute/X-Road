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
package ee.ria.xroad.commonui;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.SystemPropertiesLoader;

import static com.typesafe.config.ConfigValueFactory.fromAnyRef;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_CENTER;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_SIGNER;

/**
 * Encapsulates actor system management in UI.
 */
public final class UIServices {

    private static final Logger LOG = LoggerFactory.getLogger(UIServices.class);

    static {
        SystemPropertiesLoader.create().withCommonAndLocal()
            .with(CONF_FILE_CENTER)
            .with(CONF_FILE_SIGNER)
            .load();
    }

    private String configName;
    private ActorSystem actorSystem;

    /**
     * Creates the instance using the provided actor system name and
     * configuration name.
     * @param actorSystemName the actor system name
     * @param configName the configuration name
     */
    public UIServices(String actorSystemName, String configName) {
        this.configName = configName;

        LOG.debug("Creating ActorSystem...");

        // TODO this hardcoded configuration should ideally be loaded from
        // application.conf file
        Config config = config(new String[][] {
                {"akka.remote.quarantine-systems-for", "off"},
                {"akka.remote.gate-invalid-addresses-for", "2s"},
        });

        LOG.debug("Akka using configuration: {}", config);
        actorSystem = ActorSystem.create(actorSystemName, config);
    }

    /**
     * @return the actor system
     */
    public ActorSystem getActorSystem() {
        return actorSystem;
    }

    /**
     * Stops the actor system.
     * @throws Exception if an error occurs
     */
    public void stop() throws Exception {
        LOG.info("stop()");

        if (actorSystem != null) {
            actorSystem.shutdown();
        }
    }

    private Config config(String[][] config) {
        Config result = ConfigFactory.load().getConfig(configName);
        for (String[] keyValue : config) {
            result = result.withValue(keyValue[0], fromAnyRef(keyValue[1]));
        }

        return result;
    }
}
