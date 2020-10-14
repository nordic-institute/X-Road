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
import type { LocalGroup } from '../models/LocalGroup';
import type { LocalGroupDescription } from '../models/LocalGroupDescription';
import type { Members } from '../models/Members';
import { request as __request } from '../core/request';

export class LocalGroupsService {

    /**
     * get local group information
     * Administrator views local group details.
     * @param groupId id of the local group
     * @result LocalGroup group object
     * @throws ApiError
     */
    public static async getLocalGroup(
        groupId: string,
    ): Promise<LocalGroup> {
        const result = await __request({
            method: 'GET',
            path: `/local-groups/${groupId}`,
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
     * update local group information
     * Administrator updates the local group information.
     * @param groupId id of the local group
     * @param requestBody
     * @result LocalGroup local group modified
     * @throws ApiError
     */
    public static async updateLocalGroup(
        groupId: string,
        requestBody?: LocalGroupDescription,
    ): Promise<LocalGroup> {
        const result = await __request({
            method: 'PATCH',
            path: `/local-groups/${groupId}`,
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
     * delete local group
     * Administrator deletes the local group.
     * @param groupId id of the local group
     * @result any local group deletion was successful
     * @throws ApiError
     */
    public static async deleteLocalGroup(
        groupId: string,
    ): Promise<any> {
        const result = await __request({
            method: 'DELETE',
            path: `/local-groups/${groupId}`,
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
     * add new member for the local group
     * Administrator adds a new member for the local group. The new member can be an X-Road member or a subsystem
     * @param groupId id of the local group
     * @param requestBody
     * @result Members new members added
     * @throws ApiError
     */
    public static async addLocalGroupMember(
        groupId: string,
        requestBody?: Members,
    ): Promise<Members> {
        const result = await __request({
            method: 'POST',
            path: `/local-groups/${groupId}/members`,
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
     * delete member from local group
     * Administrator deletes the member from local group.
     * @param groupId id of the local group
     * @param requestBody
     * @result any members deleted
     * @throws ApiError
     */
    public static async deleteLocalGroupMember(
        groupId: string,
        requestBody?: Members,
    ): Promise<any> {
        const result = await __request({
            method: 'POST',
            path: `/local-groups/${groupId}/members/delete`,
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

}