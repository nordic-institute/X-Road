package ee.cyber.sdsb.common.db;


import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.util.PrefixedProperties;

import static ee.cyber.sdsb.common.ErrorCodes.X_DATABASE_ERROR;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HibernateUtil {

    @Data
    private static class SessionFactoryCtx {
        private final SessionFactory sessionFactory;
        private final StandardServiceRegistry serviceRegistry;
    }

    private static Map<String, SessionFactoryCtx> sessionFactoryCache =
            new HashMap<>();

    public static final synchronized SessionFactory getSessionFactory(
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

    public static final synchronized void closeSessionFactory(String name) {
        log.trace("closeSessionFactory({})", name);

        if (sessionFactoryCache.containsKey(name)) {
            closeSessionFactory(sessionFactoryCache.get(name));
            sessionFactoryCache.remove(name);
        }
    }

    public static final synchronized void closeSessionFactories() {
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
