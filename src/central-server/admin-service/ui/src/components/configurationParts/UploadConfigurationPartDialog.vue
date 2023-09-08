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
    :dialog="true"
    title="globalConf.cfgParts.dialog.upload.title"
    save-button-text="action.upload"
    cancel-button-text="action.cancel"
    :loading="loading"
    :disable-save="!partFile || !partFileTitle"
    @save="save"
    @cancel="$emit('cancel')"
  >
    <template #content>
      <div class="dlg-input-width">
        <xrd-file-upload
          v-slot="{ upload }"
          accepts=".xml"
          @file-changed="onFileUploaded"
        >
          <v-text-field
            v-model="partFileTitle"
            variant="outlined"
            :label="
              $t('globalConf.cfgParts.dialog.upload.uploadConfigurationPart')
            "
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
import { defineComponent, PropType } from 'vue';
import { mapActions, mapStores } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useConfigurationSource } from '@/store/modules/configuration-sources';
import {
  ConfigurationPartContentIdentifier,
  ConfigurationType,
} from '@/openapi-types';
import { FileUploadResult, XrdFileUpload } from '@niis/shared-ui';

export default defineComponent({
  components: { XrdFileUpload },
  props: {
    configurationType: {
      type: String as PropType<ConfigurationType>,
      required: true,
    },
    contentIdentifier: {
      type: String as PropType<ConfigurationPartContentIdentifier>,
      required: true,
    },
  },
  emits: ['save', 'cancel'],
  data() {
    return {
      partFile: null as File | null,
      partFileTitle: '',
      loading: false,
    };
  },
  computed: {
    ...mapStores(useConfigurationSource),
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    onFileUploaded(result: FileUploadResult): void {
      this.partFile = result.file;
      this.partFileTitle = result.file.name;
    },
    save(): void {
      if (!this.partFile) return;

      this.loading = true;
      this.configurationSourceStore
        .uploadConfigurationFile(
          this.configurationType,
          this.contentIdentifier,
          this.partFile,
        )
        .then(() => {
          this.showSuccess(
            this.$t('globalConf.cfgParts.dialog.upload.success'),
          );
          this.$emit('save');
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => (this.loading = false));
    },
  },
});
</script>
