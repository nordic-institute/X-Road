/*
 * The MIT License
 *
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
import {
  Client,
  MemberAdd,
  MemberGlobalGroup,
  MemberName,
  SecurityServer,
} from '@/openapi-types';
import { defineStore } from 'pinia';
import { WithCurrentItem } from '@niis/shared-ui';

export const useMember = defineStore('member', {
  state: (): WithCurrentItem<Client> => ({
    current: undefined,
  }),

  actions: {
    async add(member: MemberAdd) {
      return axios.post('/members', member);
    },
    async loadById(memberId: string) {
      this.loadingCurrent = true;
      this.current = undefined;
      return axios
        .get<Client>(`/members/${memberId}`)
        .then((resp) => {
          this.current = resp.data;
        })
        .catch((error) => {
          throw error;
        })
        .finally(() => (this.loadingCurrent = false));
    },
    deleteById(memberId: string) {
      return axios.delete(`/members/${memberId}`);
    },
    async editMemberName(memberId: string, memberName: MemberName) {
      return axios
        .patch<Client>(`/members/${memberId}`, memberName)
        .then((resp) => {
          this.current = resp.data;
        })
        .catch((error) => {
          throw error;
        });
    },
    async getMemberOwnedServers(memberId: string) {
      return axios
        .get<SecurityServer[]>(`/members/${memberId}/owned-servers`)
        .then((resp) => resp.data)
        .catch((error) => {
          throw error;
        });
    },
    async getUsedServers(memberId: string) {
      return axios
        .get<SecurityServer[]>(`/members/${memberId}/used-servers`)
        .then((resp) => resp.data)
        .catch((error) => {
          throw error;
        });
    },
    async getMemberGlobalGroups(memberId: string) {
      return axios
        .get<MemberGlobalGroup[]>(`/members/${memberId}/global-groups`)
        .then((resp) => resp.data)
        .catch((error) => {
          throw error;
        });
    },
    async unregister(memberId: string, serverId: string) {
      return axios.delete(`/members/${memberId}/servers/${serverId}`);
    },
  },
});
