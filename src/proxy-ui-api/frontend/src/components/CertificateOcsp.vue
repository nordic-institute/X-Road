<template>
  <div class="row-wrap">
    <div>{{$t(status)}}</div>
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
          return 'keys.ocspStatus.disabled';
          break;
        case 'EXPIRED':
          return 'keys.ocspStatus.expired';
          break;
        case 'OCSP_RESPONSE_UNKNOWN':
          return 'keys.ocspStatus.unknown';
          break;
        case 'OCSP_RESPONSE_GOOD':
          return 'keys.ocspStatus.good';
          break;
        case 'OCSP_RESPONSE_SUSPENDED':
          return 'keys.ocspStatus.suspended';
          break;
        case 'OCSP_RESPONSE_REVOKED':
          return 'keys.ocspStatus.revoked';
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
