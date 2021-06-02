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
  <xrd-button
    :min_width="50"
    text
    :outlined="false"
    class="xrd-table-button"
    data-test="backup-restore"
    @click="showConfirmation = true"
    >{{ $t('action.restore') }}
    <xrd-confirm-dialog
      :dialog="showConfirmation"
      :loading="restoring"
      title="backup.action.restore.dialog.title"
      text="backup.action.restore.dialog.confirmation"
      :data="{ file: 'conf_backup_20210505-105548.tar' }"
      @cancel="showConfirmation = false"
      @accept="restoreBackup"
    />
    <xrd-simple-dialog
      :show-progress-bar="restoring"
      :hide-save-button="true"
      :dialog="showRestoringDialog"
      cancel-button-text="action.close"
      title="backup.action.restore.dialog.restoringTitle"
      @cancel="showRestoringDialog = false"
    >
      <template #content>
        <v-sheet class="pa-3 overflow-y-auto" height="15rem" rounded outlined>
          CHECKING THE LABEL OF THE TAR ARCHIVE
          security_XROAD_7.1_CS/ORG/1111/SS1 RESTORING CONFIGURATION FROM
          /var/lib/xroad/backup/conf_backup_20210506-062914.tar CLEARING SHARED
          MEMORY STOPPING REGISTERED SERVICES initctl stop xroad-confclient
          initctl stop xroad-signer initctl stop xroad-monitor initctl stop
          xroad-proxy initctl stop xroad-opmonitor CREATING PRE-RESTORE BACKUP
          Creating database dump to /var/lib/xroad/dbdump.dat Creating
          pre-restore backup archive to
          /var/lib/xroad/conf_prerestore_backup.tar:
          security_XROAD_7.1_CS/ORG/1111/SS1 tar: Removing leading `/' from
          member names /etc/xroad/jetty/xroad.mod
          /etc/xroad/jetty/contexts-admin/proxy-ui.xml
          /etc/xroad/jetty/ocsp-responder.xml /etc/xroad/jetty/serverproxy.xml
        </v-sheet>
      </template>
    </xrd-simple-dialog>
  </xrd-button>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  name: 'RestoreBackupButton',
  data() {
    return {
      showConfirmation: false as boolean,
      showRestoringDialog: false as boolean,
      restoring: false as boolean,
    };
  },
  methods: {
    async restoreBackup() {
      this.showRestoringDialog = true;
      this.restoring = true;
      this.showConfirmation = false;
      await new Promise<void>((res) => {
        setTimeout(() => res(), 3000);
      });
      this.restoring = false;
      // show success snackbar
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
</style>
