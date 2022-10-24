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
<!--
  Certification Service details view
-->
<template>
  <main id="certification-service-details-content" class="mt-8">
    <!-- Certification Service Details -->

    <info-card
      class="mb-6"
      :title-text="$t('trustServices.subjectDistinguishedName')"
      :info-text="
        certificationServiceStore.currentCertificationService
          .subject_distinguished_name || ''
      "
      data-test="subject-distinguished-name-card"
    />

    <info-card
      class="mb-6"
      :title-text="$t('trustServices.issuerDistinguishedName')"
      :info-text="
        certificationServiceStore.currentCertificationService
          .issuer_distinguished_name || ''
      "
      data-test="issuer-distinguished-name-card"
    />

    <div class="certification-service-info-card-group">
      <info-card
        :title-text="$t('trustServices.validFrom')"
        :info-text="
          certificationServiceStore.currentCertificationService.not_before
            | formatDateTime
        "
        data-test="valid-from-card"
      />
      <info-card
        :title-text="$t('trustServices.validTo')"
        :info-text="
          certificationServiceStore.currentCertificationService.not_after
            | formatDateTime
        "
        data-test="valid-to-card"
      />
    </div>
  </main>
</template>

<script lang="ts">
import Vue from 'vue';
import InfoCard from '@/components/ui/InfoCard.vue';
import { mapStores } from 'pinia';
import { useCertificationServiceStore } from '@/store/modules/trust-services';

/**
 * Component for a Certification Service details view
 */
export default Vue.extend({
  name: 'CertificationServiceDetails',
  components: {
    InfoCard,
  },
  computed: {
    ...mapStores(useCertificationServiceStore),
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/tables';

.card-title {
  font-size: 12px;
  text-transform: uppercase;
  color: $XRoad-Black70;
  font-weight: bold;
  padding-top: 5px;
  padding-bottom: 5px;
}

.certification-service-info-card-group {
  margin-top: 24px;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;

  margin-bottom: 24px;

  .details-card {
    width: 100%;

    &:first-child {
      margin-right: 30px;
    }

    &:last-child {
      margin-left: 30px;
    }
  }
}
</style>
