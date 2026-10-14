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
package org.niis.xroad.e2e;

import org.niis.xroad.e2e.AdminApi.AdminSession;

import static org.niis.xroad.e2e.AdminApi.addRestServiceDescription;
import static org.niis.xroad.e2e.AdminApi.deleteServiceDescription;
import static org.niis.xroad.e2e.AdminApi.discoverBackendUrl;
import static org.niis.xroad.e2e.AdminApi.enableServiceDescription;
import static org.niis.xroad.e2e.AdminApi.findServiceDescriptionIdByUrl;

/**
 * A REST service added to a provider client under a service code unique to one run, so no consumer can hold an
 * agreement for it from earlier traffic. It addresses the same real backend stub as an existing REST service of
 * that client: a REST service description's URL must be unique within its client, so the new description points
 * one path segment up and the segment is supplied by the caller as the trailing path of each request.
 *
 * @param providerClientId       the provider client, for example {@code DEV:COM:1234:TestService}
 * @param serviceCode            the unique service code
 * @param serviceDescriptionId   the id of the added service description
 * @param servicePath            the proxy path a consumer calls, including the trailing backend segment
 */
record FreshRestService(String providerClientId, String serviceCode, String serviceDescriptionId, String servicePath) {

    /** The id the data space uses for the service's asset. */
    String assetId() {
        return providerClientId + ":" + serviceCode;
    }

    /**
     * Adds and enables a new service on {@code providerClientId}, after removing any description an interrupted
     * earlier run left at the same backend URL.
     *
     * @param existingServiceId the full id of an existing REST service of the client whose backend is reused
     */
    static FreshRestService create(String baseUrl, AdminSession session, String providerClientId,
                                   String existingServiceId, String serviceCodePrefix) {
        var backendUrl = discoverBackendUrl(baseUrl, session, existingServiceId);
        var lastSegment = backendUrl.lastIndexOf('/');
        var backendBaseUrl = backendUrl.substring(0, lastSegment);
        var serviceCode = serviceCodePrefix + System.currentTimeMillis();

        findServiceDescriptionIdByUrl(baseUrl, session, providerClientId, backendBaseUrl)
                .ifPresent(leftoverId -> deleteServiceDescription(baseUrl, session, leftoverId));

        var serviceDescriptionId = addRestServiceDescription(baseUrl, session, providerClientId, backendBaseUrl, serviceCode);
        enableServiceDescription(baseUrl, session, serviceDescriptionId);

        var servicePath = "/r1/" + providerClientId.replace(':', '/') + "/" + serviceCode + backendUrl.substring(lastSegment);
        return new FreshRestService(providerClientId, serviceCode, serviceDescriptionId, servicePath);
    }

    /** Removes the service description, and with it the access rights granted on the service. */
    void remove(String baseUrl, AdminSession session) {
        deleteServiceDescription(baseUrl, session, serviceDescriptionId);
    }
}
