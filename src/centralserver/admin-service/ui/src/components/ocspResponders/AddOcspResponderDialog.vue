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
      :disable-save="invalid || !certFile || !certFileTitle"
      :dialog="true"
      title="trustServices.trustService.ocspResponders.add.dialog.title"
      save-button-text="action.save"
      cancel-button-text="action.cancel"
      :loading="loading"
      @cancel="cancel"
      @save="add"
    >
      <template #content>
        <div class="dlg-input-width">
          <ValidationProvider
            v-slot="{ errors }"
            rules="required|url"
            name="url"
          >
            <v-text-field
              v-model="ocspUrl"
              :label="$t('trustServices.trustService.ocspResponders.url')"
              :error-messages="errors"
              outlined
              persistent-hint
              data-test="ocsp-responder-url-input"
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
              autofocus
              :label="$t('trustServices.uploadCertificate')"
              append-icon="icon-Upload"
              data-test="ocsp-responder-file-input"
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
import { mapActions, mapStores } from 'pinia';
import { useOcspResponderStore } from '@/store/modules/trust-services';
import { notificationsStore } from '@/store/modules/notifications';
import { FileUploadResult } from '@niis/shared-ui';
import { ValidationObserver, ValidationProvider } from 'vee-validate';

export default Vue.extend({
  name: 'AddOcspResponderDialog',
  components: {
    ValidationProvider,
    ValidationObserver,
  },
  props: {
    caId: {
      type: Number,
      required: true,
    },
  },
  data() {
    return {
      ocspUrl: '',
      certFile: null as File | null,
      certFileTitle: '',
      loading: false,
    };
  },
  computed: {
    ...mapStores(useOcspResponderStore),
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    cancelEdit(): void {
      this.$emit('cancel');
    },
    onFileUploaded(result: FileUploadResult): void {
      this.certFile = result.file;
      this.certFileTitle = result.file.name;
    },
    add(): void {
      this.loading = true;

      if (!this.certFile) return;

      this.ocspResponderServiceStore
        .addOcspResponder(this.ocspUrl, this.certFile)
        .then(() => {
          this.showSuccess(
            this.$t('trustServices.trustService.ocspResponders.add.success'),
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
