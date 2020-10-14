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