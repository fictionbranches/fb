package fb;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCrypt;

import fb.api.AccountStuff;
import fb.api.AddStuff;
import fb.api.AdminStuff;
import fb.api.CharsetResponseFilter;
import fb.api.DevStuff;
import fb.api.GetStuff;
import fb.api.JSONStuff;
import fb.api.LegacyStuff;
import fb.api.MyErrorPageGenerator;
import fb.api.MyResponseFilter;
import fb.api.NotFoundExceptionMapper;
import fb.api.RssStuff;
import fb.objects.FlatEpisode;
import fb.util.FBIndexerProgressMonitor;
import fb.util.Markdown;
import fb.util.Strings;
import fb.util.Text;
import jakarta.ws.rs.core.UriBuilder;

public class InitWebsite {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	/**
	 * Where all the stuff is.
	 * 
	 * (Relative path will be relative to working directory when program is run)
	 * 
	 * no trailing /
	 */
	public static final String BASE_DIR = "fbstuff";
	
	public static final DevLevel DEV_LEVEL = getDevLevel();
	
	/**
	 * set this value to the default (will revert to this value after restarts)
	 */
	public static boolean READ_ONLY_MODE = false;//NOSONAR
	/**
	 * Gets set to false if no search indexes are detected, then back to true after search indexes have been built
	 */
	public static boolean SEARCHING_ALLOWED = true;//NOSONAR
	public static FBIndexerProgressMonitor INDEXER_MONITOR = null;//NOSONAR
	
	/**
	 * Increment each time search index definitions need to be updated
	 */
	public static final int SEARCH_INDEX_VERSION = 1;
	
	/**
	 * true to check recaptchas
	 */
	public static final boolean RECAPTCHA = true;

	@SuppressWarnings("squid:S106")
	public static void main(String[] args) {
		if (args.length == 0) runServer();
		else if (args[0].trim().length() == 0) runServer();
		else switch (args[0].trim().toLowerCase()) {
			case "run":
				runServer();
				break;
			case "salttest":
				saltTest();
				DB.closeSessionFactory();
				System.exit(0);
				break;
			case "firstrun":
				InitDB.cleanStart();
				break;
			default:
				System.err.println("Unknown argument: " + args[0] + " (" + args[0].length() + ")");
				usage();
			}
	}
	
	@SuppressWarnings("squid:S106")
	private static void usage() {
		System.err.println("USAGE: (run | firstrun | salttest)");
		System.err.println("If no option is specified, run is default");
		System.exit(1);
	}
	
	private static void runServer() {
		HttpServer server;
		{
			Thread searchIndexer = checkBaseDirAndIndexes();
			LOGGER.info("Started. Connecting to postgres"); // This line also starts the file watcher threads
			checkDatabase();
		
			Accounts.bump(); // Force temp accounts to be loaded and account cleaner thread to start
		
			Runtime.getRuntime().addShutdownHook(new Thread(()->{
				Accounts.writeSessionsToFile();
				DB.closeSessionFactory();
			}));
			
			
			if (searchIndexer != null) {
				LOGGER.warn("Starting search indexer");
				searchIndexer.start();
				LOGGER.warn("Search indexer started");
			}
			
			Thread jsEngineStarter = new Thread(()->Markdown.formatBody("This call inits the js engine"));
			jsEngineStarter.setName("jsEngineStarter");
			jsEngineStarter.start();
			
			LOGGER.info("Starting server");
			
			int port; try {
				port = Integer.parseInt(Strings.getBACKEND_PORT());
			} catch (Exception e) {
				port = 8080;
			}
			
			server = GrizzlyHttpServerFactory.createHttpServer(
					UriBuilder.fromUri("https://0.0.0.0/").port(port).build(), jaxrsConfig(), true,
					new SSLEngineConfigurator(sslConfig()).setClientMode(false), false);

			setupTCPNIOTransport(server);

			enableExceptionLogging();

			// Enable custom error pages
			server.getServerConfiguration().setDefaultErrorPageGenerator(new MyErrorPageGenerator());
		}

		try {
			server.start();
		} catch (IOException e) {
			LOGGER.error("Failed to start server", e);
			System.exit(26);
			throw new RuntimeException(e);
		}
		LOGGER.info("Server started");
		new Thread(() -> RssStuff.ping()).start();
	}
	
	private static void checkDatabase() {
		LOGGER.info("Checking database");
		for (FlatEpisode rootEp : DB.getRoots()) {
			LOGGER.info("Found root episode: " + rootEp.generatedId + " " + rootEp.link);
		}
		LOGGER.info("Postgres connected successfully");
	}
	
