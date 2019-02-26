
import Vue from 'vue';
import Vuex, { StoreOptions } from 'vuex';
import VuexPersistence from 'vuex-persist';
import { RootState } from './types';
import { data } from './modules/data';
import { auth } from './modules/auth';

Vue.use(Vuex);
const vuexLocal = new VuexPersistence({
  storage: window.localStorage,
  modules: ['auth'],
});

const store: StoreOptions<RootState> = {
  state: {
    version: '1.0.0', // a simple property
  },
  modules: {
    auth,
    data,
  },
  plugins: [vuexLocal.plugin],
};

export default new Vuex.Store<RootState>(store);
