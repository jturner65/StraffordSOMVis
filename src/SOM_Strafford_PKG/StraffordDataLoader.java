package SOM_Strafford_PKG;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;

//TODO possibly make these all runnables so they instance a mapper for each thread they consume - mappers are threadsafe but could deadlock depending on consuming functionality
/**
* this class will load, and manage, the appropriate files containing the Strafford data used to build the SOM.  
* ultimately this class will also execute the appropriate SQL commands to call db
 * 
 * @author john
 *
 */
public abstract class StraffordDataLoader implements Callable<Boolean> {
	//ref to owning object
	protected SOMMapData mapData;
	//key in destination map of data arrays where data should be loaded
	protected String destAraDataKey;
	//used to decipher json - need one per instance
	public ObjectMapper jsonMapper;
	//manage data streams for each file type	
	//protected ArrayList<BaseRawData> dataObjs;
	//source file name and path or sql-related connection info (file name of csv holding sql connection info?)
	protected String fileNameAndPath;
	//whether is file loader (from csv files) or sql reader
	protected boolean isFileLoader;
	//flag index in owning map denoting the processing is finished
	protected int isDoneMapDataIDX;
	
//	public StraffordDataLoader(SOMMapData _mapData, String _destKey,  boolean _isFileLoader, String _dataLocInfoStr) {
//		jsonMapper = new ObjectMapper();
//		mapData = _mapData;
//		destAraDataKey = _destKey;
//		fileNameAndPath = _dataLocInfoStr;
//		isFileLoader = _isFileLoader;
//	}//ctor
	public StraffordDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {
		jsonMapper = new ObjectMapper();
		fileNameAndPath = _dataLocInfoStr;
		isFileLoader = _isFileLoader;
	}//ctor
	
	public void setLoadData(SOMMapData _mapData, String _destKey, boolean _isFileLoader, String _dataLocInfoStr, int _isDoneIDX) {
		mapData = _mapData;
		destAraDataKey = _destKey;
		fileNameAndPath = _dataLocInfoStr;
		isFileLoader = _isFileLoader;
		isDoneMapDataIDX = _isDoneIDX;
	}
	
	protected ArrayList<BaseRawData> execLoad(){	
		ArrayList<BaseRawData> dataObjs = new ArrayList<BaseRawData>();
		if (isFileLoader) {
			streamCSVDataAndBuildStructs(dataObjs);
			System.out.println("File Load Finished for "+fileNameAndPath);
		} else {//exec sql load for this file
			//TODO sql read/load here
			System.out.println("Sql Load NOT IMPLEMENTED using connection info at "+fileNameAndPath);
		}
		return dataObjs;
	}//execLoad
	
