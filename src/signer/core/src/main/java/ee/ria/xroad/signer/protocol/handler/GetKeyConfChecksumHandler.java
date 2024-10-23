/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.FileContentChangeChecker;
import ee.ria.xroad.common.util.filewatcher.FileWatcherRunner;
import ee.ria.xroad.signer.protocol.AbstractRpcHandler;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.rpc.common.Empty;
import org.niis.xroad.signer.proto.KeyConfChecksum;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.lang.ref.WeakReference;
import java.nio.file.Paths;

@Component
@Slf4j
public class GetKeyConfChecksumHandler extends AbstractRpcHandler<Empty, KeyConfChecksum> implements DisposableBean, InitializingBean {

    private FileWatcherRunner fileWatcherRunner;
    private String checkSum;

    public GetKeyConfChecksumHandler() {
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // the change watcher can not be created in the constructor, because that would publish the
        // instance reference to another thread before the constructor finishes.
        final FileContentChangeChecker changeChecker = new FileContentChangeChecker(SystemProperties.getKeyConfFile());
        this.checkSum = changeChecker.getChecksum();
        this.fileWatcherRunner = createChangeWatcher(new WeakReference<>(this), changeChecker);
    }

    /* Implementation note:
     * Weak reference for the callback is used so that instance can be garbage collected.
     * Otherwise, the FileWatcher background thread keeps it alive and creates a leak
     * if one fails to call destroy.
     */
    private FileWatcherRunner createChangeWatcher(WeakReference<GetKeyConfChecksumHandler> ref, FileContentChangeChecker changeChecker) {
        return FileWatcherRunner.create()
                .watchForChangesIn(Paths.get(changeChecker.getFileName()))
                .listenToCreate()
                .listenToModify()
                .andOnChangeNotify(() -> {
                    final GetKeyConfChecksumHandler handler = ref.get();
                    if (handler == null) {
                        //stop watcher since the GetKeyConfChecksumHandler has become garbage
                        Thread.currentThread().interrupt();
                        return;
                    }
                    boolean changed = true;
                    try {
                        changed = changeChecker.hasChanged();
                    } catch (Exception e) {
                        log.error("Failed to check if key conf has changed", e);
                    }
                    if (changed) {
                        handler.checkSum = changeChecker.getChecksum();
                    }
                })
                .buildAndStartWatcher();
    }

    @Override
    protected KeyConfChecksum handle(Empty request) throws Exception {
        KeyConfChecksum.Builder builder = KeyConfChecksum.newBuilder();
        if (StringUtils.isNotBlank(checkSum)) {
            builder.setChecksum(checkSum);
        }
        return builder.build();
    }


    @Override
    public void destroy() throws Exception {
        if (fileWatcherRunner != null) {
            fileWatcherRunner.stop();
        }
    }

}
