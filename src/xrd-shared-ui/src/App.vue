<template>
  <v-app>
    <v-main>
      <xrd-expandable>
        <template #link>
          xrd-form-label & xrd-help-icon
        </template>
        <template #action>
          Call for Action
        </template>
        <template #content>
          <xrd-form-label label-text="Label text" help-text="helpful tip"/>
          <xrd-help-icon text="help me"/>
        </template>
      </xrd-expandable>
      <xrd-expandable>
        <template #link>
          xrd-file-upload
        </template>
        <template #content>
          <xrd-file-upload
            v-slot="{ upload }"
            accepts="*"
          >
            <v-btn @click="upload">
              Upload
            </v-btn>
          </xrd-file-upload>
        </template>
      </xrd-expandable>
      <xrd-expandable>
        <template #link>
          xrd-button & xrd-close-button
        </template>
        <template #content>
          <xrd-button
            text="road-x"
            @click="loading=!loading"
          >
            {{ loading ? "Loading" : "Loaded" }}
          </xrd-button>
          <xrd-close-button @click="console.log('close it')"/>
        </template>
      </xrd-expandable>
      <xrd-expandable>
        <template #link>
          xrd-alert
        </template>
        <template #content>
          <xrd-alert
            :show="true"
            message="something went wrong"
          />
        </template>
      </xrd-expandable>
      <xrd-expandable>
        <template #link>
          xrd-empty-placeholder
        </template>
        <template #action>
          <xrd-button @click="loading=!loading" :text="loading?'Loading':'Loaded'"/>
        </template>
        <template #content>
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
          <xrd-empty-placeholder
            no-items-text="No data here"
            :loading="loading"
            skeleton-type="table-heading"
          />
        </template>
      </xrd-expandable>
      <xrd-expandable>
        <template #link>
          xrd-empty-placeholder-row
        </template>
        <template #action>
          <xrd-button @click="loading=!loading" :text="loading?'Loading':'Loaded'"/>
        </template>
        <template #content>
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
        </template>
      </xrd-expandable>
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
import XrdFileUpload from "./components/XrdFileUpload.vue";
import XrdHelpIcon from "./components/XrdHelpIcon.vue";
import XrdFormLabel from "./components/XrdFormLabel.vue";

let loading = ref(false);
let opened = ref(false);
let isOpen = ref(true);

</script>
