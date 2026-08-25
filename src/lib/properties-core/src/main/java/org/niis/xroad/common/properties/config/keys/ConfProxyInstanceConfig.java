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

package org.niis.xroad.common.properties.config.keys;

import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Optional;

/**
 * Per-instance configuration carried inside the {@code xroad.configuration-proxy.instances} JSON map.
 * The JSON object uses camelCase field names, e.g.
 * {@code {"EE":{"tokenId":"tok1","sourceAnchorFileUri":"http://cs/anchor.xml"}}}.
 */
@SuppressWarnings("checkstyle:MagicNumber")
@NoArgsConstructor
@Setter
public class ConfProxyInstanceConfig {

    private String tokenId;
    private String signingKeyId;
    private String keyAlgorithm = "RSA";
    private String sourceAnchorFileUri;
    private int validityInterval = 600;

    public Optional<String> tokenId() {
        return Optional.ofNullable(tokenId);
    }

    public Optional<String> signingKeyId() {
        return Optional.ofNullable(signingKeyId);
    }

    public String keyAlgorithm() {
        return keyAlgorithm;
    }

    public String sourceAnchorFileUri() {
        return sourceAnchorFileUri;
    }

    public int validityInterval() {
        return validityInterval;
    }
}
