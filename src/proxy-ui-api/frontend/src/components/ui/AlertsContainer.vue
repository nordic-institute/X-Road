<template>
  <v-container
    v-if="isAuthenticated && !needsInitialization && hasAlerts"
    fluid
    class="alerts-container"
  >
    <v-alert
      data-test="global-alert-global-configuration"
      :value="showGlobalConfAlert"
      color="red"
    >
      <span class="alert-text">{{
        $t('globalAlert.globalConfigurationInvalid')
      }}</span>
    </v-alert>
    <v-alert
      data-test="global-alert-soft-token-pin"
      :value="showSoftTokenPinEnteredAlert"
      color="red"
    >
      <span
        class="alert-text clickable-link"
        @click="tokenLogin()"
      >
        {{ $t('globalAlert.softTokenPinNotEntered') }}
      </span>
    </v-alert>
    <v-alert
      data-test="global-alert-soft-token-pin"
      :value="showRestoreInProgress"
      color="red"
    >
      <span class="alert-text">{{
        $t('globalAlert.backupRestoreInProgress', {
          startTime: formatDateTime(restoreStartTime),
        })
      }}</span>
    </v-alert>
  </v-container>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { formatDateTime } from '@/filters';
import { RouteName } from '@/global';
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
      'isAuthenticated',
      'needsInitialization',
    ]),
  },
  methods: {
    formatDateTime,
    tokenLogin(): void {
      this.$router.replace({ name: RouteName.SignAndAuthKeys });
    },
  },
});
</script>

<style scoped lang="scss">
.alerts-container {
  width: 100%;
  padding: 0;
  & > * {
    margin-top: 4px;
    margin-bottom: 0;
    border-radius: 0;
  }
  & > :first-child {
    margin-top: 4px;
  }
}
.alert-text {
  color: white;
  text-align: center;
  display: block;
}

.clickable-link {
  text-decoration: underline;
  cursor: pointer;
}
</style>
