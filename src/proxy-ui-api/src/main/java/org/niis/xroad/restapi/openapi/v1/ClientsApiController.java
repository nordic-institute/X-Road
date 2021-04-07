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
package org.niis.xroad.restapi.openapi.v1;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.openapi.ApiUtil;
import org.niis.xroad.restapi.openapi.v1.model.Client;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.stream.Collectors;

/**
 * clients api
 */
@Controller(value = "clientsApiControllerV1")
@RequestMapping(ApiUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class ClientsApiController implements ClientsApi {

    private final org.niis.xroad.restapi.openapi.v2.ClientsApiController clientsApiControllerV2;

    /**
     * Finds clients matching search terms
     * @param name
     * @param instance
     * @param memberClass
     * @param memberCode
     * @param subsystemCode
     * @param showMembers include members (without susbsystemCode) in the results
     * @param internalSearch search only in the local clients
     * @return
     */
    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public ResponseEntity<List<Client>> findClients(String name, String instance, String memberClass,
            String memberCode, String subsystemCode, Boolean showMembers, Boolean internalSearch,
            Boolean localValidSignCert, Boolean excludeLocal) {
        log.info("findClients V1");
        Boolean hasFunctionalSignCert = null; // parameter only for V2 API
        Boolean newV2BooleanParam = null; // parameter only for V2 API
        ResponseEntity<List<org.niis.xroad.restapi.openapi.v2.model.Client>> clients =
                clientsApiControllerV2.findClientsMultiVersion(name, instance, memberClass,
                memberCode, subsystemCode, showMembers, internalSearch,
                localValidSignCert,
                hasFunctionalSignCert, newV2BooleanParam,
                excludeLocal);
        return convert(clients);
    }

    /**
     * Shallow copy only
     * @param clients
     * @return
     */
    private ResponseEntity<List<Client>> convert(
            ResponseEntity<List<org.niis.xroad.restapi.openapi.v2.model.Client>> clients) {
        List<Client> converted = clients.getBody().stream()
                .map(client -> convert(client))
                .collect(Collectors.toList());
        return new ResponseEntity<>(converted, clients.getStatusCode());
    }

    private Client convert(org.niis.xroad.restapi.openapi.v2.model.Client client) {
        Client c = new Client();
        BeanUtils.copyProperties(client, c);
        c.setMemberName(client.getMemberNameChangedV2());
        return c;
    }
}
