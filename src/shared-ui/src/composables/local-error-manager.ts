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

import { ref } from 'vue';

import { ErrorManager, ErrorNotification, NotificationId } from '../types';

import { addError, remove, addTranslatedErrorMessage } from './notifications-helper';
import { i18n } from '../plugins/i18n';

export function useLocalErrorManager(): ErrorManager {
  const errors = ref([] as ErrorNotification[]);

  return {
    addErrorMessage(messageKey: string, messageParams: Record<string, unknown> = {}): void {
      addTranslatedErrorMessage(errors.value, i18n.global.t(messageKey, messageParams));
    },
    addTranslatedErrorMessage(message: string): void {
      addTranslatedErrorMessage(errors.value, message);
    },
    errors,
    remove(id: NotificationId) {
      remove(id, errors.value);
    },
    clear() {
      const size = errors.value.length;
      errors.value.splice(0, size);
    },
    hasErrors(): boolean {
      return errors.value.length > 0;
    },
    addError(errorObject: unknown) {
      addError(errors.value, errorObject);
    },
  };
}
