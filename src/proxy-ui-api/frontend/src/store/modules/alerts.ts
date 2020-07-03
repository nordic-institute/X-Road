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

export const actions: ActionTree<AlertsState, RootState> = {
  checkAlertStatus({ commit, dispatch }): void {
    api
      .get('/notifications/alerts')
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
