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

import { ActionError, Notification } from '@/ui-types';
import { defineStore } from 'pinia';
import { TranslateResult } from 'vue-i18n';
import axios from 'axios';

// Helper functions

// Finds if an array of notifications contains a similar notification.
function containsNotification(
  errorNotifications: Notification[],
  notification: Notification,
): number {
  if (!notification || !errorNotifications || errorNotifications.length === 0) {
    return -1;
  }
  return errorNotifications.findIndex((e: Notification) => {
    if (notification?.responseData !== e?.responseData) {
      return false;
    }

    if (notification?.url !== e?.url) {
      return false;
    }

    if (notification?.status !== e?.status) {
      return false;
    }

    if (notification?.errorCode !== e?.errorCode) {
      return false;
    }

    return notification?.errorMessage === e?.errorMessage;
  });
}

// Add error notification to the store
function addErrorNotification(
  errorNotifications: Notification[],
  notification: Notification,
): Notification[] {
  // Check for duplicate
  const index = containsNotification(errorNotifications, notification);

  if (index > -1) {
    // If there is a duplicate, remove it and increase the count
    notification.count = errorNotifications[index].count + 1;
    errorNotifications.splice(index, 1);
  }

  errorNotifications.push(notification);
  return errorNotifications;
}

function createEmptyNotification(timeout: number): Notification {
  // Returns a new "empty" notification
  return {
    timeout: timeout,
    timeAdded: Date.now(),
    show: true,
    count: 1,
  };
}

export const notificationsStore = defineStore('notificationsStore', {
  state: () => {
    return {
      errorNotifications: [] as Notification[],
      successNotifications: [] as Notification[],
      continueInitialisation: false,
    };
  },

  actions: {
    deleteNotification(id: number): void {
      this.errorNotifications = this.errorNotifications.filter(
        (item: Notification) => item.timeAdded !== id,
      );
    },

    deleteSuccessNotification(id: number): void {
      this.successNotifications = this.successNotifications.filter(
        (item: Notification) => item.timeAdded !== id,
      );
    },

    resetNotifications() {
      // Clear the store state
      this.$reset();
    },

    // Show error notification with axios error object
    showError(errorObject: unknown): void {
      // Show error using the x-road specific data in an axios error object
      // Don't show errors when the errorcode is 401 which is usually because of session expiring
      if (axios.isAxiosError(errorObject)) {
        if (errorObject?.response?.status !== 401) {
          const notification = createEmptyNotification(-1);

          // Store error object as a string that can be shown to the user
          notification.errorObjectAsString = errorObject.toString();

          // Data shown in nofitication component
          notification.errorCode = errorObject?.response?.data?.error?.code;
          notification.metaData = errorObject?.response?.data?.error?.metadata;
          notification.responseData = errorObject?.response?.config?.data;
          notification.errorId =
            errorObject?.response?.headers['x-road-ui-correlation-id'];

          // Data needed to compare with other notificatios for handling duplicates
          notification.url = errorObject?.response?.config?.url;
          notification.status = errorObject?.response?.data?.status;

          this.errorNotifications = addErrorNotification(
            this.errorNotifications,
            notification,
          );
        }
      } else if (errorObject instanceof Error) {
        this.showErrorMessage(errorObject.message);
      } else {
        this.showErrorMessage('Unexpected error');
      }
    },

    showErrorMessage(messageText: string | TranslateResult): void {
      // Show error snackbar with text string
      const notification = createEmptyNotification(-1);
      notification.errorMessage = messageText as string;
      this.errorNotifications = addErrorNotification(
        this.errorNotifications,
        notification,
      );
    },

    showSuccess(messageText: string | TranslateResult): void {
      // Show success snackbar with text string
      const notification = createEmptyNotification(3000);
      notification.successMessage = messageText as string;
      this.successNotifications.push(notification);
    },

    // Add error with an action
    setErrorAction(val: ActionError): void {
      const notification = createEmptyNotification(-1);
      notification.action = val.action;
      notification.errorMessage = val.errorMessage;
      this.errorNotifications = addErrorNotification(
        this.errorNotifications,
        notification,
      );
    },

    setContinueInit(val: boolean): void {
      this.continueInitialisation = val;
    },
  },
});
