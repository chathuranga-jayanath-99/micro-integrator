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

package org.wso2.micro.integrator.http.backend.test;

import org.apache.http.HttpResponse;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.wso2.esb.integration.common.extensions.carbonserver.CarbonServerExtension;
import org.wso2.esb.integration.common.utils.CPUMonitor;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.clients.SimpleHttpClient;
import org.wso2.micro.integrator.http.client.test.RequestMethods;
import org.wso2.micro.integrator.http.client.test.SamplePayloads;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.wso2.micro.integrator.http.backend.test.Constants.API_CONTEXT;
import static org.wso2.micro.integrator.http.client.test.Constants.KEYSTORE_PATH;
import static org.wso2.micro.integrator.http.client.test.Utils.checkCPUUsage;
import static org.wso2.micro.integrator.http.client.test.Utils.getPayload;

public abstract class HTTPCoreBackendTest extends ESBIntegrationTest {

    private static CPUMonitor cpuMonitor;
    private List<BackendServer> backendServerList;
    protected SimpleHttpClient client;

    @BeforeClass
    public void init() throws Exception {

        cpuMonitor = new CPUMonitor();
        startBackendServers();
        super.init();
    }

    @BeforeMethod
    public void beforeTestCase(Object[] testArgs) throws Exception {

        HTTPRequestWithBackendResponse httpRequestWithBackendResponse = (HTTPRequestWithBackendResponse) testArgs[0];
        CarbonServerExtension.restartServer();
        setBackendServerParams(getPayload(httpRequestWithBackendResponse.getBackendResponse().getBackendPayloadSize()));
        client = new SimpleHttpClient();
        cpuMonitor.startLogging();
    }

    @AfterMethod
    public void afterTestCase() {

        cpuMonitor.stop();
    }

    @AfterClass
    public void cleanUp() throws Exception {

        stopBackendServers();
        super.cleanup();
    }

    private void startBackendServers() throws Exception {

        System.setProperty("javax.net.ssl.keyStore", KEYSTORE_PATH);
        System.setProperty("javax.net.ssl.keyStorePassword", "wso2carbon");

        backendServerList = getBackEndServers();

        for (BackendServer server : backendServerList) {
            server.start();
        }
    }

    private void stopBackendServers() {

        for (BackendServer server : backendServerList) {
            server.shutdown();
        }
    }

    private void setBackendServerParams(String backendPayload) {

        for (BackendServer server : backendServerList) {
            server.setPayload(backendPayload);
        }
    }

    protected abstract List<BackendServer> getBackEndServers() throws Exception;

    /**
     * Asserts the CPU usage. This method will add an alias to track the assertion that was called before closing the
     * socket.
     */
    protected static void assertCPUUsage() {

        checkCPUUsage(cpuMonitor, "CPU settled after closing the socket by client");
    }

    protected HttpResponse invokeHTTPCoreBETestAPI(HTTPRequestWithBackendResponse httpRequestWithBackendResponse)
            throws IOException {

        String apiInvocationURL = httpRequestWithBackendResponse.getHttpRequest().isSSLEnabled() ?
                getApiInvocationURLHttps(API_CONTEXT) : getApiInvocationURL(API_CONTEXT);

        apiInvocationURL += populatePathParam(httpRequestWithBackendResponse.getBackendResponse());

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/plain");

        if (httpRequestWithBackendResponse.getHttpRequest().getMethod().equals(RequestMethods.GET)) {
            return client.doGet((apiInvocationURL), headers);
        }
        return client.doPost((apiInvocationURL), headers, SamplePayloads.LARGE_PAYLOAD, "text/plain");
    }

    private static String populatePathParam(BackendResponse httpRequest) {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("/");
        stringBuilder.append(httpRequest.getProtocol());
        stringBuilder.append("/");
        stringBuilder.append(httpRequest.getPort());
        return stringBuilder.toString();
    }
}
