/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { SecurityServer } from '../models/SecurityServer';
import { request as __request } from '../core/request';

export class SecurityServersService {

    /**
     * get all security servers
     * SS administrator views the details of all security servers
     * @param currentServer whether to only get the current server's identifier
     * @result SecurityServer list of SecurityServer objects
     * @throws ApiError
     */
    public static async getSecurityServers(
        currentServer: boolean = false,
    ): Promise<Array<SecurityServer>> {
        const result = await __request({
            method: 'GET',
            path: `/security-servers`,
            query: {
                'current_server': currentServer,
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
     * get security server information
     * SS administrator views the details of a security server.
     * @param id id of the security server
     * @result SecurityServer ok
     * @throws ApiError
     */
    public static async getSecurityServer(
        id: string,
    ): Promise<SecurityServer> {
        const result = await __request({
            method: 'GET',
            path: `/security-servers/${id}`,
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