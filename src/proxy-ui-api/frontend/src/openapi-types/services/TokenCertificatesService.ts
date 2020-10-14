/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { PossibleActions } from '../models/PossibleActions';
import type { SecurityServerAddress } from '../models/SecurityServerAddress';
import type { TokenCertificate } from '../models/TokenCertificate';
import { request as __request } from '../core/request';

export class TokenCertificatesService {

    /**
     * import new certificate
     * Imports certificate to the system
     * @param requestBody certificate to import
     * @result TokenCertificate certificate created
     * @throws ApiError
     */
    public static async importCertificate(
        requestBody?: any,
    ): Promise<TokenCertificate> {
        const result = await __request({
            method: 'POST',
            path: `/token-certificates`,
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
     * get certificate information
     * Administrator views certificate details.
     * @param hash SHA-1 hash of the certificate
     * @result TokenCertificate token certificate
     * @throws ApiError
     */
    public static async getCertificate(
        hash: string,
    ): Promise<TokenCertificate> {
        const result = await __request({
            method: 'GET',
            path: `/token-certificates/${hash}`,
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
     * delete certificate
     * Administrator deletes the certificate.
     * @param hash SHA-1 hash of the certificate
     * @result any deletion was successful
     * @throws ApiError
     */
    public static async deleteCertificate(
        hash: string,
    ): Promise<any> {
        const result = await __request({
            method: 'DELETE',
            path: `/token-certificates/${hash}`,
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
     * activate certificate
     * Administrator activates selected certificate.
     * @param hash SHA-1 hash of the certificate
     * @result any request was successful
     * @throws ApiError
     */
    public static async activateCertificate(
        hash: string,
    ): Promise<any> {
        const result = await __request({
            method: 'PUT',
            path: `/token-certificates/${hash}/activate`,
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
     * deactivate certificate
     * Administrator deactivates selected certificate.
     * @param hash SHA-1 hash of the certificate
     * @result any certificate was deactivated
     * @throws ApiError
     */
    public static async disableCertificate(
        hash: string,
    ): Promise<any> {
        const result = await __request({
            method: 'PUT',
            path: `/token-certificates/${hash}/disable`,
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
     * import an existing certificate from a token by cert hash
     * Imports certificate from a token to the system
     * @param hash SHA-1 hash of the certificate
     * @result TokenCertificate the imported certificate
     * @throws ApiError
     */
    public static async importCertificateFromToken(
        hash: string,
    ): Promise<TokenCertificate> {
        const result = await __request({
            method: 'POST',
            path: `/token-certificates/${hash}/import`,
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
     * get possible actions for one certificate
     * UI needs to know which actions can be done on one certificate
     * @param hash SHA-1 hash of the certificate
     * @result PossibleActions possible actions that can be done on the certificate
     * @throws ApiError
     */
    public static async getPossibleActionsForCertificate(
        hash: string,
    ): Promise<PossibleActions> {
        const result = await __request({
            method: 'GET',
            path: `/token-certificates/${hash}/possible-actions`,
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
     * register certificate
     * Administrator registers selected certificate.
     * @param hash SHA-1 hash of the certificate
     * @param requestBody
     * @result any request was successful
     * @throws ApiError
     */
    public static async registerCertificate(
        hash: string,
        requestBody?: SecurityServerAddress,
    ): Promise<any> {
        const result = await __request({
            method: 'PUT',
            path: `/token-certificates/${hash}/register`,
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
     * unregister authentication certificate
     * Administrator unregisters selected authentication certificate.
     * @param hash SHA-1 hash of the certificate
     * @result any request was successful
     * @throws ApiError
     */
    public static async unregisterAuthCertificate(
        hash: string,
    ): Promise<any> {
        const result = await __request({
            method: 'PUT',
            path: `/token-certificates/${hash}/unregister`,
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
     * marks an auth certificate for deletion
     * Administrator marks an auth certificate for deletion.
     * @param hash SHA-1 hash of the certificate
     * @result any request was successful
     * @throws ApiError
     */
    public static async markAuthCertForDeletion(
        hash: string,
    ): Promise<any> {
        const result = await __request({
            method: 'PUT',
            path: `/token-certificates/${hash}/mark-for-deletion`,
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