/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * token type
 */
export enum TokenStatus {
  OK = 'OK',
  USER_PIN_LOCKED = 'USER_PIN_LOCKED',
  USER_PIN_INCORRECT = 'USER_PIN_INCORRECT',
  USER_PIN_INVALID = 'USER_PIN_INVALID',
  USER_PIN_EXPIRED = 'USER_PIN_EXPIRED',
  USER_PIN_COUNT_LOW = 'USER_PIN_COUNT_LOW',
  USER_PIN_FINAL_TRY = 'USER_PIN_FINAL_TRY',
  NOT_INITIALIZED = 'NOT_INITIALIZED',
}
