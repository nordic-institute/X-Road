/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * response that tells if hsm tokens were logged out during the restore process
 */
export type TokensLoggedOut = {
  /**
   * whether any hsm tokens were logged out during the restore process
   */
  hsm_tokens_logged_out: boolean;
};
