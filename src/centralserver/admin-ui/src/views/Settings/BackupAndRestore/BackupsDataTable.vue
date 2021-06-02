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
  <v-card flat class="pb-3">
    <xrd-table>
      <thead>
        <tr>
          <th>{{ $t('name') }}</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr :key="'conf_backup_20210505-105548.tar'">
          <td>conf_backup_20210505-105548.tar</td>
          <td>
            <div class="d-flex justify-end">
              <xrd-button
                :min_width="50"
                text
                :outlined="false"
                class="xrd-table-button"
                data-test="backup-download"
                >{{ $t('action.download') }}
              </xrd-button>
              <restore-backup-button />
              <delete-backup-button />
            </div>
          </td>
        </tr>
        <tr :key="'conf_backup_20210404-092511.tar'">
          <td>conf_backup_20210404-092511.tar</td>
          <td>
            <div class="d-flex justify-end">
              <xrd-button
                :min_width="50"
                text
                :outlined="false"
                class="xrd-table-button"
                data-test="backup-download"
                >{{ $t('action.download') }}
              </xrd-button>
              <restore-backup-button />
              <delete-backup-button />
            </div>
          </td>
        </tr>
      </tbody>
    </xrd-table>
  </v-card>
</template>

<script lang="ts">
import Vue from 'vue';
import DeleteBackupButton from '@/views/Settings/BackupAndRestore/DeleteBackupButton.vue';
import RestoreBackupButton from '@/views/Settings/BackupAndRestore/RestoreBackupButton.vue';

export default Vue.extend({
  components: {
    DeleteBackupButton,
    RestoreBackupButton,
  },
});
</script>
