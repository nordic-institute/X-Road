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
import type { CsrFormat } from '../models/CsrFormat';
import type { CsrGenerate } from '../models/CsrGenerate';
import type { Key } from '../models/Key';
import type { KeyName } from '../models/KeyName';
import type { PossibleActions } from '../models/PossibleActions';
import { request as __request } from '../core/request';

export class KeysService {

    /**
     * get information for the selected key in selected token
     * Administrator views key details.
     * @param id id of the key
     * @result Key key object
     * @throws ApiError
     */
    public static async getKey(
        id: string,
    ): Promise<Key> {
        const result = await __request({
            method: 'GET',
            path: `/keys/${id}`,
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
     * update key information
     * Administrator updates the key information.
     * @param id id of the key
     * @param requestBody
     * @result Key key modified
     * @throws ApiError
     */
    public static async updateKey(
        id: string,
        requestBody?: KeyName,
    ): Promise<Key> {
        const result = await __request({
            method: 'PATCH',
            path: `/keys/${id}`,
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
     * delete key
     * Administrator deletes the key. Note that with this endpoint it's possible to delete an authentication key with a registered authentication certificate. Attempt to delete an authentication key with a registered authentication certificate and with ignore_warnings = false causes the operation to fail with a warning in response's ErrorInfo object. Attempt to delete an authentication key with a registered authentication certificate and with ignore_warnings = true succeeds. The authentication certificate is first unregistered, and the key and certificate are deleted after that.
     *
     * @param id id of the key
     * @param ignoreWarnings if true, any ignorable warnings are ignored. if false (or missing), any warnings cause request to fail
     * @result any key deletion was successful
     * @throws ApiError
     */
    public static async deleteKey(
        id: string,
        ignoreWarnings: boolean = false,
    ): Promise<any> {
        const result = await __request({
            method: 'DELETE',
            path: `/keys/${id}`,
            query: {
                'ignore_warnings': ignoreWarnings,
            },
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
     * generate csr for the selected key
     * Administrator generates csr for the key.
     * @param id id of the key
     * @param requestBody request to generate csr
     * @result any created CSR
     * @throws ApiError
     */
    public static async generateCsr(
        id: string,
        requestBody?: CsrGenerate,
    ): Promise<any> {
        const result = await __request({
            method: 'POST',
            path: `/keys/${id}/csrs`,
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
     * download a CSR binary
     * Administrator downloads a csr that has been created earlier
     * @param id id of the key
     * @param csrId id of the csr
     * @param csrFormat format of the certificate signing request (PEM or DER)
     * @result any CSR binary
     * @throws ApiError
     */
    public static async downloadCsr(
        id: string,
        csrId: string,
        csrFormat?: CsrFormat,
    ): Promise<any> {
        const result = await __request({
            method: 'GET',
            path: `/keys/${id}/csrs/${csrId}`,
            query: {
                'csr_format': csrFormat,
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
     * delete csr from the selected key
     * Administrator deletes csr from the key.
     * @param id id of the key
     * @param csrId id of the csr
     * @result any csr deletion was successful
     * @throws ApiError
     */
    public static async deleteCsr(
        id: string,
        csrId: string,
    ): Promise<any> {
        const result = await __request({
            method: 'DELETE',
            path: `/keys/${id}/csrs/${csrId}`,
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
     * get possible actions for one csr
     * UI needs to know which actions can be done on one csr
     * @param id id of the key
     * @param csrId id of the csr
     * @result PossibleActions possible actions that can be done on the certificate
     * @throws ApiError
     */
    public static async getPossibleActionsForCsr(
        id: string,
        csrId: string,
    ): Promise<PossibleActions> {
        const result = await __request({
            method: 'GET',
            path: `/keys/${id}/csrs/${csrId}/possible-actions`,
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
     * get possible actions for one key
     * UI needs to know which actions can be done on one key
     * @param id id of the key
     * @result PossibleActions possible actions that can be done on the certificate
     * @throws ApiError
     */
    public static async getPossibleActionsForKey(
        id: string,
    ): Promise<PossibleActions> {
        const result = await __request({
            method: 'GET',
            path: `/keys/${id}/possible-actions`,
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