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
  <div class="help-wrap" @click="helpClick()">
    <v-hover v-slot="{ isHovering }">
      <xrd-icon-base
        :color="isHovering ? '#663cdc' : '#575169'"
        class="help-icon"
      >
        <xrd-icon-tooltip />
      </xrd-icon-base>
    </v-hover>

    <xrd-help-dialog
      :dialog="showHelp"
      :title="helpTitle"
      :text="helpText"
      @cancel="closeHelp"
    >
      <v-img :src="helpImage"></v-img>
    </xrd-help-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { XrdHelpDialog, XrdIconTooltip } from '@niis/shared-ui';

export default defineComponent({
  components: {
    XrdIconTooltip,
    XrdHelpDialog,
  },
  props: {
    helpImage: {
      type: String,
      required: true,
    },
    helpTitle: {
      type: String,
      required: true,
    },
    helpText: {
      type: String,
      required: true,
    },
  },
  data: () => ({
    showHelp: false,
  }),
  methods: {
    helpClick(): void {
      this.showHelp = true;
    },
    closeHelp(): void {
      this.showHelp = false;
    },
  },
});
</script>

<style lang="scss" scoped>
.help-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.help-icon {
  margin-left: 20px;
  margin-bottom: 4px;
  font-size: 22px;
  cursor: pointer;
}
</style>
