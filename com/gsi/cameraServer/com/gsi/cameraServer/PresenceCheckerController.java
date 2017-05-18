package com.gsi.cameraServer;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;


/**
 * simple class to see if a device is present in LAN using a ping style.
 * Has own quartz scheduler 
 *
 * Motion Detection (MD) controller. Does not the motion detection, but switches this 
 * ON and OFF depending if some hosts can be detected in W/LAN e.g. mobile phones or TV from  
 * the owners. If so, the MD may be switched OFF. 
 * 
 * @author gsinne
 *
 */

public class PresenceCheckerController  {


	private static final long serialVersionUID = 1L;

	public enum MOTION_DETECTION { ON, OFF , UNKOWN }

	private long 	delayTime;

	private static 	Logger 						logger 		 = LogManager.getLogger	(PresenceCheckerController.class);
	private 	   	CameraControl 				camControl 	 = null;
	private 		MOTION_DETECTION 			currentState = MOTION_DETECTION.UNKOWN;

	ArrayList 		<String> hosts2ping ;
	private 		Scheduler scheduler;
	String 			cameraBaseUri =  null; 

	private Configuration config;

	private Date lastFireTime = null, nextFireTime = null;
	private long diff =-1L;

	// a local simple scheduler for delayed ON commands
	private ScheduledExecutorService schedulerService;
	/**
	 * the future for delayed ON function
	 */
	private ScheduledFuture<?> 	delayedFuture = null; 

	private DelayedMotionControl delayedMotionControl;

	private List	  <String> 	pingCommandLine;
	private ArrayList <String>  pingFaultStrings;  

	public PresenceCheckerController( Configuration config) throws Exception { 
		
		this.config  	 	 = config;
		delayTime 	 	 	 = Long.parseLong(config.getProperty("MotionControl.ONDelayTime","0"));
		scheduler 		 	 = new StdSchedulerFactory().getScheduler();
		schedulerService 	 = Executors.newScheduledThreadPool( 1 );
		delayedMotionControl = new DelayedMotionControl ( MOTION_DETECTION.UNKOWN);
		// Prepare ping commands 
		pingCommandLine = new ArrayList<>(4);
		pingCommandLine.add("ping");

		if (SystemUtils.IS_OS_WINDOWS)
			pingCommandLine.add("-n");
		else if (SystemUtils.IS_OS_UNIX) {
			pingCommandLine.add("-c");	
		}
		else
			throw new UnsupportedOperationException("Unsupported operating system for ping");
		
		pingCommandLine.add	("3");	// important for u'x 		
		pingCommandLine.add	("127.0.0.1");	// dummy: replaced when used
		
		// "Zielhost nicht erreichbar","Zeitüberschreitung" 
		String [] f =  config.getProperty("MotionControl.pingFaultyAnswer").split(",");
		pingFaultStrings =  new ArrayList<String>(f.length); 
		for (String s : f ) {
			s= s.trim().toLowerCase();
			pingFaultStrings.add(s);
		}				
		// pingFaultStrings =  new ArrayList<String> (Arrays.asList ());	// doesn't allow trim,
		
	}

	/**
	 * 	@param address
	 *  @return true if host is reachable, false if not
	 *   http://www.rgagnon.com/javadetails/java-0093.html
	 *   http://stackoverflow.com/questions/9922543/why-does-inetaddress-isreachable-return-false-when-i-can-ping-the-ip-address
		http://stackoverflow.com/questions/11506321/java-code-to-ping-an-ip-address
		    
	 */

	public boolean testPing(String address) {
		

		try {
			
			pingCommandLine.set(pingCommandLine.size() -1, address); // always the same 
			ProcessBuilder processBuilder = new ProcessBuilder(pingCommandLine);
			Process process 			  = processBuilder.start();

			BufferedReader standardOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String reponseLine;
			
			while ((reponseLine = standardOutput.readLine()) != null)
			{
				// Picks up Windows and Unix unreachable hosts
				for (String fault : pingFaultStrings) {
					if ( reponseLine.toLowerCase().contains(fault))
						return false;
				}
			}

			return true;
		}
		catch (IOException e) {
			logger.error("Unable to connect to <" + address +">");
			return false;
		}
		
//		finally {	makes no sense as only the last is logged
//			// write response to disk in debug mode	
//			if ( (logger.isDebugEnabled()) && completeResponse.length() > 0 )
//				try {
//					FileUtils.writeStringToFile(new File ("./logs/lastPingResponse.log"), completeResponse);
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//		
//		}
	}

