<template>
  <div>
    <table class="xrd-table">
      <thead>
        <tr>
          <th>{{$t(title)}}</th>
          <th>{{$t('keys.id')}}</th>
          <th>{{$t('keys.ocsp')}}</th>
          <th>{{$t('keys.expires')}}</th>
          <th>{{$t('keys.status')}}</th>
        </tr>
      </thead>

      <tbody v-for="key in keys" v-bind:key="key.id">
        <div class="name-wrap-top">
          <v-icon class="icon" @click="keyClick(key)">mdi-key-outline</v-icon>
          <div class="clickable-link" @click="keyClick(key)">{{key.name}}</div>
        </div>
        <tr v-for="cert in key.certificates" v-bind:key="cert.id">
          <td>
            <div class="name-wrap">
              <v-icon class="icon" @click="certificateClick(cert)">mdi-file-document-outline</v-icon>
              <div
                class="clickable-link"
                @click="certificateClick(cert)"
              >{{cert.certificate_details.issuer_common_name}} {{cert.certificate_details.serial}}</div>
            </div>
          </td>
          <td>{{cert.certificate_details.hash}}</td>
          <td>{{ cert.ocsp_status | ocspStatus }}</td>
          <td>{{cert.certificate_details.not_after | formatDate}}</td>
          <td class="status-cell">
            <certificate-status :certificate="cert" />
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import Vue from 'vue';
import CertificateStatus from '@/components/CertificateStatus.vue';

export default Vue.extend({
  components: {
    CertificateStatus,
  },
  props: {
    keys: {
      type: Array,
      required: true,
    },
    title: {
      type: String,
      required: true,
    },
  },
  data() {
    return {};
  },
  computed: {},
  methods: {
    keyClick(key: any): void {
      this.$emit('keyClick', key);
    },
    certificateClick(cert: any): void {
      this.$emit('certificateClick', cert);
    },
  },
});
</script>


<style lang="scss" scoped>
@import '../assets/tables';
.icon {
  margin-left: 18px;
  margin-right: 20px;
}

.clickable-link {
  text-decoration: underline;
  cursor: pointer;
}

.name-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.name-wrap-top {
  @extend .name-wrap;
  margin-top: 18px;
  margin-bottom: 5px;
}

.status-cell {
  width: 110px;
}
</style>
