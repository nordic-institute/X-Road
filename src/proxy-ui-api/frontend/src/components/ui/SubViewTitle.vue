<template>
  <div class="new-content">
    <div class="cert-dialog-header">
      <span class="cert-headline">{{ title }}</span>
      <v-spacer></v-spacer>
      <i v-if="showClose" @click="close()" id="close-x"></i>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  props: {
    title: {
      type: String,
    },
    showClose: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {};
  },
  methods: {
    close() {
      this.$emit('close');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/colors';

.new-content {
  max-width: 850px;
  width: 100%;
}

.cert-dialog-header {
  display: flex;
  justify-content: center;
  border-bottom: 1px solid $XRoad-Grey40;
  color: $XRoad-Grey60;
  font-family: Roboto;
  font-size: 34px;
  font-weight: 300;
  letter-spacing: 0.5px;
  line-height: 51px;
}

#close-x {
  cursor: pointer;
  font-style: normal;
  font-size: 50px;
  color: $XRoad-Grey40;
}

#close-x:before {
  content: '\00d7';
}
</style>
