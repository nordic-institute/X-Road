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
package org.niis.xroad.centralserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.junit.helper.WithInOrder;

import io.vavr.control.Option;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.restapi.entity.FlattenedSecurityServerClientView;
import org.niis.xroad.centralserver.restapi.entity.MemberId;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.niis.xroad.centralserver.restapi.repository.FlattenedSecurityServerClientRepository;
import org.niis.xroad.centralserver.restapi.repository.XRoadMemberRepository;
import org.niis.xroad.centralserver.restapi.service.exception.EntityExistsException;
import org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_EXISTS;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest implements WithInOrder {

    @Mock
    private StableSortHelper stableSortHelper;
    @Mock
    private XRoadMemberRepository xRoadMemberRepository;
    @Mock
    private FlattenedSecurityServerClientRepository flattenedSecurityServerClientRepository;

    @InjectMocks
    private ClientService clientService;

    @Nested
    @DisplayName("find(FlattenedSecurityServerClientRepository.SearchParameters params, Pageable pageable)")
    class FindMethod {

        @Mock
        private Pageable pageable;
        @Mock
        private Pageable modifiedPageable;
        @Mock
        private Specification<FlattenedSecurityServerClientView> spec;
        @Mock
        private FlattenedSecurityServerClientRepository.SearchParameters params;
        @Mock
        private Page<FlattenedSecurityServerClientView> result;

        @Test
        @DisplayName("should just verify sanity")
        void shouldJustVerifySanity() {
            doReturn(spec).when(flattenedSecurityServerClientRepository).multiParameterSearch(params);
            doReturn(modifiedPageable).when(stableSortHelper).addSecondaryIdSort(pageable);
            doReturn(result).when(flattenedSecurityServerClientRepository).findAll(spec, modifiedPageable);

            clientService.find(params, pageable);

            inOrder().verify(inOrder -> {
                inOrder.verify(flattenedSecurityServerClientRepository).multiParameterSearch(params);
                inOrder.verify(stableSortHelper).addSecondaryIdSort(pageable);
                inOrder.verify(flattenedSecurityServerClientRepository).findAll(spec, modifiedPageable);
            });
        }
    }

    @Nested
    @DisplayName("add(Client clientDto)")
    class AddMethod implements WithInOrder {

        @Mock
        private XRoadMember xRoadMember;
        private MemberId memberId = MemberId.create("TEST", "CLASS", "MEMBER");

        @Test
        @DisplayName("should create client when not already present")
        void shouldCreateClientWhenNotAlreadyPresent() {
            XRoadMember persistedXRoadMember = mock(XRoadMember.class);
            doReturn(memberId).when(xRoadMember).getIdentifier();
            doReturn(Option.none()).when(xRoadMemberRepository).findOneBy(memberId);
            doReturn(persistedXRoadMember).when(xRoadMemberRepository).save(xRoadMember);

            SecurityServerClient result = clientService.add(xRoadMember);

            assertEquals(persistedXRoadMember, result);
            inOrder(persistedXRoadMember).verify(inOrder -> {
                inOrder.verify(xRoadMemberRepository).findOneBy(memberId);
                inOrder.verify(xRoadMemberRepository).save(xRoadMember);
            });
        }

        @Test
        @DisplayName("should not create client when already present")
        void shouldNotCreateClientWhenAlreadyPresent() {
            SecurityServerClient presentSecurityServerClient = mock(SecurityServerClient.class);
            doReturn(memberId).when(xRoadMember).getIdentifier();
            doReturn(Option.of(presentSecurityServerClient)).when(xRoadMemberRepository).findOneBy(memberId);
            String clientIdentifier = memberId.toShortString();

            Executable testable = () -> clientService.add(xRoadMember);

            EntityExistsException exception = assertThrows(EntityExistsException.class, testable);
            assertEquals(MEMBER_EXISTS.getDescription(), exception.getMessage());
            assertThat(exception.getErrorDeviation().getMetadata())
                    .hasSize(1)
                    .containsExactly(clientIdentifier);
            inOrder(presentSecurityServerClient).verify(inOrder -> {
                inOrder.verify(xRoadMember).getIdentifier();
                inOrder.verify(xRoadMemberRepository).findOneBy(memberId);
                inOrder.verify(xRoadMember).getIdentifier();
            });
        }
    }

    @Nested
    @DisplayName("findMember(ClientId clientId)")
    class FindMember implements WithInOrder {

        private ClientId clientId = ClientId.Conf.create("TEST", "CLASS", "MEMBER");

        @Mock
        private XRoadMember xRoadMember;

        @Test
        @DisplayName("Should find client from xRoadMemberRepository")
        void shouldFindClient() {
            doReturn(Option.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);

            var result = clientService.findMember(clientId);

            assertTrue(result.isDefined());
        }
    }

    @Nested
    @DisplayName("deleteMember(ClientId clientId)")
    class DeleteMember implements WithInOrder {

        private ClientId clientId = ClientId.Conf.create("TEST", "CLASS", "MEMBER");

        @Mock
        private XRoadMember xRoadMember;

        @Test
        @DisplayName("Should delete client from xRoadMemberRepository")
        void shouldDeleteClient() {
            doReturn(Option.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);

            clientService.delete(clientId);

            inOrder().verify(inOrder -> {
                inOrder.verify(xRoadMemberRepository).findMember(clientId);
                inOrder.verify(xRoadMemberRepository).delete(xRoadMember);
            });
        }

        @Test
        @DisplayName("Should not delete client when it's non-existent")
        void shouldThrowExceptionWhenClientNotFound() {
            doReturn(Option.none()).when(xRoadMemberRepository).findMember(clientId);

            Executable testable = () -> clientService.delete(clientId);

            NotFoundException actualThrown = assertThrows(NotFoundException.class, testable);
            assertEquals(ErrorMessage.MEMBER_NOT_FOUND.getDescription(), actualThrown.getMessage());
            inOrder().verify(inOrder -> {
                inOrder.verify(xRoadMemberRepository).findMember(clientId);
            });
        }
    }
}
