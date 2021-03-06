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
package org.apache.felix.dm.test.bundle.annotation.extraproperties;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.apache.felix.dm.annotation.api.Property;
import org.apache.felix.dm.annotation.api.Service;
import org.apache.felix.dm.annotation.api.ServiceDependency;
import org.apache.felix.dm.annotation.api.Start;
import org.apache.felix.dm.test.bundle.annotation.sequencer.Sequencer;

public class ExtraFactoryServiceProperties
{
    public interface Provider
    {
    }

    @Service(properties={@Property(name="foo", value="bar")}, factorySet="MyFactory")
    public static class ProviderImpl implements Provider
    {
        @Start
        Map<String, String> start()
        {
            return new HashMap<String, String>() {{ put("foo2", "bar2"); }};
        }
    }
    
    @Service
    public static class ProviderImplFactory
    {
        @ServiceDependency
        Set<Dictionary> m_factory;
        
        @Start
        void start()
        {
            m_factory.add(new Hashtable() {{ put("foo3", "bar3"); }});
        }
    }
    
    @Service
    public static class Consumer
    {
        @ServiceDependency(filter="(test=ExtraFactoryServiceProperties)")
        Sequencer m_sequencer;
        
        private Map m_properties;
        
        @ServiceDependency
        void bindProvider(Map properties, Provider m_provider)
        {
            m_properties = properties;
        }
        
        @Start
        void start() 
        {
            System.out.println("provider service properties: " + m_properties);
            if ("bar".equals(m_properties.get("foo"))) 
            {
                m_sequencer.step(1);
            }
            
            if ("bar2".equals(m_properties.get("foo2"))) 
            {
                m_sequencer.step(2);
            }
            
            if ("bar3".equals(m_properties.get("foo3"))) 
            {
                m_sequencer.step(3);
            }
        }
    }
}
