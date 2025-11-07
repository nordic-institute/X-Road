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
  <XrdCard
    data-test="diagnostics-backup-encryption"
    title="diagnostics.encryption.backup.title"
    class="overview-card"
  >
    <div v-if="backupEncryptionDiagnostics">
      <div
        class="ml-4 mb-4 d-flex flex-row align-center"
        data-test="backup-encryption-status"
      >
        <span class="mr-2">
          {{ $t('diagnostics.encryption.statusTitle') }}
        </span>
        <XrdStatusChip
          :type="
            encryptionStatusType(
              backupEncryptionDiagnostics.backup_encryption_status,
            )
          "
          :text="`diagnostics.encryption.status.${backupEncryptionDiagnostics.backup_encryption_status}`"
        >
          <template #icon>
            <XrdStatusIcon
              class="mr-1 ml-n1"
              :status="
                encryptionStatusIcon(
                  backupEncryptionDiagnostics.backup_encryption_status,
                )
              "
            />
          </template>
        </XrdStatusChip>
      </div>

      <v-table
        v-if="backupEncryptionDiagnostics.backup_encryption_status"
        class="xrd"
        data-test="backup-encryption-keys"
      >
        <thead>
          <tr>
            <th>
              {{ $t('diagnostics.encryption.backup.configuredKeyId') }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="confKeys in backupEncryptionDiagnostics.backup_encryption_keys"
            :key="confKeys"
          >
            <td>
              {{ confKeys }}
            </td>
          </tr>
          <XrdEmptyPlaceholderRow
            :loading="backupEncryptionLoading"
            :data="backupEncryptionDiagnostics.backup_encryption_keys"
            :no-items-text="$t('noData.noBackUpEncryptionKeys')"
          />
        </tbody>
      </v-table>
    </div>
  </XrdCard>
</template>
<script lang="ts">
import { mapActions, mapState } from 'pinia';
import { useDiagnostics } from '@/store/modules/diagnostics';
import { defineComponent } from 'vue';
import {
  XrdCard,
  Status,
  XrdStatusChip,
  statusToType,
  useNotifications,
  XrdEmptyPlaceholderRow,
  XrdStatusIcon,
} from '@niis/shared-ui';

export default defineComponent({
  components: { XrdStatusIcon, XrdCard, XrdStatusChip, XrdEmptyPlaceholderRow },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data: () => ({
    backupEncryptionLoading: false,
  }),
  computed: {
    ...mapState(useDiagnostics, ['backupEncryptionDiagnostics']),
  },
  created() {
    this.backupEncryptionLoading = true;
    this.fetchBackupEncryptionDiagnostics()
      .catch((error) => {
        this.addError(error);
      })
      .finally(() => {
        this.backupEncryptionLoading = false;
      });
  },
  methods: {
    ...mapActions(useDiagnostics, ['fetchBackupEncryptionDiagnostics']),
    encryptionStatusIcon(enabled: boolean): Status {
      switch (enabled) {
        case true:
          return 'ok';
        case false:
          return 'pending';
        default:
          return 'error';
      }
    },
    encryptionStatusType(enabled: boolean) {
      return statusToType(this.encryptionStatusIcon(enabled));
    },
  },
});
</script>
<style lang="scss" scoped></style>
