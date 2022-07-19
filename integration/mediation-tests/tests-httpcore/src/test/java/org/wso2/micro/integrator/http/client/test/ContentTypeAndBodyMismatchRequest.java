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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.wso2.esb.integration.common.utils.clients.tcpclient.Client;

import java.io.PrintWriter;

import static org.wso2.micro.integrator.http.client.test.Constants.HTTP_SC_200;
import static org.wso2.micro.integrator.http.client.test.Constants.HTTP_SC_202;

/**
 * Test case tests for MI behaviour(specifically CPU usage) when a content type and body mismatch HTTPS request is
 * received.
 * In this case, We are sending Content-Type: application/xml header with a JSON payload
 */
public class ContentTypeAndBodyMismatchRequest extends HTTPCoreClientTest {

    @Test(groups = {"wso2.esb"}, description = "Test for MI behaviour when Content Type and Body differs in a HTTP " +
            "request.", dataProvider = "httpRequestWithExpectedHTTPSC")
    public void testContentTypeAndBodyMismatchRequest(HttpRequestWithExpectedHTTPSC httpRequest) throws Exception {

        Client tcpClient = Utils.getTCPClient(httpRequest);
        tcpClient.open();
        sendHTTPRequest(tcpClient.getPrintWriter(), httpRequest.getMethod(), Utils
                .getPayload(httpRequest.getPayloadSize()));

        assertCPUUsageBeforeClosingSocket();

        Assert.assertTrue(tcpClient.getResponseAsString().contains(httpRequest.getExpectedHTTPSC()),
                "A " + httpRequest.getExpectedHTTPSC() + " HTTP Status");
        tcpClient.close();

        assertCPUUsageAfterClosingSocket();
    }

    private static void sendHTTPRequest(PrintWriter printWriter, RequestMethods method, String payload) {

        printWriter.print(method + " " + Constants.API_CONTEXT + " HTTP/1.1" + Constants.CRLF);
        printWriter.print("Accept: application/json" + Constants.CRLF);
        printWriter.print("Connection: keep-alive" + Constants.CRLF);
        printWriter.print("Content-Type: application/xml" + Constants.CRLF);
        if (StringUtils.isNotBlank(payload)) {
            printWriter.print("Content-Length: " + payload.getBytes().length + Constants.CRLF);
        }
        printWriter.print(Constants.CRLF);
        if (StringUtils.isNotBlank(payload)) {
            printWriter.print(payload);
        }
        printWriter.flush();
    }

    @DataProvider(name = "httpRequestWithExpectedHTTPSC")
    private static Object[][] httpRequestWithExpectedHTTPSC() {

        return new Object[][]{
                {new HttpRequestWithExpectedHTTPSC(RequestMethods.GET, PayloadSize.EMPTY, true, HTTP_SC_200)},
                {new HttpRequestWithExpectedHTTPSC(RequestMethods.GET, PayloadSize.SMALL, true, HTTP_SC_200)},
                {new HttpRequestWithExpectedHTTPSC(RequestMethods.GET, PayloadSize.LARGE, true, HTTP_SC_200)},
                {new HttpRequestWithExpectedHTTPSC(RequestMethods.POST, PayloadSize.EMPTY, true, HTTP_SC_200)},
                {new HttpRequestWithExpectedHTTPSC(RequestMethods.POST, PayloadSize.SMALL, true, HTTP_SC_202)},
                {new HttpRequestWithExpectedHTTPSC(RequestMethods.POST, PayloadSize.LARGE, true, HTTP_SC_202)},
                {new HttpRequestWithExpectedHTTPSC(RequestMethods.GET, PayloadSize.EMPTY, false, HTTP_SC_200)},
                {new HttpRequestWithExpectedHTTPSC(RequestMethods.GET, PayloadSize.SMALL, false, HTTP_SC_200)},
                {new HttpRequestWithExpectedHTTPSC(RequestMethods.GET, PayloadSize.LARGE, false, HTTP_SC_200)},
                {new HttpRequestWithExpectedHTTPSC(RequestMethods.POST, PayloadSize.EMPTY, false, HTTP_SC_200)},
                {new HttpRequestWithExpectedHTTPSC(RequestMethods.POST, PayloadSize.SMALL, false, HTTP_SC_202)},
                {new HttpRequestWithExpectedHTTPSC(RequestMethods.POST, PayloadSize.LARGE, false, HTTP_SC_202)}
        };
    }
}
