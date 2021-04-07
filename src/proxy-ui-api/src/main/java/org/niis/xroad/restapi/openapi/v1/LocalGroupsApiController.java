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
import org.niis.xroad.restapi.openapi.v1.model.LocalGroup;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * groups api
 */
@Controller(value = "localGroupsApiControllerV1")
@RequestMapping(ApiUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class LocalGroupsApiController implements LocalGroupsApi {

    private final org.niis.xroad.restapi.openapi.v2.LocalGroupsApiController localGroupsApiControllerV2;

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_LOCAL_GROUPS')")
    public ResponseEntity<LocalGroup> getLocalGroup(String groupIdString) {
        log.info("getLocalGroup V1");
        ResponseEntity<org.niis.xroad.restapi.openapi.v2.model.LocalGroup> responseFromV2 =
                localGroupsApiControllerV2.getLocalGroup(groupIdString);
        return convert(responseFromV2);
    }

    /**
     * Only a shallow copy
     * @param response
     * @return
     */
    private ResponseEntity<LocalGroup> convert(
            ResponseEntity<org.niis.xroad.restapi.openapi.v2.model.LocalGroup> response) {
        LocalGroup l = new LocalGroup();
        BeanUtils.copyProperties(response.getBody(), l);
        return new ResponseEntity<>(l, response.getStatusCode());
    }

}
