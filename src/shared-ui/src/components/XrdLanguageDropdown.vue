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
  <div v-if="languages.length > 1" class="language-changer">
    <v-menu location="bottom">
      <template #activator="{ props }">
        <v-btn class="no-uppercase" data-test="language-button" v-bind="props" variant="text">
          <strong>{{ currentLanguage }}</strong>
          <v-icon icon="mdi-chevron-down" />
        </v-btn>
      </template>

      <v-list>
        <v-list-item
          v-for="language in languages"
          :key="language.code"
          :active="language.code === currentLanguage"
          data-test="language-list-tile"
          @click="changeLanguage(language.code)"
        >
          <v-tooltip activator="parent" location="right">
            <span class="text-capitalize">{{ language.display }}</span>
          </v-tooltip>
          {{ language.code }}
        </v-list-item>
      </v-list>
    </v-menu>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import { useLanguageHelper } from '../plugins/i18n';

const { selectLanguage, supportedLanguages, currentLanguage } = useLanguageHelper();

const displayName = computed(() => {
  return new Intl.DisplayNames([currentLanguage.value], {
    type: 'language',
  });
});
const languages = computed(() => {
  return supportedLanguages.map((lang) => ({
    code: lang,
    display: displayName.value.of(lang),
  }));
});

function changeLanguage(language: string) {
  if (language !== currentLanguage.value) {
    selectLanguage(language);
  }
}
</script>

<style lang="scss" scoped>
.language-changer {
  margin-left: auto;
  display: flex;
  align-items: center;

  .no-uppercase {
    text-transform: none;
    font-weight: 600;
  }
}
</style>
