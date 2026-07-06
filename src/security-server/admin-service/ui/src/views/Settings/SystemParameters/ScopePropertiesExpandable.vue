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
  <XrdExpandable
    :data-test="`configurable-properties-panel-${scope}`"
    :is-open="isOpen"
    @open="emit('open', $event)"
  >
    <template #link="{ toggle }">
      <span
        class="cursor-pointer font-weight-medium text-capitalize"
        :data-test="`configurable-properties-panel-title-${scope}`"
        @click="toggle"
      >{{ scope }}</span>
    </template>

    <template #content>
      <div class="pr-4 pb-4 pl-4">
        <v-table class="xrd configurable-properties-table">
          <thead>
          <tr>
            <th>{{ $t('systemParameters.configurableProperties.table.header.propertyName') }}</th>
            <th>{{ $t('systemParameters.configurableProperties.table.header.currentValue') }}</th>
            <th>{{ $t('systemParameters.configurableProperties.table.header.defaultValue') }}</th>
            <th>{{ $t('systemParameters.configurableProperties.table.header.description') }}</th>
            <th></th>
          </tr>
          </thead>
          <tbody :data-test="`configurable-properties-table-body-${scope}`">
          <tr
            v-for="prop in properties"
            :key="prop.property_name"
            data-test="configurable-property-row"
          >
            <td class="property-name-cell">{{ prop.property_name }}</td>
            <td class="property-value-cell">{{ prop.current_value ?? '-' }}</td>
            <td class="property-value-cell">{{ prop.default_value || '-' }}</td>
            <td class="property-description-cell">{{ getPropertyDescription(prop.property_name) }}</td>
            <td>
              <div class="d-flex align-center justify-end">
                <v-tooltip v-if="modifiedProperties.has(prop.property_name!)" open-delay="500">
                  <template #activator="{ props: tooltipProps }">
                    <v-icon v-bind="tooltipProps" icon="warning" color="warning" class="mr-2" />
                  </template>
                  {{ $t('systemParameters.configurableProperties.propertyRestartWarning') }}
                </v-tooltip>
                <XrdBtn
                  data-test="edit-configurable-property-button"
                  variant="text"
                  text="action.edit"
                  color="tertiary"
                  @click="emit('edit-property', prop)"
                />
              </div>
            </td>
          </tr>
          </tbody>
        </v-table>
      </div>
    </template>
  </XrdExpandable>
</template>

<script lang="ts" setup>
import { XrdExpandable, XrdBtn } from '@niis/shared-ui';
import type { SecurityServerConfigurableProperty } from '@/openapi-types';
import { useI18n } from 'vue-i18n';

defineProps<{
  scope: string;
  properties: SecurityServerConfigurableProperty[];
  modifiedProperties: Set<string>;
  isOpen: boolean;
}>();

const emit = defineEmits<{
  open: [value: boolean];
  'edit-property': [prop: SecurityServerConfigurableProperty];
}>();

const { t, te } = useI18n();

function getPropertyDescription(propertyName: string | undefined): string {
  if (!propertyName) return '-';
  const key = 'systemParameters.configurableProperties.descriptions.' + propertyName;
  return te(key) ? String(t(key)) : '-';
}
</script>

<style lang="scss" scoped>
:deep(.configurable-properties-table) {
  table {
    table-layout: fixed;
    width: 100%;
  }

  td {
    overflow-wrap: break-word;
    word-break: break-word;
  }
}
</style>
