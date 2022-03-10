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
import org.niis.xroad.centralserver.restapi.entity.FlattenedSecurityServerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.stream.Collector;
import java.util.stream.Collectors;

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
    public void pagedSortedFindClientsBySecurityServerId() {
        PageRequest page = PageRequest.of(0, 2, Sort.by("id").descending());
        var clientsPage = repository.findAll(
                FlattenedSecurityServerClientRepository.securityServerId(1000001),
                page);
        assertEquals(2, clientsPage.getTotalPages());
        assertEquals(3, clientsPage.getTotalElements());
        assertEquals(2, clientsPage.getNumberOfElements());
        assertEquals(0, clientsPage.getNumber());
        assertEquals(Arrays.asList(1000010, 1000002),
                clientsPage.get().map(FlattenedSecurityServerClient::getId).collect(Collectors.toList()));

        page = page.next();
        clientsPage = repository.findAll(
                FlattenedSecurityServerClientRepository.securityServerId(1000001),
                page);
        assertEquals(1, clientsPage.getNumberOfElements());
        assertEquals(1, clientsPage.getNumber());
        assertEquals(Arrays.asList(1000001),
                clientsPage.get().map(FlattenedSecurityServerClient::getId).collect(Collectors.toList()));
    }

    @Test
    public void paging() {
        PageRequest page = PageRequest.of(0, 5);
        var memberPage = repository.findAll(
                FlattenedSecurityServerClientRepository.member(),
                page);
        assertEquals(2, memberPage.getTotalPages());
        assertEquals(9, memberPage.getTotalElements());
        assertEquals(5, memberPage.getNumberOfElements());
        assertEquals(0, memberPage.getNumber());

        page = page.next();
        memberPage = repository.findAll(
                FlattenedSecurityServerClientRepository.member(),
                page);
        assertEquals(2, memberPage.getTotalPages());
        assertEquals(9, memberPage.getTotalElements());
        assertEquals(4, memberPage.getNumberOfElements());
        assertEquals(1, memberPage.getNumber());

        page = page.next();
        memberPage = repository.findAll(
                FlattenedSecurityServerClientRepository.member(),
                page);
        assertEquals(2, memberPage.getTotalPages());
        assertEquals(9, memberPage.getTotalElements());
        assertEquals(0, memberPage.getNumberOfElements());
        assertEquals(2, memberPage.getNumber());
    }

    @Test
    public void sorting() {
        PageRequest page = PageRequest.of(0, 5, Sort.by("id"));
        var memberPage = repository.findAll(
                FlattenedSecurityServerClientRepository.member(),
                page);
        assertEquals(2, memberPage.getTotalPages());
        assertEquals(9, memberPage.getTotalElements());
        assertEquals(5, memberPage.getNumberOfElements());
        assertEquals(0, memberPage.getNumber());
        var pageClients = memberPage.stream().collect(Collectors.toList());
        assertEquals(5, pageClients.size());
    }

    @Test
    public void findClientsBySecurityServerId() {
        var clients = repository.findAll(
                FlattenedSecurityServerClientRepository.securityServerId(1000001));
        assertEquals(3, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.securityServerId(1000002));
        assertEquals(2, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.securityServerId(1));
        assertEquals(0, clients.size());

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
