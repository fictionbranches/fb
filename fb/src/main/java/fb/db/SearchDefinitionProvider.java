package fb.db;

import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.standard.StandardFilterFactory;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.hibernate.search.analyzer.definition.LuceneAnalysisDefinitionProvider;
import org.hibernate.search.analyzer.definition.LuceneAnalysisDefinitionRegistryBuilder;

public class SearchDefinitionProvider implements LuceneAnalysisDefinitionProvider {

	@Override
	public void register(LuceneAnalysisDefinitionRegistryBuilder builder) {
		builder.analyzer("fbAnalyzer")
			.tokenizer(StandardTokenizerFactory.class)
			.tokenFilter(LowerCaseFilterFactory.class)
			.tokenFilter(StandardFilterFactory.class);
	}

}
