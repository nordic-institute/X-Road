<template>
  <v-stepper :alt-labels="true" v-model="e1" class="stepper">
    <v-stepper-header>
      <v-stepper-step :complete="e1 > 1" step="1">Member details</v-stepper-step>
      <v-divider></v-divider>
      <v-stepper-step :complete="e1 > 2" step="2">Get Sign Key</v-stepper-step>
      <v-divider></v-divider>
      <v-stepper-step step="3">Subsystem Name</v-stepper-step>
    </v-stepper-header>

    <v-stepper-items>
      <v-stepper-content step="1">
        <v-card class="mb-5" color="grey lighten-2" height="250px"></v-card>
        <v-btn flat round @click="cancel">Cancel</v-btn>
        <v-btn round color="primary" @click="e1 = 2">Continue</v-btn>
      </v-stepper-content>

      <v-stepper-content step="2">
        <v-card class="mb-5" color="grey lighten-2" height="250px"></v-card>
        <v-btn flat round @click="cancel">Cancel</v-btn>
        <v-btn round color="primary" @click="e1 = 1">Previous</v-btn>
        <v-btn round color="primary" @click="e1 = 3">Continue</v-btn>
      </v-stepper-content>

      <v-stepper-content step="3">
        <v-card class="mb-5" color="grey lighten-2" height="250px"></v-card>
        <v-btn flat round @click="cancel">Cancel</v-btn>
        <v-btn round color="primary" @click="e1 = 2">Previous</v-btn>
        <v-btn round color="primary" @click="e1 = 1">Continue</v-btn>
      </v-stepper-content>
    </v-stepper-items>
  </v-stepper>
</template>

<script lang="ts">
import Vue from 'vue';
import { RouteName } from '@/global';

export default Vue.extend({
  data: () => ({
    e1: 0,
  }),
  methods: {
    cancel() {
      this.$router.replace({ name: RouteName.Clients });
    },
  },
});
</script>

<style lang="scss" scoped>
.stepper {
  width: 100%;
}
</style>

