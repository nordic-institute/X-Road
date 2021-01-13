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
  <div class="wrapper">
    <div class="title-row">
      <div class="xrd-view-title">{{ $t('tab.keys.ssTlsCertificate') }}</div>
      <div>
        <help-button
          helpImage="tls_certificate.png"
          helpTitle="keys.helpTitleSS"
          helpText="keys.helpTextSS"
        ></help-button>
      </div>
    </div>

    <div class="details-view-tools">
      <large-button
        v-if="generateKeyVisible"
        class="button-spacing"
        outlined
        @click="generateDialog = true"
        data-test="security-server-tls-certificate-generate-key-button"
        >{{ $t('ssTlsCertificate.generateKey') }}</large-button
      >
      <file-upload
        v-if="importCertificateVisible"
        accepts=".pem, .cer, .der"
        @file-changed="onImportFileChanged"
        v-slot="{ upload }"
      >
        <large-button
          class="button-spacing"
          outlined
          @click="upload"
          data-test="security-server-tls-certificate-import-certificate-key"
          >{{ $t('ssTlsCertificate.importCertificate') }}</large-button
        >
      </file-upload>
      <large-button
        v-if="exportCertificateVisible"
        class="button-spacing"
        outlined
        :loading="exportPending"
        @click="exportCertificate()"
        data-test="security-server-tls-certificate-export-certificate-button"
        >{{ $t('ssTlsCertificate.exportCertificate') }}</large-button
      >
    </div>

    <generate-tls-and-certificate-dialog
      :dialog="generateDialog"
      @cancel="generateDialog = false"
      @saved="newCertificateGenerated"
    />

    <div class="content-card">
      <div class="content-title">{{ $t('ssTlsCertificate.keyCertTitle') }}</div>
      <div class="horizontal-line-dark"></div>

      <div class="content-wrap">
        <div>
          <div class="key-wrap">
            <i class="icon-Key icon" />

            {{ $t('ssTlsCertificate.keyText') }}
          </div>
          <div class="cert-wrap">
            <i
              class="icon-Certificate icon clickable-link"
              @click="certificateClick()"
            />

            <div
              class="clickable-link"
              v-if="certificate"
              @click="certificateClick()"
            >
              {{ certificate.hash | colonize }}
            </div>
          </div>
        </div>

        <div>
          <LargeButton
            v-if="generateCsrVisible"
            class="mr-2"
            @click="generateCsr()"
            text
            :outlined="false"
            data-test="security-server-tls-certificate-generate-csr-button"
            >{{ $t('ssTlsCertificate.generateCsr') }}</LargeButton
          >
        </div>
      </div>

      <div class="horizontal-line-light"></div>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { Permissions, RouteName } from '@/global';
import { CertificateDetails } from '@/openapi-types';
import * as api from '@/util/api';
import GenerateTlsAndCertificateDialog from '@/views/KeysAndCertificates/SecurityServerTlsCertificate/GenerateTlsAndCertificateDialog.vue';
import { saveResponseAsFile } from '@/util/helpers';
import { FileUploadResult } from '@niis/shared-ui';
import HelpButton from '../HelpButton.vue';

export default Vue.extend({
  components: {
    GenerateTlsAndCertificateDialog,
    HelpButton,
  },
  data() {
    return {
      certificate: undefined as CertificateDetails | undefined,
      generateDialog: false,
      exportPending: false,
    };
  },
  computed: {
    generateKeyVisible(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.GENERATE_INTERNAL_TLS_KEY_CERT,
      );
    },
    importCertificateVisible(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.IMPORT_INTERNAL_TLS_CERT,
      );
    },
    exportCertificateVisible(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.EXPORT_INTERNAL_TLS_CERT,
      );
    },
    generateCsrVisible(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.GENERATE_INTERNAL_TLS_CSR,
      );
    },
  },
  methods: {
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
      api
        .get<CertificateDetails>('/system/certificate')
        .then((res) => {
          this.certificate = res.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
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
        .catch((error) => this.$store.dispatch('showError', error))
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
          this.$store.dispatch(
            'showSuccess',
            'ssTlsCertificate.certificateImported',
          );
          this.fetchData();
        })
        .catch((error) => this.$store.dispatch('showError', error));
    },
  },
  created() {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/detail-views';
.wrapper {
  margin-top: 20px;
  width: 100%;
}

.title-row {
  display: flex;
  flex-direction: row;
  align-items: flex-end;
}

.content-title {
  color: $XRoad-Black;
  font-size: $XRoad-DefaultFontSize;
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
  background-color: $XRoad-White100;
  border-radius: 4px;
}

.content-wrap {
  margin-top: 30px;
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
}

.key-wrap {
  display: flex;
}

.cert-wrap {
  display: flex;
  margin-top: 20px;
  padding-left: 40px;
}

.horizontal-line-dark {
  width: 100%;
  height: 1.5px;
  border-top: 1px solid $XRoad-Grey40;
  background-color: $XRoad-Grey10;
}

.horizontal-line-light {
  width: 100%;
  height: 1px;
  background-color: $XRoad-Grey10;
}

.icon {
  margin-left: 18px;
  margin-right: 20px;
}

.clickable {
  cursor: pointer;
}

.clickable-link {
  cursor: pointer;
  height: 100%;
  color: $XRoad-Link;
}
</style>
