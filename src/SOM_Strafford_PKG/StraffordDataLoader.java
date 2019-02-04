package SOM_Strafford_PKG;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;

/**
* this class will load, and manage, the appropriate files containing the Strafford data used to build the SOM.  
* ultimately this class will also execute the appropriate SQL commands to call db
 * 
 * @author john
 *
 */
public abstract class StraffordDataLoader implements Callable<Boolean> {
	//ref to owning object
	protected static SOMMapManager mapMgr;
	//key in destination map of data arrays where data should be loaded
	protected String destAraDataKey;
	//used to decipher json - need one per instance/thread
	public ObjectMapper jsonMapper;
	//source file name and path or sql-related connection info (file name of csv holding sql connection info?)
	protected String fileNameAndPath;
	//whether is file loader (from csv files) or sql reader
	protected boolean isFileLoader;
	//flag index in owning map denoting the processing is finished
	protected int isDoneMapDataIDX;
	//# of columns used by the particular table being read - used to verify if all fields are present in table.  if not known, set to 2
	protected boolean hasJson;
	//set whether we want to debug this loader or not
	protected boolean debug;
	
	public StraffordDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {
		jsonMapper = new ObjectMapper();
		fileNameAndPath = _dataLocInfoStr;
		isFileLoader = _isFileLoader;
	}//ctor
	
	//use this to set values for each individual load.
	//_dataInfoAra : idx0 : idx in SOMMapManager.rawDataArrays where the loaded data should go
	//				 idx1 : file path and name
	//_flagsAra    : idx0 : if this is file/sql loader; idx1 : if the source data has json columns; idx2 : debug
	//_isDOneIDX : boolean flag idx in SOMMapManager to mark that this data loader has finished

	public void setLoadData(SOMMapManager _mapMgr, String[] _dataInfoAra, boolean[] _flagsAra, int _isDoneIDX) {
		mapMgr = _mapMgr;
		BaseRawData.mapMgr = _mapMgr;
		destAraDataKey = _dataInfoAra[0];
		fileNameAndPath = _dataInfoAra[1];
		isFileLoader = _flagsAra[0];
		hasJson = _flagsAra[1];
		debug = _flagsAra[2];
		isDoneMapDataIDX = _isDoneIDX;
	}//setLoadData
	
	protected ArrayList<BaseRawData> execLoad(){	
		ArrayList<BaseRawData> dataObjs = new ArrayList<BaseRawData>();
		if (isFileLoader) {
			streamCSVDataAndBuildStructs(dataObjs);
			mapMgr.dispMessage("StraffordDataLoader","execLoad","File Load Finished for "+fileNameAndPath, MsgCodes.info5);
		} else {//exec sql load for this file
			//TODO sql read/load here
			mapMgr.dispMessage("StraffordDataLoader","execLoad","Sql Load NOT IMPLEMENTED using connection info at "+fileNameAndPath, MsgCodes.error1);
		}
		return dataObjs;
	}//execLoad
	
