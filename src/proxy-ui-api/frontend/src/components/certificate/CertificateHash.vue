<template>
  <div class="cert-hash">
    <div class="hash-info">{{ $t('cert.hashInfo') }}</div>
    <div>{{ hash | colonize }}</div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  props: {
    hash: {
      type: String,
      required: true,
    },
  },
});
</script>

<style lang="scss" scoped>
.cert-hash {
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.5px;
  line-height: 30px;
}

.hash-info {
  font-size: 16px;
}
</style>
