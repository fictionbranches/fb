package fb;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.core.UriBuilder;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.strategies.SimpleDynamicNIOStrategy;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
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
import fb.api.NotFoundExceptionMapper;
import fb.api.RssStuff;
import fb.objects.FlatEpisode;
import fb.util.BadLogger;
import fb.util.Strings;

public class InitWebsite {
	
	/**
	 * Where all the stuff is.
	 * 
	 * (Relative path will be relative to working directory when program is run)
	 * 
	 * no trailing /
	 */
	public static final String BASE_DIR = "fbstuff";
	
	public static final boolean DEV_MODE = new File(BASE_DIR + "/dev_mode").exists();
	
	/**
	 * set this value to the default (will revert to this value after restarts)
	 */
	public static boolean READ_ONLY_MODE = false;//NOSONAR
	/**
	 * Gets set to false if no search indexes are detected, then back to true after search indexes have been built
	 */
	public static boolean SEARCHING_ALLOWED = true;//NOSONAR
	
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
		System.err.println("USAGE: (run | init | count)");
		System.err.println("If no option is specified, run is default");
		System.exit(1);
	}
	
	private static void runServer() {
		HttpServer server;
		{
			Thread searchIndexer = checkBaseDirAndIndexes();
			BadLogger.log("Started. Connecting to postgres"); // This line also starts the file watcher threads
			checkDatabase();
		
			Accounts.bump(); // Force temp accounts to be loaded and account cleaner thread to start
		
			Runtime.getRuntime().addShutdownHook(new Thread(()->{
				Accounts.writeSessionsToFile();
				DB.closeSessionFactory();
			}));
			
			
			if (searchIndexer != null) {
				BadLogger.log("Starting search indexer");
				searchIndexer.start();
				BadLogger.log("Search indexer started");
			}
			
			BadLogger.log("Starting server");
			
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
			BadLogger.log(e);
			System.exit(26);
			throw new RuntimeException(e);
		}
		BadLogger.log("Server started");
	}
	
	private static void checkDatabase() {
		BadLogger.log("Checking database");
		for (FlatEpisode rootEp : DB.getRoots()) {
			BadLogger.log("Found root episode: " + rootEp.generatedId + " " + rootEp.link);
		}
		BadLogger.log("Postgres connected successfully");
	}
	
	private static Thread checkBaseDirAndIndexes() {
		BadLogger.log("Checking base dir");
		File baseDir = new File(InitWebsite.BASE_DIR);
		if (baseDir.exists()) {
			if (!baseDir.isDirectory()) {
				BadLogger.log("Base dir " + baseDir.getAbsolutePath() + " could not be created, file with same name exists");
				System.exit(24);
			}
		} else if (!(new File(InitWebsite.BASE_DIR).mkdirs())) {
			BadLogger.log("Base dir " + baseDir.getAbsolutePath() + " does not exist and could not be created");
			System.exit(24);
		}
		File indexDir = new File(InitWebsite.BASE_DIR + "/search-indexes");
		if (indexDir.exists() && indexDir.isFile()) {
			BadLogger.log("Search index directory " + indexDir.getAbsolutePath() + " is a file");
			DB.closeSessionFactory();
			System.exit(1);
		} else if (!indexDir.exists() || indexDir.list().length==0) {
			InitWebsite.SEARCHING_ALLOWED = false;
			Thread t = new Thread(()->{
				DB.doSearchIndex();
				InitWebsite.SEARCHING_ALLOWED = true;
			});
			t.setName("SearchIndexerThread");
			BadLogger.log("Started search indexer thread");
			return t;
		}
		BadLogger.log("Finished check base dir");
		return null;
	}
	
	private static ResourceConfig jaxrsConfig() {
		ArrayList<Class<?>> list = Stream.of(AccountStuff.class, AddStuff.class,
				AdminStuff.class, GetStuff.class, LegacyStuff.class, RssStuff.class, JSONStuff.class).collect(Collectors.toCollection(ArrayList::new));
		if (DEV_MODE) {
			list.add(DevStuff.class);
			list.add(DevStuff.DevStuff2.class);
		}
		ResourceConfig resourceConfig = new ResourceConfig(list.toArray(new Class<?>[0]));
		resourceConfig.register(CharsetResponseFilter.class);
		resourceConfig.register(NotFoundExceptionMapper.class);
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
			
			BadLogger.log("Finished reading " + keystore.length + " bytes into the keystore");
		} catch (IOException e) {
			//never happens
			BadLogger.log(e);
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
				.setTcpNoDelay(true)
				.setIOStrategy(SimpleDynamicNIOStrategy.getInstance())
				.setWorkerThreadPoolConfig(ThreadPoolConfig.defaultConfig()
					.setCorePoolSize(1)
					.setMaxPoolSize(5)
					.setQueueLimit(512))
				.build();
		for (NetworkListener nl : server.getListeners()) {
			BadLogger.log("Set transport for listener: " + nl);
			nl.setTransport(transport);
		}
	}
	
	private static void enableExceptionLogging() {
		// Enable exception logging
		Logger l = Logger.getLogger("org.glassfish.grizzly.http.server.HttpHandler");
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
			BadLogger.log(sb.toString());
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
}
