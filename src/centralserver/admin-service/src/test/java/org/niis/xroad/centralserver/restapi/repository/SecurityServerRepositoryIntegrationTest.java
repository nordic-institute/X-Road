/**
 * The MIT License
 * <p>
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
package org.niis.xroad.centralserver.restapi.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;
import static org.niis.xroad.centralserver.restapi.entity.SecurityServer_.SERVER_CODE;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@WithMockUser
public class SecurityServerRepositoryIntegrationTest {

    @Autowired
    private SecurityServerRepository repository;

    @Test
    public void testFindAll() {
        var servers = repository.findAll(
                SecurityServerRepository.multifieldSearch("ADMINSS"));
        assertEquals(1, servers.size());

        PageRequest page = PageRequest.of(0, 1, Sort.by("id").descending());
        var serversPage = repository.findAll(
                SecurityServerRepository.multifieldSearch("ADMINSS"),
                page);
        assertEquals(1, serversPage.getTotalPages());
        assertEquals(1, serversPage.getTotalElements());
        assertEquals(1, serversPage.getNumberOfElements());
    }

    @Test
    public void testFindAllNonExisting() {
        PageRequest page = PageRequest.of(0, 25, Sort.by(SERVER_CODE).ascending());
        var servers =
                repository.findAll(SecurityServerRepository.multifieldSearch("NOTexisting"), Pageable.unpaged());
        assertEquals(1, servers.getTotalPages());
        assertEquals(0, servers.getTotalElements());
    }
}
