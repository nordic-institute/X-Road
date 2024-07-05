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
package ee.ria.xroad.proxy;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.conf.serverconf.CachingServerConfImpl;
import ee.ria.xroad.common.conf.serverconf.ServerConf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import static ee.ria.xroad.common.SystemProperties.CONF_FILE_NODE;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_PROXY;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_SIGNER;

/**
 * Main program for the proxy server.
 */
@Slf4j
public class ProxyMain {

    private static final String APP_NAME = "xroad-proxy";

    /**
     * Main program entry point.
     *
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
    public static void main(String[] args) throws Exception {
        try {
            new ProxyMain().createApplicationContext();
        } catch (Exception ex) {
            log.error("Proxy failed to start", ex);
            throw ex;
        }
    }

    public GenericApplicationContext createApplicationContext(Class<?>... ctxExtension) {
        var startTime = System.currentTimeMillis();
        log.trace("startup()");
        Version.outputVersionInfo(APP_NAME);
        log.info("Starting proxy ({})...", readProxyVersion());

        log.trace("Loading global bean dependencies");
        loadSystemProperties();
        loadGlobalConf();

        var springCtx = new AnnotationConfigApplicationContext();
        springCtx.register(ProxyConfig.class);
        if (ctxExtension.length > 0) {
            springCtx.register(ctxExtension);
        }
        springCtx.refresh();
        springCtx.registerShutdownHook();
        log.info("Proxy started in {} ms", System.currentTimeMillis() - startTime);
        return springCtx;
    }

    protected void loadSystemProperties() {
        SystemPropertiesLoader.create()
                .withCommonAndLocal()
                .withAddOn()
                .with(CONF_FILE_PROXY)
                .with(CONF_FILE_SIGNER)
                .withLocalOptional(CONF_FILE_NODE)
                .load();

        org.apache.xml.security.Init.init();
    }

    protected void loadGlobalConf() {
        try {
            log.trace("loadConfigurations()");
            if (SystemProperties.getServerConfCachePeriod() > 0) {
                ServerConf.reload(new CachingServerConfImpl());
            }
        } catch (Exception e) {
            log.error("Failed to initialize configurations", e);
        }
    }

    /**
     * Return X-Road software version
     *
     * @return version string e.g. 6.19.0
     */
    public static String readProxyVersion() {
        return Version.XROAD_VERSION;
    }
}
