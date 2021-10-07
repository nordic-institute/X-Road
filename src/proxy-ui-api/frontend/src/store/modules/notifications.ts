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
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { Notification } from '@/ui-types';

export interface NotificationsState {
  errorNotifications: Notification[];
  successNotifications: Notification[];
  staticNotification: string[]; // only one notification but may have multiple rows – hence the array
}

const getDefaultState = () => {
  return {
    errorNotifications: [],
    successNotifications: [],
    staticNotification: [],
  };
};

// Finds if an array of notifications contains a similar notification.
function containsNotification(
  errorNotifications: Notification[],
  notification: Notification,
): number {
  if (!notification || !errorNotifications || errorNotifications.length === 0) {
    return -1;
  }
  const result = errorNotifications.findIndex((e: Notification) => {
    if (
      notification?.errorObject?.response?.config?.data !==
      e?.errorObject?.response?.config?.data
    ) {
      return false;
    }

    if (
      notification?.errorObject?.response?.config?.url !==
      e?.errorObject?.response?.config?.url
    ) {
      return false;
    }

    if (
      notification?.errorObject?.response?.data?.status !==
      e?.errorObject?.response?.data?.status
    ) {
      return false;
    }

    if (
      notification?.errorObject?.response?.data?.error?.code !==
      e?.errorObject?.response?.data?.error?.code
    ) {
      return false;
    }

    if (notification?.errorMessageRaw !== e?.errorMessageRaw) {
      return false;
    }

    if (notification?.errorMessageCode !== e?.errorMessageCode) {
      return false;
    }

    return true;
  });

  return result;
}

// Add error notification to the store
function addErrorNotification(
  state: NotificationsState,
  notification: Notification,
): void {
  // Check for duplicate
  const index = containsNotification(state.errorNotifications, notification);

  if (index > -1) {
    // If there is a duplicate, remove it and increase the count
    notification.count = state.errorNotifications[index].count + 1;
    state.errorNotifications.splice(index, 1);
  }

  state.errorNotifications.push(notification);
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

// Initial state. The state can be reseted with this.
const notificationsState: NotificationsState = getDefaultState();

export const getters: GetterTree<NotificationsState, RootState> = {
  successNotifications(state: NotificationsState): Notification[] {
    return state.successNotifications;
  },
  errorNotifications(state: NotificationsState): Notification[] {
    return state.errorNotifications;
  },
  staticNotification(state: NotificationsState) {
    return state.staticNotification;
  },
};

export const mutations: MutationTree<NotificationsState> = {
  resetNotificationsState(state): void {
    Object.assign(state, getDefaultState());
  },
  setSuccessCode(state: NotificationsState, val: string): void {
    const notification = createEmptyNotification(3000);
    notification.successMessageCode = val;
    state.successNotifications.push(notification);
  },
  setSuccessRaw(state: NotificationsState, val: string): void {
    const notification = createEmptyNotification(3000);
    notification.successMessageRaw = val;
    state.successNotifications.push(notification);
  },
  setErrorMessageCode(state: NotificationsState, val: string): void {
    const notification = createEmptyNotification(-1);
    notification.errorMessageCode = val;
    addErrorNotification(state, notification);
  },
  setErrorMessageRaw(state: NotificationsState, val: string): void {
    const notification = createEmptyNotification(-1);
    notification.errorMessageRaw = val;
    addErrorNotification(state, notification);
  },
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  setErrorObject(state: NotificationsState, errorObject: any): void {
    const notification = createEmptyNotification(-1);
    notification.errorObject = errorObject;
    addErrorNotification(state, notification);
  },

  deleteSuccessNotification(state: NotificationsState, id: number): void {
    state.successNotifications = state.successNotifications.filter(
      (item: Notification) => item.timeAdded !== id,
    );
  },

  deleteNotification(state: NotificationsState, id: number): void {
    state.errorNotifications = state.errorNotifications.filter(
      (item: Notification) => item.timeAdded !== id,
    );
  },

  clearErrorNotifications(state: NotificationsState): void {
    state.errorNotifications = [];
  },
  setStaticNotification(
    state: NotificationsState,
    notificationMessages: string[],
  ): void {
    state.staticNotification = notificationMessages;
  },
  clearStaticNotification(state: NotificationsState): void {
    state.staticNotification = [];
  },
};

export const actions: ActionTree<NotificationsState, RootState> = {
  resetNotificationsState({ commit }): void {
    // Clear the store state
    commit('resetNotificationsState');
  },
  showSuccess({ commit }, localisationCode: string): void {
    // Show success snackbar with a localisation code for text
    commit('setSuccessCode', localisationCode);
  },
  showSuccessRaw({ commit }, messageText: string): void {
    // Show success snackbar without localisation
    commit('setSuccessRaw', messageText);
  },
  showErrorMessageCode({ commit }, localisationCode: string): void {
    // Show error snackbar with a localisation code for text
    commit('setErrorMessageCode', localisationCode);
  },
  showErrorMessageRaw({ commit }, messageText: string): void {
    // Show error snackbar without localisation
    commit('setErrorMessageRaw', messageText);
  },
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  showError({ commit }, errorObject: any): void {
    // Show error using the error object
    // Don't show errors when the errorcode is 401 which is usually because of session expiring
    if (errorObject?.response?.status !== 401) {
      commit('setErrorObject', errorObject);
    }
  },
  showStaticNotification({ commit }, notifications: string[]) {
    commit('setStaticNotification', notifications);
  },
  clearStaticNotification({ commit }) {
    commit('clearStaticNotification');
  },
};

export const module: Module<NotificationsState, RootState> = {
  namespaced: false,
  state: notificationsState,
  getters,
  actions,
  mutations,
};
