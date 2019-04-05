<template>
  <div>
    <v-card flat class="xr-card">
      <v-flex>
        <h1 class="title mb-3">Connection type</h1>
        <v-select
          v-model="connectionType"
          :items="connectionTypes"
          class="select-connection"
          :key="revertHack"
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
          outline
          round
          color="primary"
          class="text-capitalize table-button rounded-button"
          type="file"
          @click="$refs.inputUpload.click()"
        >Add</v-btn>
        <input v-show="false" ref="inputUpload" type="file" @change="onFileChange">
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
                @click="openCertificate(certificate)"
                class="certificate-link"
              >{{certificate.hash}}</span>
            </td>
          </tr>
        </template>
      </table>
    </v-card>

    <v-card flat class="xr-card">
      <h1 class="title mb-3">Security Server certificate</h1>
      <div class="cert-table-title">Certificate Hash (SHA/1)</div>
      <table class="certificate-table server-certificates">
        <template v-if="ssCertificate">
          <tr>
            <td class="cert-icon">
              <certificateIcon/>
            </td>
            <td>
              <span>{{ssCertificate.hash}}</span>
            </td>

            <td class="column-button">
              <v-btn
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
  data() {
    return {
      connectionTypes: ['http', 'https', 'https no auth'],
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
        this.$store.dispatch('saveConnectionType', value).catch((error) => {
          this.revertHack += 1;

          console.log(this.revertHack);
          this.$bus.$emit('show-error', error.message);
        });
      },
    },
  },
  created() {
    this.fetchSSCertificate(this.$route.params.id as string);
    this.fetchTlsCertificates(this.$route.params.id as string);
  },
  methods: {
    onFileChange(e: any) {
      const fileList = e.target.files || e.dataTransfer.files;
      if (!fileList.length) {
        return;
      }
      // this.createImage(files[0]);

      const formData = new FormData();
      // append the files to FormData
      Array.from(Array(fileList.length).keys()).map((x) => {
        formData.append('fieldName', fileList[x], fileList[x].name);
      });

      // save it

      this.$store.dispatch('uploadTlsCertificate', formData).then(
        (response) => {
          this.$bus.$emit('show-success', 'WOW!');
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      );
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
      //     downloadSSCertificate

      this.$store.dispatch('downloadSSCertificate', hash).then(
        (response) => {
          this.$bus.$emit('show-success', 'Download ok!');
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      );
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
          id: this.$route.params.id,
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
  font-family: 'Helvetica Neue';
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