	//parse string into data object, which will then construct training/testing data for som map
	protected abstract BaseRawData parseStringToObj(String[] strAra, String jsonStr);	
	public void streamCSVDataAndBuildStructs(ArrayList<BaseRawData> dAra) {try {_strmCSVFileBuildObjs(dAra);} catch (Exception e) {e.printStackTrace();}}
	//stream read the csv file and build the data objects
	public void _strmCSVFileBuildObjs(ArrayList<BaseRawData> dAra) throws IOException {		
		FileInputStream inputStream = null;
		Scanner sc = null;
	    int line = 1, badEntries = 0;
		try {
			System.out.println("Working Directory = " +System.getProperty("user.dir"));
		    inputStream = new FileInputStream(fileNameAndPath);
		    sc = new Scanner(inputStream);
		    //get rid of headers
		    sc.nextLine();
		    boolean done = false;
		    while ((sc.hasNextLine()) && !(done)) {
		    	String datStr = sc.nextLine();
		    	//System.out.println("line : " +line + " | datStr : "+datStr);
		    	String [] strAras1 = datStr.split("\"\\{");		//split into string holding columns and string holding json 
		    	if (strAras1.length < 2) {
		    		System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! bad entry at "+line+" lacking description/payload json : " + datStr);
		    		++badEntries;
		    	} else {
		    		String str = strAras1[1];
		    	    if (str.charAt(str.length() - 1) == '"') {str = str.substring(0, str.length() - 1);}
			        String jsonStr = "{"+str.replace("\t", "");
			        String[] vals = strAras1[0].replace("\"","").split(",");
			        //convert string to object to be used to train map		        
			    	//System.out.println("col string : "+strAras1[0]+"\njson string : ---"+jsonStr+"---");
			        BaseRawData obj = parseStringToObj(vals, jsonStr);
			        if (obj.isBadRec) {
			        	System.out.println("Object : " + obj.toString() + " is a bad record due to having no jp/jpg data.  Entry is Ignored.");
			        } else {
			        	dAra.add(obj);
			        }
		    	}
		        ++line;		        
		        if (line % 100000 == 0) {
		        	System.out.println("Finished line : "+line); 		        	
		        	//done = true;//end early for debugging
		        }
		    }
		    //Scanner suppresses exceptions
		    if (sc.ioException() != null) { throw sc.ioException(); }
		} catch (Exception e) {	e.printStackTrace();} 
		finally {
		    if (inputStream != null) {inputStream.close();		    }
		    if (sc != null) { sc.close();		    }
		}
		System.out.println("Finished loading "+ fileNameAndPath + " With "+badEntries+" bad/missing json entries and " + dAra.size() + " good records.");
	}//loadFileContents
	
	
	@Override
	public Boolean call() throws Exception {
		//execute load, take load results and add to mapData.rawDataArrays
		ArrayList<BaseRawData> dataObjs = execLoad();
		//put in destAraDataKey of  mapData.rawDataArrays
		mapData.rawDataArrays.put(destAraDataKey, dataObjs);
		//set boolean in mapData to mark that this is finished
		mapData.setFlag(isDoneMapDataIDX, true);
		//run(); 
		return true;
	}//call launches this loader - when finished will return true
	
	
}//StraffordDataLoader base class

//stream prospects and build the objects that will then decipher their json content and build the training/testing data based on them
class ProspectDataLoader extends StraffordDataLoader{
	public ProspectDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected BaseRawData parseStringToObj(String[] strAra, String jsonStr) {
		prospectData obj = new prospectData(strAra[0].trim(), jsonStr.trim(),jsonMapper);
		obj.procInitData(strAra);
		return obj;
	}
}//

//stream Order Events and build the objects that will then decipher their json content and build the training/testing data based on them
class OrderEventDataLoader extends StraffordDataLoader{
	public OrderEventDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected BaseRawData parseStringToObj(String[] strAra, String jsonStr) {
		OrderEvent obj = new OrderEvent(strAra[0].trim(), jsonStr.trim(),jsonMapper);
		obj.procInitData(strAra);
		return obj;
	}
}//
//stream Order Events and build the objects that will then decipher their json content and build the training/testing data based on them
class LinkEventDataLoader extends StraffordDataLoader{
	public LinkEventDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected BaseRawData parseStringToObj(String[] strAra, String jsonStr) {
		LinkEvent obj = new LinkEvent(strAra[0].trim(), jsonStr.trim(),jsonMapper);
		obj.procInitData(strAra);
		return obj;
	}
}//
//stream Order Events and build the objects that will then decipher their json content and build the training/testing data based on them
class OptEventDataLoader extends StraffordDataLoader{
	public OptEventDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected BaseRawData parseStringToObj(String[] strAra, String jsonStr) {
		OptEvent obj = new OptEvent(strAra[0].trim(), jsonStr.trim(),jsonMapper);
		obj.procInitData(strAra);
		return obj;
	}
}//






//base data object from strafford db - describes a prospect or event, keyed by OID
abstract class BaseRawData {
	//format of dates in db records
	public static final String dateFormatString = "yyyy-MM-dd HH:mm:ss";
	//construction to keep track of count of seen jp ids : key is jp, val is count
	public static TreeMap<Integer, Integer> jpSeen = new TreeMap<Integer, Integer>();
	//construction to keep track of count of jp ids seen in events : key is jp, val is count
	public static TreeMap<Integer, Integer> jpEvSeen = new TreeMap<Integer, Integer>();
	//construction to keep track of the jps associated with particular jpg
	public static TreeMap<Integer, TreeSet <Integer>> jpgroupsAndJps = new TreeMap<Integer, TreeSet <Integer>>();	
	//type of data object this is
	public final String TypeOfData;
	
