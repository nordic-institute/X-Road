<template>
  <div>
    <v-card flat>
      <table class="detail-table" v-if="client">
        <tr>
          <td>Member Name</td>
          <td>{{client.member_name}}</td>
        </tr>
        <tr>
          <td>Member Class</td>
          <td>{{client.member_class}}</td>
        </tr>
        <tr>
          <td>Member Code</td>
          <td>{{client.member_code}}</td>
        </tr>
        <tr v-if="client.subsystem_code">
          <td>Subsystem Code</td>
          <td>{{client.subsystem_code}}</td>
        </tr>
      </table>
    </v-card>

    <v-card flat>
      <table class="certificate-table details-certificates">
        <tr>
          <th>Certificate</th>
          <th>Serial Number</th>
          <th>State</th>
          <th>Expires</th>
        </tr>
        <template v-if="certificates && certificates.length > 0">
          <tr v-for="certificate in certificates" v-bind:key="certificate.name">
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

    <certificateDialog :dialog="dialog" :certificate="certificate" @close="closeDialog()"/>
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
      // required: true,
    },
  },
  data() {
    return {
      dialog: false,
      certificate: null,
    };
  },
  computed: {
    ...mapGetters(['client', 'certificates']),
  },
  created() {
    this.$store.commit('clearAll');
    this.fetchClient(this.id);
    this.fetchCertificates(this.id);
  },
  methods: {
    viewCertificate(cert: any) {
      Object.entries(cert).forEach(([key, value]) => console.log(key, value));

      this.certificate = cert;
      this.dialog = true;
    },
    closeDialog() {
      console.log('udufuf8838838');
      this.dialog = false;
    },
    fetchClient(id: string) {
      this.$store.dispatch('fetchClient', id).then(
        (response) => {
          this.$bus.$emit('show-success', 'Great success!');
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      );
    },
    fetchCertificates(id: string) {
      this.$store.dispatch('fetchCertificates', id).then(
        (response) => {
          this.$bus.$emit('show-success', 'Great success!');
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      );
    },
  },
});
</script>

<style lang="scss" >
@import '../assets/tables';

.xr-tabs {
  border-bottom: #9b9b9b solid 1px;
}

.cert-name {
  text-decoration: underline;
  cursor: pointer;
}

.content {
  width: 100%;
}

.details-certificates {
  margin-top: 40px;
}
</style>

