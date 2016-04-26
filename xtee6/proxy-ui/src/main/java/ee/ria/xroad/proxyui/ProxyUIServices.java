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
package ee.ria.xroad.proxyui;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.commonui.UIServices;
import ee.ria.xroad.signer.protocol.SignerClient;

import static ee.ria.xroad.common.SystemProperties.*;

/**
 * Contains the UI actor system instance and configuration checker jobs.
 */
@Slf4j
public final class ProxyUIServices {

    private static final int JOB_REPEAT_INTERVAL = 30;

    static {
        SystemPropertiesLoader.create().withCommonAndLocal()
            .with(CONF_FILE_PROXY)
            .with(CONF_FILE_PROXY_UI)
            .with(CONF_FILE_SIGNER)
            .load();
    }

    private static UIServices uiActorSystem;
    private static JobManager jobManager;

    private ProxyUIServices() {
    }

    /**
     * Initializes the UI actor system and registers configuration checker jobs.
     * @throws Exception in case of any errors
     */
    public static void start() throws Exception {
        log.info("start()");

        if (uiActorSystem == null) {
            uiActorSystem = new UIServices("ProxyUI", "proxyui");
        }

        SignerClient.init(uiActorSystem.getActorSystem());

        if (jobManager == null) {
            jobManager = new JobManager();
            jobManager.registerRepeatingJob(
                    GlobalConfChecker.class, JOB_REPEAT_INTERVAL);
        }

        jobManager.start();
    }

    /**
     * Stops the UI actor system and running periodic jobs.
     * @throws Exception in case of any errors
     */
    public static void stop() throws Exception {
        if (uiActorSystem != null) {
            uiActorSystem.stop();
        }

        if (jobManager != null) {
            jobManager.stop();
        }
    }
}
