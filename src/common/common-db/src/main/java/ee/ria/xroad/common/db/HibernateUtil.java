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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.PrefixedProperties;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.internal.util.config.ConfigurationHelper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static ee.ria.xroad.common.ErrorCodes.X_DATABASE_ERROR;

/**
 * Hibernate utility methods.
 */
@Slf4j
public final class HibernateUtil {

    @Data
    private static class SessionFactoryCtx {
        private final SessionFactory sessionFactory;
    }

    private HibernateUtil() {
    }

    private static Map<String, SessionFactoryCtx> sessionFactoryCache = new HashMap<>();

    /**
     * Returns the session factory for the given session factory name.
     * If the session factory has not been already created, it is created and stored in the cache.
     *
     * @param name the name of the session factory
     * @return the session factory
     */
    public static synchronized SessionFactory getSessionFactory(String name) {
        return getSessionFactory(name, null);
    }

    /**
     * Returns the session factory for the given session factory name.
     * If the session factory has not been already created, it is created and stored in the cache.
     *
     * @param name        the name of the session factory
     * @param interceptor the interceptor to use on sessions created with this factory
     * @return the session factory
     */
    public static synchronized SessionFactory getSessionFactory(String name, Interceptor interceptor) {
        if (sessionFactoryCache.containsKey(name)) {
            return sessionFactoryCache.get(name).getSessionFactory();
        } else {
            try {
                SessionFactoryCtx ctx = createSessionFactoryCtx(name, interceptor);
                sessionFactoryCache.put(name, ctx);

                return ctx.getSessionFactory();
            } catch (Exception e) {
                log.error("Failed to create session factory", e);

                throw new CodedException(X_DATABASE_ERROR, e);
            }
        }
    }

    /**
     * Closes the session factory.
     *
     * @param name the name of the session factory to close
     */
    public static synchronized void closeSessionFactory(String name) {
        log.trace("closeSessionFactory({})", name);

        if (sessionFactoryCache.containsKey(name)) {
            closeSessionFactory(sessionFactoryCache.get(name));
            sessionFactoryCache.remove(name);
        }
    }

    /**
     * Closes all session factories in the cache. Should be called when the main program exits.
     */
    public static synchronized void closeSessionFactories() {
        log.trace("closeSessionFactories()");

        Collection<SessionFactoryCtx> sessionFactories = new ArrayList<>(sessionFactoryCache.values());

        for (SessionFactoryCtx ctx : sessionFactories) {
            closeSessionFactory(ctx);
        }

        sessionFactoryCache.clear();
    }

    private static void closeSessionFactory(SessionFactoryCtx ctx) {
        try {
            ctx.getSessionFactory().getCurrentSession().close();
        } catch (HibernateException e) {
            log.error("Error closing session", e);
        }

        try {
            ctx.getSessionFactory().close();
        } catch (HibernateException e) {
            log.error("Error closing session factory", e);
        }

    }

    private static SessionFactoryCtx createSessionFactoryCtx(String name, Interceptor interceptor) throws Exception {
        log.trace("Creating session factory for '{}'...", name);

        Configuration configuration = new Configuration();
        if (interceptor != null) {
            configuration.setInterceptor(interceptor);
        }

        configuration
                .configure()
                .configure(name + ".hibernate.cfg.xml");
        applyDatabasePropertyFile(configuration, name);
        applySystemProperties(configuration, name);

        SessionFactory sessionFactory = configuration.buildSessionFactory();

        return new SessionFactoryCtx(sessionFactory);
    }

    private static void applySystemProperties(Configuration configuration, String name) {
        final String prefix = name + ".hibernate.";
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                configuration.setProperty(key.substring(name.length() + 1), System.getProperty(key));
            }
        }
    }

    private static void applyDatabasePropertyFile(Configuration configuration, String name) throws IOException {
        try (InputStream in = new FileInputStream(SystemProperties.getDatabasePropertiesFile())) {
            final Properties extraProperties = new PrefixedProperties(name + ".");
            extraProperties.load(in);
            configuration.addProperties(extraProperties);
        }
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
        int configuredBatchSize = ConfigurationHelper.getInt(Environment.STATEMENT_BATCH_SIZE, props, defaultBatchSize);

        log.trace("Configured JDBC batch size is {}", configuredBatchSize);

        return configuredBatchSize;
    }
}