	/**
	 * the method that does the work, called from the job.
	 * @param context 
	 * synchronized cause several crons may call this  method, that might overlap
	 */
	public synchronized void doCheck (JobExecutionContext context){

		// loop all adr 
		String hostFound="";
		boolean rc = false; 
		for (String inetadr : hosts2ping) {
			rc 		= testPing (inetadr);  		//	true : host present, switch off control
			if ( rc == true){
				hostFound =  inetadr;
				break;
			}

		}

		lastFireTime = context.getTrigger().getPreviousFireTime();
		nextFireTime = context.getTrigger().getNextFireTime	();
		long diffnew =0L;
		// check if its the end of an cron invall. if so switch MC ON 
		if (lastFireTime != null) {			
			diffnew= (nextFireTime.getTime() - lastFireTime.getTime()) /1000L ;
			if (diff < 0)  
				diff= diffnew;
			else {
				if (diffnew > diff){
					if (logger.isDebugEnabled())
						logger.debug ("Found end of cycle. Last check time <" + Util.dateTimeFormatterNoTZ.format(lastFireTime.toInstant() )  
												 +  ", next check time:" 	  + Util.dateTimeFormatterNoTZ.format (nextFireTime.toInstant())
												 + ", lastDiff:" + diff + ", new diff:" + diffnew);
					else
						logger.info("Found end of cycle. Switching motion control ON. "
								 + "Last check time <" +  Util.dateTimeFormatterNoTZ.format(lastFireTime.toInstant())
								 + ", next check time:" + Util.dateTimeFormatterNoTZ.format (nextFireTime.toInstant()));
								
					setMotionControl(MOTION_DETECTION.ON, "End of cron cycle. Forced motion control ON.");
					diff =-1L;		// reset
					return;
				}
			}
			diff= diffnew;
		}

		logger.debug( (rc ? "Presence of host detected:" + hostFound  : "NO host detected" ) + "; next check time:"  + Util.dateTimeFormatterNoTZ.format (nextFireTime.toInstant()));

		if ( rc == true) 	
			setMotionControl(MOTION_DETECTION.OFF, "Host detected <"+ hostFound +">");
		else 
			setMotionControl(MOTION_DETECTION.ON, "NO host detected." );

	}

