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

/** The provider Security Server's admin API as the fixture executor uses it. */
public interface AdminGateway {

    /** Adds a REST service description and returns its id. */
    String addRestDescription(String providerClientId, String backendUrl, String serviceCode);

    /** Adds a WSDL service description and returns its id. */
    String addWsdlDescription(String providerClientId, String wsdlUrl);

    /** Adds an OpenAPI 3 service description and returns its id. */
    String addOpenApiDescription(String providerClientId, String specUrl, String serviceCode);

    /** Points the OpenAPI 3 service description at another description. */
    void updateOpenApiDescription(String descriptionId, String specUrl, String serviceCode);

    void enableDescription(String descriptionId);

    /** Disables the description; a {@code null} notice sends none. */
    void disableDescription(String descriptionId, String notice);

    /** Renames the service code of the REST description, keeping its backend URL. */
    void renameRestService(String descriptionId, String backendUrl, String serviceCode, String newServiceCode);

    /** Grants the consumer access to the service and returns whether the grant was created, not already there. */
    boolean grantAccess(String providerClientId, String consumerClientId, String serviceCode);

    /** Asks for the subject, a member or subsystem, to be granted access to the service and returns the HTTP status. */
    int requestAccessGrant(String providerClientId, String subjectId, String serviceCode);

    /** Revokes the consumer's access to the service, with its endpoint grants of that service. */
    void revokeAccess(String providerClientId, String consumerClientId, String serviceCode);

    /** Refreshes the service description from its backend. */
    void refreshDescription(String descriptionId);

    /** Starts serving the override from the backend and returns its id. */
    String overrideBackendStub(AdminCall.OverrideBackendStub override);

    /** Stops serving the override and returns the HTTP status of the call. */
    int removeBackendStub(String overrideId);

    /** Creates an endpoint on the REST service of the description and returns its id. */
    String addEndpoint(String descriptionId, String serviceCode, String method, String path);

    /** Finds the id of the endpoint of the description's service with the given method and path. */
    String findEndpointId(String descriptionId, String serviceCode, String method, String path);

    /** Changes the method and path of the endpoint. */
    void updateEndpoint(String endpointId, String method, String path);

    void grantEndpointAccess(String endpointId, String consumerClientId);

    /** Deletes the endpoint and returns the HTTP status of the call. */
    int deleteEndpoint(String endpointId);

    /** Deletes the service description and returns the HTTP status of the call. */
    int deleteDescription(String descriptionId);

    /** Creates a local group of the owner client and returns its id. */
    String createLocalGroup(String ownerClientId, String code);

    /** Adds a member or subsystem to the local group. */
    void addLocalGroupMember(String groupId, String memberId);

    /** Removes a member or subsystem from the local group. */
    void removeLocalGroupMember(String groupId, String memberId);

    /** Deletes the local group and returns the HTTP status of the call. */
    int deleteLocalGroup(String groupId);

    /** Creates a global group at the Central Server. */
    void createGlobalGroup(String code);

    /** Adds a member or subsystem to the global group at the Central Server. */
    void addGlobalGroupMember(String code, String memberId);

    /** Removes a member or subsystem from the global group at the Central Server. */
    void removeGlobalGroupMember(String code, String memberId);

    /** Deletes the global group at the Central Server and returns the HTTP status of the call. */
    int deleteGlobalGroup(String code);

    /**
     * Grants the global group, named {@code INSTANCE:CODE}, access to the service, waiting until the provider's
     * Security Server knows the group.
     */
    void grantGlobalGroupAccess(String providerClientId, String groupId, String serviceCode);

    /** Grants the global group, named {@code INSTANCE:CODE}, access to the endpoint, waiting as the service grant. */
    void grantGlobalGroupEndpointAccess(String endpointId, String groupId);
}
