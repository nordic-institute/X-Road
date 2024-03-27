package org.niis.xroad.securityserver.restapi.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AcmeCustomSchema {

    XRD_ACME("xrd-acme"),
    XRD_ACME_PROFILE_ID("xrd-acme-profile-id");

    private final String schema;
}
