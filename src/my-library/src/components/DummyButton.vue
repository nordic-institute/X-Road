<template>
  <div class="wrapper">
        <div>
      Normal Vue component
    </div>
    <button @click="increment" class="button">{{ text }}</button>
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
  border: solid blueviolet 3px;
  width: 320px;
  padding: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  margin: 10px;
}

.button {
    border: solid black 1px;
    background-color: beige;
    padding: 3px;
}

</style>