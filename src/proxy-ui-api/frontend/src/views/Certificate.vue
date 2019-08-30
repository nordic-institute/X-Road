<template>
  <div class="wrapper">
    <div class="new-content">
      <subViewTitle :title="$t('cert.certificate')" @close="close" />
      <template v-if="certificate">
        <div class="cert-hash">
          <div>
            <div class="hash-info">{{$t('cert.hashInfo')}}</div>
            <div>{{certificate.hash | colonize}}</div>
          </div>

          <v-btn
            v-if="showDeleteButton"
            outlined
            round
            color="primary"
            class="text-capitalize table-button rounded-button"
            @click="deleteCertificate()"
          >{{$t('action.delete')}}</v-btn>
        </div>

        <certificate-line childKey="version" :sourceObject="certificate" />
        <certificate-line childKey="serial" :sourceObject="certificate" />
        <certificate-line childKey="signature_algorithm" :sourceObject="certificate" />
        <certificate-line childKey="issuer_distinguished_name" :sourceObject="certificate" />
        <certificate-line childKey="not_before" :sourceObject="certificate" date />
        <certificate-line childKey="not_after" :sourceObject="certificate" date />
        <certificate-line childKey="subject_distinguished_name" :sourceObject="certificate" />

        <certificate-line childKey="public_key_algorithm" :sourceObject="certificate" />
        <certificate-line
          childKey="rsa_public_key_modulus"
          :label="$t('cert.rsaModulus')"
          :sourceObject="certificate"
          chunk
        />

        <certificate-line
          childKey="rsa_public_key_exponent"
          :label="$t('cert.rsaExp')"
          :sourceObject="certificate"
        />

        <certificate-line childKey="state" :sourceObject="certificate" />
        <certificate-line childKey="key_usages" arrayType :sourceObject="certificate" />
        <certificate-line childKey="signature" :sourceObject="certificate" chunk />
      </template>
    </div>
    <v-dialog v-model="confirm" persistent max-width="290">
      <v-card>
        <v-card-title class="headline">{{$t('cert.deleteCertTitle')}}</v-card-title>
        <v-card-text>{{$t('cert.deleteCertConfirm')}}</v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="primary" flat @click="confirm = false">{{$t('action.cancel')}}</v-btn>
          <v-btn color="primary" flat @click="doDeleteCertificate()">{{$t('action.yes')}}</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { Permissions } from '@/global';
import SubViewTitle from '@/components/SubViewTitle.vue';
import CertificateLine from '@/components/CertificateLine.vue';

export default Vue.extend({
  components: {
    SubViewTitle,
    CertificateLine,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
    hash: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      confirm: false,
      certificate: null,
    };
  },
  computed: {
    ...mapGetters(['tlsCertificates']),
    showDeleteButton(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.DELETE_CLIENT_INTERNAL_CERT,
      );
    },
  },
  filters: {
    pretty(value: any) {
      return JSON.stringify(JSON.parse(value), null, 2);
    },
  },
  methods: {
    close(): void {
      this.$router.go(-1);
    },
    fetchData(clientId: string, hash: string): void {
      this.$store.dispatch('fetchTlsCertificate', { clientId, hash }).then(
        (response) => {
          this.certificate = response.data;
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      );
    },
    deleteCertificate(): void {
      this.confirm = true;
    },
    doDeleteCertificate(): void {
      this.confirm = false;

      this.$store
        .dispatch('deleteTlsCertificate', {
          clientId: this.id,
          hash: this.hash,
        })
        .then(
          (response) => {
            this.$bus.$emit('show-success', 'cert.certDeleted');
          },
          (error) => {
            this.$bus.$emit('show-error', error.message);
          },
        )
        .finally(() => {
          this.close();
        });
    },
  },
  created() {
    this.fetchData(this.id, this.hash);
  },
});
</script>

<style lang="scss" scoped>
.wrapper {
  display: flex;
  justify-content: center;
  flex-direction: column;
  max-width: 850px;
  height: 100%;
  width: 100%;
}

.content {
  max-width: 850px;
  width: 400px;
  border: 1px black solid;
}

.cert-dialog-header {
  display: flex;
  justify-content: center;
  border-bottom: 1px solid #9b9b9b;
  color: #4a4a4a;
  font-family: Roboto;
  font-size: 34px;
  font-weight: 300;
  letter-spacing: 0.5px;
  line-height: 51px;
}

#close-x {
  cursor: pointer;
  font-style: normal;
  font-size: 50px;
  color: #979797;
}

#close-x:before {
  content: '\00d7';
}

.cert-hash {
  margin-top: 30px;
  display: flex;
  justify-content: space-between;
  color: #202020;
  font-family: Roboto;
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.5px;
  line-height: 30px;
  margin-bottom: 20px;
}

.hash-info {
  color: #202020;
  font-family: Roboto;
  font-size: 16px;
}
</style>

