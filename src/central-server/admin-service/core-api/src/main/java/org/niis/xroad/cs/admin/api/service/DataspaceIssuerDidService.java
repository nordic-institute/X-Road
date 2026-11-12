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
package org.niis.xroad.cs.admin.api.service;

import java.util.List;

/**
 * The Central Server's Issuer identity binding. Records the data space Issuer's identity once, at
 * provisioning time, so that globalconf generation can publish it as the dataspace issuer trust anchor.
 */
public interface DataspaceIssuerDidService {

    /**
     * Binds the Issuer identity. Fails if the same DID is already bound: the binding happens once and is
     * never replaced. A different DID is not rejected here.
     *
     * @param issuerDid the Issuer's DID
     * @param host the Issuer's published host
     * @param didPort the Issuer's published DID port
     * @param issuanceUrl the Issuer's published issuance URL
     * @param statuslistBaseUrl the Issuer's published statuslist base URL
     */
    void register(String issuerDid, String host, int didPort, String issuanceUrl, String statuslistBaseUrl);

    /**
     * @return every bound Issuer DID of this instance, or an empty list if none is bound
     */
    List<String> findAll();
}
