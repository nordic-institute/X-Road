/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ServiceClient } from './ServiceClient';

/**
 * object containing and array of ServiceClients
 */
export interface ServiceClients {
    /**
     * array of ServiceClients
     */
    items?: Array<ServiceClient>;
}
