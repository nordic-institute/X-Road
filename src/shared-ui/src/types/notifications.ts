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

import { Ref } from 'vue';

export const ERROR_CODE_PREFIX = 'error_code.';

export type AddError = (errorObject: unknown) => void;

export interface ErrorManager {
  errors: Ref<ErrorNotification[]>;

  addError: AddError;

  addErrorMessage(messageKey: string, messageParams?: Record<string, unknown>): void;

  addTranslatedErrorMessage(messageText: string): void;

  remove(id: NotificationId): void;

  clear(): void;

  hasErrors(): boolean;
}

export interface NotificationManager extends ErrorManager {
  hasContextErrors: Ref<boolean>;
  successes: Ref<Notification[]>;

  addSuccessMessage(messageKey: string, messageParams?: Record<string, unknown>, preserve?: boolean): void;

  addTranslatedSuccessMessage(message: string, preserve?: boolean): void;

  addError(errorObject: unknown, config?: ErrorConfigWithNavigate): void;

  setFlag(name: string, value: string | boolean): void;

  getFlag(name: string): string | boolean | undefined;

  hasFlag(name: string): boolean;

  clearFlag(name: string): void;
}

export type NotificationId = number;

export type Notification = {
  id: NotificationId;
  message: string;
  type: 'context-error' | 'success';
  asWarning?: boolean;
  preserve?: boolean;
  timeout: number;
};

export type ValidationError = { field: string; codes: string[] };

export type ErrorData = {
  correlationId?: string;
  code?: string;
  responseData?: string;
  metaData?: string[];
  url?: string;
  status?: string;
  validationErrors?: ValidationError[];
};

export type ErrorNotification = { error: ErrorData; count: number } & Notification;

export type ErrorConfig = {
  navigate?: boolean;
  preserve?: boolean;
  warning?: boolean;
};

type ErrorConfigWithNavigate = {
  navigate?: boolean;
} & ErrorConfig;
