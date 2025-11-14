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
  <v-stepper
    v-model="currentStep"
    class="wizard xrd-rounded-12"
    alt-labels
    flat
  >
    <!-- Headers -->
    <v-stepper-header
      v-if="$slots['header-items']"
      class="elevation-0"
    >
      <slot name="header-items" />
    </v-stepper-header>

    <v-stepper-window class="ma-0">
      <slot
        :next-step="nextStep"
        :previous-step="previousStep"
      />
    </v-stepper-window>
  </v-stepper>
</template>

<script lang="ts" setup>
const currentStep = defineModel({ type: Number, default: 0 });

function nextStep() {
  currentStep.value++;
}

function previousStep() {
  currentStep.value++;
}
</script>

<style lang="scss" scoped>
.wizard {
  background: rgb(var(--v-theme-surface-container-lowest));
  padding: 0;

  :deep(.v-stepper-header) {
    .v-divider {
      border: 1px solid rgb(var(--v-theme-border-strong));
      opacity: 0.2;
    }

    .v-stepper-item {
      font-weight: 600;
      opacity: 0.6;
      font-size: 14px;

      &.v-stepper-item--selected {
        opacity: 1;
      }

      .v-stepper-item__avatar.v-avatar {
        font-size: 12px;
        background: rgb(var(--v-theme-primary));
        color: rgb(var(--v-theme-on-primary));
      }
    }
  }
}
</style>
