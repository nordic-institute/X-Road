<template>
  <simpleDialog
    :dialog="dialog"
    title="login.logIn"
    @save="save"
    @cancel="cancel"
    saveButtonText="login.logIn"
  >
    <div slot="content">
      <div class="dlg-edit-row">
        <div class="dlg-row-title">Token PIN</div>
        <ValidationProvider
          rules="required"
          ref="tokenPin"
          name="tokenPin"
          v-slot="{ errors }"
          class="validation-provider"
        >
          <v-text-field
            v-model="url"
            single-line
            class="dlg-row-input"
            name="tokenPin"
            :error-messages="errors"
          ></v-text-field>
        </ValidationProvider>
      </div>
    </div>
  </simpleDialog>
</template>


<script lang="ts">
import Vue from 'vue';
import axios from 'axios';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import SimpleDialog from '@/components/SimpleDialog.vue';
import { isValidWsdlURL } from '@/util/helpers';

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
      this.$emit('save');
      this.clear();
    },
    clear(): void {
      this.url = '';
      (this.$refs.tokenPin as InstanceType<typeof ValidationProvider>).reset();
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../assets/dialogs';
</style>

