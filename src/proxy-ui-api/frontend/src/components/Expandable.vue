<template>
  <div>
    <div class="header">
      <div>
        <v-btn flat fab small @click="clicked" class="no-hover">
          <div>
            <v-icon v-if="isOpen" class="button-icon">expand_more</v-icon>
            <v-icon v-else class="button-icon">chevron_right</v-icon>
          </div>
        </v-btn>
      </div>
      <div class="header-link">
        <slot name="link"></slot>
      </div>

      <v-spacer />
      <div>
        <slot name="action">
          <v-switch class="switch"></v-switch>
        </slot>
      </div>
    </div>
    <div v-if="isOpen">
      <slot name="content"></slot>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  name: 'expandable',
  components: {},
  props: {
    isOpen: {
      type: Boolean,
      required: true,
    },
  },
  methods: {
    clicked(): void {
      if (this.isOpen) {
        this.$emit('close');
      } else {
        this.$emit('open');
      }
    },
  },
});
</script>

<style lang="scss" scoped >
@import '../assets/colors';

.no-hover:hover:before,
.no-hover:focus:before {
  background-color: transparent;
}

.header {
  display: flex;
  align-items: center;
  height: 48px;
  width: 850px;
  border-radius: 4px;
  background-color: $XRoad-Grey10;
  box-shadow: 0 1px 1px 0 rgba(0, 0, 0, 0.2);
}

.switch {
  width: 60px;
}
</style>


