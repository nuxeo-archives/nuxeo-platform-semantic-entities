/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
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

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.storage.sql.SQLRepositoryTestCase;
import org.nuxeo.ecm.core.versioning.VersioningService;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.semanticentities.Constants;
import org.nuxeo.ecm.platform.semanticentities.EntitySuggestion;
import org.nuxeo.ecm.platform.semanticentities.LocalEntityService;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceInfo;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceRelation;
import org.nuxeo.runtime.api.Framework;

public class LocalEntityServiceTest extends SQLRepositoryTestCase {

    LocalEntityService service;

    private DocumentModel john;

    private DocumentModel johndoe;

    private DocumentModel beatles;

    private DocumentModel liverpool;

    private DocumentModel doc1;

    private DocumentModel doc2;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        // necessary for the fulltext indexer
        deployBundle("org.nuxeo.ecm.core.convert.api");
        deployBundle("org.nuxeo.ecm.core.convert");
        deployBundle("org.nuxeo.ecm.core.convert.plugins");

        // semantic entities types
        deployBundle("org.nuxeo.ecm.platform.semanticentities.core");

        // CMIS query maker
        deployBundle("org.nuxeo.ecm.core.opencmis.impl");

        // initialize the session field
        openSession();
        DocumentModel domain = session.createDocumentModel("/",
                "default-domain", "Folder");
        session.createDocument(domain);
        session.save();

