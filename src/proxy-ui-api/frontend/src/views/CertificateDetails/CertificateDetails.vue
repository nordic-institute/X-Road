<template>
  <div class="wrapper xrd-view-common">
    <div class="new-content">
      <subViewTitle :title="$t('cert.certificate')" @close="close" />
      <div class="details-view-tools" v-if="certificate">
        <large-button
          v-if="showActivate"
          class="button-spacing"
          outlined
          @click="activateCertificate(certificate.certificate_details.hash)"
          data-test="activate-button"
          >{{ $t('action.activate') }}</large-button
        >
        <large-button
          v-if="showDisable"
          class="button-spacing"
          outlined
          @click="deactivateCertificate(certificate.certificate_details.hash)"
          data-test="deactivate-button"
          >{{ $t('action.deactivate') }}</large-button
        >
        <large-button
          v-if="showUnregister"
          class="button-spacing"
          outlined
          @click="confirmUnregisterCertificate = true"
          data-test="unregister-button"
          >{{ $t('action.unregister') }}</large-button
        >
        <large-button
          v-if="showDelete"
          class="button-spacing"
          outlined
          @click="showConfirmDelete()"
          data-test="delete-button"
          >{{ $t('action.delete') }}</large-button
        >
      </div>
      <template v-if="certificate && certificate.certificate_details">
        <div class="cert-hash-wrapper">
          <certificateHash :hash="certificate.certificate_details.hash" />
        </div>
        <certificateInfo :certificate="certificate.certificate_details" />
      </template>
    </div>

    <!-- Confirm dialog for delete -->
    <confirmDialog
      :dialog="confirm"
      title="cert.deleteCertTitle"
      text="cert.deleteCertConfirm"
      @cancel="confirm = false"
      @accept="deleteCertificate()"
    />

    <!-- Confirm dialog for unregister certificate -->
    <ConfirmDialog
      :dialog="confirmUnregisterCertificate"
      :loading="unregisterLoading"
      title="keys.unregisterTitle"
      text="keys.unregisterText"
      @cancel="confirmUnregisterCertificate = false"
      @accept="unregisterCert()"
    />

    <!-- Confirm dialog for unregister error handling -->
    <UnregisterErrorDialog
      v-if="unregisterErrorResponse"
      :errorResponse="unregisterErrorResponse"
      :dialog="confirmUnregisterError"
      @cancel="confirmUnregisterError = false"
      @accept="markForDeletion()"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import { UsageTypes, Permissions, PossibleActions } from '@/global';
import {
  TokenCertificate,
  PossibleActions as PossibleActionsList,
} from '@/openapi-types';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import CertificateInfo from '@/components/certificate/CertificateInfo.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import CertificateHash from '@/components/certificate/CertificateHash.vue';
import UnregisterErrorDialog from './UnregisterErrorDialog.vue';

