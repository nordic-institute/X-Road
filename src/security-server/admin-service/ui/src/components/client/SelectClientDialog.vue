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
  <XrdSimpleDialog
    hide-close
    scrollable
    save-button-text="localGroup.addSelected"
    height="752"
    width="840"
    :title="title"
    :disable-save="!selectedMember"
    @save="save"
    @cancel="cancel"
  >
    <template #content>
      <XrdFormBlock>
        <v-text-field
          v-model="search"
          data-test="client-search-input"
          class="xrd w-50 mb-6"
          hide-details
          autofocus
          prepend-inner-icon="search"
          :label="$t('action.search')"
        />
        <v-radio-group v-model="selectedMember">
          <v-data-table
            class="xrd"
            hide-default-footer
            items-per-page="-1"
            no-data-text="localGroup.noResults"
            :headers="headers"
            :items="filteredMembers"
          >
            <template #item.radio="{ item }">
              <v-radio :value="item" class="xrd" />
            </template>
            <template #item.name="{ item }">
              <client-name :client="item" />
            </template>
          </v-data-table>
        </v-radio-group>
      </XrdFormBlock>
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { Client } from '@/openapi-types';
import ClientName from '@/components/client/ClientName.vue';
import { XrdFormBlock } from '@niis/shared-ui';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

export default defineComponent({
  components: { ClientName, XrdFormBlock },
  props: {
    title: {
      type: String,
      required: true,
    },
    searchLabel: {
      type: String,
      required: true,
    },
    selectableClients: {
      type: Array as PropType<Client[]>,
      default: () => {
        return [];
      },
    },
  },
  emits: ['cancel', 'save'],
  data() {
    return {
      search: '',
      selectedMember: undefined,
    };
  },
  computed: {
    headers() {
      return [
        {
          title: '',
          align: 'start',
          key: 'radio',
        },
        {
          title: this.$t('general.name') as string,
          align: 'start',
          key: 'name',
          sortRaw(itemA, itemB) {
            const aName =
              itemA.subsystem_name ||
              itemA.subsystem_code ||
              itemA.member_name ||
              '';
            const bName =
              itemB.subsystem_name ||
              itemB.subsystem_code ||
              itemB.member_name ||
              '';
            return aName.localeCompare(bName);
          },
        },
        {
          title: this.$t('localGroup.id') as string,
          align: 'start',
          key: 'id',
        },
      ] as DataTableHeader[];
    },
    filteredMembers() {
      if (!this.search) {
        return this.selectableClients;
      }

      const tempSearch = this.search.toString().toLowerCase().trim();
      if (tempSearch === '') {
        return this.selectableClients;
      }

      return this.selectableClients.filter((member) => {
        if (member.member_name?.toLowerCase().includes(tempSearch)) {
          return true;
        } else if (member.subsystem_name?.toLowerCase().includes(tempSearch)) {
          return true;
        } else if (member.id?.toLowerCase().includes(tempSearch)) {
          return true;
        }

        return false;
      });
    },
  },
  methods: {
    cancel(): void {
      this.clearForm();
      this.$emit('cancel');
    },
    save(): void {
      this.$emit('save', this.selectedMember);
      this.clearForm();
    },

    clearForm(): void {
      // Reset initial state
      this.selectedMember = undefined;
      this.search = '';
    },
  },
});
</script>

<style lang="scss" scoped></style>
