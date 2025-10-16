<!--
   The MIT License

   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->
<template>
  <XrdAppLogin ref="loginForm" :loading @login="submit" />
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { mapActions, mapState } from 'pinia';

import axios, { AxiosError } from 'axios';

import { XrdAppLogin, useNotifications } from '@niis/shared-ui';

import { Permissions, RouteName } from '@/global';
import { useSystem } from '@/store/modules/system';
import { useUser } from '@/store/modules/user';

interface Form {
  clearForm(): void;

  addErrors(...errors: string[]): void;
}

export default defineComponent({
  components: {
    XrdAppLogin,
  },
  setup() {
    const { addError, addSuccessMessage, clear, addErrorMessage } =
      useNotifications();
    return { addError, addSuccessMessage, clear, addErrorMessage };
  },
  data() {
    return {
      loading: false as boolean,
    };
  },
  computed: {
    ...mapState(useUser, [
      'hasPermission',
      'firstAllowedTab',
      'hasInitState',
      'needsInitialization',
    ]),
  },
  methods: {
    ...mapActions(useUser, [
      'loginUser',
      'logoutUser',
      'fetchInitializationStatus',
      'fetchUserData',
      'fetchCurrentSecurityServer',
      'clearAuth',
    ]),
    ...mapActions(useSystem, [
      'fetchSecurityServerVersion',
      'fetchSecurityServerNodeType',
      'fetchAuthenticationProviderType',
      'clearSystemStore',
    ]),
    async submit(username: string, password: string) {
      // Clear error notifications when route is changed
      this.clear();

      /* Clear user data so there is nothing left from previous sessions.
       For example user has closed browser tab without loggin out > user data is left in browser local storage */
      this.clearAuth();
      this.clearSystemStore();

      const loginData = { username, password };
      const loginForm = this.$refs.loginForm as Form;

      this.loading = true;

      try {
        await this.loginUser(loginData);
      } catch (error) {
        if (axios.isAxiosError(error)) {
          // Display invalid username/password error in inputs
          if (error?.response?.status === 401) {
            // Clear inputs
            loginForm.clearForm();

            loginForm.addErrors(this.$t('login.errorMsg401'));
          }
          this.addErrorMessage('login.generalError');
        } else {
          if (error instanceof Error) {
            this.addError(error.message);
          } else {
            throw error;
          }
        }
        this.loading = false;
        return;
      }

      // Auth ok. Start phase 2 (fetch user data and current security server info).

      try {
        loginForm.clearForm();
        await this.fetchUserData();
        await this.fetchInitializationData(); // Used to be inside fetchUserData()
        await this.fetchSecurityServerVersion();
        await this.fetchSecurityServerNodeType();
        await this.fetchAuthenticationProviderType();
      } catch (error) {
        this.addError(error as AxiosError);
      }

      // Clear loading state
      this.loading = false;
    },

    async fetchInitializationData() {
      const redirectToLogin = async () => {
        // Logout without page refresh
        await this.logoutUser(false);
      };

      await this.fetchInitializationStatus();
      await this.fetchSecurityServerNodeType();
      if (!this.hasInitState) {
        this.addErrorMessage('initialConfiguration.noInitializationStatus');
        await redirectToLogin();
      } else if (this.needsInitialization) {
        // Check if the user has permission to initialize the server
        if (!this.hasPermission(Permissions.INIT_CONFIG)) {
          await redirectToLogin();
          throw new Error(
            this.$t('initialConfiguration.noPermission') as string,
          );
        }
        await this.$router.replace({ name: RouteName.InitialConfiguration });
      } else {
        // No need to initialise, proceed to "main view"
        await this.fetchCurrentSecurityServer();
        await this.$router.replace(this.firstAllowedTab.to);
      }
    },
  },
});
</script>
<style lang="scss" scoped></style>
