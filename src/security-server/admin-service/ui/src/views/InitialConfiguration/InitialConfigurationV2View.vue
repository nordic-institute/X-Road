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
          {{ $t('initialConfiguration.serverConf.title') }}
        </v-stepper-item>
        <v-divider />
        <v-stepper-item :complete="currentStep > softTokenStepNum" :value="softTokenStepNum">
          {{ $t('initialConfiguration.pin.title') }}
        </v-stepper-item>
        <v-divider />
        <v-stepper-item :complete="currentStep > autoInitStepNum" :value="autoInitStepNum">
          {{ $t('initialConfiguration.autoInit.title') }}
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

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { storeToRefs } from 'pinia';

import { useNotifications, XrdElevatedViewSimple, XrdWizard } from '@niis/shared-ui';

import { useAlerts } from '@/store/modules/alerts';
import { useInitializationV2 } from '@/store/modules/initializationV2';
import { useUser } from '@/store/modules/user';
import { useMainTabs } from '@/store/modules/main-tabs';

import ConfigurationAnchorStep from './ConfigurationAnchorStep.vue';
import ServerConfStep from './ServerConfStep.vue';
import SoftTokenStep from './SoftTokenStep.vue';
import AutoInitStep from './AutoInitStep.vue';

const router = useRouter();
const { addError, addSuccessMessage } = useNotifications();

const mainTabsStore = useMainTabs();
const { firstAllowedTab } = storeToRefs(mainTabsStore);

const initV2Store = useInitializationV2();
const { anchorImported, isFullyInitialized } = storeToRefs(initV2Store);
const { fetchStatus } = initV2Store;

const { checkAlertStatus } = useAlerts();
const { setInitializationStatus, fetchCurrentSecurityServer } = useUser();

const currentStep = ref(1);
const loading = ref(true);
// Captured once on load so the stepper layout doesn't shift mid-wizard
const showAnchorStep = ref(false);

const anchorStepNum = computed(() => (showAnchorStep.value ? 1 : 0));
const serverConfStepNum = computed(() => anchorStepNum.value + 1);
const softTokenStepNum = computed(() => serverConfStepNum.value + 1);
const autoInitStepNum = computed(() => softTokenStepNum.value + 1);

function skipCompletedSteps(): void {
  if (isFullyInitialized.value) {
    router.replace(firstAllowedTab.value.to);
    return;
  }

  // If anchor is not imported, start at anchor step
  if (showAnchorStep.value) {
    currentStep.value = anchorStepNum.value;
    return;
  }

  if (initV2Store.getStepStatus('SERVERCONF') === 'COMPLETED') {
    if (initV2Store.getStepStatus('SOFTTOKEN') === 'COMPLETED') {
      currentStep.value = autoInitStepNum.value;
    } else {
      currentStep.value = softTokenStepNum.value;
    }
  } else {
    currentStep.value = serverConfStepNum.value;
  }
}

function onAllDone(): void {
  addSuccessMessage('initialConfiguration.success');
  setInitializationStatus();
  fetchCurrentSecurityServer();
  checkAlertStatus();
  router.replace(firstAllowedTab.value.to);
}

// Initialize on creation
try {
  await fetchStatus();
  showAnchorStep.value = !anchorImported.value;
  skipCompletedSteps();
} catch (error) {
  addError(error);
} finally {
  loading.value = false;
}
</script>

<style lang="scss" scoped></style>
