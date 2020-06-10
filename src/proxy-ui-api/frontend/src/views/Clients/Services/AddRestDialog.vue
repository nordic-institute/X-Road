<template>
  <simpleDialog
    :dialog="dialog"
    :width="560"
    title="services.addRest"
    @save="save"
    @cancel="cancel"
    :disableSave="!isValid"
  >
    <div slot="content">
      <ValidationObserver ref="form" v-slot="{ validate, invalid }">
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
              :placeholder="$t('services.serviceCodePlaceholder')"
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
import * as api from '@/util/api';

export default Vue.extend({
  components: { SimpleDialog, ValidationProvider, ValidationObserver },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
    clientId: {
      type: String,
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
  computed: {
    isValid(): boolean {
      if (
        isValidRestURL(this.url) &&
        this.serviceCode.length > 0 &&
        this.serviceType !== ''
      ) {
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
      api
        .post(`/clients/${this.clientId}/service-descriptions`, {
          url: this.url,
          rest_service_code: this.serviceCode,
          type: this.serviceType,
        })
        .then((res) => {
          this.$store.dispatch(
            'showSuccess',
            this.serviceType === 'OPENAPI3'
              ? 'services.openApi3Added'
              : 'services.restAdded',
          );
          this.$emit('save', {
            serviceType: this.serviceType,
            url: this.url,
            serviceCode: this.serviceCode,
          });
          this.clear();
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },
    clear(): void {
      this.url = '';
      this.serviceCode = '';
      this.serviceType = '';
      (this.$refs.form as InstanceType<typeof ValidationObserver>).reset();
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';
</style>
