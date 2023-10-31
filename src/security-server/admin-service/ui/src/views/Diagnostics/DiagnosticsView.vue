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
  <v-container fluid class="xrd-view-common px-7" data-test="diagnostics-view">
    <div class="xrd-view-title pt-6">{{ $t('tab.main.diagnostics') }}</div>
    <v-row align="center" justify="center" class="fill-height elevation-0">
      <v-card variant="flat" class="xrd-card diagnostic-card">
        <v-card-title class="text-h5" data-test="diagnostics-java-version">
          {{ $t('diagnostics.javaVersion.title') }}
        </v-card-title>

        <v-card-text class="xrd-card-text">
          <table class="xrd-table">
            <thead>
              <tr>
                <th class="status-column">{{ $t('diagnostics.status') }}</th>
                <th>{{ $t('diagnostics.message') }}</th>
                <th class="level-column">
                  {{ $t('diagnostics.javaVersion.vendor') }}
                </th>
                <th class="level-column">
                  {{ $t('diagnostics.javaVersion.title') }}
                </th>
                <th class="level-column">
                  {{ $t('diagnostics.javaVersion.earliest') }}
                </th>
                <th class="level-column">
                  {{ $t('diagnostics.javaVersion.latest') }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td data-test="java-icon">
                  <xrd-status-icon
                    v-if="securityServerVersion.using_supported_java_version"
                    status="ok"
                  />
                  <xrd-status-icon v-else status="error" />
                </td>
                <td
                  v-if="securityServerVersion.using_supported_java_version"
                  data-test="java-message"
                >
                  {{ $t('diagnostics.javaVersion.ok') }}
                </td>
                <td v-else data-test="java-message">
                  {{ $t('diagnostics.javaVersion.notSupported') }}
                </td>
                <td data-test="java-vendor">
                  {{ securityServerVersion.java_vendor }}
                </td>
                <td data-test="java-version">
                  {{ securityServerVersion.java_version }}
                </td>
                <td data-test="java-min">
                  {{ securityServerVersion.min_java_version }}
                </td>
                <td data-test="java-max">
                  {{ securityServerVersion.max_java_version }}
                </td>
              </tr>
            </tbody>
          </table>
        </v-card-text>
      </v-card>

      <v-card variant="flat" class="xrd-card diagnostic-card">
        <v-card-title
          class="text-h5"
          data-test="diagnostics-global-configuration"
        >
          {{ $t('diagnostics.globalConfiguration.title') }}
        </v-card-title>
        <v-card-text class="xrd-card-text">
          <table class="xrd-table">
            <thead>
              <tr>
                <th class="status-column">{{ $t('diagnostics.status') }}</th>
                <th>{{ $t('diagnostics.message') }}</th>
                <th class="time-column">
                  {{ $t('diagnostics.previousUpdate') }}
                </th>
                <th class="time-column">
                  {{ $t('diagnostics.nextUpdate') }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="globalConf">
                <td>
                  <xrd-status-icon
                    :status="statusIconType(globalConf.status_class)"
                  />
                </td>

                <td data-test="global-configuration-message">
                  {{
                    $t(
                      `diagnostics.globalConfiguration.configurationStatus.${globalConf.status_code}`,
                    )
                  }}
                </td>
                <td class="time-column">
                  {{ $filters.formatHoursMins(globalConf.prev_update_at) }}
                </td>
                <td class="time-column">
                  {{ $filters.formatHoursMins(globalConf.next_update_at) }}
                </td>
              </tr>
              <XrdEmptyPlaceholderRow
                :colspan="4"
                :loading="globalConfLoading"
                :data="globalConf"
                :no-items-text="$t('noData.noTimestampingServices')"
              />
            </tbody>
          </table>
        </v-card-text>
      </v-card>

      <v-card
        variant="flat"
        class="xrd-card diagnostic-card"
        :class="{ disabled: !messageLogEnabled }"
      >
        <v-card-text class="xrd-card-text">
          <v-row no-gutters class="px-4">
            <v-col>
              <h3 :class="{ disabled: !messageLogEnabled }">
                {{ $t('diagnostics.timestamping.title') }}
              </h3>
            </v-col>
            <v-col v-if="!messageLogEnabled" class="text-right disabled">
              {{ $t('diagnostics.addOnStatus.messageLogDisabled') }}
            </v-col>
          </v-row>

          <table class="xrd-table">
            <thead>
              <tr>
                <th class="status-column">{{ $t('diagnostics.status') }}</th>
                <th class="url-column">{{ $t('diagnostics.serviceUrl') }}</th>
                <th>{{ $t('diagnostics.message') }}</th>
                <th class="time-column">
                  {{ $t('diagnostics.previousUpdate') }}
                </th>
                <th class="time-column"></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="timestampingService in timestampingServices"
                :key="timestampingService.url"
              >
                <td>
                  <xrd-status-icon
                    :status="
                      statusIconTypeTSP(timestampingService.status_class)
                    "
                  />
                </td>
                <td
                  class="url-column"
                  :class="{ disabled: !messageLogEnabled }"
                  data-test="service-url"
                >
                  {{ timestampingService.url }}
                </td>
                <td
                  :class="{ disabled: !messageLogEnabled }"
                  data-test="timestamping-message"
                >
                  {{
                    $t(
                      `diagnostics.timestamping.timestampingStatus.${timestampingService.status_code}`,
                    )
                  }}
                </td>
                <td
                  class="time-column"
                  :class="{ disabled: !messageLogEnabled }"
                >
                  {{
                    $filters.formatHoursMins(timestampingService.prev_update_at)
                  }}
                </td>
                <td></td>
              </tr>
              <XrdEmptyPlaceholderRow
                :colspan="5"
                :loading="timestampingLoading || addonStatusLoading"
                :data="timestampingServices"
                :no-items-text="$t('noData.noTimestampingServices')"
              />
            </tbody>
          </table>
        </v-card-text>
      </v-card>

      <v-card variant="flat" class="xrd-card diagnostic-card">
        <v-card-title class="text-h5" data-test="diagnostics-ocsp-responders">
          {{ $t('diagnostics.ocspResponders.title') }}
        </v-card-title>
        <v-card-text class="xrd-card-text">
          <XrdEmptyPlaceholder
            :loading="ocspLoading"
            :data="ocspResponderDiagnostics"
            :no-items-text="$t('noData.noData')"
          />

          <div
            v-for="ocspDiags in ocspResponderDiagnostics"
            :key="ocspDiags.distinguished_name"
          >
            <div class="sub-title">
              <span>{{
                $t('diagnostics.ocspResponders.certificationService')
              }}</span>
              {{ ocspDiags.distinguished_name }}
            </div>
            <table class="xrd-table">
              <thead>
                <tr>
                  <th class="status-column">
                    {{ $t('diagnostics.status') }}
                  </th>
                  <th class="url-column">
                    {{ $t('diagnostics.serviceUrl') }}
                  </th>
                  <th>{{ $t('diagnostics.message') }}</th>
                  <th class="time-column">
                    {{ $t('diagnostics.previousUpdate') }}
                  </th>
                  <th class="time-column">
                    {{ $t('diagnostics.nextUpdate') }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="ocsp in ocspDiags.ocsp_responders" :key="ocsp.url">
                  <td>
                    <xrd-status-icon
                      :status="statusIconType(ocsp.status_class)"
                    />
                  </td>
                  <td class="url-column" data-test="service-url">
                    {{ ocsp.url }}
                  </td>
                  <td data-test="ocsp-responders-message">
                    {{
                      $t(
                        `diagnostics.ocspResponders.ocspStatus.${ocsp.status_code}`,
                      )
                    }}
                  </td>
                  <td class="time-column">
                    {{ $filters.formatHoursMins(ocsp.prev_update_at ?? '') }}
                  </td>
                  <td class="time-column">
                    {{ $filters.formatHoursMins(ocsp.next_update_at) }}
                  </td>
                </tr>
                <XrdEmptyPlaceholderRow
                  :colspan="4"
                  :loading="ocspLoading"
                  :data="ocspDiags"
                  :no-items-text="$t('noData.noCertificateAuthorities')"
                />
              </tbody>
            </table>
          </div>
        </v-card-text>
      </v-card>

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
                {{
                  $t('diagnostics.encryption.messageLog.archive.groupingTitle')
                }}
              </span>
              {{
                $t(
                  `${messageLogEncryptionDiagnostics.message_log_grouping_rule}`,
                )
              }}
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
                        <v-icon
                          size="small"
                          :class="
                            messageLogEncryptionTooltipIconType(
                              messageLogEnabled,
                            )
                          "
                          v-bind="props"
                          icon="icon-Error"
                        />
                        <xrd-icon-base
                          v-bind="props"
                          :class="
                            messageLogEncryptionTooltipIconType(
                              messageLogEnabled,
                            )
                          "
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
    </v-row>
  </v-container>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import * as api from '@/util/api';
import {
  BackupEncryptionStatus,
  MessageLogEncryptionStatus,
  OcspResponderDiagnostics,
  GlobalConfDiagnostics,
  AddOnStatus,
  TimestampingServiceDiagnostics,
} from '@/openapi-types';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useSystem } from '@/store/modules/system';
import { XrdIconError } from '@niis/shared-ui';

export default defineComponent({
  components: { XrdIconError },
  data: () => ({
    messageLogEnabled: false,
    timestampingServices: [] as TimestampingServiceDiagnostics[],
    globalConf: undefined as GlobalConfDiagnostics | undefined,
    ocspResponderDiagnostics: [] as OcspResponderDiagnostics[],
    backupEncryptionDiagnostics: undefined as
      | BackupEncryptionStatus
      | undefined,
    messageLogEncryptionDiagnostics: undefined as
      | MessageLogEncryptionStatus
      | undefined,
    globalConfLoading: false,
    timestampingLoading: false,
    ocspLoading: false,
    addonStatusLoading: false,
    backupEncryptionLoading: false,
    messageLogEncryptionLoading: false,
  }),
  computed: {
    ...mapState(useSystem, ['securityServerVersion']),
  },

  created() {
    this.fetchData();
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
    fetchData(): void {
      this.globalConfLoading = true;
      this.timestampingLoading = true;
      this.ocspLoading = true;
      this.addonStatusLoading = true;
      this.backupEncryptionLoading = true;
      this.messageLogEncryptionLoading = true;

      api
        .get<AddOnStatus>('/diagnostics/addon-status')
        .then((res) => {
          this.messageLogEnabled = res.data.messagelog_enabled;
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.addonStatusLoading = false;
        });

      api
        .get<TimestampingServiceDiagnostics[]>(
          `/diagnostics/timestamping-services`,
        )
        .then((res) => {
          this.timestampingServices = res.data;
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.timestampingLoading = false;
        });

      api
        .get<GlobalConfDiagnostics>('/diagnostics/globalconf')
        .then((res) => {
          this.globalConf = res.data;
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.globalConfLoading = false;
        });

      api
        .get<OcspResponderDiagnostics[]>('/diagnostics/ocsp-responders')
        .then((res) => {
          this.ocspResponderDiagnostics = res.data;
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.ocspLoading = false;
        });

      api
        .get<BackupEncryptionStatus>('/diagnostics/backup-encryption-status')
        .then((res) => {
          this.backupEncryptionDiagnostics = res.data;
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.backupEncryptionLoading = false;
        });

      api
        .get<MessageLogEncryptionStatus>(
          '/diagnostics/message-log-encryption-status',
        )
        .then((res) => {
          this.messageLogEncryptionDiagnostics = res.data;
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.messageLogEncryptionLoading = false;
        });
    },

    statusIconTypeTSP(status: string): string {
      if (!status) {
        return '';
      }
      if (this.messageLogEnabled) {
        return this.statusIconType(status);
      } else {
        return this.statusIconType(status) + '-disabled';
      }
    },

    statusIconType(status: string): string {
      if (!status) {
        return '';
      }
      switch (status) {
        case 'OK':
          return 'ok';
        case 'WAITING':
          return 'progress-register';
        case 'FAIL':
          return 'error';
        default:
          return 'error';
      }
    },

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

    messageLogEncryptionTooltipIconType(enabled: boolean): string {
      return enabled === false ? 'disabled' : 'warning-icon';
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

  &:first-of-type {
    margin-top: 40px;
  }

  margin-bottom: 30px;

  /* eslint-disable-next-line vue-scoped-css/no-unused-selector */
  .v-card__title {
    color: $XRoad-Black100;
    height: 30px;
    padding: 16px;
    font-weight: 700;
    font-size: 18px;
  }
}

.status-column {
  width: 80px;
}

.status-wrapper {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.level-column {
  @media only screen and (min-width: 1200px) {
    width: 20%;
  }
}

.url-column {
  width: 240px;
}

.time-column {
  width: 160px;
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

.warning-icon {
  margin-right: 12px;
  color: $XRoad-Warning;
}

.group-name {
  padding-left: 32px;
}
</style>
