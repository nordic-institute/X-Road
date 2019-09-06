<template>
  <v-dialog :value="dialog" :width="width" persistent>
    <v-card class="xrd-card">
      <v-card-title>
        <span class="headline">{{title}}</span>
        <v-spacer />
        <i @click="cancel()" id="dlg-close-x"></i>
      </v-card-title>
      <v-card-text class="content-wrapper">
        <slot name="content"></slot>
      </v-card-text>
      <v-card-actions class="xrd-card-actions">
        <v-spacer></v-spacer>
        <large-button outlined @click="cancel()">{{$t('action.cancel')}}</large-button>
        <large-button :disabled="disableSaveButton" @click="save()">{{$t(saveButtonLabel)}}</large-button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
/** Base component for simple dialogs */

import Vue from 'vue';
import LargeButton from '@/components/LargeButton.vue';

export default Vue.extend({
  components: {
    LargeButton,
  },
  props: {
    // Title of the dialog
    title: {
      type: String,
      required: true,
    },
    // Dialog visible / hidden
    dialog: {
      type: Boolean,
      required: true,
    },
    // Disable save button
    disableSave: {
      type: Boolean,
    },
    // Text of the save button
    saveButtonLabel: {
      type: String,
      default: 'action.add',
    },
    width: {
      type: Number,
      default: 550,
    },
  },

  computed: {
    disableSaveButton(): boolean {
      if (this.disableSave !== undefined && this.disableSave !== null) {
        return this.disableSave;
      }

      return false;
    },
  },

  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    save(): void {
      this.$emit('save');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../assets/colors';

.content-wrapper {
  margin-top: 18px;
}

.dlg-button-margin {
  margin-right: 14px;
}

#dlg-close-x {
  font-family: Roboto;
  font-size: 34px;
  font-weight: 300;
  letter-spacing: 0.5px;
  line-height: 21px;
  cursor: pointer;
  font-style: normal;
  font-size: 50px;
  color: $XRoad-Grey40;
}

#dlg-close-x:before {
  content: '\00d7';
}
</style>

