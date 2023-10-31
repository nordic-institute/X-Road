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
  <v-img
    :src="xroad7Logo"
    height="35"
    width="132"
    max-height="35"
    max-width="132"
    class="xrd-logo"
    @click="home()"
  ></v-img>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import xroad7Logo from '@/assets/xroad7_logo.svg';

export default defineComponent({
  computed: {
    ...mapState(useUser, ['firstAllowedTab']),
    xroad7Logo(): string {
      return xroad7Logo;
    },
  },
  methods: {
    home(): void {
      this.$router.replace(this.firstAllowedTab.to).catch((err) => {
        // Ignore the error regarding navigating to the same path
        if (err.name === 'NavigationDuplicated') {
          // eslint-disable-next-line no-console
          console.info('Duplicate navigation');
        } else {
          // Throw for any other errors
          throw err;
        }
      });
    },
  },
});
</script>

<style lang="scss" scoped>
.xrd-logo {
  margin-top: auto;
  margin-bottom: auto;
  cursor: pointer;
  @media only screen and (max-width: 920px) {
    display: none;
  }
}
</style>
