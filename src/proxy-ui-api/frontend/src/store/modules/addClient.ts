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

/**
 * Pinia store for add client/subsystem/member wizards
 */
import { defineStore } from 'pinia';
import * as api from '@/util/api';
import { createClientId } from '@/util/helpers';
import { useUser } from './user';
import { encodePathParameter } from '@/util/api';
import {
  Client,
  Key,
  KeyUsageType,
  Token,
  TokenCertificateSigningRequest,
} from '@/openapi-types';
import { AddMemberWizardModes } from '@/global';
import { useCsrStore } from './certificateSignRequest';

// Compares two Clients on member level and returns true if the
// member ids of the clients match. Otherwise returns false.
const memberEquals = (client: Client, other: Client): boolean =>
  client.member_class === other.member_class &&
  client.member_code === other.member_code &&
  client.instance_id === other.instance_id;

// Filters out clients that have local relatives.
// If the member owning the client or another subsystem
// of the same member is already present locally,
// the client is excluded.
const excludeClientsWithLocalRelatives = (
  clients: Client[],
  localClients: Client[],
): Client[] => {
  return clients.filter((client: Client) => {
    return !localClients.some((localClient: Client) =>
      memberEquals(localClient, client),
    );
  });
};

interface ReservedMemberData {
  instanceId: string;
  memberClass: string;
  memberCode: string;
}

export interface AddClientState {
  expandedTokens: string[];
  tokens: Token[];
  tokenId: string | undefined;
  selectableClients: Client[];
  selectableMembers: Client[];
  reservedClients: Client[];
  selectedMemberName: string | undefined;
  memberClass: string;
  memberCode: string;
  subsystemCode: string | undefined;
  memberWizardMode: string;
  reservedMemberData: ReservedMemberData | undefined;
}

