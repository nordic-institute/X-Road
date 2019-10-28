/**
 * The MIT License
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
package org.niis.xroad.restapi.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import javax.servlet.DispatcherType;

import java.util.EnumSet;

/**
 * Limit request sizes to correct values
 */
@Component
@Slf4j
public class LimitRequestSizesConfiguration {

    @Autowired
    FileUploadEndpointsConfiguration fileUploadEndpointsConfiguration;

    // size limits for file upload requests, and all other requests
    private static final long REGULAR_REQUEST_SIZE_BYTE_LIMIT = 50 * 1024;
    private static final long FILE_UPLOAD_REQUEST_SIZE_BYTE_LIMIT = 10 * 1024 * 1024;

    @Bean
    public FilterRegistrationBean<LimitRequestSizesFilter> basicRequestFilter() {
        FilterRegistrationBean<LimitRequestSizesFilter> bean = new FilterRegistrationBean<>();
        bean.setName("RegularLimitSizeFilter");
        bean.setFilter(new LimitRequestSizesFilter(REGULAR_REQUEST_SIZE_BYTE_LIMIT,
                fileUploadEndpointsConfiguration.getEndpointDefinitions(),
                LimitRequestSizesFilter.Mode.SKIP_ENDPOINTS));
        bean.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<LimitRequestSizesFilter> fileUploadFilter() {
        FilterRegistrationBean<LimitRequestSizesFilter> bean = new FilterRegistrationBean<>();
        bean.setName("BinaryUploadLimitSizeFilter");
        bean.setFilter(new LimitRequestSizesFilter(FILE_UPLOAD_REQUEST_SIZE_BYTE_LIMIT,
                fileUploadEndpointsConfiguration.getEndpointDefinitions(),
                LimitRequestSizesFilter.Mode.LIMIT_ENDPOINTS));
        bean.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

}
