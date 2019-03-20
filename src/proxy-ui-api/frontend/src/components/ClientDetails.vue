<template>
  <div>
    <v-card flat>
      <table class="detail-table">
        <tr>
          <td>Member Name</td>
          <td>{{subsystem.name}}</td>
        </tr>
        <tr>
          <td>Member Class</td>
          <td>{{subsystem.class}}</td>
        </tr>
        <tr>
          <td>Member Code</td>
          <td>{{subsystem.memberCode}}</td>
        </tr>
        <tr>
          <td>Subsystem Code</td>
          <td>{{subsystem.subsystemCode}}</td>
        </tr>
      </table>
    </v-card>

    <v-card flat>
      <table class="certificate-table">
        <tr>
          <th>Certificate</th>
          <th>Serial Number</th>
          <th>State</th>
          <th>Expires</th>
        </tr>
        <tr v-for="certificate in certificates" v-bind:key="certificate.name">
          <td>{{certificate.name}}</td>
          <td>{{certificate.serial}}</td>
          <td>{{certificate.state}}</td>
          <td>{{certificate.expires}}</td>
        </tr>
      </table>
    </v-card>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

import { mapGetters } from 'vuex';
import { Permissions } from '@/global';

export default Vue.extend({
  computed: {
    ...mapGetters(['client', 'certificates']),
  },

  data() {
    return {
      subsystem: {
        name: 'NIIS',
        class: 'Org',
        memberCode: '1111',
        subsystemCode: 'Library',
      },
    };
  },
  methods: {
    fetchCertificates() {
      this.$store.dispatch('fetchCertificates').then(
        (response) => {
          this.$bus.$emit('show-success', 'Great success!');
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      );
    },
  },
  created() {
    this.fetchCertificates();
  },
});
</script>

<style lang="scss" >
@import '../assets/tables';

.xr-tabs {
  border-bottom: #9b9b9b solid 1px;
}

.content {
  width: 100%;
}
</style>

