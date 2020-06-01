<template>
  <simpleDialog
    :dialog="dialog"
    title="services.addWsdl"
    @save="save"
    @cancel="cancel"
    :disableSave="!isValid"
  >
    <div slot="content">
      <div class="dlg-edit-row">
        <div class="dlg-row-title">{{ $t('services.url') }}</div>
        <ValidationProvider
          rules="required|wsdlUrl"
          ref="serviceUrl"
          name="serviceUrl"
          v-slot="{ errors }"
          class="validation-provider"
        >
          <v-text-field
            v-model="url"
            single-line
            class="dlg-row-input"
            name="serviceUrl"
            :error-messages="errors"
          ></v-text-field>
        </ValidationProvider>
      </div>
    </div>
  </simpleDialog>
</template>

<script lang="ts">
import Vue from 'vue';
import { ValidationProvider } from 'vee-validate';
import SimpleDialog from '@/components/ui/SimpleDialog.vue';
import { isValidWsdlURL } from '@/util/helpers';

export default Vue.extend({
  components: { SimpleDialog, ValidationProvider },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
  },

  data() {
    return {
      url: '',
    };
  },

  computed: {
    isValid(): boolean {
      return isValidWsdlURL(this.url);
    },
  },

  methods: {
    cancel(): void {
      this.$emit('cancel');
      this.clear();
    },
    save(): void {
      this.$emit('save', this.url);
      this.clear();
    },
    clear(): void {
      this.url = '';
      (this.$refs.serviceUrl as InstanceType<
        typeof ValidationProvider
      >).reset();
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';
</style>
