package org.hacker.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import org.hacker.exception.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Model;
import com.jfinal.plugin.activerecord.Table;
import com.jfinal.plugin.activerecord.TableMapping;

/**
 * BaseModel 只提供基础方法
 * JPAModel是一个JPA规范的阉割版，目前只支持，关联的CUD，并且其中的SQL支持测试MYSQL5.5
 * 
 * @version 1.0
 * @author Mr.J.(slashchenxiaojun@sina.com)
 * 
 * @date 2017-03-13
 */
public abstract class JPAModel<M extends JPAModel<M>> extends Model<M> {
  // update时采取的是否需要关联删除原关联对象的策略
  private static final boolean cascadeDeleteObject = false;
  private static final Logger LOG = LoggerFactory.getLogger(JPAModel.class);
  private static final long serialVersionUID = -4334213295707048279L;
  private static final ThreadLocal<StringBuffer> sqlLocal = new ThreadLocal<>();
  private static final ThreadLocal<List<Object>> valueLocal = new ThreadLocal<>();
  
  /**
   * PS: 非事务安全!
   * @return
   */
  @Override
  public boolean save() {
    /**
     * 自动构建主键有3种方式:
     * 使用MYSQL的主键自增 无需任何代码
     * 使用ORACLE的序列来充当主键，`SEQ_NAME`开发者自定义
     * this.set(TableMapping.me().getTable(getClass()).getPrimaryKey()[0], "SEQ_NAME.nextval");
     * 使用UUID来充当主键
     * this.set(TableMapping.me().getTable(getClass()).getPrimaryKey()[0], KCode.UUID().replace("-", ""));
     */
    Table table = TableMapping.me().getTable(getClass());
    if (table.getColumnTypeMap().containsKey("create_date")) {
      this.set("create_date", new Date());
    }
    if (table.getColumnTypeMap().containsKey("modify_date")) {
      this.set("modify_date", new Date());
    }
    boolean success = super.save();
    if (success) {
      try {
        cascadeInsert0(table);
      } catch (IllegalArgumentException | IllegalAccessException e) {
        e.printStackTrace();
        return false;
      }
    }
    return success;
  }
  
  /**
   * 不做联级操作的update
   * @return
   */
  public boolean updateWithOutCascade() {
    com.jfinal.plugin.activerecord.Table table = TableMapping.me().getTable(getClass());
    if (table.getColumnTypeMap().containsKey("modify_date")) {
      this.set("modify_date", new Date());
    } else {
      throw new PersistenceException("Oop! modify_date is not exit, if you want to use JPAModel, modify_date must be exit.");
    }
    boolean success = super.update();
    return success;
  }
  
  /**
   * PS: 非事务安全!
   */
  @Override
  public boolean update() {
    com.jfinal.plugin.activerecord.Table table = TableMapping.me().getTable(getClass());
    if (table.getColumnTypeMap().containsKey("modify_date")) {
      this.set("modify_date", new Date());
    } else {
      throw new PersistenceException("Oop! modify_date is not exit, if you want to use JPAModel, modify_date must be exit.");
    }
    boolean success = super.update();
    if (success) {
      try {
        cascadeUpdate0(table, cascadeDeleteObject);
      } catch (IllegalArgumentException | IllegalAccessException | InstantiationException e) {
        e.printStackTrace();
        return false;
      }
    }
    return success;
  }

  @Override
  public boolean delete() {
    com.jfinal.plugin.activerecord.Table table = TableMapping.me().getTable(getClass());
    // 由于walle生成的代码中，中间表和对象表的外键采取了cascade策略，所有如果先调用对象表的delete()会联级删除中间表
    try {
      cascadeDelete0(table);
    } catch (IllegalArgumentException | IllegalAccessException | InstantiationException e) {
      e.printStackTrace();
      return false;
    }
    boolean success = super.delete();
    return success;
  }

