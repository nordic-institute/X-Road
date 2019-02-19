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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.NativeWebRequest;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import java.util.List;
import java.util.Optional;

/**
 * clients api
 */
@Controller
@RequestMapping("/api")
@Slf4j
//CHECKSTYLE.OFF: TodoComment - need this todo and still want builds to succeed
//@PreAuthorize("hasAuthority('ROLE_XROAD-SERVICE-ADMINISTRATOR')") // TODO: proper auth
//CHECKSTYLE.ON: TodoComment
public class ClientsApiController implements org.niis.xroad.restapi.openapi.ClientsApi {

    public static final int MAX_FIFTY_RESULTS = 50;
    private final NativeWebRequest request;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    public ClientsApiController(NativeWebRequest request) {
        this.request = request;
    }

    /**
     * Example exception
     */
    @PreAuthorize("hasAuthority('ROLE_XROAD-SERVICE-ADMINISTRATOR')")
    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such Thing there")
    public static class RestNotFoundException extends RuntimeException {
        public RestNotFoundException(String s) {
            super(s);
        }
    }

    @Override
    public ResponseEntity<List<org.niis.xroad.restapi.openapi.model.Client>> getClients(@Valid String sort,
             @Valid String term, @Min(0) @Valid Integer offset, @Min(0) @Max(MAX_FIFTY_RESULTS) @Valid Integer limit) {
        List<org.niis.xroad.restapi.openapi.model.Client> clients = clientRepository.getAllClients();
        return new ResponseEntity<>(clients, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<org.niis.xroad.restapi.openapi.model.Client> getClient(String id) {
//CHECKSTYLE.OFF: TodoComment - need this todo and still want builds to succeed
        org.niis.xroad.restapi.openapi.model.Client client = clientRepository.getClient(id);
        // TODO: 404 not working
        return new ResponseEntity<>(client, HttpStatus.OK);
//CHECKSTYLE.ON: TodoComment

    }

    @Override
    public ResponseEntity<List<org.niis.xroad.restapi.openapi.model.Certificate>> getClientCertificates(String id) {
        clientRepository.throwSpringException("spring exception");
        return null;
    }

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.ofNullable(request);
    }

}
