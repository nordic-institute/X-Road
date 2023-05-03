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
      :title="$t('csr.generateCsr')"
      :show-close="false"
    />
    <v-stepper
      v-model="currentStep"
      :alt-labels="true"
      class="wizard-stepper wizard-noshadow"
    >
      <v-stepper-header class="wizard-noshadow">
        <v-stepper-step :complete="currentStep > 1" step="1">{{
          $t('csr.csrDetails')
        }}</v-stepper-step>
        <v-divider></v-divider>
        <v-stepper-step :complete="currentStep > 2" step="2">{{
          $t('csr.generateCsr')
        }}</v-stepper-step>
      </v-stepper-header>

      <v-stepper-items class="wizard-stepper-content">
        <!-- Step 1 -->
        <v-stepper-content step="1">
          <WizardPageCsrDetails
            :show-previous-button="false"
            @cancel="cancel"
            @done="save"
          />
        </v-stepper-content>
        <!-- Step 2 -->
        <v-stepper-content step="2">
          <WizardPageGenerateCsr
            @cancel="cancel"
            @previous="currentStep = 1"
            @done="cancel"
          />
        </v-stepper-content>
      </v-stepper-items>
    </v-stepper>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import WizardPageCsrDetails from '@/components/wizard/WizardPageCsrDetails.vue';
import WizardPageGenerateCsr from '@/components/wizard/WizardPageGenerateCsr.vue';
import { RouteName } from '@/global';
import { mapActions } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useCsrStore } from '@/store/modules/certificateSignRequest';

export default Vue.extend({
  components: {
    WizardPageCsrDetails,
    WizardPageGenerateCsr,
  },
  props: {
    keyId: {
      type: String,
      required: true,
      default: undefined,
    },
    tokenType: {
      type: String,
      required: false,
      default: undefined,
    },
  },
  data() {
    return {
      currentStep: 1,
    };
  },
  created() {
    this.storeKeyId(this.keyId);
    this.setCsrTokenType(this.tokenType);
    this.fetchKeyData().catch((error) => {
      this.showError(error);
    });
    this.fetchCertificateAuthorities().catch((error) => {
      this.showError(error);
    });
  },
  beforeDestroy() {
    // Clear the store state
    this.resetCsrState();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useCsrStore, [
      'resetCsrState',
      'setCsrTokenType',
      'storeKeyId',
      'fetchCsrForm',
      'fetchKeyData',
      'fetchCertificateAuthorities',
    ]),
    save(): void {
      this.fetchCsrForm().then(
        () => {
          this.currentStep = 2;
        },
        (error) => {
          this.showError(error);
        },
      );
    },
    cancel(): void {
      this.$router.replace({ name: RouteName.SignAndAuthKeys });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/wizards';
</style>
