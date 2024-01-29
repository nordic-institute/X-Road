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
  <div class="view-wrap">
    <xrd-sub-view-title
      class="wizard-view-title"
      :title="$t('wizard.addClientTitle')"
      :show-close="false"
      data-test="wizard-title"
    />
    <!-- eslint-disable-next-line vuetify/no-deprecated-components -->
    <v-stepper
      v-model="currentStep"
      :alt-labels="true"
      class="wizard-stepper wizard-noshadow"
    >
      <v-stepper-header class="wizard-noshadow">
        <v-stepper-item :complete="currentStep > 1" :value="1">
          {{ $t('wizard.clientDetails') }}
        </v-stepper-item>
        <v-divider></v-divider>

        <v-stepper-item
          v-if="isModeFull"
          :complete="currentStep > tokenPageNumber"
          :value="tokenPageNumber"
        >
          {{ $t('wizard.token.title') }}
        </v-stepper-item>
        <v-divider v-if="isModeFull"></v-divider>

        <v-stepper-item
          v-if="isModeFull"
          :complete="currentStep > keyPageNumber"
          :value="keyPageNumber"
        >
          {{ $t('wizard.signKey.title') }}
        </v-stepper-item>
        <v-divider v-if="isModeFull"></v-divider>

        <v-stepper-item
          v-if="isModeFull || isModeCsrExist"
          :complete="currentStep > csrDetailsPageNumber"
          :value="csrDetailsPageNumber"
        >
          {{ $t('csr.csrDetails') }}
        </v-stepper-item>
        <v-divider v-if="isModeFull || isModeCsrExist"></v-divider>

        <v-stepper-item
          v-if="isModeFull || isModeCsrExist"
          :complete="currentStep > csrGeneratePageNumber"
          :value="csrGeneratePageNumber"
        >
          {{ $t('csr.generateCsr') }}
        </v-stepper-item>
        <v-divider v-if="isModeFull || isModeCsrExist"></v-divider>

        <v-stepper-item :value="finishPageNumber">
          {{ $t('wizard.finish.title') }}
        </v-stepper-item>
      </v-stepper-header>

      <v-stepper-window class="wizard-stepper-content">
        <!-- Step 1 -->
        <v-stepper-window-item :value="1">
          <ClientDetailsPage @cancel="cancel" @done="currentStep++" />
        </v-stepper-window-item>
        <!-- Step 2 -->
        <v-stepper-window-item v-if="isModeFull" :value="tokenPageNumber">
          <TokenPage
            @cancel="cancel"
            @previous="currentStep--"
            @done="currentStep++"
          />
        </v-stepper-window-item>
        <!-- Step 3 -->
        <v-stepper-window-item v-if="isModeFull" :value="keyPageNumber">
          <SignKeyPage
            @cancel="cancel"
            @previous="currentStep--"
            @done="currentStep++"
          />
        </v-stepper-window-item>
        <!-- Step 4 -->
        <v-stepper-window-item
          v-if="isModeFull || isModeCsrExist"
          :value="csrDetailsPageNumber"
        >
          <CsrDetailsPageLocked
            save-button-text="action.next"
            @cancel="cancel"
            @previous="currentStep--"
            @done="csrDetailsReady"
          />
        </v-stepper-window-item>
        <!-- Step 5 -->
        <v-stepper-window-item
          v-if="isModeFull || isModeCsrExist"
          :value="csrGeneratePageNumber"
        >
          <GenerateCsrPage
            save-button-text="action.next"
            @cancel="cancel"
            @previous="currentStep--"
            @done="currentStep++"
          />
        </v-stepper-window-item>
        <!-- Step 6 -->
        <v-stepper-window-item :value="finishPageNumber">
          <FinishPage @cancel="cancel" @previous="currentStep--" @done="done" />
        </v-stepper-window-item>
      </v-stepper-window>
    </v-stepper>
  </div>
</template>
<script lang="ts" setup>
import { computed, onBeforeMount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import ClientDetailsPage from './ClientDetailsPage.vue';
import TokenPage from '@/components/wizard/TokenPage.vue';
import SignKeyPage from '@/components/wizard/SignKeyPage.vue';
import FinishPage from './FinishPage.vue';
import CsrDetailsPageLocked from '@/components/wizard/CsrDetailsPageLocked.vue';
import GenerateCsrPage from '@/components/wizard/GenerateCsrPage.vue';
import { AddMemberWizardModes, RouteName } from '@/global';
import { useAddClient } from '@/store/modules/addClient';
import { useNotifications } from '@/store/modules/notifications';
import { useCsr } from '@/store/modules/certificateSignRequest';
import {
  VStepper,
  VStepperHeader,
  VStepperItem,
  VStepperWindow,
  VStepperWindowItem,
} from 'vuetify/labs/VStepper';

const NO_SELECTION = 999;

const router = useRouter();
const addClientStore = useAddClient();
const { showError } = useNotifications();
const { resetAddClientState } = useAddClient();
const {
  setupSignKey,
  resetCsrState,
  fetchCertificateAuthorities,
  storeCsrClient,
  storeCsrIsNewMember,
  fetchCsrForm,
} = useCsr();

const currentStep = ref(1);
const isModeFull = computed(
  () => addClientStore.addMemberWizardMode === AddMemberWizardModes.FULL,
);
const isModeCsrExist = computed(
  () => addClientStore.addMemberWizardMode === AddMemberWizardModes.CSR_EXISTS,
);

const tokenPageNumber = computed(() => (isModeFull.value ? 2 : NO_SELECTION));
const keyPageNumber = computed(() => (isModeFull.value ? 3 : NO_SELECTION));
const csrDetailsPageNumber = computed(() => {
  if (isModeFull.value) {
    return 4;
  } else if (isModeCsrExist.value) {
    return 2;
  }
  return NO_SELECTION;
});
const csrGeneratePageNumber = computed(() => {
  if (isModeFull.value) {
    return 5;
  } else if (isModeCsrExist.value) {
    return 3;
  }
  return NO_SELECTION;
});
const finishPageNumber = computed(() => {
  if (isModeFull.value) {
    return 6;
  } else if (isModeCsrExist.value) {
    return 4;
  }
  return 2;
});

onMounted(() => {
  // Set up the CSR part with Sign mode
  setupSignKey();
  // Fetch certificate authorities. Used in "sign key" step.
  fetchCertificateAuthorities().catch((error) => showError(error));
});

onBeforeMount(() => {
  // Clear the stores used in the wizard
  resetAddClientState();
  resetCsrState();
});

function cancel(): void {
  router.replace({ name: RouteName.Clients });
}

function csrDetailsReady(): void {
  // Add the selected client id in the CSR store
  storeCsrClient(addClientStore.selectedMemberId);
  storeCsrIsNewMember(true);

  fetchCsrForm()
    .then(() => currentStep.value++)
    .catch((error) => showError(error));
}

function done(): void {
  router.replace({ name: RouteName.Clients });
}
</script>


<style lang="scss" scoped>
@import '@/assets/wizards';
</style>
