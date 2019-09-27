<template>
  <div class="cert-hash">
    <div class="hash-info">{{$t('cert.hashInfo')}}</div>
    <div>{{hash | colonize}}</div>
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
  color: #202020;
  font-family: Roboto;
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.5px;
  line-height: 30px;
}

.hash-info {
  color: #202020;
  font-family: Roboto;
  font-size: 16px;
}
</style>

