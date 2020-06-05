<template>
  <div>
    <v-card flat>
      <table class="xrd-table detail-table" v-if="client">
        <tr>
          <td>{{ $t('client.memberName') }}</td>
          <td>{{ client.member_name }}</td>
        </tr>
        <tr>
          <td>{{ $t('client.memberClass') }}</td>
          <td>{{ client.member_class }}</td>
        </tr>
        <tr>
          <td>{{ $t('client.memberCode') }}</td>
          <td>{{ client.member_code }}</td>
        </tr>
        <tr v-if="client.subsystem_code">
          <td>{{ $t('client.subsystemCode') }}</td>
          <td>{{ client.subsystem_code }}</td>
        </tr>
      </table>
    </v-card>

    <v-card flat>
      <table class="xrd-table details-certificates">
        <tr>
          <th>{{ $t('cert.signCertificate') }}</th>
          <th>{{ $t('cert.serialNumber') }}</th>
          <th>{{ $t('cert.state') }}</th>
          <th>{{ $t('cert.expires') }}</th>
        </tr>
        <template v-if="signCertificates && signCertificates.length > 0">
          <tr
            v-for="certificate in signCertificates"
            v-bind:key="certificate.certificate_details.hash"
          >
            <td>
              <span class="cert-name" @click="viewCertificate(certificate)">{{
                certificate.certificate_details.issuer_common_name
              }}</span>
            </td>
            <td>{{ certificate.certificate_details.serial }}</td>
            <td v-if="certificate.active">{{ $t('cert.inUse') }}</td>
            <td v-else>{{ $t('cert.disabled') }}</td>
            <td>
              {{ certificate.certificate_details.not_after | formatDate }}
            </td>
          </tr>
        </template>
      </table>
    </v-card>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { RouteName, UsageTypes } from '@/global';

export default Vue.extend({
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  computed: {
    ...mapGetters(['client', 'signCertificates']),
  },
  created() {
    this.fetchSignCertificates(this.id);
  },
  methods: {
    viewCertificate(cert: any) {
      this.$router.push({
        name: RouteName.Certificate,
        params: {
          hash: cert.certificate_details.hash,
          usage: UsageTypes.SIGNING,
        },
      });
    },
    fetchClient(id: string) {
      this.$store.dispatch('fetchClient', id).catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },
    fetchSignCertificates(id: string) {
      this.$store.dispatch('fetchSignCertificates', id).catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';

.detail-table {
  margin-top: 40px;

  tr td:first-child {
    width: 20%;
  }
}

.cert-name {
  text-decoration: underline;
  cursor: pointer;
}

.details-certificates {
  margin-top: 40px;
}
</style>
