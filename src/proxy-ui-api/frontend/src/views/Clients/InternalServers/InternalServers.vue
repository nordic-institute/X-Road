<template>
  <div>
    <v-card flat class="xrd-card" v-if="showConnectionType">
      <v-flex>
        <h1 class="title mb-3">{{ $t('internalServers.connectionType') }}</h1>
        <v-select
          v-model="connectionType"
          :items="connectionTypes"
          class="select-connection"
          :key="revertHack"
          :disabled="!canEditConnectionType"
          :readonly="!canEditConnectionType"
        ></v-select>
      </v-flex>
      <div class="conn-info">{{ $t('internalServers.connectionInfo') }}</div>
    </v-card>

    <v-card flat class="xrd-card">
      <div class="tls-title-wrap">
        <h1 class="title mb-3">{{ $t('internalServers.tlsTitle') }}</h1>
        <file-upload
          accepts=".pem, .cer, .der"
          @fileChanged="onFileChange"
          v-slot="{ upload }"
        >
          <v-btn
            v-if="canAddTlsCert"
            outlined
            rounded
            color="primary"
            class="rounded-button elevation-0"
            @click="upload"
            >{{ $t('action.add') }}</v-btn
          >
        </file-upload>
      </div>
      <div class="cert-table-title">{{ $t('internalServers.certHash') }}</div>
      <table class="certificate-table server-certificates">
        <template v-if="tlsCertificates && tlsCertificates.length > 0">
          <tr
            v-for="certificate in tlsCertificates"
            v-bind:key="certificate.hash"
          >
            <td class="cert-icon">
              <certificateIcon />
            </td>
            <td>
              <span
                v-if="canViewTlsCertDetails"
                @click="openCertificate(certificate)"
                class="certificate-link"
                >{{ certificate.hash | colonize }}</span
              >
              <span v-else>{{ certificate.hash | colonize }}</span>
            </td>
          </tr>
        </template>
      </table>
    </v-card>

    <v-card v-if="canViewSSCert" flat class="xrd-card">
      <h1 class="title mb-3">{{ $t('internalServers.ssCertTitle') }}</h1>
      <div class="cert-table-title">{{ $t('internalServers.certHash') }}</div>
      <table class="certificate-table server-certificates">
        <template v-if="ssCertificate">
          <tr>
            <td class="cert-icon">
              <certificateIcon />
            </td>
            <td>
              <span>{{ ssCertificate.hash | colonize }}</span>
            </td>

            <td class="column-button">
              <v-btn
                v-if="canExportSSCert"
                small
                outlined
                rounded
                color="primary"
                class="xrd-small-button"
                @click="exportSSCertificate"
                >{{ $t('action.export') }}</v-btn
              >
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
import { Permissions, RouteName } from '@/global';
import CertificateIcon from './CertificateIcon.vue';
import FileUpload from '@/components/ui/FileUpload.vue';
import { FileUploadResult } from '@/ui-types';
import { CertificateDetails } from '@/openapi-types';
export default Vue.extend({
  components: {
    CertificateIcon,
    FileUpload,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      connectionTypes: [
        { text: 'HTTP', value: 'HTTP' },
        { text: 'HTTPS', value: 'HTTPS' },
        { text: 'HTTPS NO AUTH', value: 'HTTPS_NO_AUTH' },
      ],
      dialog: false,
      selectedCertificate: null,
      revertHack: 0,
    };
  },
  computed: {
    ...mapGetters(['tlsCertificates', 'ssCertificate']),

    connectionType: {
      get(): string | undefined {
        return this.$store.getters.connectionType;
      },
      set(value: string) {
        this.$store
          .dispatch('saveConnectionType', {
            clientId: this.id,
            connType: value,
          })
          .then(() => {
            this.$store.dispatch(
              'showSuccess',
              'internalServers.connTypeUpdated',
            );
          })
          .catch((error) => {
            this.revertHack += 1;
            this.$store.dispatch('showError', error);
          });
      },
    },

    showConnectionType(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.VIEW_CLIENT_INTERNAL_CONNECTION_TYPE,
      );
    },
    canEditConnectionType(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.EDIT_CLIENT_INTERNAL_CONNECTION_TYPE,
      );
    },
    canViewTlsCertDetails(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.VIEW_CLIENT_INTERNAL_CERT_DETAILS,
      );
    },
    canAddTlsCert(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.ADD_CLIENT_INTERNAL_CERT,
      );
    },
    canViewSSCert(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.VIEW_INTERNAL_SSL_CERT,
      );
    },
    canExportSSCert(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.EXPORT_INTERNAL_SSL_CERT,
      );
    },
  },
  created() {
    this.fetchSSCertificate(this.id);
    this.fetchTlsCertificates(this.id);
  },
  methods: {
    onFileChange(event: FileUploadResult): void {
      this.$store
        .dispatch('uploadTlsCertificate', {
          clientId: this.id,
          fileData: event.buffer,
        })
        .then(
          () => {
            // Refresh the tls cert list
            this.fetchTlsCertificates(this.id);
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        );
    },

    fetchTlsCertificates(id: string): void {
      this.$store.dispatch('fetchTlsCertificates', id).catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },

    exportSSCertificate(): void {
      this.$store.dispatch('downloadSSCertificate').catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },

    fetchSSCertificate(id: string): void {
      this.$store.dispatch('fetchSSCertificate', id).catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },

    openCertificate(cert: CertificateDetails): void {
      this.$router.push({
        name: RouteName.ClientTlsCertificate,
        params: {
          id: this.id,
          hash: cert.hash,
        },
      });
    },
    closeDialog(): void {
      this.dialog = false;
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
@import '../../../assets/colors';

.select-connection {
  max-width: 240px;
}

.tls-title-wrap {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
}

.xrd-card {
  margin-top: 50px;
}

.conn-info {
  color: $XRoad-Grey60;
}

.cert-table-title {
  color: $XRoad-Grey60;
  font-size: 14px;
  font-weight: bold;
  margin: 5px;
}

.server-certificates {
  width: 100%;
  border-top: $XRoad-Grey40 solid 1px;
}

.cert-icon {
  width: 20px;
}

.column-button {
  text-align: end;
}

.certificate-link {
  text-decoration: underline;
  cursor: pointer;
}
</style>
