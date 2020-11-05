import { TokenStatus } from '@/openapi-types';

// This enum type is aimed for styling token login/logout button
export enum TokenUIStatus {
  Available = 'Available',
  Active = 'Active',
  Inactive = 'Inactive',
  Unavailable = 'Unavailable',
  Unsaved = 'Unsaved',
}

export const getTokenUIStatus = (status: TokenStatus): TokenUIStatus => {
  if (
    status === TokenStatus.USER_PIN_INCORRECT ||
    status === TokenStatus.USER_PIN_INVALID ||
    status === TokenStatus.USER_PIN_COUNT_LOW ||
    status === TokenStatus.USER_PIN_FINAL_TRY
  ) {
    return TokenUIStatus.Available;
  } else if (status === TokenStatus.OK) {
    return TokenUIStatus.Active;
  } else if (
    status === TokenStatus.USER_PIN_EXPIRED ||
    status === TokenStatus.USER_PIN_LOCKED
  ) {
    return TokenUIStatus.Unavailable;
  } else if (status === TokenStatus.NOT_INITIALIZED) {
    return TokenUIStatus.Unsaved;
  }
  return TokenUIStatus.Inactive;
};
