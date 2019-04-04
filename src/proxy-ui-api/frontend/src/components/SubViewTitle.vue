<template>
  <div class="new-content">
    <div class="cert-dialog-header">
      <span class="cert-headline">{{title}}</span>
      <v-spacer></v-spacer>
      <i @click="close()" id="close-x"></i>
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
.new-content {
  max-width: 850px;
  width: 100%;
}

.cert-dialog-header {
  display: flex;
  justify-content: center;
  border-bottom: 1px solid #9b9b9b;
  color: #4a4a4a;
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
  color: #979797;
}

#close-x:before {
  content: '\00d7';
}
</style>

