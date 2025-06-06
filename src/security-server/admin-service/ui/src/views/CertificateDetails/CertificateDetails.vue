<!--
   The MIT License
   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->
<template>
  <CertificateView
    v-if="certificate"
    :certificate-details="certificate.certificate_details"
  >
    <template #tools>
      <xrd-button
        v-if="showActivate"
        class="button-spacing"
        outlined
        data-test="activate-button"
        @click="activateCertificate(certificate.certificate_details.hash)"
      >
        {{ $t('action.activate') }}
      </xrd-button>
      <xrd-button
        v-if="showDisable"
        class="button-spacing"
        outlined
        data-test="deactivate-button"
        @click="deactivateCertificate(certificate.certificate_details.hash)"
      >
        {{ $t('action.deactivate') }}
      </xrd-button>
      <xrd-button
        v-if="showUnregister"
        class="button-spacing"
        outlined
        data-test="unregister-button"
        @click="confirmUnregisterCertificate = true"
      >
        {{ $t('action.unregister') }}
      </xrd-button>
      <xrd-button
        v-if="showDelete"
        class="button-spacing"
        outlined
        data-test="delete-button"
        @click="showConfirmDelete()"
      >
        <xrd-icon-base class="xrd-large-button-icon">
          <xrd-icon-declined />
        </xrd-icon-base>
        {{ $t('action.delete') }}
      </xrd-button>
    </template>
  </CertificateView>

  <!-- Confirm dialog for delete -->
  <xrd-confirm-dialog
    v-if="confirm"
    title="cert.deleteCertTitle"
    text="cert.deleteCertConfirm"
    @cancel="confirm = false"
    @accept="deleteCertificate()"
  />

  <!-- Confirm dialog for unregister certificate -->
  <xrd-confirm-dialog
    v-if="confirmUnregisterCertificate"
    :loading="unregisterLoading"
    title="keys.unregisterTitle"
    text="keys.unregisterText"
    @cancel="confirmUnregisterCertificate = false"
    @accept="unregisterCert()"
  />

  <!-- Confirm dialog for unregister error handling -->
  <UnregisterErrorDialog
    v-if="unregisterErrorResponse"
    :error-response="unregisterErrorResponse"
    :dialog="confirmUnregisterError"
    @cancel="confirmUnregisterError = false"
    @accept="markForDeletion()"
  />
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { Permissions } from '@/global';
import {
  KeyUsageType,
  PossibleAction,
  PossibleActions as PossibleActionsList,
  TokenCertificate,
} from '@/openapi-types';
import UnregisterErrorDialog from './UnregisterErrorDialog.vue';
import { PossibleActions } from '@/openapi-types/models/PossibleActions';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import CertificateView from '@/components/certificate/CertificateView.vue';

export default defineComponent({
  components: {
    CertificateView,
    UnregisterErrorDialog,
  },
  props: {
    hash: {
      type: String,
      required: true,
    },
    usage: {
      type: String,
      default: undefined,
    },
  },
  emits: ['refresh-list'],
  data() {
    return {
      confirm: false,
      certificate: undefined as TokenCertificate | undefined,
      possibleActions: [] as PossibleActions,
      confirmUnregisterCertificate: false,
      confirmUnregisterError: false,
      unregisterLoading: false,
      unregisterErrorResponse: undefined as undefined | Record<string, unknown>,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    showDelete(): boolean {
      if (this.possibleActions.includes(PossibleAction.DELETE)) {
        if (this.usage === KeyUsageType.SIGNING) {
          return this.hasPermission(Permissions.DELETE_SIGN_CERT);
        } else if (this.usage === KeyUsageType.AUTHENTICATION) {
          return this.hasPermission(Permissions.DELETE_AUTH_CERT);
        } else {
          return this.hasPermission(Permissions.DELETE_UNKNOWN_CERT);
        }
      } else {
        return false;
      }
    },

    showUnregister(): boolean {
      if (
        this.possibleActions.includes(PossibleAction.UNREGISTER) &&
        this.hasPermission(Permissions.SEND_AUTH_CERT_DEL_REQ)
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

      if (this.possibleActions.includes(PossibleAction.ACTIVATE)) {
        if (this.usage === KeyUsageType.SIGNING) {
          return this.hasPermission(Permissions.ACTIVATE_DISABLE_SIGN_CERT);
        } else {
          return this.hasPermission(Permissions.ACTIVATE_DISABLE_AUTH_CERT);
        }
      }

      return false;
    },

    showDisable(): boolean {
      if (this.certificate === null) {
        return false;
      }

      if (this.possibleActions.includes(PossibleAction.DISABLE)) {
        if (this.usage === KeyUsageType.SIGNING) {
          return this.hasPermission(Permissions.ACTIVATE_DISABLE_SIGN_CERT);
        } else {
          return this.hasPermission(Permissions.ACTIVATE_DISABLE_AUTH_CERT);
        }
      }

      return false;
    },
  },
  created() {
    this.fetchData(this.hash);
  },

  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    close(): void {
      this.$router.back();
    },
    fetchData(hash: string): void {
      // Fetch certificate data
      api
        .get<TokenCertificate>(
          `/token-certificates/${encodePathParameter(hash)}`,
        )
        .then((res) => {
          this.certificate = res.data;
        })
        .catch((error) => {
          this.showError(error);
        });

      // Fetch possible actions
      api
        .get<PossibleActionsList>(
          `/token-certificates/${encodePathParameter(hash)}/possible-actions`,
        )
        .then((res) => {
          this.possibleActions = res.data;
        })
        .catch((error) => {
          this.showError(error);
        });
    },
    showConfirmDelete(): void {
      this.confirm = true;
    },
    deleteCertificate(): void {
      this.confirm = false;

      api
        .remove(`/token-certificates/${encodePathParameter(this.hash)}`)
        .then(() => {
          this.close();
          this.showSuccess(this.$t('cert.certDeleted'));
        })
        .catch((error) => {
          this.showError(error);
        });
    },
    activateCertificate(hash: string): void {
      api
        .put(`/token-certificates/${encodePathParameter(hash)}/activate`, hash)
        .then(() => {
          this.showSuccess(this.$t('cert.activateSuccess'));
          this.fetchData(this.hash);
        })
        .catch((error) => this.showError(error));
    },
    deactivateCertificate(hash: string): void {
      api
        .put(`token-certificates/${encodePathParameter(hash)}/disable`, hash)
        .then(() => {
          this.showSuccess(this.$t('cert.disableSuccess'));
          this.fetchData(this.hash);
        })
        .catch((error) => this.showError(error));
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
          this.showSuccess(this.$t('keys.certificateUnregistered'));
          this.fetchData(this.hash);
        })
        .catch((error) => {
          if (
            error?.response?.data?.error?.code ===
            'management_request_sending_failed'
          ) {
            this.unregisterErrorResponse = error.response;
          } else {
            this.showError(error);
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
          this.showSuccess(this.$t('keys.certMarkedForDeletion'));
          this.confirmUnregisterError = false;
          this.$emit('refresh-list');
        })
        .catch((error) => {
          this.showError(error);
          this.confirmUnregisterError = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped>
.button-spacing {
  margin-left: 20px;
}
</style>
