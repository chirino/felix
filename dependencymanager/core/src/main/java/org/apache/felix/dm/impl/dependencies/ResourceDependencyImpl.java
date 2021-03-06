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
package org.apache.felix.dm.impl.dependencies;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Properties;

import org.apache.felix.dm.Dependency;
import org.apache.felix.dm.DependencyActivation;
import org.apache.felix.dm.DependencyService;
import org.apache.felix.dm.ResourceDependency;
import org.apache.felix.dm.ResourceHandler;
import org.apache.felix.dm.Service;
import org.apache.felix.dm.ServiceComponentDependency;
import org.apache.felix.dm.impl.InvocationUtil;
import org.apache.felix.dm.impl.Logger;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;

public class ResourceDependencyImpl extends DependencyBase implements ResourceDependency, ResourceHandler, DependencyActivation, ServiceComponentDependency {
	private volatile BundleContext m_context;
	private volatile ServiceRegistration m_registration;
//	private long m_resourceCounter;

    private Object m_callbackInstance;
    private String m_callbackAdded;
    private String m_callbackChanged;
    private String m_callbackRemoved;
    private boolean m_autoConfig;
    private String m_autoConfigInstance;
    protected List m_services = new ArrayList();
	private String m_resourceFilter;
	private URL m_trackedResource;
    private boolean m_isStarted;
    private List m_resources = new ArrayList();
    private URL m_resourceInstance;
    private boolean m_propagate;
    private Object m_propagateCallbackInstance;
    private String m_propagateCallbackMethod;
	
    public ResourceDependencyImpl(BundleContext context, Logger logger) {
        super(logger);
    	m_context = context;
    	m_autoConfig = true;
    }
    
    public ResourceDependencyImpl(ResourceDependencyImpl prototype) {
        super(prototype);
        m_context = prototype.m_context;
        m_autoConfig = prototype.m_autoConfig;
        m_callbackInstance = prototype.m_callbackInstance;
        m_callbackAdded = prototype.m_callbackAdded;
        m_callbackChanged = prototype.m_callbackChanged;
        m_callbackRemoved = prototype.m_callbackRemoved;
        m_autoConfigInstance = prototype.m_autoConfigInstance;
        m_resourceFilter = prototype.m_resourceFilter;
        m_trackedResource = prototype.m_trackedResource;
        m_propagate = prototype.m_propagate;
    }
    
    public Dependency createCopy() {
        return new ResourceDependencyImpl(this);
    }
    
	public synchronized boolean isAvailable() {
		return m_resources.size() > 0;
	}

	public void start(DependencyService service) {
	    boolean needsStarting = false;
	    synchronized (this) {
	        m_services.add(service);
	        if (!m_isStarted) {
	            m_isStarted = true;
	            needsStarting = true;
	        }
	    }
	    if (needsStarting) {
	        Dictionary props = null;
	        if (m_trackedResource != null) {
                props = new Properties();
                props.put(ResourceHandler.URL, m_trackedResource);
	        }
	        else if (m_resourceFilter != null) {
	            props = new Properties();
	            props.put(ResourceHandler.FILTER, m_resourceFilter);
	        }
	        m_registration = m_context.registerService(ResourceHandler.class.getName(), this, props);
	    }
	}

	public void stop(DependencyService service) {
	    boolean needsStopping = false;
	    synchronized (this) {
            if (m_services.size() == 1 && m_services.contains(service)) {
                m_isStarted = false;
                needsStopping = true;
                m_services.remove(service);
            }
	    }
	    if (needsStopping) {
	        m_registration.unregister();
	        m_registration = null;
	    }
	}

	public void added(URL resource) {
	    if (m_trackedResource == null || m_trackedResource.equals(resource)) {
    		long counter;
    		Object[] services;
    		synchronized (this) {
    		    m_resources.add(resource);
    			counter = m_resources.size();
    			services = m_services.toArray();
    		}
            for (int i = 0; i < services.length; i++) {
                DependencyService ds = (DependencyService) services[i];
                if (counter == 1) {
                    ds.dependencyAvailable(this);
                    if (!isRequired()) {
                        invokeAdded(ds, resource);
                    }
                }
                else {
                    ds.dependencyChanged(this);
                    invokeAdded(ds, resource);
                }
            }
	    }
	}

