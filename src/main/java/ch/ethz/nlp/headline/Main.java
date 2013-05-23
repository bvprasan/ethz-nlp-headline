package ch.ethz.nlp.headline;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.ethz.nlp.headline.duc2004.Duc2004Dataset;
import ch.ethz.nlp.headline.generators.BaselineGenerator;
import ch.ethz.nlp.headline.generators.CoreNLPGenerator;
import ch.ethz.nlp.headline.generators.Generator;
import ch.ethz.nlp.headline.generators.HedgeTrimmerGenerator;
import ch.ethz.nlp.headline.selection.TfIdfProvider;
import ch.ethz.nlp.headline.visualization.PeerInspector;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;

public class Main {

	private static final Logger LOG = LoggerFactory.getLogger(Main.class);
	private static final String EVALUATION_CONFIG_FILENAME = "evaluation.conf";

	public static void main(String[] args) throws ClassNotFoundException,
			IOException {
		Config config = new Config();
		Dataset dataset = Duc2004Dataset.ofDefaultRoot();
		List<Task> tasks = dataset.getTasks();

		TfIdfProvider tfIdfProvider = TfIdfProvider.of(dataset);
		List<CoreNLPGenerator> generators = new ArrayList<>();
		generators.add(new BaselineGenerator());
		// generators.add(new PosFilteredGenerator());
		// generators.add(new CombinedSentenceGenerator(tfIdfProvider));
		generators.add(new HedgeTrimmerGenerator(tfIdfProvider));

		Multimap<Task, Peer> peersMap = LinkedListMultimap.create();
		PeerInspector peerInspector = new PeerInspector();

		for (int i = 0; i < tasks.size(); i++) {
			Task task = tasks.get(i);
			Document document = task.getDocument();
			String documentId = document.getId().toString();

			if (config.getFilterDocumentId().isPresent()
					&& !documentId.equals(config.getFilterDocumentId().get())) {
				continue;
			}

			LOG.info(String.format("Processing task %d of %d: %s", i + 1,
					tasks.size(), documentId));

			for (Generator generator : generators) {
				String content = document.getContent();
				String headline = generator.generate(content);
				Peer peer = dataset.makePeer(task, generator.getId());
				try {
					peer.store(headline);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(-1);
				}
				peersMap.put(task, peer);
			}

			peerInspector.inspect(task, peersMap.get(task));
		}

		for (CoreNLPGenerator generator : generators) {
			LOG.info(generator.getStatistics().toString());
		}

		EvaluationConfig evaluationConfig = new EvaluationConfig(dataset);
		Path configPath = FileSystems.getDefault().getPath(
				EVALUATION_CONFIG_FILENAME);
		evaluationConfig.write(configPath, peersMap);
	}

}