	//parse string into data object, which will then construct training/testing data for som map
	protected abstract BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON);	
	private void streamCSVDataAndBuildStructs(ArrayList<BaseRawData> dAra) {try {_strmCSVFileBuildObjs(dAra);} catch (Exception e) {e.printStackTrace();}}
	//stream read the csv file and build the data objects
	private void _strmCSVFileBuildObjs(ArrayList<BaseRawData> dAra) throws IOException {
		mapMgr.dispMessage("StraffordDataLoader","streamCSVDataAndBuildStructs","Start loading "+ fileNameAndPath, MsgCodes.info5);
		FileInputStream inputStream = null;
		Scanner sc = null;
	    int line = 1, badEntries = 0;
		try {
			//System.out.println("Working Directory = " +System.getProperty("user.dir"));
		    inputStream = new FileInputStream(fileNameAndPath);
		    sc = new Scanner(inputStream);
		    //get rid of headers
		    sc.nextLine();
		    boolean done = false;
		    while ((sc.hasNextLine()) && !(done)) {
		    	String datStr = sc.nextLine();
		    	//System.out.println("line : " +line + " | datStr : "+datStr);
		    	if (hasJson) {//jp and other relevant data is stored in payload/descirptor field holding json			    	
			    	String [] strAras1 = datStr.split("\"\\{");		//split into string holding columns and string holding json 
			    	if (strAras1.length < 2) {		    		
				    		if(debug) {mapMgr.dispMessage("StraffordDataLoader","streamCSVDataAndBuildStructs","!!!!!!!!!" +destAraDataKey+ " has bad entry at "+line+" lacking required description/payload json : " + datStr, MsgCodes.warning1);}
				    		++badEntries;			    	
			    	} else {
			    		String str = strAras1[1];
			    	    if (str.charAt(str.length() - 1) == '"') {str = str.substring(0, str.length() - 1);}//remove trailing quote
				        String jsonStr = "{"+str.replace("\t", "");
				        String[] vals = strAras1[0].replace("\"","").split(",");
				        //convert string to object to be used to train map		        
			        	addObjToDAra(dAra, vals, jsonStr, hasJson);
			    	}		    				    		
		    	} else {//currently only TC_Taggings, 2 columns, 
		    		//doesn't use json, so jsonStr can be empty
		    		//parse 2 entries and remove extraneous quotes - replace quote-comma-quote with apost-comma-apost, get rid of all quotes, then split on apost-comma-apost.  this retains jpg-jp list structure while splitting on columns properly
			        String[] vals = datStr.replace("\",\"","','").replace("\"","").split("','");
			        if(vals.length < 2) {
			        	if(debug) {mapMgr.dispMessage("StraffordDataLoader","streamCSVDataAndBuildStructs","!!!!!!!!!" +destAraDataKey+ " has bad entry at "+line+" lacking required jp list : " + datStr, MsgCodes.warning1);}
			    		++badEntries;			    				        	
			        } else {
			        	addObjToDAra(dAra, vals, "", hasJson);
			        }
		    	}
		        ++line;		        
		        if (line % 100000 == 0) {mapMgr.dispMessage("StraffordDataLoader","streamCSVDataAndBuildStructs","" +destAraDataKey+ " Finished line : "+line, MsgCodes.info3); 	}
		    }
		    //Scanner suppresses exceptions
		    if (sc.ioException() != null) { throw sc.ioException(); }
		} catch (Exception e) {	e.printStackTrace();} 
		finally {
		    if (inputStream != null) {inputStream.close();		    }
		    if (sc != null) { sc.close();		    }
		}
		mapMgr.dispMessage("StraffordDataLoader","streamCSVDataAndBuildStructs","Finished loading "+ fileNameAndPath + " With "+badEntries+" bad/missing json entries and " + dAra.size() + " good records.", MsgCodes.info5);
	}//loadFileContents

	private void addObjToDAra(ArrayList<BaseRawData> dAra, String[] vals, String jsonStr, boolean hasJson) {
        BaseRawData obj = parseStringToObj(vals, jsonStr, hasJson);
        if (obj.isBadRec) {
        	if(debug) {mapMgr.dispMessage("StraffordDataLoader","addObjToDAra","" +destAraDataKey+ " : " + obj.toString() + " is a useless record due to " + obj.getWhyBadRed() + ".  Record is Ignored.", MsgCodes.info3);}
        } else {
        	dAra.add(obj);
        }		
	}//addObjToDAra
	
	@Override
	public Boolean call() throws Exception {
		//execute load, take load results and add to mapData.rawDataArrays
		ArrayList<BaseRawData> dataObjs = execLoad();
		//put in destAraDataKey of  mapData.rawDataArrays
		mapMgr.rawDataArrays.put(destAraDataKey, dataObjs);
		//set boolean in mapData to mark that this is finished
		mapMgr.setFlag(isDoneMapDataIDX, true);
		return true;
	}//call launches this loader - when finished will return true
		
}//StraffordDataLoader base class

/////////////////////
// 	Instancing classes
//stream prospects and build the objects that will then decipher their json content and build the training/testing data based on them
class ProspectDataLoader extends StraffordDataLoader{
	public ProspectDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON) {
		prospectData obj = new prospectData(mapMgr,strAra[0].trim(), jsonStr.trim(),jsonMapper,hasJSON);
		obj.procInitData(strAra);
		return obj;
	}
}//

