<template>
  <simpleDialog
    :dialog="dialog"
    title="localGroup.addLocalGroup"
    @save="save"
    @cancel="cancel"
    :disableSave="!formReady"
  >
    <div slot="content">
      <div class="dlg-edit-row">
        <div class="dlg-row-title">{{ $t('localGroup.code') }}</div>
        <v-text-field
          v-model="code"
          single-line
          class="dlg-row-input"
        ></v-text-field>
      </div>

      <div class="dlg-edit-row">
        <div class="dlg-row-title">{{ $t('localGroup.description') }}</div>
        <v-text-field
          v-model="description"
          hint
          single-line
          class="dlg-row-input"
        ></v-text-field>
      </div>
    </div>
  </simpleDialog>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import SimpleDialog from '@/components/ui/SimpleDialog.vue';

export default Vue.extend({
  components: { SimpleDialog },
  props: {
    id: {
      type: String,
      required: true,
    },
    dialog: {
      type: Boolean,
      required: true,
    },
  },

  data() {
    return {
      code: '',
      description: '',
    };
  },
  computed: {
    formReady(): boolean {
      if (this.code && this.code.length > 0 && this.description.length > 0) {
        return true;
      }
      return false;
    },
  },

  methods: {
    cancel(): void {
      this.clearForm();
      this.$emit('cancel');
    },
    save(): void {
      api
        .post(`/clients/${this.id}/local-groups`, {
          code: this.code,
          description: this.description,
        })
        .then(() => {
          this.$store.dispatch('showSuccess', 'localGroup.localGroupAdded');
          this.$emit('groupAdded');
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        })
        .finally(() => this.clearForm());
    },

    clearForm(): void {
      this.code = '';
      this.description = '';
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';
</style>
