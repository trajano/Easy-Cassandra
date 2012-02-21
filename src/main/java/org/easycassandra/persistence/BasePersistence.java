/*
 * Copyright 2012 Otávio Gonçalves de Santana (otaviojava)
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.easycassandra.persistence;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.easycassandra.EasyCassandraException;
import org.easycassandra.annotations.ColumnValue;
import org.easycassandra.annotations.EmbeddedValue;
import org.easycassandra.annotations.EnumeratedValue;
import org.easycassandra.annotations.KeyValue;
import org.easycassandra.annotations.read.EnumRead;
import org.easycassandra.annotations.read.ReadInterface;
import org.easycassandra.annotations.read.ReadManager;
import org.easycassandra.annotations.read.UTF8Read;
import org.easycassandra.annotations.write.WriteInterface;
import org.easycassandra.annotations.write.WriteManager;
import org.easycassandra.util.ReflectionUtil;

/**
 *
 * @author otavio
 */
class BasePersistence {

  
    
    
    /**
     * Key value of write for The Cassandra persistence
     * @see  WriteInterface
     */
    private static Map<String, WriteInterface> writeMap;
    /**
     * Key Valeu of read for The Cassnadra persistence
     * @see ReadInterface
     */
    private static Map<String, ReadInterface> readMap;
    
    /**
     * Class for read Byte
     * @see ReadManager
     */
    private static ReadManager readManager;
    
    /**
     * Class for write Byte
     * @see WriteManager
     */
    private static WriteManager writeManager;
    
    /**
     * Class with information about Column Family managed by Easy-Cassandra
     */
    private AtomicReference<ColumnFamilyIds> referenciaSuperColunas;
    
    /**
     * field for lock or unlock for run 
     * the Thread
     */
     private  static   AtomicBoolean documentDaemon= new AtomicBoolean(false);
    
    /**
     * Thread for write the id in the Document
     */
    private Thread writeDocumentThread;
    
    /**
     * name of keyspace where the Client is
     */
    private  String keyStore;
    
static{
	 writeMap = ReadWriteMaps.getWriteMap();
     readMap = ReadWriteMaps.getReadMap();
}
    /**
     * Construtor for inicialize the readMap and the WriteMap
     * @see BasePersistence#writeMap
     * @see BasePersistence#readMap
     */
    public BasePersistence() {
       
        writeManager=new WriteManager(writeMap);
        readManager =new ReadManager(readMap);

    }
    
    /**
     * Return the ByteBuffer with the KeyValue
     * @param object - the object viewed
     * @param autoEnable - if the Keyvalue auto is enable
     * @return -The value of the KeyRow in ByteBuffer format
     * @see KeyValue
     * @{@link EasyCassandraException - for operation in EasyCassandra
     */
    protected ByteBuffer getKey(Object object, boolean autoEnable){
        Field keyField = ColumnUtil.getKeyField(object.getClass());
        if(keyField==null){
        	throw new EasyCassandraException("You must use annotation @org.easycassandra.annotations.KeyValue in some field of the Class: "+object.getClass().getName()+"  for be the keyrow in Cassandra");
        }
        String colunaFamilia = ColumnUtil.getColumnFamilyName(object.getClass());
        if (keyField != null) {
            KeyValue chave = keyField.getAnnotation(KeyValue.class);

         
            if (chave.auto() && autoEnable) {
            	Long id = null;
                id = referenciaSuperColunas.get().getId(colunaFamilia,keyStore);
              
                if(!documentDaemon.get()){
                	documentDaemon.set(true);
                	writeDocumentThread.start();
                }
              
                ReflectionUtil.setMethod(object, keyField, id);
              
               
            } 
                
                return  getWriteManager().convert(ReflectionUtil.getMethod(object, keyField));
            
           
            
            

        }


        return null;
    }
    

//columns Utils
 
    
    /**
     * Create values for query
     * e.g: (key1,key2...)
     * @param keys - the values
     * @return - the String for query in
     */
	protected String createValuesIn(Object... keys) {
		UTF8Read ufRead=new UTF8Read();
    	StringBuilder keyNames=new StringBuilder();
    	keyNames.append(" (");
    	String condicion="";
    	for(Object key:keys){
    	ByteBuffer keyBuffer = getWriteManager().convert(key);
    	keyNames.append(" "+condicion+"'"+ufRead.getObjectByByte(keyBuffer).toString()+"'");
    	condicion=",";
    	}
		return  keyNames.substring(0, keyNames.length())+") ";
	}
    
