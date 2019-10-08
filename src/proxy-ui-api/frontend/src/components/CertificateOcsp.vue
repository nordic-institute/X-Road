<template>
  <div class="row-wrap">
    <div>{{status}}</div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  props: {
    ocspStatus: {
      type: String,
      required: true,
    },
  },
  data() {
    return {};
  },
  computed: {
    status() {
      if (!this.ocspStatus) {
        return '-';
      }
      switch (this.ocspStatus) {
        case 'DISABLED':
          return 'Disabled';
          break;
        case 'EXPIRED':
          return 'Expired';
          break;
        case 'OCSP_RESPONSE_UNKNOWN':
          return 'Unknown';
          break;
        case 'OCSP_RESPONSE_GOOD':
          return 'Good';
          break;
        case 'OCSP_RESPONSE_SUSPENDED':
          return 'Suspended';
          break;
        case 'OCSP_RESPONSE_REVOKED':
          return 'Revoked';
          break;
        default:
          return '-';
          break;
      }
    },
  },
  methods: {},
});
</script>


<style lang="scss" scoped>
</style>
