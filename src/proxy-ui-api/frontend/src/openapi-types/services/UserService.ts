/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { User } from '../models/User';
import { request as __request } from '../core/request';

export class UserService {

    /**
     * get user data for the logged user
     * Administrator gets user data from backend.
     * @result User user details
     * @throws ApiError
     */
    public static async getUser(): Promise<User> {
        const result = await __request({
            method: 'GET',
            path: `/user`,
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