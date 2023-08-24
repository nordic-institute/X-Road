/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * access right given for a specific subject (ServiceClient) for specific service (service_code) owned by some client. This object does not represent endpoint-level access rights
 */
export type AccessRight = {
  /**
   * service code
   */
  service_code: string;
  /**
   * service title
   */
  readonly service_title?: string;
  /**
   * access right given at
   */
  readonly rights_given_at?: string;
};
