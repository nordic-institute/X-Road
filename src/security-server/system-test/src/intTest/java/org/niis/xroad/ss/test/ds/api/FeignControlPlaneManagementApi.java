/*
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

package org.niis.xroad.ss.test.ds.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@FeignClient(name = "controlPlaneManagementApi")
public interface FeignControlPlaneManagementApi {

    @PostMapping(value = "",
            produces = {"application/json"}, consumes = {"application/json"})
    ResponseEntity<Map<String, Object>> createParticipantContext(@RequestHeader(AUTHORIZATION) String authorization,
                                                                 @RequestBody String body);

    @GetMapping(value = "/{participantContextId}",
            produces = {"application/json"})
    ResponseEntity<Map<String, Object>> getParticipantContext(@RequestHeader(AUTHORIZATION) String authorization,
                                                              @PathVariable String participantContextId);

    @PutMapping(value = "/{participantContextId}/config",
            consumes = {"application/json"})
    ResponseEntity<Void> createParticipantContextConfig(@RequestHeader(AUTHORIZATION) String authorization,
                                                        @PathVariable String participantContextId,
                                                        @RequestBody String body);

    @PostMapping(value = "/{participantContextId}/assets",
            produces = {"application/json"}, consumes = {"application/json"})
    ResponseEntity<Map<String, Object>> createAsset(@RequestHeader(AUTHORIZATION) String authorization,
                                                    @PathVariable String participantContextId,
                                                    @RequestBody String body);

    @PostMapping(value = "/{participantContextId}/policydefinitions",
            produces = {"application/json"}, consumes = {"application/json"})
    ResponseEntity<Map<String, Object>> createPolicyDefinition(@RequestHeader(AUTHORIZATION) String authorization,
                                                               @PathVariable String participantContextId,
                                                               @RequestBody String body);

    @PostMapping(value = "/{participantContextId}/contractdefinitions",
            produces = {"application/json"}, consumes = {"application/json"})
    ResponseEntity<Map<String, Object>> createContractDefinition(@RequestHeader(AUTHORIZATION) String authorization,
                                                                 @PathVariable String participantContextId,
                                                                 @RequestBody String body);

    @PostMapping(value = "/{participantContextId}/catalog/request",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Map<String, Object>> requestCatalog(@RequestHeader(AUTHORIZATION) String authorization,
                                                       @PathVariable String participantContextId,
                                                       @RequestBody String body);
}
