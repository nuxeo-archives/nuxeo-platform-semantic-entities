package org.nuxeo.ecm.platform.semanticentities.sources;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;

public class MockDBpediaEntitySource extends DBpediaEntitySource {

    URI BO_SPARQL_URI = URI.create("http://dbpedia.org/sparql?query=CONSTRUCT+%7B+%3Chttp%3A%2F%2Fdbpedia.org%2Fresource%2FBarack_Obama%3E+%3Fp+%3Fo+%7D+WHERE+%7B+%3Chttp%3A%2F%2Fdbpedia.org%2Fresource%2FBarack_Obama%3E+%3Fp+%3Fo+%7D&format=application%2Frdf%2Bxml");

    @Override
    protected InputStream fetchSparqlResults(URI sparqlURI, String format)
            throws MalformedURLException, IOException {
        if (sparqlURI.equals(BO_SPARQL_URI)) {
            return getClass().getResourceAsStream(
                    "/mock_replies/barack_obama_sparql_reply.rdf.xml");
        } else {
            throw new IllegalArgumentException("no mock resource for query "
                    + sparqlURI);
        }
    }

    @Override
    protected InputStream fetchSuggestions(String keywords, String type,
            int maxSuggestions) throws IOException {
        return getClass().getResourceAsStream(
                "/mock_replies/44th_US_president.xml");
    }

}
