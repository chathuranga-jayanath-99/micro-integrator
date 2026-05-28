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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.annotations.Activate;
import org.wso2.carbon.usage.data.exporter.ConsumptionDataExportService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

/**
 * OSGi DS component that binds ConsumptionDataExportService implementations.
 * Defined in management APIs so the receiver bundle has no reverse dependency here.
 * The receiver registers its implementation; this binder picks it up automatically.
 */
@Component(
        name = "consumption.data.export.service.binder",
        immediate = true
)
public class ConsumptionDataExportServiceBinder {

    private static final Log LOG = LogFactory.getLog(ConsumptionDataExportServiceBinder.class);

    private static volatile ConsumptionDataExportServiceBinder instance;
    private volatile ConsumptionDataExportService exportService;

    @Activate
    protected void activate() {
        instance = this;
    }

    @Deactivate
    protected void deactivate() {
        instance = null;
        this.exportService = null;
    }

    @Reference(
            name = "consumptionDataExportService",
            service = ConsumptionDataExportService.class,
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetExportService"
    )
    protected void setExportService(ConsumptionDataExportService service) {
        this.exportService = service;
    }

    protected void unsetExportService(ConsumptionDataExportService service) {
        this.exportService = null;
    }

    public static ConsumptionDataExportService getExportService() {
        if (instance == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("ConsumptionDataExportServiceBinder is not activated");
            }
            return null;
        }
        return instance.exportService;
    }

    public static boolean isServiceAvailable() {
        return instance != null && instance.exportService != null;
    }
}
