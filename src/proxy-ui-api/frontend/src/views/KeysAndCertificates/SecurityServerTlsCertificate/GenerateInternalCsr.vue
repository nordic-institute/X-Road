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
  <v-container class="xrd-view-common justify-center wrapper">
    <v-container>
      <SubViewTitle
        :title="$t('ssTlsCertificate.generateInternalCsr.title')"
        :show-close="false"
      ></SubViewTitle>
      <v-row class="first-action">
        <v-col>{{
          $t('ssTlsCertificate.generateInternalCsr.step1.description')
        }}</v-col>
      </v-row>
      <v-row>
        <v-col cols="3" class="mt-6">
          <HelpIcon
            :text="$t('ssTlsCertificate.generateInternalCsr.step1.tooltip')"
          />
          {{ $t('ssTlsCertificate.generateInternalCsr.step1.label') }}
        </v-col>
        <v-col cols="8">
          <v-text-field
            autofocus
            v-model="distinguishedName"
            outlined
            data-text="generate-internal-csr-distinguished-name-field"
            :placeholder="
              $t('ssTlsCertificate.generateInternalCsr.step1.placeholder')
            "
          />
        </v-col>
      </v-row>
      <v-row>
        <v-col cols="8" class="mt-2">{{
          $t('ssTlsCertificate.generateInternalCsr.step2.description')
        }}</v-col>
        <v-col cols="4" class="text-right">
          <large-button
            outlined
            data-text="generate-internal-csr-generate-csr-button"
            :disabled="distinguishedName.length === 0 || csrGenerated"
            :loading="generatingCsr"
            @click="generateCsr"
            >{{
              $t('ssTlsCertificate.generateInternalCsr.step2.generateCSR')
            }}</large-button
          >
        </v-col>
      </v-row>
    </v-container>
    <div class="button-footer">
      <large-button
        @click="back"
        :disabled="csrGenerated || generatingCsr"
        outlined
        data-test="generate-internal-csr-cancel-button"
        >{{ $t('ssTlsCertificate.generateInternalCsr.cancel') }}</large-button
      >

      <large-button
        @click="back"
        :disabled="!csrGenerated"
        data-test="generate-internal-csr-done-button"
        >{{ $t('ssTlsCertificate.generateInternalCsr.done') }}</large-button
      >
    </div>
  </v-container>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import { saveResponseAsFile } from '@/util/helpers';

export default Vue.extend({
  data() {
    return {
      distinguishedName: '',
      csrGenerated: false,
      generatingCsr: false,
    };
  },
  methods: {
    back(): void {
      this.$router.go(-1);
    },
    generateCsr(): void {
      this.generatingCsr = true;
      api
        .post(
          '/system/certificate/csr',
          { name: this.distinguishedName },
          { responseType: 'blob' },
        )
        .then((res) => {
          saveResponseAsFile(res, 'request.csr');
          this.csrGenerated = true;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        })
        .finally(() => (this.generatingCsr = false));
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/detail-views';
@import '../../../assets/colors';
@import '~styles/wizards';

$spacing: 12rem;

.wrapper {
  max-width: 850px;
  height: 100%;
  width: 100%;
  color: $XRoad-Grey60;
  background-color: $XRoad-White100;
  border-radius: 4px;
  padding: 0px;
  margin-top: 20px;
}

.help-wrapper {
  display: inline;
}

.first-action {
  margin-top: $spacing;
}

.bottom-buttons-wrapper {
  border-top: solid 1px $XRoad-Grey40;
  padding-top: 1rem;
  margin-top: $spacing;
}
</style>
