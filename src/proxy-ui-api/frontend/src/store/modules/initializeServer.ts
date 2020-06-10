import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import * as api from '@/util/api';

interface InitializationStatus {
  is_anchor_imported: boolean;
  is_server_code_initialized: boolean;
  is_server_owner_initialized: boolean;
  is_software_token_initialized: boolean;
}

export interface State {
  memberClass: string | undefined;
  memberCode: string | undefined;
  securityServerCode: string | undefined;
  initializationStatus: InitializationStatus | undefined;
}

export const getDefaultState = (): State => {
  return {
    memberClass: undefined,
    memberCode: undefined,
    securityServerCode: undefined,
    initializationStatus: undefined,
  };
};

// Initial state. The state can be reseted with this.
const moduleState = getDefaultState();

export const getters: GetterTree<State, RootState> = {
  initServerMemberClass(state: State): string | undefined {
    return state.memberClass;
  },

  initServerMemberCode(state: State): string | undefined {
    return state.memberCode;
  },

  initServerSSCode(state: State): string | undefined {
    return state.securityServerCode;
  },

  isAnchorImported(state: State): boolean {
    return state.initializationStatus?.is_anchor_imported ?? false;
  },

  isServerOwnerInitialized(state: State): boolean {
    return state.initializationStatus?.is_server_owner_initialized ?? false;
  },

  isServerCodeInitialized(state: State): boolean {
    return state.initializationStatus?.is_server_code_initialized ?? false;
  },

  isSoftwareTokenInitialized(state: State): boolean {
    return state.initializationStatus?.is_software_token_initialized ?? false;
  },

  needsInitialization: (state) => {
    return !(
      state.initializationStatus?.is_anchor_imported &&
      state.initializationStatus.is_server_code_initialized &&
      state.initializationStatus.is_server_owner_initialized &&
      state.initializationStatus.is_software_token_initialized
    );
  },
};

export const mutations: MutationTree<State> = {
  resetInitServerState(state: State) {
    Object.assign(state, getDefaultState());
  },
  storeInitServerMemberCode(state: State, memberCode: string | undefined) {
    state.memberCode = memberCode;
  },
  storeInitServerMemberClass(state: State, memberClass: string | undefined) {
    state.memberClass = memberClass;
  },
  storeInitServerSSCode(state: State, code: string | undefined) {
    state.securityServerCode = code;
  },
  storeInitStatus(state: State, status: InitializationStatus) {
    state.initializationStatus = status;
  },
};

export const actions: ActionTree<State, any> = {
  resetInitServerState({ commit }) {
    commit('resetInitServerState');
  },

  fetchInitializationStatus({ commit }) {
    return api
      .get('/initialization/status')
      .then((resp) => {
        commit('storeInitStatus', resp.data);
      })
      .catch((error) => {
        throw error;
      });
  },
};

export const module: Module<State, RootState> = {
  namespaced: false,
  state: moduleState,
  getters,
  actions,
  mutations,
};
