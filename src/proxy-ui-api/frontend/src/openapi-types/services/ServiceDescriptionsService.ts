/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { IgnoreWarnings } from '../models/IgnoreWarnings';
import type { Service } from '../models/Service';
import type { ServiceDescription } from '../models/ServiceDescription';
import type { ServiceDescriptionDisabledNotice } from '../models/ServiceDescriptionDisabledNotice';
import type { ServiceDescriptionUpdate } from '../models/ServiceDescriptionUpdate';
import { request as __request } from '../core/request';

export class ServiceDescriptionsService {

    /**
     * get service description with provided id
     * Administrator views a service description with a certain id.
     * @param id id of the service description
     * @result ServiceDescription wanted service description
     * @throws ApiError
     */
    public static async getServiceDescription(
        id: string,
    ): Promise<ServiceDescription> {
        const result = await __request({
            method: 'GET',
            path: `/service-descriptions/${id}`,
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
     * update url or service code for the selected service description
     * Administrator updates the selected service description.
     * @param id id of the service description
     * @param requestBody
     * @result ServiceDescription service description modified
     * @throws ApiError
     */
    public static async updateServiceDescription(
        id: string,
        requestBody?: ServiceDescriptionUpdate,
    ): Promise<ServiceDescription> {
        const result = await __request({
            method: 'PATCH',
            path: `/service-descriptions/${id}`,
            body: requestBody,
            errors: {
                400: `there are warnings or errors related to the service description`,
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
     * delete service description
     * Administrator deletes the service description.
     * @param id id of the service description
     * @result any service description deletion was successful
     * @throws ApiError
     */
    public static async deleteServiceDescription(
        id: string,
    ): Promise<any> {
        const result = await __request({
            method: 'DELETE',
            path: `/service-descriptions/${id}`,
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
     * disable selected service description
     * Administrator disables service description.
     * @param id id of the service description
     * @param requestBody
     * @result any service description disabled
     * @throws ApiError
     */
    public static async disableServiceDescription(
        id: string,
        requestBody?: ServiceDescriptionDisabledNotice,
    ): Promise<any> {
        const result = await __request({
            method: 'PUT',
            path: `/service-descriptions/${id}/disable`,
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
     * enable selected service description
     * Administrator enables service description.
     * @param id id of the service description
     * @result any service description enabled
     * @throws ApiError
     */
    public static async enableServiceDescription(
        id: string,
    ): Promise<any> {
        const result = await __request({
            method: 'PUT',
            path: `/service-descriptions/${id}/enable`,
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
     * refresh selected service description
     * Administrator refreshes service description.
     * @param id id of the service description
     * @param requestBody
     * @result ServiceDescription service description refreshed
     * @throws ApiError
     */
    public static async refreshServiceDescription(
        id: string,
        requestBody?: IgnoreWarnings,
    ): Promise<ServiceDescription> {
        const result = await __request({
            method: 'PUT',
            path: `/service-descriptions/${id}/refresh`,
            body: requestBody,
            errors: {
                400: `there are warnings or errors related to the service description`,
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
     * get services for the selected service description
     * Administrator views the services for the selected service description.
     * @param id id of the service description
     * @result Service list of services
     * @throws ApiError
     */
    public static async getServiceDescriptionServices(
        id: string,
    ): Promise<Array<Service>> {
        const result = await __request({
            method: 'GET',
            path: `/service-descriptions/${id}/services`,
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