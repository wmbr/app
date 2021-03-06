package de.faustedition.text;

import com.google.common.base.Strings;
import com.google.common.io.Closeables;
import de.faustedition.FaustAuthority;
import de.faustedition.FaustURI;
import de.faustedition.Runtime;
import de.faustedition.graph.FaustGraph;
import de.faustedition.tei.WhitespaceUtil;
import de.faustedition.xml.*;
import eu.interedition.text.Name;
import eu.interedition.text.util.SimpleXMLTransformerConfiguration;
import eu.interedition.text.xml.XML;
import eu.interedition.text.xml.XMLTransformer;
import eu.interedition.text.xml.XMLTransformerModule;
import eu.interedition.text.xml.module.*;
import org.codehaus.stax2.XMLInputFactory2;
import org.goddag4j.Element;
import org.goddag4j.io.GoddagXMLReader;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static de.faustedition.xml.Namespaces.TEI_NS_URI;
import static eu.interedition.text.TextConstants.TEI_NS;

@Component
public class TextManager extends Runtime implements Runnable {

	private final XMLInputFactory2 xmlInputFactory = XML.createXMLInputFactory();

	@Autowired
	private Logger logger;

	@Autowired
	private FaustGraph graph;

	@Autowired
	private GraphDatabaseService db;

	@Autowired
	private XMLStorage xml;

	@Autowired
	private SessionFactory sessionFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private SortedMap<FaustURI, String> tableOfContents;

	@Transactional
	public Set<FaustURI> feedDatabase() {
		final SimpleXMLTransformerConfiguration conf = new SimpleXMLTransformerConfiguration();

		final List<XMLTransformerModule> modules = conf.getModules();
		modules.add(new LineElementXMLTransformerModule());
		modules.add(new NotableCharacterXMLTransformerModule());
		modules.add(new TextXMLTransformerModule());
		modules.add(new DefaultAnnotationXMLTransformerModule(1000, true));
		modules.add(new CLIXAnnotationXMLTransformerModule(1000));
		modules.add(new TEIAwareAnnotationXMLTransformerModule(1000));

		conf.addLineElement(new Name(TEI_NS, "div"));
		conf.addLineElement(new Name(TEI_NS, "head"));
		conf.addLineElement(new Name(TEI_NS, "sp"));
		conf.addLineElement(new Name(TEI_NS, "stage"));
		conf.addLineElement(new Name(TEI_NS, "speaker"));
		conf.addLineElement(new Name(TEI_NS, "lg"));
		conf.addLineElement(new Name(TEI_NS, "l"));
		conf.addLineElement(new Name(TEI_NS, "p"));
		conf.addLineElement(new Name(null, "line"));

		conf.addContainerElement(new Name(TEI_NS, "text"));
		conf.addContainerElement(new Name(TEI_NS, "div"));
		conf.addContainerElement(new Name(TEI_NS, "lg"));
		conf.addContainerElement(new Name(TEI_NS, "subst"));
		conf.addContainerElement(new Name(TEI_NS, "choice"));

		conf.exclude(new Name(TEI_NS, "teiHeader"));
		conf.exclude(new Name(TEI_NS, "front"));
		conf.exclude(new Name(TEI_NS, "fw"));
		conf.exclude(new Name(TEI_NS, "app"));

		conf.include(new Name(TEI_NS, "lem"));

		final Session session = sessionFactory.getCurrentSession();
		final XMLTransformer xmlTransformer = new XMLTransformer(session, conf);

		final Set<FaustURI> failed = new HashSet<FaustURI>();
		logger.info("Importing texts");
		for (FaustURI textSource : xml.iterate(new FaustURI(FaustAuthority.XML, "/text"))) {
			InputStream xmlStream = null;
			try {
				logger.info("Importing text " + textSource);
				xmlStream = xml.getInputSource(textSource).getByteStream();
				final eu.interedition.text.Text xmlText = eu.interedition.text.Text.create(session, null, xmlInputFactory.createXMLStreamReader(xmlStream));
				final eu.interedition.text.Text text = xmlTransformer.transform(xmlText);
			} catch (IOException e) {
				logger.error("I/O error while adding text " + textSource, e);
				failed.add(textSource);
			} catch (XMLStreamException e) {
				logger.error("XML error while adding text " + textSource, e);
				failed.add(textSource);
			} finally {
				Closeables.closeQuietly(xmlStream);
			}
		}
		return failed;
	}

