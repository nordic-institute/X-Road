/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { ClientStatus } from './ClientStatus';
import type { ConnectionType } from './ConnectionType';

/**
 * x-road client
 */
export type Client = {
  /**
   * <instance_id>:<member_class>:<member_code>:<subsystem>(optional). Cannot contain colons, semicolons, slashes, backslashes, percent signs or control characters
   */
  readonly id?: string;
  /**
   * Xroad instance id. Cannot contain colons, semicolons, slashes, backslashes, percent signs or control characters
   */
  readonly instance_id?: string;
  /**
   * Member name. Cannot contain colons, semicolons, slashes, backslashes, percent signs or control characters
   */
  readonly member_name?: string;
  /**
   * Member class. Cannot contain colons, semicolons, slashes, backslashes, percent signs or control characters
   */
  member_class: string;
  /**
   * Member code. Cannot contain colons, semicolons, slashes, backslashes, percent signs or control characters
   */
  member_code: string;
  /**
   * Subsystem code. Cannot contain colons, semicolons, slashes, backslashes, percent signs or control characters
   */
  subsystem_code?: string;
  /**
   * if this client is the owner member of this security server
   */
  readonly owner?: boolean;
  /**
   * if this client is local and has a valid sign cert
   */
  readonly has_valid_local_sign_cert?: boolean;
  connection_type?: ConnectionType;
  status?: ClientStatus;
};
