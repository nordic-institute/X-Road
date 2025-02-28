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
