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
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AccessRight } from '../models/AccessRight';
import type { AccessRights } from '../models/AccessRights';
import type { CertificateDetails } from '../models/CertificateDetails';
import type { Client } from '../models/Client';
import type { ClientAdd } from '../models/ClientAdd';
import type { ConnectionTypeWrapper } from '../models/ConnectionTypeWrapper';
import type { LocalGroup } from '../models/LocalGroup';
import type { LocalGroupAdd } from '../models/LocalGroupAdd';
import type { OrphanInformation } from '../models/OrphanInformation';
import type { ServiceClient } from '../models/ServiceClient';
import type { ServiceClientType } from '../models/ServiceClientType';
import type { ServiceDescription } from '../models/ServiceDescription';
import type { ServiceDescriptionAdd } from '../models/ServiceDescriptionAdd';
import type { TokenCertificate } from '../models/TokenCertificate';
import { request as __request } from '../core/request';

export class ClientsService {

    /**
     * find security server clients
     * Administrator views the clients of the security server.
     * @param name pass an optional search string (name) for looking up clients
     * @param instance pass an optional search string (instance) for looking up clients
     * @param memberClass pass an optional search string (member_class) for looking up clients
     * @param memberCode pass an optional search string (member_code) for looking up clients
     * @param subsystemCode pass an optional search string (subsystem_code) for looking up clients
     * @param showMembers to include members for search results
     * @param internalSearch to search only clients inside security server
     * @param localValidSignCert to search only local clients with valid sign cert
     * @param excludeLocal to search only clients that are not added to this security server
     * @result Client list of clients
     * @throws ApiError
     */
    public static async findClients(
        name?: string,
        instance?: string,
        memberClass?: string,
        memberCode?: string,
        subsystemCode?: string,
        showMembers: boolean = true,
        internalSearch: boolean = true,
        localValidSignCert: boolean = false,
        excludeLocal: boolean = false,
    ): Promise<Array<Client>> {
        const result = await __request({
            method: 'GET',
            path: `/clients`,
            query: {
                'name': name,
                'instance': instance,
                'member_class': memberClass,
                'member_code': memberCode,
                'subsystem_code': subsystemCode,
                'show_members': showMembers,
                'internal_search': internalSearch,
                'local_valid_sign_cert': localValidSignCert,
                'exclude_local': excludeLocal,
            },
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * Add new client for the security server.
     * Adds new client to the system. Note that with this endpoint it is possible to add an unregistered member as a client. Attempt to add an unregistered member with ClientAdd.ignore_warnings = false causes the operation to fail with a warning in response's ErrorInfo object. Attempt to add an unregistered member with ClientAdd.ignore_warnings = true succeeds.
     *
     * @param requestBody client to add
     * @result Client new client created
     * @throws ApiError
     */
    public static async addClient(
        requestBody?: ClientAdd,
    ): Promise<Client> {
        const result = await __request({
            method: 'POST',
            path: `/clients`,
            body: requestBody,
            errors: {
                400: `there are warnings or errors related to the service description`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                409: `an existing item already exists`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * get security server client information
     * Administrator views the client details of the security server.
     * @param id id of the client
     * @result Client client object
     * @throws ApiError
     */
    public static async getClient(
        id: string,
    ): Promise<Client> {
        const result = await __request({
            method: 'GET',
            path: `/clients/${id}`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * update security server client information
     * Administrator updates the client information.
     * @param id id of the client
     * @param requestBody
     * @result Client client modified
     * @throws ApiError
     */
    public static async updateClient(
        id: string,
        requestBody?: ConnectionTypeWrapper,
    ): Promise<Client> {
        const result = await __request({
            method: 'PATCH',
            path: `/clients/${id}`,
            body: requestBody,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * delete security server client
     * Administrator deletes the client of the security server.
     * @param id id of the client
     * @result any client deletion was successful
     * @throws ApiError
     */
    public static async deleteClient(
        id: string,
    ): Promise<any> {
        const result = await __request({
            method: 'DELETE',
            path: `/clients/${id}`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * get local groups for the selected client
     * Administrator views the local groups for the client.
     * @param id id of the client
     * @result LocalGroup list of local groups
     * @throws ApiError
     */
    public static async getClientLocalGroups(
        id: string,
    ): Promise<Array<LocalGroup>> {
        const result = await __request({
            method: 'GET',
            path: `/clients/${id}/local-groups`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * add new local group for the security server client
     * Administrator adds a new local group for the client.
     * @param id id of the client
     * @param requestBody group to add
     * @result LocalGroup local group created
     * @throws ApiError
     */
    public static async addClientLocalGroup(
        id: string,
        requestBody?: LocalGroupAdd,
    ): Promise<LocalGroup> {
        const result = await __request({
            method: 'POST',
            path: `/clients/${id}/local-groups`,
            body: requestBody,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                409: `an existing item already exists`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * get information about orphaned sign keys, certificates and csrs left behind a delete client
     * Administrator has deleted a client and wants to know if some orphaned sign keys, certificates or csrs exist
     * @param id id of the client
     * @result OrphanInformation information telling that orphans exist. If they don't exist, 404 is returned instead.
     * @throws ApiError
     */
    public static async getClientOrphans(
        id: string,
    ): Promise<OrphanInformation> {
        const result = await __request({
            method: 'GET',
            path: `/clients/${id}/orphans`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * delete orphaned sign keys, certificates and csrs left behind a delete client
     * Administrator deletes the orphaned sign keys, certificates and csrs left behind a delete client.
     * @param id id of the client
     * @result any deletion was successful
     * @throws ApiError
     */
    public static async deleteOrphans(
        id: string,
    ): Promise<any> {
        const result = await __request({
            method: 'DELETE',
            path: `/clients/${id}/orphans`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * register security server client
     * Administrator registers client.
     * @param id id of the client
     * @result any client was registered
     * @throws ApiError
     */
    public static async registerClient(
        id: string,
    ): Promise<any> {
        const result = await __request({
            method: 'PUT',
            path: `/clients/${id}/register`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * get service clients for the selected client's services
     * Administrator views the service clients for the client.
     * @param id id of the client
     * @result ServiceClient list of service clients
     * @throws ApiError
     */
    public static async getClientServiceClients(
        id: string,
    ): Promise<Array<ServiceClient>> {
        const result = await __request({
            method: 'GET',
            path: `/clients/${id}/service-clients`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * get single service client by client id and service client id
     * Administrator views the information for a single service client
     * @param id id of the client
     * @param scId id of the service client
     * @result ServiceClient single service clients
     * @throws ApiError
     */
    public static async getServiceClient(
        id: string,
        scId: string,
    ): Promise<ServiceClient> {
        const result = await __request({
            method: 'GET',
            path: `/clients/${id}/service-clients/${scId}`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * get access rights for the selected service client.
     * Administrator views service client's access rights.
     * @param id id of the client who owns the services
     * @param scId id of the service client
     * @result AccessRight list of access rights
     * @throws ApiError
     */
    public static async getServiceClientAccessRights(
        id: string,
        scId: string,
    ): Promise<Array<AccessRight>> {
        const result = await __request({
            method: 'GET',
            path: `/clients/${id}/service-clients/${scId}/access-rights`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * add new access rights for selected service client. If service client did not exist yet, one is created.
     * Adds access rights to the service client.
     * @param id id of the client who owns the services
     * @param scId id of the service client
     * @param requestBody
     * @result AccessRight access right that was added
     * @throws ApiError
     */
    public static async addServiceClientAccessRights(
        id: string,
        scId: string,
        requestBody?: AccessRights,
    ): Promise<Array<AccessRight>> {
        const result = await __request({
            method: 'POST',
            path: `/clients/${id}/service-clients/${scId}/access-rights`,
            body: requestBody,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                409: `an existing item already exists`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * remove access rights
     * Administrator removes access rights from selected service client.
     * @param id id of the client who owns the services
     * @param scId id of the service client
     * @param requestBody list of access rights to be deleted
     * @result any access right(s) deleted
     * @throws ApiError
     */
    public static async deleteServiceClientAccessRights(
        id: string,
        scId: string,
        requestBody?: AccessRights,
    ): Promise<any> {
        const result = await __request({
            method: 'POST',
            path: `/clients/${id}/service-clients/${scId}/access-rights/delete`,
            body: requestBody,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                409: `an existing item already exists`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * get security server client certificates information
     * Administrator views the certificates for the client.
     * @param id id of the client
     * @result TokenCertificate list of certificates
     * @throws ApiError
     */
    public static async getClientSignCertificates(
        id: string,
    ): Promise<Array<TokenCertificate>> {
        const result = await __request({
            method: 'GET',
            path: `/clients/${id}/sign-certificates`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * get security server client TLS certificates information
     * Administrator views the TLS certificates for the client.
     * @param id id of the client
     * @result CertificateDetails list of tls certificates
     * @throws ApiError
     */
    public static async getClientTlsCertificates(
        id: string,
    ): Promise<Array<CertificateDetails>> {
        const result = await __request({
            method: 'GET',
            path: `/clients/${id}/tls-certificates`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * add new certificate for the security server client
     * Administrator adds a new certificate for the client.
     * @param id id of the client
     * @param requestBody certificate to add
     * @result CertificateDetails certificate added
     * @throws ApiError
     */
    public static async addClientTlsCertificate(
        id: string,
        requestBody?: any,
    ): Promise<CertificateDetails> {
        const result = await __request({
            method: 'POST',
            path: `/clients/${id}/tls-certificates`,
            body: requestBody,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                409: `an existing item already exists`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * get TLS certificate
     * Administrator gets the TLS certificate for the selected client.
     * @param id id of the client
     * @param hash SHA-1 hash of the certificate
     * @result CertificateDetails certificate details
     * @throws ApiError
     */
    public static async getClientTlsCertificate(
        id: string,
        hash: string,
    ): Promise<CertificateDetails> {
        const result = await __request({
            method: 'GET',
            path: `/clients/${id}/tls-certificates/${hash}`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * delete certificate
     * Administrator deletes the certificate from selected client.
     * @param id id of the client
     * @param hash SHA-1 hash of the certificate
     * @result any certificate deletion was successful
     * @throws ApiError
     */
    public static async deleteClientTlsCertificate(
        id: string,
        hash: string,
    ): Promise<any> {
        const result = await __request({
            method: 'DELETE',
            path: `/clients/${id}/tls-certificates/${hash}`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * unregister security server client
     * Administrator unregisters client.
     * @param id id of the client
     * @result any unregister was successful
     * @throws ApiError
     */
    public static async unregisterClient(
        id: string,
    ): Promise<any> {
        const result = await __request({
            method: 'PUT',
            path: `/clients/${id}/unregister`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * get security server client service descriptions
     * Administrator views the service descriptions for the client.
     * @param id id of the client
     * @result ServiceDescription list of service descriptions
     * @throws ApiError
     */
    public static async getClientServiceDescriptions(
        id: string,
    ): Promise<Array<ServiceDescription>> {
        const result = await __request({
            method: 'GET',
            path: `/clients/${id}/service-descriptions`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * add new service description for the security server client
     * Administrator adds a new service description for the client.
     * @param id id of the client
     * @param requestBody
     * @result ServiceDescription service description created
     * @throws ApiError
     */
    public static async addClientServiceDescription(
        id: string,
        requestBody?: ServiceDescriptionAdd,
    ): Promise<ServiceDescription> {
        const result = await __request({
            method: 'POST',
            path: `/clients/${id}/service-descriptions`,
            body: requestBody,
            errors: {
                400: `there are warnings or errors related to the service description`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                409: `an existing item already exists`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * find ServiceClient candidates for a specific client
     * Administrator views the clients, globalgroups and localgroups, that could be added as ServiceClients for given Client's services
     * @param id id of the client
     * @param memberNameGroupDescription pass an optional search string (name) for looking up subjects - name of a member or description of a group
     * @param serviceClientType pass an optional search string (service_client_type) for looking up service clients
     * @param instance pass an optional search string (instance) for looking up service clients - full instance id should be used
     * @param memberClass pass an optional search string (member_class) for looking up service clients
     * @param memberGroupCode pass an optional search string (member_group_code) for looking up service clients - member_code of a member or group_code of a group
     * @param subsystemCode pass an optional search string (subsystem_code) for looking up service clients
     * @result ServiceClient list of service clients
     * @throws ApiError
     */
    public static async findServiceClientCandidates(
        id: string,
        memberNameGroupDescription?: string,
        serviceClientType?: ServiceClientType,
        instance?: string,
        memberClass?: string,
        memberGroupCode?: string,
        subsystemCode?: string,
    ): Promise<Array<ServiceClient>> {
        const result = await __request({
            method: 'GET',
            path: `/clients/${id}/service-client-candidates`,
            query: {
                'member_name_group_description': memberNameGroupDescription,
                'service_client_type': serviceClientType,
                'instance': instance,
                'member_class': memberClass,
                'member_group_code': memberGroupCode,
                'subsystem_code': subsystemCode,
            },
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * make client Security Server's owner. Client must be a member and already registered on the Security Server
     * Administrator changes Security Server's owner.
     * @param id id of the client to be set as owner
     * @result any client was set as owner
     * @throws ApiError
     */
    public static async changeOwner(
        id: string,
    ): Promise<any> {
        const result = await __request({
            method: 'PUT',
            path: `/clients/${id}/make-owner`,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

}