	public Set<FaustURI> feedGraph() {
		final Set<FaustURI> failed = new HashSet<FaustURI>();
		logger.info("Importing texts");
		for (final FaustURI textSource : xml.iterate(new FaustURI(FaustAuthority.XML, "/text"))) {
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    try {
                        logger.info("Importing text " + textSource);
                        add(textSource);
                    } catch (SAXException e) {
                        logger.error("XML error while adding text " + textSource, e);
                        failed.add(textSource);
                    } catch (IOException e) {
                        logger.error("I/O error while adding text " + textSource, e);
                        failed.add(textSource);
                    } catch (TransformerException e) {
                        logger.error("XML error while adding text " + textSource, e);
                        failed.add(textSource);
                    }
                }
            });
		}
		return failed;
	}

	@Override
	public void run() {
		feedGraph();
	}

	public Text add(FaustURI source) throws SAXException, IOException, TransformerException {
		if (logger.isDebugEnabled()) {
			logger.debug("Adding text from " + source);
		}

		final Document document = XMLUtil.parse(xml.getInputSource(source));
		WhitespaceUtil.normalize(document);
		document.normalizeDocument();

		final GoddagXMLReader textHandler = new GoddagXMLReader(db, CustomNamespaceMap.INSTANCE);
		final TextTitleCollector titleCollector = new TextTitleCollector();
		final ContentHandler multiplexer = new MultiplexingContentHandler(textHandler, titleCollector);
		
		final XMLFragmentFilter textFragmentFilter = new XMLFragmentFilter(multiplexer, TEI_NS_URI, "text");

		Text text = null;
		final Transaction tx = db.beginTx();
		try {
			XMLUtil.transformerFactory().newTransformer().transform(new DOMSource(document), new SAXResult(textFragmentFilter));

			final Element textRoot = textHandler.result();
			if (textRoot != null) {
				text = new Text(db, source, Strings.nullToEmpty(titleCollector.getTitle()), textRoot);
				register(text, source);
			}
			tx.success();
		} finally {
			tx.finish();
		}

		if (text != null) {
			text.tokenize();
		}
		
		synchronized (this) {
			tableOfContents = null;
		}
		
		return text;
	}

	protected void register(Text text, FaustURI source) {
		graph.getTexts().add(text);
		db.index().forNodes(Text.class.getName()).add(text.node, Text.SOURCE_KEY, source.toString());
	}

	public Text find(FaustURI source) {
		final Node node = db.index().forNodes(Text.class.getName()).get(Text.SOURCE_KEY, source.toString()).getSingle();
		return node == null ? null : new Text(node);
	}

	public synchronized SortedMap<FaustURI, String> tableOfContents() {
		if (tableOfContents == null) {
			tableOfContents = new TreeMap<FaustURI, String>();
			Transaction tx = db.beginTx();
			try {
				for (Text t : graph.getTexts()) {
					tableOfContents.put(t.getSource(), t.getTitle());
				}
				tx.success();
			} finally {
				tx.finish();
			}
		}
		return tableOfContents;
	}

	private static class TextTitleCollector extends DefaultHandler {
		private StringBuilder titleBuf;
		private String title;

		public String getTitle() {
			return title;
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			if (title != null) {
				return;
			}
			if ("head".equals(localName) && TEI_NS_URI.equals(uri)) {
				titleBuf = new StringBuilder();
			}

		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			if (title != null) {
				return;
			}
			if ("head".equals(localName) && TEI_NS_URI.equals(uri)) {
				this.title = titleBuf.toString().replaceAll("\\s+", " ").trim();
				titleBuf = null;
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			if (titleBuf != null) {
				titleBuf.append(ch, start, length);
			}
		}
	}
	
	public static void main(String[] args) throws IOException {
		main(TextManager.class, args);
	}
}
