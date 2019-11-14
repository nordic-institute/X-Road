<template>
  <v-dialog v-model="dialog" width="500">
    <v-card>
      <v-card-title>
        <span class="headline">{{$t('cert.certificate')}}</span>
      </v-card-title>
      <v-card-text v-if="certificate">
        {{$t('cert.name')}}: {{certificate.certificate_details.issuer_common_name}}
        <br />
        {{$t('cert.ocsp')}}: {{ certificate.ocsp_status | ocspStatus }}
        <br />
        {{$t('cert.hash')}}: {{certificate.certificate_details.hash}}
        <br />
        {{$t('cert.state')}}:
        <template v-if="certificate.active">{{$t('cert.inUse')}}</template>
        <template v-else>{{$t('cert.disabled')}}</template>
        <br />
        {{$t('cert.expires')}}: {{certificate.certificate_details.not_after}}
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="primary" text @click="close()">{{$t('action.ok')}}</v-btn>
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
