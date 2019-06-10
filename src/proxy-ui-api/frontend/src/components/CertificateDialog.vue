<template>
  <v-dialog v-model="dialog" width="500">
    <v-card>
      <v-card-title>
        <span class="headline">{{$t('cert.certificate')}}</span>
      </v-card-title>
      <v-card-text v-if="certificate">
        {{$t('cert.name')}}: {{certificate.name}}
        <br>
        {{$t('cert.csp')}}: {{certificate.csp}}
        <br>
        {{$t('cert.serial')}}: {{certificate.serial}}
        <br>
        {{$t('cert.state')}}: {{certificate.state}}
        <br>
        {{$t('cert.expires')}}: {{certificate.expires}}
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="primary" flat="flat" @click="close()">{{$t('action.ok')}}</v-btn>
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

