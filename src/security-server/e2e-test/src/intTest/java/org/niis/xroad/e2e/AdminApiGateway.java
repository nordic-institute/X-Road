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
import org.niis.xroad.e2e.parity.AdminGateway;

/** The provider Security Server's admin API, reached through {@link AdminApi}. */
final class AdminApiGateway implements AdminGateway {

    private final String baseUrl;
    private final AdminSession session;

    AdminApiGateway(String baseUrl, AdminSession session) {
        this.baseUrl = baseUrl;
        this.session = session;
    }

    @Override
    public String addRestDescription(String providerClientId, String backendUrl, String serviceCode) {
        return AdminApi.addRestServiceDescription(baseUrl, session, providerClientId, backendUrl, serviceCode);
    }

    @Override
    public String addWsdlDescription(String providerClientId, String wsdlUrl) {
        return AdminApi.addWsdlServiceDescription(baseUrl, session, providerClientId, wsdlUrl);
    }

    @Override
    public void enableDescription(String descriptionId) {
        AdminApi.enableServiceDescription(baseUrl, session, descriptionId);
    }

    @Override
    public void grantAccess(String providerClientId, String consumerClientId, String serviceCode) {
        AdminApi.grantConsumerAccessRights(baseUrl, session, providerClientId, consumerClientId, serviceCode);
    }

    @Override
    public int deleteDescription(String descriptionId) {
        return AdminApi.requestServiceDescriptionDeletion(baseUrl, session, descriptionId);
    }
}
