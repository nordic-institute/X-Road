<template>
  <div>
    <v-card flat>Placeholder for services</v-card>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import axios from 'axios';

import { mapGetters } from 'vuex';
import { Permissions, RouteName } from '@/global';

export default Vue.extend({
  components: {},
  props: {
    id: {
      type: String,
      required: true,
    },
  },
});
</script>

<style lang="scss" >
</style>