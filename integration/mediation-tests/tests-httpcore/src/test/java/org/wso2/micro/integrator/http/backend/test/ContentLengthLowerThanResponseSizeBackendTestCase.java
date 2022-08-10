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

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.testng.annotations.Test;
import org.wso2.micro.integrator.http.client.test.RequestMethods;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.wso2.micro.integrator.http.backend.test.Constants.HTTP_VERSION;
import static org.wso2.micro.integrator.http.backend.test.Utils.getServerSocket;
import static org.wso2.micro.integrator.http.client.test.Constants.CRLF;
import static org.wso2.micro.integrator.http.client.test.Utils.getPayload;

/**
 * Test case for MI behaviour(specifically CPU usage) when a HTTP response with content length header lower
 * than the actual body size is received.
 */
public class ContentLengthLowerThanResponseSizeBackendTestCase extends HTTPCoreBackendTest {

    @Test(groups = {"wso2.esb"}, description =
            "Test for MI behaviour when the content length header is lower than the the size of the backend " +
                    "response.",
            dataProvider = "httpRequestResponse", dataProviderClass = Constants.class)
    public void testContentLengthLowerThanResponsePayloadSize(
            HTTPRequestWithBackendResponse httpRequestWithBackendResponse)
            throws Exception {

        HttpResponse response = invokeHTTPCoreBETestAPI(httpRequestWithBackendResponse);

        assertCPUUsage();

        assertEquals(response.getStatusLine().getStatusCode(), 200, "Response not received");

        assertEquals(client.getResponsePayload(response).getBytes().length,
                getPayload(httpRequestWithBackendResponse.getBackendResponse().getBackendPayloadSize())
                        .getBytes().length,
                "Response size mismatch");
    }

    private static void sendHTTPRequest(PrintWriter printWriter, String path, RequestMethods method, String payload) {

        printWriter.print(method + " " + path + " HTTP/1.1" + CRLF);
        printWriter.print("Content-Type: application/json" + CRLF);
        printWriter.print("Accept: application/json" + CRLF);
        printWriter.print("Connection: keep-alive" + CRLF);
        if (StringUtils.isNotBlank(payload)) {
            printWriter.print("Content-Length: " + payload.getBytes().length + CRLF);
        }
        printWriter.print(CRLF);
        if (StringUtils.isNotBlank(payload)) {
            printWriter.print(payload);
        }
        printWriter.flush();
    }

    private static String populatePathParam(BackendResponse backend) {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("/");
        stringBuilder.append(backend.getProtocol());
        stringBuilder.append("/");
        stringBuilder.append(backend.getPort());
        return stringBuilder.toString();
    }

    @Override
    protected List<BackendServer> getBackEndServers() throws Exception {

        List<BackendServer> serverList = new ArrayList<>();
        serverList.add(new ContentLengthLowerThanBodyBackend(getServerSocket(true)));
        serverList.add(new ContentLengthLowerThanBodyBackend(getServerSocket(false)));

        return serverList;
    }

    private static class ContentLengthLowerThanBodyBackend extends BackendServer {

        public ContentLengthLowerThanBodyBackend(ServerSocket serverSocket) {

            super(serverSocket);
        }

        @Override
        protected synchronized void writeOutput(Socket socket) throws Exception {

            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            out.write(HTTP_VERSION + " 200 OK" + CRLF);
            out.write("Content-Type: application/json" + CRLF);
            if (StringUtils.isNotBlank(payload)) {
                out.write("Content-Length:  " + (payload.getBytes().length - 200) + CRLF);
            }
            out.write("Connection: keep-alive" + CRLF);
            out.write(CRLF);
            if (StringUtils.isNotBlank(payload)) {
                out.write(payload);
            }
            out.flush();
            socket.close();
        }
    }
}
