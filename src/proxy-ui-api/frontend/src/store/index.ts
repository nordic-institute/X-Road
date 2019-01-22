
import Vue from 'vue';
import Vuex, { StoreOptions } from 'vuex';
import { RootState } from './types';
import { data } from './modules/data';
import { auth } from './modules/auth';

Vue.use(Vuex);

const store: StoreOptions<RootState> = {
  state: {
    version: '1.0.0', // a simple property
  },
  modules: {
    auth,
    data,
  },
};

export default new Vuex.Store<RootState>(store);
