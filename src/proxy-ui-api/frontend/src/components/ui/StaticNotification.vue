<template>
  <div v-if="staticNotification.length > 0" class="static-notification">
    <v-container class="py-6">
      <p v-for="notificationRow in staticNotification" :key="notificationRow">
        {{ notificationRow }}
      </p>
    </v-container>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';

export default Vue.extend({
  name: 'StaticNotification',
  computed: {
    ...mapGetters(['staticNotification']),
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';

.static-notification {
  background-color: $XRoad-Purple10;
}
</style>
