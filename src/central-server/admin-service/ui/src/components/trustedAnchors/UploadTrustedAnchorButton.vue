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
  <div>
    <xrd-file-upload
      v-slot="{ upload }"
      accepts=".xml"
      @file-changed="onFileUploaded"
    >
      <xrd-button
        v-if="canUpload"
        data-test="upload-anchor-button"
        color="primary"
        :loading="uploading"
        class="button-spacing"
        @click="upload"
      >
        <v-icon class="xrd-large-button-icon">icon-Upload</v-icon>
        {{ $t('action.upload') }}
      </xrd-button>
    </xrd-file-upload>
    <upload-trusted-anchor-dialog
      v-if="file && preview"
      ref="dialog"
      :file="file"
      :preview="preview"
      @uploaded="$emit('uploaded')"
      @close="clear"
      @uploade="clear(true)"
    />
  </div>
</template>
<script lang="ts">
import Vue, { VueConstructor } from 'vue';
import { mapActions, mapState, mapStores } from 'pinia';
import { userStore } from '@/store/modules/user';
import { Permissions } from '@/global';
import { FileUploadResult } from '@niis/shared-ui';
import UploadTrustedAnchorDialog from './UploadTrustedAnchorDialog.vue';
import { trustedAnchorStore } from '@/store/modules/trusted-anchors';
import { TrustedAnchor } from '@/openapi-types';
import { notificationsStore } from '@/store/modules/notifications';

export default (
  Vue as VueConstructor<
    Vue & {
      $refs: {
        dialog: InstanceType<typeof UploadTrustedAnchorDialog>;
      };
    }
  >
).extend({
  components: { UploadTrustedAnchorDialog },
  data() {
    return {
      uploading: false,
      preview: null as TrustedAnchor | null,
      file: null as File | null,
    };
  },
  computed: {
    ...mapState(userStore, ['hasPermission']),
    ...mapStores(trustedAnchorStore),
    canUpload(): boolean {
      return this.hasPermission(Permissions.UPLOAD_TRUSTED_ANCHOR);
    },
  },
  methods: {
    ...mapActions(notificationsStore, ['showSuccess', 'showError']),
    onFileUploaded(result: FileUploadResult) {
      this.file = result.file;
      this.uploading = true;
      this.trustedAnchorStore
        .previewTrustedAnchors(result.file)
        .then((resp) => (this.preview = resp.data))
        .then(() => this.$refs.dialog.open())
        .catch((error) => this.showError(error))
        .finally(() => (this.uploading = false));
    },
    clear(uploaded = false) {
      if (uploaded) {
        this.$emit('uploaded');
      }
      this.preview = null;
      this.file = null;
    },
  },
});
</script>
<style lang="scss" scoped></style>
