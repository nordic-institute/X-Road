/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import { request as __request } from '../core/request';

export class XroadInstancesService {

    /**
     * get list of known xroad instance identifiers
     * Administrator lists xroad instance identifiers
     * @result string xroad instance identifiers
     * @throws ApiError
     */
    public static async getXroadInstances(): Promise<Array<string>> {
        const result = await __request({
            method: 'GET',
            path: `/xroad-instances`,
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