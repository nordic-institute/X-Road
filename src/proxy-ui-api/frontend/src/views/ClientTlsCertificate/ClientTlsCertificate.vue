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
            >{{ $t('action.delete') }}</large-button
          >
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
import { mapGetters } from 'vuex';
import { Permissions } from '@/global';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import CertificateInfo from '@/components/certificate/CertificateInfo.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import CertificateHash from '@/components/certificate/CertificateHash.vue';
import * as api from '@/util/api';
import { CertificateDetails } from '@/openapi-types';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: {
    CertificateInfo,
    ConfirmDialog,
    SubViewTitle,
    LargeButton,
    CertificateHash,
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
      certificate: null as CertificateDetails | null,
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
  methods: {
    close(): void {
      this.$router.go(-1);
    },
    fetchData(clientId: string, hash: string): void {
      api
        .get<CertificateDetails>(
          `/clients/${clientId}/tls-certificates/${hash}`,
        )
        .then(
          (response) => {
            this.certificate = response.data;
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        );
    },
    deleteCertificate(): void {
      this.confirm = true;
    },
    doDeleteCertificate(): void {
      this.confirm = false;

      api
        .remove(
          `/clients/${encodePathParameter(
            this.id,
          )}/tls-certificates/${encodePathParameter(this.hash)}`,
        )
        .then(
          () => {
            this.$store.dispatch('showSuccess', 'cert.certDeleted');
          },
          (error) => {
            this.$store.dispatch('showError', error);
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

.cert-hash-wrapper {
  margin-top: 30px;
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
}
</style>
