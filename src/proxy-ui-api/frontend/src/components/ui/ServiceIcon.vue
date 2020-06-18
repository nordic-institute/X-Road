<template>
  <v-icon small :color="getServiceIconColor(service)">{{
    getServiceIcon(service)
  }}</v-icon>
</template>

<script lang="ts">
// Icon for a service. Shows lock icon with proper color.
import Vue from 'vue';

export default Vue.extend({
  props: {
    service: {
      type: Object,
      required: true,
    },
  },
  methods: {
    getServiceIcon(service: any): string {
      if (!service.url.startsWith('https')) {
        return 'mdi-lock-open-outline';
      }
      switch (service.ssl_auth) {
        case undefined:
          return 'mdi-lock-open-outline';
        case true:
          return 'mdi-lock';
        case false:
          return 'mdi-lock';
        default:
          return '';
      }
    },

    getServiceIconColor(service: any): string {
      if (!service.url.startsWith('https')) {
        return '';
      }
      switch (service.ssl_auth) {
        case undefined:
          return '';
        case true:
          return '#00e500';
        case false:
          return '#ffd200';
        default:
          return '';
      }
    },
  },
});
</script>