	public boolean  start () throws ExceptionFatal {

		// check if the camera is ping-able

		cameraBaseUri = config.getProperty("Camera.URI");

		URI cameraUri = null;
		try {
			cameraUri = new URI (cameraBaseUri);
		} catch (URISyntaxException e) {
			throw  new  ExceptionFatal(e);
		}

		// if ( testPing (	InetAddress.getByName	(cameraUri.getHost()) ) == true)								
		if ( testPing (	cameraUri.getHost() ) == true)
			logger.debug ("Found camera on <" + cameraUri.getHost()+">");  
		else { 
			logger.error ("NO camera found on IP <" + cameraUri.getHost() + ">. Aborting.");
			return false;
		}

		// try to login/logout to see if the username pw are correct
		camControl = new CameraControl( config );
		if (camControl.init() == false)
			return false;
		
		String r = config.getProperty("MotionControl.pingHosts", null);
		
		if (r == null ) {
			logger.info ("No presence supervision cause no supervised hosts defined.");
			return false;
		}
		String[] pings = r.split(",");
		
		if (pings.length == 0) {
			logger.info ("No presence supervision cause no supervised hosts defined.");
			return false;
		}

		hosts2ping = new ArrayList <String> (pings.length);
		r="";
		for ( String p : pings) {
			hosts2ping.add(p.trim()) ;
			r += p.trim() +",";
		}
		
			for ( int i = 1; i < 10 ; i++) {
				String h = config.getProperty("MotionControl.CronSchedule." + i, null); 
				if (h == null)
					break;
				h = h.trim().replace("\"", "");
				Trigger trigger ; 
				try {
					trigger = TriggerBuilder
						.newTrigger()
						.withIdentity	(h , "group1")
						.withSchedule	( CronScheduleBuilder.cronSchedule(h))								
						.build();
				}catch (java.lang.RuntimeException e){
					logger.error("Error on scheduler expresion:<" + h + ">, reason: " + e.getCause().getMessage());
					return false;
				}
				
				JobDataMap data = new JobDataMap();
				data.put("PresenceCheckerController", this);

				JobDetail job = JobBuilder
						.newJob(PresenceCheckerJob.class)
						.withIdentity("MotionControlJob_" + i, "group1")
						.usingJobData(data)							
						.build();

				try {
					scheduler.scheduleJob(job, trigger);
				} catch (SchedulerException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				logger.info (" Supervision of presence with hosts <" + r + ">. Motion detection ON with delay:" + delayTime);
				// logger.info(" Starting motion detection on schedule <" + trigger.getKey() +"> at " + Util.simpleDateFormat.format(trigger.getNextFireTime()));
				logger.info(" Starting motion detection on schedule <" + trigger.getKey() +"> at "						
						+ Util.dateTimeFormatterNoTZ.format(trigger.getNextFireTime().toInstant()));
					        		
			}

			try {
				scheduler.start();
			} catch (SchedulerException e) {

				e.printStackTrace();
			}

//		} catch (SchedulerException e) {
//			throw new ExceptionFatal (e);
//		}
		return true;

	}

	public void shutdown(){

		// Switch on motion control in any case when this program is shutdown. if possible .. WINDOWS does not work

		if ( camControl != null){	// perhaps not found
			// camControl.setMotionControl(MOTION_DETECTION.ON, "Shutdown");	// TODO funzt nicht		
			// camControl.logout(); // this one blocks 
			camControl.shutdown();
		}
		
		schedulerService.shutdownNow();
	}


	/**
	 * initiates the switch with a potential delay determined by  configuration 'MotionControl.DelayTime' .
	 * if a command is already time queued, it is aborted: only the last cmd counts
	 * 
	 * @param newDirection
	 * 
	 */

	private  void setMotionControl (final MOTION_DETECTION newDirection , String reason) {

		// logger.debug("MotionControl:" + newDirection + "; Reason: " + reason );   // UNKOWN  may be init

		// OFF: is started immediately, everything else is cancelled 
		if ( newDirection == MOTION_DETECTION.OFF ) {

			if (currentState == MOTION_DETECTION.OFF && delayedMotionControl.getDesiredState() == MOTION_DETECTION.UNKOWN)
				return;

			if ( delayedFuture 	!= null &&  delayedFuture.isDone() == false){	// already scheduled -> no action) {
				boolean rc = delayedFuture.cancel(false);
				delayedMotionControl.setDesiredState (MOTION_DETECTION.UNKOWN);
				logger.debug("Scheduled ON cancelled. Cancel rc:" + rc);
			}
			if ( camControl.setMotionControl(newDirection, reason ) == true)
				currentState = newDirection;
			return;
		}

		// ON 
		if (currentState == MOTION_DETECTION.ON)
			return;

		if (delayedMotionControl.getDesiredState() != MOTION_DETECTION.UNKOWN) {	// cmd scheduled	
			logger.debug("ON command already scheduled.");
			return ;
		}

		delayedMotionControl.setDesiredState(newDirection);
		delayedFuture 		 = schedulerService.schedule(delayedMotionControl, delayTime, TimeUnit.SECONDS);
		logger.info("Scheduled motion detection " + newDirection  + " in "  + delayedFuture.getDelay(TimeUnit.SECONDS) + " seconds. Reason:" + reason);
	}


	/**
	 * initiates the switch with a potential delay determined by  configuration 'MotionControl.DelayTime' .
	 * if a command is already time queued, it is aborted: only the last cmd counts
	 * 
	 * @param newDirection
	 * 

@Deprecated	too complicate for this simple task 

	private void scheduleMotionControl (final MOTION_DETECTION newDirection ) {

		// logger.debug ("ScheduleMotionControl to :" + newDirection  );

		if ( currentState != newDirection) { 

			logger.debug ("ScheduleMotionControl: New direction:" + newDirection );

			// if  different switch direction, cancel previous job


			try {
				JobDetail a = scheduler.getJobDetail( jobKeyCameraCommand );	// see below for jobkey 

				// is a job already pending ?  Only the job has the data assigned.

				if (a != null) {	// if job already there, remove or delay
					MOTION_DETECTION scheduledOldDirection = (MOTION_DETECTION) a.getJobDataMap().get("direction");

					if (scheduledOldDirection == newDirection ) {
						// already there and same direction: do nothinf. reschedule ??? 
						return;
					}
					else { // already there and different direction. kill old job
						scheduler.deleteJob(jobKeyCameraCommand);
					}

				}
			} catch (SchedulerException e) {
				e.printStackTrace();
				return;
			}

			// off is started immediately 
			if ( newDirection == MOTION_DETECTION.OFF){ 
				camControl.setMotionControl(newDirection);
				return;
			}


			Trigger trigger = TriggerBuilder
					.newTrigger()
					.withIdentity ( triggerKeyScheduleControl )
					.startAt(new Date(new Date().getTime()  + (delayTime * 1000L)))
					.build();


			// create a job: pass PresenceCheckerController and new direction.
			JobDataMap 	jobData 	 = new JobDataMap();
			jobData.put ("direction", 					newDirection);
			jobData.put ("PresenceCheckerController", 	this);


			JobDetail job = JobBuilder
					.newJob			( CameraCommandJob.class )
					.withIdentity	( jobKeyCameraCommand )
					.usingJobData	( jobData )							
					.build();

			try {
				scheduler.scheduleJob(job, trigger);
			} catch (SchedulerException e) {
				e.printStackTrace();
				return;
			}			

			logger.debug("Scheduled motion detection " + newDirection + " for " + FTPServer.sdf.format(trigger.getNextFireTime()));
		}
	}

	public CameraControl getCamControl() {
		return camControl;
	}

	public MOTION_DETECTION getCurrentState() {
		return currentState;
	}

	public void setCurrentState(MOTION_DETECTION currentState) {
		this.currentState = currentState;
	}
	 */


	class DelayedMotionControl implements Runnable {

		MOTION_DETECTION desiredState;

		public void setDesiredState(MOTION_DETECTION desiredState) {
			this.desiredState = desiredState;
		}


		public MOTION_DETECTION getDesiredState() {
			return desiredState;
		}


		public DelayedMotionControl(MOTION_DETECTION newState) {
			this.desiredState = newState;
		}

		public DelayedMotionControl() {
			this.desiredState = MOTION_DETECTION.UNKOWN;
		}


		@Override public void run( ) {

			if ( camControl.setMotionControl(desiredState, "Scheduled command") ==  true)
				currentState = desiredState;
			desiredState = MOTION_DETECTION.UNKOWN;	// sign that we're done
		};
	}


}
