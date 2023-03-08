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
  <ValidationObserver v-slot="{ invalid }">
    <xrd-simple-dialog
      :dialog="true"
      title="trustServices.timestampingService.dialog.add.title"
      save-button-text="action.add"
      cancel-button-text="action.cancel"
      :loading="loading"
      :disable-save="invalid || !certFile || !certFileTitle"
      @save="save"
      @cancel="cancel"
    >
      <template #content>
        <div class="dlg-input-width">
          <ValidationProvider
            v-slot="{ errors }"
            rules="required|url"
            name="url"
          >
            <v-text-field
              v-model="tasUrl"
              :label="$t('trustServices.timestampingService.url')"
              :error-messages="errors"
              outlined
              autofocus
              persistent-hint
              data-test="timestamping-service-url-input"
            ></v-text-field>
          </ValidationProvider>
        </div>

        <div class="dlg-input-width">
          <xrd-file-upload
            v-slot="{ upload }"
            accepts=".der, .crt, .pem, .cer"
            @file-changed="onFileUploaded"
          >
            <v-text-field
              v-model="certFileTitle"
              outlined
              :label="$t('trustServices.uploadCertificate')"
              append-icon="icon-Upload"
              data-test="timestamping-service-file-input"
              @click="upload"
            ></v-text-field>
          </xrd-file-upload>
        </div>
      </template>
    </xrd-simple-dialog>
  </ValidationObserver>
</template>

<script lang="ts">
import Vue from 'vue';
import { FileUploadResult } from '@niis/shared-ui';
import { timestampingServicesStore } from '@/store/modules/trust-services';
import { ValidationObserver, ValidationProvider } from 'vee-validate';
import { mapActions, mapStores } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';

export default Vue.extend({
  name: 'AddTimestampingServiceDialog',
  components: {
    ValidationProvider,
    ValidationObserver,
  },
  data() {
    return {
      showCASettingsDialog: false,
      certFile: null as File | null,
      certFileTitle: '',
      tasUrl: '',
      loading: false,
    };
  },
  computed: {
    ...mapStores(timestampingServicesStore),
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    onFileUploaded(result: FileUploadResult): void {
      this.certFile = result.file;
      this.certFileTitle = result.file.name;
    },
    save(): void {
      if (!this.certFile) return;

      this.loading = true;
      this.timestampingServicesStore
        .addTimestampingService(this.tasUrl, this.certFile)
        .then(() => {
          this.showSuccess(
            this.$t('trustServices.timestampingService.dialog.add.success'),
          );
          this.$emit('save');
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => (this.loading = false));
    },
    cancel(): void {
      this.$emit('cancel');
    },
  },
});
</script>
