package de.faustedition.structure;

import de.faustedition.FaustAuthority;
import de.faustedition.FaustURI;
import de.faustedition.xml.XMLStorage;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.resource.Finder;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Arrays;

@Component
public class StructureFinder extends Finder {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private XMLStorage xml;

	@Autowired
	private Logger logger;

	@Override
	public ServerResource find(Request request, Response response) {
		final String path = request.getResourceRef().getRelativeRef().getPath().replaceAll("^/+", "").replaceAll("/+$", "");

		logger.debug("Finding structure resource for '" + path + "'");
		final ArrayDeque<String> pathDeque = new ArrayDeque<String>(Arrays.asList(path.split("/+")));
		if (pathDeque.size() == 0) {
			return null;
		}

		FaustURI uri = new FaustURI(FaustAuthority.XML, "/structure/archival/");
		try {
			while (pathDeque.size() > 0) {
				FaustURI next = uri.resolve(pathDeque.pop());
				if (xml.isDirectory(next)) {
					uri = FaustURI.parse(next.toString() + "/");
					continue;
				}
				if (xml.isResource(next)) {
					uri = next;
					break;
				}
				return null;
			}
		} catch (IllegalArgumentException e) {
			logger.debug("Parse error while resolving structure resource for '" + path + "'", e);
			return null;
		}


		final StructureResource resource = applicationContext.getBean(StructureResource.class);
		resource.setURI(uri);
		return resource;
	}
}