//stream Order Events and build the objects that will then decipher their json content and build the training/testing data based on them
class OrderEventDataLoader extends StraffordDataLoader{
	public OrderEventDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON) {
		OrderEvent obj = new OrderEvent(mapMgr,strAra[0].trim(), jsonStr.trim(),jsonMapper,hasJSON);
		obj.procInitData(strAra);
		return obj;
	}
}//
//stream link Events and build the objects that will then decipher their json content and build the training/testing data based on them
class LinkEventDataLoader extends StraffordDataLoader{
	public LinkEventDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON) {
		LinkEvent obj = new LinkEvent(mapMgr,strAra[0].trim(), jsonStr.trim(),jsonMapper,hasJSON);
		obj.procInitData(strAra);
		return obj;
	}
}//
//stream opt Events and build the objects that will then decipher their json content and build the training/testing data based on them
class OptEventDataLoader extends StraffordDataLoader{
	public OptEventDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON) {
		OptEvent obj = new OptEvent(mapMgr,strAra[0].trim(), jsonStr.trim(),jsonMapper,hasJSON);
		obj.procInitData(strAra);
		return obj;
	}
}//
//stream tcTagdata to build product examples
class TcTagDataLoader extends StraffordDataLoader{
	public TcTagDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON) {
		TcTagData obj = new TcTagData(mapMgr,strAra[0].trim(), jsonStr.trim(),jsonMapper,hasJSON);
		obj.procInitData(strAra);
		return obj;
	}
}//

//stream jpg data for descriptions
class JpgrpDataLoader extends StraffordDataLoader{
	public JpgrpDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON) {
		JpgrpDescData obj = new JpgrpDescData(mapMgr,strAra[0].trim(), jsonStr.trim(),jsonMapper,hasJSON);
		obj.procInitData(strAra);
		return obj;
	}
}//

//stream jp data for descriptions
class JpDataLoader extends StraffordDataLoader{
	public JpDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON) {
		JpDescData obj = new JpDescData(mapMgr,strAra[0].trim(), jsonStr.trim(),jsonMapper,hasJSON);
		obj.procInitData(strAra);
		return obj;
	}
}//

//stream jp data for descriptions

//////////////////////
// classes to hold raw data from source - dataLoaders build these
/////////////////////

//base data object from strafford db - describes a prospect or event, keyed by OID
abstract class BaseRawData {
	protected static SOMMapManager mapMgr;
	//format of dates in db records
	public static final String dateFormatString = "yyyy-MM-dd HH:mm:ss";
	//construction to keep track of count of seen jp ids : key is jp, val is count, for debugging
	private static TreeMap<Integer, Integer> jpSeen = new TreeMap<Integer, Integer>();
	//construction to keep track of count of jp ids seen in events : key is jp, val is count for debugging
	private static TreeMap<Integer, Integer> jpEvSeen = new TreeMap<Integer, Integer>();
	//construction to keep track of the jps associated with particular jpg
	private static TreeMap<Integer, TreeSet <Integer>> jpgroupsAndJps = new TreeMap<Integer, TreeSet <Integer>>();	
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
	
