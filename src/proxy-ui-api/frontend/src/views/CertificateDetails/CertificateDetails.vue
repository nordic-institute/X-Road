<template>
  <div class="wrapper xrd-view-common">
    <div class="new-content">
      <subViewTitle :title="$t('cert.certificate')" @close="close" />
      <template v-if="certificate">
        <div class="cert-hash-wrapper">
          <certificateHash :hash="certificate.hash" />
          <large-button
            v-if="showDeleteButton"
            outlined
            @click="deleteCertificate()"
          >{{$t('action.delete')}}</large-button>
        </div>
        <certificateInfo :certificate="certificate" />
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
import { mapGetters } from 'vuex';
import { Permissions } from '@/global';
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
      // TODO: mock data will be removed later
      certificate: {
        issuer_distinguished_name: 'CN=256be4e26302',
        issuer_common_name: '256be4e26302',
        subject_distinguished_name: 'CN=256be4e26302',
        subject_common_name: '256be4e26302',
        not_before: '2019-02-11T14:43:30Z',
        not_after: '2039-02-06T14:43:30Z',
        serial: '10691527287795546639',
        version: 3,
        signature_algorithm: 'SHA256withRSA',
        signature:
          '068447a5dfbb64ae543967e5064cac082e6a2f2ebc10342d3ae39f46a6b684a8648c9a21490723b6df0c477cdd112bb9b95b66335a913dcd218c66ac3b45c448b9848a6c3c77f5594e55223da1336faa8647733e8df02117d022df3db9e517b1f9b896390ef041e6264099ace7cc2075796dc21c15df13fb019fc650510288045651f2049343c9672ab00b1f62c368153807bae0659ca3b3fc0d4ff5bdc3d6e690aabd89b5a450197f61e0b497c99d6fa5da644d135d5fe649d2477963413ecc0ae81138383361b1cbddd97c63a1454f0865a91108cafd9cddce5a10b41f6a91371569707cd3337db99fdf423b3f949f1ab7b3419903644d3ba79a09050c3944',
        public_key_algorithm: 'RSA',
        rsa_public_key_modulus:
          'cb9d763ab99f19f633b7cbd5a352c4f1c8eb4f528f43790d22fc9bac659d9799e5eb3b5eb4ec9b983583277ad13e91a8abb2752ea311bc136a43f3bfa050f013e5fe97d78d616a5acc1207b09b6155e6667d9e9735c5f22aaae23f1de62edc63f90e0cdbbf5b7c633f2f108c439913da1041562ac8b2d1de818c9ffb14052ee0f8be3548ef96a295f2f7f9491dcda8dc9a600fc8d1582633b03ea29a8b55a3fef8393276a7da1c1992c9fda092b148835e7757d004dfdd4edd0ee6690ae4ad39b8e471be2929cd612a4789db4044fde2b9db3ab1d642b202bf784cbd746d4f9c5775db86d64cf46c904dd26c5b3e79306ae97a627567d91a47acfe1fe918f675',
        rsa_public_key_exponent: 65537,
        hash: 'BDB76853CD148BB7D81CBC119EEDD26B89F90613',
        key_usages: [
          'DIGITAL_SIGNATURE',
          'NON_REPUDIATION',
          'KEY_ENCIPHERMENT',
          'KEY_CERT_SIGN',
        ],
      },
    };
  },

  methods: {
    close(): void {
      this.$router.go(-1);
    },
    fetchData(hash: string): void {
      api
        .get(`/certificates/${hash}`)
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
  },
  created() {
    this.fetchData(this.hash);
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

.cert-hash-wrapper {
  margin-top: 30px;
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
}
</style>

