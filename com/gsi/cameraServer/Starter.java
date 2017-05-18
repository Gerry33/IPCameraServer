package com.gsi.cameraServer;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.status.StatusConsoleListener;
import org.apache.logging.log4j.status.StatusLogger;

public class Starter {

	private static Logger 	logger;

	public static void main(String[] argv) {
		logger 	 = LogManager.getLogger(Starter.class);
		new IPCamServer();	// to get rid of all statics 
	}
	public static Logger getLogger() {
		return logger;
	}
	
}

class IPCamServer  {

	private static Logger 				logger;
	private WEBDavUploader 				uploader 		= null;
	private BlockingQueue<File> 		uploaderQueue  	= null;
	private Converter					converter 		= null;	
	private BlockingQueue<File> 		converterQueue 	= null;
	private PresenceCheckerController 	presenceChecker;
	private Configuration 				config;
	private FTPServer ftpServer;
	   
	public IPCamServer () {

		// ...............................................................................................
		// if log4j2.xml exists in user dir, then use it, otherwise take the one delivered in classes.
		// snippet log4j2 xml location
		// https://logging.apache.org/log4j/2.0/faq.html
		
		String log4jconf= System.getProperty("user.dir") + "/log4j2.xml";
		File f = new File(log4jconf);
		if (f.exists()) {
				LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
				context.setConfigLocation(f.toURI());
				System.out.println("Using external log4j.xml at <" + f.getAbsolutePath()+ ">.");
			}
	
		logger 	= LogManager.getLogger("com.gsi.CamControl");
			
		// snippet log4j2 stderr https://logging.apache.org/log4j/2.x/manual/configuration.html 
		StatusConsoleListener listener = new StatusConsoleListener(Level.ERROR);
		StatusLogger.getLogger().registerListener(listener);
		
// 		logger 	 = LogManager.getLogger(Starter.class);
		logger.info("Starting IPCameraServer");
		// only works for "kill -TERM ", not windows CTRL-C

		Runtime.getRuntime().addShutdownHook(new Thread()  {

			public void run( ) {
				System.out.println("SIGTERM detected"); // loggers are already gone
				try {
					if (ftpServer != null)				// unlikely but may happen on wrong configs		
						ftpServer.shutdown();

					if (presenceChecker != null)				
						presenceChecker.shutdown();

					if ( uploader != null){				
						uploader.shutdown();
						uploader.join();
					}
					if (converter != null) {
						converter.shutdown();
						converter.join();
					}

				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				logger.info("Shutdown complete");
				System.out.println("Shutdown complete");
				// System.exit(0);	// no exit here, otherwise the jvm hangs 
			}
		}); 

		try {
			initServices();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);	// calls  the above thread 	
		}	

		// no return 
		// TODO wait for all subservices to shutdown.
	}

	
	private boolean initServices() throws Exception{

		try {
			config = new Configuration();
		} catch (ExceptionFatal e) {
			return false;
		}
		
		// 1. uploader 

		if (config.getProperty("WebDav.RemoteURIRoot", null) == null)
			logger.info("No upload configured");
		else { 
			uploaderQueue 	= new LinkedBlockingQueue<File>(10);
			uploader 		= new WEBDavUploader( config, uploaderQueue );	// contains also scheduler for remote removals
			uploader.start();		
		}

		// 2. converter: always present. if no conversion applies, converter will ignore 

//		if (config.getProperty("Conversion.Command.", null) == null)	// number 1 as the beginning,
//			logger.info("No conversion configured");
//		else { 
			converterQueue 	= new LinkedBlockingQueue<File>(10);
			converter 		= new Converter( config, converterQueue, uploaderQueue);
			converter.start();
		// }
		// 3. FTP server

		ftpServer = new FTPServer(config, converterQueue, uploaderQueue);
		if ( ftpServer.init() == false){
			ftpServer = null;	// invalidate
			logger.error ("Unable to start");
			return false;
		}

		/* 3. Presence checker 
		 	create a supervision thread that checks if human controlled hosts are present, and if so,
		 	switches off motion control 
		 */  

		if (config.getProperty("Camera.URI", null) == null ) {
			logger.info("No 'Camera.URI' defined. No Presence checker enabled");
		}
		else {
			presenceChecker = new PresenceCheckerController (config);
			if ( presenceChecker.start() == false ){
				throw new Exception ( "Error: cannot start");
			}
		}
		
		return true;
	}

}