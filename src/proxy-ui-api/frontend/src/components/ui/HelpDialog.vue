<template>
  <v-dialog :value="dialog" :width="width" persistent>
    <v-card class="xrd-card">
      <v-card-title>
        <span class="headline">{{ $t(title) }}</span>
      </v-card-title>
      <v-card-text class="content-wrapper">
        <v-img :src="require('./../../assets/' + imageSrc)"></v-img>
        <div class="title-wrap">
          <h2>{{ $t(title) }}</h2>
        </div>
        <div class="text-wrap">{{ $t(text) }}</div>
      </v-card-text>
      <v-card-actions class="xrd-card-actions">
        <v-spacer></v-spacer>
        <large-button @click="cancel()">{{ $t('keys.gotIt') }}</large-button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
/** Component for help dialogs */

import Vue from 'vue';
import LargeButton from '@/components/ui/LargeButton.vue';

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
    width: {
      type: Number,
      default: 850,
    },
    // Source for help image
    imageSrc: {
      type: String,
    },
    // Help text
    text: {
      type: String,
    },
  },

  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/dialogs';

.content-wrapper {
  margin-top: 20px;
}

.title-wrap {
  margin-bottom: 10px;
  width: 100%;
  text-align: center;
}

.text-wrap {
  margin: 10px;
}
</style>
