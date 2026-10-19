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

package org.niis.xroad.e2e.parity;

/**
 * One Security Server admin API call on the provider, identified by the service code it concerns. The
 * executor resolves service codes to service description ids.
 */
public sealed interface AdminCall {

    /** The service code the call concerns. */
    String serviceCode();

    record AddRestDescription(String providerClientId, String backendUrl, String serviceCode) implements AdminCall {
    }

    /** Adds a WSDL description; {@code serviceCode} is the first service code the description offers. */
    record AddWsdlDescription(String providerClientId, String wsdlUrl, String serviceCode) implements AdminCall {
    }

    record EnableDescription(String serviceCode) implements AdminCall {
    }

    record GrantAccess(String providerClientId, String consumerClientId, String serviceCode) implements AdminCall {
    }

    record AddEndpoint(String serviceCode, String method, String path) implements AdminCall {
    }

    /** Grants the consumer access to the endpoint of the service with the given method and path. */
    record GrantEndpointAccess(String serviceCode, String method, String path, String consumerClientId)
            implements AdminCall {
    }

    /** Deletes an endpoint created by {@link AddEndpoint}; the base and generated endpoints are never deleted. */
    record DeleteEndpoint(String serviceCode, String method, String path) implements AdminCall {
    }

    record DeleteDescription(String serviceCode) implements AdminCall {
    }
}
