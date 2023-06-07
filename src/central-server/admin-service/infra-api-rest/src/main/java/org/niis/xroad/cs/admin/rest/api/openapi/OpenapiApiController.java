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

package org.niis.xroad.cs.admin.rest.api.openapi;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.cs.openapi.OpenapiApi;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.apache.commons.io.IOUtils.toByteArray;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.ERROR_READING_OPENAPI_FILE;
import static org.niis.xroad.restapi.openapi.ControllerUtil.createAttachmentResourceResponse;

@Controller
@RequiredArgsConstructor
@RequestMapping(ControllerUtil.API_V1_PREFIX)
public class OpenapiApiController implements OpenapiApi {

    private static final String OPENAPI_DEFINITION_PATH = "classpath:openapi-definition.yaml";
    private final ResourceLoader resourceLoader;

    @Override
    public ResponseEntity<Resource> downloadOpenApi() {
        try {
            byte[] bytes = toByteArray(resourceLoader.getResource(OPENAPI_DEFINITION_PATH).getInputStream());
            return createAttachmentResourceResponse(bytes, "openapi.yaml");
        } catch (Exception e) {
            throw new ServiceException(ERROR_READING_OPENAPI_FILE, e);
        }
    }

}
