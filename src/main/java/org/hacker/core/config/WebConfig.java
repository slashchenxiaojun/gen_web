package org.hacker.core.config;

import org.apache.log4j.Logger;
import org.hacker.aop.handler.GlobalHandler;
import org.hacker.aop.interceptor.ErrorInterceptor;
import org.hacker.core.Play;

import com.jfinal.config.Constants;
import com.jfinal.config.Handlers;
import com.jfinal.config.Interceptors;
import com.jfinal.config.JFinalConfig;
import com.jfinal.config.Plugins;
import com.jfinal.config.Routes;
import com.jfinal.core.JFinal;
import com.jfinal.template.Engine;

public class WebConfig extends JFinalConfig {
	Logger log = Logger.getLogger(WebConfig.class);
	
	@Override
	public void configConstant(Constants me) {
		loadPropertyFile("play.properties");
		me.setDevMode(getPropertyToBoolean(Play.CONFIG_JFINAL_MODE, false));
	}

	@Override
	public void configRoute(Routes me) {
	  MappingRoute.mapping(me);
	}

	@Override
	public void configPlugin(Plugins me) {
		PluginFactory.startActiveRecordPlugin(me);
	}

	@Override
	public void configInterceptor(Interceptors me) {
		me.addGlobalActionInterceptor(new ErrorInterceptor());
	}

	@Override
	public void configHandler(Handlers me) {
		me.add(new GlobalHandler());
	}

  @Override
  public void configEngine(Engine arg0) {
    
  }
  
  public static void main(String[] args) {
    JFinal.start("src/main/webapp", 8080, "/", 1);
  }
}