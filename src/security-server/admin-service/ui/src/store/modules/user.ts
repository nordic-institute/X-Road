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
import { mainTabs } from '@/global';
import axiosAuth from '../../axios-auth';
import * as api from '@/util/api';
import {
  InitializationStatus,
  SecurityServer,
  TokenInitStatus,
  User,
} from '@/openapi-types';
import { SessionStatus, Tab } from '@/ui-types';
import i18n from '@/plugins/i18n';
import { routePermissions } from '@/routePermissions';
import { useSystem } from './system';
import { RouteRecordName } from 'vue-router';

export const useUser = defineStore('user', {
  state: () => {
    return {
      authenticated: false,
      sessionAlive: undefined as boolean | undefined,
      permissions: [] as string[],
      roles: [] as string[],
      username: '',
      currentSecurityServer: {} as SecurityServer,
      initializationStatus: undefined as InitializationStatus | undefined,
      bannedRoutes: [] as RouteRecordName[], // Array for routes the user doesn't have permission to access.
    };
  },
  persist: {
    storage: localStorage,
  },
  getters: {
    hasPermission: (state) => (perm: string) => {
      return state.permissions.includes(perm);
    },
    hasRole: (state) => (role: string) => {
      return state.roles.includes(role);
    },
    hasAnyOfPermissions: (state) => (perm: string[]) => {
      // Return true if the user has at least one of the tabs permissions
      return perm.some((permission) => state.permissions.includes(permission));
    },
    getAllowedTabs:
      (state) =>
      (tabs: Tab[]): Tab[] => {
        // returns filtered array of Tab objects based on the 'permission' attribute
        const filteredTabs = tabs.filter((tab: Tab) => {
          const routeName = tab.to.name;

          if (routeName && !state.bannedRoutes?.includes(routeName)) {
            // Return true if the user has permission
            return true;
          }
          return false;
        });

        return filteredTabs;
      },

    firstAllowedTab(): Tab {
      return this.getAllowedTabs(mainTabs)[0];
    },

    isAnchorImported(state): boolean {
      return state.initializationStatus?.is_anchor_imported ?? false;
    },

    isServerOwnerInitialized(state): boolean {
      return state.initializationStatus?.is_server_owner_initialized ?? false;
    },

    isServerCodeInitialized(state): boolean {
      return state.initializationStatus?.is_server_code_initialized ?? false;
    },

    isEnforceTokenPolicyEnabled(state): boolean {
      return state.initializationStatus?.enforce_token_pin_policy ?? false;
    },

    softwareTokenInitializationStatus(state): TokenInitStatus | undefined {
      return state.initializationStatus?.software_token_init_status;
    },

    hasInitState: (state) => {
      return typeof state.initializationStatus !== 'undefined';
    },

    needsInitialization: (state) => {
      return !(
        state.initializationStatus?.is_anchor_imported &&
        state.initializationStatus.is_server_code_initialized &&
        state.initializationStatus.is_server_owner_initialized &&
        (state.initializationStatus.software_token_init_status ===
          TokenInitStatus.INITIALIZED ||
          state.initializationStatus.software_token_init_status ===
            TokenInitStatus.UNKNOWN)
      );
    },
  },

  actions: {
    async loginUser(authData: { username: string; password: string }) {
      const data = `username=${encodeURIComponent(
        authData.username,
      )}&password=${encodeURIComponent(authData.password)}`;

      return axiosAuth({
        url: '/login',
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        data,
      })
        .then(() => {
          this.authenticated = true;
          this.sessionAlive = true;
        })
        .catch((error) => {
          throw error;
        });
    },

    async fetchSessionStatus() {
      return api
        .get<SessionStatus>('/notifications/session-status')
        .then((res) => {
          this.sessionAlive = res?.data?.valid ?? false;
        })
        .catch(() => {
          this.sessionAlive = false;
        });
    },

    async fetchUserData() {
      return api
        .get<User>('/user')
        .then((res) => {
          this.username = res.data.username;
          this.setPermissions(res.data.permissions);
          this.setRoles(res.data.roles);
        })
        .catch((error) => {
          throw error;
        });
    },
    setPermissions(permissions: string[]) {
      this.permissions = permissions;

      const tempBannedRoutes: string[] = [];

      // Init banned routes array
      this.bannedRoutes = [];

      // Go through the route permissions
      routePermissions.forEach((route) => {
        // Check that the route has name and permissions
        if (route.name && route?.permissions) {
          // Find out routes that the user doesn't have permissions to access
          if (
            !route.permissions.some((permission: string) =>
              permissions.includes(permission),
            )
          ) {
            // Add a banned route to the array
            tempBannedRoutes.push(route.name);
          }
        }
      });

      this.bannedRoutes = tempBannedRoutes;
    },

    setRoles(roles: string[]) {
      this.roles = roles.map((role) =>
        role.startsWith('ROLE_') ? role.slice(5) : role,
      );
    },
    async fetchCurrentSecurityServer() {
      return api
        .get<SecurityServer[]>('/security-servers?current_server=true')
        .then((resp) => {
          if (resp.data?.length !== 1) {
            throw new Error(
              i18n.global.t(
                'stores.user.currentSecurityServerNotFound',
              ) as string,
            );
          }
          this.currentSecurityServer = resp.data[0];
        })
        .catch((error) => {
          throw error;
        });
    },

    logoutUser(reload = true) {
      // Clear auth data
      this.clearAuth();

      // Reset system data
      const system = useSystem();
      system.clearSystemStore();

      sessionStorage.clear();

      // Call backend for logout
      return axiosAuth
        .post('/logout')
        .catch(() => {
          // Nothing to do
        })
        .finally(() => {
          if (reload) {
            // Reload the browser page to clean up the memory
            location.reload();
          }
        });
    },

    async fetchInitializationStatus() {
      return api
        .get<InitializationStatus>('/initialization/status')
        .then((resp) => {
          this.initializationStatus = resp.data;
        })
        .catch((error) => {
          throw error;
        });
    },

    setInitializationStatus(): void {
      // Sets the initialization state to done
      const initStatus: InitializationStatus = {
        is_anchor_imported: true,
        is_server_code_initialized: true,
        is_server_owner_initialized: true,
        software_token_init_status: TokenInitStatus.INITIALIZED,
      };

      this.initializationStatus = initStatus;
    },

    // This action is currenlty needed only for unit testing
    storeInitStatus(status: InitializationStatus) {
      this.initializationStatus = status;
    },

    setSessionAlive(value: boolean) {
      this.sessionAlive = value;
    },

    authUser() {
      this.authenticated = true;
    },

    clearAuth(): void {
      // Clear the store state
      this.$reset();
    },
  },
});
