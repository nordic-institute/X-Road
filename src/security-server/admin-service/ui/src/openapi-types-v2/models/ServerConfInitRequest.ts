/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
/**
 * Request to initialize server configuration
 */
export type ServerConfInitRequest = {
    /**
     * code for the security server
     */
    security_server_code: string;
    /**
     * member class of the owner
     */
    owner_member_class: string;
    /**
     * member code of the owner
     */
    owner_member_code: string;
    /**
     * whether to ignore warnings
     */
    ignore_warnings?: boolean;
};

