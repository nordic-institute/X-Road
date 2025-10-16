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
  <XrdBtn
    v-if="canBackup"
    data-test="backup-delete"
    variant="text"
    color="tertiary"
    text="action.delete"
    :loading="deleting"
    @click="showConfirmation = true"
  />
  <xrd-confirm-dialog
    v-if="showConfirmation"
    title="backup.deleteBackup.dialog.title"
    text="backup.deleteBackup.dialog.confirmation"
    focus-on-accept
    :data="{ file: filename }"
    @cancel="showConfirmation = false"
    @accept="deleteBackup"
  />
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';

import { mapActions } from 'pinia';

import { useNotifications } from '../../composables';
import { BackupHandler } from '../../types';

import { XrdBtn, XrdConfirmDialog } from '../../components';

export default defineComponent({
  components: {
    XrdBtn,
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
    ...mapActions(useNotifications, ['addError', 'addSuccessMessage']),
    deleteBackup() {
      this.deleting = true;
      this.showConfirmation = false;
      this.backupHandler
        .delete(this.filename)
        .then(() =>
          this.addSuccessMessage('backup.deleteBackup.success', {
            file: this.filename,
          }),
        )
        .then(() => this.$emit('delete'))
        .catch((error) => this.addError(error))
        .finally(() => (this.deleting = false));
    },
  },
});
</script>

<style lang="scss" scoped></style>
