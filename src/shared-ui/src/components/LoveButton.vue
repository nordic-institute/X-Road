<template>
  <v-btn color="red">
    <v-icon>favorite</v-icon>
    {{ love }}
  </v-btn>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  props: ['love'],
});
</script>
