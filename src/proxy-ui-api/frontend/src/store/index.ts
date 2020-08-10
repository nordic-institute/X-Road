import Vue from 'vue';
import Vuex, { StoreOptions } from 'vuex';
import VuexPersistence from 'vuex-persist';
import { RootState } from './types';
import { generalModule } from './modules/general';
import { clientsModule } from './modules/clients';
import { clientModule } from './modules/client';
import { tokensModule } from './modules/tokens';
import { servicesModule } from './modules/services';
import { addClientModule } from './modules/addClient';
import { csrModule } from './modules/certificateSignRequest';
import { module as notificationsModule } from './modules/notifications';
import { user } from './modules/user';
import { module as initServer } from './modules/initializeServer';
import { alertsModule } from '@/store/modules/alerts';

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
    generalModule,
    clientsModule,
    clientModule,
    tokensModule,
    servicesModule,
    csrModule,
    addClientModule,
    notificationsModule,
    initServer,
    alertsModule,
  },
  plugins: [vuexLocal.plugin],
};

export default new Vuex.Store<RootState>(store);
