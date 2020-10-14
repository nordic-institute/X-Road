/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { Language } from '../models/Language';
import { request as __request } from '../core/request';

export class LanguageService {

    /**
     * change language
     * Administrator changes the language for the UI.
     * @param code code of the language (language code)
     * @result Language language changed
     * @throws ApiError
     */
    public static async language(
        code: string,
    ): Promise<Language> {
        const result = await __request({
            method: 'PUT',
            path: `/language/${code}`,
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