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

import ee.ria.xroad.common.identifier.XRoadObjectType;

import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.restapi.entity.FlattenedSecurityServerClientView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@WithMockUser
public class FlattenedSecurityServerClientViewRepositoryTest {

    public static final int CLIENTS_TOTAL_COUNT = 18;
    public static final int SUBSYSTEMS_TOTAL_COUNT = 3;
    public static final int MEMBERS_TOTAL_COUNT = CLIENTS_TOTAL_COUNT - SUBSYSTEMS_TOTAL_COUNT;
    @Autowired
    private FlattenedSecurityServerClientRepository repository;

    @Test
    public void findUsingSpecialCharacters() {
        // free text search using % and _ which have special handling in LIKE queries
        // Member6\a
        // Member7_a
        // Member8%a
        // Member9__%%em%
        List<FlattenedSecurityServerClientView> clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("%"));
        assertEquals(2, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("_"));
        assertEquals(2, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("\\"));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("%%"));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("%em%"));
        assertEquals(1, clients.size());
    }

    @Test
    public void multifieldTextSearch() {
        // member name, member_class, member_code, subsystem_code

        // member name
        List<FlattenedSecurityServerClientView> clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("Member1"));
        assertEquals(4, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("Member2"));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("member1"));
        assertEquals(4, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("member"));
        assertEquals(12, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("ÅÖÄ"));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("åöä"));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("ÅöÄ"));
        assertEquals(1, clients.size());

        // member class
        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("MemberclassFoo"));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("MemberCLASS"));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("gOv"));
        assertEquals(CLIENTS_TOTAL_COUNT - 5, clients.size());

        // member code
        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("m1"));
        assertEquals(4, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("m4"));
        assertEquals(1, clients.size());

        // subsystem code
        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("Ss1"));
        assertEquals(1, clients.size());
    }

    @Test
    public void findClientsByInstance() {
        List<FlattenedSecurityServerClientView> clients = repository.findAll(
                FlattenedSecurityServerClientRepository.instance("teS"));
        assertEquals(CLIENTS_TOTAL_COUNT - 1, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.instance("teStFOO"));
        assertEquals(0, clients.size());
    }

    @Test
    public void findClientsByMemberClass() {
        List<FlattenedSecurityServerClientView> clients = repository.findAll(
                FlattenedSecurityServerClientRepository.memberClass("CLASSfoo"));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.memberClass("gOV"));
        // other clients: 4 ORG, 1 MemberclassFoo
        assertEquals(CLIENTS_TOTAL_COUNT - 5, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.memberClass("gOVi"));
        assertEquals(0, clients.size());
    }

    @Test
    public void findClientsByMemberCode() {
        List<FlattenedSecurityServerClientView> clients = repository.findAll(
                FlattenedSecurityServerClientRepository.memberCode("m1"));
        assertEquals(4, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.memberCode("m4"));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.memberCode("m"));
        // M1 - M11 + subsystem
        assertEquals(12, clients.size());
    }

    @Test
    public void findClientsBySubsystemCode() {
        List<FlattenedSecurityServerClientView> clients = repository.findAll(
                FlattenedSecurityServerClientRepository.subsystemCode("ss"));
        assertEquals(1, clients.size());
    }

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
                clientsPage.get().map(FlattenedSecurityServerClientView::getId).collect(Collectors.toList()));

        page = page.next();
        clientsPage = repository.findAll(
                FlattenedSecurityServerClientRepository.securityServerId(1000001),
                page);
        assertEquals(1, clientsPage.getNumberOfElements());
        assertEquals(1, clientsPage.getNumber());
        assertEquals(Arrays.asList(1000001),
                clientsPage.get().map(FlattenedSecurityServerClientView::getId).collect(Collectors.toList()));
    }

    @Test
    public void paging() {
        PageRequest page = PageRequest.of(0, 4);
        var memberPage = repository.findAll(
                FlattenedSecurityServerClientRepository.member(),
                page);
        assertEquals(4, memberPage.getTotalPages());
        assertEquals(MEMBERS_TOTAL_COUNT, memberPage.getTotalElements());
        assertEquals(4, memberPage.getNumberOfElements());
        assertEquals(0, memberPage.getNumber());

        page = page.next();
        memberPage = repository.findAll(
                FlattenedSecurityServerClientRepository.member(),
                page);
        assertEquals(4, memberPage.getNumberOfElements());
        assertEquals(1, memberPage.getNumber());

        page = page.next().next();
        memberPage = repository.findAll(
                FlattenedSecurityServerClientRepository.member(),
                page);
        assertEquals(3, memberPage.getNumberOfElements());
        assertEquals(3, memberPage.getNumber());
    }

    @Test
    public void sorting() {
        PageRequest page = PageRequest.of(0, 5, Sort.by("id"));
        Page<FlattenedSecurityServerClientView> memberPage = repository.findAll(
                FlattenedSecurityServerClientRepository.member(),
                page);
        assertEquals(3, memberPage.getTotalPages());
        assertEquals(MEMBERS_TOTAL_COUNT, memberPage.getTotalElements());
        assertEquals(5, memberPage.getNumberOfElements());
        assertEquals(0, memberPage.getNumber());
        var pageClients = memberPage.stream().collect(Collectors.toList());
        assertEquals(5, pageClients.size());
    }

    @Test
    public void findClientsBySecurityServerId() {
        List<FlattenedSecurityServerClientView> clients = repository.findAll(
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
    public void findClientsBySecurityServerIdAndFreetext() {
        List<FlattenedSecurityServerClientView> clients = repository.findAll(
                (root, query, builder) -> builder.and(
                    FlattenedSecurityServerClientRepository.clientOfSecurityServerPredicate(root, builder, 1000001),
                    FlattenedSecurityServerClientRepository.multifieldTextSearchPredicate(root, builder, "ss1")
            ));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                (root, query, builder) -> builder.and(
                        FlattenedSecurityServerClientRepository.clientOfSecurityServerPredicate(root, builder, 1000001),
                        FlattenedSecurityServerClientRepository.multifieldTextSearchPredicate(root, builder, "gov")
                ));
        assertEquals(3, clients.size());

        PageRequest page = PageRequest.of(1, 1, Sort.by("id"));
        var clientsPage = repository.findAll(
                (root, query, builder) -> builder.and(
                        FlattenedSecurityServerClientRepository.clientOfSecurityServerPredicate(root, builder, 1000001),
                        FlattenedSecurityServerClientRepository.multifieldTextSearchPredicate(root, builder, "gov")
                ), page);
        assertEquals(3, clientsPage.getTotalPages());
        assertEquals(3, clientsPage.getTotalElements());
        assertEquals(1, clientsPage.getNumberOfElements());
        assertEquals(1, clientsPage.getNumber());
    }

    @SuppressWarnings("checkstyle:MethodLength") // I think it makes sense to test all of these in same test
    @Test
    public void findClientsByMultiParameterSearch() {
        List<FlattenedSecurityServerClientView> clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                ));
        assertEquals(CLIENTS_TOTAL_COUNT, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                        .setSecurityServerId(1000001)
                ));
        assertEquals(3, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setSecurityServerId(1000001)
                                .setMultifieldSearch("ss1")
                ));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setSecurityServerId(1000001)
                                .setMemberCodeSearch("m1")
                ));
        assertEquals(2, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setSecurityServerId(1000001)
                                .setMultifieldSearch("ss1")
                                .setMemberCodeSearch("m1")
                ));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setSecurityServerId(1000001)
                                .setMultifieldSearch("ss1")
                                .setMemberCodeSearch("m1-does-not-exist")
                ));
        assertEquals(0, clients.size());

        // memberClass
        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setSecurityServerId(1000001)
                                .setMemberClassSearch("gov")
                ));
        assertEquals(3, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setSecurityServerId(1000001)
                                .setMemberClassSearch("gov")
                                .setMultifieldSearch("ss1")
                ));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setMemberCodeSearch("m1")
                                .setMemberClassSearch("gov")
                ));
        assertEquals(3, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setMemberCodeSearch("m2")
                                .setMemberClassSearch("foo")
                ));
        assertEquals(0, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setMemberCodeSearch("m1")
                                .setMemberClassSearch("foo")
                ));
        assertEquals(1, clients.size());

        // instance
        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setInstanceSearch("e")
                ));
        assertEquals(CLIENTS_TOTAL_COUNT, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setInstanceSearch("instance2")
                ));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setMemberCodeSearch("m1")
                                .setMemberClassSearch("gov")
                                .setInstanceSearch("test")
                ));
        assertEquals(2, clients.size());

        // subsystemCode
        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setSubsystemCodeSearch("s1")
                ));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setSubsystemCodeSearch("s1")
                                .setMemberCodeSearch("m1")
                ));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setSubsystemCodeSearch("s1")
                                .setMemberCodeSearch("m2")
                ));
        assertEquals(0, clients.size());

        // clientType
        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setClientType(XRoadObjectType.MEMBER)
                ));
        assertEquals(MEMBERS_TOTAL_COUNT, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setClientType(XRoadObjectType.SUBSYSTEM)
                ));
        assertEquals(SUBSYSTEMS_TOTAL_COUNT, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setClientType(XRoadObjectType.MEMBER)
                                .setMemberCodeSearch("m1")
                ));
        assertEquals(3, clients.size());

        try {
            repository.findAll(
                    repository.multiParameterSearch(
                            new FlattenedSecurityServerClientRepository.SearchParameters()
                                    .setClientType(XRoadObjectType.SERVER)
                    ));
            fail("bad client type should throw exception");
        } catch (Exception expected) { }

        // memberName
        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setMemberNameSearch("gov")
                                .setClientType(XRoadObjectType.MEMBER)
                ));
        assertEquals(0, clients.size());

        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setMemberNameSearch("2")
                ));
        // Member2, TEST2
        assertEquals(2, clients.size());

        // combo all parameters
        clients = repository.findAll(
                repository.multiParameterSearch(
                        new FlattenedSecurityServerClientRepository.SearchParameters()
                                .setClientType(XRoadObjectType.MEMBER)
                                .setMemberCodeSearch("m1")
                                .setSecurityServerId(1000001)
                                .setMultifieldSearch("ber1")
                                .setInstanceSearch("e")
                                .setMemberNameSearch("e")
                                .setMemberClassSearch("o")
                ));
        assertEquals(1, clients.size());
    }

    @Test
    public void caseInsensitiveSort() {
        Sort.Order order = Sort.Order.by("memberName").ignoreCase();
        List<FlattenedSecurityServerClientView> clients = repository.findAll(Sort.by(order));
        int index = 0;
        assertEquals("ADMORG", clients.get(index++).getMemberName());
        assertEquals("ADMORG", clients.get(index++).getMemberName()); // subsystem
        assertEquals("ADMORG", clients.get(index++).getMemberName()); // subsystem
        assertEquals("Member1", clients.get(index++).getMemberName());
        assertEquals("Member1", clients.get(index++).getMemberName()); // subsystem
        assertEquals("Member10", clients.get(index++).getMemberName());
        assertEquals("Member11", clients.get(index++).getMemberName());
        assertEquals("Member2", clients.get(index++).getMemberName());
        assertEquals("member3", clients.get(index++).getMemberName());
        assertEquals("mEmber4", clients.get(index++).getMemberName());
    }

    @Test
    public void findClientsByMemberName() {
        String memberName = "memBer1";
        List<FlattenedSecurityServerClientView> clients = repository.findAll(
                FlattenedSecurityServerClientRepository.memberName(memberName));
        // 3 members and one subsystem
        assertEquals(4, clients.size());
    }

    @Test
    public void findAll() {
        List<FlattenedSecurityServerClientView> clients = repository.findAll();
        assertEquals(CLIENTS_TOTAL_COUNT, clients.size());
    }

    @Test
    public void findByType() {
        List<FlattenedSecurityServerClientView> clients = repository.findAll(
                FlattenedSecurityServerClientRepository.member());
        assertEquals(MEMBERS_TOTAL_COUNT, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.subsystem());
        assertEquals(SUBSYSTEMS_TOTAL_COUNT, clients.size());
    }

}
