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
package ee.ria.xroad.common.db;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.Map;

import static org.niis.xroad.common.core.exception.ErrorCode.DATABASE_ERROR;

/**
 * Hibernate utility methods.
 */
@Slf4j
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class HibernateUtil {

    static SessionFactory createSessionFactory(String name, Map<String, String> hibernateProperties, Interceptor interceptor) {
        log.trace("Creating session factory for '{}'...", name);
        try {
            Configuration configuration = createEmptyConfiguration();
            if (interceptor != null) {
                configuration.setInterceptor(interceptor);
            }

            configuration
                    .configure()
                    .configure(name + ".hibernate.cfg.xml");

            if (hibernateProperties != null) {
                hibernateProperties.forEach((key, value) -> configuration.setProperty("hibernate." + key, value));
            } else {
                throw XrdRuntimeException.systemException(DATABASE_ERROR, "Database (%s) properties not found.", name);
            }
            return configuration.buildSessionFactory();
        } catch (Exception e) {
            log.error("Failed to create session factory", e);

            throw XrdRuntimeException.systemException(DATABASE_ERROR, e, "Error accessing database (%s)", name);
        }
    }

    static Configuration createEmptyConfiguration() {
        return new Configuration();
    }

    /**
     * Returns Hibernate batch size value if configured, otherwise the given default value.
     *
     * @param session          Hibernate session
     * @param defaultBatchSize default batch size
     * @return batch size
     */
    public static int getConfiguredBatchSize(Session session, int defaultBatchSize) {
        final Map<String, Object> props = session.getSessionFactory().getProperties();
        int configuredBatchSize = ConfigurationHelper.getInt(AvailableSettings.STATEMENT_BATCH_SIZE, props, defaultBatchSize);

        log.trace("Configured JDBC batch size is {}", configuredBatchSize);

        return configuredBatchSize;
    }
}
