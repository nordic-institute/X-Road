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
  <v-sheet color="surface-container" class="xrd-expansion-panel xrd-rounded-12 border" :class="{ 'pb-4': opened, 'pb-0': !opened }">
    <div data-test="header" class="xrd-expansion-panel-header d-flex flex-row align-center pt-2 pr-2 pb-2 pl-4" :class="headerClasses">
      <div>
        <v-btn class="xrd opacity-100" variant="plain" :icon="icon" :disabled="disabled" :ripple="false" color="primary" @click="toggle" />
      </div>
      <div class="align-content-center" :class="{ 'text--disabled': disabled }">
        <slot name="link" :toggle="toggle" :opened="opened" />
      </div>

      <v-spacer />
      <div class="xrd-expansion-panel-header-actions">
        <slot name="action" :opened="opened" />
      </div>
    </div>
    <v-expand-transition>
      <div v-if="opened" data-test="content" class="xrd-expansion-panel-content" :class="{ 'v-input--disabled': disabled }">
        <slot name="content" />
      </div>
    </v-expand-transition>
  </v-sheet>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';

/**
 * Expandable can be clicked open and has slots for a link and ans action
 */

export default defineComponent({
  props: {
    isOpen: {
      type: Boolean,
      default: false,
    },
    disabled: {
      type: Boolean,
      default: false,
    },
    headerClasses: {
      type: Array as PropType<string[]>,
      default: () => [],
    },
  },
  emits: ['open'],
  data() {
    return {
      opened: this.isOpen,
    };
  },
  computed: {
    icon() {
      return this.opened ? 'keyboard_arrow_down' : 'chevron_right';
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
