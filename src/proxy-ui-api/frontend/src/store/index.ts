import Vue from 'vue';
import Vuex from 'vuex';
import axiosAuth from '../axios-auth';
import router from '../router';
import data from './modules/data';

Vue.use(Vuex);

export default new Vuex.Store({
  state: {
    authenticated: false,
  },
  getters: {
    isAuthenticated(state) {
      return state.authenticated;
    },
  },
  mutations: {
    authUser(state) {
      state.authenticated = true;
    },
    clearAuthData(state) {
      // Use this to log out user
      state.authenticated = false;
    },
  },
  actions: {
    login({ commit, dispatch }, authData) {
      const data = `username=${authData.username}&password=${authData.password}`
      return axiosAuth({
        url: '/login',
        method:'POST',
        headers:{
          'Content-Type':'application/x-www-form-urlencoded'
        },
        data: data
      })
        .then((res) => {
          console.log(res);
          commit('authUser');
          router.replace('/about');
        })
        .catch((error) => {
          console.log(error);
          throw error;
        });
    },
    logout({commit, dispatch}) {
      commit('clearAuthData');
      axiosAuth.post('/logout')
          .then((res) => {
              router.replace('/');
          })
          .catch((error) => {
              console.log("logout failed");
              console.log(error);
              router.replace('/');
          });

      router.replace('/');
    },
  },
  modules: {
    data,
  }
});
