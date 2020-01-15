<template>
  <simpleDialog
    :dialog="dialog"
    title="keys.registrationRequest"
    @save="save"
    @cancel="cancel"
    :disableSave="!isValid"
  >
    <div slot="content">
      <ValidationObserver ref="form" v-slot="{ validate, invalid }">
        <div class="dlg-edit-row">
          <div class="dlg-row-title">{{$t('keys.certRegistrationInfo')}}</div>

          <ValidationProvider
            rules="required"
            name="serviceUrl"
            v-slot="{ errors }"
            class="validation-provider dlg-row-input"
          >
            <v-text-field v-model="url" single-line name="serviceUrl" :error-messages="errors"></v-text-field>
          </ValidationProvider>
        </div>
      </ValidationObserver>
    </div>
  </simpleDialog>
</template>

<script lang="ts">
import Vue from 'vue';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import SimpleDialog from '@/components/ui/SimpleDialog.vue';
import { isValidRestURL } from '@/util/helpers';

export default Vue.extend({
  components: { SimpleDialog, ValidationProvider, ValidationObserver },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
  },
  data() {
    return {
      url: '',
      serviceCode: '',
    };
  },
  computed: {
    isValid(): boolean {
      if (this.url && this.url.length > 4) {
        return true;
      }

      return false;
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
      this.serviceCode = '';
      (this.$refs.form as InstanceType<typeof ValidationObserver>).reset();
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';
</style>