	private static Thread checkBaseDirAndIndexes() {
		LOGGER.info("Checking base dir");
		File baseDir = new File(InitWebsite.BASE_DIR);
		if (baseDir.exists()) {
			if (!baseDir.isDirectory()) {
				bye("Base dir " + baseDir.getAbsolutePath() + " could not be created, file with same name exists");
			}
		} else if (!(new File(InitWebsite.BASE_DIR).mkdirs())) {
			bye("Base dir " + baseDir.getAbsolutePath() + " does not exist and could not be created");
		}
		File indexDir = new File(InitWebsite.BASE_DIR + "/search-indexes");
		File indexVersion = new File(InitWebsite.BASE_DIR + "/search-indexes-version");
		if (indexDir.exists() && indexDir.isFile()) {
			bye("Search index directory " + indexDir.getAbsolutePath() + " is a file");
		}
				
		if (!indexDir.exists() || indexDir.list().length==0 || !indexVersion.exists() || !indexVersion.isFile()) {
			return redoSearchIndexes(indexDir, indexVersion);
		}
		
		if (!indexVersion.isFile()) {
			bye("Search index version " + indexVersion.getAbsolutePath() + " is not a file");
		}
		
		int currentVersion;
		try {
			currentVersion = Integer.parseInt(Text.readTextFile(indexVersion));
		} catch (Exception e) {
			currentVersion = -1;
		}
		if (currentVersion < InitWebsite.SEARCH_INDEX_VERSION) {
			return redoSearchIndexes(indexDir, indexVersion);
		}
		
		LOGGER.info("Finished check base dir");
		return null;
	}
	
	private static Thread redoSearchIndexes(File indexDir, File indexVersion) {
		InitWebsite.SEARCHING_ALLOWED = false;
		
		if (indexDir.exists()) {
			int num = 0;
			
			File oldIndexDir;
			do {
				++num;
				oldIndexDir = new File(InitWebsite.BASE_DIR + "/search-indexes-" + num);
			} while (oldIndexDir.exists());
			
			indexDir.renameTo(oldIndexDir);
		}
		
		if (indexVersion.exists()) {
			indexVersion.delete();
		}
				
		Thread t = new Thread(()->{
			DB.doSearchIndex();
			try (BufferedWriter out = new BufferedWriter(new FileWriter(indexVersion))) {
				out.write(String.valueOf(InitWebsite.SEARCH_INDEX_VERSION));
			} catch (IOException e) {
				bye("Failed to write search index version: " + e.getMessage());
			}
			InitWebsite.SEARCHING_ALLOWED = true;
		});
		t.setName("SearchIndexerThread");
		LOGGER.warn("Started search indexer thread");
		return t;
	}
	
	private static void bye(String msg) {
		LOGGER.error(msg);
		DB.closeSessionFactory();
		System.exit(24);
		throw new RuntimeException(msg);
	}
	
	private static ResourceConfig jaxrsConfig() {
		ArrayList<Class<?>> list = Stream.of(AccountStuff.class, AddStuff.class,
				AdminStuff.class, GetStuff.class, LegacyStuff.class, RssStuff.class, JSONStuff.class).collect(Collectors.toCollection(ArrayList::new));
		if (DEV_LEVEL == DevLevel.DEV || DEV_LEVEL == DevLevel.SUPER_DEV) {
			LOGGER.info("Running in DEV_MODE");
			list.add(DevStuff.class);
			if (DEV_LEVEL == DevLevel.SUPER_DEV) {
				list.add(DevStuff.SuperDevStuff.class);
			}
		}
		ResourceConfig resourceConfig = new ResourceConfig(list.toArray(new Class<?>[0]));
		resourceConfig.register(CharsetResponseFilter.class);
		resourceConfig.register(NotFoundExceptionMapper.class);
		resourceConfig.register(MyResponseFilter.class);
		return resourceConfig;
	}
	
	private static SSLContextConfigurator sslConfig() {
		byte[] keystore;
		try {
			InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("asdf.jks");
			ArrayList<Byte> keyList = new ArrayList<>();
			byte[] buff = new byte[4096];
			while (true) {
				int bytes = in.read(buff);
				if (bytes <= 0) break;
				else for (int i=0; i<bytes; ++i) keyList.add(buff[i]);
			}
			keystore = new byte[keyList.size()];
			for (int i=0; i<keyList.size(); ++i) keystore[i] = keyList.get(i);
			
			LOGGER.info("Finished reading " + keystore.length + " bytes into the keystore");
		} catch (IOException e) {
			//never happens
			LOGGER.error("This should never happen", e);
			System.exit(25);
			throw new RuntimeException(e);
		}
		
		SSLContextConfigurator ssl = new SSLContextConfigurator();
		ssl.setKeyStoreBytes(keystore);
		ssl.setKeyStorePass("password");
		return ssl;
	}
	