	//OID field linking events to prospects
	public final String OID;
	//descriptor object holds translation of json description
	protected jsonDescr dscrObject;
	//set this if has no relevant info for calculations
	protected boolean isBadRec;
	
	//map of key==jpgroup, value ==array of jps in decreasing significance
	//index -1 holds array of jpgs in specified order of (assumed) decreasing significance
	public TreeMap<Integer, ArrayList<Integer>> rawJpMapOfArrays;
	
	public BaseRawData(String _id, String _json, ObjectMapper _mapper, String _typ) {
		OID=_id.trim().toLowerCase(); TypeOfData = _typ;
		isBadRec = false;
		Map<String, Object> jsonMap = null;
		try {jsonMap = _mapper.readValue(_json,new TypeReference<Map<String,Object>>(){});}
		catch (Exception e) {e.printStackTrace(); 		}
		if (jsonMap != null) {
			dscrObject = buildDescrObjectFromJsonMap(jsonMap);
		}
	}//ctor		
	
	//build a jpMapOfArrays-like structure with all jpgs seen and subsequent jps
	public static TreeMap<Integer, ArrayList<Integer>> buildOrderedMapOfJpgJpsSeen(){
		TreeMap<Integer, ArrayList<Integer>> res = new TreeMap<Integer, ArrayList<Integer>>();
		ArrayList<Integer> tmpList = new ArrayList<Integer>();
		ArrayList<Integer> keyPrefList = new ArrayList<Integer>();
		for (Integer jpg : jpgroupsAndJps.keySet()) {
			keyPrefList.add(jpg);
			TreeSet <Integer> setOfData = jpgroupsAndJps.get(jpg);
			tmpList = new ArrayList<Integer>();
			tmpList.addAll(setOfData);
			res.put(jpg, tmpList);
		}
		res.put(-1, keyPrefList);//add -1 entry to hold all jpg keys in "preferential" order
		return res;
	}//buildOrderedMapOfJpgJpsSeen
	
	//
	public static void RecordJPsJPGsSeen(int jpIdx, int jpgIdx, boolean isEvent) {
		//should never happen, jpgIdx isn't set to -1 until after this call, but just to be safe
		if (jpgIdx != -1) {//-1 is sentinel value to preserve hierarchy of jpgroups
			TreeSet <Integer> jpgSet = jpgroupsAndJps.get(jpgIdx);
			if (null == jpgSet) {jpgSet = new TreeSet <Integer>();}
			jpgSet.add(jpIdx);
			jpgroupsAndJps.put(jpgIdx, jpgSet);		
		}
		Integer count = jpSeen.get(jpIdx);
		if (count == null) {count=0;}
		jpSeen.put(jpIdx, ++count);
		if (isEvent){
			count = jpEvSeen.get(jpIdx);
			if (count == null) {count=0;}
			jpEvSeen.put(jpIdx, ++count);
		}
	}//RecordJPsJPGsSeen
	
	public static void resetJPSeen() {jpSeen = new TreeMap<Integer, Integer>();}
	
	//build a date from a string with specified format
	public static Date buildDateFromString(String _dateStr) {
		Date date = null;
		try {
			SimpleDateFormat frmt = new SimpleDateFormat(BaseRawData.dateFormatString);  
			date = frmt.parse(_dateStr);  
		} catch (Exception e) {//throws ParseException
			//System.out.println("BaseRawData::calcDateFromStr : Format To-Date parse exception for date string : "+_dateStr + " returning null date");
		}
		return date;
	}//calcDateFromStr
	//build a string maintaining the format of the date
	public static String buildStringFromDate(Date _date) {
		String dateStr = "";
		try {
			SimpleDateFormat frmt = new SimpleDateFormat(BaseRawData.dateFormatString);  
			dateStr = frmt.format(_date);  
		} catch (Exception e) {//throws ParseException
			//System.out.println("BaseRawData::buildStringFromDate : Format To-String parse exception for date : "+_date + " returning empty string");		
		}		
		return dateStr;
	}
	//get string representation of this BaseRawData's specific date
	public String getDateString() {return BaseRawData.buildStringFromDate(getDate());}
	
