<template>
  <v-dialog v-model="dialog" width="500">
    <v-card>
      <v-card-title>
        <span class="headline">Certificate</span>
      </v-card-title>
      <v-card-text v-if="certificate">
        Name: {{certificate.name}}
        <br>
        csp: {{certificate.csp}}
        <br>
        serial: {{certificate.serial}}
        <br>
        state: {{certificate.state}}
        <br>
        expires: {{certificate.expires}}
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="darken-1" flat="flat" @click="close()">Ok</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  props: {
    certificate: {
      type: Object,
    },
    dialog: {
      type: Boolean,
      required: true,
    },
  },

  data() {
    return {};
  },
  methods: {
    close() {
      this.$emit('close');
    },
  },
});
</script>

<style lang="scss" >
</style>

