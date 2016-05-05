/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.signer.protocol.dto;

import java.io.Serializable;

import lombok.ToString;
import lombok.Value;

import ee.ria.xroad.common.identifier.ClientId;

/**
 * Certificate info DTO.
 */
@Value
@ToString(exclude = { "certificateBytes", "ocspBytes" })
public class CertificateInfo implements Serializable {

    public static final String STATUS_SAVED = "saved";
    public static final String STATUS_REGINPROG = "registration in progress";
    public static final String STATUS_REGISTERED = "registered";
    public static final String STATUS_DELINPROG = "deletion in progress";
    public static final String STATUS_GLOBALERR = "global error";

    public static final String OCSP_RESPONSE_DELIMITER = ":";
    public static final String OCSP_RESPONSE_GOOD = "good";
    public static final String OCSP_RESPONSE_REVOKED = "revoked";
    public static final String OCSP_RESPONSE_UNKNOWN = "unknown";
    public static final String OCSP_RESPONSE_SUSPENDED = "suspended";

    private final ClientId memberId;

    private final boolean active;

    private final boolean savedToConfiguration;

    private final String status;

    private final String id;

    private final byte[] certificateBytes;
    private final byte[] ocspBytes;

    /**
     * @return returns the certificate as byte array
     */
    public byte[] getCertificateBytes() {
        return certificateBytes;
    }
}
