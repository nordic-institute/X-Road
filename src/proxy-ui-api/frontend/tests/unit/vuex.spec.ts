/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
import { RootState } from './../../src/store/types';
import mockJson from './mockClients.json';
import compareJson from './mockClientsResult.json';
import Vue from 'vue';
import Vuex, { StoreOptions } from 'vuex';
import { clientsModule } from '@/store/modules/clients';
import { user as userModule } from '@/store/modules/user';
import { TokenInitStatusEnum } from '@/global';
import { InitializationStatus } from '@/openapi-types';

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
    let mockInitStatus: InitializationStatus = {
      is_anchor_imported: true,
      is_server_code_initialized: false,
      is_server_owner_initialized: false,
      software_token_init_status: TokenInitStatusEnum.UNKNOWN
    }
    store.commit('storeInitStatus', mockInitStatus);
    expect(store.getters.needsInitialization).toBe(true);

    // Nothing is done
    mockInitStatus = {
      is_anchor_imported: false,
      is_server_code_initialized: false,
      is_server_owner_initialized: false,
      software_token_init_status: TokenInitStatusEnum.NOT_INITIALIZED
    }

    store.commit('storeInitStatus', mockInitStatus);
    expect(store.getters.needsInitialization).toBe(true);

    // Fully initialized
    mockInitStatus = {
      is_anchor_imported: true,
      is_server_code_initialized: true,
      is_server_owner_initialized: true,
      software_token_init_status: TokenInitStatusEnum.INITIALIZED
    }

    store.commit('storeInitStatus', mockInitStatus);
    expect(store.getters.needsInitialization).toBe(false);

  });
});