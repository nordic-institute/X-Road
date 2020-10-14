/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { Endpoint } from '../models/Endpoint';
import type { Service } from '../models/Service';
import type { ServiceClient } from '../models/ServiceClient';
import type { ServiceClients } from '../models/ServiceClients';
import type { ServiceUpdate } from '../models/ServiceUpdate';
import { request as __request } from '../core/request';

export class ServicesService {

    /**
     * get service
     * Administrator views selected service.
     * @param id id of the service
     * @result Service ok
     * @throws ApiError
     */
    public static async getService(
        id: string,
    ): Promise<Service> {
        const result = await __request({
            method: 'GET',
            path: `/services/${id}`,
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
     * update service
     * Administrator updates the service.
     * @param id id of the service
     * @param requestBody
     * @result Service service modified
     * @throws ApiError
     */
    public static async updateService(
        id: string,
        requestBody?: ServiceUpdate,
    ): Promise<Service> {
        const result = await __request({
            method: 'PATCH',
            path: `/services/${id}`,
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
     * create endpoint
     * Administrator creates a new endpoint.
     * @param id id of the service
     * @param requestBody
     * @result Endpoint endpoint added
     * @throws ApiError
     */
    public static async addEndpoint(
        id: string,
        requestBody?: Endpoint,
    ): Promise<Endpoint> {
        const result = await __request({
            method: 'POST',
            path: `/services/${id}/endpoints`,
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
     * get service clients who have access rights for the selected service
     * Administrator views service clients who have access to the given service
     * @param id id of the service
     * @result ServiceClient list of service clients
     * @throws ApiError
     */
    public static async getServiceServiceClients(
        id: string,
    ): Promise<Array<ServiceClient>> {
        const result = await __request({
            method: 'GET',
            path: `/services/${id}/service-clients`,
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
     * add access rights to selected service for new ServiceClients
     * Adds access rights to selected service for new ServiceClients
     * @param id id of the service
     * @param requestBody
     * @result ServiceClient access rights added
     * @throws ApiError
     */
    public static async addServiceServiceClients(
        id: string,
        requestBody?: ServiceClients,
    ): Promise<Array<ServiceClient>> {
        const result = await __request({
            method: 'POST',
            path: `/services/${id}/service-clients`,
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
     * remove access to selected service from given ServiceClients
     * Administrator removes access to selected service from given ServiceClients
     * @param id id of the service
     * @param requestBody
     * @result any access right(s) deletion was successful
     * @throws ApiError
     */
    public static async deleteServiceServiceClients(
        id: string,
        requestBody?: ServiceClients,
    ): Promise<any> {
        const result = await __request({
            method: 'POST',
            path: `/services/${id}/service-clients/delete`,
            body: requestBody,
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

}