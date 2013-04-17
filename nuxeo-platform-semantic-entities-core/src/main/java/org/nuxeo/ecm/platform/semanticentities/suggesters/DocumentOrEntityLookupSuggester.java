package org.nuxeo.ecm.platform.semanticentities.suggesters;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.ecm.platform.query.nxql.NXQLQueryBuilder;
import org.nuxeo.ecm.platform.semanticentities.LocalEntityService;
import org.nuxeo.ecm.platform.suggestbox.service.DocumentSuggestion;
import org.nuxeo.ecm.platform.suggestbox.service.Suggestion;
import org.nuxeo.ecm.platform.suggestbox.service.SuggestionContext;
import org.nuxeo.ecm.platform.suggestbox.service.SuggestionException;
import org.nuxeo.ecm.platform.suggestbox.service.suggesters.DocumentLookupSuggester;
import org.nuxeo.runtime.api.Framework;

public class DocumentOrEntityLookupSuggester extends DocumentLookupSuggester {

    // Overide default method to pass userInput twice...
    @Override
    @SuppressWarnings("unchecked")
    public List<Suggestion> suggest(String userInput, SuggestionContext context)
            throws SuggestionException {
        PageProviderService ppService = Framework.getLocalService(PageProviderService.class);
        if (ppService == null) {
            throw new SuggestionException("PageProviderService is not active");
        }
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY,
                (Serializable) context.session);
        String trimedInput = userInput.trim();
        String sanitizedInput = NXQLQueryBuilder.sanitizeFulltextInput(trimedInput);
        LocalEntityService entityService = Framework.getLocalService(LocalEntityService.class);
        String fulltextInput = sanitizedInput;
        String normalizedInput = entityService.normalizeName(sanitizedInput);
        if (trimedInput.isEmpty()) {
            return Collections.emptyList();
        }
        if (!userInput.endsWith(" ")) {
            // perform a prefix search on the last typed word
            fulltextInput += "*";
            normalizedInput += "%";
        }
        try {
            List<Suggestion> suggestions = new ArrayList<Suggestion>();
            PageProvider<DocumentModel> pp = (PageProvider<DocumentModel>) ppService.getPageProvider(
                    providerName, null, null, null, props,
                    null, new Object[] { fulltextInput, normalizedInput });
            for (DocumentModel doc : pp.getCurrentPage()) {
                suggestions.add(DocumentSuggestion.fromDocumentModel(doc));
            }
            return suggestions;
        } catch (ClientException e) {
            throw new SuggestionException(String.format(
                    "Suggester '%s' failed to perform query with input '%s'",
                    descriptor.getName(), userInput), e);
        }
    }
    
}
