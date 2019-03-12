import axiosAuth from '../../axios-auth';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';

export interface UserState {
  authenticated: boolean;
}

export const authState: UserState = {
  authenticated: false,
};

export const getters: GetterTree<UserState, RootState> = {
  isAuthenticated(state) {
    return state.authenticated;
  },
};

export const mutations: MutationTree<UserState> = {
  authUser(state) {
    state.authenticated = true;
  },
  clearAuthData(state) {
    // Use this to log out user
    state.authenticated = false;
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
  logout({ commit, dispatch }) {
    commit('clearAuthData');
    axiosAuth.post('/logout')
      .catch((error) => {
        console.error(error);
      });
  },
  clearAuth({ commit }) {
    commit('clearAuthData');
  },
  demoLogout({ commit, dispatch }) {
    axiosAuth.post('/logout')
      .catch((error) => {
        console.error(error);
      });
  },
};

export const auth: Module<UserState, RootState> = {
  namespaced: false,
  state: authState,
  getters,
  actions,
  mutations,
};
