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
