package strafford_SOM_PKG.straff_RawDataHandling;

import java.text.*;
import java.util.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import base_Utils_Objects.*;

//////////////////////
// classes to hold raw data from source - dataLoaders build these
/////////////////////

//base data object from strafford db - describes a prospect or event, keyed by OID
public abstract class BaseRawData {
//    protected static StraffSOMMapManager mapMgr;
    //object for logging/displaying to screen
    protected MessageObject msgObj;
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
    protected JSONDescr dscrObject;
    //set this if has no relevant info for calculations
    public boolean isBadRec;
    
    //map of key==jpgroup, value ==array of jps in decreasing significance
    //index -1 holds array of jpgs in specified order of (assumed) decreasing significance
    public TreeMap<Integer, ArrayList<Integer>> rawJpMapOfArrays;
    
    public BaseRawData(MessageObject _msgObj, String _id, String _json, ObjectMapper _mapper, String _typ, boolean hasJson) {
        msgObj = _msgObj;
        OID=_id.trim().toLowerCase(); TypeOfData = _typ;
        isBadRec = false;
        Map<String, Object> jsonMap = null;
        if (hasJson) {
            try {jsonMap = _mapper.readValue(_json,new TypeReference<Map<String,Object>>(){});}
            catch (Exception e) {
                msgObj.dispMessage("BaseRawData","constructor","Bad " +TypeOfData +" record (corrupted JSON) with OID : " + OID +"!!!! Record will be ignored.", MsgCodes.error1);
                //e.printStackTrace(); 
                isBadRec = true;
            }
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
            for (int i=0; i<szm1;++i) {System.out.print(""+vals.get(i)+",");            }
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
    protected abstract JSONDescr buildDescrObjectFromJsonMap(Map<String, Object> jsonMap);    
    //custom descriptive debug info relevant to this class 
    protected abstract String dbgGetCustInfoStr();        

    @Override    
    public String toString() {
        String res = "OID : " +  OID + "|Type : " + TypeOfData;
        return res;
    }
}//BaseRawData

//////////////////////////
// json decoders

//class to hold a data structure and functions that will parse the json descriptor
abstract class JSONDescr{
	protected MessageObject msgObj;
	//just keep the objects that we need from the json based on keys to match exactly
	protected Map<String,Object> mapOfRelevantJson;

	public JSONDescr(MessageObject _msgObj,Map<String,Object> _mapOfJson, String[] _keysToMatchExact) {
		msgObj = _msgObj;
		mapOfRelevantJson= new HashMap<String,Object>();
		for (String key : _keysToMatchExact) {
			Object obj = _mapOfJson.get(key);
			if (null == obj) {msgObj.dispMessage("jsonDescr","constructor"," Key : " + key + " Not found in JSON Map", MsgCodes.error1); continue;}
			mapOfRelevantJson.put(key,obj);
		}        
		//for (String key : _mapOfJson.keySet()) {if (isRelevantKeyExact(key,_keysToMatchExact)) {mapOfRelevantJson.put(key, _mapOfJson.get(key));}}        
	}//ctor

	public void dbgDispJsonVals() {for (String key : mapOfRelevantJson.keySet()) {msgObj.dispMessage("jsonDescr","dbgDispJsonVals","Relevant json entries : " + key + " | Val : --" + mapOfRelevantJson.get(key)+"--", MsgCodes.info1);}    }

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
				jpgrpsList.add(jp);    }
			jpgHierarchy.add(jpgrp);
			res.put(jpgrp, jpgrpsList);
		}    
		if (jpgHierarchy.size() > 1) {res.put(-1, jpgHierarchy);}//add hierarchy list if more than 1 jpg/jp to add
		return res;
	}//decodeJPData

}//class jsonDescr

//prospect records hold data with specific format, so needs a different convert method
class ProspectDescr extends JSONDescr{
	public ProspectDescr(MessageObject _msgObj,Map<String, Object> _mapOfJson, String[] _keysToMatchExact) {super(_msgObj,_mapOfJson,_keysToMatchExact);}

	@Override
	protected TreeMap<Integer, ArrayList<Integer>> convertToJpgJps(String jpRawDataStr) {    
		//NOT READING PROSPECT RECORD FOR JP/JPG anymore so this should be ignored - this is replaced by using source data
		return new TreeMap<Integer, ArrayList<Integer>>();
	}    
}//class prospectDescr

class EventDescr extends JSONDescr{
	public HashMap<Integer, ArrayList<Integer>> jpMap;
	public EventDescr(MessageObject _msgObj,Map<String, Object> _mapOfJson, String[] _keysToMatchExact) {super(_msgObj,_mapOfJson,_keysToMatchExact);}
	@Override
	protected TreeMap<Integer, ArrayList<Integer>> convertToJpgJps(String jpAras) {return decodeJPData(jpAras);}
}//class EventDescr

class jobPracDescr extends JSONDescr{
	public jobPracDescr(MessageObject _msgObj,Map<String, Object> _mapOfJson, String[] _keysToMatchExact) {super(_msgObj,_mapOfJson, _keysToMatchExact);}
	@Override
	protected TreeMap<Integer, ArrayList<Integer>> convertToJpgJps(String jpAras) {    return null;}//doesn't use this

}//class jobPracDescr

//doesn't use json in db currently to describe data, just has string values in columns, but they follow the same format as event data
class TCTagDescr extends JSONDescr{
	public TCTagDescr(MessageObject _msgObj,Map<String, Object> _mapOfJson, String[] _keysToMatchExact) {        super(_msgObj,_mapOfJson, _keysToMatchExact);    }
	@Override
	protected TreeMap<Integer, ArrayList<Integer>> convertToJpgJps(String jpAras){return decodeJPData(jpAras);}
}//class TCTagDescr