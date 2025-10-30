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

export const tabs: Tab[] = [
  {
    to: { name: RouteName.Members },
    key: 'members',
    name: 'tab.main.members',
    icon: 'folder',
    permissions: [Permissions.VIEW_MEMBERS, Permissions.VIEW_MEMBER_DETAILS],
  },
  {
    to: { name: RouteName.SecurityServers },
    key: 'keys',
    name: 'tab.main.securityServers',
    icon: 'dns',
    permissions: [
      Permissions.VIEW_SECURITY_SERVERS,
      Permissions.VIEW_SECURITY_SERVER_DETAILS,
    ],
  },
  {
    to: { name: RouteName.ManagementRequests },
    key: 'managementRequests',
    name: 'tab.main.managementRequests',
    icon: 'rule_settings',
    permissions: [
      Permissions.VIEW_MANAGEMENT_REQUESTS,
      Permissions.VIEW_MANAGEMENT_REQUEST_DETAILS,
    ],
  },
  {
    to: { name: RouteName.TrustServices },
    key: 'trustServices',
    name: 'tab.main.trustServices',
    icon: 'shield_lock',
    permissions: [
      Permissions.VIEW_APPROVED_CAS,
      Permissions.VIEW_APPROVED_TSAS,
      Permissions.VIEW_APPROVED_CA_DETAILS,
      Permissions.VIEW_APPROVED_TSA_DETAILS,
    ],
  },
  {
    // Global configuration tab
    to: { name: RouteName.GlobalConfiguration }, // name of the first child tab
    key: 'globalConfiguration',
    name: 'tab.main.globalConfiguration',
    icon: 'page_info',
    permissions: [
      Permissions.VIEW_CONFIGURATION_MANAGEMENT,
      Permissions.VIEW_EXTERNAL_CONFIGURATION_SOURCE,
      Permissions.VIEW_INTERNAL_CONFIGURATION_SOURCE,
      Permissions.VIEW_TRUSTED_ANCHORS,
    ],
  },
  {
    // Settings tab
    to: { name: RouteName.Settings },
    key: 'settings',
    name: 'tab.main.settings',
    icon: 'settings',
    permissions: [
      Permissions.VIEW_SYSTEM_SETTINGS,
      Permissions.VIEW_GLOBAL_GROUPS,
      Permissions.VIEW_SECURITY_SERVERS,
      Permissions.BACKUP_CONFIGURATION,
      Permissions.VIEW_API_KEYS,
    ],
  },
];

export const useMainTabs = defineStore('main-tabs', {
  state: () => ({}),
  persist: false,
  getters: {
    allTabs(): Tab[] {
      return tabs;
    },
    availableTabs(): Tab[] {
      return useUser().getAllowedTabs(tabs);
    },
    firstAllowedTab(): Tab {
      return this.availableTabs[0];
    },
  },
  actions: {},
});