	public void changed(URL resource) {
        if (m_trackedResource == null || m_trackedResource.equals(resource)) {
            Object[] services;
            synchronized (this) {
                services = m_services.toArray();
            }
            for (int i = 0; i < services.length; i++) {
                DependencyService ds = (DependencyService) services[i];
                invokeChanged(ds, resource);
            }
        }
	}

	public void removed(URL resource) {
        if (m_trackedResource == null || m_trackedResource.equals(resource)) {
    		long counter;
    		Object[] services;
    		synchronized (this) {
    		    m_resources.remove(resource);
    			counter = m_resources.size();
    			services = m_services.toArray();
    		}
            for (int i = 0; i < services.length; i++) {
                DependencyService ds = (DependencyService) services[i];
                if (counter == 0) {
                    ds.dependencyUnavailable(this);
                    if (!isRequired()) {
                        invokeRemoved(ds, resource);
                    }
                }
                else {
                    ds.dependencyChanged(this);
                    invokeRemoved(ds, resource);
                }
            }
        }
	}
	
    public void invokeAdded(DependencyService ds, URL serviceInstance) {
        invoke(ds, serviceInstance, m_callbackAdded);
    }

    public void invokeChanged(DependencyService ds, URL serviceInstance) {
        invoke(ds, serviceInstance, m_callbackChanged);
    }

    public void invokeRemoved(DependencyService ds, URL serviceInstance) {
        invoke(ds, serviceInstance, m_callbackRemoved);
    }
    
    private void invoke(DependencyService ds, URL serviceInstance, String name) {
        if (name != null) {
            ds.invokeCallbackMethod(getCallbackInstances(ds), name,
                new Class[][] {{ Service.class, URL.class }, { Service.class, Object.class }, { Service.class },  { URL.class }, { Object.class }, {}},
                new Object[][] {{ ds.getServiceInterface(), serviceInstance }, { ds.getServiceInterface(), serviceInstance }, { ds.getServiceInterface() }, { serviceInstance }, { serviceInstance }, {}}
            );
        }
    }
    
    /**
     * Sets the callbacks for this service. These callbacks can be used as hooks whenever a
     * dependency is added or removed. When you specify callbacks, the auto configuration 
     * feature is automatically turned off, because we're assuming you don't need it in this 
     * case.
     * 
     * @param added the method to call when a service was added
     * @param removed the method to call when a service was removed
     * @return this service dependency
     */
    public synchronized ResourceDependency setCallbacks(String added, String removed) {
        return setCallbacks(null, added, null, removed);
    }

    /**
     * Sets the callbacks for this service. These callbacks can be used as hooks whenever a
     * dependency is added, changed or removed. When you specify callbacks, the auto 
     * configuration feature is automatically turned off, because we're assuming you don't 
     * need it in this case.
     * 
     * @param added the method to call when a service was added
     * @param changed the method to call when a service was changed
     * @param removed the method to call when a service was removed
     * @return this service dependency
     */
    public synchronized ResourceDependency setCallbacks(String added, String changed, String removed) {
        return setCallbacks(null, added, changed, removed);
    }

    /**
     * Sets the callbacks for this service. These callbacks can be used as hooks whenever a
     * dependency is added or removed. They are called on the instance you provide. When you
     * specify callbacks, the auto configuration feature is automatically turned off, because
     * we're assuming you don't need it in this case.
     * 
     * @param instance the instance to call the callbacks on
     * @param added the method to call when a service was added
     * @param removed the method to call when a service was removed
     * @return this service dependency
     */
    public synchronized ResourceDependency setCallbacks(Object instance, String added, String removed) {
        return setCallbacks(instance, added, null, removed);
    }
    
