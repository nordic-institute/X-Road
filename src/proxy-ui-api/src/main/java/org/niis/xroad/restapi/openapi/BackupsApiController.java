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

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.NativeWebRequest;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import java.util.List;
import java.util.Optional;

/**
 * backups controller
 */
@Controller
@RequestMapping("/api")
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class BackupsApiController implements org.niis.xroad.restapi.openapi.BackupsApi {

    public static final int MAX_FIFTY_ITEMS = 50;

    private final NativeWebRequest request;

    @org.springframework.beans.factory.annotation.Autowired
    public BackupsApiController(NativeWebRequest request) {
        this.request = request;
    }

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.ofNullable(request);
    }

    @Override
    public ResponseEntity<List<org.niis.xroad.restapi.openapi.model.Backup>> getBackups(@Valid String term,
             @Min(0) @Valid Integer offset, @Min(0) @Max(MAX_FIFTY_ITEMS) @Valid Integer limit) {
        ApiUtil.setExampleResponse(request, "application/json", "{  \"date\" :"
                + " \"2000-01-23T04:56:07.000+00:00\",  \"name\" : \"ACTUAL IMPLEMENTATION\",  \"id\" :"
                + " \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",  \"status\" : \"completed\"}");
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
