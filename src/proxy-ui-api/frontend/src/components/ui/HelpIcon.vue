<template>
  <div class="help-wrapper">
    <v-tooltip top>
      <template v-slot:activator="{ on }">
        <v-icon color="#202020" small dark v-on="on">mdi-help-circle</v-icon>
      </template>
      <span>{{ text }}</span>
    </v-tooltip>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  props: {
    text: {
      type: String,
    },
  },
});
</script>

<style lang="scss" scoped>
.help-wrapper {
  cursor: default;
  margin-left: 14px;
}
</style>
