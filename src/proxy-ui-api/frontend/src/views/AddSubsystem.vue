<template>
  <v-container class="xrd-tab-max-width">
    <v-layout text-xs-center wrap column>
      <v-flex mb-4>
        <h1 class="display-1 font-weight-bold mb-3">Add a Subsystem</h1>
        <p
          class="subheading font-weight-regular"
        >Specify the name of the Subsystem and add it to the selected Member.</p>
      </v-flex>
      <v-layout pa-4 column>
        <v-layout mb-4 row justify-start>
          <v-flex mb-4 class="input-title">Member Name</v-flex>
          <v-flex mb-4 class="subs-input">{{member.name}}</v-flex>
        </v-layout>
        <v-layout mb-4 row justify-start>
          <v-flex mb-4 class="input-title">Member Class</v-flex>
          <v-flex mb-4 class="subs-input">{{member.class}}</v-flex>
        </v-layout>
        <v-layout mb-4 row justify-start>
          <v-flex mb-4 class="input-title">Member Code</v-flex>
          <v-flex mb-4 class="subs-input">{{member.code}}</v-flex>
        </v-layout>

        <v-layout mb-4 row justify-start>
          <v-flex mb-4 class="input-title">Subsystem Name</v-flex>
          <v-text-field label="Regular" class="input-name"></v-text-field>
        </v-layout>
      </v-layout>

      <v-layout mb-4 justify-space-between>
        <v-btn color="primary" @click="cancel" round outlined class="mb-2 rounded-button">Cancel</v-btn>

        <v-btn
          color="primary"
          @click="addSubsystem"
          round
          dark
          class="mb-2 rounded-button"
        >Add subsystem</v-btn>
      </v-layout>
    </v-layout>
  </v-container>
</template>

<script lang="ts">
import Vue from 'vue';
import { RouteName } from '@/global';

export default Vue.extend({
  data: () => ({
    member: {
      name: 'Kuikka',
      class: 'Org',
      code: '111',
    },
  }),
  methods: {
    addSubsystem() {
      console.log('add subsystem');
    },
    cancel() {
      this.$router.replace({ name: RouteName.Clients });
    },
  },
});
</script>

<style lang="scss" scoped>
.input-title {
  max-width: 160px;
  text-align: left;
}

.subs-input {
  text-align: left;
}

.input-name {
  padding-top: 0;
  margin-top: 0;
}
</style>