	//return a list of jpgroups in preferential order as recorded in database - this will be used as a key list to access individual jp arrays in appropriate order
	protected ArrayList<Integer> getJPGroupsInOrder(){
		ArrayList<Integer> jpgsInOrder;
		if (rawJpMapOfArrays.containsKey(-1)){//if has multiple groups then key -1 holds list of jpgs in order
			jpgsInOrder = rawJpMapOfArrays.get(-1);			
		} else {//only 1 key
			jpgsInOrder = new ArrayList<Integer>();
			jpgsInOrder.add(rawJpMapOfArrays.firstKey());
		}
		return jpgsInOrder;
	}//getJPGroupsInOrder
		
	protected String getJpsToString() {
		if (rawJpMapOfArrays.size() == 0) {return " No Job Practice Data Found ";}
		String res = " JP Data : ";	
		//get keys in order
		ArrayList<Integer> jpgsInOrder = getJPGroupsInOrder();
		
		for (Integer key : jpgsInOrder) {
			if (key == -1) {continue;}
			res += "|Grp : " +key+" : [";
			ArrayList<Integer> vals = rawJpMapOfArrays.get(key);
			int szm1 = vals.size()-1;
			for (int i=0; i<szm1;++i) {res += vals.get(i)+",";}
			res += vals.get(szm1)+"]";
		}		
		return res;
	}//getJpsToString	
	//use this to display all jp group/jps data
	public void dbgDispJpData(String jpsList) {
		System.out.println("JpsList : " + jpsList );
		System.out.println("jpMapOfArrays size : "+rawJpMapOfArrays.size() + " | " +dbgGetCustInfoStr());
		for (Integer key : rawJpMapOfArrays.keySet()) {
			System.out.print("\t|key : " +key+" : [");
			ArrayList<Integer> vals = rawJpMapOfArrays.get(key);
			int szm1 = vals.size()-1;
			for (int i=0; i<szm1;++i) {System.out.print(""+vals.get(i)+",");			}
			System.out.println(""+vals.get(szm1)+"]");
		}		
	}//dbgDispJpData	
	
	//returns the most relevant date value for this data point, either ludate from prospects or event date from events
	public abstract Date getDate();
	//manage the string array of data from the file - this is column data outside the json description/payload
	public abstract void procInitData(String[] _strAra);	
	//return individual class's specific relevant key mappings- json keys to be queried for potential training data
	public abstract String[] getRelevantExactKeys();
	//build the json descriptor to be used to acquire relevant training data
	protected abstract jsonDescr buildDescrObjectFromJsonMap(Map<String, Object> jsonMap);	
	//custom descriptive debug info relevant to this class 
	protected abstract String dbgGetCustInfoStr();		

	@Override	
	public String toString() {
		String res = "OID : " +  OID + "|Type : " + TypeOfData;
		return res;
	}
}//BaseRawData

class prospectData extends BaseRawData {
	//these should all be lowercase - these are exact key substrings we wish to match, to keep and use to build training data - all the rest of json data is being tossed
	private static final String[] relevantExactKeys = {"jp","lu"};
	//lu date from json
	private Date luDate;
	//if this prospect record is empty/lacking all info.  might not be bad
	public boolean isEmptyPrspctRec = false;

	public prospectData(String _id, String _json, ObjectMapper _mapper) {
		super(_id, _json, _mapper, "prospect");
		String jpsList = dscrObject.mapOfRelevantJson.get("jp").toString();
		String luDateStr = dscrObject.mapOfRelevantJson.get("lu").toString();
		if(luDateStr.length() > 0) {luDate = BaseRawData.buildDateFromString(luDateStr);}
		else {
			isEmptyPrspctRec = jpsList.length() - 2 == 0;//if jpsList is length 2, then no jps in this record, and no last update means this record has very little pertinent data-neither a date nor a jp associated with it
		}
		if (null==jpsList) {
			System.out.println("prospectData::constructor : Null jpsList for json string " + _json);
		} else {
			rawJpMapOfArrays = dscrObject.convert(jpsList);			
		}
	}//ctor
	
