<template>
  <v-container v-if="hasAlerts">
    <v-row justify="center" v-if="showGlobalConfAlert">
      <v-col cols="12" lg="10" xl="8">
        <v-alert data-test="global-alert-global-configuration" type="warning">
          {{ $t('globalAlert.globalConfigurationInvalid') }}
        </v-alert>
      </v-col>
    </v-row>
    <v-row justify="center" v-if="showSoftTokenPinEnteredAlert">
      <v-col cols="12" lg="10" xl="8">
        <v-alert data-test="global-alert-soft-token-pin" type="warning">
          {{ $t('globalAlert.softTokenPinNotEntered') }}
        </v-alert>
      </v-col>
    </v-row>
    <v-row justify="center" v-if="showRestoreInProgress">
      <v-col cols="12" lg="10" xl="8">
        <v-alert data-test="global-alert-soft-token-pin" type="warning">
          {{
            $t('globalAlert.backupRestoreInProgress', {
              startTime: formatDateTime(restoreStartTime),
            })
          }}
        </v-alert>
      </v-col>
    </v-row>
  </v-container>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { formatDateTime } from '@/filters';
export default Vue.extend({
  name: 'AlertsContainer',
  computed: {
    hasAlerts(): boolean {
      return (
        this.showGlobalConfAlert ||
        this.showSoftTokenPinEnteredAlert ||
        this.showRestoreInProgress
      );
    },
    ...mapGetters([
      'showGlobalConfAlert',
      'showSoftTokenPinEnteredAlert',
      'showRestoreInProgress',
      'restoreStartTime',
    ]),
  },
  methods: {
    formatDateTime,
  },
});
</script>

<style scoped lang="scss"></style>
