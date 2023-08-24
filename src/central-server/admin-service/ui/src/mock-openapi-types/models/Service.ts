/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { Endpoint } from './Endpoint';

/**
 * service for the service description
 */
export type Service = {
  /**
   * encoded service id, including client id
   */
  id: string;
  /**
   * encoded service code and version
   */
  full_service_code?: string;
  /**
   * encoded service code
   */
  service_code: string;
  /**
   * service time out value
   */
  timeout: number;
  /**
   * service title
   */
  readonly title?: string;
  /**
   * service ssl auth
   */
  ssl_auth?: boolean;
  /**
   * count of acl subjects
   */
  subjects_count?: number;
  /**
   * service url
   */
  url: string;
  /**
   * list of endpoints linked to this service
   */
  endpoints?: Array<Endpoint>;
};
