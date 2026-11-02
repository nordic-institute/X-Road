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
package org.niis.xroad.common.agreementtoken;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

/**
 * The two lookups {@link AgreementTokenVerifier} needs to judge a group agreement token, both answered from live
 * Security Server configuration: whether the signature-proven caller is in the group, and whether the live ACL
 * lets that caller use the requested endpoint. A service-only token is judged by the endpoint lookup alone.
 */
public interface AgreementTokenGroupAccess {

    /** Refuses every group and service-only token. Used when a verifier is built without a source of group configuration. */
    AgreementTokenGroupAccess NONE = new AgreementTokenGroupAccess() {
        @Override
        public boolean isMember(AgreementTokenGroup group, ClientId client) {
            return false;
        }

        @Override
        public boolean isEndpointAllowed(ClientId client, ServiceId service, String requestMethod, String requestPath) {
            return false;
        }
    };

    /**
     * @return true if {@code client}, compared exactly (subsystem included), belongs to {@code group}
     */
    boolean isMember(AgreementTokenGroup group, ClientId client);

    /**
     * @param requestMethod the REST verb, or {@code null} for SOAP
     * @param requestPath   the raw REST service path, or {@code null} for SOAP
     * @return true if the live ACL lets {@code client} call {@code service} at that endpoint
     */
    boolean isEndpointAllowed(ClientId client, ServiceId service, String requestMethod, String requestPath);
}
