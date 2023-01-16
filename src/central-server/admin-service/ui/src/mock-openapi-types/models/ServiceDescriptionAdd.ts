/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { ServiceType } from './ServiceType';

/**
 * request object containing service description url, service code and type
 */
export type ServiceDescriptionAdd = {
    /**
     * path for the service description file
     */
    url: string;
    /**
     * service code for REST service
     */
    rest_service_code?: string;
    /**
     * if true, any ignorable warnings are ignored. if false (or missing), any warnings cause request to fail
     */
    ignore_warnings: boolean;
    type: ServiceType;
}
