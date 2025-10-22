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
    title="diagnostics.encryption.messageLog.database.title"
    class="overview-card"
    :class="{ disabled: !messageLogEnabled }"
  >
    <template v-if="!messageLogEnabled" #append-title>
      <XrdStatusChip
        type="inactive"
        text="diagnostics.addOnStatus.messageLogDisabled"
      />
    </template>

    <div
      v-if="messageLogEncryptionDiagnostics"
      class="pl-4 pb-4 status"
      data-test="message-log-database-encryption-status"
    >
      <span class="mr-2">
        {{ $t('diagnostics.encryption.statusTitle') }}
      </span>
      <XrdStatusChip
        :type="messageLogEncryptionStatusType"
        :text="`diagnostics.encryption.status.${messageLogDatabaseEncryptionStatus}`"
      >
        <template #icon>
          <XrdStatusIcon
            class="mr-1 ml-n1"
            :status="messageLogEncryptionStatusIcon"
          />
        </template>
      </XrdStatusChip>
    </div>
    <XrdEmptyPlaceholder
      :loading="messageLogEncryptionLoading"
      :data="messageLogEncryptionDiagnostics"
      :no-items-text="$t('noData.noData')"
    />
  </XrdCard>
</template>
<script lang="ts">
import { defineComponent } from 'vue';
import { mapState } from 'pinia';
import { useDiagnostics } from '@/store/modules/diagnostics';
import { XrdCard, XrdStatusChip, statusToType } from '@niis/shared-ui';

type Status = 'ok' | 'pending' | 'error';
type Disabled = `${Status}-disabled`;
type StatusAndDisabled = Status | Disabled;

export default defineComponent({
  components: { XrdStatusChip, XrdCard },
  props: {
    messageLogEncryptionLoading: {
      type: Boolean,
    },
  },
  computed: {
    ...mapState(useDiagnostics, [
      'messageLogEnabled',
      'messageLogEncryptionDiagnostics',
    ]),
    messageLogDatabaseEncryptionStatus() {
      return this.messageLogEncryptionDiagnostics
        ?.message_log_database_encryption_status;
    },
    messageLogEncryptionStatusIcon(): StatusAndDisabled {
      if (this.messageLogEnabled) {
        return this.encryptionStatusIconType;
      } else {
        return (this.encryptionStatusIconType + '-disabled') as Disabled;
      }
    },
    messageLogEncryptionStatusType() {
      return statusToType(this.messageLogEncryptionStatusIcon);
    },
    encryptionStatusIconType(): Status {
      switch (this.messageLogDatabaseEncryptionStatus) {
        case true:
          return 'ok';
        case false:
          return 'pending';
        default:
          return 'error';
      }
    },
  },
  methods: {},
});
</script>
<style lang="scss" scoped>
.disabled {
  :deep(.v-card-title),
  :deep(.v-card-text) {
    background-color: rgba(var(--v-theme-on-surface-variant), 0.08) !important;
  }

  :deep(.component-title-text),
  .status {
    opacity: 0.6;
  }
}
</style>
