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

import {
  MaintenanceMode,
  NodeType,
  NodeTypeResponse,
  VersionInfo,
  AuthProviderType,
  AuthProviderTypeResponse,
} from '@/openapi-types';
import * as api from '@/util/api';

export interface SystemState {
  securityServerVersion: VersionInfo;
  securityServerNodeType?: NodeType;
  securityServerAuthProviderType?: AuthProviderType;
}

export const useSystem = defineStore('system', {
  state: (): SystemState => {
    return {
      securityServerVersion: {} as VersionInfo,
      securityServerNodeType: undefined,
      securityServerAuthProviderType: undefined,
    };
  },
  persist: {
    storage: localStorage,
  },
  getters: {
    isSecondaryNode(state) {
      return state.securityServerNodeType === NodeType.SECONDARY;
    },
    globalConfigurationVersion(state): number | undefined {
      return state.securityServerVersion.global_configuration_version;
    },
    doesSupportSubsystemNames(): boolean {
      return (
        !!this.globalConfigurationVersion &&
        this.globalConfigurationVersion >= 5
      );
    },
    isDatabaseBasedAuthentication(): boolean {
      return this.securityServerAuthProviderType === AuthProviderType.DATABASE;
    },
  },

  actions: {
    // Reset store
    clearSystemStore() {
      this.$reset();
    },
    async fetchSecurityServerNodeType() {
      return api.get<VersionInfo>('/system/version').then((res) => {
        this.securityServerVersion = res.data;
      });
    },
    async fetchSecurityServerVersion() {
      // Fetch tokens from backend
      return api.get<NodeTypeResponse>('/system/node-type').then((res) => {
        this.securityServerNodeType = res.data.node_type;
      });
    },
    async fetchAuthenticationProviderType() {
      return api
        .get<AuthProviderTypeResponse>('/system/auth-provider-type')
        .then((res) => {
          this.securityServerAuthProviderType = res.data.auth_provider_type;
        });
    },
    async enableMaintenanceMode(message?: string) {
      return api.put('/system/maintenance-mode/enable', { message });
    },
    async disableMaintenanceMode() {
      return api.put('/system/maintenance-mode/disable', {});
    },
    async fetchMaintenanceModeState() {
      return api
        .get<MaintenanceMode>('/system/maintenance-mode')
        .then((resp) => resp.data);
    },
    async changeSecurityServerAddress(address: string) {
      return api.put('/system/server-address', {
        address,
      });
    },
  },
});
