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
package org.niis.xroad.bootstrap;

import ee.ria.xroad.common.SystemPropertySource;
import ee.ria.xroad.common.Version;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@UtilityClass
public class XrdSpringServiceBuilder {
    private static final String ENV_XRD_CONFIG_SERVER_URI = "XROAD_CONFIG_SERVER_URI";

    private static final String ENV_DEPLOYMENT_TYPE = "XROAD_DEPLOYMENT_TYPE";
    private static final String ENV_APPLICATION_TYPE = "XROAD_APPLICATION_TYPE";
    private static final String ENV_ADDITIONAL_PROFILES = "XROAD_ADDITIONAL_PROFILES";

    //TODO xroad8 consider user spring application name instead of providing one
    public static SpringApplicationBuilder newApplicationBuilder(String appName, Class<?>... sources) {
        Version.outputVersionInfo(appName);

        var profiles = resolveProfiles();
        log.info("Preparing Spring initializer with profiles: {}", profiles);

        return new SpringApplicationBuilder(sources)
                .profiles(profiles.toArray(new String[0]))
                .properties(resolveProperties(profiles))
                .initializers(applicationContext -> {
                    log.info("Setting property source to Spring environment..");
                    //TODO xroad8 Remove once SystemProperties is removed
                    SystemPropertySource.setEnvironment(applicationContext.getEnvironment());
                });
    }

    private String[] resolveProperties(Set<String> profiles) {
        Set<String> properties = new HashSet<>();

        var imports = new StringBuilder("classpath:/xroad-common.yaml");
        if (profiles.contains(XrdSpringProfiles.CS)) {
            imports.append(",classpath:/xroad-cs-common.yaml");
        } else if (profiles.contains(XrdSpringProfiles.SS)) {
            imports.append(",classpath:/xroad-ss-common.yaml");
        }

        if (StringUtils.isNotBlank(System.getenv(ENV_XRD_CONFIG_SERVER_URI))) {
            imports.append(",configserver:%s".formatted(System.getenv(ENV_XRD_CONFIG_SERVER_URI)));

            properties.add("spring.cloud.config.enabled=true");
        } else {
            log.warn("No config server URI defined in environment variable {}", ENV_XRD_CONFIG_SERVER_URI);
        }
        properties.add("spring.config.import=" + imports);

        return properties.toArray(new String[0]);
    }

    private static Set<String> resolveProfiles() {
        Set<String> profiles = new HashSet<>();

        if (XrdSpringProfiles.CONTAINERIZED.equals(System.getenv(ENV_DEPLOYMENT_TYPE))) {
            profiles.add(XrdSpringProfiles.CONTAINERIZED);
        } else if (XrdSpringProfiles.NATIVE.equals(System.getenv(ENV_DEPLOYMENT_TYPE))) {
            profiles.add(XrdSpringProfiles.NATIVE);
        }

        String applicationType = System.getenv(ENV_APPLICATION_TYPE);
        if (applicationType != null) {
            switch (applicationType) {
                case XrdSpringProfiles.CS, XrdSpringProfiles.SS, XrdSpringProfiles.CP -> profiles.add(applicationType);
                default -> log.warn("Unknown application type: {}", applicationType);
            }
        }

        String additionalProfiles = System.getenv(ENV_ADDITIONAL_PROFILES);
        if (additionalProfiles != null) {
            var profilesToAdd = additionalProfiles.split(" ");
            profiles.addAll(List.of(profilesToAdd));
        }

        //Add last to override other profiles
        profiles.add(XrdSpringProfiles.OVERRIDE);

        return profiles;
    }

}
