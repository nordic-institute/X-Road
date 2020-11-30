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
    <subViewTitle
      class="view-title"
      :title="$t('csr.generateCsr')"
      :showClose="false"
    />
    <v-stepper
      :alt-labels="true"
      v-model="currentStep"
      class="stepper noshadow"
    >
      <v-stepper-header class="noshadow">
        <v-stepper-step :complete="currentStep > 1" step="1">{{
          $t('csr.csrDetails')
        }}</v-stepper-step>
        <v-divider></v-divider>
        <v-stepper-step :complete="currentStep > 2" step="2">{{
          $t('csr.generateCsr')
        }}</v-stepper-step>
      </v-stepper-header>

      <v-stepper-items class="stepper-content">
        <!-- Step 1 -->
        <v-stepper-content step="1">
          <WizardPageCsrDetails
            @cancel="cancel"
            @done="save"
            :showPreviousButton="false"
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
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import WizardPageCsrDetails from '@/components/wizard/WizardPageCsrDetails.vue';
import WizardPageGenerateCsr from '@/components/wizard/WizardPageGenerateCsr.vue';
import { RouteName } from '@/global';

export default Vue.extend({
  components: {
    SubViewTitle,
    WizardPageCsrDetails,
    WizardPageGenerateCsr,
  },
  props: {
    keyId: {
      type: String,
      required: true,
    },
    tokenType: {
      type: String,
      required: false,
    },
  },
  data() {
    return {
      currentStep: 1,
    };
  },
  methods: {
    save(): void {
      this.$store.dispatch('fetchCsrForm').then(
        () => {
          this.currentStep = 2;
        },
        (error) => {
          this.$store.dispatch('showError', error);
        },
      );
    },
    cancel(): void {
      this.$router.replace({ name: RouteName.SignAndAuthKeys });
    },
    fetchKeyData(): void {
      this.$store.dispatch('fetchKeyData').catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },
    fetchCertificateAuthorities(): void {
      this.$store.dispatch('fetchCertificateAuthorities').catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },
  },
  created() {
    this.$store.commit('storeKeyId', this.keyId);
    this.$store.dispatch('setCsrTokenType', this.tokenType);
    this.fetchKeyData();
    this.fetchCertificateAuthorities();
  },
  beforeDestroy() {
    // Clear the vuex store
    this.$store.dispatch('resetCsrState');
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/colors';
@import '../../assets/shared';

.view-wrap {
  width: 100%;
  max-width: 850px;
  margin: 10px;
}

.view-title {
  width: 100%;
  max-width: 100%;
  margin-bottom: 30px;
}

.stepper-content {
  width: 100%;
  max-width: 900px;
  margin-left: auto;
  margin-right: auto;
}

.stepper {
  width: 100%;
}

.noshadow {
  -webkit-box-shadow: none;
  -moz-box-shadow: none;
  box-shadow: none;
}
</style>
