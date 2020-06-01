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

        case CertificateStatus.REGISTRATION_IN_PROGRESS:
          return 'keys.certStatus.registration';

        case CertificateStatus.REGISTERED:
          return 'keys.certStatus.registered';

        case CertificateStatus.DELETION_IN_PROGRESS:
          return 'keys.certStatus.deletion';

        case CertificateStatus.GLOBAL_ERROR:
          return 'keys.certStatus.globalError';

        default:
          return '-';

      }
    },
    statusIconType() {
      switch (this.certificate.status) {
        case CertificateStatus.SAVED:
          return 'orange-ring';

        case CertificateStatus.REGISTRATION_IN_PROGRESS:
          return 'orange';

        case CertificateStatus.REGISTERED:
          return 'green';

        case CertificateStatus.DELETION_IN_PROGRESS:
          return 'red';

        case CertificateStatus.GLOBAL_ERROR:
          return 'red-ring';
        default:
          return 'red-ring';
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
