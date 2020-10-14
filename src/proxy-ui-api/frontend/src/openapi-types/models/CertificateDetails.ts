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
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { KeyUsage } from './KeyUsage';

/**
 * certificate details for any kind of certificate (TLS, auth, sign)
 */
export interface CertificateDetails {
    /**
     * certificate issuer distinguished name
     */
    issuer_distinguished_name: string;
    /**
     * certificate issuer common name
     */
    issuer_common_name: string;
    /**
     * certificate subject distinguished name
     */
    subject_distinguished_name: string;
    /**
     * certificate subject common name
     */
    subject_common_name: string;
    /**
     * certificate validity not before
     */
    not_before: string;
    /**
     * certificate validity not after
     */
    not_after: string;
    /**
     * serial number
     */
    serial: string;
    /**
     * version
     */
    version: number;
    /**
     * certificate signature algorithm
     */
    signature_algorithm: string;
    /**
     * hex encoded certificate signature
     */
    signature: string;
    /**
     * certificate public key algorithm
     */
    public_key_algorithm: string;
    /**
     * hex encoded RSA public key modulus (if RSA key)
     */
    rsa_public_key_modulus: string;
    /**
     * RSA public key exponent (if RSA key) as an integer
     */
    rsa_public_key_exponent: number;
    /**
     * certificate SHA-1 hash
     */
    hash: string;
    /**
     * certificate key usage array
     */
    key_usages: Array<KeyUsage>;
}
