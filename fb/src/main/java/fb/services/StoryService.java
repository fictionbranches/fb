package fb.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import fb.DB;
import fb.objects.FlatEpisode;

public class StoryService {
	private static Object rootEpisodesCacheLock = new Object();
	private static LinkedHashMap<Long,FlatEpisode> rootEpisodesCache2 = new LinkedHashMap<>();
	static {
		updateRootEpisodesCache();
		Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(StoryService::updateRootEpisodesCache, 5, 5, TimeUnit.MINUTES);
	}
	
	public static void bump() {}
	
	public static List<FlatEpisode> getRootEpisodes() {
		return StoryService.rootEpisodesCache2.values().stream().toList();
	}
	
	public static FlatEpisode getRootEpisodeById(long generatedId) {
		return StoryService.rootEpisodesCache2.get(generatedId);
	}
	
	public static void updateRootEpisodesCache() {
		synchronized (rootEpisodesCacheLock) {
			LinkedHashMap<Long, FlatEpisode> newCache = new LinkedHashMap<>();
			FlatEpisode[] arr = DB.getRoots();
			for (FlatEpisode root : arr) newCache.put(root.generatedId, root);
			rootEpisodesCache2 = newCache;
		}
	}

}