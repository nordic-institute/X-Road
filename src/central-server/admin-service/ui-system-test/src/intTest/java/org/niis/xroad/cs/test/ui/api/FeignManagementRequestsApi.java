/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.cs.test.ui.api;

import feign.QueryMapEncoder;
import feign.querymap.FieldQueryMapEncoder;
import org.niis.xroad.cs.openapi.ManagementRequestsApi;
import org.niis.xroad.cs.openapi.model.ManagementRequestsFilterDto;
import org.niis.xroad.cs.openapi.model.PagedManagementRequestsDto;
import org.niis.xroad.cs.openapi.model.PagingSortingParametersDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(name = "managementRequestsApi", path = "/api/v1", url = "https://localhost:4000")
public interface FeignManagementRequestsApi extends ManagementRequestsApi {

    /**
     * An overridden method to handle parameters.
     */
    @Override
    @GetMapping(
            value = "/management-requests",
            produces = {"application/json"}
    )
    default ResponseEntity<PagedManagementRequestsDto> findManagementRequests(
            ManagementRequestsFilterDto filter,
            PagingSortingParametersDto pagingSorting) {
        final QueryMapEncoder encoder = new FieldQueryMapEncoder();
        final Map<String, Object> params = encoder.encode(filter);
        params.putAll(encoder.encode(pagingSorting));

        return this.findManagementRequestsInternal(params);
    }

    /**
     * Workaround method to handle params, as Feign does not support several @SpringQueryMap.
     */
    @GetMapping(
            value = "/management-requests",
            produces = {"application/json"}
    )
    ResponseEntity<PagedManagementRequestsDto> findManagementRequestsInternal(
            @SpringQueryMap Map<String, Object> params);

}
