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
  <XrdView data-test="diagnostics-view" title="tab.main.diagnostics">
    <template #tabs>
      <DiagnosticsTabs />
    </template>

    <XrdSubView class="settings-subview">
      <template #header>
        <v-spacer />
        <DiagnosticsDownloadSystemInfoBtn class="mr-1" />
      </template>

      <DiagnosticsJavaVersionCard />

      <DiagnosticsMailNotificationCard />

      <DiagnosticsGlobalConfigurationCard />

      <DiagnosticsTimestampingServiceCard :addon-status-loading="addonStatusLoading" />

      <DiagnosticsOcspRespondersCard />

      <DiagnosticsBackupEncryptionCard />

      <DiagnosticsMessageLogArchiveCard
        :addon-status-loading="addonStatusLoading"
        :message-log-encryption-loading="messageLogEncryptionLoading"
      />

      <DiagnosticsMessageLogDatabaseCard :message-log-encryption-loading="messageLogEncryptionLoading" />

      <DiagnosticsProxyMemoryUsageCard />
    </XrdSubView>
  </XrdView>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { mapActions, mapState } from 'pinia';
import { useDiagnostics } from '@/store/modules/diagnostics';
import DiagnosticsJavaVersionCard from '@/views/Diagnostics/Overview/DiagnosticsJavaVersionCard.vue';
import { XrdView, XrdSubView, useNotifications } from '@niis/shared-ui';
import DiagnosticsDownloadSystemInfoBtn from '@/views/Diagnostics/Overview/DiagnosticsDownloadSystemInfoBtn.vue';
import DiagnosticsMailNotificationCard from '@/views/Diagnostics/Overview/DiagnosticsMailNotificationCard.vue';
import DiagnosticsGlobalConfigurationCard from '@/views/Diagnostics/Overview/DiagnosticsGlobalConfigurationCard.vue';
import DiagnosticsTimestampingServiceCard from '@/views/Diagnostics/Overview/DiagnosticsTimestampingServiceCard.vue';
import DiagnosticsOcspRespondersCard from '@/views/Diagnostics/Overview/DiagnosticsOcspRespondersCard.vue';
import DiagnosticsBackupEncryptionCard from '@/views/Diagnostics/Overview/DiagonsticsBackupEncryptionCard.vue';
import DiagnosticsMessageLogArchiveCard from '@/views/Diagnostics/Overview/DiagnosticsMessageLogArchiveCard.vue';
import DiagnosticsMessageLogDatabaseCard from '@/views/Diagnostics/Overview/DiagnosticsMessageLogDatabaseCard.vue';
import DiagnosticsProxyMemoryUsageCard from '@/views/Diagnostics/Overview/DiagnosticsProxyMemoryUsageCard.vue';
import DiagnosticsTabs from '@/views/Diagnostics/DiagnosticsTabs.vue';

export default defineComponent({
  components: {
    XrdSubView,
    DiagnosticsTabs,
    XrdView,
    DiagnosticsDownloadSystemInfoBtn,
    DiagnosticsJavaVersionCard,
    DiagnosticsMailNotificationCard,
    DiagnosticsGlobalConfigurationCard,
    DiagnosticsTimestampingServiceCard,
    DiagnosticsOcspRespondersCard,
    DiagnosticsBackupEncryptionCard,
    DiagnosticsMessageLogArchiveCard,
    DiagnosticsMessageLogDatabaseCard,
    DiagnosticsProxyMemoryUsageCard,
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data: () => ({
    addonStatusLoading: false,
    messageLogEncryptionLoading: false,
  }),
  computed: {
    ...mapState(useDiagnostics, ['messageLogEnabled', 'messageLogEncryptionDiagnostics']),
  },
  created() {
    this.fetchData();
  },
  methods: {
    ...mapActions(useDiagnostics, ['fetchAddonStatus', 'fetchMessageLogEncryptionDiagnostics']),
    fetchData(): void {
      this.addonStatusLoading = true;
      this.messageLogEncryptionLoading = true;
      this.fetchAddonStatus()
        .catch((error) => {
          this.addError(error);
        })
        .finally(() => {
          this.addonStatusLoading = false;
        });
      this.fetchMessageLogEncryptionDiagnostics()
        .catch((error) => {
          this.addError(error);
        })
        .finally(() => {
          this.messageLogEncryptionLoading = false;
        });
    },
  },
});
</script>
<style lang="scss" scoped>
// eslint-disable-next-line vue-scoped-css/no-unused-selector
.overview-card:not(:last-child) {
  margin-bottom: 16px;
}
</style>
