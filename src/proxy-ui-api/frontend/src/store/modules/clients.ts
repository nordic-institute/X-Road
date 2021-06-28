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
import axios from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { Client } from '@/openapi-types/ss-types';
import { createClientId, deepClone, Mutable } from '@/util/helpers';
import { ExtendedClient } from '@/ui-types';
import { ClientTypes } from '@/global';
import i18n from './../../i18n';

const UNKNOWN_NAME: string = i18n.t('client.unknownMember') as string;

export interface ClientsState {
  clients: Client[];
  formattedClients: ExtendedClient[];
  clientsLoading: boolean;
  ownerMember: Client | undefined;
  members: ExtendedClient[]; // all local members, virtual and real
  realMembers: ExtendedClient[]; // local actual real members, owner +1
  subsystems: ExtendedClient[];
}

export const clientsState: ClientsState = {
  clients: [],
  formattedClients: [],
  clientsLoading: false,
  ownerMember: undefined,
  members: [],
  subsystems: [],
  realMembers: [],
};

export const getters: GetterTree<ClientsState, RootState> = {
  clients(state): ExtendedClient[] {
    return state.formattedClients;
  },

  realMembers(state): ExtendedClient[] {
    return state.realMembers;
  },

  clientsLoading(state): boolean {
    return state.clientsLoading;
  },

  ownerMember(state): Client | undefined {
    return state.ownerMember;
  },
};

export const mutations: MutationTree<ClientsState> = {
  storeClients(state, clients: Client[]) {
    state.clients = clients;

    // New arrays to separate members and subsystems
    const realMembers: ExtendedClient[] = [];
    const members: ExtendedClient[] = [];
    const subsystems: ExtendedClient[] = [];

    // Find members. Owner member (there is only one) and possible other member
    state.clients.forEach((element: Client) => {
      if (!element.subsystem_code) {
        const clone = deepClone(element) as ExtendedClient;
        clone.type = ClientTypes.OWNER_MEMBER;
        clone.subsystem_code = undefined;
        clone.visibleName = clone.member_name || UNKNOWN_NAME;

        if (element.owner) {
          clone.type = ClientTypes.OWNER_MEMBER;
          state.ownerMember = element;
        } else {
          clone.type = ClientTypes.MEMBER;
        }

        realMembers.push(clone);
        members.push(clone);
      }
    });

    // Pick out the members
    state.clients.forEach((element) => {
      // Check if the member is already in the members array
      const memberAlreadyExists = members.find(
        (member: ExtendedClient) =>
          member.member_class === element.member_class &&
          member.member_code === element.member_code &&
          member.instance_id === element.instance_id,
      );

      if (!memberAlreadyExists) {
        // If "virtual member" is not in members array, create and add it
        const clone = deepClone(element) as Mutable<ExtendedClient>; // Type Mutable<T> removes readonly from fields
        clone.type = ClientTypes.VIRTUAL_MEMBER;

        // This should not happen, but better to throw error than create an invalid client id
        if (!element.instance_id) {
          throw new Error('Missing instance id');
        }

        // Create "virtual member" id
        clone.id = createClientId(
          element.instance_id,
          element.member_class,
          element.member_code,
        );

        clone.subsystem_code = undefined;

        // Create a name from member_name
        clone.visibleName = clone.member_name || UNKNOWN_NAME;

        clone.status = undefined;

        members.push(clone);
      }

      // Push subsystems to an array
      if (element.subsystem_code) {
        const clone = deepClone(element) as ExtendedClient;
        clone.visibleName = clone.subsystem_code || UNKNOWN_NAME;
        clone.type = ClientTypes.SUBSYSTEM;

        subsystems.push(clone);
      }
    });

    state.realMembers = realMembers;
    state.subsystems = subsystems;
    state.members = members;
    // Combine the arrays
    state.formattedClients = [...new Set([...subsystems, ...members])];
  },

  setLoading(state, loading: boolean) {
    state.clientsLoading = loading;
  },
};

export const actions: ActionTree<ClientsState, RootState> = {
  fetchClients({ commit }) {
    commit('setLoading', true);

    return axios
      .get('/clients')
      .then((res) => {
        commit('storeClients', res.data);
      })
      .catch((error) => {
        throw error;
      })
      .finally(() => {
        commit('setLoading', false);
      });
  },
};

export const clientsModule: Module<ClientsState, RootState> = {
  namespaced: false,
  state: clientsState,
  getters,
  actions,
  mutations,
};
