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
  <XrdAppLogin ref="loginForm" :loading @login="submit">
    <template #top>
      <AlertsContainer class="alerts" />
    </template>
  </XrdAppLogin>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useNotifications } from '@/store/modules/notifications';
import { useSystem } from '@/store/modules/system';
import AlertsContainer from '@/components/ui/AlertsContainer.vue';
import { swallowRedirectedNavigationError } from '@/util/helpers';
import axios from 'axios';
import { XrdAppLogin } from '@niis/shared-ui';

interface Form {
  clearForm(): void;

  addErrors(errors: string[]): void;
}

export default defineComponent({
  components: {
    XrdAppLogin,
    AlertsContainer,
  },
  data() {
    return {
      loading: false as boolean,
    };
  },
  computed: {
    ...mapState(useUser, ['getFirstAllowedTab']),
  },
  methods: {
    ...mapActions(useUser, ['login', 'fetchUserData']),
    ...mapActions(useNotifications, [
      'showError',
      'showErrorMessage',
      'resetNotifications',
    ]),
    ...mapActions(useSystem, ['fetchSystemStatus', 'fetchServerVersion']),
    async submit(username: string, password: string) {
      // Clear old error notifications (if they exist) before submit
      this.resetNotifications();

      const loginData = { username, password };

      this.loading = true;

      const loginForm = this.$refs.loginForm as Form;

      try {
        await this.login(loginData);
      } catch (error: unknown) {
        // Display invalid username/password error in inputs
        if (axios.isAxiosError(error) && error?.response?.status === 401) {
          // Clear inputs
          loginForm.clearForm();

          loginForm.addErrors(this.$t('login.errorMsg401'));
          this.showErrorMessage(this.$t('login.generalError'));
        } else {
          this.showError(error);
        }
        this.loading = false;
        return;
      }
      try {
        loginForm.clearForm();
        await this.requestUserData();
        await this.fetchServerVersion();
        await this.fetchSystemStatus();
        await this.routeToMembersPage();
      } catch (error) {
        this.showError(error);
        this.loading = false;
      }
    },
    async requestUserData() {
      this.loading = true;
      return this.fetchUserData();
    },
    async routeToMembersPage() {
      this.$router
        .replace(this.getFirstAllowedTab.to)
        .catch(swallowRedirectedNavigationError);
      this.loading = false;
    },
  },
});
</script>
<style lang="scss" scoped>
.alerts {
  top: 40px;
  left: 0;
  right: 0;
  margin-left: auto;
  margin-right: auto;
  z-index: 100;
  position: absolute;
}
</style>
