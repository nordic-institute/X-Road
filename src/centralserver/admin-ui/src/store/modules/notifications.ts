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
import { RootState, StoreTypes } from '@/global';
import { ActionError, Notification } from '@/ui-types';

export interface State {
  errorNotifications: Notification[];
  successNotifications: Notification[];
  continueInitialisation: boolean;
}

const getDefaultState = () => {
  return {
    errorNotifications: [],
    successNotifications: [],
    continueInitialisation: false,
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
  return errorNotifications.findIndex((e: Notification) => {
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

    return notification?.errorMessageCode === e?.errorMessageCode;
  });
}

// Add error notification to the store
function addErrorNotification(state: State, notification: Notification): void {
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
const notificationsState: State = getDefaultState();

export const getters: GetterTree<State, RootState> = {
  [StoreTypes.getters.SUCCESS_NOTIFICATIONS](state: State): Notification[] {
    return state.successNotifications;
  },
  [StoreTypes.getters.ERROR_NOTIFICATIONS](state: State): Notification[] {
    return state.errorNotifications;
  },
  [StoreTypes.getters.CONTINUE_INIT](state: State): boolean {
    return state.continueInitialisation;
  },
};

export const mutations: MutationTree<State> = {
  [StoreTypes.mutations.SET_NOTIFICATIONS_DEFAULT_STATE](state): void {
    Object.assign(state, getDefaultState());
  },
  [StoreTypes.mutations.SET_SUCCESS_CODE](state: State, val: string): void {
    const notification = createEmptyNotification(3000);
    notification.successMessageCode = val;
    state.successNotifications.push(notification);
  },
  [StoreTypes.mutations.SET_SUCCESS_RAW](state: State, val: string): void {
    const notification = createEmptyNotification(3000);
    notification.successMessageRaw = val;
    state.successNotifications.push(notification);
  },
  [StoreTypes.mutations.SET_ERROR_MESSAGE_CODE](
    state: State,
    val: string,
  ): void {
    const notification = createEmptyNotification(-1);
    notification.errorMessageCode = val;
    addErrorNotification(state, notification);
  },
  [StoreTypes.mutations.SET_ERROR_MESSAGE_RAW](
    state: State,
    val: string,
  ): void {
    const notification = createEmptyNotification(-1);
    notification.errorMessageRaw = val;
    addErrorNotification(state, notification);
  },

  [StoreTypes.mutations.SET_ERROR_OBJECT](
    state: State,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    errorObject: any,
  ): void {
    const notification = createEmptyNotification(-1);
    notification.errorObject = errorObject;
    addErrorNotification(state, notification);
  },

  [StoreTypes.mutations.DELETE_SUCCESS_NOTIFICATION](
    state: State,
    id: number,
  ): void {
    state.successNotifications = state.successNotifications.filter(
      (item: Notification) => item.timeAdded !== id,
    );
  },

  [StoreTypes.mutations.DELETE_NOTIFICATION](state: State, id: number): void {
    state.errorNotifications = state.errorNotifications.filter(
      (item: Notification) => item.timeAdded !== id,
    );
  },

  [StoreTypes.mutations.SET_ERROR_ACTION](
    state: State,
    val: ActionError,
  ): void {
    const notification = createEmptyNotification(-1);
    notification.action = val.action;
    notification.errorMessageCode = val.errorMessageCode;
    addErrorNotification(state, notification);
  },

  [StoreTypes.mutations.SET_CONTINUE_INIT](state: State, val: boolean): void {
    state.continueInitialisation = val;
  },
};

export const actions: ActionTree<State, RootState> = {
  [StoreTypes.actions.RESET_NOTIFICATIONS_STATE]({ commit }): void {
    // Clear the store state
    commit(StoreTypes.mutations.SET_NOTIFICATIONS_DEFAULT_STATE);
  },
  [StoreTypes.actions.SHOW_SUCCESS](
    { commit },
    localisationCode: string,
  ): void {
    // Show success snackbar with a localisation code for text
    commit(StoreTypes.mutations.SET_SUCCESS_CODE, localisationCode);
  },
  [StoreTypes.actions.SHOW_SUCCESS_RAW]({ commit }, messageText: string): void {
    // Show success snackbar without localisation
    commit(StoreTypes.mutations.SET_SUCCESS_RAW, messageText);
  },
  [StoreTypes.actions.SHOW_ERROR_MESSAGE_CODE](
    { commit },
    localisationCode: string,
  ): void {
    // Show error snackbar with a localisation code for text
    commit(StoreTypes.mutations.SET_ERROR_MESSAGE_CODE, localisationCode);
  },
  [StoreTypes.actions.SHOW_ERROR_MESSAGE_RAW](
    { commit },
    messageText: string,
  ): void {
    // Show error snackbar without localisation
    commit(StoreTypes.mutations.SET_ERROR_MESSAGE_RAW, messageText);
  },

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  [StoreTypes.actions.SHOW_ERROR]({ commit }, errorObject: any): void {
    // Show error using the error object
    // Don't show errors when the errorcode is 401 which is usually because of session expiring
    if (errorObject?.response?.status !== 401) {
      commit(StoreTypes.mutations.SET_ERROR_OBJECT, errorObject);
    }
  },
};

export const module: Module<State, RootState> = {
  namespaced: false,
  state: notificationsState,
  getters,
  actions,
  mutations,
};