export const useAddClient = defineStore('addClient', {
  state: (): AddClientState => {
    return {
      expandedTokens: [],
      tokens: [],
      selectableClients: [],
      selectableMembers: [],
      reservedClients: [],
      selectedMemberName: '',
      memberClass: '',
      memberCode: '',
      subsystemCode: undefined,
      tokenId: undefined,
      memberWizardMode: AddMemberWizardModes.FULL,
      reservedMemberData: undefined,
    };
  },
  getters: {
    addMemberWizardMode: (state) => state.memberWizardMode,
    selectedMemberId(state): string | undefined {
      // Access user store
      const user = useUser();

      // If for some reason the currentSecurityServer doesn't exist
      if (!user.currentSecurityServer.instance_id) return undefined;

      // Instance id is always the same with current server and members
      return createClientId(
        user.currentSecurityServer.instance_id as string, // Type of this is checked above
        state.memberClass,
        state.memberCode,
      );
    },
    reservedMember(state: AddClientState): ReservedMemberData | undefined {
      return state.reservedMemberData;
    },
  },

  actions: {
    resetAddClientState() {
      // Clear the store state
      this.$reset();
    },

    createClient(ignoreWarnings: boolean) {
      const body = {
        client: {
          member_class: this.memberClass,
          member_code: this.memberCode,
          subsystem_code: this.subsystemCode,
        },
        ignore_warnings: ignoreWarnings,
      };

      return api.post('/clients', body).catch((error) => {
        throw error;
      });
    },

    createMember(ignoreWarnings: boolean) {
      const body = {
        client: {
          member_class: this.memberClass,
          member_code: this.memberCode,
        },
        ignore_warnings: ignoreWarnings,
      };

      return api.post('/clients', body).catch((error) => {
        throw error;
      });
    },

    fetchReservedClients(client: Client) {
      // Fetch clients from backend that match the selected client without subsystem code
      return api
        .get<Client[]>('/clients', {
          params: {
            instance: client.instance_id,
            member_class: client.member_class,
            member_code: client.member_code,
            internal_search: true,
          },
        })
        .then((res) => {
          this.reservedClients = res.data;
        })
        .catch((error) => {
          throw error;
        });
    },

    fetchReservedMembers(client: Client) {
      // Fetch clients from backend that match the selected client without subsystem code
      return api
        .get<Client[]>('/clients', {
          params: {
            instance: client.instance_id,
            member_class: client.member_class,
            member_code: client.member_code,
            internal_search: true,
          },
        })
        .then((res) => {
          this.reservedClients = res.data;
        })
        .catch((error) => {
          throw error;
        });
    },

    fetchSelectableClients(instanceId: string) {
      const globalClientsPromise = api.get<Client[]>(
        `/clients?exclude_local=true&internal_search=false&show_members=false&instance=${encodePathParameter(
          instanceId,
        )}`,
      );
      const localClientsPromise = api.get<Client[]>('/clients');
      // Fetch list of local clients and filter out global clients
      // that have local relatives
      return Promise.all([globalClientsPromise, localClientsPromise])
        .then((response) => {
          const globalClients = response[0];
          const localClients = response[1];
          this.selectableClients = excludeClientsWithLocalRelatives(
            globalClients.data,
            localClients.data,
          );
        })
        .catch((error) => {
          throw error;
        });
    },

    fetchSelectableMembers(instanceId: string) {
      // Fetch clients from backend that can be selected
      return api
        .get<Client[]>(
          `/clients?internal_search=false&show_members=true&instance=${encodePathParameter(
            instanceId,
          )}`,
        )
        .then((res) => {
          // Filter out subsystems
          const filtered = res.data.filter((client: Client) => {
            return !client.subsystem_code;
          });

          this.selectableMembers = filtered;
        })
        .catch((error) => {
          throw error;
        });
    },

    // set AddMemberWizardModes.CERTIFICATE_EXISTS and/or AddMemberWizardModes.CSR_EXISTS to correct values
    // to adjust how add client wizard works
    // both values are possible even if this member is not yet a local client in this SS
    async searchTokens(params: {
      instanceId: string;
      memberClass: string;
      memberCode: string;
    }) {
      const clientsResponse = await api.get<Client[]>('/clients', {
        params: {
          instance: params.instanceId,
          member_class: params.memberClass,
          member_code: params.memberCode,
          internal_search: false,
          local_valid_sign_cert: true,
        },
      });

      const matchingClient: boolean = clientsResponse.data.some(
        (client: Client) => {
          if (
            client.member_code === params.memberCode &&
            client.member_class === params.memberClass
          ) {
            return true;
          }
        },
      );

      if (matchingClient) {
        // There is a valid sign certificate for given member (which may or may not have local clients)
        this.memberWizardMode = AddMemberWizardModes.CERTIFICATE_EXISTS;
        return;
      }

      // CERTIFICATE_EXISTS is ok, check for CSR_EXISTS next

      // Fetch tokens from backend
      const tokenResponse = await api.get<Token[]>('/tokens');
      // Create a client id
      const ownerId = createClientId(
        params.instanceId,
        params.memberClass,
        params.memberCode,
      );

      // Find if a token has a sign key with a certificate that has matching client data
      tokenResponse.data.some((token: Token) => {
        return token.keys.some((key: Key) => {
          if (key.usage === KeyUsageType.SIGNING) {
            // Go through the keys CSR:s
            key.certificate_signing_requests.some(
              (csr: TokenCertificateSigningRequest) => {
                if (ownerId === csr.owner_id) {
                  const csrStore = useCsrStore();
                  csrStore.setCsrTokenId(token.id);
                  csrStore.setKeyId(key.id);

                  this.memberWizardMode = AddMemberWizardModes.CSR_EXISTS;

                  return true;
                }
              },
            );
          }
        });
      });
    },

    setAddMemberWizardMode(mode: string) {
      this.memberWizardMode = mode;
    },

    setSelectedMember(member: Client) {
      this.selectedMemberName = member.member_name;
      this.memberClass = member.member_class;
      this.memberCode = member.member_code;
      this.subsystemCode = member.subsystem_code;
    },

    setSelectedMemberName(val: string | undefined) {
      this.selectedMemberName = val;
    },

    storeReservedMember(memberData?: ReservedMemberData) {
      this.reservedMemberData = memberData;
    },
  },
});
