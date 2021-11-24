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
import axiosAuth from '../../axios-auth';
import axios from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { mainTabs, RootState, StoreTypes } from '@/global';
import { RouteConfig } from 'vue-router';
import routes from '@/router/routes';
import { Tab } from '@/ui-types';

export interface State {
  authenticated: boolean;
  bannedRoutes: undefined | string[];
  isSessionAlive: boolean | undefined;
  username: string;
  permissions: string[];
}

export const getDefaultState = (): State => {
  return {
    authenticated: false,
    bannedRoutes: undefined,
    isSessionAlive: undefined,
    username: '',
    permissions: [],
  };
};

// Initial state. The state can be reseted with this.
const moduleState = getDefaultState();

export const getters: GetterTree<State, RootState> = {
  [StoreTypes.getters.IS_AUTHENTICATED](state) {
    return state.authenticated;
  },
  [StoreTypes.getters.IS_SESSION_ALIVE](state) {
    return state.isSessionAlive;
  },
  [StoreTypes.getters.USERNAME](state) {
    return state.username;
  },
  [StoreTypes.getters.HAS_PERMISSION]: (state) => (permission: string) => {
    return state.permissions.includes(permission);
  },
  [StoreTypes.getters.HAS_ANY_OF_PERMISSIONS]: (state) => (perm: string[]) => {
    // Return true if the user has at least one of the tabs permissions
    return perm?.some((permission) => state.permissions.includes(permission));
  },
  [StoreTypes.getters.GET_ALLOWED_TABS]: (state, getters) => (tabs: Tab[]) => {
    // console.debug('GET_ALLOWED_TABS - state:', state, ' ,tabs:', tabs);
    // returns filtered array of Tab objects based on the 'permission' attribute
    return tabs?.filter((tab: Tab) => {
      const neededPermissions = tab.permissions;
      return !!(
        neededPermissions &&
        getters[StoreTypes.getters.HAS_ANY_OF_PERMISSIONS](neededPermissions)
      );
    });
  },
  [StoreTypes.getters.FIRST_ALLOWED_TAB](state, getters) {
    return getters[StoreTypes.getters.GET_ALLOWED_TABS](mainTabs)[0];
  },
};

export const mutations: MutationTree<State> = {
  [StoreTypes.mutations.AUTH_USER](state) {
    state.authenticated = true;
  },
  [StoreTypes.mutations.SET_SESSION_ALIVE]: (state, value: boolean) => {
    state.isSessionAlive = value;
  },
  [StoreTypes.mutations.CLEAR_AUTH_DATA](state) {
    Object.assign(state, getDefaultState());
  },
  [StoreTypes.mutations.SET_USERNAME]: (state, username: string) => {
    state.username = username;
  },
  [StoreTypes.mutations.SET_PERMISSIONS]: (state, permissions: string[]) => {
    state.permissions = permissions;

    // Function for checking routes recursively
    function getAllowed(route: RouteConfig): void {
      if (!state.bannedRoutes) return;

      // Check that the route has name and permissions
      if (route.name && route?.meta?.permissions) {
        // Find out routes that the user doesn't have permissions to access
        // console.debug(
        //   'Route:',
        //   route.name,
        //   ' with permission: ',
        //   route?.meta?.permissions,
        // );
        if (
          !route.meta.permissions.some((permission: string) =>
            permissions.includes(permission),
          )
        ) {
          // console.debug('BANNED');
          state.bannedRoutes.push(route.name);
        }
      }

      // Check the child routes recursively
      if (route.children) {
        route.children.forEach((child: RouteConfig) => {
          getAllowed(child);
        });
      }
    }

    // Init banned routes array
    state.bannedRoutes = [];
    // Go through the route permissions
    routes.forEach((route) => {
      getAllowed(route);
    });
  },
};

export const actions: ActionTree<State, RootState> = {
  [StoreTypes.actions.LOGIN]({ commit, dispatch }, authData) {
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
    }).then(() => {
      commit(StoreTypes.mutations.AUTH_USER);
      commit(StoreTypes.mutations.SET_SESSION_ALIVE, true);
      dispatch(StoreTypes.actions.FETCH_SYSTEM_STATUS);
    });
  },

  async [StoreTypes.actions.FETCH_SESSION_STATUS]({ commit }) {
    return axios
      .get('/notifications/session-status')
      .then((res) => {
        commit(
          StoreTypes.mutations.SET_SESSION_ALIVE,
          res?.data?.valid ?? false,
        );
      })
      .catch(() => {
        commit(StoreTypes.mutations.SET_SESSION_ALIVE, false);
      });
  },

  async [StoreTypes.actions.FETCH_USER_DATA]({ commit }) {
    return axios
      .get('/user')
      .then((user) => {
        commit(StoreTypes.mutations.SET_USERNAME, user?.data?.username);
        commit(StoreTypes.mutations.SET_PERMISSIONS, user?.data?.permissions);
      })
      .catch((error) => {
        throw error;
      });
  },

  [StoreTypes.actions.LOGOUT]({ commit }, reload = true) {
    // Clear auth data
    commit(StoreTypes.mutations.CLEAR_AUTH_DATA);

    // Call backend for logout
    axiosAuth
      .post('/logout')
      .catch(() => {
        // Nothing to do
      })
      .finally(() => {
        if (reload) {
          // Reload the browser page to clean up the memory
          location.reload();
        }
      });
  },

  [StoreTypes.actions.CLEAR_AUTH]: ({ commit }) => {
    commit(StoreTypes.mutations.CLEAR_AUTH_DATA);
  },
};

export const module: Module<State, RootState> = {
  namespaced: false,
  state: moduleState,
  getters,
  actions,
  mutations,
};
