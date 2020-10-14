/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
interface Config {
    BASE: string;
    VERSION: string;
    WITH_CREDENTIALS: boolean;
    TOKEN: string | (() => Promise<string>);
}

export const OpenAPI: Config = {
    BASE: '/api/v1',
    VERSION: '1.0.29',
    WITH_CREDENTIALS: false,
    TOKEN: '',
};