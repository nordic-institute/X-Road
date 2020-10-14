/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { Endpoint } from '../models/Endpoint';
import type { EndpointUpdate } from '../models/EndpointUpdate';
import type { ServiceClient } from '../models/ServiceClient';
import type { ServiceClients } from '../models/ServiceClients';
import { request as __request } from '../core/request';

export class EndpointsService {

    /**
     * Get an endpoint by its id
     * Administrator fetches an endpoint
     * @param id id of the endpoint
     * @result Endpoint endpoint
     * @throws ApiError
     */
    public static async getEndpoint(
        id: string,
    ): Promise<Endpoint> {
        const result = await __request({
            method: 'GET',
            path: `/endpoints/${id}`,
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
     * Update an endpoint
     * Administrator updates an endpoint
     * @param id id of the endpoint
     * @param requestBody
     * @result Endpoint endpoint updated
     * @throws ApiError
     */
    public static async updateEndpoint(
        id: string,
        requestBody?: EndpointUpdate,
    ): Promise<Endpoint> {
        const result = await __request({
            method: 'PATCH',
            path: `/endpoints/${id}`,
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
     * delete endpoint
     * Administrator removes an endpoint
     * @param id id of the endpoint
     * @result any endpoint deleted
     * @throws ApiError
     */
    public static async deleteEndpoint(
        id: string,
    ): Promise<any> {
        const result = await __request({
            method: 'DELETE',
            path: `/endpoints/${id}`,
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
     * get service clients who have access rights for the selected endpoint
     * Administrator views endpoints access rights
     * @param id id of the endpoint
     * @result ServiceClient list of access rights
     * @throws ApiError
     */
    public static async getEndpointServiceClients(
        id: string,
    ): Promise<Array<ServiceClient>> {
        const result = await __request({
            method: 'GET',
            path: `/endpoints/${id}/service-clients`,
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
     * add access rights for given service clients to the selected endpoint
     * Administrator add access rights for a service clients to the selected endpoint
     * @param id id of the endpoint
     * @param requestBody
     * @result ServiceClient access rights added
     * @throws ApiError
     */
    public static async addEndpointServiceClients(
        id: string,
        requestBody?: ServiceClients,
    ): Promise<Array<ServiceClient>> {
        const result = await __request({
            method: 'POST',
            path: `/endpoints/${id}/service-clients`,
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
     * remove access rights from specified service clients to the selected endpoint
     * Administrator removes access rights from a service clients to an endpoint
     * @param id id of the endpoint
     * @param requestBody Service client to be removed
     * @result any access right(s) deleted
     * @throws ApiError
     */
    public static async deleteEndpointServiceClients(
        id: string,
        requestBody?: ServiceClients,
    ): Promise<any> {
        const result = await __request({
            method: 'POST',
            path: `/endpoints/${id}/service-clients/delete`,
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

}