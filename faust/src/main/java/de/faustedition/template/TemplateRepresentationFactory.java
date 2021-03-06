package de.faustedition.template;

import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import de.faustedition.FaustURI;
import de.faustedition.text.TextManager;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.restlet.data.*;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class TemplateRepresentationFactory {
	private static final Logger LOG = LoggerFactory.getLogger(TemplateRepresentationFactory.class);

	private static final List<Language> SUPPORTED_LANGUAGES = Collections.unmodifiableList(//
			Lists.newArrayList(new Language("de"), new Language("en")));

	@Autowired
	private Configuration configuration;

	@Autowired
	private TextManager textManager;

	public TemplateRepresentation create(String path, ClientInfo client) throws IOException {
		return create(path, client, new HashMap<String, Object>());
	}

	public TemplateRepresentation create(String path, ClientInfo client, Map<String, Object> model) {
		path = path.replaceAll("^/+" ,"").replaceAll("/+$", "");
		final Language language = client.getPreferredLanguage(SUPPORTED_LANGUAGES);
		final Locale locale = (language == null ? Locale.GERMAN : new Locale(language.getName()));

		Template template;
		try {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Getting template for '{}' (locale: '{}')", path, locale);
			}
			template = configuration.getTemplate(path + ".ftl", locale);
			Preconditions.checkNotNull(template, "Cannot find template for " + path);
		} catch (IOException e) {
			throw Throwables.propagate(e);
		}

		model.put("roles", Lists.transform(client.getRoles(), Functions.toStringFunction()));
		
		final ResourceBundle messages = ResourceBundle.getBundle("messages", locale);
		if (LOG.isTraceEnabled()) {
			LOG.trace("Putting message resource bundle '{}' into model (requested locale '{}')", messages.getLocale(), locale);
		}
		model.put("message", messages);

		final SortedMap<String, String> textTableOfContents = new TreeMap<String, String>();
		for (Map.Entry<FaustURI, String> tocEntry : textManager.tableOfContents().entrySet()) {
			final String textUriPath = tocEntry.getKey().getPath();
			final String textName = textUriPath.substring("/text/".length(), textUriPath.length() - ".xml".length());
			textTableOfContents.put(textName, tocEntry.getValue());
		}
		model.put("textToc", textTableOfContents);

		TemplateRepresentation representation = new TemplateRepresentation(template, model, MediaType.TEXT_HTML);
		representation.setLanguages(Collections.singletonList(language));

		return representation;
	}

	public static class TextTableOfContentsEntry {
		private final String path;
		private final String title;

		public TextTableOfContentsEntry(String path, String title) {
			this.path = path;
			this.title = title;
		}

		public String getPath() {
			return path;
		}

		public String getTitle() {
			return title;
		}
	}
}
