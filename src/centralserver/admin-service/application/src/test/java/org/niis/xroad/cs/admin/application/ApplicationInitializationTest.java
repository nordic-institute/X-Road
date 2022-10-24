/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.application;

import liquibase.database.core.HsqlDatabase;
import liquibase.repackaged.org.apache.commons.lang3.reflect.FieldUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.restapi.config.BootstrapConfiguration;
import org.niis.xroad.cs.admin.api.facade.GlobalConfFacade;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * application test
 */
@SpringBootTest(classes = {
        BootstrapConfiguration.class,
        ApplicationInitializationTest.ExtDataSourceProperties.class
})
@EnableAutoConfiguration
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE,
        connection = EmbeddedDatabaseConnection.HSQLDB
)
@ActiveProfiles({"test", "audit-test"})
public class ApplicationInitializationTest {

    /**
     * test that spring context loads
     */
    @Test
    @SuppressWarnings("squid:S2699") // false positive: test asserts that context loads without exceptions
    public void contextLoads() {
    }

    static {
        fixSupportedDefaultValueComputedMap();
    }

    @MockBean
    protected GlobalConfFacade globalConfFacade;

    @MockBean
    protected SignerProxyFacade signerProxyFacade;

    @Primary
    @Configuration
    @ConfigurationProperties(prefix = "spring.datasource")
    public static class ExtDataSourceProperties extends DataSourceProperties {

        @Getter
        @Setter
        private Map<String, String> urlProperties = new LinkedHashMap<>();

        @SneakyThrows
        public String determineUrl() {
            String url = super.determineUrl();
            url = appendPropertiesToUrl(url);
            return url;
        }

        private String appendPropertiesToUrl(String url) {
            for (Map.Entry<String, String> entry : this.urlProperties.entrySet()) {
                url += ";" + entry.getKey() + "=" + entry.getValue();
            }
            return url;
        }

    }

    @SneakyThrows
    private static void fixSupportedDefaultValueComputedMap() {
        Field supportedDefaultValueComputedMapField = FieldUtils.getDeclaredField(
                HsqlDatabase.class, "SUPPORTED_DEFAULT_VALUE_COMPUTED_MAP", true);
        Map<String, HashSet<String>> supportedDefaultValueComputedMap =
                (Map<String, HashSet<String>>) supportedDefaultValueComputedMapField.get(null);
        HashSet<String> set = supportedDefaultValueComputedMap.get("datetime");
        HashSet<String> modifiedSet = new HashSet<>();
        for (String value : set) {
            modifiedSet.add(value.toLowerCase());
        }
        set.clear();
        set.addAll(modifiedSet);
    }
}
