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
      <xrd-sub-view-title
        :title="$t('ssTlsCertificate.generateInternalCsr.title')"
        :show-close="false"
      />
      <v-row class="first-action">
        <v-col>{{
          $t('ssTlsCertificate.generateInternalCsr.step1.description')
        }}</v-col>
      </v-row>
      <v-row>
        <v-col cols="3" class="mt-6 icon-wrapper">
          <xrd-help-icon
            :text="$t('ssTlsCertificate.generateInternalCsr.step1.tooltip')"
          />
          {{ $t('ssTlsCertificate.generateInternalCsr.step1.label') }}
        </v-col>
        <v-col cols="8">
          <v-text-field
            v-model="distinguishedName"
            autofocus
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
          <xrd-button
            outlined
            data-text="generate-internal-csr-generate-csr-button"
            :disabled="distinguishedName.length === 0 || csrGenerated"
            :loading="generatingCsr"
            @click="generateCsr"
            >{{
              $t('ssTlsCertificate.generateInternalCsr.step2.generateCSR')
            }}</xrd-button
          >
        </v-col>
      </v-row>
    </v-container>
    <div class="button-footer">
      <xrd-button
        :disabled="csrGenerated || generatingCsr"
        outlined
        data-test="generate-internal-csr-cancel-button"
        @click="back"
        >{{ $t('ssTlsCertificate.generateInternalCsr.cancel') }}</xrd-button
      >

      <xrd-button
        :disabled="!csrGenerated"
        data-test="generate-internal-csr-done-button"
        @click="back"
        >{{ $t('ssTlsCertificate.generateInternalCsr.done') }}</xrd-button
      >
    </div>
  </v-container>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import { saveResponseAsFile } from '@/util/helpers';
import { mapActions } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';

export default Vue.extend({
  data() {
    return {
      distinguishedName: '',
      csrGenerated: false,
      generatingCsr: false,
    };
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
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
          this.showError(error);
        })
        .finally(() => (this.generatingCsr = false));
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/detail-views';
@import '~styles/colors';
@import '~styles/wizards';

$spacing: 12rem;

.wrapper {
  max-width: 850px;
  height: 100%;
  width: 100%;
  color: $XRoad-Black70;
  background-color: $XRoad-White100;
  box-shadow: $XRoad-DefaultShadow;
  border-radius: 4px;
  padding: 0px;
  margin-top: 20px;
}

.icon-wrapper {
  display: flex;
  flex-direction: row;
  justify-content: space-evenly;
}

.first-action {
  margin-top: $spacing;
}
</style>
