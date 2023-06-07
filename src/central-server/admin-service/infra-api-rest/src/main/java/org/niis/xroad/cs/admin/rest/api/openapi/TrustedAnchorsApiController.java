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

package org.niis.xroad.cs.admin.rest.api.openapi;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType;
import org.niis.xroad.cs.admin.api.service.TrustedAnchorService;
import org.niis.xroad.cs.admin.rest.api.converter.TrustedAnchorConverter;
import org.niis.xroad.cs.openapi.TrustedAnchorsApi;
import org.niis.xroad.cs.openapi.model.TrustedAnchorDto;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.service.FileVerifier;
import org.niis.xroad.restapi.util.MultipartFileUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_TRUSTED_ANCHOR;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_TRUSTED_ANCHOR;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class TrustedAnchorsApiController implements TrustedAnchorsApi {
    private static final String FILENAME_FORMAT = "configuration_anchor_%s_%s_%s.xml";
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd_HH_mm_ss";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)
            .withZone(ZoneId.systemDefault());

    private final TrustedAnchorService trustedAnchorService;
    private final TrustedAnchorConverter trustedAnchorConverter;
    private final FileVerifier fileVerifier;

    @Override
    @AuditEventMethod(event = DELETE_TRUSTED_ANCHOR)
    @PreAuthorize("hasAuthority('DELETE_TRUSTED_ANCHOR')")
    public ResponseEntity<Void> deleteTrustedAnchor(String hash) {
        trustedAnchorService.delete(hash);
        return noContent().build();
    }

    @Override
    @PreAuthorize("hasAuthority('DOWNLOAD_TRUSTED_ANCHOR')")
    public ResponseEntity<Resource> downloadTrustedAnchor(final String hash) {
        final var trustedAnchor = trustedAnchorService.findByHash(hash);
        final var filename = getAnchorFilename(trustedAnchor.getInstanceIdentifier(), trustedAnchor.getGeneratedAt());
        return ControllerUtil.createAttachmentResourceResponse(trustedAnchor.getTrustedAnchorFile(), filename);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_TRUSTED_ANCHORS')")
    public ResponseEntity<List<TrustedAnchorDto>> getTrustedAnchors() {
        return ok(trustedAnchorService.findAll().stream()
                .map(trustedAnchorConverter::toTarget)
                .collect(Collectors.toList()));
    }

    @Override
    @PreAuthorize("hasAuthority('UPLOAD_TRUSTED_ANCHOR')")
    public ResponseEntity<TrustedAnchorDto> previewTrustedAnchor(MultipartFile anchor) {
        byte[] fileBytes = MultipartFileUtils.readBytes(anchor);
        fileVerifier.validateXml(anchor.getOriginalFilename(), fileBytes);
        return ok(trustedAnchorConverter.toTarget(
                trustedAnchorService.preview(fileBytes))
        );
    }

    @Override
    @AuditEventMethod(event = ADD_TRUSTED_ANCHOR)
    @PreAuthorize("hasAuthority('UPLOAD_TRUSTED_ANCHOR')")
    public ResponseEntity<TrustedAnchorDto> uploadTrustedAnchor(MultipartFile anchor) {
        byte[] fileBytes = MultipartFileUtils.readBytes(anchor);
        fileVerifier.validateXml(anchor.getOriginalFilename(), fileBytes);
        return status(CREATED).body(
                trustedAnchorConverter.toTarget(
                        trustedAnchorService.upload(fileBytes))
        );
    }


    private static String getAnchorFilename(final String instanceIdentifier,
                                            final Instant generatedAt) {
        final var formattedDateTime = generatedAt == null ? "" : DATE_TIME_FORMATTER.format(generatedAt);

        return String.format(FILENAME_FORMAT, instanceIdentifier, ConfigurationSourceType.EXTERNAL, formattedDateTime);
    }
}
