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
  <v-layout align-center justify-center class="mt-6">
    <div class="view-wrap">
      <xrd-sub-view-title
        class="wizard-view-title"
        :title="$t('initialConfiguration.title')"
        :show-close="false"
        data-test="wizard-title"
      />
      <v-stepper
        v-model="currentStep"
        :alt-labels="true"
        class="wizard-stepper wizard-noshadow"
      >
        <!-- Headers without anchor page -->
        <v-stepper-header v-if="isAnchorImported" class="wizard-noshadow">
          <v-stepper-step :complete="currentStep > 1" step="1">{{
            $t('initialConfiguration.member.title')
          }}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 2" step="2">{{
            $t('initialConfiguration.pin.title')
          }}</v-stepper-step>
        </v-stepper-header>
        <!-- Headers with anchor page -->
        <v-stepper-header v-else class="wizard-noshadow">
          <v-stepper-step :complete="currentStep > 1" step="1">{{
            $t('initialConfiguration.anchor.title')
          }}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 2" step="2">{{
            $t('initialConfiguration.member.title')
          }}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 3" step="3">{{
            $t('initialConfiguration.pin.title')
          }}</v-stepper-step>
        </v-stepper-header>

        <v-stepper-items v-if="isAnchorImported" class="wizard-stepper-content">
          <!-- Member step -->
          <v-stepper-content step="1">
            <OwnerMemberStep :show-previous-button="false" @done="nextStep" />
          </v-stepper-content>
          <!-- PIN step -->
          <v-stepper-content step="2">
            <TokenPinStep @previous="currentStep = 1" @done="tokenPinReady" />
          </v-stepper-content>
        </v-stepper-items>

        <v-stepper-items v-else class="wizard-stepper-content">
          <!-- Anchor step -->
          <v-stepper-content step="1">
            <ConfigurationAnchorStep @done="nextStep" />
          </v-stepper-content>
          <!-- Member step -->
          <v-stepper-content step="2">
            <OwnerMemberStep @previous="currentStep = 1" @done="nextStep" />
          </v-stepper-content>
          <!-- PIN step -->
          <v-stepper-content step="3">
            <TokenPinStep
              :save-busy="pinSaveBusy"
              @previous="currentStep = 2"
              @done="tokenPinReady"
            />
          </v-stepper-content>
        </v-stepper-items>
      </v-stepper>

      <!-- Confirm dialog for warnings when initializing server -->
      <warningDialog
        :dialog="confirmInitWarning"
        :warnings="warningInfo"
        localization-parent="initialConfiguration.warning"
        @cancel="confirmInitWarning = false"
        @accept="acceptInitWarning()"
      ></warningDialog>
    </div>
  </v-layout>
</template>

<script lang="ts">
import Vue from 'vue';

import TokenPinStep from './TokenPinStep.vue';
import ConfigurationAnchorStep from './ConfigurationAnchorStep.vue';
import WarningDialog from '@/components/ui/WarningDialog.vue';
import OwnerMemberStep from './OwnerMemberStep.vue';
import * as api from '@/util/api';
import { InitialServerConf } from '@/openapi-types';
import { mapActions, mapState } from 'pinia';
import { useAlerts } from '@/store/modules/alerts';
import { useUser } from '@/store/modules/user';
import { useNotifications } from '@/store/modules/notifications';
import { useInitializeServer } from '@/store/modules/initializeServer';

export default Vue.extend({
  components: {
    TokenPinStep,
    ConfigurationAnchorStep,
    OwnerMemberStep,
    WarningDialog,
  },
  data() {
    return {
      currentStep: 1 as number,
      pinSaveBusy: false as boolean,
      warningInfo: [] as string[],
      confirmInitWarning: false as boolean,
      requestPayload: {} as InitialServerConf,
    };
  },
  computed: {
    ...mapState(useUser, [
      'isAnchorImported',
      'isServerOwnerInitialized',
      'isServerCodeInitialized',
      'firstAllowedTab',
    ]),
    ...mapState(useInitializeServer, [
      'initServerSSCode',
      'initServerMemberClass',
      'initServerMemberCode',
    ]),
  },

  created() {
    this.fetchInitializationStatus().catch((error) => {
      this.showError(error);
    });
  },
  methods: {
    ...mapActions(useAlerts, ['checkAlertStatus']),
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useUser, [
      'setInitializationStatus',
      'fetchInitializationStatus',
      'fetchCurrentSecurityServer',
    ]),
    nextStep(): void {
      this.currentStep++;
    },

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

    initServer(payload: InitialServerConf): void {
      api
        .post('/initialization', payload)
        .then(() => {
          this.showSuccess(this.$t('initialConfiguration.success'));
          // Set init state to done so that the routing goes into "normal" mode
          this.setInitializationStatus();
          this.pinSaveBusy = false;
          this.fetchCurrentSecurityServer();
          this.checkAlertStatus(); // Check if we have any alerts after initialisation
          this.$router.replace({
            name: this.firstAllowedTab.to.name,
          });
        })
        .catch((error) => {
          if (error?.response?.data?.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.confirmInitWarning = true;
          } else {
            this.showError(error);
            this.pinSaveBusy = false;
          }
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/wizards';
</style>
