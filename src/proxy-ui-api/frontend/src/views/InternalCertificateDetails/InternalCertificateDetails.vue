<template>
  <div class="wrapper xrd-view-common">
    <div class="new-content">
      <SubViewTitle :title="$t('cert.certificate')" @close="close" />
      <template v-if="certificate">
        <div class="cert-hash-wrapper">
          <certificateHash :hash="certificate.hash" />
        </div>
        <certificateInfo :certificate="certificate" />
      </template>
      <SubViewFooter @close="close" />
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import { UsageTypes, Permissions, PossibleActions } from '@/global';
import { CertificateDetails } from '@/openapi-types';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import SubViewFooter from '@/components/ui/SubViewFooter.vue';
import CertificateInfo from '@/components/certificate/CertificateInfo.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import CertificateHash from '@/components/certificate/CertificateHash.vue';

export default Vue.extend({
  components: {
    CertificateInfo,
    SubViewTitle,
    LargeButton,
    CertificateHash,
    SubViewFooter,
  },
  props: {},
  data() {
    return {
      certificate: undefined as CertificateDetails | undefined,
    };
  },
  methods: {
    close(): void {
      this.$router.go(-1);
    },
    fetchData(): void {
      api
        .get(`/system/certificate`)
        .then((res) => {
          this.certificate = res.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },
  },
  created() {
    this.fetchData();
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
