<template>
  <div class="row-wrap">
    <StatusIcon :status="statusIconType" />
    <div>{{ $t(status) }}</div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { CertificateStatus } from '@/global';
import StatusIcon from '@/components/ui/StatusIcon.vue';

export default Vue.extend({
  components: {
    StatusIcon,
  },
  props: {
    certificate: {
      type: Object,
      required: true,
    },
  },
  data() {
    return {};
  },
  computed: {
    status() {
      switch (this.certificate.status) {
        case CertificateStatus.SAVED:
          return 'keys.certStatus.saved';
          break;
        case CertificateStatus.REGISTRATION_IN_PROGRESS:
          return 'keys.certStatus.registration';
          break;
        case CertificateStatus.REGISTERED:
          return 'keys.certStatus.registered';
          break;
        case CertificateStatus.DELETION_IN_PROGRESS:
          return 'keys.certStatus.deletion';
          break;
        case CertificateStatus.GLOBAL_ERROR:
          return 'keys.certStatus.globalError';
          break;
        default:
          return '-';
          break;
      }
    },
    statusIconType() {
      switch (this.certificate.status) {
        case CertificateStatus.SAVED:
          return 'orange-ring';
          break;
        case CertificateStatus.REGISTRATION_IN_PROGRESS:
          return 'orange';
          break;
        case CertificateStatus.REGISTERED:
          return 'green';
          break;
        case CertificateStatus.DELETION_IN_PROGRESS:
          return 'red';
          break;
        case CertificateStatus.GLOBAL_ERROR:
          return 'red-ring';
          break;
        default:
          return 'red-ring';
          break;
      }
    },
  },
  methods: {},
});
</script>

<style lang="scss" scoped>
.row-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
}
</style>