  @Override
  public boolean deleteById(Object idValue) {
    Table table = TableMapping.me().getTable(getClass());
    // 由于walle生成的代码中，中间表和对象表的外键采取了cascade策略，所有如果先调用对象表的delete()会联级删除中间表
    try {
      cascadeDelete0(table);
    } catch (IllegalArgumentException | IllegalAccessException | InstantiationException e) {
      e.printStackTrace();
      return false;
    }
    boolean success = super.deleteById(idValue);
    return success;
  }

  @Override
  public boolean deleteById(Object... idValues) {
    Table table = TableMapping.me().getTable(getClass());
    // 由于walle生成的代码中，中间表和对象表的外键采取了cascade策略，所有如果先调用对象表的delete()会联级删除中间表
    try {
      cascadeDelete0(table);
    } catch (IllegalArgumentException | IllegalAccessException | InstantiationException e) {
      e.printStackTrace();
      return false;
    }
    boolean success = super.deleteById(idValues);
    return success;
  }
  
  public M select() {
    return select("*");
  }
  
  @SuppressWarnings("unchecked")
  public M select(String select) {
    StringBuffer sql = sqlLocal.get();
    List<Object> args = valueLocal.get();
    if (sql == null) {
      sql = new StringBuffer("select " + select + " from ");
      sqlLocal.set(sql);
    }
    if (args == null) {
      args = new ArrayList<Object>();
      valueLocal.set(args);
    }
    Set<Entry<String, Object>> set = this._getAttrsEntrySet();
    Iterator<Entry<String, Object>> it = set.iterator();
    Table table = TableMapping.me().getTable(getClass());
    sql.append("`" + table.getName() + "`").append(" where 1 = 1");
    while (it.hasNext()) {
      Entry<String, Object> entry = it.next();
      sql.append(" and `").append(entry.getKey()).append("` = ?");
      args.add(entry.getValue());
    }
    return (M) this;
  }
  
  @SuppressWarnings("unchecked")
  public M groupBy(String name) throws IllegalAccessException {
    StringBuffer sql = sqlLocal.get();
    if (sql == null) throw new IllegalAccessException("Oop! must use method 'select()' first.");
    sql.append(" group by ").append(name);
    return (M) this;
  }
  
  @SuppressWarnings("unchecked")
  public M haveing(String content) throws IllegalAccessException {
    StringBuffer sql = sqlLocal.get();
    if (sql == null) throw new IllegalAccessException("Oop! must use method 'select()' first.");
    sql.append(" haveing ").append(content);
    return (M) this;
  }
  
  @SuppressWarnings("unchecked")
  public M orderBy(String name) throws IllegalAccessException {
    StringBuffer sql = sqlLocal.get();
    if (sql == null) throw new IllegalAccessException("Oop! must use method 'select()' first.");
    sql.append(" order by ").append(name);
    return (M) this;
  }
  
  @SuppressWarnings("unchecked")
  public M asc() throws IllegalAccessException {
    StringBuffer sql = sqlLocal.get();
    if (sql == null) throw new IllegalAccessException("Oop! must use method 'select()' first.");
    sql.append(" asc");
    return (M) this;
  }
  
  @SuppressWarnings("unchecked")
  public M desc() throws IllegalAccessException {
    StringBuffer sql = sqlLocal.get();
    if (sql == null) throw new IllegalAccessException("Oop! must use method 'select()' first.");
    sql.append(" desc");
    return (M) this;
  }
  
