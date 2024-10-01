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
package ee.ria.xroad.signer.tokenmanager.module;

import lombok.Data;

import java.util.Set;

/**
 * PKCS#11 private key attributes.
 */
@Data
public class PrivKeyAttributes {
    // True, if this private key is sensitive.
    private Boolean sensitive;

    // True, if this private key can be used for encryption.
    private Boolean decrypt;

    // True, if this private key can be used for signing.
    private Boolean sign;

    // True, if this private key can be used for signing with recover.
    private Boolean signRecover;

    // True, if this private key can be used for unwrapping wrapped keys.
    private Boolean unwrap;

    // True, if this private key can not be extracted from the token.
    private Boolean extractable;

    // True, if this private key was always sensitive.
    private Boolean alwaysSensitive;

    // True, if this private key was never extractable.
    private Boolean neverExtractable;

    // True, if this private key can only be wrapped with a wrapping key having set the attribute trusted to true.
    private Boolean wrapWithTrusted;

    // The set of mechanism that can be used with this key.
    Set<Long> allowedMechanisms;
}
