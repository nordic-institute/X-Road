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
  <titled-view title-key="tlsCertificates.managementService.title" data-test="tls-certificates-view">
    <template #header-buttons>
      <xrd-button
        v-if="hasPermissionToDownloadCertificate"
        :loading="loadingDownload"
        data-test="download-management-service-certificate"
        @click="download"
      >
        <xrd-icon-base class="xrd-large-button-icon">
          <XrdIconDownload/>
        </xrd-icon-base>
        {{ $t('tlsCertificates.managementService.downloadCertificate') }}
      </xrd-button>
    </template>

    <div class="mb-6">
      <v-card class="pb-4">
        <table class="xrd-table mt-0 pb-3">
          <tbody>
          <tr>
            <td class="title-cell">
              <div class="key-row">
                <div class="key-wrap">
                  <i class="icon-Key icon"/>
                  {{ $t('tlsCertificates.managementService.key') }}
                </div>
              </div>
            </td>
            <td class="action-cell">
              <div class="button-wrap">
                <xrd-button
                  v-if="hasPermissionToGenerateKey"
                  class="button-spacing"
                  outlined
                  data-test="management-service-certificate-generateKey"
                  @click="generateKey"
                >
                  <xrd-icon-base class="xrd-large-button-icon">
                    <XrdIconAdd/>
                  </xrd-icon-base>
                  {{ $t('tlsCertificates.managementService.generateKey.button') }}
                </xrd-button
                >
                <xrd-button
                  v-if="hasPermissionToUploadCertificate"
                  class="button-spacing"
                  outlined
                  data-test="upload-management-service-certificate"
                  @click="uploadCertificate"
                >
                  <xrd-icon-base class="xrd-large-button-icon">
                    <XrdIconUpload/>
                  </xrd-icon-base>
                  {{ $t('tlsCertificates.managementService.uploadCertificate.button') }}
                </xrd-button>
              </div>
            </td>
          </tr>
          </tbody>
        </table>
        <table class="xrd-table mt-0 pb-3">
          <tbody>
          <tr>
            <td>
              <div >
                <div
                  class="xrd-clickable"
                  data-test="view-management-service-certificate"
                  @click="navigateToCertificateDetails"
                >
                  <xrd-icon-base class="internal-conf-icon">
                    <XrdIconCertificate/>
                  </xrd-icon-base>
                  <hash-value :value="hash" />
                </div>
              </div>
            </td>
            <td>
              <div class="button-wrap">
                <xrd-button
                  v-if="hasPermissionToGenerateCsr"
                  class="button-spacing"
                  text
                  data-test="management-service-certificate-generateCsr"
                  @click="generateCsr"
                >{{ $t('tlsCertificates.managementService.generateCsr.button') }}
                </xrd-button>
              </div>
            </td>
          </tr>
          </tbody>
        </table>
      </v-card>
    </div>
  </titled-view>

  <management-service-generate-key-dialog
    v-if="showGenerateKeyDialog"
    @confirm="closeGenerateKeyDialog"
    @cancel="showGenerateKeyDialog = false"
  >
  </management-service-generate-key-dialog>
  <management-service-generate-csr-dialog
    v-if="showGenerateCsrDialog"
    @generate="showGenerateCsrDialog = false"
    @cancel="showGenerateCsrDialog = false"
  >
  </management-service-generate-csr-dialog>
  <management-service-upload-certificate-dialog
    v-if="showUploadtCertificateDialog"
    @upload="closeUploadCertificateDialog"
    @cancel="showUploadtCertificateDialog = false"
  >
  </management-service-upload-certificate-dialog>
</template>

<script lang="ts">

import { defineComponent } from 'vue';
import { Permissions, RouteName } from '@/global';
import { mapActions, mapState, mapStores } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { CertificateDetails as CertificateDetailsType } from '@/openapi-types';
import {
  XrdIconDownload,
  XrdIconUpload,
  XrdIconCertificate,
} from '@niis/shared-ui';
import TitledView from "@/components/ui/TitledView.vue";
import { useManagementServices } from "@/store/modules/management-services";
import ManagementServiceUploadCertificateDialog
  from "@/components/tlsCertificates/ManagementServiceUploadCertificateDialog.vue";
import ManagementServiceGenerateKeyDialog from "@/components/tlsCertificates/ManagementServiceGenerateKeyDialog.vue";
import ManagementServiceGenerateCsrDialog from "@/components/tlsCertificates/ManagementServiceGenerateCsrDialog.vue";
import { useUser } from "@/store/modules/user";
import HashValue from "@/components/ui/HashValue.vue";

export default defineComponent({
  components: {
    ManagementServiceUploadCertificateDialog,
    TitledView,
    XrdIconDownload,
    XrdIconUpload,
    XrdIconCertificate,
    ManagementServiceGenerateKeyDialog,
    ManagementServiceGenerateCsrDialog,
    HashValue
  },
  data() {
    return {
      loading: false,
      loadingDownload: false,
      certificateDetails: null as CertificateDetailsType | null,
      hash: "",
      showGenerateKeyDialog: false,
      showGenerateCsrDialog: false,
      showUploadtCertificateDialog: false,
    };
  },
  computed: {
    ...mapStores(useManagementServices),
    ...mapState(useUser, ['hasPermission']),
    hasPermissionToDownloadCertificate(): boolean {
      return this.hasPermission(Permissions.DOWNLOAD_MANAGEMENT_SERVICE_TLS_CERT);
    },
    hasPermissionToViewCertificate(): boolean {
      return this.hasPermission(Permissions.VIEW_MANAGEMENT_SERVICE_TLS_CERT);
    },
    hasPermissionToGenerateKey(): boolean {
      return this.hasPermission(Permissions.GENERATE_MANAGEMENT_SERVICE_TLS_KEY_CERT);
    },
    hasPermissionToGenerateCsr(): boolean {
      return this.hasPermission(Permissions.GENERATE_MANAGEMENT_SERVICE_TLS_CSR);
    },
    hasPermissionToUploadCertificate(): boolean {
      return this.hasPermission(Permissions.UPLOAD_MANAGEMENT_SERVICE_TLS_CERT);
    },
  },
  created() {
    this.fetchData();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    fetchData(): void {
      this.loading = true;
      this.managementServicesStore
        .getCertificate()
        .then((resp) => (this.certificateDetails = resp.data))
        .then(() => this.hash = this.certificateDetails?.hash)
        .catch((error) => this.showError(error))
        .finally(() => (this.loading = false));
    },
    download() {
      this.loadingDownload = true;
      this.managementServicesStore
        .downloadCertificate()
        .catch((error) => this.showError(error))
        .finally(() => (this.loadingDownload = false));
    },
    generateKey() {
      this.showGenerateKeyDialog = true;
    },
    closeGenerateKeyDialog() {
      this.showGenerateKeyDialog = false;
      this.fetchData();
    },
    closeUploadCertificateDialog() {
      this.showUploadtCertificateDialog = false;
      this.fetchData();
    },
    generateCsr() {
      this.showGenerateCsrDialog = true;
    },
    uploadCertificate() {
      this.showUploadtCertificateDialog = true;
    },
    navigateToCertificateDetails() {
      this.$router.push({
        name: RouteName.ManagementServiceCertificateDetails,
      });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';

.button-wrap {
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.cert-row {
  display: flex;
  width: 100%;
  align-items: center;
  padding-left: 56px;
  height: 56px;
}

.internal-conf-icon {
  margin-right: 15px;
  color: $XRoad-Purple100;
}
</style>
