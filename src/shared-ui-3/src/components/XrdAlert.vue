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
  <div class="px-3">
    <!-- Error -->
    <v-container v-if="show" fluid class="alerts-container px-3">
      <v-alert
        data-test="contextual-modal-alert"
        border="start"
        border-color="error"
        variant="outlined"
        class="alert mb-2"
        color="red"
        icon="icon-Error-notification"
      >
        <div class="row-wrapper-top scrollable identifier-wrap">
          <div class="row-wrapper">
            <!-- Show localised text by id -->
            <div>{{ message }}</div>
          </div>
        </div>
      </v-alert>
    </v-container>
  </div>
</template>

<script lang="ts">
import { defineComponent } from "vue";

export default defineComponent({
  props: {
    // Alert visible / hidden
    show: {
      type: Boolean,
      required: true,
    },
    message: {
      type: String,
      required: false,
      default: '',
    }
  }
});

</script>

<style lang="scss" scoped>
@import '../assets/colors';

.alerts-container {
  padding: 0;

  & > * {
    margin-top: 0;
    margin-bottom: 4px;
    border-radius: 0;
  }

  & > :first-child {
    margin-top: 4px;
  }
}

.alert {
  margin-top: 16px;
  border: solid 1px #dedce4;
  border-radius: 4px;
}

.row-wrapper-top {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
}

.row-wrapper {
  display: flex;
  flex-direction: column;
  overflow: auto;
  overflow-wrap: break-word;
  justify-content: center;
  margin-right: 30px;
}

.scrollable {
  overflow-y: auto;
  max-height: 300px;
}
</style>
