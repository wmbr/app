package de.faustedition.reasoning;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.FileBackedOutputStream;
import de.faustedition.VerseInterval;
import de.faustedition.document.MaterialUnit;
import de.faustedition.transcript.TranscribedVerseInterval;
import edu.bath.transitivityutils.ImmutableRelation;
import edu.bath.transitivityutils.Relation;
import edu.bath.transitivityutils.Relations;
import org.hibernate.SessionFactory;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.OutputRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class InscriptionPrecedenceResource extends ServerResource {

	@Autowired
	private GraphDatabaseService graphDb;

	@Autowired
	private SessionFactory sessionFactory;

	@Autowired
	private Environment environment;

	@Override
	protected void doInit() throws ResourceException {
		super.doInit();

		final VerseInterval verseInterval = VerseInterval.fromRequestAttibutes(getRequestAttributes());
		final Multimap<String, TranscribedVerseInterval> intervalIndex = Multimaps.index(TranscribedVerseInterval.forInterval(sessionFactory.getCurrentSession(), verseInterval), new Function<TranscribedVerseInterval, String>() {

			@Override
			public String apply(@Nullable TranscribedVerseInterval input) {
				final Node materialUnitNode = graphDb.getNodeById(input.getTranscript().getMaterialUnitId());
				return MaterialUnit.forNode(materialUnitNode).toString();
			}
		});

		inscriptions = Sets.newHashSet();
		for (String sigil : Ordering.natural().immutableSortedCopy(intervalIndex.keySet())) {
			final Inscription inscription = new Inscription(sigil);
			for (TranscribedVerseInterval interval : intervalIndex.get(sigil)) {
				inscription.addInterval(interval.getStart(), interval.getEnd());
			}
			Preconditions.checkState(!inscription.isEmpty());
			inscriptions.add(inscription);

		}
		for (Inscription subject : inscriptions) {
			for (Inscription object : inscriptions) {
				if (InscriptionRelations.syntagmaticallyPrecedesByFirstLine(subject, object)) {
					syntagmaticPrecedence.relate(subject, object);
				}
				if (InscriptionRelations.exclusivelyContains(subject, object)) {
					exclusiveContainment.relate(subject, object);
				}
				if (InscriptionRelations.paradigmaticallyContains(subject, object)) {
					paradigmaticContainment.relate(subject, object);
				}
			}
		}
	}

	public Representation dot() {
		/*
		RelationPrinter.printGraph(reasoning.precedence, "pre", "black", 1, inscriptions, path + "pre.dot");
		RelationPrinter.printGraph(reasoning.exclusiveContainment, "econ", "black", 1, inscriptions, path + "econ.dot");
		RelationPrinter.printGraph(reasoning.paradigmaticContainment, "pcon", "black", 1, inscriptions, path + "pcon.dot");
		RelationPrinter.printGraph(reasoning.syntagmaticPrecedence, "syn", "black", 1, inscriptions, path + "syn.dot");

		RelationPrinter.printGraph(reasoning.contradictions(reasoning.syntagmaticPrecedence, reasoning.precedence), "syn", "red", 1, inscriptions, path + "syn_contradicting_pre.dot");

		RelationPrinter.printInscriptionCSV(RelationPrinter.orderUniverse(reasoning.precedence, inscriptions), FROM_LINE, TO_LINE, path + "gantt.csv");
		*/
		return new StringRepresentation(asDot());
	}

	@Get("svg")
	public Representation svg() throws IOException, ExecutionException, InterruptedException {
		final ExecutorService executorService = Executors.newCachedThreadPool();
		final Process tred = new ProcessBuilder(environment.getRequiredProperty("graphviz.tred.path")).start();
		final Process dot = new ProcessBuilder(environment.getRequiredProperty("graphviz.dot.path"), "-Tsvg").start();

		executorService.submit(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				InputStream dataStream = null;
				OutputStream tredStream = null;
				try {
					ByteStreams.copy(dataStream = new ByteArrayInputStream(asDot().getBytes(Charset.forName("UTF-8"))), tredStream = tred.getOutputStream());
				} finally {
					Closeables.close(dataStream, false);
					Closeables.close(tredStream, false);
				}
				return null;
			}
		});

		executorService.submit(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				InputStream tredStream = null;
				OutputStream dotStream = null;
				try {
					ByteStreams.copy(tredStream = tred.getInputStream(), dotStream = dot.getOutputStream());
				} finally {
					Closeables.close(tredStream, false);
					Closeables.close(dotStream, false);
				}
				return null;
			}
		});

		final Future<FileBackedOutputStream> dotFuture = executorService.submit(new Callable<FileBackedOutputStream>() {
			@Override
			public FileBackedOutputStream call() throws Exception {
				final FileBackedOutputStream buf = new FileBackedOutputStream(102400);
				InputStream dotStream = null;
				try {
					ByteStreams.copy(dotStream = dot.getInputStream(), buf);
				} finally {
					Closeables.close(dotStream, false);
					Closeables.close(buf, false);
				}
				return buf;
			}
		});

		Preconditions.checkState(tred.waitFor() == 0);
		Preconditions.checkState(dot.waitFor() == 0);

		final FileBackedOutputStream resultBuf = dotFuture.get();

		return new OutputRepresentation(MediaType.IMAGE_SVG) {
			@Override
			public void write(OutputStream outputStream) throws IOException {
				ByteStreams.copy(resultBuf.getSupplier(), outputStream);
				resultBuf.reset();
			}
		};

		/*
		RelationPrinter.printGraph(reasoning.precedence, "pre", "black", 1, inscriptions, path + "pre.dot");
		RelationPrinter.printGraph(reasoning.exclusiveContainment, "econ", "black", 1, inscriptions, path + "econ.dot");
		RelationPrinter.printGraph(reasoning.paradigmaticContainment, "pcon", "black", 1, inscriptions, path + "pcon.dot");
		RelationPrinter.printGraph(reasoning.syntagmaticPrecedence, "syn", "black", 1, inscriptions, path + "syn.dot");

		RelationPrinter.printGraph(reasoning.contradictions(reasoning.syntagmaticPrecedence, reasoning.precedence), "syn", "red", 1, inscriptions, path + "syn_contradicting_pre.dot");

		RelationPrinter.printInscriptionCSV(RelationPrinter.orderUniverse(reasoning.precedence, inscriptions), FROM_LINE, TO_LINE, path + "gantt.csv");
		*/
	}

	private String asDot() {
		final StringBuilder dot = new StringBuilder("digraph genetic_graph {\n");

		for (Inscription inscription : inscriptions) {
			dot.append(toLabel(inscription)).append(";\n");
		}

		dot.append(" edge [");
		dot.append(" color=").append("black");
		dot.append(" fontcolor=").append("black");
		dot.append(" weight=").append("1");
		dot.append(" ];\n");

		for (Inscription i : inscriptions) {
			for (Inscription j : inscriptions) {
				if (precedence.areRelated(i, j)) {
					final String premise = precedence.findRelevantPremise(i, j).getName();

					dot.append(toLabel(i));
					dot.append(" -> ");
					dot.append(toLabel(j));
					dot.append(" [ ");
					dot.append(" label=").append(premise);
					dot.append(" color=").append("r_syn".equals(premise) ? "grey" : "black");
					dot.append(" ];\n");
				}
			}
		}

		dot.append("}\n");
		return dot.toString();
	}

	private String toLabel(Inscription inscription) {
		return inscription.getName().replaceAll("[ ,:\\(\\)\\-\\.\\{\\}/]", "_");
	}

	private Set<Inscription> inscriptions;
	private Relation<Inscription> syntagmaticPrecedence = Relations.newTransitiveRelation();
	private Relation<Inscription> exclusiveContainment = MultimapBasedRelation.create();
	private Relation<Inscription> paradigmaticContainment = MultimapBasedRelation.create();

	@SuppressWarnings("unchecked")
	public PremiseBasedRelation<Inscription> precedence = new PremiseBasedRelation<Inscription>(
		new PremiseBasedRelation.Premise<Inscription>() {
			@Override
			public String getName() {
				return "r_econ";
			}

			@Override
			public boolean applies(Inscription i, Inscription j) {
				return exclusiveContainment.areRelated(i, j);
			}
		},
		new PremiseBasedRelation.Premise<Inscription>() {
			@Override
			public String getName() {
				return "r_pcon";
			}

			@Override
			public boolean applies(Inscription i, Inscription j) {
				return paradigmaticContainment.areRelated(j, i);
			}
		},
		new PremiseBasedRelation.Premise<Inscription>() {
			@Override
			public String getName() {
				return "r_syn";
			}

			@Override
			public boolean applies(Inscription i, Inscription j) {
				return syntagmaticPrecedence.areRelated(i, j);
			}
		}
	);

	/**
	 * @param relA
	 * @param relB is supposed to be the overriding or "stronger" relation
	 * @return A relation result where result(i, j) if relA(i,j) && relB(j,i)
	 */
	public Relation<Inscription> contradictions(ImmutableRelation<Inscription> relA, ImmutableRelation<Inscription> relB) {
		Relation<Inscription> result = MultimapBasedRelation.create();
		for (Inscription i : inscriptions)
			for (Inscription j : inscriptions) {
				if (relA.areRelated(i, j) && relB.areRelated(j, i)) {
					result.relate(i, j);
				}
			}
		return result;
	}

}