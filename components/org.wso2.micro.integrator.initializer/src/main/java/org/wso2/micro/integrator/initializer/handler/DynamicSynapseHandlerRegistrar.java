/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.micro.integrator.initializer.handler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseHandler;
import org.apache.synapse.core.SynapseEnvironment;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.micro.integrator.initializer.services.SynapseEnvironmentService;

import java.util.List;
import java.util.Map;

/**
 * OSGi component that dynamically registers SynapseHandlers published as OSGi services.
 * Handlers can be registered with service properties to control their registration behavior.
 */
@Component(
    name = "org.wso2.micro.integrator.initializer.handler.DynamicSynapseHandlerRegistrar",
    immediate = true
)
public class DynamicSynapseHandlerRegistrar {

    private static final Log log = LogFactory.getLog(DynamicSynapseHandlerRegistrar.class);
    private static final String HANDLER_NAME_PROPERTY = "handler.name";
    private static final String HANDLER_ENABLED_PROPERTY = "handler.enabled";
    
    private SynapseEnvironmentService synapseEnvironmentService;

    @Reference(
        name = "synapse.handler.service",
        service = SynapseHandler.class,
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC,
        unbind = "unregisterSynapseHandler"
    )
    protected void registerSynapseHandler(SynapseHandler handler, Map<String, Object> properties) {
        if (handler == null) {
            log.warn("Attempted to register null SynapseHandler");
            return;
        }

        // Check if handler is explicitly enabled via service property
        Object enabledProperty = properties.get(HANDLER_ENABLED_PROPERTY);
        if (enabledProperty == null || !Boolean.parseBoolean(enabledProperty.toString())) {
            if (log.isDebugEnabled()) {
                String handlerName = getHandlerName(handler, properties);
                log.debug("SynapseHandler " + handlerName + " is not enabled, skipping registration");
            }
            return;
        }

        if (synapseEnvironmentService != null) {
            SynapseEnvironment synapseEnvironment = synapseEnvironmentService.getSynapseEnvironment();
            if (synapseEnvironment != null) {
                try {
                    synapseEnvironment.registerSynapseHandler(handler);
                    String handlerName = getHandlerName(handler, properties);
                    if (log.isDebugEnabled()) {
                        log.debug("Registering SynapseHandler: " + handlerName);
                    }
                } catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Failed to register SynapseHandler: " + handler.getClass().getName(), e);
                    }
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("SynapseEnvironment not available, cannot register handler: " + 
                             handler.getClass().getName());
                }
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("SynapseEnvironmentService not available, cannot register handler: " + 
                         handler.getClass().getName());
            }
        }
    }

    protected void unregisterSynapseHandler(SynapseHandler handler, Map<String, Object> properties) {
        if (handler == null) {
            if (log.isDebugEnabled()) {
                log.debug("Attempted to unregister null SynapseHandler");
            }
            return;
        }

        String handlerName = getHandlerName(handler, properties);
        if (synapseEnvironmentService != null) {
            SynapseEnvironment synapseEnvironment = synapseEnvironmentService.getSynapseEnvironment();
            if (synapseEnvironment != null) {
                try {
                    List<SynapseHandler> handlers = synapseEnvironment.getSynapseHandlers();
                    if (handlers != null) {
                        handlers.remove(handler);
                        if (log.isDebugEnabled()) {
                            log.debug("Unregistered SynapseHandler: " + handlerName);
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("SynapseHandlers list is null, cannot unregister handler: " + handlerName);
                        }
                    }
                } catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Failed to unregister SynapseHandler: " + handlerName, e);
                    }
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("SynapseEnvironment not available, handler may have already been unregistered: " + 
                             handlerName);
                }
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("SynapseEnvironmentService not available, handler may have already been unregistered: " + 
                         handlerName);
            }
        }
    }

    @Reference(
        name = "synapse.environment.service",
        service = SynapseEnvironmentService.class,
        cardinality = ReferenceCardinality.MANDATORY,
        policy = ReferencePolicy.DYNAMIC,
        unbind = "unsetSynapseEnvironmentService"
    )
    protected void setSynapseEnvironmentService(SynapseEnvironmentService synapseEnvironmentService) {
        if (log.isDebugEnabled()) {
            log.debug("SynapseEnvironmentService bound to DynamicSynapseHandlerRegistrar");
        }
        this.synapseEnvironmentService = synapseEnvironmentService;
    }

    protected void unsetSynapseEnvironmentService(SynapseEnvironmentService synapseEnvironmentService) {
        if (log.isDebugEnabled()) {
            log.debug("SynapseEnvironmentService unbound from DynamicSynapseHandlerRegistrar");
        }
        this.synapseEnvironmentService = null;
    }

    /**
     * Get handler name from properties or use class name as fallback
     */
    private String getHandlerName(SynapseHandler handler, Map<String, Object> properties) {
        Object nameProperty = properties.get(HANDLER_NAME_PROPERTY);
        if (nameProperty != null) {
            return nameProperty.toString();
        }
        return handler.getClass().getName();
    }
}
