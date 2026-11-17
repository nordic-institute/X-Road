/*
 * The MIT License
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
import org.niis.xroad.cs.admin.api.dto.TrustedIssuer;
import org.niis.xroad.cs.admin.api.dto.TrustedIssuerRemoval;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerDidService;
import org.niis.xroad.cs.openapi.TrustedIssuersApi;
import org.niis.xroad.cs.openapi.model.TrustedIssuerDto;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_TRUSTED_ISSUER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.LAST_TRUSTED_ISSUER;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class TrustedIssuersController implements TrustedIssuersApi {

    private final DataspaceIssuerDidService dataspaceIssuerDidService;
    private final AuditDataHelper auditDataHelper;

    @Override
    @PreAuthorize("hasAuthority('VIEW_TRUSTED_ISSUERS')")
    public ResponseEntity<List<TrustedIssuerDto>> getTrustedIssuers() {
        return ok(dataspaceIssuerDidService.findAllTrusted().stream()
                .map(TrustedIssuersController::toDto)
                .toList());
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_TRUSTED_ISSUER')")
    @AuditEventMethod(event = DELETE_TRUSTED_ISSUER)
    public ResponseEntity<Void> deleteTrustedIssuer(Integer trustedIssuerId) {
        TrustedIssuerRemoval removal = dataspaceIssuerDidService.remove(trustedIssuerId);
        auditDataHelper.put(DID, removal.did());
        auditDataHelper.put(LAST_TRUSTED_ISSUER, removal.lastTrustedIssuer());
        return noContent().build();
    }

    private static TrustedIssuerDto toDto(TrustedIssuer issuer) {
        return new TrustedIssuerDto()
                .id(issuer.id())
                .did(issuer.did())
                .createdAt(FormatUtils.fromInstantToOffsetDateTime(issuer.createdAt()));
    }
}
