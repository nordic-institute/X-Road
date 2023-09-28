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
  <v-app>
    <v-main style="max-width: 50%; margin: 0 auto">
      <xrd-expandable>
        <template #link> xrd-backups-data-table </template>
        <template #content>
          <xrd-backups-toolbar
            :backup-handler="backupsHandler"
            :can-backup="true"
            accepts="*"
          />
          <xrd-backups-data-table
            :can-backup="true"
            :backup-handler="backupsHandler"
            :loading="false"
            :backups="backups"
          />
        </template>
      </xrd-expandable>
      <xrd-expandable>
        <template #link> xrd-table </template>
        <template #content>
          <xrd-table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Year</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <th>John Doe</th>
                <th>2000</th>
              </tr>
            </tbody>
          </xrd-table>
        </template>
      </xrd-expandable>
      <xrd-expandable>
        <template #link> xrd-search </template>
        <template #content>
          <xrd-search v-model="search" label="search-label" />
          <xrd-search v-model="search" label="search-label" />
        </template>
      </xrd-expandable>
      <xrd-expandable>
        <template #link> xrd-sub-view-title/container </template>
        <template #content>
          <xrd-sub-view-container>
            <xrd-sub-view-title title="Before read" />
            <div>here is some of content to read before sleep</div>
          </xrd-sub-view-container>
        </template>
      </xrd-expandable>
      <xrd-expandable>
        <template #link> xrd-status-icon </template>
        <template #content>
          <xrd-status-icon status="ok" />
          <xrd-status-icon status="saved" />
          <xrd-status-icon status="pending" />
          <xrd-status-icon status="error" />
        </template>
      </xrd-expandable>
      <xrd-expandable>
        <template #link>
          xrd-simple-dialog, xrd-confirm-dialog & xrd-help-dialog
        </template>
        <template #content>
          <xrd-button text="Open dialog" @click="simpleDialog = true" />
          <xrd-simple-dialog
            :dialog="simpleDialog"
            title="action.add"
            @cancel="simpleDialog = false"
            @save="simpleDialog = false"
          >
            <template #content>
              <div>dialog content</div>
            </template>
          </xrd-simple-dialog>
          <xrd-button
            text="Open Confirm dialog"
            @click="confirmDialog = true"
          />
          <xrd-confirm-dialog
            :dialog="confirmDialog"
            title="action.add"
            text="locals_demo"
            :loading="false"
            @cancel="confirmDialog = false"
            @accept="confirmDialog = false"
          >
            <template #content>
              <div>dialog content</div>
            </template>
          </xrd-confirm-dialog>
          <xrd-button text="Open Help dialog" @click="helpDialog = true" />
          <xrd-help-dialog
            :dialog="helpDialog"
            title="action.add"
            text="locals_demo"
          >
            <template #content>
              <div>dialog content</div>
            </template>
          </xrd-help-dialog>
        </template>
      </xrd-expandable>
      <xrd-expandable>
        <template #link> xrd-form-label & xrd-help-icon </template>
        <template #content>
          <xrd-form-label label-text="Label text" help-text="helpful tip" />
          <xrd-help-icon text="help me" />
        </template>
      </xrd-expandable>
      <xrd-expandable>
        <template #link> xrd-file-upload </template>
        <template #content>
          <xrd-file-upload v-slot="{ upload }" accepts="*">
            <v-btn @click="upload"> Upload </v-btn>
          </xrd-file-upload>
        </template>
      </xrd-expandable>
      <xrd-expandable>
        <template #link> xrd-button & xrd-close-button </template>
        <template #content>
          <xrd-button text="road-x" @click="loading = !loading">
            {{ loading ? 'Loading' : 'Loaded' }}
          </xrd-button>
          <xrd-close-button @click="console.log('close it')" />
        </template>
      </xrd-expandable>
      <xrd-expandable>
        <template #link> xrd-alert </template>
        <template #content>
          <xrd-alert :show="true" message="something went wrong" />
        </template>
      </xrd-expandable>
      <xrd-expandable>
        <template #link> xrd-empty-placeholder </template>
        <template #action>
          <xrd-button text @click="loading = !loading">
            {{ loading ? 'Loading' : 'Loaded' }}
          </xrd-button>
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
        <template #link> xrd-empty-placeholder-row </template>
        <template #action>
          <xrd-button text @click="loading = !loading">
            {{ loading ? 'Loading' : 'Loaded' }}
          </xrd-button>
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
import { ref } from 'vue';
import XrdSubViewTitle from './components/XrdSubViewTitle.vue';
import XrdTable from './components/XrdTable.vue';
import XrdBackupsDataTable from './components/backups-and-restore/XrdBackupsDataTable.vue';
import XrdBackupsToolbar from './components/backups-and-restore/XrdBackupsToolbar.vue';
import { BackupHandler } from './types';

let loading = ref(false);
let simpleDialog = ref(false);
let confirmDialog = ref(false);
let helpDialog = ref(false);
let search = ref('');
let backups = ref([{ filename: 'safafaf.gpg' }]);
let backupsHandler = ref({
  download(filename: string): Promise<unknown> {
    console.log('download', filename);
    return new Promise((resolveOuter) => {
      resolveOuter(
        new Promise((resolveInner) => {
          setTimeout(resolveInner, 1000);
        }),
      );
    });
  },
} as BackupHandler);
</script>
