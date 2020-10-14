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