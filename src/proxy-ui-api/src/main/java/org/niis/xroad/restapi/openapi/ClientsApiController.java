/**
 * The MIT License
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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.converter.ClientConverter;
import org.niis.xroad.restapi.openapi.model.Client;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * clients api
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
@Transactional
public class ClientsApiController implements org.niis.xroad.restapi.openapi.ClientsApi {

    private final NativeWebRequest request;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientConverter clientConverter;

    @Autowired
    public ClientsApiController(NativeWebRequest request) {
        this.request = request;
    }

    /**
     * Example exception
     */
    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such Thing there")
    public static class RestNotFoundException extends RuntimeException {
        public RestNotFoundException(String s) {
            super(s);
        }
    }

    @Override
    public ResponseEntity<List<org.niis.xroad.restapi.openapi.model.Group>> getClientGroups(String id) {
        if (true) throw new RestNotFoundException("RestNotFoundException");
        return null;
    }

    @Override
    public ResponseEntity<List<org.niis.xroad.restapi.openapi.model.Service>> getClientServices(String id) {
        if (true) throw new NullPointerException("NullPointerException");
        return null;
    }

    /**
     * test transactions
     * @return
     */
    @PreAuthorize("permitAll()")
    @RequestMapping(value = "/update")
    public String getAndUpdateServerCode() {
        return clientRepository.getAndUpdateServerCode();
    }

    /**
     * test transactions
     * @return
     */
    @PreAuthorize("permitAll()")
    @RequestMapping(value = "/rollback")
    public String getAndUpdateServerCodeRollback() {
        String code = clientRepository.getAndUpdateServerCode();
        if (true) throw new NullPointerException("code broke, transaction should rollback");
        return code;
    }

    /**
     * get roles
     * @param authentication
     * @return
     */
    @PreAuthorize("permitAll()")
    @RequestMapping(value = "/roles")
    public ResponseEntity<Set<String>> getRoles(Authentication authentication) {
        return new ResponseEntity<>(
                getAuthorities(authentication, name -> name.startsWith("ROLE_")),
                HttpStatus.OK);
    }

    /**
     * get permissions
     * @param authentication
     * @return
     */
    @PreAuthorize("permitAll()")
    @RequestMapping(value = "/permissions")
    public ResponseEntity<Set<String>> getPermissions(Authentication authentication) {
        return new ResponseEntity<>(
                getAuthorities(authentication, name -> !name.startsWith("ROLE_")),
                HttpStatus.OK);
    }

    private Set<String> getAuthorities(Authentication authentication,
                                       Predicate<String> authorityNamePredicate) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(authority -> ((GrantedAuthority) authority).getAuthority())
                .filter(authorityNamePredicate)
                .collect(Collectors.toSet());
        return roles;
    }


    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public ResponseEntity<List<Client>> getClients() {
        List<ClientType> clientTypes = clientRepository.getAllClients();
        List<Client> clients = new ArrayList<>();
        for (ClientType clientType : clientTypes) {
            clients.add(clientConverter.convert(clientType));
        }
        return new ResponseEntity<>(clients, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('NO_ONE_HAS_THIS')")
    public ResponseEntity<Client> getClient(String id) {
//CHECKSTYLE.OFF: TodoComment - need this todo and still want builds to succeed
        ClientId clientId = clientConverter.convertId(id);
        ClientType clientType = clientRepository.getClient(clientId);
        Client client = clientConverter.convert(clientType);
        // TODO: 404 not working
        return new ResponseEntity<>(client, HttpStatus.OK);
//CHECKSTYLE.ON: TodoComment

    }

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.ofNullable(request);
    }

}
