<template>
  <div>
    <table class="xrd-table">

      <!-- SOFTWARE token table header -->
      <template v-if="tokenType === TokenType.SOFTWARE">
        <thead>
          <tr>
            <th>{{$t(title)}}</th>
            <th>{{$t('keys.id')}}</th>
            <th>{{$t('keys.ocsp')}}</th>
            <th>{{$t('keys.expires')}}</th>
            <th>{{$t('keys.status')}}</th>
            <th></th>
          </tr>
        </thead>
      </template>

      <!-- HARDWARE token table header -->
      <template v-if="tokenType === TokenType.HARDWARE">
        <thead>
          <tr>
            <th>{{$t(title)}}</th>
            <th>{{$t('keys.id')}}</th>
            <th>{{$t('keys.ocsp')}}</th>
            <th>{{$t('keys.expires')}}</th>
            <th>{{$t('keys.status')}}</th>
            <th></th>
            <th></th>
          </tr>
        </thead>
      </template>

      <tbody v-for="key in keys" v-bind:key="key.id">

        <!-- SOFTWARE token table body -->
        <template v-if="tokenType === TokenType.SOFTWARE">
          <tr>
            <div class="name-wrap-top">
              <v-icon class="icon" @click="keyClick(key)">mdi-key-outline</v-icon>
              <div class="clickable-link" @click="keyClick(key)">{{key.name}}</div>
            </div>
            <td class="no-border"></td>
            <td class="no-border"></td>
            <td class="no-border"></td>
            <td class="no-border"></td>
            <td class="no-border td-align-right">
              <SmallButton
                      class="table-button-fix"
                      :disabled="disableGenerateCsr"
                      @click="generateCsr(key)"
              >{{$t('keys.generateCsr')}}</SmallButton>
            </td>
          </tr>

          <tr v-for="cert in key.certificates" v-bind:key="cert.id">
            <td class="td-name">
              <div class="name-wrap">
                <v-icon class="icon" @click="certificateClick(cert)">mdi-file-document-outline</v-icon>
                <div
                  class="clickable-link"
                  @click="certificateClick(cert)"
                >{{cert.certificate_details.issuer_common_name}} {{cert.certificate_details.serial}}</div>
              </div>
            </td>
            <td>{{cert.owner_id}}</td>
            <td>{{ cert.ocsp_status | ocspStatus }}</td>
            <td>{{cert.certificate_details.not_after | formatDate}}</td>
            <td class="status-cell"><certificate-status :certificate="cert" /></td>
            <td></td>
          </tr>
        </template>

        <!-- HARDWARE token table body -->
        <template v-if="tokenType === TokenType.HARDWARE">
          <tr>
            <div class="name-wrap-top">
              <v-icon class="icon" @click="keyClick(key)">mdi-key-outline</v-icon>
              <div class="clickable-link" @click="keyClick(key)">{{key.name}}</div>
            </div>
            <td class="no-border"></td>
            <td class="no-border"></td>
            <td class="no-border"></td>
            <td class="no-border"></td>
            <td class="no-border"></td>
            <td class="no-border"><SmallButton
                    class="table-button-fix"
                    :disabled="disableGenerateCsr"
                    @click="generateCsr(key)"
            >{{$t('keys.generateCsr')}}</SmallButton></td>
          </tr>

          <tr v-for="cert in key.certificates" v-bind:key="cert.id">
            <td class="td-name">
              <div class="name-wrap">
                <v-icon class="icon" @click="certificateClick(cert)">mdi-file-document-outline</v-icon>
                <div
                        class="clickable-link"
                        @click="certificateClick(cert)"
                >{{cert.certificate_details.issuer_common_name}} {{cert.certificate_details.serial}}</div>
              </div>
            </td>
            <td>{{cert.owner_id}}</td>
            <td>{{ cert.ocsp_status | ocspStatus }}</td>
            <td>{{cert.certificate_details.not_after | formatDate}}</td>
            <td class="status-cell"><certificate-status :certificate="cert" /></td>
            <td></td>
            <td>
              <SmallButton v-if="!cert.saved_to_configuration" @click="importCert()">{{$t('keys.importCert')}}</SmallButton>
            </td>
          </tr>
        </template>

        <!-- CSRs -->
        <template
          v-if="key.certificate_signing_requests && key.certificate_signing_requests.length > 0"
        >
          <tr v-for="req in key.certificate_signing_requests" v-bind:key="req.id">
            <td class="td-name">
              <div class="name-wrap">
                <i class="icon-xrd_certificate icon" @click="certificateClick(req)"></i>
                <div class="clickable-link" @click="certificateClick(cert)">{{$t('keys.request')}}</div>
              </div>
            </td>
            <td>{{req.id}}</td>
            <td></td>
            <td></td>
            <td class="status-cell"></td>
            <td></td>
          </tr>
        </template>

      </tbody>
    </table>
  </div>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import Vue from 'vue';
import CertificateStatus from './CertificateStatus.vue';
import SmallButton from '@/components/ui/SmallButton.vue';
import { Key, Certificate } from '@/types';
import {TokenType} from "@/global";

export default Vue.extend({
  components: {
    CertificateStatus,
    SmallButton,
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
    disableGenerateCsr: {
      type: Boolean,
    },
    tokenType: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      TokenType: TokenType,
    };
  },
  computed: {},
  methods: {
    keyClick(key: Key): void {
      this.$emit('keyClick', key);
    },
    certificateClick(cert: Certificate): void {
      this.$emit('certificateClick', cert);
    },
    generateCsr(key: Key): void {
      this.$emit('generateCsr', key);
    },
    importCert(hash: string): void {
      this.$emit('importCertByHash', hash);
    },
  },
});
</script>


<style lang="scss" scoped>
@import '../../../assets/tables';
.icon {
  margin-left: 18px;
  margin-right: 20px;
  cursor: pointer;
}

.no-border {
  border-bottom-width: 0 !important;
}

.table-button-fix {
  margin-left: auto;
  margin-right: 0;
  margin-top: 10px;
}

.td-align-right {
  text-align: right;
}

.td-name {
  text-align: center;
  vertical-align: middle;
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

.name-wrap-top {
  @extend .name-wrap;
  margin-top: 18px;
  margin-bottom: 5px;
  min-width: 100%;
}

.status-cell {
  width: 110px;
}
</style>
