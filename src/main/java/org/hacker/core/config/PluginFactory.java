package org.hacker.core.config;

import javax.sql.DataSource;

import org.hacker.core.Play;

import com.jfinal.config.Plugins;
import com.jfinal.kit.PropKit;
import com.jfinal.plugin.activerecord.ActiveRecordPlugin;
import com.jfinal.plugin.druid.DruidPlugin;

public class PluginFactory {
	public static void startActiveRecordPlugin() {
    DruidPlugin dp = getDruidPlugin();
    ActiveRecordPlugin arp = getActiveRecordPlugin(dp.getDataSource());
    
    initModel(arp);
		dp.start();
		arp.start();
	}

  public static void startActiveRecordPlugin(Plugins me) {
    DruidPlugin dp = getDruidPlugin();
    ActiveRecordPlugin arp = getActiveRecordPlugin(dp.getDataSource());
    
    initModel(arp);
    me.add(dp);
    me.add(arp);
  }
  
  private static DruidPlugin getDruidPlugin() {
    return new DruidPlugin(
        getProperty(Play.CONFIG_JDBC_URL), 
        getProperty(Play.CONFIG_JDBC_USERNAME), 
        getProperty(Play.CONFIG_JDBC_PASSWORD).trim());
  }
  
  private static ActiveRecordPlugin getActiveRecordPlugin(DataSource dataSource) {
    ActiveRecordPlugin arp = new ActiveRecordPlugin(dataSource);
    if(getPropertyToBoolean(Play.CONFIG_JFINAL_MODE, false)){
      arp.setShowSql(true);
    }
    arp.setTransactionLevel(getPropertyToInteger(Play.CONFIG_JDBC_TRANLEVEL));
    return arp;
  }
  
  /**
   * mapping model and mapping sql
   */
  private static void initModel(ActiveRecordPlugin arp) {
    MappingModel.mapping(arp);
  }
  
	/**
	 * 获取play.properties的配置
	 * @param key
	 * @return
	 */
	public static String getProperty(String key) {
		return PropKit.use("play.properties", "UTF-8").get(key);
	}
	
  public static Integer getPropertyToInteger(String key) {
    return PropKit.use("play.properties", "UTF-8").getInt(key);
  }
	 
	public static Boolean getPropertyToBoolean(String key, Boolean defaultValue) {
		return PropKit.use("play.properties", "UTF-8").getBoolean(key, defaultValue);
	}
}
