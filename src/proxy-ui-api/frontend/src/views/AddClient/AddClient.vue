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
    <v-stepper
      v-model="currentStep"
      :alt-labels="true"
      class="wizard-stepper wizard-noshadow"
    >
      <template v-if="addMemberWizardMode === wizardModes.FULL">
        <v-stepper-header class="wizard-noshadow">
          <v-stepper-step :complete="currentStep > 1" step="1">{{
            $t('wizard.clientDetails')
          }}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 2" step="2">{{
            $t('wizard.token.title')
          }}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 3" step="3">{{
            $t('wizard.signKey.title')
          }}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 4" step="4">{{
            $t('csr.csrDetails')
          }}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 5" step="5">{{
            $t('csr.generateCsr')
          }}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step step="6">{{
            $t('wizard.finish.title')
          }}</v-stepper-step>
        </v-stepper-header>
      </template>

      <template v-if="addMemberWizardMode === wizardModes.CERTIFICATE_EXISTS">
        <v-stepper-header class="wizard-noshadow">
          <v-stepper-step :complete="currentStep > 1" step="1">{{
            $t('wizard.clientDetails')
          }}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step step="2">{{
            $t('wizard.finish.title')
          }}</v-stepper-step>
        </v-stepper-header>
      </template>

      <template v-if="addMemberWizardMode === wizardModes.CSR_EXISTS">
        <v-stepper-header class="wizard-noshadow">
          <v-stepper-step :complete="currentStep > 1" step="1">{{
            $t('wizard.clientDetails')
          }}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 2" step="2">{{
            $t('csr.csrDetails')
          }}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 3" step="3">{{
            $t('csr.generateCsr')
          }}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step step="4">{{
            $t('wizard.finish.title')
          }}</v-stepper-step>
        </v-stepper-header>
      </template>

      <v-stepper-items class="wizard-stepper-content">
        <!-- Step 1 -->
        <v-stepper-content step="1">
          <ClientDetailsPage @cancel="cancel" @done="currentStep++" />
        </v-stepper-content>
        <!-- Step 2 -->
        <v-stepper-content :step="tokenPageNumber">
          <TokenPage
            @cancel="cancel"
            @previous="currentStep--"
            @done="currentStep++"
          />
        </v-stepper-content>
        <!-- Step 3 -->
        <v-stepper-content :step="keyPageNumber">
          <SignKeyPage
            @cancel="cancel"
            @previous="currentStep--"
            @done="currentStep++"
          />
        </v-stepper-content>
        <!-- Step 4 -->
        <v-stepper-content :step="csrDetailsPageNumber">
          <CsrDetailsPageLocked
            save-button-text="action.next"
            @cancel="cancel"
            @previous="currentStep--"
            @done="csrDetailsReady"
          />
        </v-stepper-content>
        <!-- Step 5 -->
        <v-stepper-content :step="csrGeneratePageNumber">
          <GenerateCsrPage
            save-button-text="action.next"
            @cancel="cancel"
            @previous="currentStep--"
            @done="currentStep++"
          />
        </v-stepper-content>
        <!-- Step 6 -->
        <v-stepper-content :step="finishPageNumber">
          <FinishPage @cancel="cancel" @previous="currentStep--" @done="done" />
        </v-stepper-content>
      </v-stepper-items>
    </v-stepper>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import ClientDetailsPage from './ClientDetailsPage.vue';
import TokenPage from '@/components/wizard/TokenPage.vue';
import SignKeyPage from '@/components/wizard/SignKeyPage.vue';
import FinishPage from './FinishPage.vue';
import CsrDetailsPageLocked from '@/components/wizard/CsrDetailsPageLocked.vue';
import GenerateCsrPage from '@/components/wizard/GenerateCsrPage.vue';
import { RouteName, AddMemberWizardModes } from '@/global';
import { mapActions, mapState } from 'pinia';
import { useAddClient } from '@/store/modules/addClient';
import { useNotifications } from '@/store/modules/notifications';
import { useCsrStore } from '@/store/modules/certificateSignRequest';

const NO_SELECTION = 999;

export default Vue.extend({
  components: {
    ClientDetailsPage,
    TokenPage,
    SignKeyPage,
    FinishPage,
    CsrDetailsPageLocked,
    GenerateCsrPage,
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
      if (
        this.addMemberWizardMode === AddMemberWizardModes.CERTIFICATE_EXISTS
      ) {
        return NO_SELECTION;
      }
      if (this.addMemberWizardMode === AddMemberWizardModes.CSR_EXISTS) {
        return 2;
      }

      return 4;
    },

    csrGeneratePageNumber(): number {
      if (
        this.addMemberWizardMode === AddMemberWizardModes.CERTIFICATE_EXISTS
      ) {
        return NO_SELECTION;
      }
      if (this.addMemberWizardMode === AddMemberWizardModes.CSR_EXISTS) {
        return 3;
      }

      return 5;
    },

    finishPageNumber(): number {
      if (
        this.addMemberWizardMode === AddMemberWizardModes.CERTIFICATE_EXISTS
      ) {
        return 2;
      }
      if (this.addMemberWizardMode === AddMemberWizardModes.CSR_EXISTS) {
        return 4;
      }
      return 6;
    },
  },
  created() {
    // Set up the CSR part with Sign mode
    this.setupSignKey();
    // Fetch certificate authorities. Used in "sign key" step.
    this.fetchCertificateAuthorities().catch((error) => {
      this.showError(error);
    });
  },
  beforeDestroy() {
    // Clear the stores used in the wizard
    this.resetAddClientState();
    this.resetCsrState();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useCsrStore, [
      'setupSignKey',
      'resetCsrState',
      'fetchCertificateAuthorities',
      'storeCsrClient',
      'storeCsrIsNewMember',
      'fetchCsrForm',
    ]),
    ...mapActions(useAddClient, ['resetAddClientState']),

    cancel(): void {
      this.$router.replace({ name: RouteName.Clients });
    },
    csrDetailsReady(): void {
      // Add the selected client id in the CSR store
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
@import '~styles/wizards';
</style>
