<template>
  <button class="xrd-button">
    {{ props.text }}
  </button>
</template>

<script setup lang="ts">
const props = defineProps<{
  text: string;
}>();
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
