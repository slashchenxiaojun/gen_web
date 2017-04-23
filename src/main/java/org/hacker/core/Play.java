package org.hacker.core;

import com.jfinal.kit.PropKit;

public class Play {
  public static final String PLAY = "play.properties";
	
  public static final String 
  CONFIG_JDBC_URL       = "jdbc.url",
  CONFIG_JDBC_USERNAME  = "jdbc.user",
  CONFIG_JDBC_PASSWORD  = "jdbc.pass",
  CONFIG_JDBC_TRANLEVEL = "jdbc.transactionLevel",
  CONFIG_JFINAL_MODE    = "jfinal.devMode";
  
	public static boolean isJFinalDebug() {
	  return getProperty(CONFIG_JFINAL_MODE).equals("true");
	}
	
	public static String getProperty(String key) {
		return PropKit.use(PLAY, "UTF-8").get(key);
	}
}
