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
package org.nuxeo.ecm.platform.semanticentities.sources;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.platform.semanticentities.service.RemoteEntitySourceDescriptor;

public class MockStanbolEntityHubSource extends StanbolEntityHubSource {

    @SuppressWarnings("unused")
    private static final Log log = LogFactory.getLog(MockStanbolEntityHubSource.class);

    static final String DBPEDIA_PREFIX = "http://dbpedia.org/resource/";

    @Override
    public void setDescriptor(RemoteEntitySourceDescriptor descriptor) {
        descriptor.getParameters().put("stanbolURL", "http://fakehost/");
        super.setDescriptor(descriptor);
    }

    @Override
    protected InputStream doHttpGet(URI uri, String format) throws MalformedURLException, IOException {

        if (uri.toString().endsWith("Obama.jpg")) {
            return getClass().getResourceAsStream("/mock_replies/200px-Official_portrait_of_Barack_Obama.jpg");
        }
        String query = uri.getQuery();
        if (query.startsWith("id=" + DBPEDIA_PREFIX)) {
            String fileName = query.substring(("id=" + DBPEDIA_PREFIX).length()) + ".json";
            return getClass().getResourceAsStream("/mock_replies/" + fileName);
        }
        throw new IllegalArgumentException("no mock resource registered for URI " + uri);
    }

    @Override
    protected InputStream doHttpPost(URI uri, String accepted, String contentType, String payload)
            throws MalformedURLException, IOException {
        if (uri.toString().endsWith("entityhub/site/dbpedia/query")) {
            if (payload.toLowerCase().contains("obama") && !payload.toLowerCase().contains("place")) {
                // poorman's way to emulate the server behavior based on the
                // JSON payload content of the query
                return getClass().getResourceAsStream("/mock_replies/obama_query_response.json");
            }
            // TODO: add mock replies for John Lennon and Liverpool instead
            return new ByteArrayInputStream("{\"results\": []}".getBytes());
        }
        throw new IllegalArgumentException("no mock resource registered for URI " + uri + " and payload: " + payload);
    }

}
