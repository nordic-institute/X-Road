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
      v-if="showUploadAnchor"
      v-slot="{ upload }"
      accepts=".xml"
      @file-changed="onUploadFileChanged"
    >
      <xrd-button
        data-test="system-parameters-configuration-anchor-upload-button"
        outlined
        :loading="previewing"
        class="ml-5"
        @click="upload"
      >
        <xrd-icon-base class="xrd-large-button-icon">
          <xrd-icon-upload />
        </xrd-icon-base>
        {{
          $t('systemParameters.configurationAnchor.action.upload.button')
        }}</xrd-button
      ></xrd-file-upload
    >

    <v-dialog
      v-if="showPreview"
      :model-value="showPreview"
      persistent
      max-width="850"
    >
      <v-card class="xrd-card">
        <v-card-title>
          <span data-test="dialog-title" class="text-h5">
            {{
              $t(
                'systemParameters.configurationAnchor.action.upload.dialog.title',
              )
            }}
          </span>
        </v-card-title>
        <v-card-text class="content-wrapper">
          <v-container>
            <v-row class="mb-5">
              <v-col>
                {{
                  $t(
                    'systemParameters.configurationAnchor.action.upload.dialog.info',
                  )
                }}
              </v-col>
            </v-row>
            <v-row no-gutters>
              <v-col class="font-weight-bold" cols="12" sm="3">
                {{
                  $t(
                    'systemParameters.configurationAnchor.action.upload.dialog.field.hash',
                  )
                }}
              </v-col>
              <v-col cols="12" sm="9">{{
                $filters.colonize(anchorPreview.hash)
              }}</v-col>
            </v-row>
            <v-row no-gutters>
              <v-col class="font-weight-bold" cols="12" sm="3">
                {{
                  $t(
                    'systemParameters.configurationAnchor.action.upload.dialog.field.generated',
                  )
                }}
              </v-col>
              <v-col cols="12" sm="9">{{
                $filters.formatDateTime(anchorPreview.created_at)
              }}</v-col>
            </v-row>
            <v-row class="mt-5">
              <v-col>
                {{
                  $t(
                    'systemParameters.configurationAnchor.action.upload.dialog.confirmation',
                  )
                }}
              </v-col>
            </v-row>
          </v-container>
        </v-card-text>
        <v-card-actions class="xrd-card-actions">
          <v-spacer></v-spacer>
          <xrd-button
            data-test="system-parameters-upload-configuration-anchor-dialog-cancel-button"
            outlined
            @click="close"
            >{{ $t('action.cancel') }}</xrd-button
          >
          <xrd-button
            data-test="system-parameters-upload-configuration-anchor-dialog-confirm-button"
            :loading="uploading"
            @click="confirmUpload"
            >{{ $t('action.confirm') }}</xrd-button
          >
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Permissions } from '@/global';
import * as api from '@/util/api';
import { Anchor } from '@/openapi-types';
import { PostPutPatch } from '@/util/api';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import { FileUploadResult } from '@/ui-types';
import { XrdIconUpload } from '@niis/shared-ui';

const EmptyAnchorPreview: Anchor = {
  hash: '',
  created_at: '',
};

export default defineComponent({
  name: 'UploadConfigurationAnchorDialog',
  components: { XrdIconUpload },
  props: {
    initMode: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['uploaded'],
  data() {
    return {
      previewing: false as boolean,
      uploading: false as boolean,
      anchorPreview: EmptyAnchorPreview,
      uploadedFile: null as string | ArrayBuffer | null,
      showPreview: false as boolean,
      permissions: Permissions,
      anchorFile: undefined as string | undefined,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    showUploadAnchor(): boolean {
      return this.hasPermission(Permissions.UPLOAD_ANCHOR);
    },
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    onUploadFileChanged(event: FileUploadResult): void {
      if (this.initMode) {
        this.previewAnchor(
          event,
          '/system/anchor/previews?validate_instance=false',
        );
      } else {
        this.previewAnchor(event, '/system/anchor/previews');
      }
    },

    previewAnchor(event: FileUploadResult, query: string): void {
      this.previewing = true;
      api
        .post<Anchor>(query, event.buffer, {
          headers: {
            'Content-Type': 'application/octet-stream',
          },
        })
        .then((resp) => {
          this.uploadedFile = event.buffer;
          this.anchorPreview = resp.data;
          this.showPreview = true;
        })
        .catch((error) => {
          this.showError(error);
          // Clear the anchor file
          this.anchorFile = undefined;
        })
        .finally(() => (this.previewing = false));
    },

    confirmUpload(): void {
      if (this.initMode) {
        this.uploadAnchor(api.post);
      } else {
        this.uploadAnchor(api.put);
      }
    },

    uploadAnchor(apiCall: PostPutPatch): void {
      this.uploading = true;
      apiCall('/system/anchor', this.uploadedFile, {
        headers: {
          'Content-Type': 'application/octet-stream',
        },
      })
        .then(() => {
          this.showSuccess(
            this.$t(
              'systemParameters.configurationAnchor.action.upload.dialog.success',
            ),
          );
          this.$emit('uploaded');
        })
        .catch((error) => this.showError(error))
        .finally(() => {
          this.uploading = false;
          this.close();
        });
    },
    close(): void {
      this.previewing = false;
      this.showPreview = false;
      this.anchorPreview = EmptyAnchorPreview;
    },
  },
});
</script>