	//build descriptor object from json map
	@Override
	protected jsonDescr buildDescrObjectFromJsonMap(Map<String, Object> jsonMap) {return new prospectDescr(jsonMap,getRelevantExactKeys());	}	
	//return prospect objects' relevant query keys for json
	@Override
	public String[] getRelevantExactKeys() {		return relevantExactKeys;	}	
	//most relevant "record date" for prospects
	@Override
	public Date getDate() {return luDate;}	
	//currently no init data for prospect records
	@Override
	public void procInitData(String[] _strAra) {}		
	//custom descriptive debug info relevant to this class
	@Override
	protected String dbgGetCustInfoStr() {		return "";	}	
	@Override
	public String toString() {
		String res = super.toString() ;
		res +=getJpsToString();
		return res;
	}
}//prospectData

//class to provide a description of an event read in from db-derived csv
abstract class EventRawData extends BaseRawData {	
	//date this event occurred
	private Date eventDate;
	//event id
	private Integer eventID;
	//event type
	private String eventType;
	
	public EventRawData(String _id, String _json, ObjectMapper _mapper, String _typ) {
		super(_id, _json, _mapper,_typ);
		String jpsList = dscrObject.mapOfRelevantJson.get("jps").toString();
		rawJpMapOfArrays = dscrObject.convert(jpsList);
		//if(jpMapOfArrays.size() > 0) {dbgDispJpData(jpsList);}
	}
	
	//return csv-compatible string of all datapoints relevant to building a training example from this raw data
	protected String getJpsToCSVStr() {
		if (rawJpMapOfArrays.size() == 0) {return "None,";}
		String res = "";	
		//get keys in order
		ArrayList<Integer> jpgsInOrder = getJPGroupsInOrder();		
		for (Integer key : jpgsInOrder) {
			if (key == -1) {continue;}
			res += "JPG," +key+",JPst,";
			ArrayList<Integer> vals = rawJpMapOfArrays.get(key);
			int szm1 = vals.size()-1;
			for (int i=0; i<szm1;++i) {res += vals.get(i)+",";}
			res += vals.get(szm1)+",JPEnd,";
		}		
		return res;
	}//getJpsToString	
		
	//return CSV data relevant for building training/testing example for this event data point
	public String getAllTrainDataForCSV(boolean inclDate) {
		//start with event date
		String res = "";
		if (inclDate){
			res += "EvDt,"+ getDateString()+",";
		}
		//specific data for this event
		res += getIndivTrainDataForCSV();//includes comma if necessary
		res += "JPGJP_Start,"+getJpsToCSVStr()+"JPGJP_End,";		
		return res;		
	}//getAllTrainDataForCSV
	
	//most relevant "record date" for events
	@Override
	public Date getDate() {return eventDate;}

	//event specific data - event type, event id, event date, in this order
	@Override
	public void procInitData(String[] _strAra) {
		//idxs 0 is oid and len-1 is payload
		int lenAra = _strAra.length;
		eventDate = BaseRawData.buildDateFromString(_strAra[lenAra-1]);
		eventID = Integer.parseInt(_strAra[2].trim());
		eventType = _strAra[1].trim();
	}
	
	public Integer getEventID() {return eventID;}
	public String getEventType() {return eventType;}
	//custom event-specific info to return to build CSV to use to build examples
	public abstract String getIndivTrainDataForCSV();
	//build descriptor object from json map
	@Override
	protected jsonDescr buildDescrObjectFromJsonMap(Map<String, Object> jsonMap) {return new EventDescr(jsonMap,getRelevantExactKeys());	}

	@Override
	public String toString() {
		String res = super.toString() + " Event Type : " +eventType + " Event Date : " + eventDate + " EventID " + eventID;
		return res;
	}
}//EventRawData

