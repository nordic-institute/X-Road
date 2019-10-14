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
          return this.$t('keys.ocspStatus.disabled');
          break;
        case 'EXPIRED':
          return this.$t('keys.ocspStatus.expired');
          break;
        case 'OCSP_RESPONSE_UNKNOWN':
          return this.$t('keys.ocspStatus.unknown');
          break;
        case 'OCSP_RESPONSE_GOOD':
          return this.$t('keys.ocspStatus.good');
          break;
        case 'OCSP_RESPONSE_SUSPENDED':
          return this.$t('keys.ocspStatus.suspended');
          break;
        case 'OCSP_RESPONSE_REVOKED':
          return this.$t('keys.ocspStatus.revoked');
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
