<template>
  <v-app>
    <v-main>
      <xrd-button text="road-x">Nested</xrd-button>
      <br>
      <xrd-close-button @click="console.log('close it')" />
      <br>
      <v-btn text="road-y" @click="console.log('click it')"/>
    </v-main>
  </v-app>
</template>

<script setup lang="ts">

import XrdButton from "./components/XrdButton.vue";
import XrdCloseButton from "./components/XrdCloseButton.vue";
</script>
