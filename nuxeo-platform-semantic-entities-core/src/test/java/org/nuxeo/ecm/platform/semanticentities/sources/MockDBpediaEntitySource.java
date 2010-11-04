package org.nuxeo.ecm.platform.semanticentities.sources;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;

public class MockDBpediaEntitySource extends DBpediaEntitySource {

    URI BO_SPARQL_URI = URI.create("http://dbpedia.org/sparql?query=CONSTRUCT+%7B+%3Chttp%3A%2F%2Fdbpedia.org%2Fresource%2FBarack_Obama%3E+%3Fp+%3Fo+%7D+WHERE+%7B+%3Chttp%3A%2F%2Fdbpedia.org%2Fresource%2FBarack_Obama%3E+%3Fp+%3Fo+%7D&format=application%2Frdf%2Bxml");

    URI BO_THUMB_URI = URI.create("http://upload.wikimedia.org/wikipedia/commons/thumb/e/e9/Official_portrait_of_Barack_Obama.jpg/200px-Official_portrait_of_Barack_Obama.jpg");

    @Override
    protected InputStream fetchResourceAsStream(URI uri, String format)
            throws MalformedURLException, IOException {
        if (uri.equals(BO_SPARQL_URI)) {
            return getClass().getResourceAsStream(
                    "/mock_replies/barack_obama_sparql_reply.rdf.xml");
        } else if (uri.equals(BO_THUMB_URI)) {
            return getClass().getResourceAsStream(
                    "/mock_replies/200px-Official_portrait_of_Barack_Obama.jpg");
        } else {
            throw new IllegalArgumentException(
                    "no mock resource registered for URI " + uri);
        }
    }

    @Override
    protected InputStream fetchSuggestions(String keywords, String type,
            int maxSuggestions) throws IOException {
        if (keywords.contains("Barack Obama") || keywords.contains("44th")) {
            return getClass().getResourceAsStream(
                    "/mock_replies/44th_US_president.xml");
        } else {
            throw new IllegalArgumentException("no mock suggestions for query "
                    + keywords);
        }
    }

}
