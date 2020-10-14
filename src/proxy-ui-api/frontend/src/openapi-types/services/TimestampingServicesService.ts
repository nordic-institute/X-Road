/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { TimestampingService } from '../models/TimestampingService';
import { request as __request } from '../core/request';

export class TimestampingServicesService {

    /**
     * view the approved timestamping services
     * Administrator views the approved timestamping services.
     * @result TimestampingService list of approved timestamping services
     * @throws ApiError
     */
    public static async getApprovedTimestampingServices(): Promise<Array<TimestampingService>> {
        const result = await __request({
            method: 'GET',
            path: `/timestamping-services`,
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