        service = Framework.getService(LocalEntityService.class);
        assertNotNull(service);
        makeSomeDocuments();
    }

    @Override
    public void tearDown() throws Exception {
        closeSession();
        super.tearDown();
    }

    public void makeSomeDocuments() throws ClientException {
        doc1 = session.createDocumentModel("/", "doc1", "File");
        doc1.setPropertyValue("dc:title", "A short bio for John");
        doc1.setPropertyValue(
                "dc:description",
                "John Lennon was born in Liverpool in 1940. John was a musician."
                        + " This document about John Lennon has many occurrences"
                        + " of the words 'John' and 'Lennong' hence should rank high"
                        + " for suggestions on such keywords.");
        doc1 = session.createDocument(doc1);

        doc2 = session.createDocumentModel("/", "doc2", "File");
        doc2.setPropertyValue("dc:title", "John Lennon is not a lemon");
        doc2.setPropertyValue("dc:description",
                "John Lennon is not a lemon despite his yellow submarine.");
        doc2 = session.createDocument(doc2);

        session.save(); // force write to SQL backend
        Framework.getLocalService(EventService.class).waitForAsyncCompletion();
    }

    public void makeSomeEntities() throws ClientException {
        DocumentModel container = service.getEntityContainer(session);
        assertNotNull(container);
        assertEquals(Constants.ENTITY_CONTAINER_TYPE, container.getType());

        john = session.createDocumentModel(container.getPathAsString(), null,
                "Person");
        john.setPropertyValue("dc:title", "John Lennon");
        john.setPropertyValue(
                "entity:summary",
                "John Winston Ono Lennon, MBE (9 October 1940 – 8 December 1980)"
                        + " was an English rock musician, singer-songwriter, author, and peace"
                        + " activist who gained worldwide fame as one of the founding members of"
                        + " The Beatles.");
        john.setPropertyValue(
                "entity:sameas",
                (Serializable) Arrays.asList("http://dbpedia.org/resource/John_Lennon"));
        john.setPropertyValue("entity:sameasDisplayLabel",
                (Serializable) Arrays.asList("John Lennon"));
        john.setPropertyValue(
                "entity:types",
                (Serializable) Arrays.asList("http://dbpedia.org/ontology/MusicalArtist"));
        john.setPropertyValue("person:birthDate", new GregorianCalendar(1940,
                10, 9));
        john.setPropertyValue("person:birthDate", new GregorianCalendar(1980,
                12, 8));
        john = session.createDocument(john);

        // add another john
        johndoe = session.createDocumentModel(container.getPathAsString(),
                null, "Person");
        johndoe.setPropertyValue("dc:title", "John Doe");
        johndoe = session.createDocument(johndoe);

        beatles = session.createDocumentModel(container.getPathAsString(),
                null, "Organization");
        beatles.setPropertyValue("dc:title", "The Beatles");
        beatles.setPropertyValue(
                "entity:summary",
                "The Beatles were an English rock band, formed in Liverpool in 1960"
                        + " and one of the most commercially successful and critically acclaimed"
                        + " acts in the history of popular music.");

        beatles.setPropertyValue(
                "entity:sameas",
                (Serializable) Arrays.asList("http://dbpedia.org/resource/The_Beatles"));
        beatles.setPropertyValue("entity:sameasDisplayLabel",
                (Serializable) Arrays.asList("The Beatles"));
        beatles.setPropertyValue(
                "entity:types",
                (Serializable) Arrays.asList("http://dbpedia.org/ontology/Band"));
        beatles = session.createDocument(beatles);

        liverpool = session.createDocumentModel(container.getPathAsString(),
                null, "Place");
        liverpool.setPropertyValue("dc:title", "Liverpool");
        liverpool.setPropertyValue(
                "entity:summary",
                "Liverpool is a city and metropolitan borough of Merseyside, England, along"
                        + " the eastern side of the Mersey Estuary. It was founded as a borough"
                        + " in 1207 and was granted city status in 1880.");

        liverpool.setPropertyValue(
                "entity:sameas",
                (Serializable) Arrays.asList("http://dbpedia.org/resource/Liverpool"));
        liverpool.setPropertyValue("entity:sameasDisplayLabel",
                (Serializable) Arrays.asList("http://Liverpool"));
        liverpool.setPropertyValue(
                "entity:types",
                (Serializable) Arrays.asList("http://dbpedia.org/ontology/City"));
        liverpool.setPropertyValue("place:latitude", 53.4);
        liverpool.setPropertyValue("place:longitude", -2.983);
        liverpool = session.createDocument(liverpool);
        session.save();
        Framework.getLocalService(EventService.class).waitForAsyncCompletion();
    }

    public void testCreateEntities() throws ClientException {
        makeSomeEntities();
    }

    @SuppressWarnings("unchecked")
    public void testAddOccurrences() throws Exception {
        makeSomeEntities();

        // fetch the initial john popularity for later comparison
        double pop0 = john.getProperty("entity:popularity").getValue(
                Double.class);

        OccurrenceRelation occ1 = service.addOccurrence(session, doc1.getRef(),
                john.getRef(), "John Lennon was born in Liverpool in 1940.", 0,
                11);
        assertNotNull(occ1);
        john = session.getDocument(john.getRef());
        // John Lennon is the main entity name, hence not stored in the altnames
        // field
        assertTrue(john.getProperty("entity:altnames").getValue(List.class).isEmpty());

        // check the increase in popularity
        double pop1 = john.getProperty("entity:popularity").getValue(
                Double.class);
        assertTrue(pop1 > pop0);

        OccurrenceRelation occ2 = service.addOccurrence(session, doc1.getRef(),
                john.getRef(), "John was a musician.", 0, 4);
        assertNotNull(occ2);
        john = session.getDocument(john.getRef());
        // John is not the exact main entity name, hence stored in the altnames
        // field of the entity to improve fulltext lookup quality of the entity
        // in the future
        assertEquals(new ArrayList<String>(
                john.getProperty("entity:altnames").getValue(List.class)),
                Arrays.asList("John"));
        // check the popularity is still the same since this is an occurrence
        // from the same document
        double pop2 = john.getProperty("entity:popularity").getValue(
                Double.class);
        assertTrue(pop2 == pop1);

        assertEquals(occ1.getOccurrenceDocument().getRef(),
                occ2.getOccurrenceDocument().getRef());
        List<OccurrenceInfo> occurrences = occ2.getOccurrences();
        assertEquals(2, occurrences.size());
        assertEquals("John Lennon", occurrences.get(0).mention);
        assertEquals(0, occurrences.get(0).startPosInContext);
        assertEquals(11, occurrences.get(0).endPosInContext);
        assertEquals("John", occurrences.get(1).mention);

        // add an occurrence from another document
        OccurrenceRelation occ3 = service.addOccurrence(session, doc2.getRef(),
                john.getRef(), "John Lennon is not a lemon.", 0, 11);

        // check the increase in popularity
        john = session.getDocument(john.getRef());
        double pop3 = john.getProperty("entity:popularity").getValue(
                Double.class);
        assertTrue(pop3 > pop2);

        assertNotNull(occ3);
        assertEquals(1, occ3.getOccurrences().size());
        assertEquals("John Lennon", occ3.getOccurrences().get(0).mention);
        assertEquals(0, occ3.getOccurrences().get(0).startPosInContext);
        assertEquals(11, occ3.getOccurrences().get(0).endPosInContext);
    }

    public void testGetRelatedDocumentsAndEntities() throws Exception {
        // create some entities in the KB and an unrelated document
        makeSomeEntities();

        PageProvider<DocumentModel> johnDocs = service.getRelatedDocuments(
                session, john.getRef(), null);
        assertEquals(0L, johnDocs.getCurrentPage().size());

        PageProvider<DocumentModel> doc1Entities = service.getRelatedEntities(
                session, doc1.getRef(), null);
        assertEquals(0L, doc1Entities.getCurrentPage().size());

        // add a relation between John and his bio
        service.addOccurrence(session, doc1.getRef(), john.getRef(),
                "John Lennon was born in Liverpool in 1940.", 0, 11);

        johnDocs = service.getRelatedDocuments(session, john.getRef(), null);
        assertEquals(1L, johnDocs.getCurrentPage().size());
        DocumentModel doc0 = johnDocs.getCurrentPage().get(0);
        assertEquals("A short bio for John", doc0.getTitle());

        doc1Entities = service.getRelatedEntities(session, doc1.getRef(), null);
        assertEquals(1L, doc1Entities.getCurrentPage().size());
        DocumentModel ent0 = doc1Entities.getCurrentPage().get(0);
        assertEquals("John Lennon", ent0.getPropertyValue("dc:title"));

        // add a relation between Liverpool and John's bio
        service.addOccurrence(session, doc1.getRef(), liverpool.getRef(),
                "John was born in Liverpool in 1940.", 17, 26);

        // John still has only one related document, and Liverpool too
        johnDocs = service.getRelatedDocuments(session, john.getRef(), null);
        assertEquals(1L, johnDocs.getCurrentPage().size());
        doc0 = johnDocs.getCurrentPage().get(0);
        assertEquals("A short bio for John", doc0.getTitle());

        PageProvider<DocumentModel> liverpoolDocs = service.getRelatedDocuments(
                session, liverpool.getRef(), null);
        assertEquals(1L, liverpoolDocs.getCurrentPage().size());
        doc0 = liverpoolDocs.getCurrentPage().get(0);
        assertEquals("A short bio for John", doc0.getTitle());

        // the bio is hence now related to two entities
        doc1Entities = service.getRelatedEntities(session, doc1.getRef(), null);
        assertEquals(2L, doc1Entities.getCurrentPage().size());
        ent0 = doc1Entities.getCurrentPage().get(0);
        assertEquals("John Lennon", ent0.getPropertyValue("dc:title"));
        DocumentModel ent1 = doc1Entities.getCurrentPage().get(1);
        assertEquals("Liverpool", ent1.getPropertyValue("dc:title"));

        // We can restrict the entities to lookup by type
        doc1Entities = service.getRelatedEntities(session, doc1.getRef(),
                "Person");
        assertEquals(1L, doc1Entities.getCurrentPage().size());
        ent0 = doc1Entities.getCurrentPage().get(0);
        assertEquals("John Lennon", ent0.getPropertyValue("dc:title"));

        doc1Entities = service.getRelatedEntities(session, doc1.getRef(),
                "Place");
        assertEquals(1L, doc1Entities.getCurrentPage().size());
        ent0 = doc1Entities.getCurrentPage().get(0);
        assertEquals("Liverpool", ent1.getPropertyValue("dc:title"));
    }

    public void testSuggestLocalEntitiesEmptyKB() throws ClientException {
        List<EntitySuggestion> suggestions = service.suggestLocalEntity(session,
                "John", null, 3);
        assertTrue(suggestions.isEmpty());
    }

    public void testSuggestLocalEntities() throws ClientException {
        makeSomeEntities();

        List<EntitySuggestion> suggestions = service.suggestLocalEntity(session,
                "John", "Person", 3);
        assertEquals(2, suggestions.size());
        // by default the popularities are identical hence the ordering is
        // undefined
        List<DocumentModel> expectedEntities = Arrays.asList(john, johndoe);
        assertTrue(expectedEntities.contains(suggestions.get(0).entity));
        assertTrue(expectedEntities.contains(suggestions.get(1).entity));

        suggestions = service.suggestLocalEntity(session, "Lennon John",
                "Person", 3);
        assertEquals(1, suggestions.size());
        assertEquals(john, suggestions.get(0).entity);

        // make Lennon more popular my adding occurrences pointing to him
        service.addOccurrence(session, doc1.getRef(), john.getRef(),
                "John Lennon was born in Liverpool in 1940.", 0, 11);

        // Lennon is now the top person for the "John" query
        suggestions = service.suggestLocalEntity(session, "John", "Person", 3);
        assertEquals(2, suggestions.size());
        assertEquals(john, suggestions.get(0).entity);
        assertEquals(johndoe, suggestions.get(1).entity);

        // create a new version for Lennon
        john.putContextData(VersioningService.VERSIONING_OPTION,
                VersioningOption.MAJOR);
        john = session.saveDocument(john);
        session.save();

        // We should still get only two suggestions (the archived versions are
        // not suggested)
        suggestions = service.suggestLocalEntity(session, "John", "Person", 3);
        assertEquals(2, suggestions.size());
        assertEquals(john, suggestions.get(0).entity);
        assertEquals(johndoe, suggestions.get(1).entity);

        // delete the john entity (using the trash)
        session.followTransition(john.getRef(), "delete");
        session.save();

        // We only get non-deleted live entities as suggestion
        suggestions = service.suggestLocalEntity(session, "John", "Person", 3);
        assertEquals(1, suggestions.size());
        assertEquals(johndoe, suggestions.get(0).entity);

        session.followTransition(john.getRef(), "undelete");
        session.save();
        suggestions = service.suggestLocalEntity(session, "John", "Person", 3);
        assertEquals(2, suggestions.size());
        assertEquals(john, suggestions.get(0).entity);
        assertEquals(johndoe, suggestions.get(1).entity);
    }

    public void testSuggestEntities() throws Exception {
        // deploy off-line mock DBpedia source to override the default source
        // that needs an internet connection: comment the following contrib to
        // test again the real DBpedia server
        deployContrib("org.nuxeo.ecm.platform.semanticentities.core.tests",
                "OSGI-INF/test-semantic-entities-dbpedia-entity-contrib.xml");

        // empty local KB, only remote source output
        List<EntitySuggestion> suggestions = service.suggestEntity(session,
                "Barack Obama", "Person", 3);
        assertNotNull(suggestions);
        assertEquals(suggestions.size(), 1);
        EntitySuggestion firstGuess = suggestions.get(0);
        assertEquals("Barack Obama", firstGuess.label);
        assertFalse(firstGuess.isLocal());

        // synchronize the remote entity as a local entity
        DocumentModel localEntity = service.asLocalEntity(session, firstGuess);
        assertEquals(localEntity.getTitle(), "Barack Obama");

        // perform the same suggestion query again: this time the result is
        // local
        suggestions = service.suggestEntity(session, "Barack Obama", "Person",
                3);
        assertNotNull(suggestions);
        assertEquals(suggestions.size(), 1);
        firstGuess = suggestions.get(0);
        assertEquals("Barack Obama", firstGuess.label);
        assertTrue(firstGuess.isLocal());
    }

    public void testGetOccurrenceRelation() throws Exception {
        makeSomeEntities();
        OccurrenceRelation relation = service.getOccurrenceRelation(session,
                doc1.getRef(), john.getRef());
        assertNull(relation);

        List<OccurrenceInfo> mentions = Arrays.asList(new OccurrenceInfo(
                "John Lennon", "John Lennon was born in Liverpool in 1940."),
                new OccurrenceInfo("John", "John was a musician."));
        service.addOccurrences(session, doc1.getRef(), john.getRef(), mentions);

        relation = service.getOccurrenceRelation(session, doc1.getRef(),
                john.getRef());
        assertNotNull(relation);
        assertEquals(doc1.getRef(), relation.getSourceDocumentRef());
        assertEquals(john.getRef(), relation.getTargetEntityRef());
        assertEquals(mentions, relation.getOccurrences());
    }

    public void testAddOccurrenceRelationWithEmptyOccurrenceData()
            throws Exception {
        makeSomeEntities();
        OccurrenceRelation relation = service.getOccurrenceRelation(session,
                doc1.getRef(), john.getRef());
        assertNull(relation);

        service.addOccurrences(session, doc1.getRef(), john.getRef(), null);

        relation = service.getOccurrenceRelation(session, doc1.getRef(),
                john.getRef());
        assertNotNull(relation);
        assertEquals(doc1.getRef(), relation.getSourceDocumentRef());
        assertEquals(john.getRef(), relation.getTargetEntityRef());
        assertEquals(Arrays.asList(), relation.getOccurrences());
    }

    public void testSuggestDocument() throws Exception {
        List<DocumentModel> suggestions = service.suggestDocument(session,
                "lemon", null, 3);
        assertNotNull(suggestions);
        assertEquals(1, suggestions.size());
        assertEquals(doc2.getRef(), suggestions.get(0).getRef());

        suggestions = service.suggestDocument(session, "Lennon John", null, 3);
        assertNotNull(suggestions);
        assertEquals(2, suggestions.size());
        assertEquals(doc1.getRef(), suggestions.get(0).getRef());
        assertEquals(doc2.getRef(), suggestions.get(1).getRef());

        // check that entities don't show up in the results
        makeSomeEntities();
        suggestions = service.suggestDocument(session, "Lennon John", null, 3);
        assertNotNull(suggestions);
        assertEquals(2, suggestions.size());
        assertEquals(doc1.getRef(), suggestions.get(0).getRef());
        assertEquals(doc2.getRef(), suggestions.get(1).getRef());
    }

    public void testGetLinkedLocalEntity() throws Exception {
        URI johnURI = URI.create("http://dbpedia.org/resource/John_Lennon");

        // empty KB will not yield any match
        DocumentModel linkedEntity = service.getLinkedLocalEntity(session,
                johnURI);
        assertNull(linkedEntity);

        makeSomeEntities();

        // the service can find john since it's properly linked
        linkedEntity = service.getLinkedLocalEntity(session, johnURI);
        assertNotNull(linkedEntity);
        assertEquals(john.getRef(), linkedEntity.getRef());

    }

    public void testCleanupKeywords() {
        assertEquals("This is a test",
                LocalEntityServiceImpl.cleanupKeywords("This is. a\n test?"));
        assertEquals("a b", LocalEntityServiceImpl.cleanupKeywords("a'.;,<>b"));
    }

}
