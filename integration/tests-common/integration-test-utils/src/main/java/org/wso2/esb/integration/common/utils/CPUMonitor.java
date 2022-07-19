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

package org.wso2.esb.integration.common.utils;

import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Callable;

/**
 * This class is used to monitor the CPU usage of the MI instance.
 */
public class CPUMonitor {

    private static final int CPU_THRESHOLD = 80;

    private static final String CPU_USAGE_FILE_PATH =
            System.getProperty(ESBTestConstant.CARBON_HOME) + File.separator + "cpu_usage.txt";

    private static final String CPU_LOGGER_SH_PATH =
            System.getProperty(ESBTestConstant.CARBON_HOME) + File.separator + "cpu_usage.sh";

    private static final String CARBON_PID_PATH =
            System.getProperty(ESBTestConstant.CARBON_HOME) + File.separator + "wso2carbon.pid";

    private Process process;

    public CPUMonitor() throws Exception {

        setup();
    }

    /**
     * This method will start a process to run the bash script.
     */
    public void startLogging() throws IOException {

        File file = new File(CPU_LOGGER_SH_PATH);
        file.setExecutable(true);
        process = new ProcessBuilder(CPU_LOGGER_SH_PATH, CARBON_PID_PATH, CPU_USAGE_FILE_PATH).start();
    }

    /**
     * This method will read the CPU Usage from the file cpu_usage.txt.
     */
    private static int readCPUFromFile() throws IOException {

        try (BufferedReader br = new BufferedReader(new FileReader(CPU_USAGE_FILE_PATH))) {
            String line;
            if ((line = br.readLine()) != null) {
                return Integer.parseInt(line);
            }
        }
        return -1;
    }

    /**
     * Check whether the CPU usage is below the given threshold.
     *
     * @return true if the CPU usage is below the threshold, false otherwise
     * @throws Exception if error occur while reading the CPU usage from file
     */
    public Callable<Boolean> isCPUSettled() {

        return new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {

                return readCPUFromFile() < CPU_THRESHOLD;
            }
        };
    }

    /**
     * This method will copy the bash script which is used to write the CPU usage to a file.
     *
     * @throws IOException if error occur while copying the CPU usage script
     */
    private void setup() throws IOException {

        URL inputUrl = getClass().getResource("/helper/cpu_usage.sh");
        File dest = new File(CPU_LOGGER_SH_PATH);
        FileUtils.copyURLToFile(inputUrl, dest);
    }

    /**
     * This method will destroy the process used to run the bash script.
     */
    public void stop() {

        process.destroy();
    }
}
