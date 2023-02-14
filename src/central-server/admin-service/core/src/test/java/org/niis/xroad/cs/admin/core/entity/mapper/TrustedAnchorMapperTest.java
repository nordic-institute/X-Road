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
package org.niis.xroad.cs.admin.core.entity.mapper;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.core.entity.AnchorUrlCertEntity;
import org.niis.xroad.cs.admin.core.entity.AnchorUrlEntity;
import org.niis.xroad.cs.admin.core.entity.TrustedAnchorEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TrustedAnchorMapperImpl.class)
class TrustedAnchorMapperTest {
    @Autowired
    TrustedAnchorMapper mapper;

    @Test
    void shouldMapAllFields() {
        TrustedAnchorEntity source = createTrustedAnchorEntity();

        var target = mapper.toTarget(source);

        assertThat(target)
                .usingRecursiveComparison()
                .isEqualTo(source);

        assertThat(target).hasNoNullFieldsOrProperties()
                .usingRecursiveAssertion()
                .allFieldsSatisfy(Objects::nonNull);
    }

    private static TrustedAnchorEntity createTrustedAnchorEntity() {
        var source = new TrustedAnchorEntity();
        source.setAnchorUrls(Set.of(createAnchorUrlEntity()));
        source.setInstanceIdentifier("INSTANCE");
        source.setTrustedAnchorHash("trusted anchor hash");
        source.setTrustedAnchorFile("trusted anchor file".getBytes(UTF_8));
        source.setGeneratedAt(Instant.now());
        return source;
    }

    private static AnchorUrlEntity createAnchorUrlEntity() {
        var anchorUrl = new AnchorUrlEntity();
        anchorUrl.setUrl("http://url");
        anchorUrl.setAnchorUrlCerts(Set.of(createAnchorUrlCertEntity()));
        return anchorUrl;
    }

    private static AnchorUrlCertEntity createAnchorUrlCertEntity() {
        var anchorUrlCert = new AnchorUrlCertEntity();
        anchorUrlCert.setCert("anchor url cert".getBytes(UTF_8));
        return anchorUrlCert;
    }

}
