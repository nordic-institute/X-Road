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
  <XrdView data-test="system-parameters-view" title="tab.main.settings">
    <template #tabs>
      <SettingsViewTabs />
    </template>
    <XrdSubView class="settings-subview">
      <XrdCard v-if="hasPermission(Permissions.CHANGE_CONFIGURATION_PROPERTY)" title="configurableProperties.title" class="settings-block">
        <template #title-actions>
          <XrdBtn
            v-if="hasAnyOpenScope"
            data-test="configurable-properties-collapse-all"
            variant="text"
            text="configurableProperties.collapseAll"
            prepend-icon="keyboard_arrow_up"
            color="tertiary"
            @click="collapseAllScopes"
          />
        </template>
        <v-alert
          v-if="modifiedScopes.size > 0"
          class="ma-4"
          type="warning"
          variant="outlined"
          border="start"
          density="compact"
          data-test="configurable-properties-restart-warning"
        >
          {{ $t('configurableProperties.restartWarning', { scopes: [...modifiedScopes].join(', ') }) }}
        </v-alert>

        <div class="px-4">
          <XrdRoundedSearchField
            v-model="propertySearch"
            data-test="configurable-properties-search"
            autofocus
            :label="$t('configurableProperties.search')"
          />
        </div>

        <XrdEmptyPlaceholder
          class="px-4"
          :data="filteredScopeKeys"
          :filtered="propertySearch.length > 0"
          :loading="loadingProperties"
          :no-items-text="$t('noData.noConfigurableProperties')"
        />

        <div v-if="!loadingProperties && filteredScopeKeys.length > 0" class="mt-3 mx-4 mb-4" data-test="configurable-properties-panels">
          <XrdScopePropertiesExpandable
            v-for="(scope, index) in filteredScopeKeys"
            :key="scope"
            :class="{ 'mb-4': index < filteredScopeKeys.length - 1 }"
            :scope="scope"
            :properties="filteredPropertiesByScope[scope]"
            :modified-properties="modifiedProperties"
            :is-open="openScopes[scope] ?? false"
            :get-property-description="getPropertyDescription"
            @open="openScopes[scope] = $event"
            @edit-property="editingProperty = $event"
          />
        </div>
      </XrdCard>
    </XrdSubView>
    <XrdEditConfigurablePropertyDialog
      v-if="editingProperty"
      :property="editingProperty"
      :configurable-properties-handler="configurablePropertiesHandler"
      @cancel="editingProperty = undefined"
      @saved="onPropertySaved"
    />
  </XrdView>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  useNotifications,
  XrdBtn,
  XrdCard,
  XrdEditConfigurablePropertyDialog,
  XrdEmptyPlaceholder,
  XrdScopePropertiesExpandable,
  XrdSubView,
  XrdView,
} from '@niis/shared-ui';
import type { ConfigurablePropertiesHandler, ConfigurablePropertyDto } from '@niis/shared-ui';
import { Permissions } from '@/global';
import { useSystem } from '@/store/modules/system';
import { useUser } from '@/store/modules/user';
import SettingsViewTabs from '@/views/Settings/SettingsViewTabs.vue';

const { addError } = useNotifications();
const { t, te } = useI18n();
const { fetchConfigurableProperties, updateConfigurableProperty } = useSystem();
const configurablePropertiesHandler: ConfigurablePropertiesHandler = { updateConfigurableProperty };
const { hasPermission } = useUser();

const configurableProperties = ref<ConfigurablePropertyDto[]>([]);
const loadingProperties = ref(false);
const editingProperty = ref<ConfigurablePropertyDto | undefined>(undefined);
const modifiedScopes = ref<Set<string>>(new Set());
const modifiedProperties = ref<Set<string>>(new Set());
const openScopes = ref<Record<string, boolean>>({});
const propertySearch = ref('');

const propertiesByScope = computed<Record<string, ConfigurablePropertyDto[]>>(() => {
  const result: Record<string, ConfigurablePropertyDto[]> = {};
  for (const prop of configurableProperties.value) {
    const scope = prop.scope || 'common';
    if (!result[scope]) result[scope] = [];
    result[scope].push(prop);
  }
  for (const scope of Object.keys(result)) {
    result[scope].sort((a, b) => (a.property_name ?? '').localeCompare(b.property_name ?? ''));
  }
  return result;
});

const filteredPropertiesByScope = computed<Record<string, ConfigurablePropertyDto[]>>(() => {
  const term = propertySearch.value.trim().toLowerCase();
  if (!term) return propertiesByScope.value;

  const result: Record<string, ConfigurablePropertyDto[]> = {};
  for (const [scope, props] of Object.entries(propertiesByScope.value)) {
    const matched = props.filter((p) => p.property_name?.toLowerCase().includes(term));
    if (matched.length > 0) result[scope] = matched;
  }
  return result;
});

const filteredScopeKeys = computed(() =>
  Object.keys(filteredPropertiesByScope.value).sort((a, b) => (a === 'common' ? -1 : b === 'common' ? 1 : a.localeCompare(b))),
);

const hasAnyOpenScope = computed(() => filteredScopeKeys.value.some((scope) => openScopes.value[scope]));

function getPropertyDescription(propertyName: string | undefined): string {
  if (!propertyName) return '-';
  const key = 'systemParameters.configurableProperties.descriptions.' + propertyName;
  return te(key) ? String(t(key)) : '-';
}

async function fetchConfigurablePropertiesList() {
  loadingProperties.value = true;
  return fetchConfigurableProperties()
    .then((data) => (configurableProperties.value = data))
    .catch((error) => addError(error))
    .finally(() => (loadingProperties.value = false));
}

function collapseAllScopes(): void {
  for (const scope of filteredScopeKeys.value) {
    openScopes.value[scope] = false;
  }
}

function onPropertySaved(scope: string): void {
  const propertyName = editingProperty.value?.property_name;
  editingProperty.value = undefined;
  modifiedScopes.value.add(scope);
  if (propertyName) modifiedProperties.value.add(propertyName);
  fetchConfigurablePropertiesList();
}

if (hasPermission(Permissions.CHANGE_CONFIGURATION_PROPERTY)) {
  fetchConfigurablePropertiesList();
}
</script>

<style lang="scss" scoped>
.settings-block:not(:last-child) {
  margin-bottom: 16px;
}
</style>
