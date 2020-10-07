<template>
  <div class="wrapper">
    Slot text: <slot></slot> <br />
    <v-btn
      @click="increment"
      :disabled="isDisabled"
      rounded
      color="primary"
      class="large-button"
      >{{ text }}</v-btn
    >
  </div>
</template>

<script>
export default {
  name: "MyButton",
  props: {
    isDisabled: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      count: 0
    };
  },
  computed: {
    times() {
      return this.count > 1 ? "times" : "time";
    },
    text() {
      return `I have been clicked ${this.count} ${this.times}`;
    }
  },
  methods: {
    increment() {
      this.count += 1;
    }
  }
};
</script>

<style lang="scss" scoped>
$large-button-width: 140px;

.wrapper {
  border: solid grey 3px;
  width: 320px;
  padding: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  margin: 10px;
  background-color: beige;
}

.large-button {
  min-width: $large-button-width !important;
  border-radius: 4px;
  text-transform: uppercase;
  background-color: white;
}
</style>
