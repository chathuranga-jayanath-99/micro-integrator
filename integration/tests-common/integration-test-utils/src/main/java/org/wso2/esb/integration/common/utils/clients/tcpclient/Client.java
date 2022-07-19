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

package org.wso2.esb.integration.common.utils.clients.tcpclient;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * This class provides the base implementation for TCP client with or without SSL.
 */
public abstract class Client {

    protected static final Log log = LogFactory.getLog(Client.class);
    private final String host;
    private final int port;
    protected PrintWriter printWriter;
    protected PrintStream printStream;
    protected BufferedReader bufferedReader;

    public Client(String host, int port) {

        this.host = host;
        this.port = port;
    }

    public abstract void open() throws Exception;

    public abstract void close() throws Exception;

    protected String getHost() {

        return host;
    }

    protected int getPort() {

        return port;
    }

    public String getResponseAsString() throws IOException {

        StringBuilder stringBuilder = new StringBuilder();
        String line = null;
        bufferedReader = getBufferedReader();
        while ((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line);
            if (line.trim().equals("0")) {
                break;
            }
        }
        return stringBuilder.toString();
    }

    public PrintStream getPrintStream() {

        return printStream;
    }

    public PrintWriter getPrintWriter() {

        return this.printWriter;
    }

    public BufferedReader getBufferedReader() {

        return this.bufferedReader;
    }
}
