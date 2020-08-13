<template>
  <ValidationObserver ref="form" v-slot="{ valid }">
    <simpleDialog
      :dialog="dialog"
      :width="560"
      title="services.addRest"
      @save="save"
      @cancel="cancel"
      :disableSave="!valid"
    >
      <div slot="content">
        <div class="dlg-edit-row">
          <div class="dlg-row-title">{{ $t('services.serviceType') }}</div>

          <ValidationProvider
            rules="required"
            name="serviceType"
            v-slot="{ errors }"
            class="validation-provider dlg-row-input"
          >
            <v-radio-group
              v-model="serviceType"
              name="serviceType"
              :error-messages="errors"
              row
            >
              <v-radio
                name="REST"
                :label="$t('services.restApiBasePath')"
                value="REST"
              ></v-radio>
              <v-radio
                name="OPENAPI3"
                :label="$t('services.OpenApi3Description')"
                value="OPENAPI3"
              ></v-radio>
            </v-radio-group>
          </ValidationProvider>
        </div>

        <div class="dlg-edit-row">
          <div class="dlg-row-title">{{ $t('services.url') }}</div>

          <ValidationProvider
            rules="required|restUrl"
            name="serviceUrl"
            v-slot="{ errors }"
            class="validation-provider dlg-row-input"
          >
            <v-text-field
              :placeholder="$t('services.urlPlaceholder')"
              v-model="url"
              single-line
              name="serviceUrl"
              :error-messages="errors"
            ></v-text-field>
          </ValidationProvider>
        </div>

        <div class="dlg-edit-row">
          <div class="dlg-row-title">{{ $t('services.serviceCode') }}</div>

          <ValidationProvider
            rules="required|xrdIdentifier"
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
              :placeholder="$t('services.serviceCodePlaceholder')"
              :maxlength="255"
              :error-messages="errors"
            ></v-text-field>
          </ValidationProvider>
        </div>
      </div>
    </simpleDialog>
  </ValidationObserver>
</template>

<script lang="ts">
import Vue from 'vue';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import SimpleDialog from '@/components/ui/SimpleDialog.vue';

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
      serviceType: '',
      url: '',
      serviceCode: '',
    };
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
      this.clear();
    },
    save(): void {
      this.$emit('save', this.serviceType, this.url, this.serviceCode);
      this.clear();
    },
    clear(): void {
      this.url = '';
      this.serviceCode = '';
      this.serviceType = '';
      requestAnimationFrame(() => {
        (this.$refs.form as InstanceType<typeof ValidationObserver>).reset();
      });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';
</style>
