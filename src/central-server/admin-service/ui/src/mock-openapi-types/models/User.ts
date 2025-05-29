/* istanbul ignore file */
/* tslint:disable */

/**
 * x-road user
 */
export type User = {
  /**
   * user username
   */
  username: string;
  /**
   * user roles
   */
  roles: Array<string>;
  /**
   * user permissions
   */
  permissions: Array<string>;
};
