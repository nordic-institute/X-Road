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
  <main>
    <xrd-simple-dialog
      v-if="showUploadCertificateDialog"
      cancel-button-text="action.cancel"
      save-button-text="action.upload"
      title="trustServices.addCertificationService"
      :disable-save="certFile === null"
      @save="onUpload"
      @cancel="cancel"
    >
      <template #content>
        <div class="dlg-input-width">
          <xrd-file-upload
            v-slot="{ upload }"
            accepts=".der, .crt, .pem, .cer"
            @file-changed="onFileUploaded"
          >
            <v-text-field
              v-model="certFileTitle"
              variant="outlined"
              autofocus
              :label="$t('trustServices.uploadCertificate')"
              append-inner-icon="icon-Upload"
              @click="upload"
            />
          </xrd-file-upload>
        </div>
      </template>
    </xrd-simple-dialog>

    <xrd-simple-dialog
      v-if="showCASettingsDialog"
      cancel-button-text="action.cancel"
      save-button-text="action.save"
      title="trustServices.caSettings"
      :disable-save="certProfile === ''"
      :loading="loading"
      @save="onSave"
      @cancel="cancel"
    >
      <template #content>
        <div class="dlg-input-width">
          <v-checkbox
            v-model="tlsAuthOnly"
            :label="$t('trustServices.addCASettingsCheckbox')"
          />
          <v-text-field
            v-model="certProfile"
            variant="outlined"
            :label="$t('trustServices.certProfileInput')"
            :hint="$t('trustServices.certProfileInputExplanation')"
            persistent-hint
            data-test="cert-profile-input"
          />
        </div>
      </template>
    </xrd-simple-dialog>
  </main>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { FileUploadResult,XrdFileUpload } from '@niis/shared-ui';
import { Event } from '@/ui-types';

export default defineComponent({
  components: { XrdFileUpload },
  emits: [Event.Cancel, Event.Add],
  data() {
    return {
      showCASettingsDialog: false,
      certFile: null as File | null,
      certFileTitle: '',
      certProfile: '',
      tlsAuthOnly: false,
      showUploadCertificateDialog: true,
      loading: false,
    };
  },
  methods: {
    onFileUploaded(result: FileUploadResult): void {
      this.certFile = result.file;
      this.certFileTitle = result.file.name;
    },
    onUpload(): void {
      this.showCASettingsDialog = true;
      this.showUploadCertificateDialog = false;
    },
    onSave(): void {
      if (this.certFile !== null) {
        this.loading = true;
        const certService = {
          certificate: this.certFile,
          tls_auth: this.tlsAuthOnly.toString(),
          certificate_profile_info: this.certProfile,
        };
        this.$emit(Event.Add, certService, {
          done: () => {
            this.loading = false;
            this.clearForm();
          },
        });
      }
    },
    cancel(): void {
      this.$emit(Event.Cancel);
      this.clearForm();
    },
    clearForm(): void {
      this.certFile = null as File | null;
      this.certProfile = '';
      this.tlsAuthOnly = false;
      this.certFileTitle = '';
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';
</style>
