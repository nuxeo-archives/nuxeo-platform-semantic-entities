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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;

public class MockDBpediaEntitySource extends DBpediaEntitySource {

    URI BO_THUMB_URI = URI.create("http://upload.wikimedia.org/wikipedia/en/thumb/e/e9/Official_portrait_of_Barack_Obama.jpg/200px-Official_portrait_of_Barack_Obama.jpg");

    @Override
    protected InputStream doHttpGet(URI uri, String format) throws MalformedURLException, IOException {

        if (uri.equals(BO_THUMB_URI)) {
            return getClass().getResourceAsStream("/mock_replies/200px-Official_portrait_of_Barack_Obama.jpg");
        } else if (uri.toString().contains("CONSTRUCT")) {
            return getClass().getResourceAsStream("/mock_replies/barack_obama_sparql_reply.rdf.xml");
        } else {
            throw new IllegalArgumentException("no mock resource registered for URI " + uri);
        }
    }

    @Override
    protected InputStream fetchSuggestions(String keywords, int maxSuggestions) throws IOException {
        if (keywords.contains("Obama")) {
            return getClass().getResourceAsStream("/mock_replies/lookup-obama.xml");
        } else {
            return getClass().getResourceAsStream("/mock_replies/lookup-empty-resultset.xml");
        }
    }

}
