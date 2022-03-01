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

import { defineStore } from 'pinia';
import * as api from '@/util/api';
import { MemberName } from '@/openapi-types';

export const useGeneral = defineStore('general', {
  state: () => {
    return {
      xroadInstances: [] as string[],
      memberClasses: [] as string[],
      memberClassesCurrentInstance: [] as string[],
      memberName: '' as string,
    };
  },

  actions: {
    fetchMemberClasses() {
      return api
        .get<string[]>('/member-classes')
        .then((res) => {
          this.memberClasses = res.data;
        })
        .catch((error) => {
          throw error;
        });
    },

    fetchMemberClassesForCurrentInstance() {
      return api
        .get<string[]>('/member-classes?current_instance=true')
        .then((res) => {
          this.memberClassesCurrentInstance = res.data;
        })
        .catch((error) => {
          throw error;
        });
    },

    fetchMemberName(memberClass: string, memberCode: string) {
      // this is currently an inline schema and is not automatically generated to a typescript type
      return api
        .get<MemberName>(
          `/member-names?member_class=${memberClass}&member_code=${memberCode}`,
        )
        .then((res) => {
          this.memberName = res.data.member_name || '';
        })
        .catch((error) => {
          throw error;
        });
    },

    fetchXroadInstances() {
      return api
        .get('/xroad-instances')
        .then((res) => {
          this.xroadInstances = res.data as string[];
        })
        .catch((error) => {
          throw error;
        });
    },
  },
});
