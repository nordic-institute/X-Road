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
import org.niis.xroad.restapi.converter.ServiceDescriptionConverter;
import org.niis.xroad.restapi.openapi.model.InlineObject9;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * clients api
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class ServiceDescriptionsApiController implements ServiceDescriptionsApi {

    private final NativeWebRequest request;
    private final ServiceDescriptionConverter serviceDescriptionConverter;

    /**
     * ServiceDescriptionsApiController constructor
     * @param serviceDescriptionConverter
     */

    @Autowired
    public ServiceDescriptionsApiController(NativeWebRequest request,
                                            ServiceDescriptionConverter serviceDescriptionConverter) {
        this.request = request;
        this.serviceDescriptionConverter = serviceDescriptionConverter;
    }

    @Override
    @PreAuthorize("hasAuthority('ENABLE_DISABLE_WSDL')")
    public ResponseEntity<Void> disableServiceDescription(String id, InlineObject9 inlineObject9) {
        log.info("id: {}", id);
        log.info("inlineobject: {}", inlineObject9);
        if (inlineObject9 != null) {
            log.info("disabled notice: {}", inlineObject9.getDisabledNotice());
        }
        return new ResponseEntity<Void>(HttpStatus.OK);
    }

}
