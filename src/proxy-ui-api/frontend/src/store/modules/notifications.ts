import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';


export interface NotificationsState {
  successMessageCode: string;
  successMessageRaw: string;
  showSuccessCode: boolean;
  showSuccessRaw: boolean;
  errorMessageCode: string;
  errorMessageRaw: string;
  errorObject: any;
  showErrorObject: boolean;
  showErrorRaw: boolean;
  showErrorCode: boolean;
}

const getDefaultState = () => {
  return {
    successMessageCode: '',
    successMessageRaw: '',
    showSuccessCode: false,
    showSuccessRaw: false,
    errorMessageCode: '',
    errorMessageRaw: '',
    showErrorObject: false,
    showErrorCode: false,
    showErrorRaw: false,
    errorObject: undefined,
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
  showErrorObject(state: NotificationsState): boolean {
    return state.showErrorObject;
  },
  showErrorRaw(state: NotificationsState): boolean {
    return state.showErrorRaw;
  },
  showErrorCode(state: NotificationsState): boolean {
    return state.showErrorCode;
  },
  errorMessageRaw(state: NotificationsState): string {
    return state.errorMessageRaw;
  },
  errorMessageCode(state: NotificationsState): string {
    return state.errorMessageCode;
  },
  errorObject(state: NotificationsState): any {
    return state.errorObject;
  },
};

export const mutations: MutationTree<NotificationsState> = {
  resetNotificationsState(state) {
    Object.assign(state, getDefaultState());
  },
  setSuccessCode(state: NotificationsState, val: string) {
    state.successMessageCode = val;
    state.showSuccessCode = true;
  },
  setSuccessRaw(state: NotificationsState, val: string) {
    state.successMessageRaw = val;
    state.showSuccessRaw = true;
  },
  setErrorMessageCode(state: NotificationsState, val: string) {
    state.errorMessageCode = val;
    state.showErrorCode = true;
  },
  setErrorMessageRaw(state: NotificationsState, val: string) {
    state.errorMessageRaw = val;
    state.showErrorRaw = true;
  },
  setErrorObject(state: NotificationsState, errorObject: any) {
    state.errorObject = errorObject;
    state.showErrorObject = true;
  },
  setSuccessRawVisible(state: NotificationsState, val: boolean) {
    state.showSuccessRaw = val;
  },
  setSuccessCodeVisible(state: NotificationsState, val: boolean) {
    state.showSuccessCode = val;
  },
  setErrorRawVisible(state: NotificationsState, val: boolean) {
    state.showErrorRaw = val;
  },
  setErrorCodeVisible(state: NotificationsState, val: boolean) {
    state.showErrorCode = val;
  },
  setErrorObjectVisible(state: NotificationsState, val: boolean) {
    state.showErrorObject = val;
  },
};

export const actions: ActionTree<NotificationsState, RootState> = {
  resetNotificationsState({ commit }) {
    // Clear the store state
    commit('resetNotificationsState');
  },
  showSuccess({ commit }, localisationCode: string) {
    // Show success snackbar with a localisation code for text
    commit('setSuccessCode', localisationCode);
  },
  showSuccessRaw({ commit }, messageText: string) {
    // Show success snackbar without localisation
    commit('setSuccessRaw', messageText);
  },
  showErrorMessageCode({ commit }, localisationCode: string) {
    // Show error snackbar with a localisation code for text
    commit('setErrorMessageCode', localisationCode);
  },
  showErrorMessageRaw({ commit }, messageText: string) {
    // Show error snackbar without localisation
    commit('setErrorMessageRaw', messageText);
  },
  showError({ commit }, errorObject: any) {
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
