import axios from 'axios';
import router from '../../router';

export const state = {
  cities: [],
  loading: false,
};

export const getters = {
  cities: state => state.cities,
  loading: state => state.loading,
};

export const mutations = {
  storeCities(state, cities) {
    state.cities = cities;
  },
  setLoading(state, loading) {
    state.loading = loading;
  }
};

export const actions = {
  fetchData({ commit, rootGetters }) {
    if (!rootGetters.isAuthenticated) {
      console.log('Not authenticated! Cant call get cities!')
      return;
    }

    commit('setLoading', true);

    return axios.get('/cities')
      .then((res) => {
        console.log(res);
        const cities = res.data;
        commit('storeCities', cities);
      })
      .catch((error) => {
        console.log(error);
        if (error.response.status === 401) {
          commit('clearAuthData');
          router.replace('/');
        }
        throw error;
      })
      .finally(() => {
        commit('setLoading', false);
      });
  },
};

export default {
  state,
  getters,
  actions,
  mutations
};
