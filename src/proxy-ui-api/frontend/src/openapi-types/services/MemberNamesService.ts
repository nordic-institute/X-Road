/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { MemberName } from '../models/MemberName';
import { request as __request } from '../core/request';

export class MemberNamesService {

    /**
     * find member name by member class and member code
     * Administrator looks up member's name
     * @param memberClass class of the member
     * @param memberCode code of the member
     * @result MemberName name of the member
     * @throws ApiError
     */
    public static async findMemberName(
        memberClass: string,
        memberCode: string,
    ): Promise<MemberName> {
        const result = await __request({
            method: 'GET',
            path: `/member-names`,
            query: {
                'member_class': memberClass,
                'member_code': memberCode,
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

}