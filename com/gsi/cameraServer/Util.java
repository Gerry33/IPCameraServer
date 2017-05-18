package com.gsi.cameraServer;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;


public class Util {
	
	// http://stackoverflow.com/questions/6840803/simpledateformat-thread-safety 
	// public  SimpleDateFormat  		simpleDateFormat 		= new SimpleDateFormat		   ("yyyy-MM-dd HH:mm:ss"); not thread safe
	// instead:
	public  static DateTimeFormatter dateTimeFormatterNoTZ 	= DateTimeFormatter.ofPattern  ("dd-MM-yyyy HH:mm:ss").withZone ( ZoneId.systemDefault() );	
	private static 		 String 	fileSystemDelimiter 			= null;
		

	public static String getFileSizeAsString  (long size) {

		String unit = "B";

		if (size > 1024) {
			size = size / 1024;
			unit= "kB";
		}
		if (size > 1024) {
			size = size / 1024; 
			unit = "MB";
		}
		return size + " " + unit;
	}

	public static String getSystemFileDelimiter (){
		if (fileSystemDelimiter == null)  
			fileSystemDelimiter = System.getProperty("file.separator");
		return fileSystemDelimiter;
	}
	
	/**
	 * convert seconds to hh:mm:ss
	 * @param totalSecs
	 * @return
	 */
	public static String convertSeconds2HH_MM_String (long totalSecs){
		// return String.format("%02d:%02d:%02d", totalSecs / 3600 , (totalSecs % 3600) / 60 , totalSecs % 60);	
		return String.format("%02d:%02d", totalSecs / 3600 , (totalSecs % 3600) / 60);
	}
	
	public static String convertSeconds2HH_MM_SS_String (long totalSecs){
		return String.format("%02d:%02d:%02d", totalSecs / 3600 , (totalSecs % 3600) / 60 , totalSecs % 60);
		
	}
	/**
	 * convert seconds to mm:ss
	 * @param totalSecs
	 * @return
	 */
	public static String convertSeconds2MM_SS_String (long totalSecs){
		return String.format("%02d:%02d",  (totalSecs % 3600) / 60 , totalSecs % 60);	
	}
	
	
}

