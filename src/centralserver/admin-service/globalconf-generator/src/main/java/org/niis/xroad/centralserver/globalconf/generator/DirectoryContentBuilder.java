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
package org.niis.xroad.centralserver.globalconf.generator;

import ee.ria.xroad.common.util.HashCalculator;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.niis.xroad.centralserver.globalconf.generator.MultipartMessage.header;
import static org.niis.xroad.centralserver.globalconf.generator.MultipartMessage.partBuilder;

@RequiredArgsConstructor
public class DirectoryContentBuilder {
    public static final DateTimeFormatter EXPIRE_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ssX")
            .withZone(ZoneOffset.UTC);

    private final HashCalculator hashCalculator;
    private final Instant expireDate;
    private final String pathPrefix;
    private final String instanceIdentifier;

    private List<ConfigurationPart> configurationParts = new ArrayList<>();

    DirectoryContentBuilder contentPart(ConfigurationPart configurationPart) {
        configurationParts.add(configurationPart);
        return this;
    }


    String build() {
        var builder = MultipartMessage.builder();
        builder.part(partBuilder()
                .header(header("Expire-date", EXPIRE_DATE_FORMATTER.format(expireDate)))
                .header(header("Version", "2"))
                .build());
        configurationParts.forEach(confPart -> {
            builder.part(buildPart(confPart));
        });
        return builder.build().toString();

    }

    private MultipartMessage.Part buildPart(ConfigurationPart confPart) {
        return partBuilder()
                .content(calculateHash(confPart.getData()))
                .header(header("Content-type", "application/octet-stream"))
                .header(header("Content-transfer-encoding", "base64"))
                .header(header("Content-identifier",
                        String.format("%s; instance='%s'", confPart.getContentIdentifier(), instanceIdentifier)))
                .header(header("Content-location", String.format("%s/%s", pathPrefix, confPart.getFilename())))
                .header(header("Hash-algorithm-id", hashCalculator.getAlgoURI()))
                .build();
    }

    @SneakyThrows
    private String calculateHash(@NonNull byte[] data) {
        return hashCalculator.calculateFromBytes(data);
    }

}
