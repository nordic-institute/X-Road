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
 * One Security Server admin API call on the provider. A call about a service names it by service code and a
 * call about a local group names it by owner and group code; the executor resolves both to ids.
 */
public sealed interface AdminCall {

    /** A call that concerns one service. */
    sealed interface ServiceCall extends AdminCall {

        /** The service code the call concerns. */
        String serviceCode();
    }

    record AddRestDescription(String providerClientId, String backendUrl, String serviceCode) implements ServiceCall {
    }

    /** Adds a WSDL description; {@code serviceCode} is the first service code the description offers. */
    record AddWsdlDescription(String providerClientId, String wsdlUrl, String serviceCode) implements ServiceCall {
    }

    /** Adds an OpenAPI 3 description; {@code serviceCode} is the REST service code the description gets. */
    record AddOpenApiDescription(String providerClientId, String specUrl, String serviceCode) implements ServiceCall {
    }

    /** Points the OpenAPI 3 description of the service at another description and re-parses it. */
    record UpdateOpenApiDescription(String serviceCode, String specUrl) implements ServiceCall {
    }

    record EnableDescription(String serviceCode) implements ServiceCall {
    }

    record GrantAccess(String providerClientId, String consumerClientId, String serviceCode) implements ServiceCall {
    }

    record AddEndpoint(String serviceCode, String method, String path) implements ServiceCall {
    }

    /** Grants the consumer access to the endpoint of the service with the given method and path. */
    record GrantEndpointAccess(String serviceCode, String method, String path, String consumerClientId)
            implements ServiceCall {
    }

    /** Changes the method and path of an endpoint created by {@link AddEndpoint}. */
    record UpdateEndpoint(String serviceCode, String method, String path, String newMethod, String newPath)
            implements ServiceCall {
    }

    /**
     * Deletes an endpoint created by {@link AddEndpoint}, in setup or in cleanup; the base and generated
     * endpoints are never deleted.
     */
    record DeleteEndpoint(String serviceCode, String method, String path) implements ServiceCall {
    }

    record DeleteDescription(String serviceCode) implements ServiceCall {
    }

    /** Creates a local group of the owner client. */
    record CreateLocalGroup(String ownerClientId, String code) implements AdminCall {
    }

    /** Adds a member or subsystem to the owner's local group. */
    record AddLocalGroupMember(String ownerClientId, String code, String memberId) implements AdminCall {
    }

    /** Removes a member or subsystem from the owner's local group. */
    record RemoveLocalGroupMember(String ownerClientId, String code, String memberId) implements AdminCall {
    }

    /** Deletes the owner's local group, in setup or in cleanup; the group's access rights go with it. */
    record DeleteLocalGroup(String ownerClientId, String code) implements AdminCall {
    }

    /** Grants the provider's local group access to the service. */
    record GrantGroupAccess(String providerClientId, String groupCode, String serviceCode) implements ServiceCall {
    }

    /** Grants the provider's local group access to the endpoint of the service with the given method and path. */
    record GrantGroupEndpointAccess(String providerClientId, String groupCode, String serviceCode, String method,
                                    String path) implements ServiceCall {
    }
}
