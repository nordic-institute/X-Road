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
  <div class="drop-menu">
    <v-menu location="bottom right">
      <template #activator="{ props }">
        <v-btn
          variant="text"
          class="no-uppercase"
          data-test="username-button"
          v-bind="props"
        >
          {{ username }}
          <v-icon icon="mdi-chevron-down" />
        </v-btn>
      </template>

      <v-list>
        <v-list-item
          id="logout-list-tile"
          data-test="logout-list-tile"
          @click="logout"
        >
          <v-list-item-title id="logout-title">{{
            $t('login.logOut')
          }}</v-list-item-title>
        </v-list-item>
      </v-list>
    </v-menu>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { RouteName } from '@/global';

export default defineComponent({
  computed: {
    ...mapState(useUser, ['username']),
  },
  methods: {
    ...mapActions(useUser, ['logoutUser']),
    logout(): void {
      this.logoutUser();
      this.$router.replace({ name: RouteName.Login });
    },
  },
});
</script>

<style lang="scss" scoped>
.drop-menu {
  margin-left: auto;
  margin-right: 70px;
  display: flex;
  align-items: center;
}

.no-uppercase {
  text-transform: none;
  font-weight: 600;
}
</style>
