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
    :title="$t('globalConf.trustedAnchor.dialog.upload.title')"
    save-button-text="action.confirm"
    :loading="uploading"
    width="850"
    @cancel="close"
    @save="confirm"
  >
    <template #text>
      <v-container>
        <v-row class="mb-5">
          <v-col>
            {{ $t('globalConf.trustedAnchor.dialog.upload.info') }}
          </v-col>
        </v-row>
        <v-row no-gutters>
          <v-col class="font-weight-bold" cols="12" sm="3">
            {{ $t('globalConf.trustedAnchor.dialog.upload.field.hash') }}
          </v-col>
          <v-col cols="12" sm="9">{{ preview.hash }}</v-col>
        </v-row>
        <v-row no-gutters>
          <v-col class="font-weight-bold" cols="12" sm="3">
            {{ $t('globalConf.trustedAnchor.dialog.upload.field.generated') }}
          </v-col>
          <v-col cols="12" sm="9">
            <date-time :value="preview.generated_at" />
          </v-col>
        </v-row>
        <v-row class="mt-5">
          <v-col>
            {{ $t('globalConf.trustedAnchor.dialog.upload.confirmation') }}
          </v-col>
        </v-row>
      </v-container>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { TrustedAnchor } from '@/openapi-types';
import { mapActions, mapStores } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useTrustedAnchor } from '@/store/modules/trusted-anchors';
import DateTime from '@/components/ui/DateTime.vue';

export default defineComponent({
  components: { DateTime },
  props: {
    preview: {
      type: Object as PropType<TrustedAnchor>,
      required: true,
    },
    file: {
      type: File,
      required: true,
    },
  },
  emits: ['uploaded', 'close'],
  data() {
    return {
      opened: false,
      uploading: false,
    };
  },
  computed: {
    ...mapStores(useTrustedAnchor),
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    open() {
      this.opened = true;
    },
    close() {
      this.opened = false;
      this.$emit('close');
    },
    confirm() {
      this.uploading = true;
      this.trustedAnchorStore
        .uploadTrustedAnchor(this.file)
        .then(() =>
          this.showSuccess(
            this.$t('globalConf.trustedAnchor.dialog.upload.success'),
          ),
        )
        .then(() => (this.opened = false))
        .then(() => this.$emit('uploaded'))
        .catch((error) => this.showError(error))
        .finally(() => (this.uploading = false));
    },
  },
});
</script>
