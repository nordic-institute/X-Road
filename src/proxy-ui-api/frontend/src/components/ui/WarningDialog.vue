<template>
  <v-dialog :value="dialog" persistent :max-width="maxWidth">
    <v-card>
      <v-card-title class="headline">{{$t('warning')}}</v-card-title>
      <v-card-text>
        <div v-for="warning in warnings" :key="warning.code">
          <!-- create the localisation key from warning code -->
          <div class="dlg-warning-header">{{$t("services."+warning.code)}}</div>
          <span v-for="meta in warning.metadata" :key="meta">{{meta}},&#32;</span>
        </div>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="primary" text outlined @click="cancel()">{{$t(cancelButtonText)}}</v-btn>
        <v-btn color="primary" text outlined @click="accept()">{{$t(acceptButtonText)}}</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
// A dialog for backend warnings
import Vue from 'vue';

export default Vue.extend({
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
    warnings: {
      type: Array,
    },
    cancelButtonText: {
      type: String,
      default: 'action.cancel',
    },
    acceptButtonText: {
      type: String,
      default: 'action.continue',
    },
    maxWidth: {
      type: String,
      default: '90%',
    },
  },

  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    accept(): void {
      this.$emit('accept');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/dialogs';
</style>

