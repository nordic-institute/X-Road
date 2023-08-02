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
  <div class="form-label">
    <div class="label-text">
      {{ labelText }}
    </div>
    <div class="info-text">
      {{ helpText }}
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from "vue";

/**
 * Label and help icon that can be used with inputs
 */
export default defineComponent({
  props: {
    labelText: {
      type: String,
      required: true,
    },
    helpText: {
      type: String,
      required: false,
      default: '',
    },
  },
});
</script>

<style lang="scss" scoped>
.form-label {
  width: 405px;
  min-width: 405px;
  display: flex;
  flex-direction: column;
  align-items: baseline;
  color: #211e1e;
  text-align: left;

  .label-text {
    font-style: normal;
    font-weight: bold;
    font-size: 14px;
    line-height: 19px;
  }

  .info-text {
    font-style: normal;
    font-weight: normal;
    font-size: 14px;
    line-height: 19px;
  }
}
</style>
