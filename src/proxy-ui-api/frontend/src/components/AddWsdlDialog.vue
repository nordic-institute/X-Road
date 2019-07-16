<template>
  <simpleDialog
    :dialog="dialog"
    :title="$t('services.addWsdl')"
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
          v-validate="'required|url'"
          data-vv-as="url"
          name="url"
          type="text"
          :error-messages="errors.collect('url')"
        ></v-text-field>
      </div>
    </div>
  </simpleDialog>
</template>

<script lang="ts">
import Vue from 'vue';
import axios from 'axios';
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
    };
  },

  computed: {
    isValid(): boolean {
      return isValidURL(this.url);
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
      this.$validator.reset();
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../assets/dialogs';
</style>

