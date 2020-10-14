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