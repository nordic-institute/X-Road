
import Vue from 'vue';
import Vuex, { StoreOptions } from 'vuex';
import VuexPersistence from 'vuex-persist';
import { RootState } from './types';
import { mockDataModule } from './modules/mockData';
import { clientsModule } from './modules/clients';
import { clientModule } from './modules/client';
import { keysModule } from './modules/keys';
import { user } from './modules/user';

Vue.use(Vuex);
const vuexLocal = new VuexPersistence({
  storage: window.localStorage,
  modules: ['user'],
});

const store: StoreOptions<RootState> = {
  state: {
    version: '1.0.0', // a simple property
  },
  modules: {
    user,
    mockDataModule,
    clientsModule,
    clientModule,
    keysModule,
  },
  // @ts-ignore
  plugins: [vuexLocal.plugin],
};

export default new Vuex.Store<RootState>(store);