  public List<M> fetch(int start, int count) throws IllegalAccessException {
    StringBuffer sql = sqlLocal.get();
    List<Object> args = valueLocal.get();
    if (sql == null) throw new IllegalAccessException("Oop! must use method 'select()' first.");
    sql.append(" limit ").append(start).append(", ").append(count);
    LOG.debug("SQL: {}", sql.toString());
    // clear sql ,args
    sqlLocal.set(null);
    valueLocal.set(null);
    List<M> result = find(sql.toString(), args.toArray());
    // for JPA FetchType.EAGER
    // can't be achieved...
//    for (Field field : this.getClass().getDeclaredFields()) {
//      field.setAccessible(true);
//      System.out.println(field.getName());
//      if (field.getAnnotation(OneToOne.class) != null) {
//        OneToOne oneToOne = field.getAnnotation(OneToOne.class);
//        if (oneToOne.fetch() == FetchType.EAGER) {
//          try {
//            Object o = field.getType().newInstance();
//            System.out.println(o.getClass());
//          } catch (InstantiationException e) {
//            e.printStackTrace();
//          }
//        }
//        break;
//      }
//      
//      if (field.getAnnotation(OneToMany.class) != null) {
//        OneToMany oneToMany = field.getAnnotation(OneToMany.class);
//        if (oneToMany.fetch() == FetchType.EAGER) {
//          String tableName = getCascadeObjectTableName(field.getGenericType());
//          String mtableName = TableMapping.me().getTable(getClass()).getName();
//          List<Record> slaves = Db.find("select * from " + tableName + " where " + mtableName + "_id = ?", this.get("id"));
//          field.set(this, slaves);
//        }
//        break;
//      }
//    }
    return result;
  }
  
  public List<M> fetch(int count) throws IllegalAccessException {
    return fetch(0, count);
  }

  public M first() throws IllegalAccessException {
    List<M> list = fetch(0, 1);
    return list == null || list.size() == 0 ? null : list.get(0);
  }
  
  private CascadeType[] getCascadeType(Field field) {
    if (field.isAnnotationPresent(OneToOne.class))
      return field.getAnnotation(OneToOne.class).cascade();
    if (field.isAnnotationPresent(OneToMany.class))
      return field.getAnnotation(OneToMany.class).cascade();
    if (field.isAnnotationPresent(ManyToMany.class))
      return field.getAnnotation(ManyToMany.class).cascade();
    return null;
  }
  
  private boolean cascadeInsert(CascadeType[] types) {
    for (CascadeType cascadeType : types) {
        if (cascadeType == CascadeType.ALL || cascadeType == CascadeType.PERSIST) {
            return true;
        }
    }
    return false;
  }
  
  private boolean cascadeUpdate(CascadeType[] types) {
    for (CascadeType cascadeType : types) {
        if (cascadeType == CascadeType.ALL || cascadeType == CascadeType.MERGE) {
            return true;
        }
    }
    return false;
  }
  
  private boolean cascadeDelete(CascadeType[] types) {
    for (CascadeType cascadeType : types) {
        if (cascadeType == CascadeType.ALL || cascadeType == CascadeType.REMOVE) {
            return true;
        }
    }
    return false;
  }
  
  @SuppressWarnings("unchecked")
  private void cascadeInsert0(Table table) throws IllegalArgumentException, IllegalAccessException {
    // for JPA save, ManyToOne not support cascade insert
    Set<Field> cascadeField = getCascadeObjectArray();
    for (Field field : cascadeField) {
      Object obj = field.get(this);
      // 关联的对象必须继承JPAModel
      // OneToOne, OneToMany, ManyToMany
      if (field.isAnnotationPresent(OneToOne.class) ||
          field.isAnnotationPresent(OneToMany.class) ||
          field.isAnnotationPresent(ManyToMany.class)) {
        String masterTableName = table.getName();
        String slavesTableName = getCascadeObjectTableName(field.getGenericType());
        
        CascadeType[] cascadeTypes = getCascadeType(field);
        if (cascadeTypes == null) continue;
        boolean cascadeInsert = cascadeInsert(cascadeTypes);
        if (cascadeInsert) {
          // OneToOne
          if (obj instanceof Model) {
            Model<M> model = (Model<M>) obj;
            _cascadeInsert0(model, masterTableName, slavesTableName);
          // OneToMany, ManyToMany
          } else if (obj instanceof Collection) {
            Collection<Model<M>> collection = (Collection<Model<M>>) obj;
            for (Model<M> model : collection) {
              _cascadeInsert0(model, masterTableName, slavesTableName);
            }
          }
        }
      }
      // -----
    }
  }
  
