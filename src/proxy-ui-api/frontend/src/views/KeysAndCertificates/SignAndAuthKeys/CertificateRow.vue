<template>
  <tr>
    <td class="td-name">
      <div class="name-wrap">
        <i
          class="icon-xrd_certificate icon clickable"
          @click="certificateClick(cert, key)"
        ></i>
        <div class="clickable-link" @click="certificateClick()">
          {{ cert.certificate_details.issuer_common_name }}
          {{ cert.certificate_details.serial }}
        </div>
      </div>
    </td>
    <td>{{ cert.owner_id }}</td>
    <td>{{ cert.ocsp_status | ocspStatus }}</td>
    <td>{{ cert.certificate_details.not_after | formatDate }}</td>
    <td class="status-cell">
      <certificate-status :certificate="cert" />
    </td>
    <td class="td-align-right">
      <slot name="certificateAction"></slot>
    </td>
  </tr>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import Vue from 'vue';
import { Prop } from 'vue/types/options';
import CertificateStatus from './CertificateStatus.vue';
import { TokenCertificate } from '@/openapi-types';

export default Vue.extend({
  components: {
    CertificateStatus,
  },
  props: {
    cert: {
      type: Object as Prop<TokenCertificate>,
      required: true,
    },
  },

  methods: {
    certificateClick(): void {
      this.$emit('certificateClick');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
.icon {
  margin-left: 18px;
  margin-right: 20px;
}

.clickable {
  cursor: pointer;
}

.td-align-right {
  text-align: right;
}

.clickable-link {
  text-decoration: underline;
  cursor: pointer;
  height: 100%;
}

.name-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;

  i.v-icon.mdi-file-document-outline {
    margin-left: 42px;
  }
}
</style>
