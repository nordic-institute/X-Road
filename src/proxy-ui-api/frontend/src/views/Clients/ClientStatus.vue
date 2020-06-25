<template>
  <div class="status-wrapper">
    <StatusIcon :status="statusIconType" />
    <div class="status-text">{{ getStatusText(status) }}</div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import StatusIcon from '@/components/ui/StatusIcon.vue';

export default Vue.extend({
  components: {
    StatusIcon,
  },
  props: {
    status: {
      type: String,
    },
  },

  computed: {
    statusIconType(): string {
      if (!this.status) {
        return '';
      }
      switch (this.status.toLowerCase()) {
        case 'registered':
          return 'green';
        case 'registration_in_progress':
          return 'green-ring';
        case 'saved':
          return 'orange-ring';
        case 'deletion_in_progress':
          return 'red-ring';
        case 'global_error':
          return 'red';
        default:
          return 'red';
      }
    },
  },

  methods: {
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
</style>
