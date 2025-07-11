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
  <v-card variant="flat" class="xrd-card diagnostic-card">
    <v-card-title class="text-h5" data-test="diagnostics-backup-encryption">
      {{ $t('diagnostics.encryption.backup.title') }}
    </v-card-title>

    <v-card-text class="xrd-card-text">
      <div v-if="backupEncryptionDiagnostics">
        <div
          class="sub-title status-wrapper"
          data-test="backup-encryption-status"
        >
          <span>
            {{ $t('diagnostics.encryption.statusTitle') }}
          </span>
          <xrd-status-icon
            :status="
              encryptionStatusIconType(
                backupEncryptionDiagnostics.backup_encryption_status,
              )
            "
          />
          {{
            $t(
              `diagnostics.encryption.status.${backupEncryptionDiagnostics.backup_encryption_status}`,
            )
          }}
        </div>

        <table
          v-if="backupEncryptionDiagnostics.backup_encryption_status"
          class="xrd-table"
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
        </table>
      </div>
    </v-card-text>
  </v-card>
</template>
<script lang="ts">
import { mapActions, mapState } from 'pinia';
import { useDiagnostics } from '@/store/modules/diagnostics';
import { useNotifications } from '@/store/modules/notifications';
import { defineComponent } from 'vue';

export default defineComponent({
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
        this.showError(error);
      })
      .finally(() => {
        this.backupEncryptionLoading = false;
      });
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
    ...mapActions(useDiagnostics, ['fetchBackupEncryptionDiagnostics']),
    encryptionStatusIconType(enabled: boolean): string {
      switch (enabled) {
        case true:
          return 'ok';
        case false:
          return 'pending';
        default:
          return 'error';
      }
    },
  },
});
</script>
<style lang="scss" scoped>
@use '@niis/shared-ui/src/assets/colors';
@use '@niis/shared-ui/src/assets/tables';

.xrd-card-text {
  padding-left: 0;
  padding-right: 0;
}

.diagnostic-card {
  width: 100%;
  margin-bottom: 30px;

  &:first-of-type {
    margin-top: 40px;
  }
}

.status-wrapper {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.sub-title {
  margin-top: 30px;
  margin-left: 16px;

  font-style: normal;
  font-weight: bold;
  font-size: colors.$DefaultFontSize;
  line-height: 20px;
  color: colors.$Black100;

  span {
    font-style: normal;
    font-weight: normal;
    font-size: colors.$DefaultFontSize;
    line-height: 20px;
    padding-right: 16px;
  }
}
</style>
