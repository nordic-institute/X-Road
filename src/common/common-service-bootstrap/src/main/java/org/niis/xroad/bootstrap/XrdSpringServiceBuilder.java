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
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@UtilityClass
public class XrdSpringServiceBuilder {
    private static final String ENV_DEPLOYMENT_TYPE = "XROAD_DEPLOYMENT_TYPE";
    private static final String ENV_APPLICATION_TYPE = "XROAD_APPLICATION_TYPE";
    private static final String ENV_ADDITIONAL_PROFILES = "XROAD_ADDITIONAL_PROFILES";

    //TODO perhaps introduce config dir location env var.
    private static final String CONFIG_GLOBAL_OVERRIDE_PATH = "/etc/xroad/conf.d/application-override.yaml";
    private static final String CONFIG_APP_OVERRIDE_PATH = "/etc/xroad/conf.d/%s-override.yaml";

    //TODO xroad8 consider user spring application name instead of providing one
    public static SpringApplicationBuilder newApplicationBuilder(String appName, Class<?>... sources) {
        Version.outputVersionInfo(appName);

        var profiles = resolveProfiles();
        log.info("Preparing Spring initializer with profiles: {}", profiles);

        return new SpringApplicationBuilder(sources)
                .profiles(profiles.toArray(new String[0]))
                .properties(resolveProperties(profiles, appName))
                .initializers(applicationContext -> {
                    log.info("Setting property source to Spring environment..");
                    //TODO xroad8 Remove once SystemProperties is removed
                    SystemPropertySource.setPropertyResolver(new SystemPropertySource.PropertyResolver() {
                        @Override
                        public String getProperty(String key) {
                            return applicationContext.getEnvironment().getProperty(key);
                        }

                        @Override
                        public String getProperty(String key, String defaultValue) {
                            return applicationContext.getEnvironment().getProperty(key, defaultValue);
                        }

                        @Override
                        public <T> T getProperty(String key, Class<T> targetType) {
                            return applicationContext.getEnvironment().getProperty(key, targetType);
                        }
                    });
                });
    }

    private String[] resolveProperties(Set<String> profiles, String appName) {
        Set<String> properties = new HashSet<>();

        properties.add(resolveConfigImportProperty(profiles, appName));

        return properties.toArray(new String[0]);
    }

    private String resolveConfigImportProperty(Set<String> profiles, String appName) {
        List<String> imports = new ArrayList<>();

        // Base import
        imports.add("classpath:/xroad-common.yaml");

        // Profile specific imports
        if (profiles.contains(XrdSpringProfiles.CS)) {
            imports.add("classpath:/xroad-cs-common.yaml");
        } else if (profiles.contains(XrdSpringProfiles.SS)) {
            imports.add("classpath:/xroad-ss-common.yaml");
        }

        // K8s import
        if (profiles.contains(XrdSpringProfiles.K8)) {
            imports.add("kubernetes:");
        }

        // Override imports
        imports.add("optional:file://" + CONFIG_GLOBAL_OVERRIDE_PATH);
        imports.add("optional:file://" + CONFIG_APP_OVERRIDE_PATH.formatted(appName));

        log.info("Registered configuration imports: {}", imports);
        return "spring.config.import=" + String.join(",", imports);
    }

    private static Set<String> resolveProfiles() {
        Set<String> profiles = new HashSet<>();

        if (XrdSpringProfiles.CONTAINERIZED.equals(System.getenv(ENV_DEPLOYMENT_TYPE))) {
            profiles.add(XrdSpringProfiles.CONTAINERIZED);
            profiles.add(XrdSpringProfiles.SECRET_STORE);
        } else if (XrdSpringProfiles.NATIVE.equals(System.getenv(ENV_DEPLOYMENT_TYPE))) {
            profiles.add(XrdSpringProfiles.NATIVE);
            profiles.add(XrdSpringProfiles.SECRET_STORE);
        }

        String applicationType = System.getenv(ENV_APPLICATION_TYPE);
        if (applicationType != null) {
            if (applicationType.equals(XrdSpringProfiles.CS) || applicationType.equals(XrdSpringProfiles.SS)) {
                profiles.add(applicationType);
            } else {
                log.warn("Unknown application type: {}", applicationType);
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
