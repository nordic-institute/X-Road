<template>
  <div>
    <div class="header">
      <div>
        <v-btn fab icon small @click="clicked" class="no-hover">
          <v-icon v-if="isOpen" class="button-icon">mdi-chevron-down</v-icon>
          <v-icon v-else class="button-icon">mdi-chevron-right</v-icon>
        </v-btn>
      </div>
      <div class="header-link">
        <slot name="link"></slot>
      </div>

      <v-spacer />
      <div class="action-wrap">
        <slot name="action"></slot>
      </div>
    </div>
    <div v-if="isOpen" class="content-wrap">
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

<style lang="scss" scoped>
@import '../../assets/colors';

.no-hover:hover:before,
.no-hover:focus:before {
  background-color: transparent;
}

.no-hover {
  margin-left: 3px;
  margin-right: 3px;
}

.header {
  display: flex;
  align-items: center;
  height: 48px;
  border-radius: 4px;
  background-color: $XRoad-Grey10;
  box-shadow: 0 1px 1px 0 rgba(0, 0, 0, 0.2);
}

.action-wrap {
  padding-right: 8px;
}

.content-wrap {
  padding: 10px;
}
</style>
