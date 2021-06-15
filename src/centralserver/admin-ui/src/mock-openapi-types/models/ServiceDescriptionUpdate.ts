/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { ServiceType } from './ServiceType';

/**
 * request object for updating a service description url or service code
 */
export type ServiceDescriptionUpdate = {
    /**
     * path for the service description file
     */
    url: string;
    /**
     * service code for REST service
     */
    rest_service_code?: string;
    /**
     * new service code for REST service
     */
    new_rest_service_code?: string;
    /**
     * if true, any ignorable warnings are ignored. if false (or missing), any warnings cause request to fail
     */
    ignore_warnings: boolean;
    type: ServiceType;
}
