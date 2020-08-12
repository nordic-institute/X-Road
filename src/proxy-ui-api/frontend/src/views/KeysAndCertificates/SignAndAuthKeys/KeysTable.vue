<template>
  <div>
    <table class="xrd-table">
      <thead>
        <tr>
          <th class="title-col">{{ $t(title) }}</th>
          <th class="id-col">{{ $t('keys.id') }}</th>
          <th class="ocsp-col">{{ $t('keys.ocsp') }}</th>
          <th class="expiration-col">{{ $t('keys.expires') }}</th>
          <th class="status-col">{{ $t('keys.status') }}</th>
          <th class="action-col"></th>
        </tr>
      </thead>

      <tbody v-for="key in keys" v-bind:key="key.id">
        <!-- SOFTWARE token table body -->
        <template v-if="tokenType === 'SOFTWARE'">
          <KeyRow
            :disableGenerateCsr="disableGenerateCsr(key)"
            :hasPermission="hasPermission"
            :tokenLoggedIn="tokenLoggedIn"
            :tokenKey="key"
            @generateCsr="generateCsr(key)"
            @keyClick="keyClick(key)"
          />

          <CertificateRow
            v-for="cert in key.certificates"
            v-bind:key="cert.id"
            :cert="cert"
            @certificateClick="certificateClick(cert, key)"
          >
            <div slot="certificateAction">
              <SmallButton
                class="table-button-fix test-register"
                v-if="
                  showRegisterCertButton &&
                    cert.possible_actions.includes('REGISTER')
                "
                @click="showRegisterCertDialog(cert)"
                >{{ $t('action.register') }}</SmallButton
              >
            </div>
          </CertificateRow>
        </template>

        <!-- HARDWARE token table body -->
        <template v-if="tokenType === 'HARDWARE'">
          <KeyRow
            :disableGenerateCsr="disableGenerateCsr(key)"
            :hasPermission="hasPermission"
            :tokenLoggedIn="tokenLoggedIn"
            :tokenKey="key"
            @generateCsr="generateCsr(key)"
            @keyClick="keyClick(key)"
          />

          <CertificateRow
            v-for="cert in key.certificates"
            v-bind:key="cert.id"
            :cert="cert"
            @certificateClick="certificateClick(cert, key)"
          >
            <div slot="certificateAction">
              <template v-if="hasPermission">
                <SmallButton
                  v-if="cert.possible_actions.includes('IMPORT_FROM_TOKEN')"
                  class="table-button-fix"
                  @click="importCert(cert.certificate_details.hash)"
                  >{{ $t('keys.importCert') }}</SmallButton
                >

                <!-- Special case where HW cert has auth usage -->
                <div v-else-if="key.usage === 'AUTHENTICATION'">
                  {{ $t('keys.authNotSupported') }}
                </div>
              </template>
            </div>
          </CertificateRow>
        </template>

        <!-- CSRs -->
        <template
          v-if="
            key.certificate_signing_requests &&
              key.certificate_signing_requests.length > 0
          "
        >
          <tr
            v-for="req in key.certificate_signing_requests"
            v-bind:key="req.id"
          >
            <td class="td-name">
              <div class="name-wrap">
                <i class="icon-xrd_certificate icon"></i>
                <div>{{ $t('keys.request') }}</div>
              </div>
            </td>
            <td colspan="4">{{ req.id }}</td>
            <td class="td-align-right">
              <SmallButton
                class="table-button-fix"
                v-if="hasPermission && req.possible_actions.includes('DELETE')"
                @click="showDeleteCsrDialog(req, key)"
                >{{ $t('keys.deleteCsr') }}</SmallButton
              >
            </td>
          </tr>
        </template>
      </tbody>
    </table>

    <RegisterCertificateDialog
      :dialog="registerDialog"
      @save="registerCert"
      @cancel="registerDialog = false"
    />

    <ConfirmDialog
      :dialog="confirmDeleteCsr"
      title="keys.deleteCsrTitle"
      text="keys.deleteCsrText"
      @cancel="confirmDeleteCsr = false"
      @accept="deleteCsr()"
    />
  </div>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import Vue from 'vue';
import RegisterCertificateDialog from './RegisterCertificateDialog.vue';
import KeyRow from './KeyRow.vue';
import CertificateRow from './CertificateRow.vue';
import SmallButton from '@/components/ui/SmallButton.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import {
  Key,
  TokenCertificate,
  TokenCertificateSigningRequest,
} from '@/openapi-types';
import { Permissions, UsageTypes, PossibleActions } from '@/global';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: {
    SmallButton,
    RegisterCertificateDialog,
    ConfirmDialog,
    KeyRow,
    CertificateRow,
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
  data() {
    return {
      registerDialog: false,
      confirmDeleteCsr: false,
      usageTypes: UsageTypes,
      selectedCert: undefined as TokenCertificate | undefined,
      selectedCsr: undefined as TokenCertificateSigningRequest | undefined,
      selectedKey: undefined as Key | undefined,
    };
  },
  computed: {
    hasPermission(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.ACTIVATE_DEACTIVATE_TOKEN,
      );
    },
    showRegisterCertButton(): boolean {
      if (
        this.hasPermission &&
        this.$store.getters.hasPermission(Permissions.SEND_AUTH_CERT_REG_REQ)
      ) {
        return true;
      }
      return false;
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
    certificateClick(cert: TokenCertificate, key: Key): void {
      this.$emit('certificateClick', { cert, key });
    },
    generateCsr(key: Key): void {
      this.$emit('generateCsr', key);
    },
    importCert(hash: string): void {
      this.$emit('importCertByHash', hash);
    },
    showRegisterCertDialog(cert: TokenCertificate): void {
      this.registerDialog = true;
      this.selectedCert = cert;
    },
    registerCert(address: string): void {
      this.registerDialog = false;
      if (!this.selectedCert) {
        return;
      }

      api
        .put(
          `/token-certificates/${this.selectedCert.certificate_details.hash}/register`,
          { address },
        )
        .then(() => {
          this.$store.dispatch('showSuccess', 'keys.certificateRegistered');
          this.$emit('refreshList');
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },
    showDeleteCsrDialog(req: TokenCertificateSigningRequest, key: Key): void {
      this.confirmDeleteCsr = true;
      this.selectedCsr = req;
      this.selectedKey = key;
    },
    deleteCsr(): void {
      this.confirmDeleteCsr = false;

      if (!this.selectedKey || !this.selectedCsr) {
        return;
      }

      api
        .remove(
          `/keys/${encodePathParameter(
            this.selectedKey.id,
          )}/csrs/${encodePathParameter(this.selectedCsr.id)}`,
        )
        .then(() => {
          this.$store.dispatch('showSuccess', 'keys.csrDeleted');
          this.$emit('refreshList');
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
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

.table-button-fix {
  margin-left: auto;
  margin-right: 0;
}

.td-align-right {
  text-align: right;
}

.td-name {
  text-align: center;
  vertical-align: middle;
}

.name-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-left: 0.5rem;

  i.v-icon.mdi-file-document-outline {
    margin-left: 42px;
  }
}
.title-col {
  width: 30%;
}
.ocsp-col,
.expiration-col,
.status-col,
.action-col {
  width: 10%;
}
</style>
