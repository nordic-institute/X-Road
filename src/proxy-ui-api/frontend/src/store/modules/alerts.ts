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
import * as api from '@/util/api';

export interface AlertStatus {
  currentTime?: string;
  backupRestoreRunningSince?: string;
  globalConfValid: boolean;
  softTokenPinEntered: boolean;
}

interface AlertsState {
  alertStatus: AlertStatus;
  queried: boolean;
}

// Initial state. The state can be reseted with this.
const initialState: AlertsState = {
  alertStatus: {
    globalConfValid: true,
    softTokenPinEntered: true,
  },
  queried: false,
};

export const getters: GetterTree<AlertsState, RootState> = {
  showGlobalConfAlert(state: AlertsState): boolean {
    return state.queried && !state.alertStatus.globalConfValid;
  },
  showSoftTokenPinEnteredAlert(state: AlertsState): boolean {
    return state.queried && !state.alertStatus.softTokenPinEntered;
  },
  showRestoreInProgress(state: AlertsState): boolean {
    return state.alertStatus.backupRestoreRunningSince !== undefined;
  },
  restoreStartTime(state: AlertsState): string {
    return state.alertStatus.backupRestoreRunningSince || '';
  },
};

export const mutations: MutationTree<AlertsState> = {
  setAlertStatus(state: AlertsState, val: AlertStatus): void {
    state.alertStatus = val;
  },
  setQueried(state: AlertsState, val: boolean): void {
    state.queried = val;
  },
};

type AlertsResponse = {
  global_conf_valid: boolean;
  soft_token_pin_entered: boolean;
  backup_restore_running_since?: string;
  current_time: string;
};

export const actions: ActionTree<AlertsState, RootState> = {
  checkAlertStatus({ commit, dispatch }): void {
    api
      .get<AlertsResponse>('/notifications/alerts')
      .then((resp) => {
        commit('setAlertStatus', {
          globalConfValid: resp.data.global_conf_valid,
          softTokenPinEntered: resp.data.soft_token_pin_entered,
          backupRestoreRunningSince: resp.data.backup_restore_running_since,
          currentTime: resp.data.current_time,
        } as AlertStatus);
        commit('setQueried', true);
      })
      .catch((error) => dispatch('showError', error));
  },
  clearAlerts({ commit }): void {
    commit('setAlertStatus', initialState);
  },
};

export const alertsModule: Module<AlertsState, RootState> = {
  namespaced: false,
  state: initialState,
  getters,
  actions,
  mutations,
};
