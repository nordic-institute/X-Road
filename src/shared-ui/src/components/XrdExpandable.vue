<!--
   The MIT License

   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->
<template>
  <div class="exp-wrapper">
    <div class="exp-header">
      <div>
        <v-btn variant="flat" icon size="small" class="no-hover rounded-circle" :disabled="disabled" :style="style" @click="toggle">
          <v-icon v-if="opened" color="primary" icon="mdi-chevron-down" />
          <v-icon v-else color="primary" icon="mdi-chevron-right" />
        </v-btn>
      </div>
      <div :class="{ 'text--disabled': disabled }">
        <slot name="link" :toggle="toggle" />
      </div>

      <v-spacer />
      <div class="exp-action-wrap">
        <slot name="action" />
      </div>
    </div>
    <div v-if="opened" :class="['exp-content-wrap', { 'v-input--disabled': disabled }]">
      <slot name="content" />
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

/**
 * Expandable can be clicked open and has slots for a link and ans action
 */

export default defineComponent({
  props: {
    isOpen: {
      type: Boolean,
      default: false,
    },
    color: {
      type: String,
      default: '',
    },
    disabled: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['open'],
  data() {
    return {
      opened: this.isOpen,
    };
  },
  computed: {
    style() {
      return this.color ? { color: this.color } : {};
    },
  },
  watch: {
    isOpen(newVal) {
      this.opened = newVal;
    },
  },
  methods: {
    toggle() {
      if (this.disabled) {
        return;
      }
      this.opened = !this.opened;
      this.$emit('open', this.opened);
    },
  },
});
</script>

<style lang="scss" scoped>
@use '../assets/colors';

.no-hover:hover:before,
.no-hover:focus:before {
  background-color: transparent;
}

.no-hover {
  margin-left: 3px;
  margin-right: 3px;
}

.exp-wrapper {
  border-radius: 4px;
  background-color: colors.$White100;
}

.exp-header {
  display: flex;
  align-items: center;
  height: 56px;
  padding: 10px;
}

.exp-content-wrap {
  padding-top: 16px;
  padding-bottom: 16px;
}
</style>
