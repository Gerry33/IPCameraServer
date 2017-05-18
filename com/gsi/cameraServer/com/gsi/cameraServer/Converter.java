package com.gsi.cameraServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;




import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/* 	executor service: makes no sense here as we have fat constructors
 	and the file to execute can only be given by a constructor.
 	As we have queues that have to work one by one, its better to stick with a classic singleton thread. 

	// http://tutorials.jenkov.com/java-util-concurrent/executorservice.html 
 */  

public class Converter extends Thread {

	private Logger 					logger;
	private BlockingQueue <File> 	converterQueue;	
	private BlockingQueue <File> 	uploaderQueue; 
	private int 					conversionTimeOut;
	private boolean 				deleteLocalAfterConversion = false;
	private DefaultExecutor 		executor;
	private Configuration 			configuration;

	public Converter( Configuration config, BlockingQueue<File> converterQueue, BlockingQueue<File> uploaderQueue ) {

		logger 			= LogManager.getLogger	(Converter.class);
		this.configuration = config;
		
//		String 	h = null;
//		int 	i = 0;
//		do {
//			i++;
//			h = config.getProperty("Conversion.Command." + i, null); 
//			if (h != null){
//				h= h.trim();
//				String[] b = h.split(";");
//				if ( b.length != 2){
//					logger.error("Cannot parse conversion command <" + h +">, splitted length:" + b.length);
//					continue;
//				}				
//				conversionCmd.put( b[0].trim(),b[1].trim());
//			}
//		}while (h != null);


		this.conversionTimeOut 	= Integer.parseInt(config.getProperty	("Conversion.Timeout","120"));
		if (config.getProperty ("Conversion.DeleteLocalAfterConversion","y").equalsIgnoreCase("y"))
			deleteLocalAfterConversion = true;

		this.logger 			= LogManager.getLogger	(Converter.class);
		this.converterQueue 	= converterQueue;
		this.uploaderQueue 		= uploaderQueue;

		executor = new DefaultExecutor();

	}
	// for tests 
	public Converter( Configuration config ) {	
		// this(config, null, null); does not work		
		this.conversionTimeOut 	= Integer.parseInt(config.getProperty	("Conversion.Timeout","120"));
		this.logger 			= LogManager.getLogger	(Converter.class);

	}

	public void run () {
		File file, convertedFile ;
		logger.info("Converter started");

		while (!Thread.currentThread().isInterrupted()) {
			try
			{
				file 		  = converterQueue.take();
				convertedFile = convertFile(file);
				if( uploaderQueue != null)
					if (convertedFile != null)				
						uploaderQueue.put(convertedFile);
			}
			catch (final InterruptedException e)
			{
				logger.debug("Uploader thread interrupted");              
				Thread.currentThread().interrupt();
			}  
		}
		logger.info ("Converter stopped.");
	}

	/**
	 * @param  inFile
	 * @return File converted File 
	 * 
	 * 	https://commons.apache.org/proper/commons-exec/tutorial.html
	 *  http://alvinalexander.com/java/java-exec-processbuilder-process-1
	 */
	public  File convertFile (File inFile) {

		String 	fileNameWithoutExt  = inFile.getAbsolutePath().substring(0,  inFile.getAbsolutePath().lastIndexOf(".")) ;
		String 	extension  			= inFile.getName()		  .substring    (inFile.getName()		 .lastIndexOf(".") + 1);
		
		String cmd = configuration.getProperty("Conversion.Command." + extension, null); 
		if (cmd == null){
			logger.debug ("No conversion cause not fitting file extension:" + inFile.getAbsolutePath());
			return inFile;
		}
		
		// String  adaptedCmdFile 		= new String( conversionCmd.get(extension)); 	// make copy
		
		//  ${FileName}.$EXT(mp4) needed to find the generated output file for deletion
		
		String  outputExtension =  cmd.substring(cmd.lastIndexOf( "$EXT_") + "$EXT_".length(), cmd.length() );
		cmd 	= cmd.replace ("${FileName}", fileNameWithoutExt);
		cmd	= cmd.replace ("$EXT_", "");

		CommandLine cmdLine 		= CommandLine.parse(cmd);

		String userDir = System.getProperty("user.dir") ;

		LocalTime start 			= LocalTime.now();		

		executor.setExitValue(1);

		DefaultExecuteResultHandler resultHandler 	= new DefaultExecuteResultHandler();
		ByteArrayOutputStream 		outputStream 	= new ByteArrayOutputStream();
		try {
			logger.debug("Executing conversion cmd line:<"+ cmdLine +"> in " + userDir );
			// http://stackoverflow.com/questions/7340452/process-output-from-apache-commons-exec

			ExecuteWatchdog 	 watchdog = new ExecuteWatchdog(conversionTimeOut * 1000L);	// 
			executor.setWatchdog(watchdog);

			PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
			executor.setStreamHandler(streamHandler);

			executor.execute(cmdLine, resultHandler);

		} catch (IOException e) {			
			e.printStackTrace();
			return null;
		}

		try {
			resultHandler.waitFor(); 	// blocks
			// logger.debug("Conversion output :" + outputStream.toString()); 

		} catch (InterruptedException e) {
			logger.error("Execution of conversion interrupted for file2convert: <" + inFile.getAbsolutePath() +">,:" + e.getMessage());
			e.printStackTrace();
			return null;

		}

		File  outFile =  new File(fileNameWithoutExt + "." + outputExtension);

		if ( outFile.exists() == true && outFile.length() > 0) {
			long totalSecs = ChronoUnit.SECONDS.between(start, LocalTime.now()); 		// snippet time diff LocalTime

			logger.info("Conversion completed from " + inFile.getAbsolutePath() +"> to <" + outFile.getAbsolutePath() + " in "  
					+ String.format("%02d:%02d", (totalSecs % 3600) / 60 , totalSecs % 60) + " (min:sec)." );
			
			if (deleteLocalAfterConversion == true) {
				logger.debug ("Deleting <" + inFile.getAbsolutePath()+"> after successfull conversion.");
				FileUtils.deleteQuietly(inFile);
			}
			return outFile;
		}

		logger.error ("Conversion error: Expected output file does not exist <" + outFile.getAbsolutePath() 
			+ ">, input file was:<" + inFile.getAbsolutePath() +">."
			+ "Conversion output:" +  outputStream.toString());	
		return null;

	}

	public void shutdown() {

		logger.debug("Converter received termination request");
		this.interrupt();


	}	
}
