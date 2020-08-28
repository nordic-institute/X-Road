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
  successMessageCode: string;
  successMessageRaw: string;
  showSuccessCode: boolean;
  showSuccessRaw: boolean;
  notifications: Notification[];
}

const getDefaultState = () => {
  return {
    successMessageCode: '',
    successMessageRaw: '',
    showSuccessCode: false,
    showSuccessRaw: false,
    notifications: [],
  };
};

// Initial state. The state can be reseted with this.
const notificationsState: NotificationsState = getDefaultState();

export const getters: GetterTree<NotificationsState, RootState> = {
  showSuccessCode(state: NotificationsState): boolean {
    return state.showSuccessCode;
  },
  showSuccessRaw(state: NotificationsState): boolean {
    return state.showSuccessRaw;
  },
  successMessageCode(state: NotificationsState): string {
    return state.successMessageCode;
  },
  successMessageRaw(state: NotificationsState): string {
    return state.successMessageRaw;
  },
  notifications(state: NotificationsState): Notification[] {
    return state.notifications;
  },
};

export const mutations: MutationTree<NotificationsState> = {
  resetNotificationsState(state): void {
    Object.assign(state, getDefaultState());
  },
  setSuccessCode(state: NotificationsState, val: string): void {
    state.successMessageCode = val;
    state.showSuccessCode = true;
  },
  setSuccessRaw(state: NotificationsState, val: string): void {
    state.successMessageRaw = val;
    state.showSuccessRaw = true;
  },
  setErrorMessageCode(state: NotificationsState, val: string): void {
    const temp: Notification = {
      timeout: 0,
      errorMessageCode: val,
      timeAdded: Date.now(),
      show: true,
    };

    state.notifications.push(temp);
  },
  setErrorMessageRaw(state: NotificationsState, val: string): void {
    const temp: Notification = {
      timeout: 2000,
      errorMessageRaw: val,
      timeAdded: Date.now(),
      show: true,
    };

    state.notifications.push(temp);
  },
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  setErrorObject(state: NotificationsState, errorObject: any): void {
    const temp: Notification = {
      timeout: 0,
      errorObject: errorObject,
      timeAdded: Date.now(), // Simple id solution
      show: true,
    };

    state.notifications.push(temp);
  },
  setSuccessRawVisible(state: NotificationsState, val: boolean): void {
    state.showSuccessRaw = val;
  },
  setSuccessCodeVisible(state: NotificationsState, val: boolean): void {
    state.showSuccessCode = val;
  },
  deleteNotification(state: NotificationsState, id: number): void {
    state.notifications = state.notifications.filter(
      (item: Notification) => item.timeAdded !== id,
    );
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
    commit('setErrorObject', errorObject);
  },
};

export const module: Module<NotificationsState, RootState> = {
  namespaced: false,
  state: notificationsState,
  getters,
  actions,
  mutations,
};
