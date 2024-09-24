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
            {{ $t('diagnostics.encryption.messageLog.archive.title') }}
          </h3>
        </v-col>
        <v-col v-if="!messageLogEnabled" class="text-right disabled">
          {{ $t('diagnostics.addOnStatus.messageLogDisabled') }}
        </v-col>
      </v-row>

      <div v-if="messageLogEncryptionDiagnostics">
        <div
          class="sub-title status-wrapper"
          data-test="message-log-archive-encryption-status"
        >
          <span>
            {{ $t('diagnostics.encryption.statusTitle') }}
          </span>
          <xrd-status-icon
            :status="
              messageLogEncryptionStatusIconType(
                messageLogEncryptionDiagnostics.message_log_archive_encryption_status,
              )
            "
          />
          {{
            $t(
              `diagnostics.encryption.status.${messageLogEncryptionDiagnostics.message_log_archive_encryption_status}`,
            )
          }}
          <span class="group-name">
            {{ $t('diagnostics.encryption.messageLog.archive.groupingTitle') }}
          </span>
          {{ messageLogEncryptionDiagnostics.message_log_grouping_rule }}
        </div>
        <table
          v-if="
            messageLogEncryptionDiagnostics.message_log_archive_encryption_status
          "
          class="xrd-table"
          data-test="member-encryption-status"
        >
          <thead>
            <tr>
              <th>
                {{
                  $t(
                    'diagnostics.encryption.messageLog.archive.memberIdentifier',
                  )
                }}
              </th>
              <th>
                {{ $t('diagnostics.encryption.messageLog.archive.keyId') }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="member in messageLogEncryptionDiagnostics.members"
              :key="member.member_id"
            >
              <td :class="{ disabled: !messageLogEnabled }">
                {{ member.member_id }}
              </td>
              <td
                class="status-wrapper"
                :class="{ disabled: !messageLogEnabled }"
              >
                {{ $filters.commaSeparate(member.keys ?? []) }}
                <v-tooltip
                  v-if="member.default_key_used"
                  max-width="267px"
                  location="right"
                >
                  <template #activator="{ props }">
                    <xrd-icon-base
                      v-bind="props"
                      :class="messageLogEncryptionTooltipIconType"
                    >
                      <xrd-icon-error />
                    </xrd-icon-base>
                  </template>
                  <span>{{
                    $t(
                      'diagnostics.encryption.messageLog.archive.defaultKeyNote',
                    )
                  }}</span>
                </v-tooltip>
              </td>
            </tr>
            <XrdEmptyPlaceholderRow
              :colspan="2"
              :loading="messageLogEncryptionLoading || addonStatusLoading"
              :data="messageLogEncryptionDiagnostics.members"
              :no-items-text="$t('noData.noData')"
            />
          </tbody>
        </table>
      </div>
    </v-card-text>
  </v-card>
</template>
<script lang="ts">
import { mapState } from 'pinia';
import { useDiagnostics } from '@/store/modules/diagnostics';
import { defineComponent } from 'vue';

export default defineComponent({
  props: {
    addonStatusLoading: {
      type: Boolean,
    },
    messageLogEncryptionLoading: {
      type: Boolean,
    },
  },
  computed: {
    ...mapState(useDiagnostics, [
      'messageLogEnabled',
      'messageLogEncryptionDiagnostics',
    ]),
    messageLogEncryptionTooltipIconType(): string {
      return this.messageLogEnabled === false ? 'disabled' : 'warning-icon';
    },
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
@import '@/assets/colors';
@import '@/assets/tables';

h3 {
  color: $XRoad-Black100;
  font-size: 24px;
  font-weight: 400;
  letter-spacing: normal;
  line-height: 2rem;
}

.disabled {
  cursor: not-allowed;
  background: $XRoad-Black10;
  color: $XRoad-WarmGrey100;
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
  font-size: $XRoad-DefaultFontSize;
  line-height: 20px;
  color: $XRoad-Black100;

  span {
    font-style: normal;
    font-weight: normal;
    font-size: $XRoad-DefaultFontSize;
    line-height: 20px;
    padding-right: 16px;
  }
}

.group-name {
  padding-left: 32px;
}
</style>