class OrderEvent extends EventRawData{
	//these should all be lowercase - these are exact key substrings we wish to match in json, to keep and use to build training data - all the rest of json data is being tossed
	private static final String[] relevantExactKeys = {"jps"};
	
	public OrderEvent(String _id, String _json, ObjectMapper _mapper) {super(_id, _json, _mapper,"orders"); this.isBadRec = rawJpMapOfArrays.size() == 0;}

	//return the order event's relevant query keys for json
	@Override
	public String[] getRelevantExactKeys() {		return relevantExactKeys;	}	
	//custom event-specific info to return to build CSV to use to build examples
	@Override
	public String getIndivTrainDataForCSV() {		return "";}	//must include trailing comma if ever returns data
	//custom descriptive debug info relevant to this class
	@Override
	protected String dbgGetCustInfoStr() {	return "";}
	@Override
	public String toString() {
		String res = super.toString();
		res +=getJpsToString();
		return res;
	}
	
}//OrderEvent

class LinkEvent extends EventRawData{
	//these should all be lowercase - these are exact key substrings we wish to match in json, to keep and use to build training data - all the rest of json data is being tossed
	private static final String[] relevantExactKeys = {"jps"};
	
	public LinkEvent(String _id, String _json, ObjectMapper _mapper) {super(_id, _json, _mapper,"links");this.isBadRec = rawJpMapOfArrays.size() == 0;}

	//return the order event's relevant query keys for json
	@Override
	public String[] getRelevantExactKeys() {		return relevantExactKeys;	}	
	//custom event-specific info to return to build CSV to use to build examples
	@Override
	public String getIndivTrainDataForCSV() {		return "";}	//must include trailing comma if ever returns data
	//custom descriptive debug info relevant to this class
	@Override
	protected String dbgGetCustInfoStr() {	return "";}
	@Override
	public String toString() {
		String res = super.toString();
		res +=getJpsToString();
		return res;
	}
	
}//OrderEvent

class OptEvent extends EventRawData{
	//these should all be lowercase - these are exact key substrings we wish to match, to keep and use to build training data - all the rest of json data is being tossed
	private static final String[] relevantExactKeys = {"jps", "type"};
	//type of opt choice in event
	private Integer optType;
	
	public OptEvent(String _id, String _json, ObjectMapper _mapper) {
		super(_id, _json, _mapper,"opts");
		optType = Integer.parseInt(dscrObject.mapOfRelevantJson.get("type").toString());	
	}
	
	public Integer getOptType() {return optType;}
	
	//return the opt event's relevant query keys for json
	@Override
	public String[] getRelevantExactKeys() {		return relevantExactKeys;	}
	//custom event-specific info to return to build CSV to use to build examples
	@Override
	public String getIndivTrainDataForCSV() {		return "Opt_Type,"+optType+",";}
	//custom descriptive debug info relevant to this class
	@Override
	protected String dbgGetCustInfoStr() {return "Opt Type : "+ optType;}

	@Override
	public String toString() {
		String res = super.toString() + " Opt Type : " +optType;
		res +=getJpsToString();
		return res;
	}
}//class OptEvent

////////////////////////
//	classes to hold instances of json data converted to a form that java can make sense of

//class to hold a data structure and functions that will parse the json descriptor
abstract class jsonDescr{
	//just keep the objects that we need from the json based on keys to match exactly
	protected Map<String,Object> mapOfRelevantJson;
	
	public jsonDescr(Map<String,Object> _mapOfJson, String[] _keysToMatchExact) {
		mapOfRelevantJson= new HashMap<String,Object>();
		for (String key : _keysToMatchExact) {
			Object obj = _mapOfJson.get(key);
			if (null == obj) {System.out.println("jsonDescr::constructor : Key : " + key + " Not found in JSON Map"); continue;}
			mapOfRelevantJson.put(key,obj);
		}		
		//for (String key : _mapOfJson.keySet()) {if (isRelevantKeyExact(key,_keysToMatchExact)) {mapOfRelevantJson.put(key, _mapOfJson.get(key));}}		
	}//ctor
	
