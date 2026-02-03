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
  <XrdElevatedViewSimple id="initial-configuration-v2" title="initialConfigurationV2.title" class="overflow-hidden">
    <v-progress-linear v-if="loading" indeterminate />

    <XrdWizard v-if="!loading" v-model="currentStep">
      <!-- Headers -->
      <template #header-items>
        <template v-if="showAnchorStep">
          <v-stepper-item :complete="currentStep > anchorStepNum" :value="anchorStepNum">
            {{ $t('initialConfiguration.anchor.title') }}
          </v-stepper-item>
          <v-divider />
        </template>
        <v-stepper-item :complete="currentStep > serverConfStepNum" :value="serverConfStepNum">
          {{ $t('initialConfigurationV2.serverConf.title') }}
        </v-stepper-item>
        <v-divider />
        <v-stepper-item :complete="currentStep > softTokenStepNum" :value="softTokenStepNum">
          {{ $t('initialConfiguration.pin.title') }}
        </v-stepper-item>
        <v-divider />
        <v-stepper-item :complete="currentStep > autoInitStepNum" :value="autoInitStepNum">
          {{ $t('initialConfigurationV2.autoInit.title') }}
        </v-stepper-item>
      </template>

      <template #default="{ nextStep, previousStep }">
        <v-stepper-window-item v-if="showAnchorStep" :value="anchorStepNum">
          <ConfigurationAnchorStep :value="anchorStepNum" @done="nextStep" />
        </v-stepper-window-item>

        <v-stepper-window-item :value="serverConfStepNum">
          <ServerConfStep :show-previous-button="showAnchorStep" @previous="previousStep" @done="nextStep" />
        </v-stepper-window-item>

        <v-stepper-window-item :value="softTokenStepNum">
          <SoftTokenStep @previous="previousStep" @done="nextStep" />
        </v-stepper-window-item>

        <v-stepper-window-item :value="autoInitStepNum">
          <AutoInitStep @done="onAllDone" />
        </v-stepper-window-item>
      </template>
    </XrdWizard>
  </XrdElevatedViewSimple>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { mapActions, mapState } from 'pinia';

import { useNotifications, XrdElevatedViewSimple, XrdWizard } from '@niis/shared-ui';

import { useAlerts } from '@/store/modules/alerts';
import { useInitializationV2 } from '@/store/modules/initializationV2';
import { useUser } from '@/store/modules/user';
import { useMainTabs } from '@/store/modules/main-tabs';

import ConfigurationAnchorStep from './ConfigurationAnchorStep.vue';
import ServerConfStep from './ServerConfStep.vue';
import SoftTokenStep from './SoftTokenStep.vue';
import AutoInitStep from './AutoInitStep.vue';

export default defineComponent({
  components: {
    ConfigurationAnchorStep,
    ServerConfStep,
    SoftTokenStep,
    AutoInitStep,
    XrdElevatedViewSimple,
    XrdWizard,
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    return {
      currentStep: 1,
      loading: true,
      // Captured once on load so the stepper layout doesn't shift mid-wizard
      showAnchorStep: false,
    };
  },
  computed: {
    ...mapState(useMainTabs, ['firstAllowedTab']),
    ...mapState(useInitializationV2, ['anchorImported', 'overallStatus', 'isFullyInitialized']),

    anchorStepNum(): number {
      return this.showAnchorStep ? 1 : 0;
    },
    serverConfStepNum(): number {
      return this.anchorStepNum + 1;
    },
    softTokenStepNum(): number {
      return this.serverConfStepNum + 1;
    },
    autoInitStepNum(): number {
      return this.softTokenStepNum + 1;
    },
  },
  async created() {
    try {
      await this.fetchStatus();
      this.showAnchorStep = !this.anchorImported;
      this.skipCompletedSteps();
    } catch (error) {
      this.addError(error);
    } finally {
      this.loading = false;
    }
  },
  methods: {
    ...mapActions(useInitializationV2, ['fetchStatus']),
    ...mapActions(useAlerts, ['checkAlertStatus']),
    ...mapActions(useUser, ['setInitializationStatus', 'fetchCurrentSecurityServer']),

    skipCompletedSteps(): void {
      if (this.isFullyInitialized) {
        this.$router.replace(this.firstAllowedTab.to);
        return;
      }

      // If anchor is not imported, start at anchor step
      if (this.showAnchorStep) {
        this.currentStep = this.anchorStepNum;
        return;
      }

      const store = useInitializationV2();

      if (store.getStepStatus('SERVERCONF') === 'COMPLETED') {
        if (store.getStepStatus('SOFTTOKEN') === 'COMPLETED') {
          this.currentStep = this.autoInitStepNum;
        } else {
          this.currentStep = this.softTokenStepNum;
        }
      } else {
        this.currentStep = this.serverConfStepNum;
      }
    },

    onAllDone(): void {
      this.addSuccessMessage('initialConfigurationV2.success');
      this.setInitializationStatus();
      this.fetchCurrentSecurityServer();
      this.checkAlertStatus();
      this.$router.replace(this.firstAllowedTab.to);
    },
  },
});
</script>

<style lang="scss" scoped></style>
