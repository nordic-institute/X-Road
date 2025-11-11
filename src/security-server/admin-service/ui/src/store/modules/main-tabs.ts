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
import { Permissions, RouteName } from '@/global';
import { Tab } from '@niis/shared-ui';
import { useUser } from '@/store/modules/user';

const tabs: Tab[] = [
  {
    to: { name: RouteName.Clients },
    key: 'clients',
    name: 'tab.main.clients',
    icon: 'id_card',
  },
  {
    to: { name: RouteName.Keys },
    key: 'keys',
    name: 'tab.main.keys',
    icon: 'key',
  },
  {
    to: { name: RouteName.Diagnostics },
    key: 'diagnostics',
    name: 'tab.main.diagnostics',
    icon: 'monitoring',
  },
  {
    to: { name: RouteName.Settings },
    key: 'settings',
    name: 'tab.main.settings',
    icon: 'settings',
    permissions: [
      Permissions.VIEW_SYS_PARAMS,
      Permissions.BACKUP_CONFIGURATION,
      Permissions.VIEW_ADMIN_USERS,
    ],
  },
];

export const useMainTabs = defineStore('main-tabs', {
  state: () => ({}),
  persist: false,
  getters: {
    availableTabs(): Tab[] {
      return useUser().getAllowedTabs(tabs);
    },
    firstAllowedTab(): Tab {
      return this.availableTabs[0];
    },
  },
  actions: {},
});
