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
package ee.ria.xroad.signer;

import ee.ria.xroad.common.SystemPropertySource;
import ee.ria.xroad.common.Version;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Signer main program.
 */
@Slf4j
@SpringBootApplication
public class SignerMain {

    private static final String APP_NAME = "xroad-signer";

    public static void main(String[] args) {
        Version.outputVersionInfo(APP_NAME);


        new SpringApplicationBuilder(SignerMain.class, SignerConfig.class)
                .profiles(resolveProfiles())
                .initializers(applicationContext -> {
                    log.info("Setting property source to Spring environment..");
                    SystemPropertySource.setEnvironment(applicationContext.getEnvironment());
                })
                .web(WebApplicationType.NONE)
                .build()
                .run(args);
    }

    private static String[] resolveProfiles() {
        var xroadEnv = System.getenv("XROAD_ENV");

        List<String> profiles = new ArrayList<>();

        //TODO constants
        if ("security-server".equals(xroadEnv)) {
            profiles.add("env-ss");
        } else if ("central-server".equals(xroadEnv)) {
            profiles.add("env-cs");
        }

        profiles.add("group-ee"); //TODO add conditions

        profiles.add("override");
        return profiles.toArray(new String[0]);
    }
}
