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
import * as api from '@/util/api';
import { Client } from '@/openapi-types';
import { createClientId, deepClone, Mutable } from '@/util/helpers';
import { ExtendedClient } from '@/ui-types';
import { ClientTypes } from '@/global';
import i18n from '@/plugins/i18n';
import { defineStore } from 'pinia';

const UNKNOWN_NAME: string = i18n.global.t('client.unknownMember') as string;

export interface ClientsState {
  clients: Client[];
  formattedClients: ExtendedClient[];
  clientsLoading: boolean;
  ownerMember: Client | undefined;
  members: ExtendedClient[]; // all local members, virtual and real
  realMembers: ExtendedClient[]; // local actual real members, owner +1
  subsystems: ExtendedClient[];
}

export const useClients = defineStore('clients', {
  state: (): ClientsState => {
    return {
      clients: [] as Client[],
      formattedClients: [],
      clientsLoading: false,
      ownerMember: undefined,
      members: [],
      subsystems: [],
      realMembers: [],
    };
  },
  getters: {
    getClients: (state) => state.formattedClients,
  },

  actions: {
    fetchClients() {
      this.clientsLoading = true;

      return api
        .get<Client[]>('/clients')
        .then((res) => {
          this.storeClients(res.data);
        })
        .catch((error) => {
          throw error;
        })
        .finally(() => {
          this.clientsLoading = false;
        });
    },
    storeClients(clients: Client[]) {
      this.clients = clients;

      // New arrays to separate members and subsystems
      const realMembers: ExtendedClient[] = [];
      const members: ExtendedClient[] = [];
      const subsystems: ExtendedClient[] = [];

      // Find members. Owner member (there is only one) and possible other member
      this.clients.forEach((element: Client) => {
        if (!element.subsystem_code) {
          const clone = deepClone(element) as ExtendedClient;
          clone.type = ClientTypes.OWNER_MEMBER;
          clone.subsystem_code = undefined;
          clone.visibleName = clone.member_name || UNKNOWN_NAME;

          if (element.owner) {
            clone.type = ClientTypes.OWNER_MEMBER;
            this.ownerMember = element;
          } else {
            clone.type = ClientTypes.MEMBER;
          }

          realMembers.push(clone);
          members.push(clone);
        }
      });

      // Pick out the members
      this.clients.forEach((element) => {
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

      this.realMembers = realMembers;
      this.subsystems = subsystems;
      this.members = members;
      // Combine the arrays
      this.formattedClients = [...new Set([...subsystems, ...members])];
    },
  },
});
