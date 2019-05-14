<template>
  <div>
    <v-card flat class="xr-card" v-if="showConnectionType">
      <v-flex>
        <h1 class="title mb-3">Connection type</h1>
        <v-select
          v-model="connectionType"
          :items="connectionTypes"
          class="select-connection"
          :key="revertHack"
          :disabled="!canEditConnectionType"
          :readonly="!canEditConnectionType"
        ></v-select>
      </v-flex>
      <div
        class="conn-info"
      >Connection type for servers in service provider role is set in the Services tab by the service URL (http/https)</div>
    </v-card>

    <v-card flat class="xr-card">
      <div class="tls-title-wrap">
        <h1 class="title mb-3">Information System TLS certificate</h1>
        <v-btn
          v-if="canAddTlsCert"
          outline
          round
          color="primary"
          class="text-capitalize table-button rounded-button"
          type="file"
          @click="$refs.inputUpload.click()"
        >Add</v-btn>
        <input
          v-show="false"
          ref="inputUpload"
          type="file"
          accept=".pem, .cer, .der"
          @change="onFileChange"
        >
      </div>
      <div class="cert-table-title">Certificate Hash (SHA/1)</div>
      <table class="certificate-table server-certificates">
        <template v-if="tlsCertificates && tlsCertificates.length > 0">
          <tr v-for="certificate in tlsCertificates" v-bind:key="certificate.hash">
            <td class="cert-icon">
              <certificateIcon/>
            </td>
            <td>
              <span
                v-if="canViewTlsCertDetails"
                @click="openCertificate(certificate)"
                class="certificate-link"
              >{{certificate.hash | colonize}}</span>
              <span v-else>{{certificate.hash | colonize}}</span>
            </td>
          </tr>
        </template>
      </table>
    </v-card>

    <v-card v-if="canViewSSCert" flat class="xr-card">
      <h1 class="title mb-3">Security Server certificate</h1>
      <div class="cert-table-title">Certificate Hash (SHA/1)</div>
      <table class="certificate-table server-certificates">
        <template v-if="ssCertificate">
          <tr>
            <td class="cert-icon">
              <certificateIcon/>
            </td>
            <td>
              <span>{{ssCertificate.hash | colonize}}</span>
            </td>

            <td class="column-button">
              <v-btn
                v-if="canExportSSCert"
                small
                outline
                round
                color="primary"
                class="text-capitalize table-button xr-small-button"
                @click="exportSSCertificate(ssCertificate.hash)"
              >Export</v-btn>
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
import CertificateIcon from '@/components/CertificateIcon.vue';
export default Vue.extend({
  components: {
    CertificateIcon,
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
          .catch((error) => {
            this.revertHack += 1;
            this.$bus.$emit('show-error', error.message);
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
    onFileChange(e: any) {
      const fileList = e.target.files || e.dataTransfer.files;
      if (!fileList.length) {
        return;
      }

      const reader = new FileReader();

      // Upload file when it's loaded in FileReader
      reader.onload = (e: any) => {
        if (!e || !e.target || !e.target.result) {
          return;
        }

        this.$store
          .dispatch('uploadTlsCertificate', {
            clientId: this.id,
            fileData: e.target.result,
          })
          .then(
            (response) => {
              // Refresh the tls cert list
              this.fetchTlsCertificates(this.id);
            },
            (error) => {
              this.$bus.$emit('show-error', error.message);
            },
          );
      };

      reader.readAsArrayBuffer(fileList[0]);
    },

    fetchServer(id: string) {
      this.$store.dispatch('fetchServer').catch((error) => {
        this.$bus.$emit('show-error', error.message);
      });
    },

    fetchTlsCertificates(id: string) {
      this.$store.dispatch('fetchTlsCertificates', id).catch((error) => {
        this.$bus.$emit('show-error', error.message);
      });
    },

    exportSSCertificate(hash: string) {
      this.$store.dispatch('downloadSSCertificate', hash).catch((error) => {
        this.$bus.$emit('show-error', error.message);
      });
    },

    fetchSSCertificate(id: string) {
      this.$store.dispatch('fetchSSCertificate', id).catch((error) => {
        this.$bus.$emit('show-error', error.message);
      });
    },

    openCertificate(cert: any) {
      this.$router.push({
        name: RouteName.Certificate,
        params: {
          id: this.id,
          hash: cert.hash,
        },
      });
    },
    closeDialog() {
      this.dialog = false;
    },
  },
});
</script>

<style lang="scss" >
@import '../assets/tables';
@import '../assets/colors';

.select-connection {
  max-width: 240px;
}

.tls-title-wrap {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
}

.xr-card {
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
  border-top: #9b9b9b solid 2px;
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

