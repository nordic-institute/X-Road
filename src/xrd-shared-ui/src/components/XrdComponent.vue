<template>
  <button class="xrd-button" @click="$emit('press')">
    {{ text }}
  </button>
</template>

<script lang="ts">
import { defineComponent } from "vue";

export default defineComponent({
  props: {
    text: {
      type: String
    }
  },
  emits: ['press']
});
</script>

<style scoped>
.xrd-button {
  background-color: #000;
  color: #fff;
  padding: 10px 20px;
  border: none;
  border-radius: 5px;
}
</style>
