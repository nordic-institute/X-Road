import axiosAuth from '../../axios-auth';
import router from '../../router';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';

export interface AuthState {
  authenticated: boolean;
}

export const authState: AuthState = {
  authenticated: false,
};

export const getters: GetterTree<AuthState, RootState> = {
  isAuthenticated(state) {

    if (document.cookie.split(';').filter((item) => item.includes('XSRF-TOKEN=')).length) {
      return true;
    }
    return false;
  },
};

export const mutations: MutationTree<AuthState> = {
  authUser(state) {
    state.authenticated = true;
  },
  clearAuthData(state) {
    // Use this to log out user
    state.authenticated = false;
  },
};

export const actions: ActionTree<AuthState, RootState> = {
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
        router.replace('/');
      })
      .catch((error) => {
        throw error;
      });
  },
  logout({ commit, dispatch }) {
    commit('clearAuthData');
    axiosAuth.post('/logout')
      .catch((error) => {
        console.log('logout failed');
        console.log(error);
      });

    router.replace('/login');
  },
  clearAuth({ commit }) {
    commit('clearAuthData');
  },
  demoLogout({ commit, dispatch }) {
    axiosAuth.post('/logout')
      .catch((error) => {
        console.log('logout failed');
        console.log(error);
      });
  },
};

export const auth: Module<AuthState, RootState> = {
  namespaced: false,
  state: authState,
  getters,
  actions,
  mutations,
};