export default Vue.extend({
  components: {
    CertificateInfo,
    ConfirmDialog,
    SubViewTitle,
    LargeButton,
    CertificateHash,
    UnregisterErrorDialog,
  },
  props: {
    hash: {
      type: String,
      required: true,
    },
    usage: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      confirm: false,
      certificate: undefined as TokenCertificate | undefined,
      possibleActions: [] as string[],
      confirmUnregisterCertificate: false,
      confirmUnregisterError: false,
      unregisterLoading: false,
      unregisterErrorResponse: undefined as undefined | object,
    };
  },
  computed: {
    showDelete(): boolean {
      if (this.possibleActions.includes(PossibleActions.DELETE)) {
        if (this.usage === UsageTypes.SIGNING) {
          return this.$store.getters.hasPermission(
            Permissions.DELETE_SIGN_CERT,
          );
        } else {
          return this.$store.getters.hasPermission(
            Permissions.DELETE_AUTH_CERT,
          );
        }
      } else {
        return false;
      }
    },

    showUnregister(): boolean {
      if (
        this.possibleActions.includes(PossibleActions.UNREGISTER) &&
        this.$store.getters.hasPermission(Permissions.SEND_AUTH_CERT_DEL_REQ)
      ) {
        return true;
      } else {
        return false;
      }
    },

    showActivate(): boolean {
      if (this.certificate === null) {
        return false;
      }

      if (this.possibleActions.includes(PossibleActions.ACTIVATE)) {
        if (this.usage === UsageTypes.SIGNING) {
          return this.$store.getters.hasPermission(
            Permissions.ACTIVATE_DISABLE_SIGN_CERT,
          );
        } else {
          return this.$store.getters.hasPermission(
            Permissions.ACTIVATE_DISABLE_AUTH_CERT,
          );
        }
      }

      return false;
    },

    showDisable(): boolean {
      if (this.certificate === null) {
        return false;
      }

      if (this.possibleActions.includes(PossibleActions.DISABLE)) {
        if (this.usage === UsageTypes.SIGNING) {
          return this.$store.getters.hasPermission(
            Permissions.ACTIVATE_DISABLE_SIGN_CERT,
          );
        } else {
          return this.$store.getters.hasPermission(
            Permissions.ACTIVATE_DISABLE_AUTH_CERT,
          );
        }
      }

      return false;
    },
  },

  methods: {
    close(): void {
      this.$router.go(-1);
    },
    fetchData(hash: string): void {
      // Fetch certificate data
      api
        .get<TokenCertificate>(`/token-certificates/${hash}`)
        .then((res) => {
          this.certificate = res.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });

      // Fetch possible actions
      api
        .get<PossibleActionsList>(
          `/token-certificates/${hash}/possible-actions`,
        )
        .then((res) => {
          this.possibleActions = res.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },
    showConfirmDelete(): void {
      this.confirm = true;
    },
    deleteCertificate(): void {
      this.confirm = false;

      api
        .remove(`/token-certificates/${this.hash}`)
        .then(() => {
          this.close();
          this.$store.dispatch('showSuccess', 'cert.certDeleted');
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },
    activateCertificate(hash: string): void {
      api
        .put(`/token-certificates/${hash}/activate`, hash)
        .then(() => {
          this.$store.dispatch('showSuccess', 'cert.activateSuccess');
          this.fetchData(this.hash);
        })
        .catch((error) => this.$store.dispatch('showError', error));
    },
    deactivateCertificate(hash: string): void {
      api
        .put(`token-certificates/${hash}/disable`, hash)
        .then(() => {
          this.$store.dispatch('showSuccess', 'cert.disableSuccess');
          this.fetchData(this.hash);
        })
        .catch((error) => this.$store.dispatch('showError', error));
    },

    unregisterCert(): void {
      this.unregisterLoading = true;

      if (!this.certificate) {
        return;
      }

      api
        .put(
          `/token-certificates/${this.certificate.certificate_details.hash}/unregister`,
          {},
        )
        .then(() => {
          this.$store.dispatch('showSuccess', 'keys.keyAdded');
        })
        .catch((error) => {
          if (
            error?.response?.data?.error?.code ===
            'management_request_sending_failed'
          ) {
            this.unregisterErrorResponse = error.response;
          } else {
            this.$store.dispatch('showError', error);
          }

          this.confirmUnregisterError = true;
        })
        .finally(() => {
          this.confirmUnregisterCertificate = false;
          this.unregisterLoading = false;
        });
    },

    markForDeletion(): void {
      if (!this.certificate) {
        return;
      }

      api
        .put(
          `/token-certificates/${this.certificate.certificate_details.hash}/mark-for-deletion`,
          {},
        )
        .then(() => {
          this.$store.dispatch('showSuccess', 'keys.certMarkedForDeletion');
          this.confirmUnregisterError = false;
          this.$emit('refreshList');
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
          this.confirmUnregisterError = false;
        });
    },
  },
  created() {
    this.fetchData(this.hash);
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/detail-views';

.wrapper {
  display: flex;
  justify-content: center;
  flex-direction: column;
  max-width: 850px;
  height: 100%;
  width: 100%;
}

.cert-hash-wrapper {
  margin-top: 30px;
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
}

.button-spacing {
  margin-left: 20px;
}
</style>
