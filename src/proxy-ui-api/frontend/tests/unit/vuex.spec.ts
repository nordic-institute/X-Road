import { Module } from 'vuex';
import { RootState } from './../../src/store/types';
import mockJson from './mockClients.json';
import compareJson from './mockClientsResult.json';
import Vue from 'vue';
import Vuex, { StoreOptions } from 'vuex';
Vue.use(Vuex);

import { mutations as clientsMutations, getters as clientsGetters, ClientsState } from '@/store/modules/clients';

describe('clients actions', () => {
  let store: any;
  let setDataMock;
  beforeEach(() => {
    setDataMock = jest.fn();

    const state: ClientsState = {
      loading: false,
      localMembers: [],
      clients: [],
      formattedClients: [],
      members: [],
      subsystems: [],
    };

    const clientsModule: Module<ClientsState, RootState> = {
      namespaced: false,
      state,
      getters: clientsGetters,
      mutations: clientsMutations,
    };

    const storeOptions: StoreOptions<RootState> = {
      modules: {
        clientsModule,
      },
    };

    store = new Vuex.Store<RootState>(storeOptions);

    store.commit('storeClients', mockJson);
  });

  it('Get clients', () => {
    const result = store.getters.clients;
    // Check that the array has correct length
    expect(result).toHaveLength(8);

    // Compare the array to a correct result
    expect(result).toEqual(expect.arrayContaining(compareJson));
  });
});
