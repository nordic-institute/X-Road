
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { Client } from '@/types';
import * as api from '@/util/api';

// TODO: this should be in types ??
interface InitializationStatus {
  is_anchor_imported: boolean;
  is_server_code_initialized: boolean;
  is_server_owner_initialized: boolean;
  is_software_token_initialized: boolean;
}


export interface InitServerState {
  memberClass: string | undefined;
  memberCode: string | undefined;
  securityServerCode: string | undefined;
  initializationStatus: InitializationStatus | undefined;
}

const getDefaultState = () => {
  return {
    memberClass: undefined,
    memberCode: undefined,
    securityServerCode: undefined,
    initializationStatus: undefined,
  };
};

// Initial state. The state can be reseted with this.
const moduleState = getDefaultState();

export const getters: GetterTree<InitServerState, RootState> = {

  initServerMemberClass(state): string | undefined {
    return state.memberClass;
  },

  initServerMemberCode(state): string | undefined {
    return state.memberCode;
  },

  initServerSSCode(state): string | undefined {
    return state.securityServerCode;
  },

  isAnchorImported(state): boolean {
    return state.initializationStatus?.is_anchor_imported || false;
  },

  isServerOwnerInitialized(state): boolean {
    return state.initializationStatus?.is_server_owner_initialized || false;
  },

  isServerCodeInitialized(state): boolean {
    return state.initializationStatus?.is_server_code_initialized || false;
  },

  isSoftwareTokenInitialized(state): boolean {
    return state.initializationStatus?.is_software_token_initialized || false;
  },

  needsInitialisation: (state) => {
    if (state.initializationStatus?.is_anchor_imported && state.initializationStatus.is_server_code_initialized &&
      state.initializationStatus.is_server_owner_initialized && state.initializationStatus.is_software_token_initialized
    ) {
      return false;
    }
    return true;
  }
};

export const mutations: MutationTree<InitServerState> = {
  resetInitServerState(state) {
    Object.assign(state, getDefaultState());
  },
  storeInitServerMemberCode(state, memberCode: string | undefined) {
    state.memberCode = memberCode;
  },
  storeInitServerMemberClass(state, memberClass: string | undefined) {
    state.memberClass = memberClass;
  },
  storeInitServerSSCode(state, code: string | undefined) {
    state.securityServerCode = code;
  },
  storeInitStatus(state, status: InitializationStatus) {
    state.initializationStatus = status;
  },
};

export const actions: ActionTree<InitServerState, any> = {
  resetInitServerState({ commit }) {
    commit('resetInitServerState');
  },

  fetchInitializationStatus({ commit, rootGetters, dispatch }) {
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

export const module: Module<InitServerState, RootState> = {
  namespaced: false,
  state: moduleState,
  getters,
  actions,
  mutations,
};
