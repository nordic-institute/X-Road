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
    data-test="backup-restore"
    class="xrd-table-button"
    :min-width="50"
    text
    @click="showConfirmation = true"
  >
    {{ $t('action.restore') }}
    <xrd-confirm-dialog
      v-if="showConfirmation"
      :loading="restoring"
      title="backup.restoreFromBackup.dialog.title"
      text="backup.restoreFromBackup.dialog.confirmation"
      :data="{ file: filename }"
      @cancel="showConfirmation = false"
      @accept="restoreFromBackup"
    />
  </xrd-button>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { BackupHandler } from '../../types';
import XrdButton from "../XrdButton.vue";
import XrdConfirmDialog from "../XrdConfirmDialog.vue";
export default defineComponent({
  components: {
    XrdButton,
    XrdConfirmDialog
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
  emits: ['refresh-backups'],
  data() {
    return {
      showConfirmation: false,
      restoring: false
    };
  },
  computed: {},
  methods: {
    restoreFromBackup() {
      this.restoring = true;
      this.backupHandler
        .restore(this.filename)
        .then(() =>
          this.backupHandler.showSuccess('backup.restoreFromBackup.success', {
            file: this.filename,
          }),
        )
        .then(() => this.$emit('refresh-backups'))
        .catch((error) => this.backupHandler.showError(error))
        .finally(() => {
          this.showConfirmation = false;
          this.restoring = false;
        });
    }
  }
});

</script>

<style lang="scss" scoped></style>
