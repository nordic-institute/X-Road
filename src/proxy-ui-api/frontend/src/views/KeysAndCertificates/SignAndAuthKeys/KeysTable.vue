<template>
  <div>
    <table class="xrd-table">
      <!-- SOFTWARE token table header -->
      <template v-if="tokenType === 'SOFTWARE'">
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
      <template v-if="tokenType === 'HARDWARE'">
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
        <template v-if="tokenType === 'SOFTWARE'">
          <tr>
            <div class="name-wrap-top">
              <i class="icon-xrd_key icon clickable" @click="keyClick(key)"></i>
              <div class="clickable-link" @click="keyClick(key)">{{key.name}}</div>
            </div>
            <td class="no-border"></td>
            <td class="no-border"></td>
            <td class="no-border"></td>
            <td class="no-border"></td>
            <td class="no-border td-align-right">
              <SmallButton
                v-if="hasPermission"
                class="table-button-fix"
                :disabled="disableGenerateCsr(key)"
                @click="generateCsr(key)"
              >{{$t('keys.generateCsr')}}</SmallButton>
            </td>
          </tr>

          <tr v-for="cert in key.certificates" v-bind:key="cert.id">
            <td class="td-name">
              <div class="name-wrap">
                <i class="icon-xrd_certificate icon clickable" @click="certificateClick(cert, key)"></i>
                <div
                  class="clickable-link"
                  @click="certificateClick(cert, key)"
                >{{cert.certificate_details.issuer_common_name}} {{cert.certificate_details.serial}}</div>
              </div>
            </td>
            <td>{{cert.owner_id}}</td>
            <td>{{ cert.ocsp_status | ocspStatus }}</td>
            <td>{{cert.certificate_details.not_after | formatDate}}</td>
            <td class="status-cell">
              <certificate-status :certificate="cert" />
            </td>
            <td class="td-align-right">
              <SmallButton
                class="table-button-fix test-register"
                v-if="showRegisterCertButton && cert.possible_actions.includes('REGISTER')"
                @click="showRegisterCertDialog(cert)"
              >{{$t('action.register')}}</SmallButton>
            </td>
          </tr>
        </template>

        <!-- HARDWARE token table body -->
        <template v-if="tokenType === 'HARDWARE'">
          <tr>
            <div class="name-wrap-top">
              <i class="icon-xrd_key icon clickable" @click="keyClick(key)"></i>
              <div class="clickable-link" @click="keyClick(key)">{{key.name}}</div>
            </div>
            <td class="no-border"></td>
            <td class="no-border"></td>
            <td class="no-border"></td>
            <td class="no-border"></td>
            <td class="no-border"></td>
            <td class="no-border td-align-right">
              <SmallButton
                v-if="hasPermission"
                class="table-button-fix"
                :disabled="disableGenerateCsr(key)"
                @click="generateCsr(key)"
              >{{$t('keys.generateCsr')}}</SmallButton>
            </td>
          </tr>

          <tr v-for="cert in key.certificates" v-bind:key="cert.id">
            <td class="td-name">
              <div class="name-wrap">
                <i class="icon-xrd_certificate icon clickable" @click="certificateClick(cert, key)"></i>
                <div
                  class="clickable-link"
                  @click="certificateClick(cert, key)"
                >{{cert.certificate_details.issuer_common_name}} {{cert.certificate_details.serial}}</div>
              </div>
            </td>
            <td>{{cert.owner_id}}</td>
            <td>{{ cert.ocsp_status | ocspStatus }}</td>
            <td>{{cert.certificate_details.not_after | formatDate}}</td>
            <td class="status-cell">
              <certificate-status :certificate="cert" />
            </td>
            <td></td>
            <td class="td-align-right">
              <SmallButton
                class="table-button-fix"
                v-if="!cert.saved_to_configuration && hasPermission"
                @click="importCert()"
              >{{$t('keys.importCert')}}</SmallButton>
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
                <i class="icon-xrd_certificate icon"></i>
                <div>{{$t('keys.request')}}</div>
              </div>
            </td>
            <td>{{req.id}}</td>
            <td></td>
            <td></td>
            <td class="status-cell"></td>
            <td class="td-align-right">
              <SmallButton
                class="table-button-fix"
                v-if="hasPermission && req.possible_actions.includes('DELETE')"
                @click="showDeleteCsrDialog(req, key)"
              >{{$t('keys.deleteCsr')}}</SmallButton>
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
import CertificateStatus from './CertificateStatus.vue';
import RegisterCertificateDialog from './RegisterCertificateDialog.vue';
import SmallButton from '@/components/ui/SmallButton.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import { Key, TokenCertificate, TokenCertificateSigningRequest } from '@/openapi-types';
import { Permissions, UsageTypes, PossibleActions } from '@/global';
import * as api from '@/util/api';

export default Vue.extend({
  components: {
    CertificateStatus,
    SmallButton,
    RegisterCertificateDialog,
    ConfirmDialog,
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
    cancelRegisterCert(): void {
      this.registerDialog = false;
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
        .then((res) => {
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
        .remove(`/keys/${this.selectedKey.id}/csrs/${this.selectedCsr.id}`)
        .then((res) => {
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

.clickable {
  cursor: pointer;
}

.no-border {
  border-bottom-width: 0 !important;
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
