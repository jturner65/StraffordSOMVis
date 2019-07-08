package strafford_SOM_PKG.straff_RawDataHandling.raw_data.json.base;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import base_Utils_Objects.io.MessageObject;
import base_Utils_Objects.io.MsgCodes;

/**
 * class to hold a data structure and functions that will parse the json descriptor
 * @author john
 *
 */
public abstract class Straff_JSONDescr{
	protected MessageObject msgObj;
	//just keep the objects that we need from the json based on keys to match exactly
	public Map<String,Object> mapOfRelevantJson;

	public Straff_JSONDescr(MessageObject _msgObj,Map<String,Object> _mapOfJson, String[] _keysToMatchExact) {
		msgObj = _msgObj;
		mapOfRelevantJson= new HashMap<String,Object>();
		for (String key : _keysToMatchExact) {
			Object obj = _mapOfJson.get(key);
			if (null == obj) {msgObj.dispMessage("JSONDescr","constructor"," Key : " + key + " Not found in JSON Map", MsgCodes.error1); continue;}
			mapOfRelevantJson.put(key,obj);
		}        
		//for (String key : _mapOfJson.keySet()) {if (isRelevantKeyExact(key,_keysToMatchExact)) {mapOfRelevantJson.put(key, _mapOfJson.get(key));}}        
	}//ctor

	public void dbgDispJsonVals() {for (String key : mapOfRelevantJson.keySet()) {msgObj.dispMessage("JSONDescr","dbgDispJsonVals","Relevant json entries : " + key + " | Val : --" + mapOfRelevantJson.get(key)+"--", MsgCodes.info1);}    }

	//convert will convert string to map of jpg-keyed arrays of jps
	public abstract TreeMap<Integer, ArrayList<Integer>> convertToJpgJps(String jpAras);

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
				//BaseRawData.RecordJPsJPGsSeen(jp,jpgrp, true);//to debug load of jp's seen
				jpgrpsList.add(jp);    }
			jpgHierarchy.add(jpgrp);
			res.put(jpgrp, jpgrpsList);
		}    
		if (jpgHierarchy.size() > 1) {res.put(-1, jpgHierarchy);}//add hierarchy list if more than 1 jpg/jp to add
		return res;
	}//decodeJPData

}//class jsonDescr