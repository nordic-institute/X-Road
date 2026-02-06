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

import { computed } from 'vue';

import { RouteLocationRaw, Router } from 'vue-router';

import { i18n } from '../plugins/i18n';
import { useNotificationsContainer } from '../stores';
import { Notification, NotificationId } from '../types';
import { NotificationManager } from '../types/notifications';

import { addError, addTranslatedSuccessMessage, isErrorNotification, remove, addTranslatedErrorMessage } from './notifications-helper';

const routingContext = {
  router: undefined as Router | undefined,
  navigationRules: undefined as Record<number, RouteLocationRaw> | undefined,
};

export function setupAddErrorNavigation(router: Router, navigationRules: Record<number, RouteLocationRaw>) {
  routingContext.router = router;
  routingContext.navigationRules = navigationRules;
}

export function useNotifications(): NotificationManager {
  const container = useNotificationsContainer();
  const errors = computed(() => container.notifications.filter(isErrorNotification));
  return {
    clearFlag(name: string): void {
      container.flags.delete(name);
    },
    getFlag(name: string): string | boolean | undefined {
      return container.flags.get(name);
    },
    hasFlag(name: string): boolean {
      return container.flags.has(name);
    },
    setFlag(name: string, value: string | boolean): void {
      container.flags.set(name, value);
    },
    errors,
    hasContextErrors: computed(() => container.notifications.findIndex(isErrorNotification) > -1),
    successes: computed(() => container.notifications.filter((item) => item.type === 'success')),
    remove(id: NotificationId) {
      remove(id, container.notifications);
    },
    clear() {
      const preserved: Notification[] = [];
      container.notifications
        .filter((item) => item.preserve)
        .forEach((item) => {
          item.preserve = false;
          preserved.push(item);
        });
      const size = container.notifications.length;
      container.notifications.splice(0, size, ...preserved);
    },
    hasErrors(): boolean {
      return errors.value && errors.value.length > 0;
    },
    addErrorMessage(messageKey: string, messageParams: Record<string, unknown> = {}): void {
      addTranslatedErrorMessage(container.notifications, i18n.global.t(messageKey, messageParams));
    },
    addTranslatedErrorMessage(message: string): void {
      addTranslatedErrorMessage(container.notifications, message);
    },
    addError(errorObject: unknown, config = {}) {
      addError(container.notifications, errorObject, config.navigate ? routingContext : undefined, config);
    },
    addTranslatedSuccessMessage(message: string, preserve: boolean = false): void {
      addTranslatedSuccessMessage(container.notifications, message, preserve);
    },
    addSuccessMessage(messageKey: string, messageParams: Record<string, unknown> = {}, preserve = false) {
      addTranslatedSuccessMessage(container.notifications, i18n.global.t(messageKey, messageParams), preserve);
    },
  };
}
