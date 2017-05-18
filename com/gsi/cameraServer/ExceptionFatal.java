package com.gsi.cameraServer;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionFatal extends Exception {
	/*
	 * To handle fatals logic error that are not able to continue.
	 * NO stack dumps. this is left to general exception   
	 */

		public ExceptionFatal(String msg) {
			super(msg);
			Starter.getLogger().error(this, this);
		}

		public ExceptionFatal(Exception e) {
			super(e);
			Starter.getLogger().error(this, this);
		}
		public ExceptionFatal(Exception e, String s ) {

			super(e);
			Starter.getLogger().fatal(s + ":"+ e.getMessage());
			StringWriter stack = new StringWriter();
			e.printStackTrace(new PrintWriter(stack));
			Starter.getLogger().fatal("ExceptionFatal: Caught exception. Stacktrace: " + stack.toString() );
			
		}

		private static final long serialVersionUID = 8520769922341245425L;

}
