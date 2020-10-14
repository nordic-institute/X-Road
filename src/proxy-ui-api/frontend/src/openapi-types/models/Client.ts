/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ClientStatus } from './ClientStatus';
import type { ConnectionType } from './ConnectionType';

/**
 * x-road client
 */
export interface Client {
    /**
     * <instance_id>:<member_class>:<member_code>:<subsystem>(optional)
     */
    readonly id?: string;
    /**
     * xroad instance id
     */
    readonly instance_id?: string;
    /**
     * member name
     */
    readonly member_name?: string;
    /**
     * member class
     */
    member_class: string;
    /**
     * member code
     */
    member_code: string;
    /**
     * subsystem code
     */
    subsystem_code?: string;
    /**
     * if this client is the owner member of this security server
     */
    readonly owner?: boolean;
    /**
     * if this client is local and has a valid sign cert
     */
    readonly has_valid_local_sign_cert?: boolean;
    connection_type?: ConnectionType;
    status?: ClientStatus;
}
