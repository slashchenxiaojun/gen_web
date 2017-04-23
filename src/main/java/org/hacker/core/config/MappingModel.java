package org.hacker.core.config;

import com.jfinal.plugin.activerecord.ActiveRecordPlugin;

import org.hacker.module.movie.model.Movie;
import org.hacker.module.movieman.model.MovieMan;
import org.hacker.module.poster.model.Poster;

/**
 * Generated by JFinal, do not modify this file.
 * <pre>
 * Example:
 * public void configPlugin(Plugins me) {
 *     ActiveRecordPlugin arp = new ActiveRecordPlugin(...);
 *     _MappingKit.mapping(arp);
 *     me.add(arp);
 * }
 * </pre>
 */
public class MappingModel {

  public static void mapping(ActiveRecordPlugin arp) {
    arp.addMapping("ss_movie", "id", Movie.class);
    arp.addMapping("ss_movie_man", "id", MovieMan.class);
    arp.addMapping("ss_poster", "id", Poster.class);
  }
  
}
