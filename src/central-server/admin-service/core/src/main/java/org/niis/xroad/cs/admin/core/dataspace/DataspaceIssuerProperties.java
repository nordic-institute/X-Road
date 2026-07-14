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
package org.niis.xroad.cs.admin.core.dataspace;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Environment-specific configuration for data space issuer provisioning. The X-Road membership
 * credential shape itself is fixed and lives as constants in the provisioning service.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "xroad.dataspace.issuer")
public class DataspaceIssuerProperties {

    private static final int DEFAULT_DID_PORT = 6183;
    private static final int DEFAULT_ISSUANCE_PORT = 6185;

    /** Public hostname of the issuer (the {@code did:web} host) used to build the issuer DID and service URL. */
    private String host = "localhost";

    /** DID document port of the issuer. */
    private int didPort = DEFAULT_DID_PORT;

    /** Issuance API port of the issuer. */
    private int issuancePort = DEFAULT_ISSUANCE_PORT;

    /** URL of the published X-Road membership credential JSON schema. */
    private String credentialJsonSchemaUrl = "https://example.com/schema/XRoadMembershipCredential.json";
}
