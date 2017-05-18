package com.gsi.cameraServer;


import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class PresenceCheckerJob implements Job {

	// no constructor allowed
	@Override
	public void execute(JobExecutionContext context ) throws JobExecutionException {

		PresenceCheckerController pcc = (PresenceCheckerController) context.getJobDetail().getJobDataMap().get("PresenceCheckerController"); 
		pcc.doCheck ( context  ); 

	}

}
