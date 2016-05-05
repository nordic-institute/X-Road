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
package ee.ria.xroad.common.db;


import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.PrefixedProperties;

import static ee.ria.xroad.common.ErrorCodes.X_DATABASE_ERROR;

/**
 * Hibernate utility methods.
 */
@Slf4j
public final class HibernateUtil {

    @Data
    private static class SessionFactoryCtx {
        private final SessionFactory sessionFactory;
        private final StandardServiceRegistry serviceRegistry;
    }

    private HibernateUtil() {
    }

    private static Map<String, SessionFactoryCtx> sessionFactoryCache =
            new HashMap<>();

    /**
     * Returns the session factory for the given session factory name.
     * If the session factory has not been already created, it is created
     * and stored in the cache.
     * @param name the name of the session factory
     * @return the session factory
     */
    public static synchronized SessionFactory getSessionFactory(
            String name) {
        if (sessionFactoryCache.containsKey(name)) {
            return sessionFactoryCache.get(name).getSessionFactory();
        } else {
            try {
                SessionFactoryCtx ctx = createSessionFactoryCtx(name);
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
     * Closes all session factories in the cache. Should be called when the
     * main program exits.
     */
    public static synchronized void closeSessionFactories() {
        log.trace("closeSessionFactories()");

        Collection<SessionFactoryCtx> sessionFactories =
                new ArrayList<>(sessionFactoryCache.values());
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

        StandardServiceRegistryBuilder.destroy(ctx.getServiceRegistry());
    }

    private static SessionFactoryCtx createSessionFactoryCtx(String name)
            throws Exception {
        log.trace("Creating session factory for '{}'...", name);

        Configuration configuration = getDefaultConfiguration(name);
        configuration.configure("hibernate.cfg.xml");
        configuration.configure(name + ".hibernate.cfg.xml");

        StandardServiceRegistry serviceRegistry =
                new StandardServiceRegistryBuilder().applySettings(
                        configuration.getProperties()).build();

        SessionFactory sessionFactory =
                configuration.buildSessionFactory(serviceRegistry);

        return new SessionFactoryCtx(sessionFactory, serviceRegistry);
    }

    private static Configuration getDefaultConfiguration(String name)
            throws Exception {
        String databaseProps = SystemProperties.getDatabasePropertiesFile();

        Properties extraProperties = new PrefixedProperties(name + ".");

        try (InputStream in = new FileInputStream(databaseProps)) {
            extraProperties.load(in);
        }

        Configuration configuration = new Configuration();
        configuration.addProperties(extraProperties);
        return configuration;
    }
}
