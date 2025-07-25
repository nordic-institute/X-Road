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
  <xrd-button
    v-if="canBackup"
    data-test="backup-delete"
    class="xrd-table-button"
    :min-width="50"
    text
    :loading="deleting"
    :outlined="false"
    @click="showConfirmation = true"
  >
    {{ $t('action.delete') }}
    <xrd-confirm-dialog
      v-if="showConfirmation"
      title="backup.deleteBackup.dialog.title"
      text="backup.deleteBackup.dialog.confirmation"
      focus-on-accept
      :data="{ file: filename }"
      @cancel="showConfirmation = false"
      @accept="deleteBackup"
    />
  </xrd-button>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { BackupHandler } from '../../utils';
import XrdButton from '../XrdButton.vue';
import XrdConfirmDialog from '../XrdConfirmDialog.vue';

export default defineComponent({
  components: {
    XrdButton,
    XrdConfirmDialog,
  },
  props: {
    canBackup: {
      type: Boolean,
      required: true,
    },
    filename: {
      type: String,
      required: true,
    },
    backupHandler: {
      type: Object as PropType<BackupHandler>,
      required: true,
    },
  },
  emits: ['delete'],
  data() {
    return { showConfirmation: false, deleting: false };
  },
  methods: {
    deleteBackup() {
      this.deleting = true;
      this.showConfirmation = false;
      this.backupHandler
        .delete(this.filename)
        .then(() =>
          this.backupHandler.showSuccess('backup.deleteBackup.success', {
            file: this.filename,
          }),
        )
        .then(() => this.$emit('delete'))
        .catch((error) => this.backupHandler.showError(error))
        .finally(() => (this.deleting = false));
    },
  },
});
</script>

<style lang="scss" scoped></style>
