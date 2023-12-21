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

import axiosAuth from '../../axios-auth';
import axios from 'axios';
import { defineStore } from 'pinia';
import { Tab } from '@/ui-types';
import { User } from '@/openapi-types';
import { mainTabs } from '@/global';
import { get } from '@/util/api';

export const useUser = defineStore('user', {
  state: () => {
    return {
      authenticated: false,
      isSessionAlive: false,
      username: '' as string,
      permissions: [] as string[],
      roles: [] as string[],
      count: 0,
    };
  },
  persist: {
    storage: localStorage,
  },
  getters: {
    getUsername(): string {
      return this.username;
    },
    isAuthenticated(): boolean {
      return this.authenticated;
    },
    hasPermission: (state) => {
      // Return true if the user has given permission
      return (permission: string) => state.permissions.includes(permission);
    },

    canAssignRole: (state) => {
      return (role: string) => {
        return (
          state.roles.includes(role) ||
          (role === 'XROAD_MANAGEMENT_SERVICE' &&
            state.roles.includes('XROAD_SYSTEM_ADMINISTRATOR'))
        );
      };
    },

    hasAnyOfPermissions: (state) => {
      // Return true if the user has at least one of the tabs permissions
      return (perm: string[]): boolean =>
        perm?.some((permission) => state.permissions.includes(permission));
    },

    getAllowedTabs: (state) => (tabs: Tab[]) => {
      // returns filtered array of Tab objects based on the 'permission' attribute
      return tabs?.filter((tab: Tab) => {
        const neededPermissions = tab.permissions;

        return !!(
          neededPermissions &&
          neededPermissions?.some((permission) =>
            state.permissions.includes(permission),
          )
        );
      });
    },

    getFirstAllowedTab(): Tab {
      return this.getAllowedTabs(mainTabs)[0];
    },
  },

  actions: {
    async login(authData: { username: string; password: string }) {
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
      }).then(() => {
        this.authenticated = true;
        this.isSessionAlive = true;
      });
    },

    async fetchSessionStatus() {
      return axios
        .get('/notifications/session-status')
        .then((res) => {
          this.isSessionAlive = res?.data?.valid ?? false;
        })
        .catch(() => {
          this.isSessionAlive = false;
        });
    },

    async fetchUserData() {
      return get<User>('/user')
        .then((user) => {
          this.username = user?.data?.username;
          this.setPermissions(user?.data?.permissions);
          this.roles = user?.data?.roles.map((role) =>
            role.startsWith('ROLE_') ? role.slice(5) : role,
          );
        })
        .catch((error) => {
          throw error;
        });
    },

    setPermissions(permissions: string[]) {
      this.permissions = permissions;
    },

    logout(reload = true) {
      // Clear auth data
      this.clearAuth();

      // Call backend for logout
      axiosAuth
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

    clearAuth() {
      // Clear auth by resetting the state
      this.$reset();
    },

    setSessionAlive(value: boolean) {
      this.isSessionAlive = value;
    },
  },
});
