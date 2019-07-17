<template>
  <simpleDialog
    :dialog="dialog"
    :title="$t('services.addRest')"
    @save="save"
    @cancel="cancel"
    :disableSave="!isValid"
  >
    <div slot="content">
      <div class="dlg-edit-row">
        <div class="dlg-row-title">{{$t('services.url')}}</div>
        <v-text-field
          v-model="url"
          single-line
          class="dlg-row-input"
          v-validate="'required|restUrl'"
          data-vv-as="url"
          name="url"
          type="text"
          :error-messages="errors.collect('url')"
        ></v-text-field>
      </div>

      <div class="dlg-edit-row">
        <div class="dlg-row-title">{{$t('services.serviceCode')}}</div>
        <v-text-field
          v-model="serviceCode"
          single-line
          class="dlg-row-input"
          v-validate="'required'"
          data-vv-as="code"
          name="code"
          type="text"
          :maxlength="255"
          :error-messages="errors.collect('code')"
        ></v-text-field>
      </div>
    </div>
  </simpleDialog>
</template>

<script lang="ts">
import Vue from 'vue';
import SimpleDialog from '@/components/SimpleDialog.vue';
import { isValidURL } from '@/util/helpers';

export default Vue.extend({
  components: { SimpleDialog },
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
      if (isValidURL(this.url) && this.serviceCode.length > 0) {
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
      this.$validator.reset();
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../assets/dialogs';
</style>


