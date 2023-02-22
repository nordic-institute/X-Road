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
  <div id="intermediate-ca-view">
    <div class="table-toolbar mt-0 pl-0">
      <div class="xrd-view-title">
        {{
          intermediateCasServiceStore.currentCs.name +
          ' / ' +
          intermediateCasServiceStore.currentSelectedIntermediateCa
            .ca_certificate.subject_common_name
        }}
      </div>
      <xrd-button
        outlined
        data-test="view-certificate-button"
        @click="navigateToCertificateDetails()"
      >
        {{ $t('trustServices.viewCertificate') }}
      </xrd-button>
    </div>
    <PageNavigation :items="intermediateCaNavigationItems" />
    <router-view />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import PageNavigation, {
  NavigationItem,
} from '@/components/layout/PageNavigation.vue';
import { Colors, RouteName } from '@/global';
import { mapStores } from 'pinia';
import { useIntermediateCaStore } from '@/store/modules/trust-services';

/**
 * Wrapper component for intermediate CA view
 */
export default Vue.extend({
  name: 'IntermediateCa',
  components: { PageNavigation },
  props: {
    intermediateCaId: {
      type: Number,
      required: true,
    },
  },
  data() {
    return {
      colors: Colors,
    };
  },
  computed: {
    ...mapStores(useIntermediateCaStore),
    intermediateCaNavigationItems(): NavigationItem[] {
      return [
        {
          url: `/intermediate-ca/${this.intermediateCaId}/details`,
          label: this.$t(
            'trustServices.trustService.pagenavigation.details',
          ) as string,
        },
        {
          url: `/intermediate-ca/${this.intermediateCaId}/ocsp-responders`,
          label: this.$t(
            'trustServices.trustService.pagenavigation.ocspResponders',
          ) as string,
        },
      ];
    },
  },
  created() {
    this.intermediateCasServiceStore.loadById(this.intermediateCaId);
  },
  methods: {
    navigateToCertificateDetails() {
      this.$router.push({
        name: RouteName.IntermediateCACertificateDetails,
        params: {
          intermediateCaId: String(this.intermediateCaId),
        },
      });
    },
  },
});
</script>
<style lang="scss" scoped>
@import '~styles/tables';
</style>