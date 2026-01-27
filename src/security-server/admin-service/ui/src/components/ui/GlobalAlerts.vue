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
  <v-container v-if="authenticated && !needsInitialization && hasAlerts" class="pa-0" fluid>
    <XrdBanner
      v-if="showGlobalConfAlert"
      data-test="global-alert-global-configuration"
      icon="error"
      color="error"
      text="globalAlert.globalConfigurationInvalid"
    />

    <XrdBanner
      v-if="isAllowedToLoginToken && showSoftTokenPinEnteredAlert"
      icon="error"
      color="error"
      text="globalAlert.softTokenPinNotEntered"
    >
      <template v-if="showKeysPageLink" #actions>
        <XrdBtn data-test="global-alert-soft-token-pin" variant="text" text="keys.logIn" @click="navigateToKeysPage()" />
      </template>
    </XrdBanner>

    <XrdBanner v-if="showRestoreInProgress" data-test="global-alert-restore" icon="error" color="error">
      <i18n-t scope="global" keypath="globalAlert.backupRestoreInProgress">
        <template #startTime>
          <XrdDateTime :value="restoreStartTime" />
        </template>
      </i18n-t>
    </XrdBanner>

    <XrdBanner v-if="isSecondaryNode" data-test="global-alert-secondary-node" icon="error" color="error" text="globalAlert.secondaryNode" />

    <XrdBanner v-if="showCertificateRenewalJobFailureAlert" data-test="global-alert-certificate-renewal-failure" icon="error" color="error">
      <span class="alert-text">
        {{ $t('globalAlert.certificateRenewalJobFailure') }}
      </span>

      <p v-if="authCertificateIdsWithErrors.length > 0">
        {{ `${$t('globalAlert.certificateRenewalJobFailureAuth')} ${failedAuthCertIds}` }}
      </p>
      <p v-if="signCertificateIdsWithErrors.length > 0">
        {{ `${$t('globalAlert.certificateRenewalJobFailureSign')} ${failedSignCertIds}` }}
      </p>
      <template v-if="showKeysPageLink" #actions>
        <XrdBtn variant="text" text="globalAlert.navigateToKeysPage" @click="navigateToKeysPage()" />
      </template>
    </XrdBanner>
  </v-container>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { mapState } from 'pinia';

import { XrdDateTime, XrdBanner, XrdBtn } from '@niis/shared-ui';

import { Permissions, RouteName } from '@/global';
import { useAlerts } from '@/store/modules/alerts';
import { useSystem } from '@/store/modules/system';
import { useUser } from '@/store/modules/user';

export default defineComponent({
  components: { XrdDateTime, XrdBanner, XrdBtn },
  computed: {
    ...mapState(useAlerts, [
      'showGlobalConfAlert',
      'showSoftTokenPinEnteredAlert',
      'showRestoreInProgress',
      'showCertificateRenewalJobFailureAlert',
      'restoreStartTime',
      'authCertificateIdsWithErrors',
      'signCertificateIdsWithErrors',
    ]),
    ...mapState(useSystem, ['isSecondaryNode']),
    ...mapState(useUser, ['username', 'authenticated', 'needsInitialization', 'hasPermission']),
    hasAlerts(): boolean {
      return (
        this.showGlobalConfAlert ||
        this.showSoftTokenPinEnteredAlert ||
        this.showRestoreInProgress ||
        this.showCertificateRenewalJobFailureAlert ||
        this.isSecondaryNode
      );
    },

    showKeysPageLink(): boolean {
      return this.$route.name !== RouteName.SignAndAuthKeys;
    },
    isAllowedToLoginToken(): boolean {
      return this.hasPermission(Permissions.ACTIVATE_DEACTIVATE_TOKEN);
    },
    failedAuthCertIds(): string {
      return this.authCertificateIdsWithErrors.join(', ');
    },
    failedSignCertIds(): string {
      return this.signCertificateIdsWithErrors.join(', ');
    },
  },
  methods: {
    navigateToKeysPage(): void {
      this.$router.replace({ name: RouteName.SignAndAuthKeys });
    },
  },
});
</script>

<style scoped lang="scss"></style>
