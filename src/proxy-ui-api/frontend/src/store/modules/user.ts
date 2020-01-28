import axiosAuth from '../../axios-auth';
import axios from 'axios';
import _ from 'lodash';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { mainTabs } from '@/global';

export interface UserState {
  authenticated: boolean;
  permissions: string[];
  username: string;
}

export const userState: UserState = {
  authenticated: false,
  permissions: [],
  username: '',
};

export const userGetters: GetterTree<UserState, RootState> = {
  isAuthenticated(state) {
    return state.authenticated;
  },
  allowedTabs(state) {
    // Returns all the tabs that the user has permission for
    const ret = _.filter(mainTabs, (tab: any) => {
      if (!tab || !tab.permission) {
        // Tab does not have set permission (permission is not needed)
        return true;
      }

      if (state.permissions.includes(tab.permission)) {
        return true;
      }
      return false;
    });

    return ret;
  },
  firstAllowedTab(state, getters) {
    return getters.allowedTabs[0];
  },
  permissions(state) {
    return state.permissions;
  },
  hasPermission: (state) => (perm: string) => {
    return state.permissions.includes(perm);
  },
  getAllowedTabs: (state, getters) => (tabs: any[]) => {
    // returns filtered array of objects based on the 'permission' attribute
    const filteredTabs = tabs.filter((tab) => {
      if (!tab.permission) {
        return true;
      }
      if (getters.hasPermission(tab.permission)) {
        return true;
      }
      return false;
    });

    return filteredTabs;
  },
  username(state) {
    return state.username;
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
  },
  setPermissions: (state, permissions: string[]) => {
    state.permissions = permissions;
  },
  setUsername: (state, username: string) => {
    state.username = username;
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

    commit('setLoading', true);

    return axios.get('/user')
      .then((res) => {
        console.log(res);
        commit('setUsername', res.data.username);
        commit('setPermissions', res.data.permissions);

      })
      .catch((error) => {
        console.log(error);
        throw error;
      });
  },

  logout({ commit, dispatch }) {
    // Clear auth data
    commit('clearAuthData');

    // Call backend for logout
    axiosAuth.post('/logout')
      .catch((error) => {
        console.error(error);
      }).finally(() => {
        // Reload the browser page to clean up the memory
        location.reload(true);
      });


  },
  clearAuth({ commit }) {
    commit('clearAuthData');
  },
  demoLogout({ commit, dispatch }) {
    // This is for logging out on backend without changing the frontend
    // For testing purposes!
    axiosAuth.post('/logout')
      .catch((error) => {
        console.error(error);
      });
  },
};

export const user: Module<UserState, RootState> = {
  namespaced: false,
  state: userState,
  getters: userGetters,
  actions,
  mutations,
};
