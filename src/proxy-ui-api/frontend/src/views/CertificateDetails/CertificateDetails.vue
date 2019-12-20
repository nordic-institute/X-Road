<template>
  <div class="wrapper xrd-view-common">
        <div class="new-content">
      <subViewTitle :title="$t('cert.certificate')" @close="close" />
      <div class="details-view-tools" v-if="certificate">
        <large-button v-if="!certificate.active" outlined @click="activateCertificate(certificate.certificate_details.hash)">{{$t('action.activate')}}</large-button>
        <large-button v-if="certificate.active" outlined @click="deactivateCertificate(certificate.certificate_details.hash)">{{$t('action.deactivate')}}</large-button>
          <large-button
                  v-if="certificate.certificate_details.hash"
                  class="button-spacing"
                  outlined
                  @click="deleteCertificate()"
          >{{$t('action.delete')}}</large-button>
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
      @accept="doDeleteCertificate()"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import CertificateInfo from '@/components/certificate/CertificateInfo.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import CertificateHash from '@/components/certificate/CertificateHash.vue';

export default Vue.extend({
  components: {
    CertificateInfo,
    ConfirmDialog,
    SubViewTitle,
    LargeButton,
    CertificateHash,
  },
  props: {
    hash: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      confirm: false,
      certificate: {
      },
    };
  },

  methods: {
    close(): void {
      this.$router.go(-1);
    },
    fetchData(hash: string): void {
      api
        .get(`/token-certificates/${hash}`)
        .then((res) => {
          this.certificate = res.data;
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },
    deleteCertificate(): void {
      this.confirm = true;
    },
    doDeleteCertificate(): void {
      this.confirm = false;
      // TODO will be implemented on later task
    },
    activateCertificate(hash: string): void {
      api
        .put(`/token-certificates/${hash}/activate`, hash)
        .then((res: any) => {
          this.$bus.$emit('show-success', 'cert.activateSuccess');
          this.fetchData(this.hash);
        })
        .catch((error) => this.$bus.$emit('show-error', error.message));
    },
    deactivateCertificate(hash: string): void {
      api
        .put(`token-certificates/${hash}/deactivate`, hash)
        .then((res) => {
            this.$bus.$emit('show-success', 'cert.disableSuccess');
            this.fetchData(this.hash);
        })
        .catch((error) => this.$bus.$emit('show-error', error.message));
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

