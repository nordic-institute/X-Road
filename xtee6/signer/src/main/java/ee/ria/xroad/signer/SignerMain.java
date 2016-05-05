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
package ee.ria.xroad.signer;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.util.AdminPort;
import lombok.extern.slf4j.Slf4j;

import static ee.ria.xroad.common.SystemProperties.CONF_FILE_PROXY;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_SIGNER;
import static ee.ria.xroad.signer.protocol.ComponentNames.SIGNER;

/**
 * Signer main program.
 */
@Slf4j
public final class SignerMain {

    static {
        SystemPropertiesLoader.create().withCommonAndLocal()
            .with(CONF_FILE_PROXY)
            .with(CONF_FILE_SIGNER)
            .load();
    }

    private static ActorSystem actorSystem;
    private static Signer signer;
    private static AdminPort adminPort;

    private SignerMain() {
    }

    /**
     * Entry point to Signer.
     * @param args the arguments
     * @throws Exception if an error occurs
     */
    public static void main(String[] args) throws Exception {
        try {
            startup();
        } catch (Exception fatal) {
            log.error("FATAL", fatal);
            System.exit(1);
        }
    }

    private static void startup() throws Exception {
        int signerPort = SystemProperties.getSignerPort();

        log.info("Starting Signer on port {}...", signerPort);

        adminPort = createAdminPort(signerPort + 1);

        actorSystem = ActorSystem.create(SIGNER, getConf(signerPort));
        adminPort.start();

        signer = new Signer(actorSystem);
        signer.start();

        actorSystem.awaitTermination();

        shutdown();
    }

    private static void shutdown() {
        log.info("Signer shutting down...");

        try {
            signer.stop();
            signer.join();
        } catch (Exception e) {
            log.error("Error stopping signer", e);
        }

        try {
            adminPort.stop();
            adminPort.join();
        } catch (Exception e) {
            log.error("Error stopping admin port", e);
        }

        actorSystem.shutdown();
    }

    private static AdminPort createAdminPort(int signerPort) {
        AdminPort port = new AdminPort(signerPort);

        port.addHandler("/execute", new AdminPort.SynchronousCallback() {
            @Override
            public void run() {
                try {
                    signer.execute();
                } catch (Exception ex) {
                    log.error("error occurred in execute handler: {}", ex);
                }
            }
        });

        port.addShutdownHook(SignerMain::shutdown);

        return port;
    }

    private static Config getConf(int signerPort) {
        Config conf = ConfigFactory.load().getConfig("signer-main");
        return conf.withValue("akka.remote.netty.tcp.port",
                ConfigValueFactory.fromAnyRef(signerPort));
    }
}
