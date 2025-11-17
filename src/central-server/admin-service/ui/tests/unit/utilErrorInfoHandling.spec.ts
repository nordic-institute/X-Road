/*
 * The MIT License
 *
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
/* eslint-disable @typescript-eslint/ban-ts-comment */
import { AxiosError, AxiosHeaders } from 'axios';
import { ErrorInfo } from '@/openapi-types';
import { describe, expect, it } from 'vitest';
import { axiosHelpers } from '@niis/shared-ui';

describe('util/helpers.ts ', () => {
  const hostAddressError = {
    status: 400,
    error: {
      code: 'validation_failure',
      validation_errors: {
        'serverAddressUpdateBody.centralServerAddress': ['ValidHostAddress'],
      },
    },
  };
  const realAxiosError: AxiosError<ErrorInfo> = {
    config: {
      headers: {} as AxiosHeaders,
    },
    isAxiosError: true,
    message: 'Correct test AxiosError',
    name: '',
    response: {
      data: hostAddressError,
      status: 0,
      statusText: 'Fake status',
      headers: {},
      config: { headers: {} as AxiosHeaders },
    },
    stack: '',
    toJSON(): Record<string, unknown> {
      return {};
    },
  };

  it('getErrorInfo gets error info out from Validation ', () => {
    const testError = Object.assign({}, realAxiosError);

    const errorInfo: ErrorInfo = axiosHelpers.getErrorInfo(testError);

    expect(errorInfo).not.toBeUndefined();
    expect(errorInfo).toEqual(hostAddressError);
  });

  it('getErrorInfo returns sparse AxiosError with null input', () => {
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    const errorInfo = axiosHelpers.getErrorInfo(null);
    expect(errorInfo).not.toBeUndefined();
    expect(errorInfo.status).not.toBeUndefined();
  });

  it('isFieldValidationError does correct decision with real errorInfo', () => {
    const fieldError = Object.assign({}, hostAddressError);
    expect(() => axiosHelpers.isFieldValidationError(fieldError)).not.toThrowError();
    expect(axiosHelpers.isFieldValidationError(fieldError)).toBeTruthy();
  });
  it('isFieldValidationError returns false with null input', () => {
    // @ts-ignore
    expect(() => axiosHelpers.isFieldValidationError(null)).not.toThrowError();
    // @ts-ignore
    expect(axiosHelpers.isFieldValidationError(null)).not.toBeTruthy();
  });
  it('isFieldValidationError return false with non-400 status', () => {
    const fieldError999 = Object.assign({}, hostAddressError);
    fieldError999.status = 999;
    expect(axiosHelpers.isFieldValidationError(fieldError999)).not.toBeTruthy();
  });
  it('isFieldValidationError return false with missing error code ', () => {
    const fieldErrorNoCode = Object.assign({}, hostAddressError);
    // @ts-ignore
    fieldErrorNoCode.error.code = undefined;
    expect(axiosHelpers.isFieldValidationError(fieldErrorNoCode)).not.toBeTruthy();
  });
});