  /**
   * <p>联级更新对象，使用的策略和hibernate相同:
   * 当关联对象为null时候，不作任何操作
   * 当关联对象不为null时，删除原有的关联关系和对象，再添加新的关联关系和对象。
   * <p>当关联关系是OneToMany或者是OneToOne时，如果不想删除关联对象，只想删除关联关系那
   * 么就不要在主对象上使用cascade
   * @param table
   */
  @SuppressWarnings("unchecked")
  private void cascadeUpdate0(Table table, boolean cascadeDeleteObject) throws IllegalArgumentException, IllegalAccessException, InstantiationException {
    // for JPA update, ManyToOne not support cascade insert
    Set<Field> cascadeField = getCascadeObjectArray();
    for (Field field : cascadeField) {
      // 关联的对象必须继承JPAModel
      Object obj = field.get(this);
      // OneToOne, OneToMany, ManyToMany
      if (field.isAnnotationPresent(OneToOne.class) ||
          field.isAnnotationPresent(OneToMany.class) ||
          field.isAnnotationPresent(ManyToMany.class)) {
        String masterTableName = table.getName();
        String slavesTableName = getCascadeObjectTableName(field.getGenericType());
        
        CascadeType[] cascadeTypes = getCascadeType(field);
        if (cascadeTypes == null) continue;
        boolean cascadeUpdate = cascadeUpdate(cascadeTypes);
        if (cascadeUpdate) {
          if (obj == null) {
            continue;
          } else {
            // OneToOne
            // OneToMany, ManyToMany
            
            // 采取删除原关联对象策略
            if (cascadeDeleteObject) {
              // 获取关联对象的class
              Class<?> MODEL = getCascadeObjectClass(field);
              Model<M> slavesModel = (Model<M>) MODEL.newInstance();
              Collection<M> slavesModelCollection = slavesModel.find("select a.* from " + slavesTableName + " a left join mp_" + masterTableName + "_" + slavesTableName + " b on a.id = b." + slavesTableName + "_id where b." + masterTableName + "_id = ?", this.get("id"));
              for (Model<M> model : slavesModelCollection) {
                // 由于walle生成的代码映射表和实例表是做了cascade关联的，所以删除实例表就等于删除了映射表
                if (!model.delete())
                  throw new PersistenceException(String.format("Oop! cascade update-delete obj {%s} fail.", slavesModel.getClass()));
              }
            } else {
              // 删除原关联关系
              long count = Db.findFirst("select count(*) from mp_" + masterTableName + "_" + slavesTableName + " where " + masterTableName + "_id = ?", this.get("id")).getLong("count(*)");
              int deleteIndex = Db.update("delete from mp_" + masterTableName + "_" + slavesTableName + " where " + masterTableName + "_id = ?", this.get("id"));
              if (deleteIndex != count && deleteIndex != Statement.SUCCESS_NO_INFO) 
                throw new PersistenceException(String.format("Oop! cascade update-delete obj {} fail. index: %d", deleteIndex));
            }
            // 保存新的关联关系
            if (obj instanceof Model) {
              Model<M> model = (Model<M>) obj;
              _cascadeInsert0(model, masterTableName, slavesTableName);
            } else if (obj instanceof Collection) {
              Collection<Model<M>> collection = (Collection<Model<M>>) obj;
              for (Model<M> model : collection) {
                _cascadeInsert0(model, masterTableName, slavesTableName);
              }
            }
          } 
        }
        // -----
      }
      // -----
    }
  }
  
