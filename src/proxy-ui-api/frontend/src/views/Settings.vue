<template>
  <v-container>
    <tabContent/>
  </v-container>
</template>


<script lang="ts">
import Vue from 'vue';
import TabContent from '../components/TabContent.vue';

export default Vue.extend({
  components: {
    TabContent,
  },
});
</script>

<style>
</style>
