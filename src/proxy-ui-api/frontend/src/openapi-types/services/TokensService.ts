/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { Key } from '../models/Key';
import type { KeyLabel } from '../models/KeyLabel';
import type { KeyLabelWithCsrGenerate } from '../models/KeyLabelWithCsrGenerate';
import type { KeyWithCertificateSigningRequestId } from '../models/KeyWithCertificateSigningRequestId';
import type { Token } from '../models/Token';
import type { TokenName } from '../models/TokenName';
import type { TokenPassword } from '../models/TokenPassword';
import { request as __request } from '../core/request';

export class TokensService {

    /**
     * get security server tokens
     * Administrator views tokens of the security server.
     * @result Token list of tokens
     * @throws ApiError
     */
    public static async getTokens(): Promise<Array<Token>> {
        const result = await __request({
            method: 'GET',
            path: `/tokens`,
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
     * get security server token information
     * Administrator views the token details of the security server.
     * @param id id of the token
     * @result Token token object
     * @throws ApiError
     */
    public static async getToken(
        id: string,
    ): Promise<Token> {
        const result = await __request({
            method: 'GET',
            path: `/tokens/${id}`,
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
     * update security server token information
     * Administrator updates the token information.
     * @param id id of the token
     * @param requestBody
     * @result Token token modified
     * @throws ApiError
     */
    public static async updateToken(
        id: string,
        requestBody?: TokenName,
    ): Promise<Token> {
        const result = await __request({
            method: 'PATCH',
            path: `/tokens/${id}`,
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
     * add a new key and generate a csr for it
     * Administrator adds a new key and generates a csr for it.
     * @param id id of the token
     * @param requestBody
     * @result KeyWithCertificateSigningRequestId key created for the token
     * @throws ApiError
     */
    public static async addKeyAndCsr(
        id: string,
        requestBody?: KeyLabelWithCsrGenerate,
    ): Promise<KeyWithCertificateSigningRequestId> {
        const result = await __request({
            method: 'POST',
            path: `/tokens/${id}/keys-with-csrs`,
            body: requestBody,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                409: `an existing item already exists or token not logged in`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * add new key
     * Adds key for selected token.
     * @param id id of the token
     * @param requestBody
     * @result Key key created for the token
     * @throws ApiError
     */
    public static async addKey(
        id: string,
        requestBody?: KeyLabel,
    ): Promise<Key> {
        const result = await __request({
            method: 'POST',
            path: `/tokens/${id}/keys`,
            body: requestBody,
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                409: `an existing item already exists or token not logged in`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * login to token
     * Login to token
     * @param id id of the token
     * @param requestBody
     * @result Token logged in
     * @throws ApiError
     */
    public static async loginToken(
        id: string,
        requestBody?: TokenPassword,
    ): Promise<Token> {
        const result = await __request({
            method: 'PUT',
            path: `/tokens/${id}/login`,
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
     * logout from token
     * Administrator logs out from token.
     * @param id id of the token
     * @result Token logged out
     * @throws ApiError
     */
    public static async logoutToken(
        id: string,
    ): Promise<Token> {
        const result = await __request({
            method: 'PUT',
            path: `/tokens/${id}/logout`,
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