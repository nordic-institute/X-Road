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
package org.niis.xroad.authproto.openapi;

import org.niis.xroad.authproto.openapi.model.Certificate;
import org.niis.xroad.authproto.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.NativeWebRequest;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * clients api
 */
@Controller
@RequestMapping("/test-api")
public class ClientsApiController implements org.niis.xroad.authproto.openapi.ClientsApi {

    static Logger logger = LoggerFactory.getLogger(ClientsApiController.class);

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
    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such Thing there")
    public static class RestNotFoundException extends RuntimeException {
        public RestNotFoundException(String s) {
            super(s);
        }
    }

    @Override
    public ResponseEntity<List<org.niis.xroad.authproto.openapi.model.Client>> getClients(@Valid String term,
            @Min(0) @Valid Integer offset, @Min(0) @Max(MAX_FIFTY_RESULTS) @Valid Integer limit) {
        List<org.niis.xroad.authproto.openapi.model.Client> clients = clientRepository.getAllClients();
        return new ResponseEntity<>(clients, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<org.niis.xroad.authproto.openapi.model.Client>> getClient(String id) {
        List<org.niis.xroad.authproto.openapi.model.Client> clients = clientRepository.getAllClients();
        String idWithSlashes = id.replaceAll(":", "/");
        logger.info("looking for id {}", idWithSlashes);
        logger.debug("looking for id {}", idWithSlashes);
        for (org.niis.xroad.authproto.openapi.model.Client client: clients) {
            if (idWithSlashes.equals(client.getName())) {
                return new ResponseEntity<List<org.niis.xroad.authproto.openapi.model.Client>>(
                        Collections.singletonList(client), HttpStatus.OK);
            }
        }
        throw new RestNotFoundException("client not found");
    }

    @Override
    public ResponseEntity<List<Certificate>> getClientCertificate(String id, String cid) {
        if (true) throw new RestNotFoundException("hiio hoi");
//        clientRepository.throwSpringException("spring exception");
        return null;
    }

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.ofNullable(request);
    }

}
