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

import { Ref, ref } from 'vue';

import { AxiosError } from 'axios';

import { getErrorInfo, isFieldValidationError, getTranslatedFieldErrors } from '../utils';

import { useNotifications } from './notifications-manager';

type SetFieldError<T> = (field: T, message: string | string[] | undefined) => void;

function noopSetFieldError() {}

export function useBasicForm<T extends string>(
  setFieldError: SetFieldError<T> = noopSetFieldError,
  errorMap: Record<T, string> = {} as Record<T, string>,
) {
  const { addSuccessMessage, addError } = useNotifications();
  const loading = ref(false);

  function showOrTranslateErrors(error: AxiosError) {
    const errorInfo = getErrorInfo(error as AxiosError);
    if (isFieldValidationError(errorInfo)) {
      const fieldErrors = errorInfo.error?.validation_errors;
      if (fieldErrors) {
        for (const field in errorMap) {
          setFieldError(field, getTranslatedFieldErrors(errorMap[field], fieldErrors));
        }
      }
    } else {
      addError(error);
    }
  }

  return { addSuccessMessage, addError, loading, showOrTranslateErrors };
}

type FileN = File | undefined;

export function useFileRef(file: FileN = undefined): Ref<FileN> {
  return ref(file);
}
