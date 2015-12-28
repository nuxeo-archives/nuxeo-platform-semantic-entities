/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Olivier Grisel
 */
package org.nuxeo.ecm.platform.semanticentities.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.model.RuntimeContext;

@XObject("remoteSource")
public class RemoteEntitySourceDescriptor {

    @XNode("@name")
    protected String name;

    @XNode("@uriPrefix")
    protected String uriPrefix;

    @XNode("@class")
    protected String className;

    @XNode("@enabled")
    protected boolean enabled = false;

    @XNode("typeMapping@default")
    protected String defaultType;

    protected ParameterizedHTTPEntitySource source;

    @XNodeMap(value = "typeMapping/type", key = "@name", type = HashMap.class, componentType = String.class)
    protected Map<String, String> mappedTypes = Collections.emptyMap();

    @XNodeMap(value = "propertyMapping/field", key = "@name", type = HashMap.class, componentType = String.class)
    protected Map<String, String> mappedProperties = Collections.emptyMap();

    @XNodeMap(value = "parameters/parameter", key = "@name", type = HashMap.class, componentType = String.class)
    protected Map<String, String> parameters = Collections.emptyMap();

    protected Map<String, String> reverseMappedTypes;

    public String getDefaultType() {
        return defaultType;
    }

    public Map<String, String> getMappedTypes() {
        return mappedTypes;
    }

    public Map<String, String> getReverseMappedTypes() {
        if (reverseMappedTypes == null) {
            Map<String, String> reversed = new TreeMap<String, String>();
            for (Map.Entry<String, String> entry : mappedTypes.entrySet()) {
                reversed.put(entry.getValue(), entry.getKey());
            }
            reverseMappedTypes = reversed;
        }
        return reverseMappedTypes;
    }

    public Map<String, String> getMappedProperties() {
        return mappedProperties;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public String getName() {
        return name;
    }

    public String getUriPrefix() {
        return uriPrefix;
    }

    public String getClassName() {
        return className;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void initializeInContext(RuntimeContext context) {
        if (className != null) {
            try {
                source = (ParameterizedHTTPEntitySource) context.loadClass(className).newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            source.setDescriptor(this);
        } else if (enabled) {
            throw new RuntimeException(String.format("Remote source descriptor '%s'  with enabled=\"true\""
                    + " must provide a class to instantiate", name));
        }
    }

    public ParameterizedHTTPEntitySource getEntitySource() {
        return source;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((className == null) ? 0 : className.hashCode());
        result = prime * result + (enabled ? 1231 : 1237);
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((uriPrefix == null) ? 0 : uriPrefix.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        RemoteEntitySourceDescriptor other = (RemoteEntitySourceDescriptor) obj;
        if (className == null) {
            if (other.className != null) {
                return false;
            }
        } else if (!className.equals(other.className)) {
            return false;
        }
        if (enabled != other.enabled) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (uriPrefix == null) {
            if (other.uriPrefix != null) {
                return false;
            }
        } else if (!uriPrefix.equals(other.uriPrefix)) {
            return false;
        }
        return true;
    }

}
