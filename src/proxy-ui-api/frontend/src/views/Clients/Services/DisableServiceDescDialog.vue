<template>
  <simpleDialog
    :dialog="dialog"
    title="services.disableTitle"
    @save="save"
    @cancel="cancel"
    saveButtonText="action.ok"
  >
    <div slot="content">
      <div class="dlg-edit-row">
        <div class="dlg-row-title">{{ $t('services.disableNotice') }}</div>
        <v-text-field
          v-model="notice"
          single-line
          class="dlg-row-input"
        ></v-text-field>
      </div>
    </div>
  </simpleDialog>
</template>

<script lang="ts">
// Dialog to confirm service description disabling
import Vue from 'vue';
import SimpleDialog from '@/components/ui/SimpleDialog.vue';

export default Vue.extend({
  components: { SimpleDialog },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
    subject: {
      type: Object,
    },
    subjectIndex: {
      type: Number,
    },
  },

  data() {
    return {
      notice: '',
    };
  },

  methods: {
    cancel(): void {
      this.$emit('cancel', this.subject, this.subjectIndex);
      this.clear();
    },
    save(): void {
      this.$emit('save', this.subject, this.subjectIndex, this.notice);
      this.clear();
    },
    clear(): void {
      this.notice = '';
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';
</style>
