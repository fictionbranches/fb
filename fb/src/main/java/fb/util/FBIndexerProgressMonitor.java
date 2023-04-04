package fb.util;

import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.hibernate.search.batchindexing.MassIndexerProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FBIndexerProgressMonitor implements MassIndexerProgressMonitor {

	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());

	private final AtomicLong documentsDoneCounter = new AtomicLong();
	private final AtomicLong totalCounter = new AtomicLong();
	private volatile long startTime;
	private final int logAfterNumberOfDocuments = 1000;
	
	public String percent() {
		return new DecimalFormat("#0.00").format(100.0 * ((double)documentsDoneCounter.get()) / ((double)totalCounter.get()));
	}

	@Override
	public void entitiesLoaded(int size) {
		// not used
	}

	@Override
	public void documentsAdded(long increment) {
		long previous = documentsDoneCounter.getAndAdd(increment);
		if (startTime == 0) {
			synchronized (this) {
				if (startTime == 0) {
					startTime = System.nanoTime();
				}
			}
		}
		/*
		 * Only log if the current increment was the one that made the counter go to a
		 * higher multiple of the period.
		 */
		long current = previous + increment;
		if ((previous / logAfterNumberOfDocuments) < (current / logAfterNumberOfDocuments)) {
			printStatusMessage(startTime, totalCounter.longValue(), current);
		}
	}

	@Override
	public void documentsBuilt(int number) {
		// not used
	}

	@Override
	public void addToTotalCount(long count) {
		totalCounter.addAndGet(count);
		LOGGER.info("Found " + count + " entities for indexing");
	}

	@Override
	public void indexingCompleted() {
		LOGGER.info("Completed indexing " + totalCounter.longValue() + " entities");
	}
	
	private void printStatusMessage(long startTime, long totalTodoCount, long doneCount) {
		long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
		LOGGER.info("Indexed " + doneCount + " entities in " + elapsedMs + " ms");
		float estimateSpeed = doneCount * 1000f / elapsedMs;
		float estimatePercentileComplete = doneCount * 100f / totalTodoCount;
		LOGGER.info("Indexing speed: " + estimateSpeed + " docs/sec; progress: " + estimatePercentileComplete + "%");
	}
}