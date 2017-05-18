
package com.gsi.cameraServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
// to make the password more secure :http://www.jasypt.org/maven.html

public class Configuration  {

	public Configuration() throws ExceptionFatal{
		
		logger = org.apache.logging.log4j.LogManager.getLogger(Configuration.class);
		
		String homeDir = System.getProperty("user.dir") ;	    	  

		resourceFile = new File(homeDir +  "/" + "CameraServer.properties").getAbsolutePath();

		config = new Properties();
		try {
			config.load(new FileInputStream ( resourceFile ) );
			logger.info("Successfull read configuration at:<"+resourceFile +">" );					
		} catch (IOException e) {
			logger.fatal("Unable to load property file expected at <" + resourceFile +">:Message:"+ e.getMessage());
			throw new  ExceptionFatal(e);
		} 

	}


	static String 		resourceFile = null;	
	static Properties 	config = null; 
	private static org.apache.logging.log4j.Logger logger=null; 

	public String getProperty(String key, String defValue) {

		String res = getProperty(key);
		if (res == null) 
			return defValue;	// maybe null
		return res.trim();		// !!! 
	}

	public  String getProperty(String key) {	    
		
		String result = config.getProperty(key);
		if (result == null ) {
			// logger.warn("IOException: unable to load property <" + key + "> from file:<" + resourceFile +">"); 
			return null;
		}
		result.trim();

		return result;
	}

	public void setProperty(String key, String value) {
		config.setProperty(key, value);

	}

	public void save() {

		logger.debug ("Writing to properties file:"+ resourceFile);

		try {
			config.store (new FileOutputStream ( resourceFile) , "Created/modified by FTPServer. Edit only if FTPServer is down.");
		} catch (IOException e) {		
			e.printStackTrace();
		}


	}

}
