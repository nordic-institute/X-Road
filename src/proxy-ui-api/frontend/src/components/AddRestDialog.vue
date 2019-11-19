<template>
  <simpleDialog
    :dialog="dialog"
    title="services.addRest"
    @save="save"
    @cancel="cancel"
    :disableSave="!isValid"
  >
    <div slot="content">
      <ValidationObserver ref="form" v-slot="{ validate, invalid }">
        <div class="dlg-edit-row">
          <div class="dlg-row-title">{{$t('services.url')}}</div>

          <ValidationProvider
            rules="required|restUrl"
            name="serviceUrl"
            v-slot="{ errors }"
            class="validation-provider dlg-row-input"
          >
            <v-text-field v-model="url" single-line name="serviceUrl" :error-messages="errors"></v-text-field>
          </ValidationProvider>
        </div>

        <div class="dlg-edit-row">
          <div class="dlg-row-title">{{$t('services.serviceCode')}}</div>

          <ValidationProvider
            rules="required"
            name="serviceCode"
            v-slot="{ errors }"
            class="validation-provider"
          >
            <v-text-field
              v-model="serviceCode"
              single-line
              class="dlg-row-input"
              name="serviceCode"
              type="text"
              :maxlength="255"
              :error-messages="errors"
            ></v-text-field>
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
      if (isValidRestURL(this.url) && this.serviceCode.length > 0) {
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
      this.$emit('save', { url: this.url, serviceCode: this.serviceCode });
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
@import '../assets/dialogs';
</style>