	private static void setupTCPNIOTransport(HttpServer server) {
		TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
				//.setTcpNoDelay(true)
				.setIOStrategy(WorkerThreadIOStrategy.getInstance())
				.setWorkerThreadPoolConfig(ThreadPoolConfig.defaultConfig()
					.setCorePoolSize(5)
					.setMaxPoolSize(1024)
					.setQueueLimit(4096)
					.setTransactionTimeout(30, TimeUnit.SECONDS)
				)
				.build();
		
		
		for (NetworkListener nl : server.getListeners()) {
			LOGGER.info("Set transport for listener: " + nl);
			nl.setTransport(transport);
		}		
	}
	
	private static void enableExceptionLogging() {
		// Enable exception logging
		java.util.logging.Logger l = java.util.logging.Logger.getLogger("org.glassfish.grizzly.http.server.HttpHandler");
		l.setLevel(Level.FINE);
		l.setUseParentHandlers(false);
		ConsoleHandler ch = new ConsoleHandler();
		ch.setLevel(Level.ALL);
		l.addHandler(ch);
	}
	
	private static void saltTest() {
		final int N = 10;
		final int L = 32;
		final Random r = Strings.r;
		
		ArrayList<String> list = new ArrayList<>(N);
		ArrayList<Character> chars = new ArrayList<>(10+26+26);
		for (char c='0'; c<='9'; ++c) chars.add(c);
		for (char c='a'; c<='z'; ++c) chars.add(c);
		for (char c='A'; c<='Z'; ++c) chars.add(c);
		for (int i=0; i<N; ++i) {
			StringBuilder sb = new StringBuilder(L);
			for (int j=0; j<L; ++j) sb.append(chars.get(r.nextInt(chars.size())));
			list.add(sb.toString());
		}
		DecimalFormat df = new DecimalFormat("0.000");
		for (int i=0; i<3; ++i) hashAll(7+i, list, chars, r);
		for (int i=8; i<20; ++i) {
			StringBuilder sb = new StringBuilder();
			sb.append(String.format("%2d ", i));
			double[] arr = hashAll(i, list, chars, r);
			double sum = 0.0;
			for (double d : arr) {
				sb.append(df.format(d) + " ");
				sum+=d;
			}
			LOGGER.info("Salt test result: " + sb.toString());
			if (sum/((double)arr.length) > 0.3) break;
		}
	}
	private static double[] hashAll(int d, ArrayList<String> list, ArrayList<Character> chars, Random r) {
		if (list.isEmpty()) return new double[] {0.0};
		double sumHash = 0.0;
		double sumCheckYes = 0.0;
		double sumCheckNo = 0.0;
		long start;
		long stop;
		for (String s : list) {
			start = System.nanoTime();
			String hash = BCrypt.hashpw(s, BCrypt.gensalt(d));
			stop = System.nanoTime();
			sumHash += stop-start;
			start = System.nanoTime();
			BCrypt.checkpw(s, hash);
			stop = System.nanoTime();
			sumCheckYes += stop-start;
			s=s.substring(0, s.length()-1) + chars.get(r.nextInt(chars.size()));	
			start = System.nanoTime();
			BCrypt.checkpw(s, hash);
			stop = System.nanoTime();
			sumCheckNo += stop-start;
		}
		sumHash /= 1000000000.0;
		sumCheckYes /= 1000000000.0;
		sumCheckNo /= 1000000000.0;
		return new double[]{ sumHash / ((double)list.size()), sumCheckYes / ((double)list.size()), sumCheckNo / ((double)list.size()) };
	}
	
	public static enum DevLevel {
		RELEASE, DEV, SUPER_DEV
	}
	private static DevLevel getDevLevel() {
		final File dev = new File(BASE_DIR + "/dev_mode");
		if (!dev.exists()) {
			LOGGER.info("Running in RELEASE mode");
			return DevLevel.RELEASE;
		}
		String devStr;
		try {
			devStr = Files.readString(new File(BASE_DIR + "/dev_mode").toPath());
		} catch (IOException e) {
			LOGGER.warn("Error reading " + dev.getAbsolutePath());
			LOGGER.warn("Running in DEV mode");
			return DevLevel.DEV;
		}
		if (devStr.toLowerCase().strip().startsWith("super")) {
			LOGGER.warn("Running in SUPER_DEV mode");
			return DevLevel.SUPER_DEV;
		}
		LOGGER.warn("Running in DEV mode");
		return DevLevel.DEV;
	}
}
