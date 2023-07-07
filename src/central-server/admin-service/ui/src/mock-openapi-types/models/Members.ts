/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * Request object containing an array of member ids. The id must be an X-Road member id or subsystem id
 */
export type Members = {
    /**
     * array of members to be added
     */
    items?: Array<string>;
}
