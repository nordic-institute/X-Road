/*
 * The MIT License
 *
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

package org.niis.xroad.cs.admin.api.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Set;

@Data
@Accessors(chain = true)
public class CertificateDetails {

    private String hash;
    private String issuerCommonName;
    private String issuerDistinguishedName;
    private Set<KeyUsageEnum> keyUsages;
    private Instant notAfter;
    private Instant notBefore;
    private String publicKeyAlgorithm;
    private BigInteger rsaPublicKeyExponent;
    private String rsaPublicKeyModulus;
    private String ecPublicParameters;
    private String ecPublicKeyPoint;
    private String serial;
    private String signature;
    private String signatureAlgorithm;
    private String subjectAlternativeNames;
    private String subjectCommonName;
    private String subjectDistinguishedName;
    private Integer version;
    private byte[] encoded;

}
