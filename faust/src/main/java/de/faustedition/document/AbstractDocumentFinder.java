package de.faustedition.document;

import de.faustedition.FaustURI;
import de.faustedition.xml.XMLStorage;
import org.neo4j.graphdb.GraphDatabaseService;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.resource.Finder;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.ArrayDeque;
import java.util.Deque;

public abstract class AbstractDocumentFinder extends Finder {

	@Autowired
	protected ApplicationContext applicationContext;

	@Autowired
	protected XMLStorage xml;

	@Autowired
	private GraphDatabaseService db;

	@Autowired
	protected Logger logger;

	@Override
	public ServerResource find(Request request, Response response) {

		try {

			final DocumentPath docPath = getDocument(request);
			final Document document = docPath.document;
			final Deque<String> remainder = docPath.remainder;
			final Deque<String> walked = docPath.walked;

			if (document == null) {
				return null;
			}


			return getResource(document, remainder, walked);


		} catch (IllegalArgumentException e) {
			logger.debug("Parse error while resolving document resource for " +
				request.getResourceRef().getRelativeRef().getPath(), e);
			return null;
		}

	}

	protected DocumentPath getDocument(Request request) {
		final Deque<String> path = FaustURI.toPathDeque(request.getResourceRef().getRelativeRef().getPath());
		path.addFirst("document");

		logger.debug("Finding document resource for " + path);
		
		Deque<String> walked = new ArrayDeque<String>();
		final FaustURI uri = xml.walk(path, walked);

		logger.debug("Finding document for " + uri);
		return new DocumentPath(Document.findBySource(db, uri), path, walked);
	}

	protected abstract ServerResource getResource(Document document, Deque<String> postfix, Deque<String> walked);


    protected class DocumentPath {
        public DocumentPath(Document document, Deque<String> path, Deque<String> walked) {
            this.document = document;
            this.remainder = path;
            this.walked = walked;
        }

        public Document document;
        public Deque<String> remainder;
        public Deque<String> walked;
    }

}