  @SuppressWarnings("unchecked")
  private void cascadeDelete0(Table table) throws IllegalArgumentException, IllegalAccessException, InstantiationException {
    // for JPA delete, ManyToOne not support cascade insert
    Set<Field> cascadeField = getCascadeObjectArray();
    for (Field field : cascadeField) {
      // 关联的对象必须继承JPAModel
      // OneToOne, OneToMany, ManyToMany
      if (field.isAnnotationPresent(OneToOne.class) ||
          field.isAnnotationPresent(OneToMany.class) ||
          field.isAnnotationPresent(ManyToMany.class)) {
        String masterTableName = table.getName();
        String slavesTableName = getCascadeObjectTableName(field.getGenericType());
        
        CascadeType[] cascadeTypes = getCascadeType(field);
        if (cascadeTypes == null) continue;
        boolean cascadeDelete = cascadeDelete(cascadeTypes);
        if (cascadeDelete) {
          // 获取关联对象的class
          Class<?> MODEL = getCascadeObjectClass(field);
          Model<M> slavesModel = (Model<M>) MODEL.newInstance();
          Collection<M> slavesModelCollection = slavesModel.find("select a.* from " + slavesTableName + " a left join mp_" + masterTableName + "_" + slavesTableName + " b on a.id = b." + slavesTableName + "_id where b." + masterTableName + "_id = ?", this.get("id"));
          // OneToOne
          // OneToMany, ManyToMany
          for (Model<M> model : slavesModelCollection) {
            _cascadeDelete0(model, masterTableName, slavesTableName);
          }
          // 由于使用walle生成的代码中间表的关联关系都是cascade，所以当删除movie后，中间表会联级删除
//          int deleteIndex = Db.update("delete from mp_" + masterTableName + "_" + slavesTableName + " where " + masterTableName + "_id = ?", this.get("id"));
//          if (deleteIndex < 0 && deleteIndex != Statement.SUCCESS_NO_INFO) 
//            throw new PersistenceException(String.format("Oop! cascade update-delete obj {%s} fail. index: %d", slavesModel.getClass(), deleteIndex));
        }
      }
      // -----
    }
  }
  
  /**
   * 使用的关联添加策略是对任意关联关系使用中间表的方式来表示
   * 这意味着无论你的关联关系是OneToOne，OneToMany，ManyToMany
   * 都将遵循2个步骤
   * 1.添加从属对象
   * 2.添加主对象与从对象的关系到映射表
   * 
   * @param masterModel
   * @param masterTableName
   * @param slavesModel
   * @param slavesTableName
   */
  private void _cascadeInsert0(Model<M> slavesModel, String masterTableName, String slavesTableName) {
    if (slavesModel.get("id") == null) {
      if (!slavesModel.save())
        throw new PersistenceException(String.format("Oop! cascade insert obj {%s} fail.", slavesModel.getClass()));
    } else {
      if (!slavesModel.update())
        throw new PersistenceException(String.format("Oop! cascade insert obj {%s} fail.", slavesModel.getClass()));
    }
    int index = Db.update("insert into mp_" + masterTableName + "_" + slavesTableName + " (" + masterTableName + "_id, " + slavesTableName + "_id) value (?, ?)", this.get("id"), slavesModel.get("id"));
    if (index <= 0 && index != Statement.SUCCESS_NO_INFO) 
      throw new PersistenceException(String.format("Oop! cascade insert obj {%s} fail. index: %d", slavesModel.getClass(), index));
  }
  
