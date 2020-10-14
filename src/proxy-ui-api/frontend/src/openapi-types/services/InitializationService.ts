/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { InitializationStatus } from '../models/InitializationStatus';
import type { InitialServerConf } from '../models/InitialServerConf';
import { request as __request } from '../core/request';

export class InitializationService {

    /**
     * Initialize a new security server with the provided initial configuration
     * Administrator initializes a new security server with the provided initial configuration
     * @param requestBody initial security server configuration
     * @result any security server initialized
     * @throws ApiError
     */
    public static async initSecurityServer(
        requestBody?: InitialServerConf,
    ): Promise<any> {
        const result = await __request({
            method: 'POST',
            path: `/initialization`,
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
     * Check the initialization status of the Security Server
     * Administrator checks the initialization status of the Security Server
     * @result InitializationStatus initialization status of the Security Server
     * @throws ApiError
     */
    public static async getInitializationStatus(): Promise<InitializationStatus> {
        const result = await __request({
            method: 'GET',
            path: `/initialization/status`,
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

}