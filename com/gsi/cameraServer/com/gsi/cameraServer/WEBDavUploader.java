package com.gsi.cameraServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.xml.namespace.QName;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.impl.SardineException;
import com.github.sardine.util.SardineUtil;

public class WEBDavUploader extends Thread{

	private String 	remoteURIRoot;	// UNEncoded

	private String 	username;
	private String 	password;
	private final  	String  	WebDavPropWin32LastModifiedTime = "Win32LastModifiedTime";
	private boolean isConnected  		= false;
	private Sardine sardine 			= null;
	private boolean supportsWin32Date 	= false;
	private Logger 	logger;
	private File 	ftpHomedir;
	int 			days2KeepFiles;

	Map<QName, String>				remotePropMap 		= null;
	private BlockingQueue<File> 	uploaderQueue;
	private boolean 				deleteAfterUpload = true;
	private ScheduledExecutorService scheduler;

	private URL rootURL;

	public WEBDavUploader(Configuration config, final BlockingQueue<File> uploaderQueue ) throws ExceptionFatal {

		logger 			= LogManager.getLogger	(WEBDavUploader.class);
		ftpHomedir 		= new File ( config.getProperty	("FTPServer.homedir"));
		remoteURIRoot   = config.getProperty	("WebDav.RemoteURIRoot");
	
		if (!remoteURIRoot.endsWith("/")){
			remoteURIRoot += "/";
			logger.warn("HomeDir does not end with a '/'. Add to configuration.");
			config.setProperty("WebDav.RemoteURIRoot", remoteURIRoot);
		}

		//		Pattern urlPattern = Pattern.compile( "^(((ht|f)tp(s?)\\:\\/\\/[0-9a-zA-Z]([-.\\w]*[0-9a-zA-Z])*(:(0-9)*)*))?(\\/?)(\\{?)([a-zA-Z0-9\\-\\.\\?\\,\\'\\/\\\\\\+\\~\\{\\}\\&%\\$#_]*)?(\\}?)(\\/?)$");
		//		Matcher matcher = urlPattern.matcher (remoteURIRoot);
		//		if ( matcher.matches() == false){
		//			throw new ExceptionFatal ("remoteURIRoot <"  + remoteURIRoot +"> is not a valid URL.");
		//		}

		try {
			rootURL = new URL(remoteURIRoot);	// encoded
		} catch (MalformedURLException e) {
			throw new ExceptionFatal(e.getMessage());
		}

		username		= config.getProperty	("WebDav.Username");
		password 	 	= config.getProperty	("WebDav.Password");
		remotePropMap 	= new HashMap	<QName, String>( 1 );
		// sardine 		= SardineFactory.begin	(username, password);
		this.uploaderQueue 		= uploaderQueue;
		
		connect();
		
		if (config.getProperty	("WebDav.DeleteLocalAfterUpload","N").equalsIgnoreCase("n"))
			deleteAfterUpload = false;
		
		days2KeepFiles		= Integer.parseInt ( config.getProperty("WebDav.Days2KeepFiles", "-1"));
		if (days2KeepFiles > 0 ) {
			scheduler = Executors.newScheduledThreadPool( 1 );
			scheduler.scheduleAtFixedRate(
							new Runnable() {
								@Override public void run() {
									removeRemotes(); 
								}}, 		// run					
							0, 				/* Startverzögerung 1h, WEBDAV must be connectzee */
							24,				// repeat every day 
							TimeUnit.HOURS);
					
			logger.info("Scheduled LOCAL cleanup job running every 24hrs.");
		}
		else 
			logger.info("No deletion of remote files configured");
		

	}

