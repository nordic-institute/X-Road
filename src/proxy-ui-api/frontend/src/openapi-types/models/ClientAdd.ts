/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { Client } from './Client';

/**
 * request to add client. Carries a Client and ignore warnings parameter
 */
export interface ClientAdd {
    client: Client;
    /**
     * if true, any ignorable warnings are ignored. if false (or missing), any warnings cause request to fail
     */
    ignore_warnings?: boolean;
}
