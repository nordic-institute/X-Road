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

import java.util.Map;

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

    /** Disables the description; a {@code null} notice disables it without one. */
    record DisableDescription(String serviceCode, String notice) implements ServiceCall {
    }

    /** Renames the service code of a REST description, keeping its backend URL. */
    record RenameRestService(String serviceCode, String backendUrl, String newServiceCode) implements ServiceCall {
    }

    record GrantAccess(String providerClientId, String consumerClientId, String serviceCode) implements ServiceCall {
    }

    /** Revokes the consumer's access to the service, with its endpoint grants of that service. */
    record RevokeAccess(String providerClientId, String consumerClientId, String serviceCode) implements ServiceCall {
    }

    /** Grants the consumer access to a service the provider has from the start, such as a management service. */
    record GrantManagementAccess(String providerClientId, String consumerClientId, String serviceCode)
            implements ServiceCall {
    }

    /** Revokes the consumer's access to a service the provider has from the start. */
    record RevokeManagementAccess(String providerClientId, String consumerClientId, String serviceCode)
            implements ServiceCall {
    }

    /** Refreshes the service description from its backend, ignoring warnings. */
    record RefreshDescription(String serviceCode) implements ServiceCall {
    }

    /**
     * Makes the backend serve the named stub in place of the stub it normally serves at the path, for the requests
     * whose query carries the given values. The override is removed by {@link RemoveBackendStubs}.
     *
     * @param stubName     the stub whose response is served, without the {@code .json} suffix
     * @param urlPath      the path the response is served at
     * @param query        query parameters the request has to carry, by name
     * @param replacements text to replace in the served body, by the text it replaces
     */
    record OverrideBackendStub(String stubName, String urlPath, Map<String, String> query,
                               Map<String, String> replacements) implements AdminCall {
    }

    /** Removes every backend override made by {@link OverrideBackendStub}, in cleanup. */
    record RemoveBackendStubs() implements AdminCall {
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

    /** Deletes the description and its access rights, in setup or in cleanup. */
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

    /** Creates a global group at the Central Server. */
    record CreateGlobalGroup(String code) implements AdminCall {
    }

    /** Adds a member or subsystem to the global group at the Central Server. */
    record AddGlobalGroupMember(String code, String memberId) implements AdminCall {
    }

    /** Removes a member or subsystem from the global group at the Central Server. */
    record RemoveGlobalGroupMember(String code, String memberId) implements AdminCall {
    }

    /** Deletes the global group at the Central Server, in setup or in cleanup. */
    record DeleteGlobalGroup(String code) implements AdminCall {
    }

    /**
     * Grants the global group, of the provider's instance, access to the service. The provider's Security Server
     * knows the group only once global configuration has carried it there, so the grant waits for that.
     */
    record GrantGlobalGroupAccess(String providerClientId, String groupCode, String serviceCode)
            implements ServiceCall {
    }

    /** Grants the global group access to the endpoint of the service with the given method and path. */
    record GrantGlobalGroupEndpointAccess(String providerClientId, String groupCode, String serviceCode,
                                          String method, String path) implements ServiceCall {
    }

    /** Grants the provider's local group access to the service. */
    record GrantGroupAccess(String providerClientId, String groupCode, String serviceCode) implements ServiceCall {
    }

    /** Grants the provider's local group access to the endpoint of the service with the given method and path. */
    record GrantGroupEndpointAccess(String providerClientId, String groupCode, String serviceCode, String method,
                                    String path) implements ServiceCall {
    }
}
