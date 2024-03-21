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

import { ref, Ref } from 'vue';
import { TranslateResult, useI18n } from 'vue-i18n';
import { useNotifications } from '@/store/modules/notifications';
import { MessageSchema } from '@/plugins/i18n';
import { AxiosError } from 'axios';
import { ErrorInfo } from '@/openapi-types';
import { getErrorInfo, getTranslatedFieldErrors, isFieldError } from '@/util/helpers';

type SetFieldError = (
  field: string,
  message: string | string[] | undefined,
) => void;

type BasicForm = {
  loading: Ref<boolean>;
  t: (key: string, props?: Record<string, unknown>) => string;
  showSuccess: (text: string | TranslateResult, preserve?: boolean) => void;
  showError: (error: unknown) => void;
  showOrTranslateErrors: (error: AxiosError) => void;
};

function noopSetFieldError(
  field: string,
  message: string | string[] | undefined,
) {
}

export function useBasicForm(
  setFieldError: SetFieldError = noopSetFieldError,
  errorMap: Record<string, string> = {},
): BasicForm {
  const { showSuccess, showError } = useNotifications();
  const { t } = useI18n<{ message: MessageSchema }>({ useScope: 'global' });
  const loading = ref(false);

  function showOrTranslateErrors(error: AxiosError) {
    const errorInfo: ErrorInfo = getErrorInfo(error as AxiosError);
    if (isFieldError(errorInfo)) {
      const fieldErrors = errorInfo.error?.validation_errors;
      if (fieldErrors) {
        for (const field in errorMap) {
          setFieldError(
            field,
            getTranslatedFieldErrors(errorMap[field], fieldErrors),
          );
        }
      }
    } else {
      showError(error);
    }
  }

  return { showSuccess, showError, loading, t, showOrTranslateErrors };
}

type FileN = File | undefined;

export function useFileRef(file: FileN = undefined): Ref<FileN> {
  return ref(file);
}
