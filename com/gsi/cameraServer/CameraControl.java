package com.gsi.cameraServer;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

import org.apache.http.ParseException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gsi.cameraServer.PresenceCheckerController.MOTION_DETECTION;
/**
 * performs actions on the camera. 
 * login/logout
 * initiate camera commands
 * @author gsinne
 *
 */
public class CameraControl {

	// we'll reuse as much as we can as we have no multithreading
	
	private CloseableHttpClient 	httpclient ;
	private HttpGet 				httpget;
	private CloseableHttpResponse 	response;
	private static Logger 			logger = LogManager.getLogger	(CameraControl.class);
	private String 					baseUri ;
	
	private boolean loggedIn 	= false;
	/**
	 * contains the URLs from the configuration. 
	 */
	private ArrayList <String> 		controlCmd;
	private Configuration config;	 
	
	//	https://hc.apache.org/httpcomponents-client-ga/examples.html
	// !!! https://hc.apache.org/httpcomponents-client-ga/httpclient/examples/org/apache/http/examples/client/ClientFormLogin.java 

	public CameraControl( Configuration config )  {

		baseUri 	=  config.getProperty("Camera.URI" );
		this.config = config;
				
	}

	public boolean init() {
		
		
		URI u = null;
		try {
			u = new URI (baseUri);
		} catch (URISyntaxException e) {
			logger.error("URISyntaxException:" + e.getMessage());
			return false;				
		}
				
		for ( int i = 1; i < 10 ; i++) {
			String h = config.getProperty("CameraCmd." + i, null); 
			if (h == null)
				break;
			if (controlCmd == null)
				controlCmd = new ArrayList <String> (4);
			controlCmd.add(h.trim());
		}
		
		//https://hc.apache.org/httpcomponents-client-ga/httpclient/examples/org/apache/http/examples/client/ClientAuthentication.java

		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(
				new AuthScope(u.getHost(), u.getPort() > 0 ? u.getPort() : 80  ),
				new UsernamePasswordCredentials (config.getProperty("Camera.UserName", null).trim(), 
												 config.getProperty("Camera.Password", null).trim())
									);

		httpclient = HttpClients.custom() .setDefaultCredentialsProvider(credsProvider) .build();
		httpget = new HttpGet();	// rest adapted by seturi
		
		if (login() == true )
			logger.debug("Camera login successful.");		
		else {
			logger.info("Camera login NOT successful. No Camera Control.");
			shutdown();
			return false;
		}
		return loggedIn;
		
	}
	
	/**
	 * @param direction : true: switch ON <br>, false OFF
	 * @param reason 
	 * @return
	 */
	public  boolean setMotionControl(MOTION_DETECTION direction, String reason) {

		if (loggedIn == false )
			login(); 	// no more checks needed as done earlier 
			
		logger.info("Switching motion control:" + direction +"; Reason:" + reason );
 
		try {
			
			/* motion control : name = 1...4, für area, enable =0/1
			   http://10.0.0.118/cgi-bin/hi3510/param.cgi?cmd=setmdattr&-enable=1&-name=1
			   	
   				config:  http://$IP_PORT/cgi-bin/hi3510/param.cgi?cmd=setmdattr&-enable=$ENABLE_DISABLE&-name=1
   						 http://10.0.0.118/cgi-bin/hi3510/param.cgi?cmd=setmdattr&-enable=1&-name=1
			  	Muss man alle zustände vorher merken ???  
			   		eher nicht das ist besser über 4 konfiguration zu machen  
			    
			 */
			for (String cmdConf : controlCmd) { 
				
				cmdConf = cmdConf.replace ("${Camera.URI}", 	 baseUri );
				cmdConf = cmdConf.replace ("${ENABLE_DISABLE_INT}",  (direction == MOTION_DETECTION.OFF ? "0" 	  : "1") ); 
				cmdConf = cmdConf.replace ("${ENABLE_DISABLE_BOOL}", (direction == MOTION_DETECTION.OFF ? "false" : "true") );

				httpget.setURI(new URI ( cmdConf ) );
				logger.debug ("Motion control cmd <" + httpget.getRequestLine() + ">");
				
				response = httpclient.execute(httpget);
				
				if (response.getStatusLine().getStatusCode() == 200) {
					logger.debug("Successfull changed motion control to " + direction + ". Cam Msg:<"+ response.getStatusLine() +">," + EntityUtils.toString(response.getEntity() ));
				}
				else  
					logger.error ("Motion control error:" + EntityUtils.toString(response.getEntity() ));
				
				if  (Thread.currentThread().isInterrupted()){ // can be 
					logger.warn("setMotionControl() interrupted. Not all cmds might be finished.");
					break;
				}
				response.close();
			}
				
		} catch (ParseException | IOException | URISyntaxException e) {
			logger.error(e,e);
			return false;
		}
		
		logout();
		
		return true;

	}

	public boolean login () {
		
		if ( loggedIn == true)
			return true;

		try {
			httpget.setURI(new URI  (baseUri + "/web/"));			

			logger.debug("Executing request:" + httpget.getRequestLine());

			response = httpclient.execute(httpget);

			logger.debug("Response received:" +response.getStatusLine());

			if (response.getStatusLine().getStatusCode() == 200) 
				logger.debug  ("Successfull logged into camera at " + httpget.getURI() + ">; Camera returned msg:<"+ response.getStatusLine() +">");
			else {
				logger.error ("Error logging in camera: Response:" + EntityUtils.toString(response.getEntity()));
				return false;
			}
			response.close();	
		} catch (ParseException | IOException | URISyntaxException  e) {
			logger.error(e,e);
			return false;
		}
		
		loggedIn = true;
		return true;

	}
	
	/**
	 *  logout -------------------------------------------------------------------------------------------------------
	 * @return 
	 * @return true: ok, <br> false: something happened 
	 */
	public boolean logout(){

		if ( loggedIn == false)
			return true;
		
		try {
			httpget.setURI(new URI (baseUri + "/logout.html"));

			response = httpclient.execute(httpget);

			if (response.getStatusLine().getStatusCode() == 200) {
				logger.debug ("Successfull logged off camera at " + httpget.getURI()  + ">; Cam Msg:<"+ response.getStatusLine() +">");
			}
			else  
				logger.error (EntityUtils.toString(response.getEntity()));
		
			response.close();
			
		} catch (ParseException | IOException | URISyntaxException e) {
			logger.error(e,e);
			return false;
		}
		loggedIn = false;
		return true;
	}

	public void shutdown (){

		try {
			// logout();	// TODO blocks on shutdown
			httpclient.close();
		} catch (IOException e) {		
			e.printStackTrace();
		}

		httpclient = null;
	}

}
