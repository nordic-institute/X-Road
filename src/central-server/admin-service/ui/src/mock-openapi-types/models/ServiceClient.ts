/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { ServiceClientType } from './ServiceClientType';

/**
 * service client. May be a subsystem, local group, or a global group
 */
export type ServiceClient = {
  /**
   * subject id - can be a subsystem id <instance_id>:<member_class>:<member_code>:<subsystem> | globalgroup id <instance_id>:<group_code> | localgroup resource id in number format <id>
   */
  id: string;
  /**
   * name of the ServiceClient - can be the name of a member or the description of a group
   */
  readonly name?: string;
  /**
   * group code in case the object is a local group
   */
  readonly local_group_code?: string;
  service_client_type?: ServiceClientType;
  /**
   * time when access right were given at. When listing client's service clients without specifying the service, the time when first service access right was given to this service client for any service. When listing service clients for a specific service, time when service client was added permission to that service.
   */
  readonly rights_given_at?: string;
};
