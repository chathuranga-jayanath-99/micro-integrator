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

import org.apache.commons.lang3.StringUtils;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.esb.integration.common.utils.clients.tcpclient.Client;

import java.io.PrintWriter;

import static org.wso2.micro.integrator.http.client.test.Constants.API_CONTEXT;
import static org.wso2.micro.integrator.http.client.test.Constants.CRLF;
import static org.wso2.micro.integrator.http.client.test.Utils.getPayload;
import static org.wso2.micro.integrator.http.client.test.Utils.getTCPClient;

/**
 * Test case tests for MI behaviour(specifically CPU usage) when a slow writing client sends a request.
 */
public class SlowWritingClientTestCase extends HTTPCoreClientTest {

    @Test(groups = {"wso2.esb"}, description = "Test for MI behaviour when a slow writing client sends a request.",
            dataProvider = "httpRequests", dataProviderClass = Constants.class)
    public void testSlowWritingClient(HTTPRequest httpRequest) throws Exception {

        Client tcpClient = getTCPClient(httpRequest);
        tcpClient.open();
        sendHTTPRequest(tcpClient.getPrintWriter(), httpRequest.getMethod(), getPayload(httpRequest.getPayloadSize()));

        assertCPUUsageBeforeClosingSocket();

        Assert.assertTrue(tcpClient.getResponseAsString().contains(Constants.HTTP_SC_200),
                "Expected a 200 OK response");

        tcpClient.close();

        assertCPUUsageAfterClosingSocket();
    }

    private static void sendHTTPRequest(PrintWriter printWriter, RequestMethods method, String payload)
            throws Exception {

        StringBuilder sb = new StringBuilder();
        sb.append(method).append(" ").append(API_CONTEXT).append(" HTTP/1.1").append(CRLF);
        sb.append("Content-Type: application/json" + CRLF);
        sb.append("Accept: application/json" + CRLF);
        sb.append("Connection: keep-alive" + CRLF);
        if (StringUtils.isNotBlank(payload)) {
            sb.append("Content-Length: ").append(payload.getBytes().length).append(CRLF);
        }
        sb.append(CRLF);
        if (StringUtils.isNotBlank(payload)) {
            sb.append(payload);
        }
        for (int i = 0; i < sb.length(); ++i) {
            printWriter.print(sb.charAt(i));
            printWriter.flush();
            if (i % 100 == 0) {
                Thread.sleep(20);
            }
        }
        printWriter.flush();
    }
}
