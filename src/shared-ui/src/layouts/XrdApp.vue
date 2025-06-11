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
  <v-app theme="light">
    <slot name="system-bar"></slot>

    <v-navigation-drawer width="96" permanent>
      <slot name="main-navigation">
        Main nav
      </slot>
    </v-navigation-drawer>

    <v-app-bar color="grey" height="48" flat>
      <slot name="title-bar">
        Title bar
      </slot>
    </v-app-bar>
    <v-app-bar
      color="grey-lighten-2"
      height="48"
      flat
    >
      <slot name="sub-tabs">
        Sub tabs
      </slot>
    </v-app-bar>


    <v-main>
      <v-container>
        <h1>Main Content</h1>
      </v-container>
    </v-main>

  </v-app>
</template>

<script lang="ts" setup>
</script>

<style lang="scss" scoped>
</style>
