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
  Certification Service settings view
-->
<template>
  <main id="certification-service-settings" class="mt-8">
    <!-- Certification Service Settings -->

    <info-card
      class="mb-6"
      :title-text="$t('trustServices.trustService.settings.tlsAuth')"
      :action-text="$t('action.edit')"
      :show-action="allowEditSettings"
      :info-text="
        certificationServiceStore.currentCertificationService.tls_auth
          ? 'True'
          : 'False'
      "
      data-test="tls-auth-card"
      @actionClicked="showEditTlsAuthDialog = true"
    />

    <info-card
      class="mb-6"
      :title-text="$t('trustServices.trustService.settings.certProfile')"
      :action-text="$t('action.edit')"
      :show-action="allowEditSettings"
      :info-text="
        certificationServiceStore.currentCertificationService
          .certificate_profile_info || ''
      "
      data-test="cert-profile-card"
      @actionClicked="showEditCertProfileDialog = true"
    />

    <!-- Edit TLS auth dialog -->
    <EditTlsAuthDialog
      v-if="showEditTlsAuthDialog"
      :certification-service="
        certificationServiceStore.currentCertificationService
      "
      @cancel="hideEditTlsAuthDialog"
      @tlsAuthChanged="hideEditTlsAuthDialog"
    ></EditTlsAuthDialog>

    <!-- Edit cert profile dialog -->
    <EditCertProfileDialog
      v-if="showEditCertProfileDialog"
      :certification-service="
        certificationServiceStore.currentCertificationService
      "
      @cancel="hideEditCertProfileDialog"
      @tlsAuthChanged="hideEditCertProfileDialog"
    ></EditCertProfileDialog>
  </main>
</template>

<script lang="ts">
/**
 * Component for a Certification Service details view
 */
import Vue from 'vue';
import InfoCard from '@/components/ui/InfoCard.vue';
import { mapState, mapStores } from 'pinia';
import { useCertificationServiceStore } from '@/store/modules/trust-services';
import { Permissions } from '@/global';
import { userStore } from '@/store/modules/user';
import EditCertProfileDialog from '@/components/certificationServices/EditCertProfileDialog.vue';
import EditTlsAuthDialog from '@/components/certificationServices/EditTlsAuthDialog.vue';

export default Vue.extend({
  name: 'CertificationServiceSettings',
  components: {
    EditTlsAuthDialog,
    EditCertProfileDialog,
    InfoCard,
  },
  data() {
    return {
      showEditTlsAuthDialog: false,
      showEditCertProfileDialog: false,
    };
  },
  computed: {
    ...mapStores(useCertificationServiceStore),
    ...mapState(userStore, ['hasPermission']),
    allowEditSettings(): boolean {
      return this.hasPermission(Permissions.EDIT_APPROVED_CA);
    },
  },
  methods: {
    hideEditTlsAuthDialog() {
      this.showEditTlsAuthDialog = false;
    },
    hideEditCertProfileDialog() {
      this.showEditCertProfileDialog = false;
    },
  },
});
</script>
