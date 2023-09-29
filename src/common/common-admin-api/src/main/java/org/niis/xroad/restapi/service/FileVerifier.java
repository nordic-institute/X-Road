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

package org.niis.xroad.restapi.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.restapi.config.AllowedFilesConfig;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import static java.util.function.Predicate.not;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.DOUBLE_FILE_EXTENSION;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INVALID_FILE_CONTENT_TYPE;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INVALID_FILE_EXTENSION;

@RequiredArgsConstructor
public class FileVerifier {
    private static final Detector DETECTOR = new DefaultDetector();

    private final AllowedFilesConfig allowedFilesConfig;

    public void validateXml(final String filename,
                            final byte[] contents) {
        validate(filename, contents, allowedFilesConfig.getXmlAllowedExtensions(), allowedFilesConfig.getXmlAllowedContentTypes());
    }

    public void validateBackup(final String filename,
                               final byte[] contents) {
        validate(filename, contents, Set.of(), allowedFilesConfig.getBackupAllowedContentTypes());
    }

    public void validateCertificate(final String filename,
                                    final byte[] contents) {
        validate(filename,
                contents,
                allowedFilesConfig.getCertificateAllowedExtensions(),
                allowedFilesConfig.getCertificateAllowedContentTypes());
    }

    private void validate(final String filename,
                          final byte[] contents,
                          final Set<String> allowedExtensions,
                          final Set<String> allowedContentTypes) {
        try {
            Optional.ofNullable(allowedExtensions)
                    .filter(not(Set::isEmpty))
                    .ifPresent(extensions -> validateExtension(filename, extensions));

            if (!CollectionUtils.isEmpty(allowedContentTypes)) {
                validateContentType(filename, contents, allowedContentTypes);
            }
        } catch (IOException e) {
            throw new MultipartException("Failed to read multipart file", e);
        }
    }

    private static void validateContentType(final String filename,
                                            final byte[] contents,
                                            final Set<String> allowedContentTypes) throws IOException {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        try (var inputStream = new ByteArrayInputStream(contents)) {
            MediaType mediaType = DETECTOR.detect(inputStream, metadata);
            if (!allowedContentTypes.contains(mediaType.getBaseType().toString())) {
                throw new ValidationFailureException(INVALID_FILE_CONTENT_TYPE,
                        mediaType.getBaseType(),
                        String.join(", ", allowedContentTypes));
            }
        }
    }

    private static void validateExtension(final String filename, final Set<String> allowedExtensions) {
        validateDoubleExtension(filename);
        final var extension = FilenameUtils.getExtension(filename);
        if (!allowedExtensions.contains(extension)) {
            throw new ValidationFailureException(INVALID_FILE_EXTENSION,
                    extension,
                    String.join(", ", allowedExtensions));
        }
    }

    private static void validateDoubleExtension(final String filename) {
        var withoutExtension = FilenameUtils.removeExtension(filename);
        if (!withoutExtension.equals(FilenameUtils.removeExtension(withoutExtension))) {
            throw new ValidationFailureException(DOUBLE_FILE_EXTENSION);
        }
    }
}
