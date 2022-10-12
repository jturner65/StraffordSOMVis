package strafford_SOM_PKG.straff_RawDataHandling.raw_data;

import java.util.Date;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import base_Utils_Objects.io.messaging.MessageObject;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.base.Straff_BaseRawData;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.json.Straff_JpDescr;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.json.base.Straff_JSONDescr;

///////////////////
//job practice-related data - doesn't use much of the functionality in BaseRawData
public abstract class Straff_JobPracticeData extends Straff_BaseRawData{
	 //jp/jpg ID and name
	 protected Integer ID;
	 protected String name;
	     
 	public Straff_JobPracticeData(MessageObject _msgObj,String _id, String _json, ObjectMapper _mapper, String _typ, boolean hasJson) {
	     super(_msgObj,_id, _json, _mapper, _typ, hasJson);        
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
	public Date getDate() {        return null;    }
	@Override
	public void procInitData(String[] _strAra) {}
	@Override
	protected String dbgGetCustInfoStr() {        return "";    }    
	@Override
	protected Straff_JSONDescr buildDescrObjectFromJsonMap(Map<String, Object> jsonMap) {    return new Straff_JpDescr(msgObj, jsonMap,getRelevantExactKeys());    }
 
}//class jobPracticeData

