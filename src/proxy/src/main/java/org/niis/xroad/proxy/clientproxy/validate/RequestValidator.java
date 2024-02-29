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
package org.niis.xroad.proxy.clientproxy.validate;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.SoapMessageImpl;

import lombok.extern.slf4j.Slf4j;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_CLIENT_IDENTIFIER;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_MEMBER;
import static ee.ria.xroad.proxy.util.MessageProcessorBase.checkIdentifier;

@Slf4j
public class RequestValidator {

    public void validateSoap(final SoapMessageImpl requestSoap, IsAuthenticationData clientCert) throws Exception {
        // Check that incoming identifiers do not contain illegal characters
        checkRequestIdentifiers(requestSoap);

        // Verify that the client is registered.
        ClientId client = requestSoap.getClient();
        verifyClientStatus(client);

        // Check client authentication mode.
        verifyClientAuthentication(client, clientCert);
    }

    private void checkRequestIdentifiers(final SoapMessageImpl requestSoap) {
        checkIdentifier(requestSoap.getClient());
        checkIdentifier(requestSoap.getService());
        checkIdentifier(requestSoap.getSecurityServer());
    }

    public void verifyClientStatus(ClientId client) {
        if (client == null) {
            throw new CodedException(X_INVALID_CLIENT_IDENTIFIER, "The client identifier is missing");
        }

        String status = ServerConf.getMemberStatus(client);
        if (!ClientType.STATUS_REGISTERED.equals(status)) {
            throw new CodedException(X_UNKNOWN_MEMBER, "Client '%s' not found", client);
        }
    }

    public void verifyClientAuthentication(ClientId sender, IsAuthenticationData clientCert) throws Exception {
        if (!SystemProperties.shouldVerifyClientCert()) {
            return;
        }
        log.trace("verifyClientAuthentication()");
        IsAuthentication.verifyClientAuthentication(sender, clientCert);
    }
}
