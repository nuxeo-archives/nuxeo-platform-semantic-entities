package org.nuxeo.ecm.platform.semanticentities.service;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

public class MockSemanticAnalysisServiceImpl extends
        SemanticAnalysisServiceImpl {

    @Override
    public String callSemanticEngine(String textContent, String outputFormat,
            int retry) throws IOException {
        InputStream is = getClass().getResourceAsStream(
                "/mock_replies/engine-output.rdf.xml");
        return IOUtils.toString(is, "UTF-8");
    }

}
