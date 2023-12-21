/*
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
package org.niis.xroad.restapi.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.List;

/**
 * Configuration which knows the endpoints that are used for file uploads.
 * These endpoints will have a larger request size limit than others.
 */
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "file-upload-endpoints")
public class FileUploadEndpointsConfiguration {

    private List<EndpointDefinition> endpointDefinitions;

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class EndpointDefinition {
        private static final PathMatcher PATH_MATCHER = new AntPathMatcher();
        private HttpMethod httpMethod;
        private String pathPattern;

        /**
         * Does request match this endpoint definition?
         *
         * @param request
         * @return
         */
        public boolean matches(HttpServletRequest request) {
            if (request != null) {
                String uri = request.getRequestURI();
                return httpMethod.matches(request.getMethod())
                        && PATH_MATCHER.match(pathPattern, uri);
            }
            return false;
        }
    }

}
