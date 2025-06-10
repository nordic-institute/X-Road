/* istanbul ignore file */
/* tslint:disable */

import type { ServiceClient } from './ServiceClient';

/**
 * object containing and array of ServiceClients
 */
export type ServiceClients = {
  /**
   * array of ServiceClients
   */
  items?: Array<ServiceClient>;
};
