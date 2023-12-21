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
      :title="$t('wizard.addMemberTitle')"
      data-test="wizard-title"
      :show-close="false"
      @close="cancel()"
    />
    <!-- eslint-disable-next-line vuetify/no-deprecated-components -->
    <v-stepper
      v-model="currentStep"
      :alt-labels="true"
      class="wizard-stepper wizard-noshadow"
    >
      <template v-if="addMemberWizardMode === wizardModes.FULL">
        <v-stepper-header class="wizard-noshadow">
          <v-stepper-item :complete="currentStep > 1" :value="1">{{
            $t('wizard.member.title')
          }}</v-stepper-item>
          <v-divider></v-divider>
          <v-stepper-item :complete="currentStep > 2" :value="2">{{
            $t('wizard.token.title')
          }}</v-stepper-item>
          <v-divider></v-divider>
          <v-stepper-item :complete="currentStep > 3" :value="3">{{
            $t('wizard.signKey.title')
          }}</v-stepper-item>
          <v-divider></v-divider>
          <v-stepper-item :complete="currentStep > 4" :value="4">{{
            $t('csr.csrDetails')
          }}</v-stepper-item>
          <v-divider></v-divider>
          <v-stepper-item :complete="currentStep > 5" :value="5">{{
            $t('csr.generateCsr')
          }}</v-stepper-item>
          <v-divider></v-divider>
          <v-stepper-item :value="6">{{
            $t('wizard.finish.title')
          }}</v-stepper-item>
        </v-stepper-header>
      </template>

      <template v-if="addMemberWizardMode === wizardModes.CERTIFICATE_EXISTS">
        <v-stepper-header class="wizard-noshadow">
          <v-stepper-step :complete="currentStep > 1" :value="1">{{
            $t('wizard.member.title')
          }}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :value="2">{{
            $t('wizard.finish.title')
          }}</v-stepper-step>
        </v-stepper-header>
      </template>

      <template v-if="addMemberWizardMode === wizardModes.CSR_EXISTS">
        <v-stepper-header class="wizard-noshadow">
          <v-stepper-item :complete="currentStep > 1" :value="1">{{
            $t('wizard.member.title')
          }}</v-stepper-item>
          <v-divider></v-divider>
          <v-stepper-item :complete="currentStep > 2" :value="2">{{
            $t('csr.csrDetails')
          }}</v-stepper-item>
          <v-divider></v-divider>
          <v-stepper-item :complete="currentStep > 2" :value="3">{{
            $t('csr.generateCsr')
          }}</v-stepper-item>
          <v-divider></v-divider>
          <v-stepper-item :value="4">{{
            $t('wizard.finish.title')
          }}</v-stepper-item>
        </v-stepper-header>
      </template>

      <v-stepper-window class="wizard-stepper-content">
        <!-- Step 1 -->
        <v-stepper-window-item :value="1">
          <MemberDetailsPage @cancel="cancel" @done="currentStep++" />
        </v-stepper-window-item>
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
        <!-- Step 4 -->
        <v-stepper-window-item :value="csrDetailsPageNumber">
          <CsrDetailsPageLocked
            save-button-text="action.next"
            @cancel="cancel"
            @previous="currentStep--"
            @done="csrDetailsReady"
          />
        </v-stepper-window-item>
        <!-- Step 5 -->
        <v-stepper-window-item :value="csrGeneratePageNumber">
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

<script lang="ts">
import { defineComponent } from 'vue';
import MemberDetailsPage from './MemberDetailsPage.vue';
import TokenPage from '@/components/wizard/TokenPage.vue';
import SignKeyPage from '@/components/wizard/SignKeyPage.vue';
import FinishPage from './FinishPage.vue';
import CsrDetailsPageLocked from '@/components/wizard/CsrDetailsPageLocked.vue';
import GenerateCsrPage from '@/components/wizard/GenerateCsrPage.vue';
import { RouteName, AddMemberWizardModes } from '@/global';
import { mapActions, mapState } from 'pinia';
import { useAddClient } from '@/store/modules/addClient';
import { useNotifications } from '@/store/modules/notifications';
import { useCsr } from '@/store/modules/certificateSignRequest';
import { useGeneral } from '@/store/modules/general';
import {
  VStepper,
  VStepperHeader,
  VStepperItem,
  VStepperWindow,
  VStepperWindowItem,
} from 'vuetify/labs/VStepper';

