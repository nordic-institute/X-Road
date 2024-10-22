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
  <v-card
    variant="flat"
    class="xrd-card diagnostic-card"
    :class="{ disabled: !messageLogEnabled }"
  >
    <v-card-text class="xrd-card-text">
      <v-row no-gutters class="px-4">
        <v-col>
          <h3 :class="{ disabled: !messageLogEnabled }">
            {{ $t('diagnostics.encryption.messageLog.database.title') }}
          </h3>
        </v-col>
        <v-col v-if="!messageLogEnabled" class="text-right disabled">
          {{ $t('diagnostics.addOnStatus.messageLogDisabled') }}
        </v-col>
      </v-row>

      <div
        v-if="messageLogEncryptionDiagnostics"
        class="sub-title status-wrapper"
        data-test="message-log-database-encryption-status"
      >
        <span>
          {{ $t('diagnostics.encryption.statusTitle') }}
        </span>
        <xrd-status-icon
          :status="
            messageLogEncryptionStatusIconType(
              messageLogEncryptionDiagnostics.message_log_database_encryption_status,
            )
          "
        />
        {{
          $t(
            `diagnostics.encryption.status.${messageLogEncryptionDiagnostics.message_log_database_encryption_status}`,
          )
        }}
      </div>
      <XrdEmptyPlaceholder
        :loading="messageLogEncryptionLoading"
        :data="messageLogEncryptionDiagnostics"
        :no-items-text="$t('noData.noData')"
      />
    </v-card-text>
  </v-card>
</template>
<script lang="ts">
import { defineComponent } from 'vue';
import { mapState } from 'pinia';
import { useDiagnostics } from '@/store/modules/diagnostics';

export default defineComponent({
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
  },
  methods: {
    messageLogEncryptionStatusIconType(enabled: boolean): string {
      if (this.messageLogEnabled) {
        return this.encryptionStatusIconType(enabled);
      } else {
        return this.encryptionStatusIconType(enabled) + '-disabled';
      }
    },

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
@use '@/assets/colors';
@use '@/assets/tables';

h3 {
  color: colors.$Black100;
  font-size: 24px;
  font-weight: 400;
  letter-spacing: normal;
  line-height: 2rem;
}

.disabled {
  cursor: not-allowed;
  background: colors.$Black10;
  color: colors.$WarmGrey100;
}

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
