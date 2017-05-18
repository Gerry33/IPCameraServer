package com.gsi.cameraServer;


public class ExceptionRetry extends Exception {
	/*
	 * To handle light logic error that are not able to continue.
	 * NO stack dumps. this is left to general exception   
	 */

		public ExceptionRetry(String msg) {
			// super(msg);
			Starter.getLogger().error(this, this);
		}

		public ExceptionRetry(Exception e) {
			// super(e);
			Starter.getLogger().error(this, this);
		}

		private static final long serialVersionUID = 8520769922341245425L;

}
