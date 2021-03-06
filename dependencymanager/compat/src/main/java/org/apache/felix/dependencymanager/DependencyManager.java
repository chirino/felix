/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.dependencymanager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.osgi.framework.BundleContext;

/**
 * The dependency manager. Manages all services and their dependencies.
 * 
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class DependencyManager
{
    private org.apache.felix.dm.DependencyManager m_delegate;
    private List m_services = Collections.synchronizedList(new ArrayList());

    /**
     * Creates a new dependency manager.
     * 
     * @param context the bundle context
     * @param logger 
     */
    public DependencyManager(BundleContext context)
    {
        m_delegate = new org.apache.felix.dm.DependencyManager(context);
    }

    /**
     * Adds a new service to the dependency manager. After the service was added
     * it will be started immediately.
     * 
     * @param service the service to add
     */
    public void add(Service service)
    {
        m_services.add(service);
        service.start();
    }

    /**
     * Removes a service from the dependency manager. Before the service is removed
     * it is stopped first.
     * 
     * @param service the service to remove
     */
    public void remove(Service service)
    {
        service.stop();
        m_services.remove(service);
    }

    /**
     * Creates a new service.
     * 
     * @return the new service
     */
    public Service createService()
    {
        return new org.apache.felix.dependencymanager.impl.ServiceImpl(this);
    }

    /**
     * Creates a new service dependency.
     * 
     * @return the service dependency
     */
    public ServiceDependency createServiceDependency()
    {
        return new org.apache.felix.dependencymanager.impl.ServiceDependencyImpl(this);
    }

    public ConfigurationDependency createConfigurationDependency()
    {
        return new org.apache.felix.dependencymanager.impl.ConfigurationDependencyImpl(this);
    }

    /**
     * Returns a list of services.
     * 
     * @return a list of services
     */
    public List getServices()
    {
        return Collections.unmodifiableList(m_services);
    }

    /**
     * Returns the dependency manager delegate (only used by backward compatibility impl)
     */
    public Object getDelegate()
    {
        return m_delegate;
    }
}
