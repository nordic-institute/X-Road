/**
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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.conf.serverconf.IsAuthentication;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionType;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between ConnectionType in api (enum) and model (IsAuthentication enum)
 */
@Getter
@RequiredArgsConstructor
public enum ConnectionTypeMapping {
    NOSSL(IsAuthentication.NOSSL, ConnectionType.HTTP),
    SSLNOAUTH(IsAuthentication.SSLNOAUTH, ConnectionType.HTTPS_NO_AUTH),
    SSLAUTH(IsAuthentication.SSLAUTH, ConnectionType.HTTPS);

    private final IsAuthentication isAuthentication; // ClientType isAuthentication values (from DB)
    private final ConnectionType connectionTypeEnum;

    /**
     * Return matching ConnectionTypeEnum, if any. Convenience method for mapping isAuthentication
     * Strings instead of IsAuthentication enum values
     * @param isAuthenticationString
     * value
     */
    public static Optional<ConnectionType> map(String isAuthenticationString) {
        return getFor(isAuthenticationString).map(ConnectionTypeMapping::getConnectionTypeEnum);
    }

    /**
     * Return matching ConnectionTypeEnum, if any
     * @param isAuthentication
     * @return
     */
    public static Optional<ConnectionType> map(IsAuthentication isAuthentication) {
        return getFor(isAuthentication).map(ConnectionTypeMapping::getConnectionTypeEnum);
    }

    /**
     * Return matching client type isAuthentication, if any
     * @param connectionTypeEnum
     * @return
     */
    public static Optional<IsAuthentication> map(ConnectionType connectionTypeEnum) {
        return getFor(connectionTypeEnum).map(ConnectionTypeMapping::getIsAuthentication);
    }

    /**
     * return item matching ClientType isAuthentication, if any
     * @param isAuthentication
     * @return
     */
    public static Optional<ConnectionTypeMapping> getFor(IsAuthentication isAuthentication) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.isAuthentication.equals(isAuthentication))
                .findFirst();
    }

    /**
     * return item matching ClientType isAuthenticationString, if any
     * @param isAuthenticationString
     * @return
     */
    public static Optional<ConnectionTypeMapping> getFor(String isAuthenticationString) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.isAuthentication.name().equals(isAuthenticationString))
                .findFirst();
    }

    /**
     * return item matching ClientType connectionTypeEnum, if any
     * @param connectionTypeEnum
     * @return
     */
    public static Optional<ConnectionTypeMapping> getFor(ConnectionType connectionTypeEnum) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.connectionTypeEnum.equals(connectionTypeEnum))
                .findFirst();
    }

}
