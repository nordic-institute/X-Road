/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { Service } from './Service';
import type { ServiceType } from './ServiceType';

/**
 * WSDL/OPENAPI3/REST service
 */
export type ServiceDescription = {
  /**
   * unique identifier
   */
  id: string;
  /**
   * service url
   */
  url: string;
  type: ServiceType;
  /**
   * service disabled
   */
  disabled: boolean;
  /**
   * disabled notice
   */
  disabled_notice: string;
  /**
   * time for service refresh
   */
  refreshed_at: string;
  /**
   * service description services
   */
  services: Array<Service>;
  /**
   * <instance_id>:<member_class>:<member_code>:<subsystem>(optional)
   */
  client_id: string;
};
