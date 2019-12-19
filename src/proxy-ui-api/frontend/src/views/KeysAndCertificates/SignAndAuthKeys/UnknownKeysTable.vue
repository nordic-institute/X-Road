<template>
  <div>
    <table class="xrd-table">
      <thead>
        <tr>
          <th>{{$t(title)}}</th>
          <th>{{$t('keys.id')}}</th>
        </tr>
      </thead>
      <tbody v-for="key in keys" v-bind:key="key.id">

        <!-- Key type SOFTWARE -->
        <template v-if="tokenType === 'SOFTWARE'">
          <tr>
            <td>
              <div class="name-wrap">
                <i class="icon-xrd_key icon" @click="keyClick(key)"></i>
                <div class="clickable-link" @click="keyClick(key)">{{key.label}}</div>
              </div>
            </td>
            <td>
              <div class="id-wrap">
                <div class="clickable-link" @click="keyClick(key)">{{key.id}}</div>
                <SmallButton
                  class="table-button-fix"
                  :disabled="disableGenerateCsr"
                  @click="generateCsr(key)"
                >{{$t('keys.generateCsr')}}</SmallButton>
              </div>
            </td>
          </tr>
        </template>

        <!-- Key type HARDWARE -->
        <template v-if="tokenType === 'HARDWARE'">
          <tr v-bind:class="{hardwarekey: hasCertificates(key)}">
            <td>
              <div class="name-wrap">
                <i class="icon-xrd_key icon" @click="keyClick(key)"></i>
                <div class="clickable-link" @click="keyClick(key)">{{key.label}}</div>
              </div>
            </td>
            <td>
              <div class="id-wrap">
                <div class="clickable-link" @click="keyClick(key)">{{key.id}}</div>


                <SmallButton
                        class="table-button-fix"
                        :disabled="disableGenerateCsr"
                        @click="generateCsr(key)"
                >{{$t('keys.generateCsr')}}</SmallButton>
              </div>
            </td>
          </tr>
          <tr v-if="hasCertificates(key)" v-for="certificate in key.certificates" v-bind:key="certificate.certificate_details.hash">
            <td></td>
            <td>
              <div class="id-wrap">
                <span>{{certificate.certificate_details.issuer_common_name}} {{certificate.certificate_details.serial}}</span>
                <SmallButton
                        v-if="showHardwareTokenImportCert(certificate)"
                        @click="importCert(certificate.certificate_details.hash)"
                        class="table-button-fix"
                >{{$t('keys.importCert')}}</SmallButton>
              </div>
            </td>
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
import SmallButton from '@/components/ui/SmallButton.vue';
import {Certificate, Key} from '@/types';

export default Vue.extend({
  components: {
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
      required: true
    }
  },
  data() {
    return {};
  },
  computed: {},
  methods: {
    keyClick(key: Key): void {
      this.$emit('keyClick', key);
    },
    generateCsr(key: Key): void {
      this.$emit('generateCsr', key);
    },
    showHardwareTokenImportCert(certificate: Certificate): boolean {
      return !certificate.saved_to_configuration;
    },
    hasCertificates(key: Key): boolean {
      return key.certificates && key.certificates.length > 0;
    },
    importCert(hash: string): void {
      this.$emit('importCertByHash', hash);
    }
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

.clickable-link {
  text-decoration: underline;
  cursor: pointer;
}

.table-button-fix {
  margin-left: auto;
  margin-right: 0;
}

.name-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  align-items: center;
}

.id-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  align-items: center;
  width: 100%;
}

.hardwarekey td {
  border-bottom: none;
}

</style>
