<template>
  <simpleDialog :dialog="dialog" title="keys.keyLabelTitle" @save="save" @cancel="cancel">
    <div slot="content">
      <div class="info-text">{{$t('keys.keyLabelInfo')}}</div>

      <div class="dlg-edit-row">
        <div class="dlg-row-title">{{$t('keys.keyLabelInput')}}</div>
        <v-text-field v-model="keyLabel" single-line class="dlg-row-input" name="keyLabel"></v-text-field>
      </div>
    </div>
  </simpleDialog>
</template>


<script lang="ts">
import Vue from 'vue';
import SimpleDialog from '@/components/ui/SimpleDialog.vue';

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
      keyLabel: '',
    };
  },

  methods: {
    cancel(): void {
      this.$emit('cancel');
      this.clear();
    },
    save(): void {
      this.$emit('save', this.keyLabel);
      this.clear();
    },
    clear(): void {
      this.keyLabel = '';
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';

.info-text {
  margin-top: 30px;
}
</style>

