/*
 *Copyright (c) 2005-2022, WSO2 LLC (http://www.wso2.com).
 *
 *WSO2 LLC licenses this file to you under the Apache License,
 *Version 2.0 (the "License"); you may not use this file except
 *in compliance with the License.
 *You may obtain a copy of the License at
 *
 *http://www.apache.org/licenses/LICENSE-2.0
 *
 *Unless required by applicable law or agreed to in writing,
 *software distributed under the License is distributed on an
 *"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *KIND, either express or implied.  See the License for the
 *specific language governing permissions and limitations
 *under the License.
 */

package org.wso2.esb.integration.common.utils.clients.tcpclient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * This class extends Client to use a socket without SSL for the communications.
 */
public class TCPClient extends Client {

    private Socket socket;

    public TCPClient(String host, int port) {

        super(host, port);
    }

    @Override
    public void open() throws Exception {

        log.info("TCPClient started :");

        socket = new Socket(getHost(), getPort());

        printWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        printStream = new PrintStream(socket.getOutputStream());
        bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    @Override
    public void close() throws Exception {

        socket.close();
    }
}
