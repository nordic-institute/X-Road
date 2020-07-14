import axiosAuth from '../../axios-auth';
import axios from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { SecurityServer, Version } from '@/openapi-types';
import { Tab } from '@/ui-types';
import { mainTabs } from '@/global';
import i18n from '@/i18n';

export interface UserState {
  authenticated: boolean;
  permissions: string[];
  username: string;
  currentSecurityServer: SecurityServer | {};
  securityServerVersion: Version | {};
}

export const userState: UserState = {
  authenticated: false,
  permissions: [],
  username: '',
  currentSecurityServer: {},
  securityServerVersion: {},
};

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
};

export const mutations: MutationTree<UserState> = {
  authUser(state) {
    state.authenticated = true;
  },
  clearAuthData(state) {
    // Use this to log out user
    state.authenticated = false;
    // Clear the permissions
    state.permissions = [];
    state.username = '';
    state.currentSecurityServer = {};
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
};

export const actions: ActionTree<UserState, RootState> = {
  login({ commit, dispatch }, authData): Promise<any> {
    const data = `username=${authData.username}&password=${authData.password}`;

    return axiosAuth({
      url: '/login',
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      data,
    })
      .then((res) => {
        commit('authUser');
      })
      .catch((error) => {
        throw error;
      });
  },

  async fetchUserData({ commit, dispatch }) {
    return axios
      .get('/user')
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

  logout({ commit, dispatch }) {
    // Clear auth data
    commit('clearAuthData');

    // Call backend for logout
    axiosAuth
      .post('/logout')
      .catch((error) => {
        // Nothing to do
      })
      .finally(() => {
        // Reload the browser page to clean up the memory
        location.reload(true);
      });
  },
  clearAuth({ commit }) {
    commit('clearAuthData');
  },
};

export const user: Module<UserState, RootState> = {
  namespaced: false,
  state: userState,
  getters: userGetters,
  actions,
  mutations,
};