	/**
	 * try to connect to the root path of the host. The path must exist.
	 * checks only if we can connect at all. 
	 * called once at startup. if it fails it makes no sense to continue. therefore only  throws  ExceptionFatal

	 * @return
	 * @throws ExceptionFatal
	 * @throws ExceptionRetry 

	 */
	private boolean connect() throws  ExceptionFatal  { 

		if (isConnected == true)
			return true;

		while (isConnected == false) {
			try {
				
				logger.debug ("Starting WEBDAV client and trying to connect to <" + remoteURIRoot + ">");
				
				sardine 		= SardineFactory.begin	(username, password);				
				
				// this is vital, otherwise the uploads do no work. Maybe a bug, maybe a feature.
				if (sardine.exists(remoteURIRoot)) { // only for directories, exists() to file result in false. rubbish . 
					isConnected = true;
					logger.info ("Succesfully connected to :<" + remoteURIRoot + ">");

					// get the properties that the servers offer
					// List<DavResource> res = sardine.getResources(remoteRootFull); deprecated
					List<DavResource> res = sardine.list (remoteURIRoot);

					if (res.size() == 0)
						throw new ExceptionFatal ("Server root <" + remoteURIRoot + "> reports no ressources.");

					// DavResource dr = res.get(0);
					DavResource dr = res.iterator().next();
					Map<String, String> props = dr.getCustomProps();

					Iterator<Entry<String, String>> iterator1 = props.entrySet() .iterator();
					while (iterator1.hasNext()) {
						Entry<String, String> tr = iterator1.next();
						// logger.debug("Server WEbDav Properties: Name:<"+tr.getKey() +">,\tvalue:<"+tr.getValue() +">");					 
						if (tr.getKey().equals( WebDavPropWin32LastModifiedTime) == true) {
							supportsWin32Date = true;
							logger.debug("Server support WIN32 date/time attribute:" + WebDavPropWin32LastModifiedTime); 
						}
					}
				} else 
					throw new  ExceptionFatal ("Server root <" + remoteURIRoot+ "> not found.");
			}
			catch ( Exception e) {
				if (connectionExceptionHandler(e, "Cannot connect to <" + remoteURIRoot  + ">" ) == 0){
					return true;
					// else retry.
				}
			}
		}
		return true;
	}

	public boolean isConnected() {
		return isConnected;
	}

	private void upload(File  file) throws ExceptionFatal {

		// cut off the homedir from the file path and add it to the remote base uri

		String relPath	= file.getAbsolutePath().substring (ftpHomedir.getAbsolutePath().length() + 1).replace("\\", "/");
		String relDir   = file.getParent()		.substring (ftpHomedir.getAbsolutePath().length()    ).replace("\\", "/");

		if (relDir.startsWith("/"))
			relDir= relDir.substring(1);

		LocalTime start 	= LocalTime.now(); 
		String uriFileStr 	= createURI( relPath  );

		for (int i = 0 ; i < 3 ; i++ ) {

			// get the potential remote dir of the file. if its there, ok. if not create the dirs first, prior to upload

			String uriDirStr  = createURI( relDir);
			try { 
				if (sardine.exists(uriDirStr)  == false)				
					createDirTree(relDir); 	// create remote dir tree first as webdav is no able to create complete dir tree 

				logger.debug ("Uploading file <"  + file.getAbsolutePath() + "> with " + Util.getFileSizeAsString  (file.length() ) 
				//+ " to dir <" + uriDirStr  
				+ " to :<" + uriFileStr + ">. Remaining uploaderQueue size:" + uploaderQueue.size());

				sardine.put(uriFileStr, new FileInputStream ( file ));
				break;

			} catch (Exception e) {
				logger.error ("WEBDAV Upload Error:" + e);
				int httpCode= connectionExceptionHandler(e, "Error on upload");
				if (httpCode == 401 ) {		// authorization error: cookie invalid. re-login
					closeWebDav();
					connect();
				}
				else
					waitAndReconnect();
			}
		}

		if ( supportsWin32Date ){

			ZonedDateTime	t  = ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault());
			String 			ts = t.format(DateTimeFormatter.RFC_1123_DATE_TIME) ;

			// Wed, 27 May 2015 08:35:46 GMT
			remotePropMap.put(SardineUtil.createQNameWithDefaultNamespace(WebDavPropWin32LastModifiedTime), ts ); 
			try{
				sardine.patch(uriFileStr, remotePropMap);
			} catch (IOException e1) {
				logger.error("Error setting DateTime:" + e1.getMessage());
			}
		}

		long totalSecs = ChronoUnit.SECONDS.between(start, LocalTime.now()); 		// snippet time diff LocalTime

