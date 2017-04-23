package org.hacker.core;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.persistence.PersistenceException;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Model;
import com.jfinal.plugin.activerecord.TableMapping;

public class BaseService {
  /**
   * 获取临时态集合的补集
   * 这个补集是最终要从数据库中删除的集合
   * 
   * persistentSet: B1, B2, B3, B4
   * transientSet: B2, B3, B5, B6
   * ComplementSet: B1, B4
   * 
   * | persistentSet | transientSet | operation                 |
   * | NULL          | NULL         | NOTHING                   |
   * | NULL          | SET          | NOTHING                   |
   * | SET           | NULL         | DELETE ALL persistentSet  |
   * | SET           | SET          | DELETE ComplementSet      |
   * 
   * @param persistentSet 持久态集合(使用model从数据库中获取的集合)
   * @param transientSet 临时态集合(用户新添但还没有持久化的集合)
   * @return
   */
  protected <E> Collection<E> getComplementSetByTransient(Collection<E> persistentSet, Collection<E> transientSet) {
    /**
     * 如果持久态没有集合,那么就不需要删除任何集合直接返回null,具体如下状态机
     * 
     * | persistentSet | transientSet | operation                 |
     * | NULL          | NULL         | NOTHING                   |
     * | NULL          | SET          | NOTHING                   |
     * | SET           | NULL         | DELETE ALL persistentSet  |
     * | SET           | SET          | DELETE ComplementSet      |
     */
    if (persistentSet == null) return null;
    else if (transientSet == null) return persistentSet;
    Collection<E> resultCollection = new ArrayList<>(persistentSet.size());
    for (E perModel : persistentSet) {
      boolean exist = false;
      for (E traModel : transientSet) {
        if (perModel.equals(traModel)) {
          exist = true; break;
        }
      }
      if (!exist) resultCollection.add(perModel);
    }
    return resultCollection;
  }
  
  /**
   * 获取持久态集合的补集
   * 这个补集是最终要从数据库中添加的集合
   * 
   * persistentSet: B1, B2, B3, B4
   * transientSet: B2, B3, B5, B6
   * ComplementSet: B5, B6
   * 
   * | persistentSet | transientSet | operation                 |
   * | NULL          | NULL         | NOTHING                   |
   * | NULL          | SET          | INSERT ALL transientSet   |
   * | SET           | NULL         | NOTHING                   |
   * | SET           | SET          | INSERT ComplementSet      |
   * 
   * @param persistentSet 持久态集合(使用model从数据库中获取的集合)
   * @param transientSet 临时态集合(用户新添但还没有持久化的集合)
   * @return
   */
  protected <E> Collection<E> getComplementSetByPersistent(Collection<E> persistentSet, Collection<E> transientSet) {
    /**
     * 如果持久态没有集合,那么就不需要新添任何集合直接返回null,具体如下状态机
     * 
     * | persistentSet | transientSet | operation                 |
     * | NULL          | NULL         | NOTHING                   |
     * | NULL          | SET          | INSERT ALL transientSet   |
     * | SET           | NULL         | NOTHING                   |
     * | SET           | SET          | INSERT ComplementSet      |
     */
    if (transientSet == null) return null;
    else if (persistentSet == null) return transientSet;
    Collection<E> resultCollection = new ArrayList<>(persistentSet.size());
    for (E traModel : transientSet) {
      boolean exist = false;
      for (E perModel : persistentSet) {
        if (perModel.equals(traModel)) {
          exist = true; break;
        }
      }
      if (!exist) resultCollection.add(traModel);
    }
    return resultCollection;
  }
  
  protected <E extends Model<M>, M extends Model<M>> void batchInsert4OneToMany(List<E> model) {
    if (model != null && model.size() > 0) {
      for (int index : Db.batchSave(model, model.size())) 
        if (index < 0 && index != Statement.SUCCESS_NO_INFO) throw new PersistenceException("Oop! batch insert fail. index: " + index);
    }
  }
  
  /**
   * 给ManyToMany关联的Model作批量添加操作
   * guideModel需要持久化
   * modelList可是持久化也可以是瞬态
   * 
   * @param guideModel 主导表对应的model(持久化)
   * @param modelCollection 引导表对应的modelList(持久化)
   * @param sql 执行的sql语句
   * @return
   */
  protected <E extends Model<M>, M extends Model<M>> void batchInsert4ManyToMany(Model<?> guideModel, List<E> modelCollection, String sql) {
    if (modelCollection != null && modelCollection.size() > 0) {
      int modelSize = modelCollection.size();
      Object[][] paras = new Object[modelSize][];
      for (int i = 0; i < modelSize; i++) {
        Model<M> model = modelCollection.get(i);
        // 默认的主键是id
        if (model.get("id") == null && !model.save()) throw new PersistenceException("Oop! batch insert fail. modelCollection persistence fail.");
        paras[i] = new Object[] { guideModel.get("id"), model.get("id") };
      }
      for (int index : Db.batch(sql, paras, modelSize)) {
        if (index < 0 && index != Statement.SUCCESS_NO_INFO) throw new PersistenceException("Oop! batch insert fail. index: " + index);
      }
    }
  }
  
  protected <E extends Model<M>, M extends Model<M>> void batchDelete4OneToMany(List<E> model) {
    if (model != null && model.size() > 0) {
      String tableName = TableMapping.me().getTable(model.get(0).getClass()).getName();
      String[] primaryKeys = TableMapping.me().getTable(model.get(0).getClass()).getPrimaryKey();
      // 构建delete sql
      StringBuilder sql = new StringBuilder("delete from `").append(tableName).append("` where ");
      for (int i = 0; i < primaryKeys.length; i++) {
        if (i > 0) {
          sql.append(" and ");
        }
        sql.append("`").append(primaryKeys[i]).append("` = ").append("?");
      }
      int size = model.size();
      Object[][] paras = new Object[size][];
      for (int i = 0; i < size; i++) {
        Object[] para = new Object[primaryKeys.length];
        for (int j = 0; j < primaryKeys.length; j++) {
          para[j] = model.get(i).get(primaryKeys[j]);
        }
        paras[i] = para;
      }
      int[] result = Db.batch(sql.toString(), paras, size);
      for (int index : result) 
        if (index < 0 && index != Statement.SUCCESS_NO_INFO) throw new PersistenceException("Oop! batch delete fail. index: " + index);
    }
  }
  
  /**
   * 给ManyToMany关联的Model作批量删除操作
   * 
   * @param guideModel 主导表对应的model
   * @param modelCollection 引导表对应的modelList
   * @param sql 执行的sql语句
   * @return
   */
  protected <E extends Model<M>, M extends Model<M>> void batchDelete4ManyToMany(Model<?> guideModel, List<E> modelCollection, String sql) {
    if (modelCollection != null && modelCollection.size() > 0) {
      int modelSize = modelCollection.size();
      Object[][] paras = new Object[modelSize][];
      for (int i = 0; i < modelSize; i++) {
        Model<M> model = modelCollection.get(i);
        if (!model.delete()) throw new PersistenceException("Oop! batch delete fail. model: " + model);
        // 默认的主键是id
        paras[i] = new Object[] { guideModel.get("id"), model.get("id") };
      }
      int[] result = Db.batch(sql, paras, modelSize);
      for (int index : result) {
        // executeBatch返回值参考Java API
        if (index < 0 && index != Statement.SUCCESS_NO_INFO) throw new PersistenceException("Oop! batch delete fail. index: " + index);
      }
    }
  }
}
