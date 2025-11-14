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
  <XrdView
    data-test="tls-certificates-view"
    :title="title"
  >
    <template
      v-if="$slots['append-header']"
      #append-header
    >
      <slot name="append-header" />
    </template>

    <template
      v-if="$slots.tabs"
      #tabs
    >
      <slot name="tabs" />
    </template>

    <XrdSubView>
      <template #header>
        <v-spacer />
        <XrdBtn
          v-if="canGenerateKey"
          data-test="management-service-certificate-generateKey"
          variant="outlined"
          prepend-icon="autorenew"
          text="tlsCertificates.generateKey.button"
          @click="generateKey"
        />
        <XrdBtn
          v-if="canUpload"
          data-test="upload-management-service-certificate"
          class="ml-4"
          variant="outlined"
          prepend-icon="upload"
          text="tlsCertificates.uploadCertificate.button"
          @click="uploadCertificate"
        />
        <XrdBtn
          v-if="canDownload"
          data-test="download-management-service-certificate"
          class="ml-4"
          variant="outlined"
          prepend-icon="download"
          text="tlsCertificates.downloadCertificate"
          :loading="loadingDownload"
          @click="download"
        />
      </template>
      <XrdCard>
        <v-table class="xrd bg-surface-container">
          <thead>
            <tr>
              <th>{{ $t('tlsCertificates.key') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td class="on-surface font-weight-medium">
                <v-icon
                  icon="shield_lock filled"
                  size="24"
                />
                {{ $t('tlsCertificates.keyText') }}
              </td>
              <td>
                <XrdBtn
                  v-if="canGenerateCsr"
                  data-test="management-service-certificate-generateCsr"
                  class="float-right"
                  variant="text"
                  color="tertiary"
                  text="tlsCertificates.generateCsr.button"
                  @click="generateCsr"
                />
              </td>
            </tr>
            <tr>
              <td>
                <XrdLabelWithIcon
                  data-test="view-management-service-certificate"
                  class="ml-9"
                  icon="editor_choice"
                  :label="hash"
                  :clickable="canViewCertificate"
                  @navigate="navigateToCertificateDetails"
                >
                  <template #label>
                    <XrdHashValue :value="hash" />
                  </template>
                </XrdLabelWithIcon>
              </td>
              <td></td>
            </tr>
          </tbody>
        </v-table>
      </XrdCard>
      <GenerateKeyDialog
        v-if="showGenerateKeyDialog"
        :handler="handler"
        @accept="closeGenerateKeyDialog"
        @cancel="showGenerateKeyDialog = false"
      />
      <GenerateCsrDialog
        v-if="showGenerateCsrDialog"
        :handler="handler"
        @generate="showGenerateCsrDialog = false"
        @cancel="showGenerateCsrDialog = false"
      />
      <UploadCertificateDialog
        v-if="showUploadCertificateDialog"
        :handler="handler"
        @upload="closeUploadCertificateDialog"
        @cancel="showUploadCertificateDialog = false"
      />
    </XrdSubView>
  </XrdView>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';

import { XrdSubView, XrdView } from '../../layouts';
import { XrdBtn, XrdCard, XrdHashValue, XrdLabelWithIcon } from '../../components';
import { useNotifications } from '../../composables';

import GenerateCsrDialog from './dialogs/GenerateCsrDialog.vue';
import GenerateKeyDialog from './dialogs/GenerateKeyDialog.vue';
import UploadCertificateDialog from './dialogs/UploadCertificateDialog.vue';
import { TlsCertificate, TlsCertificatesHandler } from '../../types';

export default defineComponent({
  components: {
    UploadCertificateDialog,
    GenerateKeyDialog,
    GenerateCsrDialog,
    XrdHashValue,
    XrdView,
    XrdSubView,
    XrdCard,
    XrdBtn,
    XrdLabelWithIcon,
  },
  props: {
    canDownload: {
      type: Boolean,
      required: true,
    },
    canUpload: {
      type: Boolean,
      required: true,
    },
    canViewCertificate: {
      type: Boolean,
      required: true,
    },
    canGenerateKey: {
      type: Boolean,
      required: true,
    },
    canGenerateCsr: {
      type: Boolean,
      required: true,
    },
    handler: {
      type: Object as PropType<TlsCertificatesHandler>,
      required: true,
    },
    title: {
      type: String,
      required: true,
    },
    certDetailsViewName: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data() {
    return {
      loading: false,
      loadingDownload: false,
      certificateDetails: undefined as TlsCertificate | undefined,
      hash: '',
      showGenerateKeyDialog: false,
      showGenerateCsrDialog: false,
      showUploadCertificateDialog: false,
    };
  },
  created() {
    this.fetchData();
  },
  methods: {
    fetchData(): void {
      this.loading = true;
      this.handler
        .fetchTlsCertificate()
        .then((data) => (this.certificateDetails = data))
        .then((data) => (this.hash = data.hash))
        .catch((error) => this.addError(error))
        .finally(() => (this.loading = false));
    },
    download() {
      this.loadingDownload = true;
      this.handler
        .downloadCertificate()
        .catch((error) => this.addError(error))
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
      this.showUploadCertificateDialog = false;
      this.fetchData();
    },
    generateCsr() {
      this.showGenerateCsrDialog = true;
    },
    uploadCertificate() {
      this.showUploadCertificateDialog = true;
    },
    navigateToCertificateDetails() {
      this.$router.push({
        name: this.certDetailsViewName,
      });
    },
  },
});
</script>
