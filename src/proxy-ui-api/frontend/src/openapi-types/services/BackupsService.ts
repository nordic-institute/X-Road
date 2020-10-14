/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { Backup } from '../models/Backup';
import type { TokensLoggedOut } from '../models/TokensLoggedOut';
import { request as __request } from '../core/request';

export class BackupsService {

    /**
     * get security server backups
     * Administrator views the backups for the security server.
     * @result Backup list of security server backups
     * @throws ApiError
     */
    public static async getBackups(): Promise<Array<Backup>> {
        const result = await __request({
            method: 'GET',
            path: `/backups`,
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
     * add new backup for the security server
     * Adds security server backup to the system
     * @result Backup item created
     * @result any item accepted
     * @throws ApiError
     */
    public static async addBackup(): Promise<Backup | any> {
        const result = await __request({
            method: 'POST',
            path: `/backups`,
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
     * upload new backup for the security server
     * Uploads new security server backup to the system
     * @param ignoreWarnings if true, any ignorable warnings are ignored. if false (or missing), any warnings cause request to fail
     * @param requestBody backup to add
     * @result Backup item created
     * @result any item accepted
     * @throws ApiError
     */
    public static async uploadBackup(
        ignoreWarnings: boolean = false,
        requestBody?: any,
    ): Promise<Backup | any> {
        const result = await __request({
            method: 'POST',
            path: `/backups/upload`,
            query: {
                'ignore_warnings': ignoreWarnings,
            },
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
     * delete security server backup
     * Administrator deletes the backup of the security server.
     * @param filename filename of the backup
     * @result any deletion was successful
     * @throws ApiError
     */
    public static async deleteBackup(
        filename: string,
    ): Promise<any> {
        const result = await __request({
            method: 'DELETE',
            path: `/backups/${filename}`,
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
     * restore security server configuration from backup
     * Administrator restores the security server configuration from backup.
     * @param filename filename of the backup
     * @result TokensLoggedOut restore was successful
     * @throws ApiError
     */
    public static async restoreBackup(
        filename: string,
    ): Promise<TokensLoggedOut> {
        const result = await __request({
            method: 'PUT',
            path: `/backups/${filename}/restore`,
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
     * download security server backup
     * Administrator downloads the backup of the security server.
     * @param filename filename of the backup
     * @result any backup file downloaded
     * @throws ApiError
     */
    public static async downloadBackup(
        filename: string,
    ): Promise<any> {
        const result = await __request({
            method: 'GET',
            path: `/backups/${filename}/download`,
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