		String durStr= ""; 
		if ( totalSecs > 0) {	// otherwise div 0
			long avgSpeed =  file.length() / totalSecs;
			durStr = " = " + Util.getFileSizeAsString(avgSpeed) +"/sec";
		}		
		logger.info ("Upload completed from :<"+ file.getAbsolutePath() + "> to <" + uriFileStr +"> with " + Util.getFileSizeAsString  (file.length() ) 
		+ " in " + String.format("%02d:%02d", (totalSecs % 3600) / 60 , totalSecs % 60) + " (min:sec). " + durStr + ". Remaining uploaderQueue :" + uploaderQueue.size());

		if (deleteAfterUpload) {
			logger.debug ("Deleting <" + file.getAbsolutePath()+"> after successfull upload.");		
			FileUtils.deleteQuietly  (  file);
		}

	}

	/**
	 * create recursively a WEBDAV directory tree
	 * cause webdav cannot create 'abc/cde/xyz' in one try. 
	 * 
	 * @param relDir: e.g. abc/cde/fgh/
	 * mkdir expects: 
	 * 	1.				abc
	 * 	2.				abc/cde
	 * 	3.				abc/cde/fgh/
	 * @throws IOException 
	 * @throws ExceptionFatal 
	 */

	private void createDirTree(String relDir) throws IOException, ExceptionFatal{

		String[]s= relDir.split("/");
		String uri = null, r ="";

		for (String p : s) {
			if (p.length()==0)	//empty
				continue;
			r += p + "/";

			try {
				// uri = new URL(remoteURIRoot + relUri).toURI().toASCIIString(); // crashes for unknown reason
				// URI uridir  = urlDir.toURI();	// crashes for unknown reason
				URL urlDir = new URL (remoteURIRoot + r);
				uri  	   = new URI (urlDir.getProtocol(), urlDir.getUserInfo(), urlDir.getHost(), urlDir.getPort(), urlDir.getPath(),null, null).toASCIIString();

				logger.debug ("Trying creation of dir:<" + urlDir.toString() +">");

			} catch (URISyntaxException e) {
				e.printStackTrace();
				throw new ExceptionFatal(e);
			}

			if (sardine.exists(uri)) { // maybe created by others images, now video following. creating already existing results in HTTP 405 : conflict
				logger.debug ("Dir Creation: dir <" + uri +"> already exists. Skipping.");
				continue;
			}
			sardine.createDirectory(uri);
			logger.debug ("Created dir:<" + uri +">");
		}
	}

	public void run() {

		logger.info ("WEBDAV Uploader sucessfully started to upload to <" + remoteURIRoot +">");

		while (!Thread.currentThread().isInterrupted()) {
			try
			{
				File fi = uploaderQueue.take(); // blocking call, peek is not possible cause it does not block				
				try {
					connect();
				} catch (ExceptionFatal e) {
					uploaderQueue.put(fi);
					logger.error("Re-trying to connect remote <" + remoteURIRoot +"> on next request. Queue size:" + uploaderQueue.size());						
					waitAndReconnect();
					continue;
				}
				upload (fi);

			}
			catch (final InterruptedException e)
			{
				logger.debug("Uploader thread interrupted");              
				Thread.currentThread().interrupt();
			} catch (ExceptionFatal e) {
				e.printStackTrace();				
				logger.fatal("Uploader exiting.");
				this.interrupt();
			}  
		}
		logger.info ("Uploader stopped.");
	}

	private void closeWebDav(){

		logger.debug("Uploader closing connection.");
		try {
			sardine.shutdown();
		} catch (IOException e) {
			e.printStackTrace();
		}
		isConnected = false;
	}

	public void shutdown() 
	{
		logger.debug("Uploader got termination request");
		if (scheduler != null )
			scheduler.shutdown(); 
		closeWebDav();
		this.interrupt();

	}

	// returns an encoded uri. 
	synchronized String createURI (String path) throws ExceptionFatal {

		URL 	urlDir; 
		String 	remoteURI = null  ;
		try {
			urlDir 	  = new URL (remoteURIRoot + path);
			remoteURI = new URI (urlDir.getProtocol(), urlDir.getUserInfo(), urlDir.getHost(), urlDir.getPort(), urlDir.getPath(),null, null).toASCIIString();
		} catch (MalformedURLException e) {
			throw new ExceptionFatal(e);
		} catch (URISyntaxException e) {
			e.printStackTrace();
			throw new ExceptionFatal(e);
		}
		return remoteURI;
	}


	private void removeRemotes () {

		try {
			if ( FTPLetImpl.getLastTS() ==0L ){
				return;
			}
			LocalDateTime lastReceivedTS = LocalDateTime.ofInstant(Instant.ofEpochMilli(FTPLetImpl.getLastTS()), ZoneId.systemDefault()) ;
			
			logger.info ("Removing remote files " + days2KeepFiles  + " days elder than latest received file with TS :" 
					+ lastReceivedTS.format(Util.dateTimeFormatterNoTZ) + ">");
			
			List<DavResource> 	resList 	= null;
			String path, relPath; 

			connect();

			resList = sardine.list (remoteURIRoot );	

			if (resList == null || (resList != null && resList.size() == 0)) {
				return;
			}
			
			// loop all resources 
			for (DavResource dr : resList ) {

				path = dr.getPath();				// '/wd/test' 	, UNEncoded String 
				if ( dr.isDirectory()  == true && path.endsWith("/") == false) 	// some server do not supply an ending '/' on dirs
					path +="/";
				if ( remoteURIRoot.endsWith(path))		// skip home current dir	  
					continue;							// http://xyz.com/wd/test

				String rootPath = rootURL.getPath();	//  			 /wd/test
				relPath    		= path.substring ( rootPath.length() );

				LocalDateTime dirDateTime = LocalDateTime.ofInstant(dr.getCreation().toInstant(), ZoneId.systemDefault());
				
				long days 	= ChronoUnit.DAYS.between(dirDateTime, lastReceivedTS);  // snippet time diff LocalTime
				String uri 	= createURI(relPath);

				if ( days >= days2KeepFiles){

					logger.info("Removing remote " + (dr.isDirectory() ? "dir" : "file") +" at URI <" + uri + "> with dateTime " + dirDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) 
					+ " aged " + days   + " days; elder than max days:" + days2KeepFiles);
					sardine.delete(uri);
				}
				
			}
		}
		catch (SardineException e) {
			logger.error("listPlainRemote:" + remoteURIRoot + ",msg:" + e.getMessage() + ",HTTP-Response:" + e.getResponsePhrase() + ":HTTP Code:" + e.getStatusCode());
		}
		catch (IOException e) {
			logger.error("listPlainRemote:" + remoteURIRoot + ",msg:" + e.getMessage());
			// e.printStackTrace();
		} catch (ExceptionFatal e) {
			e.printStackTrace();
		}

	}

	private void waitAndReconnect() throws ExceptionFatal  {

		for (int i = 0; i < 10; i++) {
			if (isConnected == true) 
				closeWebDav();
			try {
				logger.info("Error connecting to <" + remoteURIRoot +">. Sleeping 120 secs, then continueing. Sequence " + i + " of 10");
				Thread.sleep(120000L);
			} catch (InterruptedException e) {				
				throw new ExceptionFatal (e, "Sleep interupted.");
			}
			if ( connect() == true) 
				return;
		}
		throw new ExceptionFatal ("Reconnect limit 10 * 120 secs exceeded. Aborting");
	}

	/**
	 * Decide if its a fatal or a retry based on excetion type and/or http error
	 * diese Excpetion kommt nie beim Filerunner an, da der nur ioexpections verarbeitet. sehr ärgerlich. 
	 * @returns
	 * 0: ok, 
	 * > 0: HTTP return code  
	 * 
	 * @throws WDExceptionFatal
	 */

	private int connectionExceptionHandler(Exception e, String msg) throws ExceptionFatal {

		if (e instanceof SardineException) {
			// then we have http codes available
			SardineException se = (SardineException) e;

			msg += "SardineException: WEBDAV Error: <" + se.getStatusCode() + ":" + se.getResponsePhrase() +">";
			logger.error(msg);	
			// 502 gateway error: server or in between: temporary
			// 503 'service temporarily unavailable'  :	temporary
			// 401 'authorizaution ... '  			  :	temporary, re-login, cookie outdated	

			if (se.getStatusCode() == 403 || se.getStatusCode() == 414 )	// more fatals to add here 
				throw new ExceptionFatal(msg);
			else{
				return se.getStatusCode();	// upper layer must decide 
			}

		} else	if (e instanceof IOException) {
			msg += "IOException with retry";
			waitAndReconnect();
			return 0;		
		}
		else
			throw new ExceptionFatal(e);

	}
}