	public void dbgDispJsonVals() {for (String key : mapOfRelevantJson.keySet()) {System.out.println("Relevant json entries : " + key + " | Val : --" + mapOfRelevantJson.get(key)+"--");}	}
//	
		
	//convert will convert map to actual instancing class fields, TODO verify this is necessary
	protected abstract TreeMap<Integer, ArrayList<Integer>> convert(String jpAras);
	
	//this will convert a string of jp data to the appropriate format -  in original record
	//data is arrays of arrays where 1st idx is jpg and subsequent ids are jps of decreasing relevance
	//in this structure key will be jpg and array is list of jps in decreasing relevance 
	//idx -1 holds order of supremacy of jpgroups
	protected TreeMap<Integer, ArrayList<Integer>> decodeJPData(String jpAras){
		TreeMap<Integer, ArrayList<Integer>> res = new TreeMap<Integer, ArrayList<Integer>>();
		String strOfAras = jpAras.replace("\\}", "").replaceAll("\"", "").trim();
		if (strOfAras.length() < 3) {return res;}//means this is empty
		
		String[] araOfAras = strOfAras.split("\\], \\[");
		ArrayList<Integer> jpgrpsList, jpgHierarchy = new ArrayList<Integer>();
		for (int i=0;i<araOfAras.length; ++i) {
			String ara = araOfAras[i].replaceAll("\\[", "").replaceAll("\\]", "");
			String[] araOfVals = ara.split(",");
			jpgrpsList = new ArrayList<Integer>();
			Integer jpgrp = Integer.parseInt(araOfVals[0].trim()); 
			for (int j=1;j<araOfVals.length;++j) {	
				Integer jp = Integer.parseInt(araOfVals[j].trim());
				BaseRawData.RecordJPsJPGsSeen(jp,jpgrp, true);//to debug load of jp's seen
				jpgrpsList.add(jp);	}
			jpgHierarchy.add(jpgrp);
			res.put(jpgrp, jpgrpsList);
		}	
		if (jpgHierarchy.size() > 1) {res.put(-1, jpgHierarchy);}//add hierarchy list if more than 1 jpg/jp to add
		return res;
	}
	
}//class jsonDescr

class prospectDescr extends jsonDescr{
	public prospectDescr(Map<String, Object> _mapOfJson, String[] _keysToMatchExact) {super(_mapOfJson,_keysToMatchExact);}

	@Override
	protected TreeMap<Integer, ArrayList<Integer>> convert(String jpRawDataStr) {	
		//split on label for jp id and jpg id
		String[] tmpStr1 = jpRawDataStr.split("id=");
		if (tmpStr1.length < 3) {return new TreeMap<Integer, ArrayList<Integer>>();}
		
		Integer jp = Integer.parseInt(tmpStr1[1].split(",")[0].trim());
		Integer jpgrp = 0;
		try {
			jpgrp = Integer.parseInt(tmpStr1[2].split(",")[0].trim());
			BaseRawData.RecordJPsJPGsSeen(jp, jpgrp, false);//to debug load of jp's seen
		} catch (Exception e){
			//jp 0, 800, 446
			System.out.println("Poorly formatted jp breaks jpgrp query : " + jp + " | raw str : " + jpRawDataStr + ". Entry is ignored.");
			return new TreeMap<Integer, ArrayList<Integer>>();
		}
		TreeMap<Integer, ArrayList<Integer>> res = new TreeMap<Integer, ArrayList<Integer>>();
		ArrayList<Integer> tmp = new ArrayList<Integer>();
		tmp.add(jp);
		res.put(jpgrp, tmp);	
		return res;
	}
	
}//class prospectDescr

class EventDescr extends jsonDescr{
	public HashMap<Integer, ArrayList<Integer>> jpMap;
	public EventDescr(Map<String, Object> _mapOfJson, String[] _keysToMatchExact) {super(_mapOfJson,_keysToMatchExact);}
	@Override
	protected TreeMap<Integer, ArrayList<Integer>> convert(String jpAras) {return decodeJPData(jpAras);}
}//class EventDescr
