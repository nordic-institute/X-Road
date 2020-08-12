import { RootState } from './../../src/store/types';
import mockJson from './mockClients.json';
import compareJson from './mockClientsResult.json';
import Vue from 'vue';
import Vuex, { StoreOptions } from 'vuex';
import { clientsModule } from '@/store/modules/clients';
import { user as userModule } from '@/store/modules/user';

Vue.use(Vuex);

describe('clients actions', () => {
  let store: any;
  let setDataMock;
  beforeEach(() => {
    setDataMock = jest.fn();

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

    const storeOptions: StoreOptions<RootState> = {
      modules: {
        userModule,
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