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
  <xrd-simple-dialog
    title="trustServices.timestampingService.dialog.add.title"
    save-button-text="action.add"
    cancel-button-text="action.cancel"
    :loading="loading"
    :disable-save="!meta.valid || !certFile || !certFileTitle"
    @save="save"
    @cancel="cancel"
  >
    <template #content>
      <div class="dlg-input-width">
        <v-text-field
          v-bind="tasUrl"
          :label="$t('trustServices.timestampingService.url')"
          :error-messages="errors.url"
          variant="outlined"
          autofocus
          persistent-hint
          data-test="timestamping-service-url-input"
        ></v-text-field>
      </div>

      <div class="dlg-input-width">
        <xrd-file-upload
          v-slot="{ upload }"
          accepts=".der, .crt, .pem, .cer"
          @file-changed="onFileUploaded"
        >
          <v-text-field
            v-model="certFileTitle"
            variant="outlined"
            :label="$t('trustServices.uploadCertificate')"
            append-inner-icon="icon-Upload"
            data-test="timestamping-service-file-input"
            @click="upload"
          ></v-text-field>
        </xrd-file-upload>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { FileUploadResult, XrdFileUpload } from '@niis/shared-ui';
import { useTimestampingServicesStore } from '@/store/modules/trust-services';
import { mapActions, mapStores } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useForm } from 'vee-validate';

export default defineComponent({
  components: { XrdFileUpload },
  emits: ['save', 'cancel'],
  setup() {
    const { values, errors, meta, defineComponentBinds } = useForm({
      validationSchema: { url: 'required|url' },
    });
    const tasUrl = defineComponentBinds('url');
    return { values, errors, meta, tasUrl };
  },
  data() {
    return {
      showCASettingsDialog: false,
      certFile: null as File | null,
      certFileTitle: '',
      loading: false,
    };
  },
  computed: {
    ...mapStores(useTimestampingServicesStore),
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    onFileUploaded(result: FileUploadResult): void {
      this.certFile = result.file;
      this.certFileTitle = result.file.name;
    },
    save(): void {
      if (!this.certFile) return;

      this.loading = true;
      this.timestampingServicesStore
        .addTimestampingService(this.values.url, this.certFile)
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
