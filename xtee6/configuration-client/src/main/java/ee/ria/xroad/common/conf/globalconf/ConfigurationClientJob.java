/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.SystemProperties;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.time.LocalTime;

/**
 * Quartz job implementation for the configuration client.
 */
public class ConfigurationClientJob implements Job {

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        JobDataMap data = context.getJobDetail().getJobDataMap();
        Object client = data.get("client");

        if (client != null && client instanceof ConfigurationClient) {
            try {
                ((ConfigurationClient) client).execute();
                DiagnosticsStatus status = new DiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, LocalTime.now(),
                        LocalTime.now().plusSeconds(SystemProperties.getConfigurationClientUpdateIntervalSeconds()));
                context.setResult(status);
            } catch (Exception e) {
                DiagnosticsStatus status = new DiagnosticsStatus(ConfigurationClientUtils.getErrorCode(e),
                        LocalTime.now(),
                        LocalTime.now().plusSeconds(SystemProperties.getConfigurationClientUpdateIntervalSeconds()));
                context.setResult(status);
                throw new JobExecutionException(e);
            }
        } else {
            throw new JobExecutionException(
                    "Could not get configuration client from job data");
        }
    }
}
