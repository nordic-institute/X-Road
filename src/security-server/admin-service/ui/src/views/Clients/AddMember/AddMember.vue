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
  <XrdElevatedViewSimple
    title="wizard.addMemberTitle"
    closeable
    @close="cancel"
  >
    <XrdWizard v-model="currentStep">
      <template #header-items>
        <v-stepper-item :complete="currentStep > 1" :value="1">
          {{ $t('wizard.member.title') }}
        </v-stepper-item>
        <v-divider />

        <template v-if="isModeFull">
          <v-stepper-item
            :complete="currentStep > tokenPageNumber"
            :value="tokenPageNumber"
          >
            {{ $t('wizard.token.title') }}
          </v-stepper-item>
          <v-divider />

          <v-stepper-item
            :complete="currentStep > keyPageNumber"
            :value="keyPageNumber"
          >
            {{ $t('wizard.signKey.title') }}
          </v-stepper-item>
          <v-divider />
        </template>
        <template v-if="isModeFull || isModeCsrExists">
          <v-stepper-item
            :complete="currentStep > csrDetailsPageNumber"
            :value="csrDetailsPageNumber"
          >
            {{ $t('csr.csrDetails') }}
          </v-stepper-item>
          <v-divider />

          <v-stepper-item
            :complete="currentStep > csrGeneratePageNumber"
            :value="csrGeneratePageNumber"
          >
            {{ $t('csr.generateCsr') }}
          </v-stepper-item>
          <v-divider />
        </template>

        <v-stepper-item :value="finishPageNumber">
          {{ $t('wizard.finish.title') }}
        </v-stepper-item>
      </template>

      <!-- Step 1 -->
      <v-stepper-window-item :value="1">
        <MemberDetailsPage :value="1" @cancel="cancel" @done="currentStep++" />
      </v-stepper-window-item>
      <template v-if="isModeFull">
        <!-- Step 2 -->
        <v-stepper-window-item :value="tokenPageNumber">
          <TokenPage
            @cancel="cancel"
            @previous="currentStep--"
            @done="currentStep++"
          />
        </v-stepper-window-item>
        <!-- Step 3 -->
        <v-stepper-window-item :value="keyPageNumber">
          <SignKeyPage
            @cancel="cancel"
            @previous="currentStep--"
            @done="currentStep++"
          />
        </v-stepper-window-item>
      </template>
      <template v-if="isModeFull || isModeCsrExists">
        <!-- Step 4 -->
        <v-stepper-window-item :value="csrDetailsPageNumber">
          <CsrDetailsPageLocked
            save-button-text="action.next"
            :loading="loadingCsrForm"
            @cancel="cancel"
            @previous="currentStep--"
            @done="csrDetailsReady"
          />
        </v-stepper-window-item>
        <!-- Step 5 -->
        <v-stepper-window-item :value="csrGeneratePageNumber">
          <GenerateCsrPage
            v-if="csrStore.csrFormReady"
            save-button-text="action.next"
            @cancel="cancel"
            @previous="currentStep--"
            @done="currentStep++"
          />
        </v-stepper-window-item>
      </template>
      <!-- Step 6 -->
      <v-stepper-window-item :value="finishPageNumber">
        <FinishPage @cancel="cancel" @previous="currentStep--" @done="done" />
      </v-stepper-window-item>
    </XrdWizard>
  </XrdElevatedViewSimple>
</template>

<script lang="ts" setup>
import { computed, onBeforeMount, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import MemberDetailsPage from './MemberDetailsPage.vue';
import TokenPage from '@/components/wizard/TokenPage.vue';
import SignKeyPage from '@/components/wizard/SignKeyPage.vue';
import FinishPage from './FinishPage.vue';
import CsrDetailsPageLocked from '@/components/wizard/CsrDetailsPageLocked.vue';
import GenerateCsrPage from '@/components/wizard/GenerateCsrPage.vue';
import { AddMemberWizardModes, RouteName } from '@/global';
import { useAddClient } from '@/store/modules/addClient';
import { useCsr } from '@/store/modules/certificateSignRequest';
import { useGeneral } from '@/store/modules/general';
import {
  XrdElevatedViewSimple,
  XrdWizard,
  useNotifications,
} from '@niis/shared-ui';

const NO_SELECTION = 999;

const props = defineProps({
  ownerInstanceId: {
    type: String,
    required: true,
  },
  ownerMemberClass: {
    type: String,
    required: true,
  },
  ownerMemberCode: {
    type: String,
    required: true,
  },
});

const router = useRouter();
const { addError } = useNotifications();
const addClientStore = useAddClient();
const { fetchMemberClassesForCurrentInstance } = useGeneral();
const csrStore = useCsr();
const {
  storeCsrClient,
  storeCsrIsNewMember,
  resetCsrState,
  fetchCsrForm,
  setCsrForm,
  fetchCertificateAuthorities,
  setupSignKey,
} = csrStore;

const currentStep = ref(1);
const isModeFull = computed(
  () => addClientStore.addMemberWizardMode === AddMemberWizardModes.FULL,
);

const isModeCsrExists = computed(
  () => addClientStore.addMemberWizardMode === AddMemberWizardModes.CSR_EXISTS,
);

const tokenPageNumber = computed(() => (isModeFull.value ? 2 : NO_SELECTION));

const keyPageNumber = computed(() => (isModeFull.value ? 3 : NO_SELECTION));

const csrDetailsPageNumber = computed(() => {
  if (isModeFull.value) {
    return 4;
  } else if (isModeCsrExists.value) {
    return 2;
  }
  return NO_SELECTION;
});
const csrGeneratePageNumber = computed(() => {
  if (isModeFull.value) {
    return 5;
  } else if (isModeCsrExists.value) {
    return 3;
  }
  return NO_SELECTION;
});

const finishPageNumber = computed(() => {
  if (isModeFull.value) {
    return 6;
  } else if (isModeCsrExists.value) {
    return 4;
  }
  return 2;
});

function cancel(): void {
  router.replace({ name: RouteName.Clients });
}

const loadingCsrForm = ref(false);

function csrDetailsReady(): void {
  // Add the selected client id in csr store
  const idString = addClientStore.selectedMemberId;
  storeCsrClient(idString);
  storeCsrIsNewMember(true);

  loadingCsrForm.value = true;
  setCsrForm([]);
  fetchCsrForm()
    .then(() => currentStep.value++)
    .catch((error) => addError(error))
    .finally(() => (loadingCsrForm.value = false));
}

function done(): void {
  router.replace({ name: RouteName.Clients });
}

onMounted(() => {
  // Set up the CSR part with Sign mode
  setupSignKey();

  fetchCertificateAuthorities().catch((error) => addError(error));

  fetchMemberClassesForCurrentInstance();

  // Store the reserved member info to store
  addClientStore.storeReservedMember({
    instanceId: props.ownerInstanceId,
    memberClass: props.ownerMemberClass,
    memberCode: props.ownerMemberCode,
  });
});

onBeforeMount(() => {
  addClientStore.resetAddClientState();
  resetCsrState();
});
</script>

<style lang="scss" scoped></style>
