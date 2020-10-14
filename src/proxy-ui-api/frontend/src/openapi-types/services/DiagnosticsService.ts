/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { GlobalConfDiagnostics } from '../models/GlobalConfDiagnostics';
import type { OcspResponderDiagnostics } from '../models/OcspResponderDiagnostics';
import type { TimestampingServiceDiagnostics } from '../models/TimestampingServiceDiagnostics';
import { request as __request } from '../core/request';

export class DiagnosticsService {

    /**
     * view global configuration diagnostics information
     * Administrator views the global configuration diagnostics information.
     * @result GlobalConfDiagnostics global configuration diagnostics information
     * @throws ApiError
     */
    public static async getGlobalConfDiagnostics(): Promise<GlobalConfDiagnostics> {
        const result = await __request({
            method: 'GET',
            path: `/diagnostics/globalconf`,
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
     * view ocsp responders diagnostics information
     * Administrator views the ocsp responders diagnostics information.
     * @result OcspResponderDiagnostics ocsp responders diagnostics information
     * @throws ApiError
     */
    public static async getOcspRespondersDiagnostics(): Promise<Array<OcspResponderDiagnostics>> {
        const result = await __request({
            method: 'GET',
            path: `/diagnostics/ocsp-responders`,
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
     * view timestamping services diagnostics information
     * Administrator views the timestamping services diagnostics information.
     * @result TimestampingServiceDiagnostics timestamping services diagnostics information
     * @throws ApiError
     */
    public static async getTimestampingServicesDiagnostics(): Promise<Array<TimestampingServiceDiagnostics>> {
        const result = await __request({
            method: 'GET',
            path: `/diagnostics/timestamping-services`,
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