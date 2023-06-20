<template>
  <v-app>
    <v-main>
      <br>
      <xrd-button text="road-x">
        Nested
      </xrd-button>
      <br>
      <xrd-close-button @click="console.log('close it')" />
      <br>
      <xrd-alert
        :show="true"
        message="something went wrong"
      />
      <br>
      <v-btn
        variant="text"
        @click="console.log('click it')"
      />
    </v-main>
  </v-app>
</template>

<script setup lang="ts">

import XrdAlert from "./components/XrdAlert.vue";
import XrdButton from "./components/XrdButton.vue";
import XrdCloseButton from "./components/XrdCloseButton.vue";
</script>
