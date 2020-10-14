/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import { request as __request } from '../core/request';

export class MemberClassesService {

    /**
     * get list of known member classes
     * Administrator lists member classes.
     * @param currentInstance if true, return member classes for this instance. if false (default), return member classes for all instances
     * @result string array of member classes
     * @throws ApiError
     */
    public static async getMemberClasses(
        currentInstance: boolean = false,
    ): Promise<Array<string>> {
        const result = await __request({
            method: 'GET',
            path: `/member-classes`,
            query: {
                'current_instance': currentInstance,
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
     * get list of known member classes for a given instance
     * Administrator lists member classes for a given instance.
     * @param id instance id
     * @result string array of member classes
     * @throws ApiError
     */
    public static async getMemberClassesForInstance(
        id: string,
    ): Promise<Array<string>> {
        const result = await __request({
            method: 'GET',
            path: `/member-classes/${id}`,
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