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
package org.niis.xroad.monitor.core.common;

/**
 * Names for system metrics
 */
public final class SystemMetricNames {

    public static final String OPEN_FILE_DESCRIPTOR_COUNT = "OpenFileDescriptorCount";
    public static final String MAX_FILE_DESCRIPTOR_COUNT = "MaxFileDescriptorCount";
    public static final String COMMITTED_VIRTUAL_MEMORY = "CommittedVirtualMemory";
    public static final String TOTAL_SWAP_SPACE = "TotalSwapSpace";
    public static final String FREE_SWAP_SPACE = "FreeSwapSpace";
    public static final String FREE_PHYSICAL_MEMORY = "FreePhysicalMemory";
    public static final String TOTAL_PHYSICAL_MEMORY = "TotalPhysicalMemory";
    public static final String SYSTEM_CPU_LOAD = "SystemCpuLoad";
    public static final String DISK_SPACE_TOTAL = "DiskSpaceTotal";
    public static final String DISK_SPACE_FREE = "DiskSpaceFree";
    public static final String PROCESSES = "Processes";
    public static final String PROCESS_STRINGS = "ProcessDump";
    public static final String XROAD_PROCESSES = "Xroad Processes";
    public static final String XROAD_PROCESS_STRINGS = "XroadProcessDump";
    public static final String PACKAGES = "Packages";
    public static final String PACKAGE_STRINGS = "PackagesDump";
    public static final String OS_INFO = "OperatingSystem";
    public static final String CERTIFICATES = "Certificates";
    public static final String CERTIFICATES_STRINGS = "CertificatesDump";

    private SystemMetricNames() {
    }
}
