import { Module } from 'vuex';
import { RootState } from './../../src/store/types';
import mockJson from './mockClients.json';
import compareJson from './mockClientsResult.json';
import Vue from 'vue';
import Vuex, { StoreOptions } from 'vuex';
import { clientsState, mutations as clientsMutations, getters as clientsGetters, ClientsState } from '@/store/modules/clients';
import { getDefaultState as initState, mutations as initMutations, getters as initGetters, State as InitState } from '@/store/modules/initializeServer';


Vue.use(Vuex);

describe('clients actions', () => {
  let store: any;
  let setDataMock;
  beforeEach(() => {
    setDataMock = jest.fn();

    const clientsModule: Module<ClientsState, RootState> = {
      namespaced: false,
      state: clientsState,
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



describe('initialize store', () => {
  let store: any;

  beforeEach(() => {

    const testModule: Module<InitState, RootState> = {
      namespaced: false,
      state: initState,
      getters: initGetters,
      mutations: initMutations,
    };

    const storeOptions: StoreOptions<RootState> = {
      modules: {
        testModule,
      },
    };

    store = new Vuex.Store<RootState>(storeOptions);
  });

  it('Needs initialization', () => {

    // Anchor is ok
    let mockInitStatus = {
      is_anchor_imported: true,
      is_server_code_initialized: false,
      is_server_owner_initialized: false,
      is_software_token_initialized: false
    }
    store.commit('storeInitStatus', mockInitStatus);
    expect(store.getters.needsInitialization).toBe(true);

    // Nothing is done
    mockInitStatus = {
      is_anchor_imported: false,
      is_server_code_initialized: false,
      is_server_owner_initialized: false,
      is_software_token_initialized: false
    }

    store.commit('storeInitStatus', mockInitStatus);
    expect(store.getters.needsInitialization).toBe(true);

    // Fully initialized
    mockInitStatus = {
      is_anchor_imported: true,
      is_server_code_initialized: true,
      is_server_owner_initialized: true,
      is_software_token_initialized: true
    }

    store.commit('storeInitStatus', mockInitStatus);
    expect(store.getters.needsInitialization).toBe(false);

  });
});