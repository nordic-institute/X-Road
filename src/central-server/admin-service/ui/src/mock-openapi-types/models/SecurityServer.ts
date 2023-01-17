/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * security server information
 */
export type SecurityServer = {
    /**
     * <instance_id>:<member_class>:<member_code>:<security_server_code>
     */
    id: string;
    /**
     * xroad instance id
     */
    instance_id?: string;
    /**
     * member class
     */
    member_class?: string;
    /**
     * member code
     */
    member_code?: string;
    /**
     * security server code
     */
    server_code?: string;
    /**
     * security server address (ip or name)
     */
    server_address?: string;
}
