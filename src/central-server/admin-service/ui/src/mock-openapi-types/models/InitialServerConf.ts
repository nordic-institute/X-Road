/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * security server initial configuration
 */
export type InitialServerConf = {
  /**
   * member class
   */
  owner_member_class?: string;
  /**
   * member code
   */
  owner_member_code?: string;
  /**
   * security server code
   */
  security_server_code?: string;
  /**
   * pin code for the initial software token
   */
  software_token_pin?: string;
  /**
   * if true, any ignorable warnings are ignored. if false (or missing), any warnings cause request to fail
   */
  ignore_warnings: boolean;
};
