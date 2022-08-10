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
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.wso2.micro.integrator.http.backend.test.Constants.HTTP_VERSION;
import static org.wso2.micro.integrator.http.backend.test.Utils.getServerSocket;
import static org.wso2.micro.integrator.http.utils.Constants.CRLF;
import static org.wso2.micro.integrator.http.utils.Utils.getPayload;

/**
 * Test case for MI behaviour(specifically CPU usage) when a chunked HTTP response is received.
 */
public class ChunkedBackendTestCase extends HTTPCoreBackendTest {

    @Test(groups = {"wso2.esb"}, description =
            "Test for MI behaviour when a chunked HTTP response is received.",
            dataProvider = "httpRequestResponse", dataProviderClass = Constants.class)
    public void testChunkedBackend(HTTPRequestWithBackendResponse httpRequestWithBackendResponse)
            throws Exception {

        HttpResponse response = invokeHTTPCoreBETestAPI(httpRequestWithBackendResponse);

        assertCPUUsage();

        assertEquals(response.getStatusLine().getStatusCode(), 403, "Response not received");

        assertEquals(client.getResponsePayload(response).getBytes().length,
                getPayload(httpRequestWithBackendResponse.getBackendResponse().getBackendPayloadSize())
                        .getBytes().length,
                "Response size mismatch");
    }

    @Override
    protected List<BackendServer> getBackEndServers() throws Exception {

        List<BackendServer> serverList = new ArrayList<>();
        serverList.add(new ChunkedBackendServer(getServerSocket(true)));
        serverList.add(new ChunkedBackendServer(getServerSocket(false)));

        return serverList;
    }

    private static class ChunkedBackendServer extends BackendServer {

        public ChunkedBackendServer(ServerSocket serverSocket) {

            super(serverSocket);
        }

        @Override
        protected void readInput(Socket socket) throws Exception {

            // Get input and output streams to talk to the client
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            char[] buf = new char[10];
            StringBuilder outt = new StringBuilder();
            while (true) {
                try {
                    int read = in.read(buf);
                    outt.append(buf, 0, read);
                    if (read < 100)
                        break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        protected synchronized void writeOutput(Socket socket) throws Exception {

            PrintStream out = new PrintStream(socket.getOutputStream());

            InputStream payloadStream = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));

            int chunkSize = 100;
            int count;
            byte[] buffer = new byte[chunkSize];

            out.print(HTTP_VERSION + " 403 Forbidden" + CRLF);
            out.print("Content-Type: application/json" + CRLF);
            out.print("Transfer-Encoding: chunked" + CRLF);
            out.print("Connection: keep-alive" + CRLF);
            out.print(CRLF);

            while ((count = payloadStream.read(buffer)) > 0) {
                out.printf("%x" + CRLF, count);
                out.write(buffer, 0, count);
                out.print(CRLF);
            }

            out.print("0" + CRLF);
            out.print(CRLF);
            out.flush();
            socket.close();
        }
    }
}
