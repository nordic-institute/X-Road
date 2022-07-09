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
package org.niis.xroad.centralserver.restapi.openapi;

import org.junit.function.ThrowingRunnable;
import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.openapi.model.PagedSecurityServersDto;
import org.niis.xroad.centralserver.openapi.model.PagingSortingParametersDto;
import org.niis.xroad.centralserver.openapi.model.SecurityServerDto;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import javax.validation.ConstraintViolationException;

import java.util.List;

import static java.util.Locale.ROOT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SecurityServerControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    private SecurityServersApiController securityServersApiController;

    @Test
    @WithMockUser(authorities = {"VIEW_SECURITY_SERVERS"})
    public void testOKSortParameterReturn200OK() {
        final PagingSortingParametersDto sortingParameters =
                new PagingSortingParametersDto().sort("xroad_id.server_code").desc(true);

        ResponseEntity<PagedSecurityServersDto> response =
                securityServersApiController.findSecurityServers(null, sortingParameters);

        assertNotNull(response);
        assertEquals(HttpStatus.OK.value(), response.getStatusCodeValue());
        assertNotNull(response.getBody());
        List<SecurityServerDto> securityServerList = response.getBody().getClients();
        assertTrue(
                "In descending sort for server code, the first item have be lexicographically"
                        + " prior server code compared to the second",
                0 < securityServerList.get(0).getXroadId().getServerCode().toUpperCase(ROOT)
                        .compareTo(securityServerList.get(1).getXroadId().getServerCode().toUpperCase(
                                ROOT)));
    }

    @Test
    @WithMockUser(authorities = {"VIEW_SECURITY_SERVERS"})
    public void givenAscendingByOwnerCodeGetRightSorted() {
        final PagingSortingParametersDto sortingByOwnerCode =
                new PagingSortingParametersDto().sort("xroad_id.member_code").desc(false);

        ResponseEntity<PagedSecurityServersDto> response2 =
                securityServersApiController.findSecurityServers(null, sortingByOwnerCode);

        assertNotNull(response2);
        assertEquals(HttpStatus.OK.value(), response2.getStatusCodeValue());
        assertNotNull(response2.getBody());
        List<SecurityServerDto> securityServerList2 = response2.getBody().getClients();
        assertTrue(
                "In descending sort for owner code, the first item have be lexicographically"
                        + " prior owner code compared to the last",
                0 > securityServerList2.get(0).getXroadId().getServerCode().toUpperCase(ROOT)
                        .compareTo(securityServerList2.get(securityServerList2.size() - 1).getXroadId().getServerCode()
                                .toUpperCase(
                                        ROOT)));
    }

    @Test
    @WithMockUser(authorities = {"VIEW_SECURITY_SERVERS"})
    public void givenDescendingByOwnerNameGetRightSorted() {
        final PagingSortingParametersDto sortingByOwnerName =
                new PagingSortingParametersDto().sort("owner_name").desc(true);

        ResponseEntity<PagedSecurityServersDto> response2 =
                securityServersApiController.findSecurityServers(null, sortingByOwnerName);

        assertNotNull(response2);
        assertEquals(HttpStatus.OK.value(), response2.getStatusCodeValue());
        assertNotNull(response2.getBody());
        List<SecurityServerDto> securityServerList2 = response2.getBody().getClients();
        assertTrue(
                "In descending sort for owner name, the first item have be lexicographically"
                        + " after owner name compared to the last",
                0 < securityServerList2.get(0).getOwnerName().toUpperCase(ROOT).compareTo(
                        securityServerList2.get(securityServerList2.size() - 1).getOwnerName().toUpperCase(ROOT)));
    }

    @Test
    @WithMockUser(authorities = {"VIEW_SECURITY_SERVERS"})
    public void givenDescendingByOwnerClassGetRightSorted() {
        final PagingSortingParametersDto sortingByOwnerClass =
                new PagingSortingParametersDto().sort("xroad_id.member_class").desc(true);

        ResponseEntity<PagedSecurityServersDto> response2 =
                securityServersApiController.findSecurityServers(null, sortingByOwnerClass);

        assertNotNull(response2);
        assertEquals(HttpStatus.OK.value(), response2.getStatusCodeValue());
        assertNotNull(response2.getBody());
        List<SecurityServerDto> securityServerList2 = response2.getBody().getClients();
        assertTrue(
                "In descending sort for owner class, the first item have be lexicographically"
                        + " after owner class compared to the last",
                0 < securityServerList2.get(0).getXroadId().getMemberClass().toUpperCase(ROOT)
                        .compareTo(securityServerList2.get(securityServerList2.size() - 1).getXroadId().getMemberClass()
                                .toUpperCase(
                                        ROOT)));
    }

    @Test
    @WithMockUser(authorities = {"VIEW_SECURITY_SERVERS"})
    public void testInvalidSortParameterThrowsConstraintViolation() throws ConstraintViolationException {
        PagingSortingParametersDto sortingParameters = new PagingSortingParametersDto().sort("Really invalid&&%&¤&#&&");

        ThrowingRunnable testable = () ->
                securityServersApiController.findSecurityServers("not_relevant", sortingParameters);

        assertThrows(ConstraintViolationException.class, testable);
    }

    @Test
    @WithMockUser(authorities = {"VIEW_SECURITY_SERVERS"})
    public void testUnknownSortingFieldParameterThrowsConstraintViolation() throws BadRequestException {
        PagingSortingParametersDto sortingParameters = new PagingSortingParametersDto().sort("unknown_field");

        ThrowingRunnable testable = () ->
                securityServersApiController.findSecurityServers("not_relevant", sortingParameters);

        assertThrows(BadRequestException.class, testable);
    }

    @Test
    @WithMockUser(authorities = {"UNKNOWN_AUTHORITY"})
    public void testUnknownAuthorityThrowsAccessDeniedExceptio() throws AccessDeniedException {
        PagingSortingParametersDto sortingParameters = new PagingSortingParametersDto();

        ThrowingRunnable testable = () ->
                securityServersApiController.findSecurityServers("not_relevant", sortingParameters);

        assertThrows(AccessDeniedException.class, testable);
    }

}
