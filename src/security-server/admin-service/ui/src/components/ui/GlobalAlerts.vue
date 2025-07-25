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
  <v-container
    v-if="authenticated && !needsInitialization && hasAlerts"
    fluid
    class="alerts-container px-3"
  >
    <v-alert
      v-if="showGlobalConfAlert"
      data-test="global-alert-global-configuration"
      variant="outlined"
      border="start"
      class="alert"
      icon="icon-Error-notification"
      type="error"
    >
      <span class="alert-text">{{
        $t('globalAlert.globalConfigurationInvalid')
      }}</span>
    </v-alert>
    <v-alert
      v-if="isAllowedToLoginToken && showSoftTokenPinEnteredAlert"
      data-test="global-alert-soft-token-pin"
      variant="outlined"
      border="start"
      class="alert"
      icon="icon-Error-notification"
      type="error"
    >
      <span
        v-if="showKeysPageLink"
        class="alert-text clickable-link"
        @click="navigateToKeysPage()"
      >
        {{ $t('globalAlert.softTokenPinNotEntered') }}
      </span>
      <span v-else class="alert-text">
        {{ $t('globalAlert.softTokenPinNotEntered') }}
      </span>
    </v-alert>
    <v-alert
      v-if="showRestoreInProgress"
      data-test="global-alert-restore"
      variant="outlined"
      border="start"
      class="alert"
      icon="icon-Error-notification"
      type="error"
    >
      <span class="alert-text">{{
        $t('globalAlert.backupRestoreInProgress', {
          startTime: $filters.formatDateTime(restoreStartTime),
        })
      }}</span>
    </v-alert>
    <v-alert
      v-if="isSecondaryNode"
      data-test="global-alert-secondary-node"
      variant="outlined"
      border="start"
      class="alert"
      icon="icon-Error-notification"
      type="error"
    >
      <span class="alert-text">{{ $t('globalAlert.secondaryNode') }}</span>
    </v-alert>
    <v-alert
      v-if="showCertificateRenewalJobFailureAlert"
      data-test="global-alert-certificate-renewal-failure"
      variant="outlined"
      border="start"
      class="alert"
      icon="icon-Error-notification"
      type="error"
    >
      <span class="alert-text">
        {{ $t('globalAlert.certificateRenewalJobFailure') }}
      </span>
      <span
        v-if="showKeysPageLink"
        class="alert-text clickable-link"
        @click="navigateToKeysPage()"
        >{{ $t('globalAlert.navigateToKeysPage') }}</span
      >
      <span v-if="authCertificateIdsWithErrors.length > 0" class="alert-text">{{
        `${$t('globalAlert.certificateRenewalJobFailureAuth')} ${failedAuthCertIds}`
      }}</span>
      <span v-if="signCertificateIdsWithErrors.length > 0" class="alert-text">{{
        `${$t('globalAlert.certificateRenewalJobFailureSign')} ${failedSignCertIds}`
      }}</span>
    </v-alert>
  </v-container>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { mapState } from 'pinia';
import { useAlerts } from '@/store/modules/alerts';
import { useSystem } from '@/store/modules/system';
import { useUser } from '@/store/modules/user';
import { Permissions, RouteName } from '@/global';

export default defineComponent({
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
    ...mapState(useUser, [
      'authenticated',
      'needsInitialization',
      'hasPermission',
    ]),
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

<style scoped lang="scss">
@use '@niis/shared-ui/src/assets/colors';

.alerts-container {
  padding: 0;

  & > * {
    margin-bottom: 4px;
    border-radius: 0;
  }

  & > :first-child {
    margin-top: 16px;
  }
}

.alert {
  margin-top: 16px;
  border: 2px solid colors.$WarmGrey30;
  box-sizing: border-box;
  border-radius: 4px;
  background-color: colors.$White100;
}

.alert-text {
  color: colors.$Black100;
  display: block;
}

.clickable-link {
  text-decoration: underline;
  cursor: pointer;
}
</style>
