package com.gsi.cameraServer;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// https://mina.apache.org/ftpserver-project/ftplet.html 

public class FTPLetImpl extends DefaultFtplet {

	private Logger logger;
	private File ftpHomeDir;
	private BlockingQueue<File> uploaderQueue;
	private BlockingQueue<File> converterQueue;
	private static long lastTS = 0L;

	public static long getLastTS() {
		return lastTS;
	}

	public FTPLetImpl(File ftpHomeDir,  BlockingQueue<File> converterQueue, BlockingQueue<File> uploaderQueue) {

		logger	 			= LogManager.getLogger(FTPLetImpl.class);		
		this.ftpHomeDir 	= ftpHomeDir;
		this.uploaderQueue  = uploaderQueue;
		this.converterQueue = converterQueue;
	}

	

	@Override
	public FtpletResult onLogin(FtpSession session, FtpRequest request) throws FtpException, IOException {
		logger.debug ("User <" + session.getUser().getName() + "> successfull logged in from " + session.getClientAddress() + ", session:"+ session.getSessionId());
		return super.onLogin(session, request);
	}

	
	 @Override
	public FtpletResult onDisconnect(FtpSession session) throws FtpException, IOException {
		logger.debug(session.getUser().getName() + " Disconnected session:"+ session.getSessionId());
		return super.onDisconnect(session);
	}
	 /*
	  * (non-Javadoc)
	  * @see org.apache.ftpserver.ftplet.DefaultFtplet#onDownloadStart(org.apache.ftpserver.ftplet.FtpSession, org.apache.ftpserver.ftplet.FtpRequest)
	  */
	@Override
	public FtpletResult onDownloadStart(FtpSession session, FtpRequest request) throws FtpException, IOException {
		logger.debug(session.getUser().getName() + ":Receiving file " + request.getArgument());
		return super.onDownloadStart(session, request);
	}

	@Override
	public FtpletResult onDownloadEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
		logger.debug("Finished Receiving file " + request.getArgument());
		FtpletResult r  = super.onDownloadEnd(session, request);

		return r;
	}

	/* not needed 
	@Override
	public FtpletResult onMkdirStart(FtpSession session, FtpRequest request) throws FtpException, IOException {
		logger.debug("onMkdirStart: Created dir <" + request.getArgument() +">");
		return super.onDownloadEnd(session, request);
	}



    @Override
    public FtpletResult onMkdirEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
    	logger.debug("Created dir <" + request.getArgument() +">");
        return super.onDownloadEnd(session, request);
    }


	@Override
	public FtpletResult onUploadStart(FtpSession session, FtpRequest request) throws FtpException, IOException {
		// logger.debug("Upload start:<" + request.getArgument() +">" + ", command:" + request.getCommand() + ", requestLine:" + request.getRequestLine());        
		return super.onUploadStart(session, request);
	}

	/*
		@Override  
		public FtpletResult onConnect(FtpSession session) throws FtpException, IOException {
			logger.debug("Connection request from " + session.getClientAddress());		
			return super.onConnect(session);
			} 

	 */

	// http://javadox.com/org.apache.ftpserver/ftplet-api/1.0.5/org/apache/ftpserver/ftplet/FtpSession.html

	@Override
	public FtpletResult onUploadEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
		
		logger.info("Received file:<" + request.getArgument() +"> from <"+ session.getClientAddress().toString() + ">");	// on session 

		String dir = session.getFileSystemView().getWorkingDirectory().getAbsolutePath();
		//		if (dir.startsWith("/"))		// rooturi already ends with '/'
		//			dir = dir.substring(1);

		// logger.debug("session:getWorkingDirectory<" + dir +">");

		FtpletResult r = super.onUploadEnd(session, request);

		// create the full file path as this is not reported by 'request'. there we only have the filename  
		// File f = new File (ftpHomeDir.getAbsolutePath() + dir  + fileSystemSeparator + request.getArgument() );
		File f = new File (ftpHomeDir.getAbsolutePath() + dir, request.getArgument() );

		if  (f.exists() ) {			
			if ( converterQueue != null)
				converterQueue.add(f);	// if an upload is also configured, this is done by the converter
			else
				if( uploaderQueue != null) {
					uploaderQueue.add(f);				
					logger.debug("Queued for upload :<" + f.getAbsolutePath() +">, uploaderQueue size:" + uploaderQueue.size());
				}
			lastTS = f.lastModified();
		}
		else
			logger.error("Uploaded FTP file <" + request.getArgument() + "> not found at <" + f.getAbsolutePath() +">");

		return r;
	}

	
}

