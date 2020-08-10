<template>
  <simpleDialog
    :dialog="dialog"
    title="ssTlsCertificate.generateTlsAndCertificateDialog.title"
    saveButtonText="action.confirm"
    :showClose="false"
    @save="save"
    @cancel="$emit('cancel')"
    :loading="loading"
  >
    <div slot="content">
      <p data-test="generate-tls-and-certificate-dialog-explanation-text">
        {{ $t('ssTlsCertificate.generateTlsAndCertificateDialog.explanation') }}
      </p>
      <p data-test="generate-tls-and-certificate-dialog-confirmation-text">
        {{
          $t('ssTlsCertificate.generateTlsAndCertificateDialog.confirmation')
        }}
      </p>
    </div>
  </simpleDialog>
</template>

<script lang="ts">
import Vue from 'vue';
import SimpleDialog from '@/components/ui/SimpleDialog.vue';
import * as api from '@/util/api';

export default Vue.extend({
  components: { SimpleDialog },
  data() {
    return {
      loading: false,
    };
  },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
  },
  methods: {
    save() {
      this.loading = true;
      api
        .post('/system/certificate', {})
        .then(() => {
          this.$store.dispatch(
            'showSuccess',
            'ssTlsCertificate.generateTlsAndCertificateDialog.success',
          );
          this.$emit('saved');
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
          this.$emit('cancel'); // still close the dialog
        })
        .finally(() => (this.loading = false));
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';
p {
  margin-top: 30px;
}
</style>
