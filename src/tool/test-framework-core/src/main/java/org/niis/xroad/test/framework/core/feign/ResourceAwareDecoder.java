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
package org.niis.xroad.test.framework.core.feign;

import feign.FeignException;
import feign.Response;
import feign.codec.Decoder;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

/**
 * Custom decoder that handles both JSON responses and raw Resource responses.
 * Delegates to JacksonDecoder for JSON, but handles Resource types directly.
 */
@RequiredArgsConstructor
public class ResourceAwareDecoder implements Decoder {
    private final Decoder delegate;

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        // Check if the expected type is ResponseEntity<Resource>
        if (type instanceof ParameterizedType parameterizedType) {
            Type rawType = parameterizedType.getRawType();

            if (rawType == ResponseEntity.class) {
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments.length > 0 && actualTypeArguments[0] == Resource.class) {
                    // Handle ResponseEntity<Resource> - return raw response as Resource
                    return decodeResourceResponse(response);
                }
            }
        }

        // For all other types, delegate to the standard decoder (JacksonDecoder wrapped
        // in ResponseEntityDecoder)
        return delegate.decode(response, type);
    }

    private ResponseEntity<Resource> decodeResourceResponse(Response response) throws IOException {
        // Extract headers
        Map<String, Collection<String>> headers = response.headers();
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach((key, values) -> httpHeaders.addAll(key, values.stream().toList()));

        // Extract filename from Content-Disposition header
        String filename = httpHeaders.getContentDisposition().getFilename();

        // Read the entire response body into a byte array
        // This is necessary because InputStreamResource doesn't expose properties
        // properly
        byte[] bodyBytes = response.body().asInputStream().readAllBytes();

        // Create a Resource with accessible filename and inputStream properties
        Resource resource = new NamedByteArrayResource(bodyBytes, filename);

        // Get status
        HttpStatus status = HttpStatus.valueOf(response.status());

        return new ResponseEntity<>(resource, httpHeaders, status);
    }
}
