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
package org.nuxeo.ecm.platform.semanticentities.sources;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.jboss.remoting.samples.chat.utility.Parameters;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.semanticentities.DereferencingException;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntity;
import org.nuxeo.ecm.platform.semanticentities.service.ParameterizedHTTPEntitySource;
import org.nuxeo.ecm.platform.semanticentities.service.RemoteEntitySourceDescriptor;

/**
 * Implementation of the RemoteEntitySource interface from the HTTP endpoint of
 * the EntityHub of a Stanbol instance.
 */
public class StanbolEntityHubSource extends ParameterizedHTTPEntitySource {

    private static final Log log = LogFactory.getLog(StanbolEntityHubSource.class);

    protected final ObjectMapper mapper = new ObjectMapper();

    protected String endpointURL;

    public StanbolEntityHubSource() {
        initHttpClient();
    }

    @Override
    public void setDescriptor(RemoteEntitySourceDescriptor descriptor) {
        this.descriptor = descriptor;
        endpointURL = descriptor.getParameters().get("stanbolURL");
        if (endpointURL == null || endpointURL.isEmpty()) {
            throw new RuntimeException("stanbolURL parameter is missing for the" +
            		" StanbolEntityHubSource ");
        }
        if (!endpointURL.endsWith("/")) {
            endpointURL += "/";
        }
        endpointURL += "entityhub/";
        String site = descriptor.getParameters().get("site");
        if (site != null) {
            endpointURL += "site/" + site + "/";
        }
        log.info("Configured StanbolEntityHubSource to endpoint: "
                + endpointURL);
    }

    /*
     * Raw HTTP fetch overridable for the tests.
     */
    protected InputStream fetchResourceAsStream(URI remoteEntity, String format) throws IOException {
        URI stanbolUri = UriBuilder.fromPath(endpointURL).queryParam("id",
                remoteEntity.toString()).build();
        HttpGet get = new HttpGet(stanbolUri);
        try {
            get.setHeader("Accept", format);
            HttpResponse response = httpClient.execute(get);
            if (response.getStatusLine().getStatusCode() == 200) {
                return response.getEntity().getContent();
            } else {
                String errorMsg = String.format("Error resolving '%s' : ",
                        stanbolUri);
                errorMsg += response.getStatusLine().toString();
                throw new IOException(errorMsg);
            }
        } catch (ClientProtocolException e) {
            get.abort();
            throw e;
        } catch (IOException e) {
            get.abort();
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> fetchJSONDescription(URI remoteEntity)
            throws JsonParseException, JsonMappingException, IOException {
        // TODO: make the format configurable and upgrade to JSON-LD once
        // the spec is stabilizing a bit
        String format = "application/json";
        return mapper.readValue(fetchResourceAsStream(remoteEntity, format),
                Map.class);
    }

    @Override
    public Set<String> getAdmissibleTypes(URI remoteEntity)
            throws DereferencingException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void dereferenceInto(DocumentModel localEntity, URI remoteEntity,
            boolean override) throws DereferencingException {
        // TODO Auto-generated method stub

    }

    @Override
    public List<RemoteEntity> suggestRemoteEntity(String keywords, String type,
            int maxSuggestions) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

}
