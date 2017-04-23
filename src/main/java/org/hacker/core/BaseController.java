package org.hacker.core;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.hacker.exception.AssertException;

import com.jfinal.core.Controller;
import com.jfinal.kit.StrKit;
import com.jfinal.plugin.activerecord.ActiveRecordException;
import com.jfinal.plugin.activerecord.Model;
import com.jfinal.plugin.activerecord.Table;
import com.jfinal.plugin.activerecord.TableMapping;

/**
 * BaseController 只提供基础方法
 * 
 * @version 1.0
 * @author Mr.J.(slashchenxiaojun@sina.com)
 * 
 * @date 2015-05-18
 * */
public class BaseController extends Controller {
  private  ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
  private  Validator validator = factory.getValidator();
  
	public void OK() {
		renderStatus(0, "ok", null);
	}
	
	public void OK(Object data) {
		renderStatus(0, "ok", data);
	}
	
	public void Error(Integer code, String msg) {
		renderStatus(code, msg, null);
	}
	
	protected void renderStatus(Integer code, String msg, Object data) {
		setAttr("code", code);
		setAttr("msg", msg);
		setAttr("data", data);
		renderJson(new String[] {"code", "msg", "data"});
	}
	
	/**
	 * 使用这个方法不是很高效，因为它需要反射N次newInstance
	 * 
	 * @param modelClass
	 * @param modelName
	 * @return
	 */
	protected <T> List<T> getModels(Class<T> modelClass, String modelName) {
		Object obj = null;
		try {
			obj = modelClass.newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		if (obj instanceof Model == false) {
			throw new IllegalArgumentException("getModel only support class of Model, using getBean for other class.");
		}
		
		List<T> list = null;
		Model<?> model = (Model<?>)obj;
		Table table = TableMapping.me().getTable(model.getClass());
		if (table == null) {
			throw new ActiveRecordException("The Table mapping of model: " + modelClass.getName() + 
					" not exists or the ActiveRecordPlugin not start.");
		}
		
		String modelNameAndDot = StrKit.notBlank(modelName) ? modelName + "." : null;
		Map<String, String[]> parasMap = getParaMap();
		// 对 paraMap进行遍历而不是对table.getColumnTypeMapEntrySet()进行遍历，以便支持 CaseInsensitiveContainerFactory
		// 以及支持界面的 attrName有误时可以感知并抛出异常避免出错
		for (Entry<String, String[]> entry : parasMap.entrySet()) {
			String paraName = entry.getKey();
			String attrName;
			if (modelNameAndDot != null) {
				if (paraName.startsWith(modelNameAndDot)) {
					attrName = paraName.substring(modelNameAndDot.length());
				} else {
					continue ;
				}
			} else {
				attrName = paraName;
			}
			
			Class<?> colType = table.getColumnType(attrName);
			if (colType == null) {
				throw new ActiveRecordException("The model attribute " + attrName + " is not exists.");
			}
			
			try {
				String[] paraValueArray = entry.getValue();
				
				// deferred load list
				if(list == null) {
					list = new ArrayList<>(paraValueArray.length);
					for(int i = 0; i < paraValueArray.length; i++) {
						try {
							list.add(modelClass.newInstance());
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					}
				}
				for(int i = 0; i < list.size(); i++) {
					Model<?> model0 = (Model<?>)list.get(i);
					String val = paraValueArray[i];
					val = StrKit.isBlank(val) ? null : val;
					Object value = val != null ? TypeConverter.convert(colType, val) : null;
					if(value != null)
						model0.set(attrName, value);
				}
			} catch (Exception e) {
				throw new RuntimeException("Can not convert parameter: " + paraName, e);
			}
		}
		
		return list;
	}
	
	/**
	 * 使用hibernate-validator实现的JSR303验证model
	 */
	public <T> void JSR303Validator(T model) {
	  Set<ConstraintViolation<T>> constraintViolations = validator.validate(model);
	  if(constraintViolations.size() == 0) return;
    for(ConstraintViolation<T> c : constraintViolations) {
      throw new AssertException("Oop~ " + c.getPropertyPath() + " " + c.getMessage());
    }
	}
	
	public <T> void JSR303Validator(Collection<T> model) {
	   for (T t : model) {
	     JSR303Validator(t);
	   }
	}
}

final class TypeConverter {
	
	private static final String timeStampPattern = "yyyy-MM-dd HH:mm:ss";
	private static final String datePattern = "yyyy-MM-dd";
	private static final int timeStampLen = timeStampPattern.length();
	
	/**
	 * test for all types of mysql
	 * 
	 * 表单提交测试结果:
	 * 1: 表单中的域,就算不输入任何内容,也会传过来 "", 也即永远不可能为 null.
	 * 2: 如果输入空格也会提交上来
	 * 3: 需要考 model中的 string属性,在传过来 "" 时是该转成 null还是不该转换,
	 *    我想, 因为用户没有输入那么肯定是 null, 而不该是 ""
	 * 
	 * 注意: 1:当type参数不为String.class, 且参数s为空串blank的情况,
	 *       此情况下转换结果为 null, 而不应该抛出异常
	 *      2:调用者需要对被转换数据做 null 判断，参见 ModelInjector 的两处调用
	 */
	public static final Object convert(Class<?> type, String s) throws ParseException {
		// mysql type: varchar, char, enum, set, text, tinytext, mediumtext, longtext
		if (type == String.class) {
			return ("".equals(s) ? null : s);	// 用户在表单域中没有输入内容时将提交过来 "", 因为没有输入,所以要转成 null.
		}
		s = s.trim();
		if ("".equals(s)) {	// 前面的 String跳过以后,所有的空字符串全都转成 null,  这是合理的
			return null;
		}
		// 以上两种情况无需转换,直接返回, 注意, 本方法不接受null为 s 参数(经测试永远不可能传来null, 因为无输入传来的也是"")
		
		
		// mysql type: int, integer, tinyint(n) n > 1, smallint, mediumint
		if (type == Integer.class || type == int.class) {
			return Integer.parseInt(s);
		}
		
		// mysql type: bigint
		if (type == Long.class || type == long.class) {
			return Long.parseLong(s);
		}
		
		// java.util.Date 类型专为传统 java bean 带有该类型的 setter 方法转换做准备，万不可去掉
		// 经测试 JDBC 不会返回 java.util.Data 类型。java.sql.Date, java.sql.Time,java.sql.Timestamp 全部直接继承自 java.util.Data, 所以 getDate可以返回这三类数据
		if (type == java.util.Date.class) {
			if (s.length() >= timeStampLen) {	// if (x < timeStampLen) 改用 datePattern 转换，更智能
				// Timestamp format must be yyyy-mm-dd hh:mm:ss[.fffffffff]
				// return new java.util.Date(java.sql.Timestamp.valueOf(s).getTime());	// error under jdk 64bit(maybe)
				return new SimpleDateFormat(timeStampPattern).parse(s);
			}
			else {
				// return new java.util.Date(java.sql.Date.valueOf(s).getTime());	// error under jdk 64bit
				return new SimpleDateFormat(datePattern).parse(s);
			}
		}
		
		// mysql type: date, year
		if (type == java.sql.Date.class) {
			if (s.length() >= timeStampLen) {	// if (x < timeStampLen) 改用 datePattern 转换，更智能
				// return new java.sql.Date(java.sql.Timestamp.valueOf(s).getTime());	// error under jdk 64bit(maybe)
				return new java.sql.Date(new SimpleDateFormat(timeStampPattern).parse(s).getTime());
			}
			else {
				// return new java.sql.Date(java.sql.Date.valueOf(s).getTime());	// error under jdk 64bit
				return new java.sql.Date(new SimpleDateFormat(datePattern).parse(s).getTime());
			}
		}
		
		// mysql type: time
		if (type == java.sql.Time.class) {
			return java.sql.Time.valueOf(s);
		}
		
		// mysql type: timestamp, datetime
		if (type == java.sql.Timestamp.class) {
			if (s.length() >= timeStampLen) {
				return java.sql.Timestamp.valueOf(s);
			}
			else {
				return new java.sql.Timestamp(new SimpleDateFormat(datePattern).parse(s).getTime());
			}
		}
		
		// mysql type: real, double
		if (type == Double.class || type == double.class) {
			return Double.parseDouble(s);
		}
		
		// mysql type: float
		if (type == Float.class || type == float.class) {
			return Float.parseFloat(s);
		}
		
		// mysql type: bit, tinyint(1)
		if (type == Boolean.class || type == boolean.class) {
			String value = s.toLowerCase();
			if ("1".equals(value) || "true".equals(value)) {
				return Boolean.TRUE;
			}
			else if ("0".equals(value) || "false".equals(value)) {
				return Boolean.FALSE;
			}
			else {
				throw new RuntimeException("Can not parse to boolean type of value: " + s);
			}
		}
		
		// mysql type: decimal, numeric
		if (type == java.math.BigDecimal.class) {
			return new java.math.BigDecimal(s);
		}
		
		// mysql type: unsigned bigint
		if (type == java.math.BigInteger.class) {
			return new java.math.BigInteger(s);
		}
		
		// mysql type: binary, varbinary, tinyblob, blob, mediumblob, longblob. I have not finished the test.
		if (type == byte[].class) {
			return s.getBytes();
		}
		
		throw new RuntimeException(type.getName() + " can not be converted, please use other type of attributes in your model!");
	}
}

