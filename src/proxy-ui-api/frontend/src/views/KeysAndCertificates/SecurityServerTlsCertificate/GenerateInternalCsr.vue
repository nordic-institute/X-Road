<template>
  <v-container class="xrd-view-common justify-center wrapper">
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
          v-model="distinguishedName"
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
    <v-row class="bottom-buttons-wrapper">
      <v-col cols="6" class="text-left">
        <large-button
          @click="back"
          :disabled="csrGenerated || generatingCsr"
          outlined
          data-test="generate-internal-csr-cancel-button"
          >{{ $t('ssTlsCertificate.generateInternalCsr.cancel') }}</large-button
        >
      </v-col>
      <v-col cols="6" class="text-right">
        <large-button
          @click="back"
          :disabled="!csrGenerated"
          data-test="generate-internal-csr-done-button"
          >{{ $t('ssTlsCertificate.generateInternalCsr.done') }}</large-button
        >
      </v-col>
    </v-row>
  </v-container>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import HelpIcon from '@/components/ui/HelpIcon.vue';
import { saveResponseAsFile } from '@/util/helpers';

export default Vue.extend({
  components: {
    SubViewTitle,
    LargeButton,
    HelpIcon,
  },
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

$spacing: 12rem;

.wrapper {
  max-width: 850px;
  height: 100%;
  width: 100%;
  color: $XRoad-Grey60;
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