	public BaseRawData(SOMMapManager _mapMgr, String _id, String _json, ObjectMapper _mapper, String _typ, boolean hasJson) {
		mapMgr = _mapMgr;
		OID=_id.trim().toLowerCase(); TypeOfData = _typ;
		isBadRec = false;
		Map<String, Object> jsonMap = null;
		if (hasJson) {
			try {jsonMap = _mapper.readValue(_json,new TypeReference<Map<String,Object>>(){});}
			catch (Exception e) {e.printStackTrace(); 		}
			if (jsonMap != null) {
				dscrObject = buildDescrObjectFromJsonMap(jsonMap);
			}
		} else {
			jsonMap = null;
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
	
	//return string describing why badRec has been set to true.  usually is because no JP/JPG data
	public String getWhyBadRed() {return "having no decipherable jp/jpg data";}
	
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
	
	public boolean hasBadJP = false;
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
			for (int i=0; i<szm1;++i) {
				Integer val = vals.get(i);
				if (val <20) {hasBadJP=true;}
				res += val+",";}
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

	public prospectData(SOMMapManager _mapMgr,String _id, String _json, ObjectMapper _mapper, boolean hasJson) {
		super(_mapMgr,_id, _json, _mapper, "prospect", hasJson);
		String jpsList = dscrObject.mapOfRelevantJson.get("jp").toString();
		String luDateStr = dscrObject.mapOfRelevantJson.get("lu").toString();
		if(luDateStr.length() > 0) {luDate = BaseRawData.buildDateFromString(luDateStr);}
		else {
			isEmptyPrspctRec = jpsList.length() - 2 == 0;//if jpsList is length 2, then no jps in this record, and no last update means this record has very little pertinent data-neither a date nor a jp associated with it
		}
		if (null==jpsList) {
			mapMgr.dispMessage("prospectData","constructor"," Null jpsList for json string " + _json, MsgCodes.error1);
		} else {
			rawJpMapOfArrays = dscrObject.convertToJpgJps(jpsList);			
		}
	}//ctor
	
	//build descriptor object from json map
	@Override
	protected jsonDescr buildDescrObjectFromJsonMap(Map<String, Object> jsonMap) {return new prospectDescr(mapMgr, jsonMap,getRelevantExactKeys());	}	
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

// class to describe tc_taggings data - this just consists of two columns of data per record, an id and a jp list
//note this data is not stored as json in the db
class TcTagData extends BaseRawData{
	//doesn't use json so no list of keys, just following format used for events since jp lists follow same format in db
	private static final String[] relevantExactKeys = {};
	public TcTagData(SOMMapManager _mapMgr,String _id, String _json, ObjectMapper _mapper, boolean hasJson) {	
		//change to true if tc-taggings ever follows event format of having json to describe the jpg/jp lists
		super(_mapMgr,_id, _json, _mapper, "TC_Tags", hasJson);		
		//descr object not made in super if doesn't use json
		if (!hasJson) {//if doesn't use, wont' have json map - use dscrObject to convert string from string array in procInitData
			Map<String, Object> jsonMap = null;
			dscrObject = buildDescrObjectFromJsonMap(jsonMap);
		}				
	}//ctor

	@Override
	public Date getDate() {	return null;}//currently no date in tc record
	//initialize actual list of jps here
	@Override
	public void procInitData(String[] _strAra) {//idx 1 is jpg/jp listing of same format as json
		if (_strAra.length != 2) {
			System.out.print("TcTagData::procInitData : len of Str ara in tc taggings class : " +_strAra.length + " Entries : [");
			for (int i=0;i<_strAra.length-1;++i) {System.out.print(""+i+": -"+_strAra[i]+"- , ");}
			System.out.println(""+(_strAra.length-1)+": -"+_strAra[_strAra.length-1]+"-]");
		}
		rawJpMapOfArrays = dscrObject.convertToJpgJps(_strAra[1]);		
		//if(rawJpMapOfArrays.size() > 1) {System.out.println("tcTaggings procInitData OID : " + OID + " Str1 : " + _strAra[1] + " Raw Map of arrays : " + getJpsToString());}
	}//procInitData
	@Override
	public String[] getRelevantExactKeys() {	return relevantExactKeys;	}
	@Override
	protected jsonDescr buildDescrObjectFromJsonMap(Map<String, Object> jsonMap) {return new TCTagDescr(mapMgr, jsonMap,getRelevantExactKeys());}
	@Override
	protected String dbgGetCustInfoStr() {	return "";	}	
	@Override
	public String toString() {
		String res = super.toString() + " : TC Tag :";
		res +=getJpsToString();
		return res;
	}
}//class TcTagData

///////////////////
// job practice-related data - doesn't use much of the functionality in BaseRawData
abstract class jobPracticeData extends BaseRawData{
	//jp/jpg ID and name
	protected Integer ID;
	protected String name;
		
	public jobPracticeData(SOMMapManager _mapMgr,String _id, String _json, ObjectMapper _mapper, String _typ, boolean hasJson) {
		super(_mapMgr,_id, _json, _mapper, _typ, hasJson);		
		name = dscrObject.mapOfRelevantJson.get("name").toString().trim();
		//0 length name for any record of this type is useless
		isBadRec = name.length() == 0;
		ID = Integer.parseInt(_id);
	}//ctor
	//return string describing why badRec has been set to true.  usually is because no JP/JPG data
	@Override
	public String getWhyBadRed() {return "having no specified name in record";}
	//return jp/jpg
	public Integer getJPID() {return ID;}
	//return jp/jpg name
	public String getName() {return name;}
	
	@Override
	public Date getDate() {		return null;	}
	@Override
	public void procInitData(String[] _strAra) {}
	@Override
	protected String dbgGetCustInfoStr() {		return "";	}	
	@Override
	protected jsonDescr buildDescrObjectFromJsonMap(Map<String, Object> jsonMap) {	return new jobPracDescr(mapMgr, jsonMap,getRelevantExactKeys());	}
	
}//class jobPracticeData

//class to hold a raw record of jp data
class JpDescData extends jobPracticeData{
	private Integer jpgrp = -1;
	private String jpgrpName = "none";
	//keys in json relevant for this data
	private static final String[] relevantExactKeys = {"name","job_practice_group"};
	
	public JpDescData(SOMMapManager _mapMgr,String _id, String _json, ObjectMapper _mapper, boolean hasJson) { 
		super(_mapMgr,_id, _json, _mapper,"jpDesc",hasJson);
		String jpgrpString = "";
		Object jpgrpJSon = dscrObject.mapOfRelevantJson.get("job_practice_group");		
		if ((null != jpgrpJSon) && (!isBadRec)) {
			jpgrpString = jpgrpJSon.toString().replace("{","").replace("}","").trim();
			if (jpgrpString.length() > 0) {
				String jpgIDStr = jpgrpString.split("id=")[1].trim().split(",")[0].trim();
				jpgrp = Integer.parseInt(jpgIDStr);
				jpgrpName = jpgrpString.split("name=")[1].trim().split(",")[0].trim();		
			}
		}		
	}//ctor
	
	public Integer getParentJPGrp() {return jpgrp;}
	public String getParentJPGrpName() {return jpgrpName;}	
	@Override
	public String[] getRelevantExactKeys() {		return relevantExactKeys;}
	@Override
	public String toString() {
		String res = super.toString() ;
		res +="JP : " + ID + " | Name : " + name + " | JpGrp : " + jpgrp + " | JpGrp Name : " + jpgrpName;
		return res;
	}
	
}//class jpDescData

//class to hold a raw record of jp group data
class JpgrpDescData extends jobPracticeData{
	//keys in json relevant for this data
	private static final String[] relevantExactKeys = {"name"};
	
	public JpgrpDescData(SOMMapManager _mapMgr,String _id, String _json, ObjectMapper _mapper, boolean hasJson) { super(_mapMgr,_id, _json, _mapper,"jpgrpDesc",hasJson);}
	@Override
	public String[] getRelevantExactKeys() {		return relevantExactKeys;}
	@Override
	public String toString() {
		String res = super.toString() ;
		res +="JpGrp : " + ID + " | Name : " + name ;
		return res;
	}
	
}//class jpgDescData

////////////////////////////////
//	event-related raw data.  primary source of training data
//class to provide a description of an event read in from db-derived csv
abstract class EventRawData extends BaseRawData {	
	//date this event occurred
	private Date eventDate;
	//event id
	private Integer eventID;
	//event type
	private String eventType;
	
	public EventRawData(SOMMapManager _mapMgr,String _id, String _json, ObjectMapper _mapper, String _typ, boolean hasJson) {
		super(_mapMgr,_id, _json, _mapper,_typ, hasJson);
		String jpsList = dscrObject.mapOfRelevantJson.get("jps").toString();
		rawJpMapOfArrays = dscrObject.convertToJpgJps(jpsList);
	}
		
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
	protected jsonDescr buildDescrObjectFromJsonMap(Map<String, Object> jsonMap) {return new EventDescr(mapMgr, jsonMap,getRelevantExactKeys());	}

	@Override
	public String toString() {
		String res = super.toString() + " Event Type : " +eventType + " Event Date : " + eventDate + " EventID " + eventID;
		return res;
	}
}//EventRawData

class OrderEvent extends EventRawData{
	//these should all be lowercase - these are exact key substrings we wish to match in json, to keep and use to build training data - all the rest of json data is being tossed
	private static final String[] relevantExactKeys = {"jps"};
	
	public OrderEvent(SOMMapManager _mapMgr,String _id, String _json, ObjectMapper _mapper, boolean hasJson) {super(_mapMgr,_id, _json, _mapper,"orders",hasJson); this.isBadRec = rawJpMapOfArrays.size() == 0;}

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
	
	public LinkEvent(SOMMapManager _mapMgr,String _id, String _json, ObjectMapper _mapper, boolean hasJson) {super(_mapMgr,_id, _json, _mapper,"links",hasJson);this.isBadRec = rawJpMapOfArrays.size() == 0;}

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
	
	public OptEvent(SOMMapManager _mapMgr,String _id, String _json, ObjectMapper _mapper, boolean hasJson) {
		super(_mapMgr,_id, _json, _mapper,"opts",hasJson);
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
	protected static SOMMapManager mapMgr;
	//just keep the objects that we need from the json based on keys to match exactly
	protected Map<String,Object> mapOfRelevantJson;
	
	public jsonDescr(SOMMapManager _mapMgr,Map<String,Object> _mapOfJson, String[] _keysToMatchExact) {
		mapMgr = _mapMgr;
		mapOfRelevantJson= new HashMap<String,Object>();
		for (String key : _keysToMatchExact) {
			Object obj = _mapOfJson.get(key);
			if (null == obj) {mapMgr.dispMessage("jsonDescr","constructor"," Key : " + key + " Not found in JSON Map", MsgCodes.error1); continue;}
			mapOfRelevantJson.put(key,obj);
		}		
		//for (String key : _mapOfJson.keySet()) {if (isRelevantKeyExact(key,_keysToMatchExact)) {mapOfRelevantJson.put(key, _mapOfJson.get(key));}}		
	}//ctor
	
	public void dbgDispJsonVals() {for (String key : mapOfRelevantJson.keySet()) {mapMgr.dispMessage("jsonDescr","dbgDispJsonVals","Relevant json entries : " + key + " | Val : --" + mapOfRelevantJson.get(key)+"--", MsgCodes.info1);}	}
		
	//convert will convert string to map of jpg-keyed arrays of jps
	protected abstract TreeMap<Integer, ArrayList<Integer>> convertToJpgJps(String jpAras);
	
	//this will convert a string of jp data to the appropriate format -  in original record
	//data is arrays of arrays where 1st idx is jpg and subsequent ids are jps of decreasing relevance
	//in this structure key will be jpg and array is list of jps in decreasing relevance 
	//idx -1 holds order of supremacy of jpgroups
	protected TreeMap<Integer, ArrayList<Integer>> decodeJPData(String jpAras){
		TreeMap<Integer, ArrayList<Integer>> res = new TreeMap<Integer, ArrayList<Integer>>();
		String strOfAras = jpAras.replace("\\}", "").replaceAll("\"", "").trim();
		if (strOfAras.length() < 3) {return res;}//means this is empty
		//need to support appropriate split here - 0 or 1 space
		String[] araOfAras = strOfAras.split("\\], ?\\[");
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
	}//decodeJPData
	
}//class jsonDescr

//prospect records hold data with specific format, so needs a different convert method
class prospectDescr extends jsonDescr{
	public prospectDescr(SOMMapManager _mapMgr,Map<String, Object> _mapOfJson, String[] _keysToMatchExact) {super(_mapMgr,_mapOfJson,_keysToMatchExact);}

	@Override
	protected TreeMap<Integer, ArrayList<Integer>> convertToJpgJps(String jpRawDataStr) {	
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
			mapMgr.dispMessage("prospectDescr","convertToJpgJps"," Poorly formatted jp breaks jpgrp query : " + jp + " | raw str : " + jpRawDataStr + ". Entry is ignored.", MsgCodes.error1);
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
	public EventDescr(SOMMapManager _mapMgr,Map<String, Object> _mapOfJson, String[] _keysToMatchExact) {super(_mapMgr,_mapOfJson,_keysToMatchExact);}
	@Override
	protected TreeMap<Integer, ArrayList<Integer>> convertToJpgJps(String jpAras) {return decodeJPData(jpAras);}
}//class EventDescr

class jobPracDescr extends jsonDescr{
	public jobPracDescr(SOMMapManager _mapMgr,Map<String, Object> _mapOfJson, String[] _keysToMatchExact) {super(_mapMgr,_mapOfJson, _keysToMatchExact);}
	@Override
	protected TreeMap<Integer, ArrayList<Integer>> convertToJpgJps(String jpAras) {	return null;}//doesn't use this
	
}//class jobPracDescr

//doesn't use json in db currently to describe data, just has string values in columns, but they follow the same format as event data
class TCTagDescr extends jsonDescr{
	public TCTagDescr(SOMMapManager _mapMgr,Map<String, Object> _mapOfJson, String[] _keysToMatchExact) {		super(_mapMgr,_mapOfJson, _keysToMatchExact);	}
	@Override
	protected TreeMap<Integer, ArrayList<Integer>> convertToJpgJps(String jpAras){return decodeJPData(jpAras);}
}//class TCTagDescr