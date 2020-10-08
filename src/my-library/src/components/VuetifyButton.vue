<template>
  <div class="wrapper">
    <div>
      Vuetify component
    </div>
    <v-btn @click="increment" rounded color="primary" class="large-button"
      >{{ text }}</v-btn
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
      return `Clicked ${this.count} ${this.times}`;
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
$large-button-width: 300px;

.wrapper {
  border: solid grey 3px;
  width: 320px;
  padding: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  margin: 10px;
}

.large-button {
  min-width: $large-button-width !important;
  border-radius: 4px;
  text-transform: uppercase;
  background-color: white;
}
</style>
