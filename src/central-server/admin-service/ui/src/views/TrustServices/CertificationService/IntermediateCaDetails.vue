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
  Intermediate CA details view
-->
<template>
  <main id="intermediate-ca-details" class="mt-8">
    <info-card
      class="mb-6"
      :title-text="
        $t('trustServices.trustService.details.subjectDistinguishedName')
      "
      :info-text="
        intermediateCasServiceStore.currentSelectedIntermediateCa
          ?.ca_certificate.subject_distinguished_name || ''
      "
      data-test="subject-distinguished-name-card"
    />

    <info-card
      class="mb-6"
      :title-text="
        $t('trustServices.trustService.details.issuerDistinguishedName')
      "
      :info-text="
        intermediateCasServiceStore.currentSelectedIntermediateCa
          ?.ca_certificate.issuer_distinguished_name || ''
      "
      data-test="issuer-distinguished-name-card"
    />

    <div class="certification-service-info-card-group">
      <info-card
        data-test="valid-from-card"
        :title-text="$t('trustServices.validFrom')"
      >
        <date-time
          :value="
            intermediateCasServiceStore.currentSelectedIntermediateCa
              ?.ca_certificate.not_before
          "
        />
      </info-card>
      <info-card
        data-test="valid-to-card"
        :title-text="$t('trustServices.validTo')"
      >
        <date-time
          :value="
            intermediateCasServiceStore.currentSelectedIntermediateCa
              ?.ca_certificate.not_after
          "
        />
      </info-card>
    </div>
  </main>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import InfoCard from '@/components/ui/InfoCard.vue';
import { mapStores } from 'pinia';
import { useIntermediateCasService } from '@/store/modules/trust-services';
import DateTime from '@/components/ui/DateTime.vue';

/**
 * Component for a Certification Service details view
 */
export default defineComponent({
  name: 'IntermediateCaDetails',
  components: {
    DateTime,
    InfoCard,
  },
  computed: {
    ...mapStores(useIntermediateCasService),
  },
});
</script>

<style lang="scss" scoped>
@use '@niis/shared-ui/src/assets/colors';
@use '@niis/shared-ui/src/assets/tables' as *;

.certification-service-info-card-group {
  margin-top: 24px;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;

  margin-bottom: 24px;

  /* eslint-disable-next-line vue-scoped-css/no-unused-selector */
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
