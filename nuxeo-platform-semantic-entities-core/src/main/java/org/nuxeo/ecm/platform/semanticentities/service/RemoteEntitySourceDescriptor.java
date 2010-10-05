/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Olivier Grisel
 */
package org.nuxeo.ecm.platform.semanticentities.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

    protected ParameterizedRemoteEntitySource source;

    @XNodeMap(value = "typeMapping/type", key = "@name", type = HashMap.class, componentType = String.class)
    protected Map<String, String> mappedTypes = Collections.emptyMap();

    @XNodeMap(value = "propertyMapping/field", key = "@name", type = HashMap.class, componentType = String.class)
    protected Map<String, String> mappedProperties = Collections.emptyMap();

    public String getDefaultType() {
        return defaultType;
    }

    public Map<String, String> getMappedTypes() {
        return mappedTypes;
    }

    public Map<String, String> getMappedProperties() {
        return mappedProperties;
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

    public void initializeInContext(RuntimeContext context)
            throws InstantiationException, IllegalAccessException,
            ClassNotFoundException {
        if (className != null) {
            source = (ParameterizedRemoteEntitySource) context.loadClass(
                    className).newInstance();
            source.setDescriptor(this);
        } else if (enabled) {
            throw new InstantiationException(String.format(
                    "Remote source descriptor '%s'  with enabled=\"true\""
                            + " must provide a class to instantiate", name));
        }
    }

    public ParameterizedRemoteEntitySource getEntitySource() {
        return source;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((className == null) ? 0 : className.hashCode());
        result = prime * result + (enabled ? 1231 : 1237);
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result
                + ((uriPrefix == null) ? 0 : uriPrefix.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RemoteEntitySourceDescriptor other = (RemoteEntitySourceDescriptor) obj;
        if (className == null) {
            if (other.className != null)
                return false;
        } else if (!className.equals(other.className))
            return false;
        if (enabled != other.enabled)
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (uriPrefix == null) {
            if (other.uriPrefix != null)
                return false;
        } else if (!uriPrefix.equals(other.uriPrefix))
            return false;
        return true;
    }

}