    //read objetct
    /**
     * The List Of the Class with information from Cassandra
     * @param colMap - Key Map with column name and value
     * @param persisteceClass - Class for the List
     * @return - List of the persisteceClass
     * @throws InstantiationException
     * @throws IllegalAccessException 
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected List getList(List<Map<String, ByteBuffer>> colMap, Class persisteceClass) throws InstantiationException, IllegalAccessException {
        List lists = new ArrayList<>();
       

        for (Map<String, ByteBuffer> listMap : colMap) {
        	 Object bean = null;
             try {
                 bean = persisteceClass.newInstance();
             } catch (InstantiationException | IllegalAccessException exception) {
                 Logger.getLogger(BasePersistence.class.getName()).log(Level.SEVERE, null, exception);
             }
            readObject(listMap, bean);
            lists.add(bean);
        }
        return lists;
    }
/**
 * The List Of the Class with information from Cassandra
 * @param listMap - Key Map with column name and value
 * @param bean -  The Object created
 * @throws InstantiationException
 * @throws IllegalAccessException 
 */
    protected void readObject(Map<String, ByteBuffer> listMap, Object bean) throws InstantiationException, IllegalAccessException {
        Field[] fieldsAll = bean.getClass().getDeclaredFields();
        for (Field field : fieldsAll) {

            if (field.getAnnotation(KeyValue.class) != null) {
                ByteBuffer byteBuffer = listMap.get("KEY");
                
                ReflectionUtil.setMethod(bean, field, getReadManager().convert(byteBuffer, field.getType()));
                continue;
            } else if (field.getAnnotation(ColumnValue.class) != null) {
                
                ByteBuffer byteBuffer = listMap.get(ColumnUtil.getColumnName(field));
                if (byteBuffer != null) {
                	
                    ReflectionUtil.setMethod(bean, field, getReadManager().convert(byteBuffer, field.getType()));
                }

            } else if (field.getAnnotation(EmbeddedValue.class) != null) {

                Object subObject = field.getType().newInstance();

                readObject(listMap, subObject);

                ReflectionUtil.setMethod(bean, field, subObject);
            } else if (field.getAnnotation(EnumeratedValue.class) != null) {

                ByteBuffer bb = listMap.get(ColumnUtil.getEnumeratedName(field));
                if (bb != null) {

                    Object[] enums = field.getType().getEnumConstants();
                    Integer index = (Integer) new EnumRead().getObjectByByte(bb);

                    ReflectionUtil.setMethod(bean, field, enums[index]);
                }

            }
        }
    }
    
    

    
      /**
     * the with synchronized for the KeySpace
     * @return Keyspace's name
     */
	 public synchronized String getKeySpace() {
		return keyStore;
	}



	 /**
	  * get of readManager 
	  * @see BasePersistence#readManager
	  * @return the readManager
	  */
    public static ReadManager getReadManager() {
		return readManager;
	}
	
    /**
     * get of writeManager
     * @see BasePersistence#writeManager
     * @return the writeManager
     */
	public static WriteManager getWriteManager() {
		return writeManager;
	}
	
	/**
     * @param referenciaSuperColunas the referenciaSuperColunas to set
     */
    public void setReferenciaSuperColunas(AtomicReference<ColumnFamilyIds> referenciaSuperColunas) {
        this.referenciaSuperColunas = referenciaSuperColunas;
    }

    /**
     * @param writeDocumentThread the writeDocumentThread to set
     */
    public void setWriteDocumentThread(Thread writeDocumentThread) {
        this.writeDocumentThread = writeDocumentThread;
    }

    /**
     * @param keyStore the keyStore to set
     */
    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }
         
    /**
     * @param lockWrite the LOCK_WRITE to set
     */
    public static void setLockWrite(AtomicBoolean lockWrite) {
        documentDaemon= lockWrite;
    }

     /**
      *  the LOCK_WRITE to get
      * @return the LockWrite
      */
    public static AtomicBoolean getLockWrite() {
        return documentDaemon;
    }
}
