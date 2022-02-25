/**
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
package org.niis.xroad.centralserver.restapi.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@WithMockUser
public class FlattenedSecurityServerClientRepositoryIntegrationTest {

    @Autowired
    private FlattenedSecurityServerClientRepository repository;

    @Test
    public void testView() {
        var clients = repository.findAll();
        assertEquals(10, clients.size());
    }

    @Test
    public void findClientsByMemberName() {
        String memberName = "Member1";
        var clients = repository.findAll(
                FlattenedSecurityServerClientRepository.subsystemWithMembername(memberName));
        // one subsystem
        assertEquals(1, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.memberWithMemberName(memberName));
        // one member
        assertEquals(1, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.clientWithMemberName(memberName));
        // one member and one subsystem
        assertEquals(2, clients.size());
    }

    @Test
    public void findAll() {
        var clients = repository.findAll();
        assertEquals(10, clients.size());
    }

    @Test
    public void sort() {
        var clients = repository.findAll(Sort.by("memberName"));
        assertEquals(10, clients.size());
        assertEquals("Member1", clients.get(0).getMemberName());

        clients = repository.findAll(Sort.by("memberName").descending());
        assertEquals(10, clients.size());
        assertEquals("Member9", clients.get(0).getMemberName());

    }


    @Test
    public void findByType() {
        var clients = repository.findAll(
                FlattenedSecurityServerClientRepository.member());
        assertEquals(9, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.subsystem());
        assertEquals(1, clients.size());
    }

}
