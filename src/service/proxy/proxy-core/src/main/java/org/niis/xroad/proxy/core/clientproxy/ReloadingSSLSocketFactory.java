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
package org.niis.xroad.proxy.core.clientproxy;

import lombok.SneakyThrows;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.proxy.core.util.SSLContextUtil;

import javax.net.ssl.SSLSocketFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class ReloadingSSLSocketFactory extends SSLSocketFactory {
    private final GlobalConfProvider globalConfProvider;
    private final KeyConfProvider keyConfProvider;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile SSLSocketFactory internalFactory;

    ReloadingSSLSocketFactory(GlobalConfProvider globalConfProvider, KeyConfProvider keyConfProvider) {
        this.globalConfProvider = globalConfProvider;
        this.keyConfProvider = keyConfProvider;
        reload();
    }

    @SneakyThrows
    @SuppressWarnings("checkstyle:SneakyThrowsCheck") //TODO XRDDEV-2390 will be refactored in the future
    public void reload() {
        lock.writeLock().lock();
        try {
            internalFactory = SSLContextUtil.createXroadSSLContext(globalConfProvider, keyConfProvider).getSocketFactory();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String[] getDefaultCipherSuites() {
        lock.readLock().lock();
        try {
            return internalFactory.getDefaultCipherSuites();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String[] getSupportedCipherSuites() {
        lock.readLock().lock();
        try {
            return internalFactory.getSupportedCipherSuites();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        lock.readLock().lock();
        try {
            return internalFactory.createSocket(s, host, port, autoClose);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        lock.readLock().lock();
        try {
            return internalFactory.createSocket(host, port);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
        lock.readLock().lock();
        try {
            return internalFactory.createSocket(host, port, localHost, localPort);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        lock.readLock().lock();
        try {
            return internalFactory.createSocket(host, port);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        lock.readLock().lock();
        try {
            return internalFactory.createSocket(address, port, localAddress, localPort);
        } finally {
            lock.readLock().unlock();
        }
    }
}
