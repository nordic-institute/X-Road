/*
 * The MIT License
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
package org.niis.xroad.globalconf.impl.extension;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.dto.InMemoryFile;
import org.niis.xroad.globalconf.GlobalConfSource;
import org.niis.xroad.globalconf.impl.FileSystemGlobalConfSource;
import org.niis.xroad.globalconf.impl.RemoteGlobalConfSource;
import org.niis.xroad.globalconf.model.AbstractXmlConf;
import org.niis.xroad.globalconf.model.FileSource;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@RequiredArgsConstructor
public class GlobalConfExtensionLoaderImpl<T extends AbstractXmlConf<?>> {
    private final ReentrantLock lock = new ReentrantLock();

    private final GlobalConfSource globalConfSource;
    private final String extensionFileName;
    private final Class<T> extensionClass;


    private T reference;

    T getExtension() {
        try {
            if (reference != null && reference.hasChanged()) {
                reload();
            } else if (reference == null) {
                load();
            }
        } catch (Exception e) {
            log.error("Exception while fetching ocsp fetch interval configuration", e);
        }
        return reference;
    }

    private void reload() {
        lock.lock();

        try {
            if (reference.hasChanged()) {
                log.trace("reload");
                reference.reload();
            }
        } finally {
            lock.unlock();
        }
    }

    private void load() throws InvocationTargetException, NoSuchMethodException,
            InstantiationException, IllegalAccessException {
        lock.lock();
        try {
            if (reference == null) {
                var source = globalConfSource.getFile(extensionFileName);
                switch (source) {
                    case FileSystemGlobalConfSource.FileSystemFileSource fsSource -> loadFromFS(fsSource);
                    case RemoteGlobalConfSource.GlobalConfFileSource remoteSource -> loadFromRemote(remoteSource);
                    default -> throw new IllegalStateException("Unknown source type: " + source.getClass());
                }
                log.trace("Parameters were loaded, value: {}", reference);
            }
        } finally {
            lock.unlock();
        }
    }

    private void loadFromFS(FileSystemGlobalConfSource.FileSystemFileSource fsSource)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        if (fsSource.getFile().isPresent()) {
            log.trace("Loading GlobalConfExtension from FS path {}", fsSource.getFile().get());
            reference = extensionClass.getDeclaredConstructor().newInstance();
            reference.load(fsSource.getFile().get().toString());
        } else {
            log.trace("Extension {}, file does not exist in FS", fsSource);
        }
    }

    private void loadFromRemote(FileSource<InMemoryFile> remoteSource)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        if (remoteSource.getFile().isPresent()) {
            log.trace("Loading GlobalConfExtension from Remote");
            reference = extensionClass.getDeclaredConstructor().newInstance();
            reference.load(remoteSource);
        } else {
            log.trace("Extension {}, file does not exist in remote source", remoteSource);
        }
    }
}
