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
  <details-view id="certification-service-view" back-to="/trust-services">
    <div class="table-toolbar mt-0 pl-0">
      <div class="xrd-view-title">
        {{ certificationServiceStore.currentCertificationService.name }}
      </div>
      <xrd-button
        outlined
        data-test="view-certificate-button"
        @click="navigateToCertificateDetails()"
      >
        {{ $t('trustServices.viewCertificate') }}
      </xrd-button>
    </div>
    <PageNavigation :tabs="certificationServiceNavigationTabs"></PageNavigation>
    <router-view />
  </details-view>
</template>

<script lang="ts">
import Vue from 'vue';
import PageNavigation, {
  PageNavigationTab,
} from '@/components/layout/PageNavigation.vue';
import { Colors, Permissions, RouteName } from '@/global';
import { mapStores } from 'pinia';
import { useCertificationServiceStore } from '@/store/modules/trust-services';
import DetailsView from '@/components/ui/DetailsView.vue';

/**
 * Wrapper component for a certification service view
 */
export default Vue.extend({
  name: 'CertificationService',
  components: { DetailsView, PageNavigation },
  props: {
    certificationServiceId: {
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
    ...mapStores(useCertificationServiceStore),
    certificationServiceNavigationTabs(): PageNavigationTab[] {
      return [
        {
          key: 'certification-service-details-tab-button',
          name: 'trustServices.trustService.pagenavigation.details',
          to: {
            name: RouteName.CertificationServiceDetails,
          },
          permissions: [Permissions.VIEW_APPROVED_CA_DETAILS],
        },

        {
          key: 'certification-service-settings-tab-button',
          name: 'trustServices.trustService.pagenavigation.settings',
          to: {
            name: RouteName.CertificationServiceSettings,
          },
          permissions: [Permissions.EDIT_APPROVED_CA],
        },

        {
          key: 'certification-service-ocsp-responders-tab-button',
          name: 'trustServices.trustService.pagenavigation.ocspResponders',
          to: {
            name: RouteName.CertificationServiceOcspResponders,
          },
          permissions: [Permissions.VIEW_APPROVED_CA_DETAILS],
        },

        {
          key: 'certification-service-intermediate-cas-tab-button',
          name: 'trustServices.trustService.pagenavigation.intermediateCas',
          to: {
            name: RouteName.CertificationServiceIntermediateCas,
          },
          permissions: [Permissions.VIEW_APPROVED_CA_DETAILS],
        },
      ];
    },
  },
  created() {
    this.certificationServiceStore.loadById(this.certificationServiceId);
  },
  methods: {
    navigateToCertificateDetails() {
      this.$router.push({
        name: RouteName.CertificationServiceCertificateDetails,
        params: {
          certificationServiceId: String(this.certificationServiceId),
        },
      });
    },
  },
});
</script>
<style lang="scss" scoped>
@import '~styles/tables';
</style>
