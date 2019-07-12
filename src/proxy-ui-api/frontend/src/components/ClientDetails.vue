<template>
  <div>
    <v-card flat>
      <table class="detail-table" v-if="client">
        <tr>
          <td>{{$t('client.memberName')}}</td>
          <td>{{client.member_name}}</td>
        </tr>
        <tr>
          <td>{{$t('client.memberClass')}}</td>
          <td>{{client.member_class}}</td>
        </tr>
        <tr>
          <td>{{$t('client.memberCode')}}</td>
          <td>{{client.member_code}}</td>
        </tr>
        <tr v-if="client.subsystem_code">
          <td>{{$t('client.subsystemCode')}}</td>
          <td>{{client.subsystem_code}}</td>
        </tr>
      </table>
    </v-card>

    <v-card flat>
      <table class="xrd-table details-certificates">
        <tr>
          <th>{{$t('cert.signCertificate')}}</th>
          <th>{{$t('cert.serialNumber')}}</th>
          <th>{{$t('cert.state')}}</th>
          <th>{{$t('cert.expires')}}</th>
        </tr>
        <template v-if="signCertificates && signCertificates.length > 0">
          <tr v-for="certificate in signCertificates" v-bind:key="certificate.name">
            <td>
              <span class="cert-name" @click="viewCertificate(certificate)">{{certificate.name}}</span>
            </td>
            <td>{{certificate.serial}}</td>
            <td>{{certificate.state}}</td>
            <td>{{certificate.expires}}</td>
          </tr>
        </template>
      </table>
    </v-card>

    <certificateDialog :dialog="dialog" :certificate="certificate" @close="closeDialog()" />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

import { mapGetters } from 'vuex';
import { Permissions } from '@/global';
import CertificateDialog from '@/components/CertificateDialog.vue';

export default Vue.extend({
  components: {
    CertificateDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      dialog: false,
      certificate: null,
    };
  },
  computed: {
    ...mapGetters(['client', 'signCertificates']),
  },
  created() {
    this.fetchSignCertificates(this.id);
  },
  methods: {
    viewCertificate(cert: any) {
      this.certificate = cert;
      this.dialog = true;
    },
    closeDialog() {
      this.dialog = false;
    },
    fetchClient(id: string) {
      this.$store.dispatch('fetchClient', id).catch((error) => {
        this.$bus.$emit('show-error', error.message);
      });
    },
    fetchSignCertificates(id: string) {
      this.$store.dispatch('fetchSignCertificates', id).catch((error) => {
        this.$bus.$emit('show-error', error.message);
      });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../assets/tables';

.cert-name {
  text-decoration: underline;
  cursor: pointer;
}

.details-certificates {
  margin-top: 40px;
}
</style>

