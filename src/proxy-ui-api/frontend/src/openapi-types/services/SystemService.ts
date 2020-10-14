/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { Anchor } from '../models/Anchor';
import type { CertificateDetails } from '../models/CertificateDetails';
import type { DistinguishedName } from '../models/DistinguishedName';
import type { TimestampingService } from '../models/TimestampingService';
import type { Version } from '../models/Version';
import { request as __request } from '../core/request';

export class SystemService {

    /**
     * view the configuration anchor information
     * Administrator views the configuration anchor information.
     * @result Anchor anchor information
     * @throws ApiError
     */
    public static async getAnchor(): Promise<Anchor> {
        const result = await __request({
            method: 'GET',
            path: `/system/anchor`,
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
     * Upload a new configuration anchor file when initializing a new security server. Calls to this endpoint only succeed if a configuration anchor is not already found – meaning that this endpoint can only be used when initializing a new security server. For updating the anchor for an already initialized security server use the PUT /system/anchor endpoint instead
     * Administrator uploads a new configuration anchor file in the security server's initialization phase
     * @param requestBody configuration anchor
     * @result any configuration anchor uploaded
     * @throws ApiError
     */
    public static async uploadInitialAnchor(
        requestBody?: any,
    ): Promise<any> {
        const result = await __request({
            method: 'POST',
            path: `/system/anchor`,
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
     * Upload a configuration anchor file to replace an existing one. Note that this only works if there already exists an anchor that can be replaced. When initalizing a new Security Server use endpoint POST /system/anchor instead
     * Administrator uploads a configuration anchor file anytime after the Security Server has been initialized
     * @param requestBody configuration anchor
     * @result any configuration anchor uploaded
     * @throws ApiError
     */
    public static async replaceAnchor(
        requestBody?: any,
    ): Promise<any> {
        const result = await __request({
            method: 'PUT',
            path: `/system/anchor`,
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
     * Read and the configuration anchor file and return the hash for a preview. The instance of the anchor is also validated unless the validate_instance query parameter is explicitly set to false. The anchor will not be saved
     * Administrator wants to preview a configuration anchor file hash
     * @param validateInstance Whether or not to validate the owner instance of the anchor. Set this to false explicitly when previewing an anchor in the security server initialization phase. Default value is true if the parameter is omitted.
     * @param requestBody configuration anchor
     * @result Anchor configuration anchor uploaded
     * @throws ApiError
     */
    public static async previewAnchor(
        validateInstance: boolean = true,
        requestBody?: any,
    ): Promise<Anchor> {
        const result = await __request({
            method: 'POST',
            path: `/system/anchor/previews`,
            query: {
                'validate_instance': validateInstance,
            },
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
     * download configuration anchor information
     * Administrator downloads the configuration anchor information.
     * @result any configuration anchor
     * @throws ApiError
     */
    public static async downloadAnchor(): Promise<any> {
        const result = await __request({
            method: 'GET',
            path: `/system/anchor/download`,
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
     * view the security server certificate information
     * Administrator views the security server TLS certificate information.
     * @result CertificateDetails certificate information
     * @throws ApiError
     */
    public static async getSystemCertificate(): Promise<CertificateDetails> {
        const result = await __request({
            method: 'GET',
            path: `/system/certificate`,
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
     * generate a new internal TLS key and cert
     * Administrator generates new internal TLS key and certificate
     * @result any tls key generated
     * @throws ApiError
     */
    public static async generateSystemTlsKeyAndCertificate(): Promise<any> {
        const result = await __request({
            method: 'POST',
            path: `/system/certificate`,
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
     * download the security server certificate as gzip compressed tar archive
     * Administrator downloads the security server TLS certificate.
     * @result any information fetched successfully
     * @throws ApiError
     */
    public static async downloadSystemCertificate(): Promise<any> {
        const result = await __request({
            method: 'GET',
            path: `/system/certificate/export`,
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
     * generate new certificate request
     * Administrator generates new certificate request.
     * @param requestBody
     * @result any created CSR
     * @throws ApiError
     */
    public static async generateSystemCertificateRequest(
        requestBody?: DistinguishedName,
    ): Promise<any> {
        const result = await __request({
            method: 'POST',
            path: `/system/certificate/csr`,
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
     * import new internal TLS certificate.
     * Administrator imports new internal TLS certificate
     * @param requestBody certificate to add
     * @result CertificateDetails tls certificate imported
     * @throws ApiError
     */
    public static async importSystemCertificate(
        requestBody?: any,
    ): Promise<CertificateDetails> {
        const result = await __request({
            method: 'POST',
            path: `/system/certificate/import`,
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
     * view the configured timestamping services
     * Administrator views the configured timestamping services.
     * @result TimestampingService list of configured timestamping services
     * @throws ApiError
     */
    public static async getConfiguredTimestampingServices(): Promise<Array<TimestampingService>> {
        const result = await __request({
            method: 'GET',
            path: `/system/timestamping-services`,
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
     * add a configured timestamping service
     * Administrator selects new timestamping service
     * @param requestBody Timestamping service to add
     * @result TimestampingService timestamping service created
     * @throws ApiError
     */
    public static async addConfiguredTimestampingService(
        requestBody?: TimestampingService,
    ): Promise<TimestampingService> {
        const result = await __request({
            method: 'POST',
            path: `/system/timestamping-services`,
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
     * delete configured timestamping service
     * Administrator removes configured timestamping service.
     * @param requestBody Timestamping service to delete
     * @result any timestamping service deletion was successful
     * @throws ApiError
     */
    public static async deleteConfiguredTimestampingService(
        requestBody?: TimestampingService,
    ): Promise<any> {
        const result = await __request({
            method: 'POST',
            path: `/system/timestamping-services/delete`,
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

    /**
     * get information for the system version
     * Administrator views key details.
     * @result Version system version information
     * @throws ApiError
     */
    public static async systemVersion(): Promise<Version> {
        const result = await __request({
            method: 'GET',
            path: `/system/version`,
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