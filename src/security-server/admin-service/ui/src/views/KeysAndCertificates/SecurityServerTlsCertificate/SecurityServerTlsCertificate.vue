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
  <XrdTitledView
    title-key="tab.keys.ssTlsCertificate"
    data-test="security-server-tls-key-view"
  >
    <template #append-title>
      <help-button
        :help-image="helpImg"
        help-title="keys.helpTitleSS"
        help-text="keys.helpTextSS"
      />
    </template>

    <template #header-buttons>
      <xrd-button
        v-if="generateKeyVisible"
        class="button-spacing"
        outlined
        data-test="security-server-tls-certificate-generate-key-button"
        @click="generateDialog = true"
        >{{ $t('ssTlsCertificate.generateKey') }}
      </xrd-button>
      <xrd-file-upload
        v-if="importCertificateVisible"
        v-slot="{ upload }"
        accepts=".pem, .cer, .der"
        @file-changed="onImportFileChanged"
      >
        <xrd-button
          class="button-spacing"
          outlined
          data-test="security-server-tls-certificate-import-certificate-key"
          @click="upload"
          >{{ $t('ssTlsCertificate.importCertificate') }}
        </xrd-button>
      </xrd-file-upload>
      <xrd-button
        v-if="exportCertificateVisible"
        class="button-spacing"
        outlined
        :loading="exportPending"
        data-test="security-server-tls-certificate-export-certificate-button"
        @click="exportCertificate()"
        >{{ $t('ssTlsCertificate.exportCertificate') }}
      </xrd-button>
    </template>

    <div class="content-card">
      <div class="content-title mt-0">
        {{ $t('ssTlsCertificate.keyCertTitle') }}
      </div>
      <div class="horizontal-line-dark"></div>

      <div class="key-row">
        <div class="key-wrap">
          <i class="icon-Key icon" />

          {{ $t('ssTlsCertificate.keyText') }}
        </div>

        <div>
          <xrd-button
            v-if="generateCsrVisible"
            class="mr-2"
            text
            :outlined="false"
            data-test="security-server-tls-certificate-generate-csr-button"
            @click="generateCsr()"
            >{{ $t('ssTlsCertificate.generateCsr') }}
          </xrd-button>
        </div>
      </div>

      <div v-if="certificate" class="cert-row">
        <div>
          <i
            class="icon-Certificate icon clickable-link"
            @click="certificateClick()"
          />
        </div>
        <div
          v-if="certificate"
          class="clickable-link"
          @click="certificateClick()"
        >
          {{ $filters.colonize(certificate.hash) }}
        </div>
      </div>
      <XrdEmptyPlaceholder
        :data="certificate"
        :loading="loading"
        :no-items-text="$t('noData.noCertificate')"
      />

      <div class="footer-pad"></div>
    </div>

    <generate-tls-and-certificate-dialog
      :dialog="generateDialog"
      @cancel="generateDialog = false"
      @saved="newCertificateGenerated"
    />
  </XrdTitledView>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Permissions, RouteName } from '@/global';
import { CertificateDetails } from '@/openapi-types';
import * as api from '@/util/api';
import GenerateTlsAndCertificateDialog from '@/views/KeysAndCertificates/SecurityServerTlsCertificate/GenerateTlsAndCertificateDialog.vue';
import { saveResponseAsFile } from '@/util/helpers';
import HelpButton from '@/components/ui/HelpButton.vue';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useNotifications } from '@/store/modules/notifications';
import { FileUploadResult } from '@/ui-types';
import helpImg from '@/assets/tls_certificate.png';

export default defineComponent({
  components: {
    GenerateTlsAndCertificateDialog,
    HelpButton,
  },
  data() {
    return {
      certificate: undefined as CertificateDetails | undefined,
      generateDialog: false,
      exportPending: false,
      loading: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    helpImg(): string {
      return helpImg;
    },
    generateKeyVisible(): boolean {
      return this.hasPermission(Permissions.GENERATE_INTERNAL_TLS_KEY_CERT);
    },
    importCertificateVisible(): boolean {
      return this.hasPermission(Permissions.IMPORT_INTERNAL_TLS_CERT);
    },
    exportCertificateVisible(): boolean {
      return this.hasPermission(Permissions.EXPORT_INTERNAL_TLS_CERT);
    },
    generateCsrVisible(): boolean {
      return this.hasPermission(Permissions.GENERATE_INTERNAL_TLS_CSR);
    },
  },
  created() {
    this.fetchData();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    certificateClick(): void {
      this.$router.push({
        name: RouteName.InternalTlsCertificate,
      });
    },
    generateCsr(): void {
      this.$router.push({
        name: RouteName.GenerateInternalCSR,
      });
    },
    fetchData(): void {
      this.loading = true;
      api
        .get<CertificateDetails>('/system/certificate')
        .then((res) => {
          this.certificate = res.data;
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => (this.loading = false));
    },
    newCertificateGenerated(): void {
      this.fetchData();
      this.generateDialog = false;
    },
    exportCertificate(): void {
      this.exportPending = true;
      api
        .get('/system/certificate/export', { responseType: 'blob' })
        .then((res) => saveResponseAsFile(res, 'certs.tar.gz'))
        .catch((error) => this.showError(error))
        .finally(() => (this.exportPending = false));
    },
    onImportFileChanged(result: FileUploadResult): void {
      api
        .post('/system/certificate/import', result.buffer, {
          headers: {
            'Content-Type': 'application/octet-stream',
          },
        })
        .then(() => {
          this.showSuccess(this.$t('ssTlsCertificate.certificateImported'));
          this.fetchData();
        })
        .catch((error) => this.showError(error));
    },
  },
});
</script>

<style lang="scss" scoped>
@use '@/assets/detail-views';
@use '@niis/shared-ui/src/assets/colors';

.content-title {
  color: colors.$Black100;
  font-size: colors.$DefaultFontSize;
  font-weight: 500;
  margin-top: 40px;
  padding-top: 16px;
  padding-left: 16px;
  margin-bottom: 12px;
}

.button-spacing {
  margin-left: 20px;
}

.content-card {
  background-color: colors.$White100;
  border-radius: 4px;
}

.key-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 56px;
  border-bottom: 1px solid colors.$WarmGrey30;
}

.key-wrap {
  display: flex;
  width: 100%;
  align-items: center;
  padding-left: 15px;
}

.cert-row {
  display: flex;
  width: 100%;
  align-items: center;
  padding-left: 56px;
  height: 56px;
}

.horizontal-line-dark {
  width: 100%;
  height: 1px;
  border-top: 1px solid colors.$WarmGrey30;
}

.footer-pad {
  width: 100%;
  height: 16px;
  border-top: 1px solid colors.$WarmGrey30;
}

.icon {
  margin-left: 18px;
  margin-right: 20px;
}

.clickable-link {
  cursor: pointer;
  display: flex;
  align-items: center;
  height: 100%;
  color: colors.$Link;
}
</style>
