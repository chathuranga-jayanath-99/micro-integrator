/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.management.apis;

import com.google.gson.JsonObject;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.om.OMText;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.transport.passthru.util.RelayConstants;
import org.apache.synapse.transport.passthru.util.StreamingOnRequestDataSource;
import org.wso2.carbon.usage.data.exporter.ConsumptionDataExportService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.activation.DataHandler;

/**
 * Resource for exporting consumption data reports as a ZIP download.
 * Accepts POST requests with startDate and endDate parameters.
 * Uses ConsumptionDataExportServiceBinder to locate the implementation at runtime —
 * no compile-time dependency on the usage-data-receiver bundle.
 */
public class ConsumptionDataResource implements MiApiResource {

    private static final Log LOG = LogFactory.getLog(ConsumptionDataResource.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final long MAX_DATE_RANGE_DAYS = 730;

    private Set<String> methods;

    public ConsumptionDataResource() {
        methods = new HashSet<>();
        methods.add(Constants.HTTP_POST);
    }

    @Override
    public Set<String> getMethods() {
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext,
                          org.apache.axis2.context.MessageContext axis2MessageContext,
                          SynapseConfiguration synapseConfiguration) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Handling POST request for consumption data export");
        }

        // Parse request payload
        JsonObject payload;
        try {
            payload = Utils.getJsonPayload(axis2MessageContext);
        } catch (IOException e) {
            LOG.error("Error parsing request payload", e);
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("Error parsing request payload", e, axis2MessageContext, Constants.BAD_REQUEST));
            return true;
        }

        // Validate required parameters
        String startDateStr = payload.has("startDate") ? payload.get("startDate").getAsString() : null;
        String endDateStr = payload.has("endDate") ? payload.get("endDate").getAsString() : null;

        if (startDateStr == null || startDateStr.trim().isEmpty()) {
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("Missing required parameter: startDate", null, axis2MessageContext, Constants.BAD_REQUEST));
            return true;
        }

        if (endDateStr == null || endDateStr.trim().isEmpty()) {
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("Missing required parameter: endDate", null, axis2MessageContext, Constants.BAD_REQUEST));
            return true;
        }

        // Parse dates
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = LocalDate.parse(startDateStr, DATE_FORMATTER);
            endDate = LocalDate.parse(endDateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("Invalid date format. Expected format: yyyy-MM-dd", e, axis2MessageContext, Constants.BAD_REQUEST));
            return true;
        }

        if (startDate.isAfter(endDate)) {
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("startDate must be before or equal to endDate", null, axis2MessageContext, Constants.BAD_REQUEST));
            return true;
        }

        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_DATE_RANGE_DAYS) {
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("Date range exceeds maximum allowed span of " + MAX_DATE_RANGE_DAYS + " days",
                            null, axis2MessageContext, Constants.BAD_REQUEST));
            return true;
        }

        // Fetch the export service once to avoid TOCTOU race with OSGi dynamic unbinding
        ConsumptionDataExportService exportService = ConsumptionDataExportServiceBinder.getExportService();
        if (exportService == null) {
            LOG.warn("ConsumptionDataExportService is not available in the OSGi registry");
            Utils.setJsonPayLoad(axis2MessageContext,
                    createErrorResponse("Consumption data export is not available on this server."));
            axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, "503");
            return true;
        }

        try {
            String zipFilename = String.format("consumption_data_%s_to_%s.zip", startDateStr, endDateStr);
            String jsonFilename = String.format("consumption_data_%s_to_%s.json", startDateStr, endDateStr);
            byte[] zipBytes = exportService.exportConsumptionDataAsZip(startDate, endDate, jsonFilename);

            setZipResponse(axis2MessageContext, zipBytes, zipFilename);

        } catch (Exception e) {
            LOG.error("Error while processing consumption data export request", e);
            Utils.setJsonPayLoad(axis2MessageContext,
                    createErrorResponse("Failed to generate consumption report: " + e.getMessage()));
            axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, "500");
        }

        return true;
    }

    private void setZipResponse(org.apache.axis2.context.MessageContext axis2MessageContext,
                                 byte[] zipBytes, String zipFilename) throws Exception {

        // Set Content-Disposition via the transport headers map
        Map<String, String> transportHeaders = new HashMap<>();
        transportHeaders.put("Content-Disposition", "attachment; filename=\"" + zipFilename + "\"");
        axis2MessageContext.setProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS, transportHeaders);

        axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);
        axis2MessageContext.setProperty(Constants.MESSAGE_TYPE, Constants.MEDIA_TYPE_APPLICATION_OCTET_STREAM);
        axis2MessageContext.setProperty(Constants.CONTENT_TYPE, Constants.MEDIA_TYPE_APPLICATION_OCTET_STREAM);

        // Build SOAP body using StreamingOnRequestDataSource — consistent with CarbonAppResource
        SOAPFactory factory = OMAbstractFactory.getSOAP12Factory();
        SOAPEnvelope env = factory.getDefaultEnvelope();
        OMNamespace ns = factory.createOMNamespace(RelayConstants.BINARY_CONTENT_QNAME.getNamespaceURI(), "ns");
        OMElement omEle = factory.createOMElement(RelayConstants.BINARY_CONTENT_QNAME.getLocalPart(), ns);
        StreamingOnRequestDataSource ds = new StreamingOnRequestDataSource(new ByteArrayInputStream(zipBytes));
        DataHandler dh = new DataHandler(ds);
        OMText textData = factory.createOMText(dh, true);
        omEle.addChild(textData);
        env.getBody().addChild(omEle);
        axis2MessageContext.setEnvelope(env);
    }

    private JsonObject createErrorResponse(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return error;
    }
}
