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

package org.niis.xroad.e2e.parity.acl;

/** Builds the pieces of a REST call through the consumer Security Server's proxy. */
public final class RestRequests {

    public static final String CLIENT_HEADER = "x-road-client";

    private RestRequests() {
    }

    /**
     * @param proxyOrigin      scheme, host and port of the consumer's proxy
     * @param providerClientId the service owner in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     * @param serviceCode      the REST service code
     * @param path             the path after the service code, empty or starting with {@code /}
     */
    public static String url(String proxyOrigin, String providerClientId, String serviceCode, String path) {
        if (!path.isEmpty() && !path.startsWith("/")) {
            throw new IllegalArgumentException("path must be empty or start with '/': " + path);
        }
        return "%s/r1/%s/%s%s".formatted(proxyOrigin, slashed(providerClientId), serviceCode, path);
    }

    /**
     * @param proxyOrigin      scheme, host and port of the consumer's proxy
     * @param providerClientId the service owner in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     * @param metaserviceCode  the metaservice code, for example {@code listMethods}
     * @param targetServiceCode the service code the metaservice is asked about, or {@code null} for none
     */
    public static String metaserviceUrl(String proxyOrigin, String providerClientId, String metaserviceCode,
                                        String targetServiceCode) {
        var url = url(proxyOrigin, providerClientId, metaserviceCode, "");
        return targetServiceCode == null ? url : url + "?serviceCode=" + targetServiceCode;
    }

    /** The URL of the {@code listClients} metaservice of the consumer's own proxy. */
    public static String listClientsUrl(String proxyOrigin) {
        return proxyOrigin + "/listClients";
    }

    /** The {@code x-road-client} header value for a client in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form. */
    public static String clientHeader(String clientId) {
        return slashed(clientId);
    }

    private static String slashed(String clientId) {
        return clientId.replace(':', '/');
    }
}
