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
package org.niis.xroad.securityserver.restapi.repository;

import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;

/**
 * test ClientRepository
 */
@RunWith(SpringRunner.class)
@DataJpaTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
public class ExampleJpaTest {

    // TestEntityManager only works with DataJpaTests (?)
    // and DataJpaTests only inject jpa repositories (which we
    // dont have at least yet), so not sure how useful this
    // kind of tests will be
    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testTestEntityManager() {
        ServerConfType conf2 = new ServerConfType();
        conf2.setServerCode("from-test");
        conf2.setId(null);
        conf2.setOwner(null);
        ServerConfType confPersisted = testEntityManager.persistFlushFind(conf2);

        ServerConfType confLoad1 = testEntityManager.find(ServerConfType.class, 1L);
        assertEquals("TEST-INMEM-SS", confLoad1.getServerCode());

        ServerConfType confLoad2 = testEntityManager.find(ServerConfType.class, confPersisted.getId());
        assertEquals("from-test", confLoad2.getServerCode());
    }

    @Test
    @SuppressWarnings("squid:S2699") // false positive: test asserts that expected exception is thrown
    public void testThatConstraintsWork() {
        // null conf_id is allowed
        jdbcTemplate.update("INSERT INTO CLIENT (id, conf_id, identifier)"
                + " values (1000, null, null)");
        try {
            // foreign key constrain should break with conf_id = 1000 (does not exist)
            jdbcTemplate.update("INSERT INTO CLIENT (id, conf_id, identifier)"
                    + " values (2000, 1000, null)");

        } catch (DataIntegrityViolationException expected) { }
    }

}


