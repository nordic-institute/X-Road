/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
