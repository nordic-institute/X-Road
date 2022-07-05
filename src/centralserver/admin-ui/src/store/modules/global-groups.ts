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
import axios from 'axios';
import {
  GlobalGroupDescription,
  GlobalGroupResource,
  GlobalGroupCodeAndDescription,
} from '@/openapi-types';

export interface State {
  globalGroups: GlobalGroupResource[];
  groupsLoading: boolean;
}

export const useGlobalGroupsStore = defineStore('globalGroup', {
  state: (): State => ({
    globalGroups: [],
    groupsLoading: false,
  }),
  actions: {
    findAll() {
      this.groupsLoading = true;
      return axios
        .get<GlobalGroupResource[]>('/global-groups')
        .then((resp) => (this.globalGroups = resp.data))
        .catch((error) => {
          throw error;
        })
        .finally(() => {
          this.groupsLoading = false;
        });
    },
    getById(groupId: string) {
      return axios
        .get<GlobalGroupResource>(`/global-groups/${groupId}`)
        .then((resp) => resp.data)
        .catch((error) => {
          throw error;
        });
    },
    add(codeAndDescription: GlobalGroupCodeAndDescription) {
      return axios
        .post('/global-groups', codeAndDescription)
        .finally(() => this.findAll());
    },
    deleteById(groupId: string) {
      return axios
        .delete<GlobalGroupResource>(`/global-groups/${groupId}`)
        .catch((error) => {
          throw error;
        });
    },
    editGroupDescription(groupId: string, description: GlobalGroupDescription) {
      return axios
        .patch<GlobalGroupResource>(`/global-groups/${groupId}`, description)
        .catch((error) => {
          throw error;
        });
    },
  },
});
