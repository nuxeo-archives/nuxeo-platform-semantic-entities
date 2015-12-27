/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 */

package org.nuxeo.ecm.platform.semanticentities.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URI;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.semanticentities.DereferencingException;
import org.nuxeo.ecm.platform.semanticentities.EntitySuggestion;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntityService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy({
// necessary for the fulltext indexer
        "org.nuxeo.ecm.core.convert.api", //
        "org.nuxeo.ecm.core.convert", //
        "org.nuxeo.ecm.core.convert.plugins", //
        // semantic entities types
        "org.nuxeo.ecm.platform.semanticentities.core", //
})
public abstract class RemoteEntityServiceTest {

    protected static final URI WIKIPEDIA_LONDON_URI = URI.create("http://en.wikipedia.org/wiki/London");

    protected static final URI DBPEDIA_LONDON_URI = URI.create("http://dbpedia.org/resource/London");

    protected static final URI DBPEDIA_BARACK_OBAMA_URI = URI.create("http://dbpedia.org/resource/Barack_Obama");

    protected static final URI DBPEDIA_MICHELLE_OBAMA_URI = URI.create("http://dbpedia.org/resource/Michelle_Obama");

    @Inject
    protected CoreSession session;

    @Inject
    RemoteEntityService service;

    @Before
    public void setUp() throws Exception {
        DocumentModel domain = session.createDocumentModel("/", "default-domain", "Folder");
        session.createDocument(domain);
        session.save();
    }

    @Test
    public void testSuggestRemoteEntity() throws IOException {
        assertTrue(service.canSuggestRemoteEntity());
        List<EntitySuggestion> suggestions = service.suggestRemoteEntity("Obama", "Person", 3);
        assertNotNull(suggestions);
        assertEquals(1, suggestions.size());

        EntitySuggestion suggested = suggestions.get(0);
        assertEquals("Barack Obama", suggested.label);
        assertEquals(DBPEDIA_BARACK_OBAMA_URI.toString(), suggested.getRemoteUri());
        assertEquals("Person", suggested.type);
        assertFalse(suggested.isLocal());

        // this should also work for a null type
        suggestions = service.suggestRemoteEntity("Obama", null, 3);
        assertNotNull(suggestions);
        assertEquals(1, suggestions.size());

        suggested = suggestions.get(0);
        assertEquals("Barack Obama", suggested.label);
        assertEquals(DBPEDIA_BARACK_OBAMA_URI.toString(), suggested.getRemoteUri());
        assertEquals("Person", suggested.type);
        assertFalse(suggested.isLocal());

        // however no place should match this name
        suggestions = service.suggestRemoteEntity("Obama", "Place", 3);
        assertNotNull(suggestions);
        assertEquals(0, suggestions.size());
    }

    @Test
    public void testCanDereferenceRemoteEntity() throws Exception {
        assertTrue(service.canDereference(DBPEDIA_LONDON_URI));
        assertFalse(service.canDereference(WIKIPEDIA_LONDON_URI));
    }

    @Test
    public void testGetAdmissibleTypes() throws Exception {
        Set<String> admissibleTypes = service.getAdmissibleTypes(DBPEDIA_BARACK_OBAMA_URI);
        assertNotNull(admissibleTypes);
        assertEquals("Person", StringUtils.join(admissibleTypes, ", "));

        admissibleTypes = service.getAdmissibleTypes(WIKIPEDIA_LONDON_URI);
        assertNotNull(admissibleTypes);
        assertEquals(0, admissibleTypes.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDerefenceRemoteEntity() throws Exception {
        DocumentModel barackDoc = session.createDocumentModel("Person");
        service.dereferenceInto(barackDoc, DBPEDIA_BARACK_OBAMA_URI, true, false);

        // the title and birth date are fetched from the remote entity
        // description
        assertEquals("Barack Obama", barackDoc.getTitle());

        String summary = barackDoc.getProperty("entity:summary").getValue(String.class);
        String expectedSummary = "Barack Hussein Obama II is the 44th and current President of the United States.";
        assertEquals(expectedSummary, summary.substring(0, expectedSummary.length()));

        List<String> altnames = barackDoc.getProperty("entity:altnames").getValue(List.class);
        assertEquals(4, altnames.size());
        // Western spelling:
        assertTrue(altnames.contains("Barack Obama"));
        // Russian spelling:
        assertTrue(altnames.contains("\u041e\u0431\u0430\u043c\u0430, \u0411\u0430\u0440\u0430\u043a"));

        Calendar birthDate = barackDoc.getProperty("person:birthDate").getValue(Calendar.class);

        TimeZone tz = TimeZone.getTimeZone("ECT");
        DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG, Locale.US);
        formatter.setTimeZone(tz);
        assertEquals("August 4, 1961 1:00:00 AM CET", formatter.format(birthDate.getTime()));

        Blob depiction = barackDoc.getProperty("entity:depiction").getValue(Blob.class);
        assertNotNull(depiction);
        assertEquals("200px-Official_portrait_of_Barack_Obama.jpg", depiction.getFilename());
        assertEquals(14748, depiction.getLength());

        List<String> sameas = barackDoc.getProperty("entity:sameas").getValue(List.class);
        assertTrue(sameas.contains(DBPEDIA_BARACK_OBAMA_URI.toString()));

        // check that further dereferencing with override == false does not
        // erase local changes
        barackDoc.setPropertyValue("dc:title", "B. Obama");
        barackDoc.setPropertyValue("person:birthDate", null);

        service.dereferenceInto(barackDoc, DBPEDIA_BARACK_OBAMA_URI, false, false);

        assertEquals("B. Obama", barackDoc.getTitle());
        birthDate = barackDoc.getProperty("person:birthDate").getValue(Calendar.class);
        assertEquals("August 4, 1961 1:00:00 AM CET", formatter.format(birthDate.getTime()));

        // existing names are not re-added
        altnames = barackDoc.getProperty("entity:altnames").getValue(List.class);
        assertEquals(4, altnames.size());

        // later dereferencing with override == true does not preserve local
        // changes
        service.dereferenceInto(barackDoc, DBPEDIA_BARACK_OBAMA_URI, true, false);
        assertEquals("Barack Obama", barackDoc.getTitle());
    }

    @Test
    public void testDerefencingTypeConsistency() throws Exception {
        DocumentModel barackDoc = session.createDocumentModel("Organization");
        try {
            service.dereferenceInto(barackDoc, DBPEDIA_BARACK_OBAMA_URI, true, false);
            fail("should have thrown DereferencingException");
        } catch (DereferencingException e) {
            assertEquals("Remote entity 'http://dbpedia.org/resource/Barack_Obama'"
                    + " can be mapped to types: ('Person')" + " but not to 'Organization'", e.getMessage());
        }
    }
}
