import axiosAuth from '../../axios-auth';
import axios from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import {
  InitializationStatus,
  SecurityServer,
  TokenInitStatus,
  User,
  Version,
} from '@/openapi-types';
import { Tab } from '@/ui-types';
import { mainTabs, TokenInitStatusEnum } from '@/global';
import i18n from '@/i18n';

export interface UserState {
  authenticated: boolean;
  permissions: string[];
  username: string;
  currentSecurityServer: SecurityServer | {};
  securityServerVersion: Version | {};
  initializationStatus: InitializationStatus | undefined;
}

export const getDefaultState = (): UserState => {
  return {
    authenticated: false,
    permissions: [],
    username: '',
    currentSecurityServer: {},
    securityServerVersion: {},
    initializationStatus: undefined,
  };
};

// Initial state. The state can be reseted with this.
const moduleState = getDefaultState();

export const userGetters: GetterTree<UserState, RootState> = {
  isAuthenticated(state) {
    return state.authenticated;
  },
  firstAllowedTab(state, getters) {
    return getters.getAllowedTabs(mainTabs)[0];
  },
  permissions(state) {
    return state.permissions;
  },
  hasPermission: (state) => (perm: string) => {
    return state.permissions.includes(perm);
  },
  getAllowedTabs: (state, getters) => (tabs: Tab[]) => {
    // returns filtered array of objects based on the 'permission' attribute
    const filteredTabs = tabs.filter((tab: Tab) => {
      if (!tab.permissions || tab.permissions.length === 0) {
        // No permission needed for this tab
        return true;
      }
      if (
        tab.permissions.some((permission) => getters.hasPermission(permission))
      ) {
        // Return true if the user has at least one of the tabs permissions
        return true;
      }
      return false;
    });

    return filteredTabs;
  },
  username(state) {
    return state.username;
  },
  currentSecurityServer(state) {
    return state.currentSecurityServer;
  },
  securityServerVersion(state) {
    return state.securityServerVersion;
  },
  isAnchorImported(state): boolean {
    return state.initializationStatus?.is_anchor_imported ?? false;
  },

  isServerOwnerInitialized(state): boolean {
    return state.initializationStatus?.is_server_owner_initialized ?? false;
  },

  isServerCodeInitialized(state): boolean {
    return state.initializationStatus?.is_server_code_initialized ?? false;
  },

  softwareTokenInitializationStatus(state): TokenInitStatus | undefined {
    return state.initializationStatus?.software_token_init_status;
  },

  hasInitState: (state) => {
    return typeof state.initializationStatus !== 'undefined';
  },

  needsInitialization: (state) => {
    return !(
      state.initializationStatus?.is_anchor_imported &&
      state.initializationStatus.is_server_code_initialized &&
      state.initializationStatus.is_server_owner_initialized &&
      (state.initializationStatus.software_token_init_status ===
        TokenInitStatusEnum.INITIALIZED ||
        state.initializationStatus.software_token_init_status ===
          TokenInitStatusEnum.UNKNOWN)
    );
  },
};

export const mutations: MutationTree<UserState> = {
  authUser(state) {
    state.authenticated = true;
  },
  clearAuthData(state) {
    Object.assign(state, getDefaultState());
  },
  setPermissions: (state, permissions: string[]) => {
    state.permissions = permissions;
  },
  setUsername: (state, username: string) => {
    state.username = username;
  },
  setCurrentSecurityServer: (state, securityServer: SecurityServer) => {
    state.currentSecurityServer = securityServer;
  },
  setSecurityServerVersion: (state, version: Version) => {
    state.securityServerVersion = version;
  },
  storeInitStatus(state, status: InitializationStatus) {
    state.initializationStatus = status;
  },
};

export const actions: ActionTree<UserState, RootState> = {
  login({ commit }, authData) {
    const data = `username=${encodeURIComponent(
      authData.username,
    )}&password=${encodeURIComponent(authData.password)}`;

    return axiosAuth({
      url: '/login',
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      data,
    })
      .then(() => {
        commit('authUser');
      })
      .catch((error) => {
        throw error;
      });
  },

  async fetchUserData({ commit }) {
    return axios
      .get<User>('/user')
      .then((res) => {
        commit('setUsername', res.data.username);
        commit('setPermissions', res.data.permissions);
      })
      .catch((error) => {
        throw error;
      });
  },

  async fetchCurrentSecurityServer({ commit }) {
    return axios
      .get<SecurityServer[]>('/security-servers?current_server=true')
      .then((resp) => {
        if (resp.data?.length !== 1) {
          throw new Error(
            i18n.t('stores.user.currentSecurityServerNotFound') as string,
          );
        }
        commit('setCurrentSecurityServer', resp.data[0]);
      })
      .catch((error) => {
        throw error;
      });
  },

  async fetchSecurityServerVersion({ commit }) {
    return axios
      .get<Version>('/system/version')
      .then((resp) => commit('setSecurityServerVersion', resp.data))
      .catch((error) => {
        throw error;
      });
  },

  logout({ commit }, reload = true) {
    // Clear auth data
    commit('clearAuthData');

    // Call backend for logout
    axiosAuth
      .post('/logout')
      .catch(() => {
        // Nothing to do
      })
      .finally(() => {
        if (reload) {
          // Reload the browser page to clean up the memory
          location.reload(true);
        }
      });
  },

  fetchInitializationStatus({ commit }) {
    return axios
      .get('/initialization/status')
      .then((resp) => {
        commit('storeInitStatus', resp.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  setInitializationStatus({ commit }) {
    // Sets the initialization state to done
    const initStatus: InitializationStatus = {
      is_anchor_imported: true,
      is_server_code_initialized: true,
      is_server_owner_initialized: true,
      software_token_init_status: TokenInitStatusEnum.INITIALIZED,
    };

    commit('storeInitStatus', initStatus);
  },

  clearAuth({ commit }) {
    commit('clearAuthData');
  },
};

export const user: Module<UserState, RootState> = {
  namespaced: false,
  state: moduleState,
  getters: userGetters,
  actions,
  mutations,
};
