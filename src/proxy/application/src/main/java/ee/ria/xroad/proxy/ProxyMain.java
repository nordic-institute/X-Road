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

import ee.ria.xroad.common.SystemPropertySource;
import ee.ria.xroad.common.Version;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.proxy.configuration.ProxyConfig;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Main program for the proxy server.
 */
@Slf4j
@SpringBootApplication
public class ProxyMain {

    private static final String APP_NAME = "xroad-proxy";

    public static void main(String[] args) throws Exception {
        Version.outputVersionInfo(APP_NAME);

        new SpringApplicationBuilder(ProxyMain.class, ProxyConfig.class)
                .profiles("group-ee")//TODO load dynamically
                .initializers(applicationContext -> {
                    log.info("Initializing Apache Santuario XML Security library..");
                    org.apache.xml.security.Init.init();
                    log.info("Setting property source to Spring environment..");
                    SystemPropertySource.setEnvironment(applicationContext.getEnvironment());
                })
                .web(WebApplicationType.NONE)
                .build()
                .run(args);
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