    /**
     * Sets the callbacks for this service. These callbacks can be used as hooks whenever a
     * dependency is added, changed or removed. They are called on the instance you provide. When you
     * specify callbacks, the auto configuration feature is automatically turned off, because
     * we're assuming you don't need it in this case.
     * 
     * @param instance the instance to call the callbacks on
     * @param added the method to call when a service was added
     * @param changed the method to call when a service was changed
     * @param removed the method to call when a service was removed
     * @return this service dependency
     */
    public synchronized ResourceDependency setCallbacks(Object instance, String added, String changed, String removed) {
        ensureNotActive();
        // if at least one valid callback is specified, we turn off auto configuration
        if (added != null || removed != null || changed != null) {
            setAutoConfig(false);
        }
        m_callbackInstance = instance;
        m_callbackAdded = added;
        m_callbackChanged = changed;
        m_callbackRemoved = removed;
        return this;
    }
    
    private void ensureNotActive() {
        if (m_registration != null) {
            throw new IllegalStateException("Cannot modify state while active.");
        }
    }
    
    /**
     * Sets auto configuration for this service. Auto configuration allows the
     * dependency to fill in any attributes in the service implementation that
     * are of the same type as this dependency. Default is on.
     * 
     * @param autoConfig the value of auto config
     * @return this service dependency
     */
    public synchronized ResourceDependency setAutoConfig(boolean autoConfig) {
        ensureNotActive();
        m_autoConfig = autoConfig;
        return this;
    }
    
    /**
     * Sets auto configuration for this service. Auto configuration allows the
     * dependency to fill in the attribute in the service implementation that
     * has the same type and instance name.
     * 
     * @param instanceName the name of attribute to auto config
     * @return this service dependency
     */
    public synchronized ResourceDependency setAutoConfig(String instanceName) {
        ensureNotActive();
        m_autoConfig = (instanceName != null);
        m_autoConfigInstance = instanceName;
        return this;
    }
    
//    private void invokeCallbackMethod(Object[] instances, String methodName, Object service) {
//        for (int i = 0; i < instances.length; i++) {
//            try {
//                invokeCallbackMethod(instances[i], methodName, service);
//            }
//            catch (NoSuchMethodException e) {
//                m_logger.log(Logger.LOG_DEBUG, "Method '" + methodName + "' does not exist on " + instances[i] + ". Callback skipped.");
//            }
//        }
//    }
//
//    private void invokeCallbackMethod(Object instance, String methodName, Object service) throws NoSuchMethodException {
//        Class currentClazz = instance.getClass();
//        boolean done = false;
//        while (!done && currentClazz != null) {
//            done = invokeMethod(instance, currentClazz, methodName,
//                new Class[][] {{Resource.class}, {Object.class}, {}},
//                new Object[][] {{service}, {service}, {}},
//                false);
//            if (!done) {
//                currentClazz = currentClazz.getSuperclass();
//            }
//        }
//        if (!done && currentClazz == null) {
//            throw new NoSuchMethodException(methodName);
//        }
//    }
//    
//    private boolean invokeMethod(Object object, Class clazz, String name, Class[][] signatures, Object[][] parameters, boolean isSuper) {
//        Method m = null;
//        for (int i = 0; i < signatures.length; i++) {
//            Class[] signature = signatures[i];
//            try {
//                m = clazz.getDeclaredMethod(name, signature);
//                if (!(isSuper && Modifier.isPrivate(m.getModifiers()))) {
//                    m.setAccessible(true);
//                    try {
//                        m.invoke(object, parameters[i]);
//                    }
//                    catch (InvocationTargetException e) {
//                        m_logger.log(Logger.LOG_ERROR, "Exception while invoking method " + m + ".", e);
//                    }
//                    // we did find and invoke the method, so we return true
//                    return true;
//                }
//            }
//            catch (NoSuchMethodException e) {
//                // ignore this and keep looking
//            }
//            catch (Exception e) {
//                // could not even try to invoke the method
//                m_logger.log(Logger.LOG_ERROR, "Exception while trying to invoke method " + m + ".", e);
//            }
//        }
//        return false;
//    }
    
