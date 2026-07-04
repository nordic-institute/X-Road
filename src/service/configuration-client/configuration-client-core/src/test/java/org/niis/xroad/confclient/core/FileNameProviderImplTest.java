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
package org.niis.xroad.confclient.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.core.exception.ErrorCode;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static ee.ria.xroad.common.TestExceptionUtils.codedException;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_IDENTIFIER;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_LOCATION;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_TRANSFER_ENCODING;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGORITHM_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileNameProviderImplTest {

    private static final String HASH_ALGORITHM_ID = "http://www.w3.org/2001/04/xmlenc#sha512";

    @TempDir
    Path globalConfDir;

    @Test
    void sharedParamsResolveUnderInstanceDirectory() {
        FileNameProviderImpl provider = new FileNameProviderImpl(globalConfDir.toString());

        Path result = provider.getFileName(sharedParamsPart("EE"));

        assertThat(result).isEqualTo(globalConfDir.resolve("EE").resolve("shared-params.xml").normalize());
    }

    @Test
    void instanceIdentifierTraversalIsRejected() {
        FileNameProviderImpl provider = new FileNameProviderImpl(globalConfDir.toString());

        assertThatThrownBy(() -> provider.getFileName(sharedParamsPart("..")))
                .is(codedException(ErrorCode.GLOBAL_CONF_PART_INVALID_INSTANCE_IDENTIFIER.code()));
    }

    @Test
    void configurationDirectoryTraversalIsRejected() {
        FileNameProviderImpl provider = new FileNameProviderImpl(globalConfDir.toString());

        assertThatThrownBy(() -> provider.getConfigurationDirectory(".."))
                .is(codedException(ErrorCode.GLOBAL_CONF_PART_INVALID_INSTANCE_IDENTIFIER.code()));
    }

    @Test
    void contentLocationWithoutFileNameIsRejected() {
        FileNameProviderImpl provider = new FileNameProviderImpl(globalConfDir.toString());

        assertThatThrownBy(() -> provider.getFileName(genericPart("/")))
                .is(codedException(ErrorCode.GLOBAL_CONF_HEADER_FIELD_WRONG_VALUE.code()));
    }

    @Test
    void traversalFileNameIsRejected() {
        FileNameProviderImpl provider = new FileNameProviderImpl(globalConfDir.toString());

        assertThatThrownBy(() -> provider.getFileName(genericPart("..")))
                .is(codedException(ErrorCode.GLOBAL_CONF_HEADER_FIELD_WRONG_VALUE.code()));
    }

    private static ConfigurationFile sharedParamsPart(String instanceIdentifier) {
        Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CONTENT_TYPE, "application/octet-stream");
        headers.put(HEADER_CONTENT_TRANSFER_ENCODING, "base64");
        headers.put(HEADER_CONTENT_LOCATION, "/%s/shared-params.xml".formatted(instanceIdentifier));
        headers.put(HEADER_HASH_ALGORITHM_ID, HASH_ALGORITHM_ID);
        headers.put(HEADER_CONTENT_IDENTIFIER, "SHARED-PARAMETERS; instance='%s'".formatted(instanceIdentifier));
        return ConfigurationFile.of(headers, OffsetDateTime.MAX, "2", "hash");
    }

    private static ConfigurationFile genericPart(String contentLocation) {
        Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CONTENT_TYPE, "application/octet-stream");
        headers.put(HEADER_CONTENT_TRANSFER_ENCODING, "base64");
        headers.put(HEADER_CONTENT_LOCATION, contentLocation);
        headers.put(HEADER_HASH_ALGORITHM_ID, HASH_ALGORITHM_ID);
        return ConfigurationFile.of(headers, OffsetDateTime.MAX, "2", "hash");
    }
}