  /**
   * 使用的关联更新策略是对任意关联关系使用中间表的方式来表示
   * 这意味着无论你的关联关系是OneToOne，OneToMany，ManyToMany
   * 有2种模式:
   * 第一种会删除相关联的从对象
   * 1.查询出所有从属对象
   * 2.删除从对象
   * 3.删除主从对象映射表
   * 4.保存新的从对象
   * 5.保存新的主从对象映射表
   * 
   * 第二种不会删除相关联的从对象(hibernate采用了这种策略)
   * 1.查询出所有从属对象
   * 2.删除主从对象映射表
   * 3.保存新的主从对象映射表
   * 
   * 这里默认采取hibernate的策略，及是第二种策略
   * 
   * 在这2种策略下又存在2种关联状态:
   * 第一种关联对象为null
   * 什么都不作
   * 第二种关联对象不为null
   * 1.查询出原有的关联对象
   * 2.是否删除原有关联对象(这个可以根据配置来处理)
   * 3.删除原映射关系
   * 4.保存新映射关系
   * 
   * @param slavesModel
   * @param masterTableName
   * @param slavesTableName
   */
  @SuppressWarnings("unused")
  @Deprecated
  private void _cascadeUpdate0(Model<M> slavesModel, String masterTableName, String slavesTableName) {
    // 因为jfinal-update的特殊性，如果jfinal发现没有set操作就不会触发update导致返回false
    // 所以在update方法中已经默认添加了modify_date，如果没有modify_date参数在数据表中，在update时会直接抛出异常
    // 1.更新从属对象(有id更新，没有id保存)
    if (slavesModel.get("id") != null) {
      if (!slavesModel.update()) 
        throw new PersistenceException(String.format("Oop! cascade update obj {%s} fail.", slavesModel.getClass()));
    } else {
      if (!slavesModel.save())
        throw new PersistenceException(String.format("Oop! cascade update obj {%s} fail.", slavesModel.getClass()));
    }
    // 3.添加主对象与从对象的关系到映射表
    int index = Db.update("insert into mp_" + masterTableName + "_" + slavesTableName + " (" + masterTableName + "_id, " + slavesTableName + "_id) value (?, ?)", this.get("id"), slavesModel.get("id"));
    if (index <= 0 && index != Statement.SUCCESS_NO_INFO) 
      throw new PersistenceException(String.format("Oop! cascade update obj {%s} fail. index: %d", slavesModel.getClass(), index));
  }
  
  /**
   * 
   * @param slavesModel
   * @param masterTableName
   * @param slavesTableName
   */
  private void _cascadeDelete0(Model<M> slavesModel, String masterTableName, String slavesTableName) {
    // 1.删除从属对象(有id删除，没有id不做操作)和主对象
    if (slavesModel.get("id") == null) {
      return;
    } else {
      if (!slavesModel.delete()) 
        throw new PersistenceException(String.format("Oop! cascade delete obj {%s} fail.", slavesModel.getClass()));
    }
  }
  
  // 获取该对象(超类)所有包含Cascade注解的变量
  private Set<Field> getCascadeObjectArray() {
    Set<Field> fields = new HashSet<>();
    Set<Field> cascadeField = new HashSet<>();
    Class<?> clazz = this.getClass();
    while (!clazz.equals(JPAModel.class)) {
        Collections.addAll(fields, clazz.getDeclaredFields());
        clazz = clazz.getSuperclass();
    }
    for (Field field : fields) {
      field.setAccessible(true);
      if (Modifier.isTransient(field.getModifiers())) {
        continue;
      }
      // ------
      if (field.isAnnotationPresent(OneToOne.class)) {
        cascadeField.add(field);
      }
      if (field.isAnnotationPresent(OneToMany.class)) {
        cascadeField.add(field);
      }
      if (field.isAnnotationPresent(ManyToOne.class)) {
        cascadeField.add(field);
      }
      if (field.isAnnotationPresent(ManyToMany.class)) {
        cascadeField.add(field);
      }
    }
    return cascadeField;
  }
  
  // 获取关联对象的表名(为了联级动态添加)
  private String getCascadeObjectTableName(Type type) {
    if (ParameterizedType.class.isAssignableFrom(type.getClass())) {  
      for (Type t0 : ((ParameterizedType) type).getActualTypeArguments()) {  
        try {
          org.hacker.core.plugin.Table o = Class.forName(t0.toString().substring(6)).getAnnotation(org.hacker.core.plugin.Table.class);
          return o.tableName();
        } catch (ClassNotFoundException e) {
          e.printStackTrace();
        }
      }
    }
    return null;
  }
  
  private Class<?> getCascadeObjectClass(Field field) {
    Type type = field.getGenericType();
    if (field.getType().equals(Collection.class)) {
      if (ParameterizedType.class.isAssignableFrom(type.getClass())) {  
        for (Type t0 : ((ParameterizedType) type).getActualTypeArguments()) {  
          try {
            return Class.forName(t0.toString().substring(6));
          } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
          }
        }
      }
    } else {
      return field.getType();
    }
    return null;
  }
}
