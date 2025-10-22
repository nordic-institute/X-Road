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
  <XrdElevatedViewSimple title="csr.addKey">
    <XrdWizard v-model="currentStep">
      <template #header-items>
        <v-stepper-item :complete="currentStep > 1" :value="1">
          {{ $t('keys.detailsTitle') }}
        </v-stepper-item>
        <v-divider />

        <v-stepper-item :complete="currentStep > 2" :value="2">
          {{ $t('csr.csrDetails') }}
        </v-stepper-item>
        <v-divider />

        <v-stepper-item :value="3">
          {{ $t('csr.generateCsr') }}
        </v-stepper-item>
      </template>

      <!-- Step 1 -->
      <v-stepper-window-item :value="1">
        <WizardPageKeyLabel
          :token-type="tokenType"
          @cancel="cancel"
          @done="currentStep++"
        />
      </v-stepper-window-item>

      <!-- Step 2 -->
      <v-stepper-window-item :value="2">
        <WizardPageCsrDetails
          save-button-text="action.next"
          @cancel="cancel"
          @previous="currentStep--"
          @done="save"
        />
      </v-stepper-window-item>

      <!-- Step 3 -->
      <v-stepper-window-item :value="3">
        <WizardPageGenerateCsr
          :key="csrGenPageKey"
          key-and-csr
          @cancel="cancel"
          @previous="currentStep--"
          @done="done"
        />
      </v-stepper-window-item>
    </XrdWizard>
  </XrdElevatedViewSimple>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import WizardPageKeyLabel from '@/components/wizard/WizardPageKeyLabel.vue';
import WizardPageCsrDetails from '@/components/wizard/WizardPageCsrDetails.vue';
import WizardPageGenerateCsr from '@/components/wizard/WizardPageGenerateCsr.vue';
import { RouteName } from '@/global';
import { mapActions } from 'pinia';
import { useCsr } from '@/store/modules/certificateSignRequest';
import {
  XrdElevatedViewSimple,
  XrdWizard,
  useNotifications,
} from '@niis/shared-ui';

export default defineComponent({
  components: {
    WizardPageKeyLabel,
    WizardPageCsrDetails,
    WizardPageGenerateCsr,
    XrdElevatedViewSimple,
    XrdWizard,
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
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data() {
    return {
      currentStep: 1,
      csrGenPageKey: 0,
    };
  },
  created() {
    this.setCsrTokenId(this.tokenId);
    this.setCsrTokenType(this.tokenType);
    this.fetchCertificateAuthorities().catch((error) => {
      this.addError(error);
    });
  },
  methods: {
    ...mapActions(useCsr, [
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
          this.rerenderCsrGenPage();
        },
        (error) => {
          this.addError(error);
        },
      );
    },
    rerenderCsrGenPage(): void {
      this.csrGenPageKey += 1;
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

<style lang="scss" scoped></style>
