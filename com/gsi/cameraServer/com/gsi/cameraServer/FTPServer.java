package com.gsi.cameraServer;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AgeFileFilter;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FTPServer {

	// ===========================================================
	// Constants
	// ===========================================================
	private int 		FTP_PORT = 2121;
	private final 		String DEFAULT_LISTENER = "default";
	private static 	int MAX_CONCURRENT_LOGINS = 2;
	final static int 	MAX_CONCURRENT_LOGINS_PER_IP = 2;
	private Logger 		logger =  LogManager.getLogger(FTPServer.class); 

	// ===========================================================
	// Fields+
	// ===========================================================
	private static 	FtpServer 		ftpServer;
	private static 	FtpServerFactory serverFactory;
	private 		ListenerFactory listenerFactory;
	private int 	idleTimeout		=5;
					Configuration 	config;
	private 		FTPLetImpl 		ftpLetImpl;
	private 		File 			ftpHomeDirFile;
	private 		String 			ftpPassword;	
	private 		String 			ftpUsername;
	private BlockingQueue<File>  uploaderQueue;
	private BlockingQueue<File>  converterQueue;	
	final 	Lock lock 		   = new ReentrantLock();
	final 	Condition condition  = lock.newCondition(); 
	private int 						days2KeepLocalFiles;
	private ScheduledExecutorService 	scheduler;
	
	
	public FTPServer( Configuration config, BlockingQueue<File> converterQueue, BlockingQueue<File> uploaderQueue) {

		this.config 		= config;
		this.uploaderQueue  = uploaderQueue;
		this.converterQueue = converterQueue;
		
	}

	public boolean init (){

		String ftphome = config.getProperty("FTPServer.homedir",null);
		ftpHomeDirFile = new File (ftphome );
		
		if (ftpHomeDirFile.exists() == false || ftpHomeDirFile.isDirectory() == false ) {
			logger.error("FTP Home does not exist or is no directory:<" + ftphome+">");
			return false;
		}
		
		ftpUsername = config.getProperty("FTPServer.Username");
		ftpPassword = config.getProperty("FTPServer.Password");
		FTP_PORT			= Integer.parseInt( config.getProperty("FTPServer.ListenerPort",			 	"2121"));
		idleTimeout			= Integer.parseInt( config.getProperty("FTPServer.ConnectionCloseOnIdleInMins", "5"));

		// logger.info("FTP Server started");

		serverFactory 	= new FtpServerFactory();
		listenerFactory = new ListenerFactory();
		listenerFactory.setPort(FTP_PORT);

		try {
				addUser		  (ftpUsername, 	 ftpPassword , ftpHomeDirFile.getAbsolutePath());				
				serverFactory.addListener (DEFAULT_LISTENER, listenerFactory.createListener());
				ftpLetImpl	= new FTPLetImpl (ftpHomeDirFile,  converterQueue, uploaderQueue );	// ???
				ftpServer 	= serverFactory.createServer();
				serverFactory.getFtplets().put(FTPLetImpl.class.getName(), ftpLetImpl);
				ftpServer.start();
				
			} catch (Exception e1) {			
				logger.error(e1);
				e1.printStackTrace();
				return false;
			}
		
		// local  Cleaner
		days2KeepLocalFiles		= Integer.parseInt ( config.getProperty("FTPServer.Days2KeepFiles", "-1"));
		if (days2KeepLocalFiles > 0 ) {
			scheduler = Executors.newScheduledThreadPool( 1 ); // one scheduler is enough		
			scheduler.scheduleAtFixedRate(
				new Runnable() {
					@Override public void run() {
						removeLocalFiles(); 
					}}, 		// run					
				0, 				/* Startverzögerung 0h */
				24,				// repeat every day 
				TimeUnit.HOURS);
		
			logger.info("Scheduled LOCAL cleanup job running every 24hrs.");
		}
		else 
			logger.info("No deletion of local files configured");
		
		logger.info("FTP Server serving on port " + listenerFactory.getPort() 
		+ " with account <" + ftpUsername  
		+ ">, homedir <" + ftpHomeDirFile + ">, idle timeout: " + idleTimeout +" mins.");
		return true; 

	}
	
	public boolean shutdown () {
		if (scheduler != null )
			scheduler.shutdown(); //

		ftpServer.stop();		    
		// logger.debug("FTP Server shut down");		
		return true;
	}

	// http://stackoverflow.com/questions/27404709/apache-ftp-server-create-user-programmatically?rq=1

	private void addUser(String username, String password, String homedir) throws FtpException { // , int uploadRateKB, int downloadRateKB) throws FtpException {

		BaseUser user = new BaseUser();

		user.setName			(username);
		user.setPassword		(password);        
		user.setHomeDirectory	(homedir);        
		user.setEnabled			(true);
		
		user.setMaxIdleTime		(2);
		
		// config.getProperty("Starter.ConnectionCloseOnIdleInMins","2");		
		user.setMaxIdleTime		(Integer.parseInt(config.getProperty("Starter.ConnectionCloseOnIdleInMins","2")) * 60);
		
		// list.add(new TransferRatePermission(downloadRateKB * BYTES_PER_KB, uploadRateKB * BYTES_PER_KB)); // 20KB

		List<Authority> list = new ArrayList<Authority>();

		list.add(new WritePermission());	 /// 
		list.add(new ConcurrentLoginPermission	(MAX_CONCURRENT_LOGINS, MAX_CONCURRENT_LOGINS_PER_IP));

		user.setAuthorities(list);

		serverFactory.getUserManager().save(user);
	}

	private void removeLocalFiles () {
		
		if ( FTPLetImpl.getLastTS() == 0L)
			return;
		
		logger.info("Removing local files at <" + ftpHomeDirFile.getAbsolutePath()+ 
				 " > " + days2KeepLocalFiles   + " days before latest received file timestamp " 
				 	   + Util.dateTimeFormatterNoTZ.format(Instant.ofEpochMilli(FTPLetImpl.getLastTS())));
				
		long cutoff = FTPLetImpl.getLastTS() 	 - ( days2KeepLocalFiles * 24 * 60 * 60 * 1000);
//was : long cutoff = System.currentTimeMillis() - ( days2KeepLocalFiles * 24 * 60 * 60 * 1000L);

		String  [] fileList = ftpHomeDirFile.list( new AgeFileFilter(cutoff) );	// snippet: files elder than

		for ( String  fileName : fileList){

			File f = new File(ftpHomeDirFile + Util.getSystemFileDelimiter() + fileName);

			logger.info ("Removing outdated local " + (f.isDirectory() ? " dir " : "file") +" <" + f.getAbsolutePath()
				+ "> with dateTime <" + Util.dateTimeFormatterNoTZ.format(Instant.ofEpochMilli(f.lastModified ()))); // snippet: long to DateTime 

			if (f.isDirectory())
				try {
					FileUtils.deleteDirectory(f);
				} catch (IOException e) {
					e.printStackTrace();
				}
			else  
				FileUtils.deleteQuietly(f);
		}
	}

	
	//	public static void restartFTP() throws FtpException {
	//		if (ftpServer != null) {
	//			ftpServer.stop();
	//			try {
	//				Thread.sleep(1000 * 3);
	//			} catch (InterruptedException e) {
	//			}
	//			ftpServer.start();
	//		}
	//	}

	//	public static void stopFTP() throws FtpException {
	//		if (ftpServer != null) {
	//			ftpServer.stop();
	//		}
	//	}

	//	public static void pauseFTP() throws FtpException {
	//		if (ftpServer != null) {
	//			ftpServer.suspend();
	//		}
	//	}
	//
	//	public static void resumeFTP() throws FtpException {
	//		if (ftpServer != null) {
	//			ftpServer.resume();
	//		}
	//	}
	
	

}
