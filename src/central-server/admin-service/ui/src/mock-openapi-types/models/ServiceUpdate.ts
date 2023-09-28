/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * object for updating a service or all services within service description
 */
export type ServiceUpdate = {
  /**
   * service url
   */
  url: string;
  /**
   * service time out value
   */
  timeout: number;
  /**
   * service ssl auth
   */
  ssl_auth: boolean;
  /**
   * url is applied for all services
   */
  url_all: boolean;
  /**
   * timeout value is applied for all services
   */
  timeout_all: boolean;
  /**
   * ssl authentication is applied for all services
   */
  ssl_auth_all: boolean;
  /**
   * if true, any ignorable warnings are ignored. if false (or missing), any warnings cause request to fail
   */
  ignore_warnings: boolean;
};
