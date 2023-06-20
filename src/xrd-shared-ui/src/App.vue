<template>
  <v-app>
    <v-main>
      <hr>
      <xrd-expandable :is-open="opened" @close="opened=false" @open="opened=true">
        <template #link>Link to somewhere
        </template>
        <template #action>Call for Action
        </template>
        <template #content>some content text
        </template>

      </xrd-expandable>
      <hr>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>City</th>
          </tr>
        </thead>
        <tbody>
          <xrd-empty-placeholder-row
            colspan="2"
            no-items-text="No rows"
            :loading="loading"
          />
        </tbody>
      </table>
      <hr>
      <xrd-button
        text="road-x"
        @click="loading=!loading"
      >
        {{ loading?"Loading":"Loaded" }}
      </xrd-button>
      <hr>
      <xrd-close-button @click="console.log('close it')" />
      <hr>
      <xrd-alert
        :show="true"
        message="something went wrong"
      />
      <hr>
      <xrd-empty-placeholder
        no-items-text="No data here"
        :loading="loading"
        skeleton-type="table-heading"
      />
      <xrd-empty-placeholder
        no-items-text="No data here"
        :loading="loading"
      />
      <xrd-empty-placeholder
        no-items-text="No data here"
        :loading="loading"
        :filtered="true"
      />
      <xrd-empty-placeholder
        no-items-text="No data here"
        :loading="loading"
        no-matches-text="No matches here"
        :filtered="true"
      />
      <xrd-empty-placeholder
        no-items-text="No data here"
        :loading="loading"
        :data="[]"
      />
    </v-main>
  </v-app>
</template>

<script setup lang="ts">
import XrdAlert from "./components/XrdAlert.vue";
import XrdButton from "./components/XrdButton.vue";
import XrdCloseButton from "./components/XrdCloseButton.vue";
import XrdEmptyPlaceholder from "./components/XrdEmptyPlaceholder.vue";
import XrdEmptyPlaceholderRow from "./components/XrdEmptyPlaceholderRow.vue";
import { ref } from 'vue'
import XrdExpandable from "./components/XrdExpandable.vue";

let loading = ref(false);
let opened = ref(false);

</script>
