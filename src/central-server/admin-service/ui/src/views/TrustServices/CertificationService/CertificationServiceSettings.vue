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
      :info-text="currentCertificationService?.tls_auth ? 'True' : 'False'"
      data-test="tls-auth-card"
      @action-clicked="showEditTlsAuthDialog = true"
    />

    <info-card
      class="mb-6"
      :title-text="$t('trustServices.trustService.settings.certProfile')"
      :action-text="$t('action.edit')"
      :show-action="allowEditSettings"
      :info-text="currentCertificationService?.certificate_profile_info || ''"
      data-test="cert-profile-card"
      @action-clicked="showEditCertProfileDialog = true"
    />

    <info-card
      class="mb-6"
      :title-text="acmeCardTitle"
      :action-text="$t('action.edit')"
      :show-action="allowEditSettings"
      data-test="cert-acme-card"
      @action-clicked="showEditAcmeServerDialog = true"
    >
      <v-row>
        <v-col cols="6">
          {{ $t('fields.acmeServerDirectoryUrl') }}
        </v-col>
        <v-col cols="6" data-test="acme-server-directory-url">
          {{ currentCertificationService?.acme_server_directory_url || '-' }}
        </v-col>
        <v-col cols="6">
          {{ $t('fields.acmeServerIpAddress') }}
        </v-col>
        <v-col cols="6" data-test="acme-server-ip-address">
          {{ currentCertificationService?.acme_server_ip_address || '-' }}
        </v-col>
        <v-col cols="6">
          {{ $t('fields.authenticationCertificateProfileId') }}
        </v-col>
        <v-col cols="6" data-test="authentication-certificate-profile-id">
          {{
            currentCertificationService?.authentication_certificate_profile_id ||
            '-'
          }}
        </v-col>
        <v-col cols="6">
          {{ $t('fields.signingCertificateProfileId') }}
        </v-col>
        <v-col cols="6" data-test="signing-certificate-profile-id">
          {{
            currentCertificationService?.signing_certificate_profile_id || '-'
          }}
        </v-col>
      </v-row>
    </info-card>

    <EditTlsAuthDialog
      v-if="showEditTlsAuthDialog && currentCertificationService"
      :certification-service="currentCertificationService"
      @cancel="hideEditTlsAuthDialog"
      @save="hideEditTlsAuthDialog"
    ></EditTlsAuthDialog>

    <EditCertProfileDialog
      v-if="showEditCertProfileDialog && currentCertificationService"
      :certification-service="currentCertificationService"
      @cancel="hideEditCertProfileDialog"
      @save="hideEditCertProfileDialog"
    ></EditCertProfileDialog>

    <EditAcmeServerDialog
      v-if="showEditAcmeServerDialog && currentCertificationService"
      :certification-service="currentCertificationService"
      @cancel="hideEditAcmeServerDialog"
      @tls-auth-changed="hideEditAcmeServerDialog"
    ></EditAcmeServerDialog>
  </main>
</template>

<script lang="ts">
/**
 * Component for a Certification Service details view
 */
import { defineComponent } from 'vue';
import InfoCard from '@/components/ui/InfoCard.vue';
import { mapState } from 'pinia';
import { useCertificationService } from '@/store/modules/trust-services';
import { Permissions } from '@/global';
import { useUser } from '@/store/modules/user';
import EditCertProfileDialog from '@/components/certificationServices/EditCertProfileDialog.vue';
import EditTlsAuthDialog from '@/components/certificationServices/EditTlsAuthDialog.vue';
import EditAcmeServerDialog from '@/components/certificationServices/EditAcmeServerDialog.vue';

export default defineComponent({
  name: 'CertificationServiceSettings',
  components: {
    EditTlsAuthDialog,
    EditCertProfileDialog,
    EditAcmeServerDialog,
    InfoCard,
  },
  data() {
    return {
      showEditTlsAuthDialog: false,
      showEditCertProfileDialog: false,
      showEditAcmeServerDialog: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapState(useCertificationService, ['currentCertificationService']),
    allowEditSettings(): boolean {
      return this.hasPermission(Permissions.EDIT_APPROVED_CA);
    },
    acmeCardTitle(): string {
      return this.currentCertificationService?.acme_server_directory_url
        ? this.$t('trustServices.trustService.settings.acmeCapable')
        : this.$t('trustServices.trustService.settings.acmeNotCapable');
    },
  },
  methods: {
    hideEditTlsAuthDialog() {
      this.showEditTlsAuthDialog = false;
    },
    hideEditCertProfileDialog() {
      this.showEditCertProfileDialog = false;
    },
    hideEditAcmeServerDialog() {
      this.showEditAcmeServerDialog = false;
    },
  },
});
</script>
