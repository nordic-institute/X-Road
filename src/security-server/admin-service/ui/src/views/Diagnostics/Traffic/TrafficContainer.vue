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
  <XrdTitledView
    title-key="tab.diagnostics.traffic"
    data-test="diagnostics-view"
    :loading="opMonitoringStatusLoading"
  >
    <template v-if="opMonitoringEnabled">
      <TrafficView />
    </template>
    <template v-else>
      <v-card variant="flat" class="xrd-card diagnostic-card">
        <v-card-title class="text-h5" data-test="diagnostics-ocsp-responders">
          {{ $t('diagnostics.traffic.disabledTitle') }}
        </v-card-title>
        <v-card-text class="xrd-card-text">
          <p>{{ $t('diagnostics.traffic.disabledMessage') }}</p>
        </v-card-text>
      </v-card>
    </template>
  </XrdTitledView>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { useDiagnostics } from '@/store/modules/diagnostics';
import TrafficView from '@/views/Diagnostics/Traffic/TrafficView.vue';
import { XrdTitledView } from '@niis/shared-ui';

const diagnosticsStore = useDiagnostics();

const opMonitoringStatusLoading = ref(
  diagnosticsStore.addOnStatus === undefined,
);

const opMonitoringEnabled = ref(false);

getOpMonitoringStatus().finally(() => {
  opMonitoringStatusLoading.value = false;
});

async function getOpMonitoringStatus() {
  if (diagnosticsStore.addOnStatus === undefined) {
    await diagnosticsStore.fetchAddonStatus();
  }
  opMonitoringEnabled.value =
    diagnosticsStore.addOnStatus?.opmonitoring_enabled ?? false;
}
</script>
