/*
 * Copyright (c) 2022, WSO2 LLC (http://www.wso2.com).
 *
 * WSO2 LLC licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.micro.integrator.http.client.test;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.wso2.esb.integration.common.extensions.carbonserver.CarbonServerExtension;
import org.wso2.esb.integration.common.utils.CPUMonitor;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;

import static org.wso2.micro.integrator.http.client.test.Utils.checkCPUUsage;

/**
 * This helper class contains the standard methods for setting up HTTP Core client scenario test cases.
 */
public class HTTPCoreClientTest extends ESBIntegrationTest {

    private static CPUMonitor cpuMonitor;

    @BeforeClass
    public void init() throws Exception {

        cpuMonitor = new CPUMonitor();
        super.init();
    }

    @BeforeMethod
    public void beforeTestCase() throws Exception {

        CarbonServerExtension.restartServer();
        cpuMonitor.startLogging();
    }

    @AfterMethod
    public void afterTestCase() {

        cpuMonitor.stop();
    }

    @AfterClass
    public void cleanUp() throws Exception {

        super.cleanup();
    }

    /**
     * Asserts the CPU usage. This method will add an alias to track the assertion that was called before closing the
     * socket.
     */
    public static void assertCPUUsageBeforeClosingSocket() {

        checkCPUUsage(cpuMonitor, "CPU settled before closing the socket by client");
    }

    /**
     * Asserts the CPU usage. This method will add an alias to track the assertion that was called after closing the
     * socket.
     */
    public static void assertCPUUsageAfterClosingSocket() {

        checkCPUUsage(cpuMonitor, "CPU settled after closing the socket by client");
    }
}
