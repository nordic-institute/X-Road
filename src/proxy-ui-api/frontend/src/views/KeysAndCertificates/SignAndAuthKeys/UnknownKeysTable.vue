<template>
  <div>
    <table class="xrd-table">
      <thead>
        <tr>
          <th>{{ $t(title) }}</th>
          <th>{{ $t('keys.id') }}</th>
        </tr>
      </thead>
      <tbody v-for="key in keys" v-bind:key="key.id">
        <!-- Key type SOFTWARE -->
        <template v-if="tokenType === 'SOFTWARE'">
          <tr>
            <td>
              <div class="name-wrap">
                <i class="icon-xrd_key icon" @click="keyClick(key)"></i>
                <div class="clickable-link" @click="keyClick(key)">
                  {{ key.name }}
                </div>
              </div>
            </td>
            <td>
              <div class="id-wrap">
                <div class="clickable-link" @click="keyClick(key)">
                  {{ key.id }}
                </div>
                <SmallButton
                  v-if="hasPermission"
                  class="table-button-fix"
                  :disabled="disableGenerateCsr(key)"
                  @click="generateCsr(key)"
                  >{{ $t('keys.generateCsr') }}</SmallButton
                >
              </div>
            </td>
          </tr>
        </template>

        <!-- Key type HARDWARE -->
        <template v-if="tokenType === 'HARDWARE'">
          <tr v-bind:class="{ borderless: hasCertificates(key) }">
            <td>
              <div class="name-wrap-top">
                <v-icon class="icon" @click="keyClick(key)"
                  >mdi-key-outline</v-icon
                >
                <div class="clickable-link" @click="keyClick(key)">
                  {{ key.name }}
                </div>
              </div>
            </td>
            <td class="td-align-right">
              <div class="id-wrap">
                <div class="clickable-link" @click="keyClick(key)">
                  {{ key.id }}
                </div>
                <SmallButton
                  v-if="hasPermission"
                  class="table-button-fix"
                  :disabled="disableGenerateCsr(key)"
                  @click="generateCsr(key)"
                  >{{ $t('keys.generateCsr') }}</SmallButton
                >
              </div>
            </td>
          </tr>
          <template v-if="hasCertificates(key)">
            <tr
              v-for="certificate in key.certificates"
              v-bind:key="certificate.certificate_details.hash"
            >
              <td class="td-name">
                <div class="name-wrap">
                  <v-icon
                    v-bind:class="{
                      hidden: showHardwareTokenImportCert(certificate),
                    }"
                    class="icon"
                    >mdi-file-document-outline</v-icon
                  >
                  <span
                    >{{ certificate.certificate_details.issuer_common_name }}
                    {{ certificate.certificate_details.serial }}</span
                  >
                </div>
              </td>
              <td>
                <div class="id-wrap">
                  <SmallButton
                    v-if="
                      showHardwareTokenImportCert(certificate) && hasPermission
                    "
                    @click="importCert(certificate.certificate_details.hash)"
                    class="table-button-fix"
                    >{{ $t('keys.importCert') }}</SmallButton
                  >
                </div>
              </td>
            </tr>
          </template>
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
import { Key, TokenCertificate } from '@/openapi-types';
import { Permissions, PossibleActions, UsageTypes } from '@/global';

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
    tokenLoggedIn: {
      type: Boolean,
    },
    tokenType: {
      type: String,
      required: true,
    },
  },
  computed: {
    hasPermission(): boolean {
      // Can the user login to the token and see actions
      return this.$store.getters.hasPermission(
        Permissions.ACTIVATE_DEACTIVATE_TOKEN,
      );
    },
  },
  methods: {
    disableGenerateCsr(key: Key): boolean {
      if (!this.tokenLoggedIn) {
        return true;
      }

      if (
        key.possible_actions?.includes(PossibleActions.GENERATE_AUTH_CSR) ||
        key.possible_actions?.includes(PossibleActions.GENERATE_SIGN_CSR)
      ) {
        return false;
      }

      return true;
    },
    keyClick(key: Key): void {
      this.$emit('keyClick', key);
    },
    generateCsr(key: Key): void {
      this.$emit('generateCsr', key);
    },
    showHardwareTokenImportCert(certificate: TokenCertificate): boolean {
      return !certificate.saved_to_configuration;
    },
    hasCertificates(key: Key): boolean {
      return key.certificates && key.certificates.length > 0;
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

  i.v-icon.mdi-file-document-outline {
    margin-left: 42px;
  }
}

.id-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  align-items: center;
  width: 100%;
}

.td-align-right {
  text-align: right;
}

.borderless td {
  border-bottom: none;
}

.hidden {
  visibility: hidden;
}
</style>
