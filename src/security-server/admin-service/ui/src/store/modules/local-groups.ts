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

import { defineStore } from 'pinia';
import { LocalGroup } from '@/openapi-types';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';

interface State {
  clientId?: string;
  localGroups: LocalGroup[];
  localGroup?: LocalGroup;
  loadingLocalGroups: boolean;
  loadingLocalGroup: boolean;
  deletingLocalGroupMembers: boolean;
  addingLocalGroupMembers: boolean;
  updatingDescription: boolean;
  deletingLocalGroup: boolean;
}

function lgBaseUrl(groupId: string, path = '') {
  const encodedId = encodePathParameter(groupId);
  return `/local-groups/${encodedId}` + path;
}

export const useLocalGroups = defineStore('local-groups', {
  state: (): State => ({
    clientId: undefined,
    localGroups: [],
    localGroup: undefined,
    loadingLocalGroups: false,
    loadingLocalGroup: false,
    deletingLocalGroup: false,
    deletingLocalGroupMembers: false,
    addingLocalGroupMembers: false,
    updatingDescription: false,
  }),
  getters: {
    sortedLocalGroups: (state): LocalGroup[] => {
      {
        return state.localGroups.sort((a: LocalGroup, b: LocalGroup) => {
          if (a.code.toLowerCase() < b.code.toLowerCase()) {
            return -1;
          }
          if (a.code.toLowerCase() > b.code.toLowerCase()) {
            return 1;
          }

          return 0;
        });
      }
    },
  },

  actions: {
    async fetchLocalGroups(clientId: string) {
      if (this.clientId && this.clientId != clientId) {
        this.localGroups = [];
      }
      const encodedId = encodePathParameter(clientId);
      this.loadingLocalGroups = true;
      return api
        .get<LocalGroup[]>(`/clients/${encodedId}/local-groups`)
        .then((res) => (this.localGroups = res.data))
        .finally(() => (this.loadingLocalGroups = false));
    },
    async fetchLocalGroup(groupId: string) {
      if (this.localGroup && this.localGroup.id != groupId) {
        this.localGroup = undefined;
      }
      this.loadingLocalGroup = true;
      return api
        .get<LocalGroup>(lgBaseUrl(groupId))
        .then((res) => (this.localGroup = res.data))
        .finally(() => (this.loadingLocalGroup = false));
    },
    async deleteLocalGroup(groupId: string) {
      if (this.localGroup && this.localGroup.id != groupId) {
        this.localGroup = undefined;
      }
      this.deletingLocalGroup = true;
      return api
        .remove(lgBaseUrl(groupId))
        .then(() => (this.localGroup = undefined))
        .finally(() => (this.deletingLocalGroup = false));
    },
    async updateDescription(groupId: string, description: string) {
      if (this.localGroup && this.localGroup.id != groupId) {
        this.localGroup = undefined;
      }
      this.updatingDescription = true;
      return api
        .patch<LocalGroup>(lgBaseUrl(groupId), {
          description,
        })
        .then((res) => (this.localGroup = res.data))
        .finally(() => (this.updatingDescription = false));
    },
    async deleteLocalGroupMembers(groupId: string, members: string[]) {
      this.deletingLocalGroupMembers = true;
      return api
        .post(lgBaseUrl(groupId, '/members/delete'), {
          items: members,
        })
        .finally(() => (this.deletingLocalGroupMembers = false));
    },
    async addLocalGroupMembers(groupId: string, members: string[]) {
      this.addingLocalGroupMembers = true;
      return api
        .post(lgBaseUrl(groupId, '/members'), {
          items: members,
        })
        .finally(() => (this.addingLocalGroupMembers = false));
    },
  },
});
