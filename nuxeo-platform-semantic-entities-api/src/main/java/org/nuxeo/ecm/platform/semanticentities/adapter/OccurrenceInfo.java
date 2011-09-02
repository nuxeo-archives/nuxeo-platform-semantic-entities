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
package org.nuxeo.ecm.platform.semanticentities.adapter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper data transfer object to handle the occurrence of the name of an entity
 * in some pure text context (typically a document snippet of maximum 3
 * sentences).
 *
 * @author ogrisel
 */
public class OccurrenceInfo {

    /**
     * The context part that actually references an entity
     */
    public final String mention;

    /**
     * A maximum 3 sentences long textual context snippet including the mention.
     */
    public final String context;

    /**
     * The start offset to locate the mention inside the context.
     */
    public final int startPosInContext;

    /**
     * The end offset to locate the mention inside the context.
     */
    public final int endPosInContext;

    public OccurrenceInfo(String mention, String context) {
        if (context == null || context.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot build OccurrenceInfo instance without a context");
        }
        if (mention == null || mention.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot build OccurrenceInfo instance without a mention");
        }
        if (!context.contains(mention)) {
            throw new IllegalArgumentException(String.format(
                    "'%s' should occur in context '%s'", mention, context));
        }
        this.context = context;
        this.mention = mention;
        startPosInContext = context.indexOf(mention);
        endPosInContext = startPosInContext + mention.length();
    }

    public OccurrenceInfo(String context, int startPosInContext,
            int endPosInContext) {
        if (context == null || context.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot build OccurrenceInfo instance without a context");
        }
        if (startPosInContext >= endPosInContext) {
            throw new IllegalArgumentException(String.format(
                    "Start position %d must be larger that end position %d",
                    startPosInContext, endPosInContext));
        } else if (startPosInContext < 0) {
            throw new IllegalArgumentException(String.format(
                    "Start position %d must be zero or positive",
                    startPosInContext));
        } else if (endPosInContext > context.length()) {
            throw new IllegalArgumentException(
                    String.format(
                            "End position %d must be smaller or equal to context length %d",
                            endPosInContext, context.length()));
        }
        this.context = context;
        mention = context.substring(startPosInContext, endPosInContext);
        this.startPosInContext = startPosInContext;
        this.endPosInContext = endPosInContext;
    }

    /**
     * @return a map suitable for saving the item in the occurrence:quotes
     *         field.
     */
    public Map<String, Serializable> asQuoteyMap() {
        Map<String, Serializable> quote = new HashMap<String, Serializable>();
        quote.put("text", context);
        quote.put("startPos", new Long(startPosInContext));
        quote.put("endPos", new Long(endPosInContext));
        return quote;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((context == null) ? 0 : context.hashCode());
        result = prime * result + ((mention == null) ? 0 : mention.hashCode());
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
        OccurrenceInfo other = (OccurrenceInfo) obj;
        if (context == null) {
            if (other.context != null) {
                return false;
            }
        } else if (!context.equals(other.context)) {
            return false;
        }
        if (mention == null) {
            if (other.mention != null) {
                return false;
            }
        } else if (!mention.equals(other.mention)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("OccurrenceInfo(\"%s\", \"%s\")", mention, context);
    }

    public String getMention() {
        return mention;
    }

    /**
     * @return the slice of the context sentence before the actual mention
     */
    public String getPrefixContext() {
        return context.substring(0, startPosInContext);
    }

    /**
     * @return the slice of the context sentence after the actual mention
     */
    public String getSuffixContext() {
        return context.substring(endPosInContext);
    }
}
