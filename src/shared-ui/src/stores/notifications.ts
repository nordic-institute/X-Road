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

import { defineStore } from 'pinia';
import { i18n } from '../plugins/i18n';
import axios from 'axios';

type NotificationId = number;

type Notification = {
  id: NotificationId;
  message: string;
  type: 'context-error' | 'success';
  asWarning?: boolean;
  preserve?: boolean;
  timeout: number;
};

type ErrorData = {
  correlationId?: string;
  code?: string;
  responseData?: string;
  metaData?: string[];
  url?: string;
  status?: string;
  warning?: boolean;
};

type ErrorNotification = { error: ErrorData; count: number } & Notification;

function isErrorNotification(notification: Notification): notification is ErrorNotification {
  return (notification as ErrorNotification).error !== undefined && notification.type === 'context-error';
}

function containsErrorNotification(notifications: Notification[], notification: ErrorNotification): number {
  if (!notification || !notifications || notifications.length === 0) {
    return -1;
  }

  return notifications.findIndex((e: Notification) => {
    if (!isErrorNotification(e)) {
      return false;
    }
    if (notification.error.responseData !== e.error.responseData) {
      return false;
    }

    if (notification.error.url !== e.error.url) {
      return false;
    }

    if (notification.error.status !== e.error.status) {
      return false;
    }

    if (notification.error.code !== e.error.code) {
      return false;
    }

    return notification.message === e.message;
  });
}

// Add error notification to the store
function addErrorNotification(notifications: Notification[], notification: ErrorNotification): Notification[] {
  // Check for duplicate
  const index = containsErrorNotification(notifications, notification);

  if (index > -1) {
    // If there is a duplicate, remove it and increase the count
    notification.count = (notifications[index] as ErrorNotification).count + 1;
    notifications.splice(index, 1);
  }

  notifications.push(notification);
  return notifications;
}

function addErrorMessage(notifications: Notification[], id: NotificationId, message: string, error: ErrorData = {}): void {
  const notification: ErrorNotification = {
    id,
    type: 'context-error',
    timeout: -1,
    // Store error object as a string that can be shown to the user
    message,
    count: 1,
    error,
  };

  addErrorNotification(notifications, notification);
}

export const ERROR_CODE_PREFIX = 'error_code.';

export const useNotifications = defineStore('xrd-notifications', {
  state: () => ({ index: 1 as NotificationId, notifications: [] as Notification[] }),
  getters: {
    contextErrors: (state) => state.notifications.filter(isErrorNotification),
    hasContextErrors: (state) => state.notifications.findIndex(isErrorNotification) > -1,
    successes: (state) => state.notifications.filter((item) => item.type === 'success'),
  },
  actions: {
    remove(notificationId: NotificationId) {
      const index = this.notifications.findIndex((item) => item.id === notificationId);
      if (index !== -1) {
        this.notifications.splice(index, 1);
      }
    },
    clearNotifications() {
      const preserved: Notification[] = [];
      this.notifications
        .filter((item) => item.preserve)
        .forEach((item) => {
          item.preserve = false;
          preserved.push(item);
        });
      this.notifications = preserved;
    },
    addTranslatedSuccessMessage(message: string, preserve = false) {
      const notification: Notification = {
        id: this.index++,
        type: 'success',
        timeout: 3000,
        message,
        preserve,
      };

      this.notifications.push(notification);
    },

    addSuccessMessage(messageKey: string, messageParams: Record<string, string> = {}, preserve = false) {
      this.addTranslatedSuccessMessage(i18n.global.t(messageKey, messageParams), preserve);
    },

    // Show error notification with axios error object
    addError(errorObject: unknown): void {
      // Show error using the x-road specific data in an axios error object
      // Don't show errors when the error code is 401 which is usually because of session expiring
      if (axios.isAxiosError(errorObject)) {
        const response = errorObject?.response;
        if (response?.status !== 401) {
          const error: ErrorData = {};

          // Data shown in notification component
          error.code = response?.data?.error?.code;
          error.metaData = response?.data?.error?.metadata;
          error.responseData = response?.config?.data;
          error.correlationId = response?.headers['x-road-ui-correlation-id'];

          // Data needed to compare with other notifications for handling duplicates
          error.url = response?.config?.url;
          error.status = response?.data?.status;
          let message = errorObject.toString();
          if (error.code && i18n.global.te(ERROR_CODE_PREFIX + error.code)) {
            message = i18n.global.t(ERROR_CODE_PREFIX + error.code);
          }

          addErrorMessage(this.notifications, this.index++, message, error);
        }
      } else if (errorObject instanceof Error) {
        this.addTranslatedErrorMessage(errorObject.message);
      } else {
        //TODO make it translatable
        this.addTranslatedErrorMessage('Unexpected error');
      }
    },

    addErrorMessage(messageKey: string, messageParams: Record<string, string> = {}): void {
      addErrorMessage(this.notifications, this.index++, i18n.global.t(messageKey, messageParams));
    },

    addTranslatedErrorMessage(message: string): void {
      addErrorMessage(this.notifications, this.index++, message);
    },
  },
});
