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
  <XrdElevatedViewSimple title="csr.generateCsr">
    <XrdWizard v-model="currentStep">
      <template #header-items>
        <v-stepper-item :complete="currentStep > 1" :value="1">
          {{ $t('csr.csrDetails') }}
        </v-stepper-item>
        <v-divider />

        <v-stepper-item :value="2">
          {{ $t('csr.generateCsr') }}
        </v-stepper-item>
      </template>

      <!-- Step 1 -->
      <v-stepper-window-item :value="1">
        <WizardPageCsrDetails
          save-button-text="action.next"
          :show-previous-button="false"
          @cancel="cancel"
          @done="save"
        />
      </v-stepper-window-item>

      <!-- Step 2 -->
      <v-stepper-window-item :value="2">
        <WizardPageGenerateCsr
          @cancel="cancel"
          @previous="currentStep--"
          @done="cancel"
        />
      </v-stepper-window-item>
    </XrdWizard>
  </XrdElevatedViewSimple>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import WizardPageCsrDetails from '@/components/wizard/WizardPageCsrDetails.vue';
import WizardPageGenerateCsr from '@/components/wizard/WizardPageGenerateCsr.vue';
import { RouteName } from '@/global';
import { mapActions } from 'pinia';
import { useCsr } from '@/store/modules/certificateSignRequest';
import { XrdElevatedViewSimple, XrdWizard, useNotifications } from '@niis/shared-ui';

export default defineComponent({
  components: {
    WizardPageCsrDetails,
    WizardPageGenerateCsr,
    XrdElevatedViewSimple,
    XrdWizard,
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  props: {
    keyId: {
      type: String,
      required: true,
    },
    tokenType: {
      type: String,
      required: false,
      default: '',
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
      this.addError(error);
    });
    this.fetchCertificateAuthorities().catch((error) => {
      this.addError(error);
    });
  },
  beforeUnmount() {
    // Clear the store state
    this.resetCsrState();
  },
  methods: {
    ...mapActions(useCsr, [
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
          this.addError(error);
        },
      );
    },
    cancel(): void {
      this.$router.replace({ name: RouteName.SignAndAuthKeys });
    },
  },
});
</script>

<style lang="scss" scoped></style>
