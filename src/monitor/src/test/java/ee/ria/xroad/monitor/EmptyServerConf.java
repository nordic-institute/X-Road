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
package ee.ria.xroad.monitor;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.metadata.Endpoint;
import ee.ria.xroad.common.metadata.RestServiceDetailsListType;

import java.security.cert.X509Certificate;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * Implementation of ServerConfProvider that does nothing but return nulls. You
 * can extend this class and override only the more interesting methods.
 */
public class EmptyServerConf implements ServerConfProvider {

    @Override
    public boolean serviceExists(ServiceId service) {
        return true;
    }

    @Override
    public boolean isQueryAllowed(ClientId sender, ServiceId service, String method, String path) {
        return true;
    }

    @Override
    public String getDisabledNotice(ServiceId service) {
        return null;
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        return null;
    }

    @Override
    public SecurityServerId.Conf getIdentifier() {
        return null;
    }

    @Override
    public int getServiceTimeout(ServiceId service) {
        return 300;
    }

    @Override
    public RestServiceDetailsListType getRestServices(
            ClientId serviceProvider) {
        return null;
    }

    @Override
    public RestServiceDetailsListType getAllowedRestServices(ClientId serviceProvider,
                                                             ClientId client) {
        return null;
    }

    @Override
    public IsAuthentication getIsAuthentication(ClientId client) {
        return null;
    }

    @Override
    public List<X509Certificate> getIsCerts(ClientId client) throws Exception {
        return emptyList();
    }

    @Override
    public List<X509Certificate> getAllIsCerts() {
        return emptyList();
    }

    @Override
    public InternalSSLKey getSSLKey() throws Exception {
        return null;
    }

    @Override
    public List<String> getTspUrl() {
        return emptyList();
    }

    @Override
    public DescriptionType getDescriptionType(ServiceId service) {
        return null;
    }

    @Override
    public String getServiceDescriptionURL(ServiceId service) {
        return null;
    }

    @Override
    public List<Endpoint> getServiceEndpoints(ServiceId service) {
        return null;
    }

    @Override
    public boolean isSslAuthentication(ServiceId service) {
        return false;
    }

    @Override
    public List<ClientId.Conf> getMembers() throws Exception {
        return emptyList();
    }

    @Override
    public List<ServiceId.Conf> getAllServices(ClientId serviceProvider) {
        return emptyList();
    }

    @Override
    public List<ServiceId.Conf> getServicesByDescriptionType(ClientId serviceProvider, DescriptionType descriptionType) {
        return emptyList();
    }

    @Override
    public List<ServiceId.Conf> getAllowedServices(ClientId serviceProvider,
            ClientId client) {
        return emptyList();
    }

    @Override
    public List<ServiceId.Conf> getAllowedServicesByDescriptionType(ClientId serviceProvider,
                                                               ClientId client, DescriptionType descriptionType) {
        return emptyList();
    }

    @Override
    public String getMemberStatus(ClientId memberId) {
        return null;
    }

}