const NO_SELECTION = 999;

export default defineComponent({
  components: {
    VStepper,
    VStepperHeader,
    VStepperItem,
    VStepperWindow,
    VStepperWindowItem,
    MemberDetailsPage,
    TokenPage,
    SignKeyPage,
    FinishPage,
    CsrDetailsPageLocked,
    GenerateCsrPage,
  },
  props: {
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
  },
  data() {
    return {
      currentStep: 1,
      wizardModes: AddMemberWizardModes,
    };
  },
  computed: {
    ...mapState(useAddClient, ['addMemberWizardMode', 'selectedMemberId']),
    tokenPageNumber(): number {
      if (
        this.addMemberWizardMode === AddMemberWizardModes.CERTIFICATE_EXISTS ||
        this.addMemberWizardMode === AddMemberWizardModes.CSR_EXISTS
      ) {
        return NO_SELECTION;
      }
      return 2;
    },
    keyPageNumber(): number {
      if (
        this.addMemberWizardMode === AddMemberWizardModes.CERTIFICATE_EXISTS ||
        this.addMemberWizardMode === AddMemberWizardModes.CSR_EXISTS
      ) {
        return NO_SELECTION;
      }
      return 3;
    },
    csrDetailsPageNumber(): number {
      switch (this.addMemberWizardMode) {
        case AddMemberWizardModes.CERTIFICATE_EXISTS:
          return NO_SELECTION;
        case AddMemberWizardModes.CSR_EXISTS:
          return 2;
        default:
          return 4;
      }
    },
    csrGeneratePageNumber(): number {
      switch (this.addMemberWizardMode) {
        case AddMemberWizardModes.CERTIFICATE_EXISTS:
          return NO_SELECTION;
        case AddMemberWizardModes.CSR_EXISTS:
          return 3;
        default:
          return 5;
      }
    },
    finishPageNumber(): number {
      switch (this.addMemberWizardMode) {
        case AddMemberWizardModes.CERTIFICATE_EXISTS:
          return 2;
        case AddMemberWizardModes.CSR_EXISTS:
          return 4;
        default:
          return 6;
      }
    },
  },
  created() {
    // Set up the CSR part with Sign mode
    this.setupSignKey();

    this.fetchCertificateAuthorities().catch((error) => {
      this.showError(error);
    });

    this.fetchMemberClassesForCurrentInstance();

    // Store the reserved member info to store
    this.storeReservedMember({
      instanceId: this.ownerInstanceId,
      memberClass: this.ownerMemberClass,
      memberCode: this.ownerMemberCode,
    });
  },
  beforeUnmount() {
    this.resetAddClientState();
    this.resetCsrState();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useCsr, [
      'storeCsrClient',
      'storeCsrIsNewMember',
      'resetCsrState',
      'fetchCsrForm',
      'fetchCertificateAuthorities',
      'setupSignKey',
    ]),
    ...mapActions(useAddClient, ['storeReservedMember', 'resetAddClientState']),
    ...mapActions(useGeneral, ['fetchMemberClassesForCurrentInstance']),
    cancel(): void {
      this.$router.replace({ name: RouteName.Clients });
    },
    csrDetailsReady(): void {
      // Add the selected client id in csr store
      const idString = this.selectedMemberId;
      this.storeCsrClient(idString);
      this.storeCsrIsNewMember(true);

      this.fetchCsrForm().then(
        () => {
          this.currentStep++;
        },
        (error) => {
          this.showError(error);
        },
      );
    },
    done(): void {
      this.$router.replace({ name: RouteName.Clients });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';
</style>
