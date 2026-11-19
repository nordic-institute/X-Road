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
package org.niis.xroad.securityserver.restapi.repository;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PendingAddressChangeRepositoryIntegrationTest extends AbstractFacadeMockingTestContext {

    private static final String FIRST_ADDRESS = "first.example.org";

    @Autowired
    private PendingAddressChangeRepository pendingAddressChangeRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void saveFailsWithDataIntegrityViolationWhenARowIsAlreadyStored() {
        jdbcTemplate.update("INSERT INTO pending_address_change (id, requested_address, requested_at)"
                + " VALUES (1, ?, CURRENT_TIMESTAMP)", FIRST_ADDRESS);

        assertThrows(DataIntegrityViolationException.class,
                () -> pendingAddressChangeRepository.save("second.example.org", Instant.now()));
    }

    @Test
    public void saveStoresTheRowWhenNoneIsStored() {
        pendingAddressChangeRepository.save(FIRST_ADDRESS, Instant.now());

        assertEquals(FIRST_ADDRESS, pendingAddressChangeRepository.findRequestedAddress().orElseThrow());
    }

}
