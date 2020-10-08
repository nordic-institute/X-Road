<template>
  <div class="wrapper">
    <v-btn @click="increment" rounded color="primary" class="large-button"
      >Xrd1: {{ text }}</v-btn
    >
  </div>
</template>
<script lang="ts">
import Vue from "vue";
export default Vue.extend({
  data() {
    return {
      count: 0
    };
  },
  computed: {
    times(): string {
      return this.count > 1 ? "times" : "time";
    },
    text(): string {
      return `I have been clicked ${this.count} ${this.times}`;
    }
  },
  methods: {
    increment(): void {
      this.count += 1;
    }
  }
});
</script>

<style lang="scss" scoped>
$large-button-width: 140px;

.wrapper {
  border: solid blue 4px;
}

.large-button {
  min-width: $large-button-width !important;
  border-radius: 4px;
  text-transform: uppercase;
  background-color: white;
}
</style>