    private synchronized Object[] getCallbackInstances(DependencyService ds) {
        if (m_callbackInstance == null) {
            return ds.getCompositionInstances();
        }
        else {
            return new Object[] { m_callbackInstance };
        }
    }

	public ResourceDependency setResource(URL resource) {
		m_trackedResource = resource;
		return this;
	}
	
    public synchronized ResourceDependency setRequired(boolean required) {
        ensureNotActive();
        setIsRequired(required);
        return this;
    }

	public ResourceDependency setFilter(String resourceFilter) {
        ensureNotActive();
		m_resourceFilter = resourceFilter;
		return this;
	}
    public synchronized boolean isAutoConfig() {
        return m_autoConfig;
    }
    
    public URL getResource() {
    	return lookupResource();
    }

    private URL lookupResource() {
        try {
            return (URL) m_resources.get(0);
        }
        catch (IndexOutOfBoundsException e) {
            return null;
        }
    }
    
    public Object getAutoConfigInstance() {
        return lookupResource();
    }

    public String getAutoConfigName() {
        return m_autoConfigInstance;
    }

    public Class getAutoConfigType() {
        return URL.class;
    }

    public void invokeAdded(DependencyService service) {
        // we remember these for future reference, needed for required callbacks
        m_resourceInstance = lookupResource();
        invokeAdded(service, m_resourceInstance);
    }

    public void invokeRemoved(DependencyService service) {
        invokeRemoved(service, m_resourceInstance);
        m_resourceInstance = null;
    }

    public ResourceDependency setPropagate(boolean propagate) {
        ensureNotActive();
        m_propagate = propagate;
        return this;
    }
    
    public ResourceDependency setPropagate(Object instance, String method) {
        setPropagate(instance != null && method != null);
        m_propagateCallbackInstance = instance;
        m_propagateCallbackMethod = method;
        return this;
    }
    
    public Dictionary getProperties() {
        URL resource = lookupResource();
        if (resource != null) {
            if (m_propagateCallbackInstance != null && m_propagateCallbackMethod != null) {
                try {
                    return (Dictionary) InvocationUtil.invokeCallbackMethod(m_propagateCallbackInstance, m_propagateCallbackMethod, new Class[][] {{ URL.class }}, new Object[][] {{ resource }});
                }
                catch (InvocationTargetException e) {
                    m_logger.log(LogService.LOG_WARNING, "Exception while invoking callback method", e.getCause());
                }
                catch (Exception e) {
                    m_logger.log(LogService.LOG_WARNING, "Exception while trying to invoke callback method", e);
                }
                throw new IllegalStateException("Could not invoke callback");
            }
            else {
                Properties props = new Properties();
                props.setProperty(ResourceHandler.HOST, resource.getHost());
                props.setProperty(ResourceHandler.PATH, resource.getPath());
                props.setProperty(ResourceHandler.PROTOCOL, resource.getProtocol());
                props.setProperty(ResourceHandler.PORT, Integer.toString(resource.getPort()));
                return props;
            }
        }
        else {
            throw new IllegalStateException("cannot find resource");
        }
    }

    public boolean isPropagated() {
        return m_propagate;
    }

    public ResourceDependency setInstanceBound(boolean isInstanceBound) {
        setIsInstanceBound(isInstanceBound);
        return this;
    }

    public String getName() {
        StringBuilder sb = new StringBuilder();
        if (m_resourceFilter != null) {
            sb.append(m_resourceFilter);
        }
        if (m_trackedResource != null) {
            sb.append(m_trackedResource.toString());
        }
        return sb.toString();
    }

    public int getState() {
        return (isAvailable() ? 1 : 0) + (isRequired() ? 2 : 0);
    }

    public String getType() {
        return "resource";
    }
}
