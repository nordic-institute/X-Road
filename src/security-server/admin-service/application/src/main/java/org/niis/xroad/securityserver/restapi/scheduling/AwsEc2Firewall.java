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
package org.niis.xroad.securityserver.restapi.scheduling;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2AsyncClient;
import software.amazon.awssdk.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.IpRange;

import java.time.Duration;
import java.util.concurrent.CompletionException;

@Slf4j
@SuppressWarnings("checkstyle:MagicNumber")
public class AwsEc2Firewall implements Firewall {

    private static final int SECURITY_SERVER_MESSAGE_PORT = 5500;
    private static final int SECURITY_SERVER_OCSP_PORT = 5577;

    private static Ec2AsyncClient ec2AsyncClient;

    // https://docs.aws.amazon.com/code-library/latest/ug/java_2_ec2_code_examples.html
    private static Ec2AsyncClient getAsyncClient() {
        if (ec2AsyncClient == null) {
            /*
            The `NettyNioAsyncHttpClient` class is part of the AWS SDK for Java, version 2,
            and it is designed to provide a high-performance, asynchronous HTTP client for interacting with AWS services.
             It uses the Netty framework to handle the underlying network communication and the Java NIO API to
             provide a non-blocking, event-driven approach to HTTP requests and responses.
             */
            SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
                    .maxConcurrency(50)  // Adjust as needed.
                    .connectionTimeout(Duration.ofSeconds(60))  // Set the connection timeout.
                    .readTimeout(Duration.ofSeconds(60))  // Set the read timeout.
                    .writeTimeout(Duration.ofSeconds(60))  // Set the write timeout.
                    .build();

            ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                    .apiCallTimeout(Duration.ofMinutes(2))  // Set the overall API call timeout.
                    .apiCallAttemptTimeout(Duration.ofSeconds(90))  // Set the individual call attempt timeout.
                    .build();

            ec2AsyncClient = Ec2AsyncClient.builder()
                    .region(Region.EU_WEST_1)
                    .httpClient(httpClient)
                    .overrideConfiguration(overrideConfig)
                    .build();
        }
        return ec2AsyncClient;
    }


    @Override
    public void addAllowAddressRule(String ipAddress, String groupName) {
        log.info("aws ec2 add allow address: {}", ipAddress);

/*
        Check if group already exists and if not try to create one.

        CreateSecurityGroupRequest createRequest = CreateSecurityGroupRequest.builder()
                .groupName(groupName[0])
                .description("security server allow rule")
                .build();

*/
        IpRange ipRange = IpRange.builder()
                .cidrIp(ipAddress + "/32")
                .build();

        IpPermission ipPerm = IpPermission.builder()
                .ipProtocol("tcp")
                .toPort(SECURITY_SERVER_MESSAGE_PORT)
                .fromPort(SECURITY_SERVER_MESSAGE_PORT)
                .ipRanges(ipRange)
                .build();

        IpPermission ipPerm2 = IpPermission.builder()
                .ipProtocol("tcp")
                .toPort(SECURITY_SERVER_OCSP_PORT)
                .fromPort(SECURITY_SERVER_OCSP_PORT)
                .ipRanges(ipRange)
                .build();


        AuthorizeSecurityGroupIngressRequest authRequest = AuthorizeSecurityGroupIngressRequest.builder()
                            .groupName(groupName)
                            .ipPermissions(ipPerm, ipPerm2)
                            .build();

        getAsyncClient().authorizeSecurityGroupIngress(authRequest)
                .whenComplete((result, exception) -> {
                    log.info("aws ec2 add allow address result: {}", result);
                    if (exception != null) {
                        log.error("aws ec2 add allow address exception: {}", exception.getMessage());
                        if (exception instanceof CompletionException && exception.getCause() instanceof Ec2Exception) {
                            throw (Ec2Exception) exception.getCause();
                        } else {
                            throw new RuntimeException("Failed to create security group: " + exception.getMessage(), exception);
                        }
                    }
                });
    }

    @Override
    public void removeAllowAddressRule(String address, String groupName) {

    }
}
