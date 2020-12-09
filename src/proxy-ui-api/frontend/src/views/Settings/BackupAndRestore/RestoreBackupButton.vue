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
  <large-button
    :min_width="50"
    text
    :outlined="false"
    class="xrd-table-button"
    data-test="backup-restore"
    @click="showConfirmation = true"
    >{{ $t('action.restore') }}
    <confirm-dialog
      :dialog="showConfirmation"
      :loading="restoring"
      title="backup.action.restore.dialog.title"
      text="backup.action.restore.dialog.confirmation"
      :data="{ file: backup.filename }"
      @cancel="showConfirmation = false"
      @accept="restoreBackup"
    />
  </large-button>
</template>

<script lang="ts">
import Vue from 'vue';
import { Prop } from 'vue/types/options';
import { Backup } from '@/openapi-types';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  name: 'RestoreBackupButton',
  props: {
    backup: {
      type: Object as Prop<Backup>,
      required: true,
    },
  },
  data() {
    return {
      showConfirmation: false as boolean,
      restoring: false as boolean,
    };
  },
  methods: {
    restoreBackup() {
      this.restoring = true;
      api
        .put(
          `/backups/${encodePathParameter(this.backup.filename)}/restore`,
          {},
        )
        .then(() => {
          this.$emit('restored');
          this.$store.dispatch(
            'showSuccessRaw',
            this.$t('backup.action.restore.success', {
              file: this.backup.filename,
            }),
          );
        })
        .catch((error) => this.$store.dispatch('showError', error))
        .finally(() => {
          this.showConfirmation = false;
          this.restoring = false;
          this.$store.dispatch('checkAlertStatus');
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
</style>
