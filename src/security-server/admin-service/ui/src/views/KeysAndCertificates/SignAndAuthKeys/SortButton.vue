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
  <div class="ml-1">
    <xrd-icon-base v-if="arrowState" class="arrow-down" :color="iconColor">
      <xrd-icon-sorting-arrow />
    </xrd-icon-base>
    <xrd-icon-base v-else class="arrow-up" :color="iconColor">
      <xrd-icon-sorting-arrow />
    </xrd-icon-base>
  </div>
</template>

<script lang="ts">
/**
 * Component for sorting icon (up and down)
 */
import { defineComponent } from 'vue';
import { Colors } from '@/global';
import { XrdIconSortingArrow } from '@niis/shared-ui';

export default defineComponent({
  components: { XrdIconSortingArrow },
  props: {
    arrowState: {
      type: Boolean,
      required: true,
    },
    active: {
      type: Boolean,
      required: true,
    },
    selected: {
      type: Boolean,
      required: true,
    },
  },
  computed: {
    iconColor(): string {
      // Determine the icon color
      if (this.selected) {
        return Colors.Black100;
      }
      if (this.active) {
        return Colors.Black30;
      }
      return 'transparent'; // Hidden
    },
  },
});
</script>

<style lang="scss" scoped>
.arrow-up {
  transform: rotate(-180deg) translateY(-4px);
  transition-duration: 0s;
}
.arrow-down {
  transition-duration: 0s;
  transform: translateY(-4px);
}
</style>
