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
  <XrdElevatedViewSimple id="initial-configuration" title="initialConfiguration.title" class="overflow-hidden">
    <XrdWizard v-model="currentStep">
      <!-- Headers -->
      <template #header-items>
        <template v-if="anchorStep">
          <v-stepper-item :complete="currentStep > anchorStep" :value="anchorStep">
            {{ $t('initialConfiguration.anchor.title') }}
          </v-stepper-item>
          <v-divider></v-divider>
        </template>
        <v-stepper-item :complete="currentStep > memberStep" :value="memberStep">
          {{ $t('initialConfiguration.member.title') }}
        </v-stepper-item>
        <v-divider></v-divider>
        <v-stepper-item :complete="currentStep > pinStep" :value="pinStep">
          {{ $t('initialConfiguration.pin.title') }}
        </v-stepper-item>
      </template>
      <template #default="{ nextStep, previousStep }">
        <v-stepper-window-item :value="anchorStep">
          <ConfigurationAnchorStep v-if="anchorStep" :value="anchorStep" @done="nextStep" />
        </v-stepper-window-item>

        <v-stepper-window-item :value="memberStep">
          <OwnerMemberStep :value="memberStep" @previous="previousStep" @done="nextStep" />
        </v-stepper-window-item>
        <v-stepper-window-item :value="pinStep">
          <TokenPinStep :save-busy="pinSaveBusy" @previous="previousStep" @done="tokenPinReady" />
        </v-stepper-window-item>
      </template>
    </XrdWizard>

    <!-- Confirm dialog for warnings when initializing server -->
    <WarningDialog
      v-if="confirmInitWarning"
      :warnings="warningInfo"
      localization-parent="initialConfiguration.warning"
      @cancel="cancelInitWarning"
      @accept="acceptInitWarning"
    />
  </XrdElevatedViewSimple>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { mapActions, mapState } from 'pinia';

import { useNotifications, XrdElevatedViewSimple, XrdWizard } from '@niis/shared-ui';

import { CodeWithDetails, InitialServerConf } from '@/openapi-types';
import { useAlerts } from '@/store/modules/alerts';
import { useInitializeServer } from '@/store/modules/initializeServer';
import { useUser } from '@/store/modules/user';
import * as api from '@/util/api';

import ConfigurationAnchorStep from './ConfigurationAnchorStep.vue';
import OwnerMemberStep from './OwnerMemberStep.vue';
import TokenPinStep from './TokenPinStep.vue';
import WarningDialog from '@/components/ui/WarningDialog.vue';
import { useMainTabs } from '@/store/modules/main-tabs';

export default defineComponent({
  components: {
    TokenPinStep,
    ConfigurationAnchorStep,
    OwnerMemberStep,
    WarningDialog,
    XrdElevatedViewSimple,
    XrdWizard,
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    return {
      currentStep: 1 as number,
      pinSaveBusy: false as boolean,
      warningInfo: [] as CodeWithDetails[],
      confirmInitWarning: false as boolean,
      requestPayload: {} as InitialServerConf,
    };
  },
  computed: {
    ...mapState(useMainTabs, ['firstAllowedTab']),
    ...mapState(useUser, ['isAnchorImported', 'isServerOwnerInitialized', 'isServerCodeInitialized']),
    ...mapState(useInitializeServer, ['initServerSSCode', 'initServerMemberClass', 'initServerMemberCode']),

    anchorStep() {
      return this.isAnchorImported ? 0 : 1;
    },
    memberStep() {
      return this.anchorStep + 1;
    },
    pinStep() {
      return this.memberStep + 1;
    },
  },

  created() {
    this.fetchInitializationStatus().catch((error) => {
      this.addError(error);
    });
  },
  methods: {
    ...mapActions(useAlerts, ['checkAlertStatus']),
    ...mapActions(useUser, ['setInitializationStatus', 'fetchInitializationStatus', 'fetchCurrentSecurityServer']),
    tokenPinReady(pin: string): void {
      this.pinSaveBusy = true;

      this.requestPayload = {
        software_token_pin: pin,
        ignore_warnings: false,
      };

      // If owner member is not already set up add it
      if (!this.isServerOwnerInitialized) {
        this.requestPayload.owner_member_class = this.initServerMemberClass;
        this.requestPayload.owner_member_code = this.initServerMemberCode;
      }

      // Add security code if it's not already set up
      if (!this.isServerCodeInitialized) {
        this.requestPayload.security_server_code = this.initServerSSCode;
      }

      this.initServer(this.requestPayload);
    },

    acceptInitWarning(): void {
      this.requestPayload.ignore_warnings = true;
      this.confirmInitWarning = false;
      this.initServer(this.requestPayload);
    },

    cancelInitWarning(): void {
      this.confirmInitWarning = false;
      this.pinSaveBusy = false;
    },

    initServer(payload: InitialServerConf): void {
      api
        .post('/initialization', payload)
        .then(() => {
          this.addSuccessMessage('initialConfiguration.success');
          // Set init state to done so that the routing goes into "normal" mode
          this.setInitializationStatus();
          this.pinSaveBusy = false;
          this.fetchCurrentSecurityServer();
          this.checkAlertStatus(); // Check if we have any alerts after initialisation
          this.$router.replace(this.firstAllowedTab.to);
        })
        .catch((error) => {
          if (error?.response?.data?.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.confirmInitWarning = true;
          } else {
            this.addError(error);
            this.pinSaveBusy = false;
          }
        });
    },
  },
});
</script>

<style lang="scss" scoped></style>
