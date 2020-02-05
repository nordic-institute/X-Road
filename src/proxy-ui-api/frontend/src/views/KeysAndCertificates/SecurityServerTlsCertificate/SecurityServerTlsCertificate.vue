<template>
  <div class="wrapper">
    <div class="details-view-tools">
      <large-button
        v-if="generateKeyVisible"
        class="button-spacing"
        outlined
      >{{$t('ssTlsCertificate.generateKey')}}</large-button>
      <large-button
        v-if="importCertificateVisible"
        class="button-spacing"
        outlined
      >{{$t('ssTlsCertificate.importCertificate')}}</large-button>
      <large-button
        v-if="exportCertificateVisible"
        class="button-spacing"
        outlined
      >{{$t('ssTlsCertificate.exportCertificate')}}</large-button>
    </div>

    <div class="content-title">{{$t('ssTlsCertificate.keyCertTitle')}}</div>
    <div class="horizontal-line-dark"></div>

    <div class="content-wrap">
      <div>
        <div class="key-wrap">
          <i class="icon-xrd_key icon"></i>
          {{$t('ssTlsCertificate.keyText')}}
        </div>
        <div class="cert-wrap">
          <i class="icon-xrd_certificate icon clickable" @click="certificateClick()"></i>
          <div
            class="clickable-link"
            v-if="certificate"
            @click="certificateClick()"
          >{{certificate.hash | colonize}}</div>
        </div>
      </div>

      <div>
        <SmallButton
          v-if="generateCsrVisible"
          class="table-button-fix"
          @click="generateCsr()"
        >{{$t('keys.generateCsr')}}</SmallButton>
      </div>
    </div>

    <div class="horizontal-line-light"></div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { Permissions, RouteName } from '@/global';
import { Key, CertificateDetails } from '@/types';
import * as api from '@/util/api';
import LargeButton from '@/components/ui/LargeButton.vue';
import SmallButton from '@/components/ui/SmallButton.vue';

export default Vue.extend({
  components: {
    LargeButton,
    SmallButton,
  },
  data() {
    return {
      certificate: undefined as CertificateDetails | undefined,
    };
  },
  computed: {
    generateKeyVisible(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.GENERATE_INTERNAL_SSL,
      );
    },
    importCertificateVisible(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.IMPORT_INTERNAL_SSL_CERT,
      );
    },
    exportCertificateVisible(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.EXPORT_INTERNAL_SSL_CERT,
      );
    },
    generateCsrVisible(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.EXPORT_INTERNAL_SSL_CERT,
      );
    },
  },
  methods: {
    certificateClick(): void {
      this.$router.push({
        name: RouteName.InternalTlsCertificate,
      });
    },
    generateCsr(): void {
      // TODO: will be implemented in another task
    },
    fetchData(): void {
      api
        .get(`/system/certificate`)
        .then((res) => {
          this.certificate = res.data;
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },
  },
  created() {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/detail-views';
.wrapper {
  margin-top: 20px;
  width: 100%;
}

.content-title {
  color: $XRoad-Black;
  font-size: 14px;
  font-weight: 500;
  margin-top: 40px;
  margin-bottom: 12px;
}

.button-spacing {
  margin-left: 20px;
}

.content-wrap {
  margin-top: 30px;
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
}

.key-wrap {
  display: flex;
}

.cert-wrap {
  display: flex;
  margin-top: 20px;
  padding-left: 40px;
}

.horizontal-line-dark {
  width: 100%;
  height: 1.5px;
  border-top: 1px solid $XRoad-Grey40;
  background-color: $XRoad-Grey10;
}

.horizontal-line-light {
  width: 100%;
  height: 1px;
  background-color: $XRoad-Grey10;
}

.icon {
  margin-left: 18px;
  margin-right: 20px;
}

.clickable {
  cursor: pointer;
}

.clickable-link {
  text-decoration: underline;
  cursor: pointer;
  height: 100%;
}
</style>

