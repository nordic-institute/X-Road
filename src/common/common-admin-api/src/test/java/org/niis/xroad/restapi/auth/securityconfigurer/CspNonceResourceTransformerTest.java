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
package org.niis.xroad.restapi.auth.securityconfigurer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.ResourceTransformerChain;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CspNonceResourceTransformer}.
 */
@ExtendWith(MockitoExtension.class)
class CspNonceResourceTransformerTest {

    @Mock
    private ResourceTransformerChain chain;

    private final CspNonceResourceTransformer transformer = new CspNonceResourceTransformer();

    private static Resource testResource(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public long lastModified() {
                return 0L;
            }
        };
    }

    @Test
    void replacesPlaceholderWithNonce() throws Exception {
        var request = new MockHttpServletRequest();
        request.setAttribute(CspNonceFilter.NONCE_ATTR, "test-nonce-123");

        var html = "<html><script nonce=\"__CSP_NONCE__\">alert(1)</script></html>";
        var resource = testResource(html);
        when(chain.transform(any(), any())).thenReturn(resource);

        var result = transformer.transform(request, resource, chain);

        var resultBytes = new String(result.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(resultBytes).contains("test-nonce-123");
        assertThat(resultBytes).doesNotContain(CspNonceFilter.CSP_NONCE_PLACEHOLDER);
    }

    @Test
    void skipsSubstitutionWhenNoNonceAttribute() throws Exception {
        var request = new MockHttpServletRequest();

        var resource = testResource("<html>__CSP_NONCE__</html>");
        when(chain.transform(any(), any())).thenReturn(resource);

        var result = transformer.transform(request, resource, chain);

        assertThat(result).isSameAs(resource);
    }

    @Test
    void chainsBeforeSubstituting() throws Exception {
        var request = new MockHttpServletRequest();
        request.setAttribute(CspNonceFilter.NONCE_ATTR, "abc");

        var inputResource = testResource("input");
        var chainedResource = testResource("<html>__CSP_NONCE__</html>");
        when(chain.transform(any(), any())).thenReturn(chainedResource);

        var result = transformer.transform(request, inputResource, chain);

        var resultBytes = new String(result.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(resultBytes).contains("abc");
        assertThat(resultBytes).doesNotContain("input");
    }

    @Test
    void handlesMultiplePlaceholderOccurrences() throws Exception {
        var request = new MockHttpServletRequest();
        request.setAttribute(CspNonceFilter.NONCE_ATTR, "multi-nonce");

        var html = "<script nonce=\"__CSP_NONCE__\"></script>"
                + "<style nonce=\"__CSP_NONCE__\"></style>"
                + "<meta content=\"__CSP_NONCE__\">";
        var resource = testResource(html);
        when(chain.transform(any(), any())).thenReturn(resource);

        var result = transformer.transform(request, resource, chain);

        var resultBytes = new String(result.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(resultBytes).doesNotContain(CspNonceFilter.CSP_NONCE_PLACEHOLDER);
        assertThat(resultBytes.split("multi-nonce", -1)).hasSize(4); // 3 occurrences = 4 parts
    }
}
