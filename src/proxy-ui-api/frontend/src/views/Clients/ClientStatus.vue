<template>
  <div class="status-wrapper">
    <div :class="getStatusIconClass(status)"></div>
    <div class="status-text">{{getStatusText(status)}}</div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { Client, ClientStatus } from '@/types';

export default Vue.extend({
  props: {
    status: {
      type: String,
    },
  },
  data() {
    return {
      tab: null,
    };
  },
  methods: {
    getStatusIconClass(status: string): string {
      if (!status) {
        return '';
      }
      switch (status.toLowerCase()) {
        case 'registered':
          return 'status-green';
        case 'registration_in_progress':
          return 'status-green-ring';
        case 'saved':
          return 'status-orange-ring';
        case 'deletion_in_progress':
          return 'status-red-ring';
        case 'global_error':
          return 'status-red';
        default:
          return '';
      }
    },
    getStatusText(status: string): string {
      if (!status) {
        return '';
      }
      switch (status.toLowerCase()) {
        case 'registered':
          return this.$t('client.statusText.registered') as string;
        case 'registration_in_progress':
          return this.$t('client.statusText.registrationInProgress') as string;
        case 'saved':
          return this.$t('client.statusText.saved') as string;
        case 'deletion_in_progress':
          return this.$t('client.statusText.deletionInProgress') as string;
        case 'global_error':
          return this.$t('client.statusText.globalError') as string;
        default:
          return '';
      }
    },
  },
});
</script>


<style lang="scss" scoped>
.status-wrapper {
  display: flex;
  flex-direction: row;
  align-items: center;
}

%status-icon-shared {
  height: 8px;
  width: 8px;
  border-radius: 50%;
  margin-right: 16px;
}

%status-ring-icon-shared {
  height: 10px;
  width: 10px;
  border-radius: 50%;
  margin-right: 16px;
  border: 2px solid;
}

.status-red {
  @extend %status-icon-shared;
  background: #d0021b;
}

.status-red-ring {
  @extend %status-ring-icon-shared;
  border-color: #d0021b;
}

.status-green {
  @extend %status-icon-shared;
  background: #7ed321;
}

.status-green-ring {
  @extend %status-ring-icon-shared;
  border-color: #7ed321;
}

.status-orange-ring {
  @extend %status-ring-icon-shared;
  border-color: #f5a623;
}
</style>