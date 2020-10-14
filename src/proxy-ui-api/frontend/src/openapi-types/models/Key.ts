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
import type { KeyUsageType } from './KeyUsageType';
import type { PossibleActions } from './PossibleActions';
import type { TokenCertificate } from './TokenCertificate';
import type { TokenCertificateSigningRequest } from './TokenCertificateSigningRequest';

/**
 * Key for the certificate. Also includes the possible actions that can be done to this object, e.g DELETE (only for key related operations and does not consider user authorization).
 */
export interface Key {
    /**
     * key id
     */
    readonly id: string;
    /**
     * key name
     */
    name: string;
    /**
     * key label
     */
    label: string;
    /**
     * list of certificates for the key
     */
    certificates: Array<TokenCertificate>;
    /**
     * list of CSRs for the key
     */
    certificate_signing_requests: Array<TokenCertificateSigningRequest>;
    usage: KeyUsageType;
    /**
     * if the key is available
     */
    available?: boolean;
    /**
     * if the key is saved to configuration
     */
    saved_to_configuration?: boolean;
    possible_actions?: PossibleActions;
}
