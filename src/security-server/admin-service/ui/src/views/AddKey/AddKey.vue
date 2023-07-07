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
      :title="$t('csr.addKey')"
      :show-close="false"
    />
    <v-stepper
      v-model="currentStep"
      :alt-labels="true"
      class="wizard-stepper wizard-noshadow"
    >
      <v-stepper-header class="wizard-noshadow">
        <v-stepper-step :complete="currentStep > 1" step="1">{{
          $t('keys.detailsTitle')
        }}</v-stepper-step>
        <v-divider></v-divider>
        <v-stepper-step :complete="currentStep > 2" step="2">{{
          $t('csr.csrDetails')
        }}</v-stepper-step>
        <v-divider></v-divider>
        <v-stepper-step :complete="currentStep > 3" step="3">{{
          $t('csr.generateCsr')
        }}</v-stepper-step>
      </v-stepper-header>

      <v-stepper-items class="wizard-stepper-content">
        <!-- Step 1 -->
        <v-stepper-content step="1">
          <WizardPageKeyLabel
            :token-type="tokenType"
            @cancel="cancel"
            @done="currentStep = 2"
          />
        </v-stepper-content>
        <!-- Step 2 -->
        <v-stepper-content step="2">
          <WizardPageCsrDetails
            @cancel="cancel"
            @previous="currentStep = 1"
            @done="save"
          />
        </v-stepper-content>
        <!-- Step 3 -->
        <v-stepper-content step="3">
          <WizardPageGenerateCsr
            key-and-csr
            @cancel="cancel"
            @previous="currentStep = 2"
            @done="done"
          />
        </v-stepper-content>
      </v-stepper-items>
    </v-stepper>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import WizardPageKeyLabel from '@/components/wizard/WizardPageKeyLabel.vue';
import WizardPageCsrDetails from '@/components/wizard/WizardPageCsrDetails.vue';
import WizardPageGenerateCsr from '@/components/wizard/WizardPageGenerateCsr.vue';
import { RouteName } from '@/global';
import { mapActions } from 'pinia';
import { useCsrStore } from '@/store/modules/certificateSignRequest';
import { useNotifications } from '@/store/modules/notifications';

export default Vue.extend({
  components: {
    WizardPageKeyLabel,
    WizardPageCsrDetails,
    WizardPageGenerateCsr,
  },
  props: {
    tokenId: {
      type: String,
      required: true,
    },
    tokenType: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      currentStep: 1,
    };
  },
  created() {
    this.setCsrTokenId(this.tokenId);
    this.setCsrTokenType(this.tokenType);
    this.fetchCertificateAuthorities().catch((error) => {
      this.showError(error);
    });
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
    ...mapActions(useCsrStore, [
      'setCsrTokenId',
      'setCsrTokenType',
      'fetchCsrForm',
      'resetCsrState',
      'fetchCertificateAuthorities',
    ]),

    save(): void {
      this.fetchCsrForm().then(
        () => {
          this.currentStep = 3;
        },
        (error) => {
          this.showError(error);
        },
      );
    },
    cancel(): void {
      this.resetCsrState();
      this.$router.replace({ name: RouteName.SignAndAuthKeys });
    },
    done(): void {
      this.resetCsrState();
      this.$router.replace({ name: RouteName.SignAndAuthKeys });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/wizards';
</style>
