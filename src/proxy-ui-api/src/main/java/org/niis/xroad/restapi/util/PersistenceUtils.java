/**
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
package org.niis.xroad.restapi.util;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.sql.DataSource;

/**
 * Util class for persistence context helper methods
 */
@Component
@Slf4j
public final class PersistenceUtils {

    private final EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    public PersistenceUtils(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Session getCurrentSession() {
        return entityManager.unwrap(Session.class);
    }

    private static final int MAX_EVICTION_ATTEMPTS = 10;
    private static final int WAIT_FOR_SOFT_EVICT_MILLIS = 3000;

    /**
     * Evict connection pool connections (used when restoring from backups, to prevent broken connections)
     * @throws InterruptedException if interrupted
     */
    public void evictPoolConnections() throws InterruptedException {
        log.info("resetting hikari datasource");
        HikariDataSource hikariDs = (HikariDataSource) dataSource;
        HikariPoolMXBean poolBean = hikariDs.getHikariPoolMXBean();
        int evictionAttempts = 0;
        int activeConnections;
        try {
            poolBean.suspendPool();
            do {
                poolBean.softEvictConnections();
                evictionAttempts++;
                // sleep a little
                Thread.currentThread().sleep(WAIT_FOR_SOFT_EVICT_MILLIS);
                activeConnections = poolBean.getActiveConnections();
                log.info("poolBean.softEvictConnections, activeConnections = {}, attempts = {}",
                        activeConnections,
                        evictionAttempts);
                log.debug("idleConnections = {}, threadsAwaitingConnection = {}, totalConnections = {} ",
                        poolBean.getIdleConnections(),
                        poolBean.getThreadsAwaitingConnection(),
                        poolBean.getTotalConnections());
                // since we're inside transaction, 1 connection is active
            } while (activeConnections > 1 && evictionAttempts < MAX_EVICTION_ATTEMPTS);
        } finally {
            poolBean.resumePool();
        }
        log.info("resetted hikari datasource");
        if (evictionAttempts >= MAX_EVICTION_ATTEMPTS && activeConnections > 1) {
            log.error("Could not soft evict all connections from HikariCP in {} attempts", evictionAttempts);
        }
    }

    public void flush() {
        entityManager.flush();
    }
}
