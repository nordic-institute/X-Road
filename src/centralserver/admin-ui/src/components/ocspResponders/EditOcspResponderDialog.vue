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
    :disable-save="!formReady"
    :dialog="true"
    title="trustServices.trustService.ocspResponders.edit.dialog.title"
    save-button-text="action.save"
    cancel-button-text="action.cancel"
    @cancel="cancel"
    @save="update"
  >
    <template #content>
      <div class="dlg-input-width">
        <v-text-field
          v-model="ocspUrl"
          :label="$t('trustServices.trustService.ocspResponders.url')"
          outlined
          persistent-hint
          data-test="ocsp-responder-url-input"
        ></v-text-field>
      </div>

      <div v-if="!certUploadActive">
        <div class="dlg-input-width mb-6">
          <xrd-button outlined class="mr-3" @click="navigateToCertificateDetails()">
            {{ $t('trustServices.viewCertificate') }}
          </xrd-button>
          <xrd-button text @click="certUploadActive = true">
            <v-icon class="xrd-large-button-icon">icon-Upload</v-icon>
            {{ $t('trustServices.trustService.ocspResponders.edit.dialog.uploadCertificate') }}
          </xrd-button>
        </div>
      </div>
      <div v-else>
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
              @click="upload"
            ></v-text-field>
          </xrd-file-upload>
        </div>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import Vue from "vue";
import {mapActions, mapStores} from "pinia";
import {useOcspResponderStore} from "@/store/modules/trust-services";
import {notificationsStore} from "@/store/modules/notifications";
import {FileUploadResult} from "@niis/shared-ui";
import {OcspResponder} from "@/openapi-types";
import {RouteName} from "@/global";

export default Vue.extend({
  name: 'EditOcspResponderDialog',
  props: {
    ocspResponder: {
      type: Object as () => OcspResponder
    }
  },
  data() {
    return {
      ocspUrl: this.ocspResponder.url,
      certFile: null as File | null,
      certFileTitle: '',
      certUploadActive: false
    };
  },
  computed: {
    ...mapStores(useOcspResponderStore),
    formReady(): boolean {
      return !!this.ocspUrl;
    },
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    cancelEdit(): void {
      this.$emit('cancel');
    },
    navigateToCertificateDetails() {
      this.$router.push({
        name: RouteName.OcspResponderCertificateDetails,
        params: {
          ocspResponderId: String(this.ocspResponder.id)
        }
      });
    },
    onFileUploaded(result: FileUploadResult): void {
      this.certFile = result.file;
      this.certFileTitle = result.file.name;
    },
    update(): void {
      this.ocspResponderServiceStore.updateOcspResponder(this.ocspResponder.id, this.ocspUrl, this.certFile)
        .then(() => {
          this.showSuccess(this.$t('trustServices.trustService.ocspResponders.edit.success'));
          this.$emit('save');
        })
        .catch((error) => {
          this.showError(error);
        });
    },
    cancel(): void {
      this.$emit('cancel');
    },
  }
});
</script>
