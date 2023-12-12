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
import { Tab } from '@/ui-types';
import { useUser } from '@/store/modules/user';

export interface State {
  allSettingsTabs: Tab[];
}

export const useSettingsTabs = defineStore('settingsTabs', {
  state: (): State => ({
    allSettingsTabs: [
      {
        key: 'globalresources-tab-button',
        name: 'tab.settings.globalResources',
        to: {
          name: RouteName.GlobalResources,
        },
        permissions: [
          Permissions.VIEW_GLOBAL_GROUPS,
          Permissions.VIEW_SECURITY_SERVERS,
        ],
      },
      {
        key: 'systemsettings-tab-button',
        name: 'tab.settings.systemSettings',
        to: {
          name: RouteName.SystemSettings,
        },
        permissions: [Permissions.VIEW_SYSTEM_SETTINGS],
      },
      {
        key: 'backupandrestore-tab-button',
        name: 'tab.settings.backupAndRestore',
        to: {
          name: RouteName.BackupAndRestore,
        },
        permissions: [Permissions.BACKUP_CONFIGURATION],
      },
      {
        key: 'apikeys-tab-button',
        name: 'tab.settings.apiKeys',
        to: {
          name: RouteName.ApiKeys,
        },
        permissions: [
          Permissions.VIEW_API_KEYS,
          Permissions.CREATE_API_KEY,
          Permissions.REVOKE_API_KEY,
        ],
      },
      {
        key: 'tlscertificates-tab-button',
        name: 'tab.settings.tlsCertificates',
        to: {
          name: RouteName.TlsCertificates,
        },
        permissions: [Permissions.VIEW_TLS_CERTIFICATES],
      },
    ],
  }),
  persist: false,
  actions: {
    getAvailableTabs() {
      return useUser().getAllowedTabs(this.allSettingsTabs);
    },
  },
});
