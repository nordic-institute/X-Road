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

import { AxiosError } from 'axios';
import { i18n } from '../plugins/i18n';
import { ErrorInfo } from '@/openapi-types';

export function getErrorInfo(axiosError: AxiosError): ErrorInfo {
  return (axiosError?.response?.data as ErrorInfo) || { status: 0 };
}

/*
 * isFieldError  -- checks if ErrorInfo contains Spring  Validation failure data
 * @params:
 *    errorInfo:  ErrorInfo  returned from Spring backend
 */
export function isFieldValidationError(errorInfo: ErrorInfo): boolean {
  const errorStatus = errorInfo?.status;
  return 400 === errorStatus && 'validation_failure' === errorInfo?.error?.code;
}

export function getTranslatedFieldErrors(fieldName: string, fieldError: Record<string, string[]>): string[] {
  const errors: string[] = fieldError[fieldName];
  if (errors) {
    return errors.map((errorKey: string) => {
      return i18n.global.t(`validationError.${errorKey}Field`).toString();
    });
  } else {
    return [];
  }
}

export function multipartFormDataConfig() {
  return {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  };
}

type FormFieldName = 'backup' | 'certificate' | 'anchor';

export function buildFileFormData(name: FormFieldName, file: File): FormData {
  const formData = new FormData();
  formData.set(name, file, file.name);

  return formData;
}
