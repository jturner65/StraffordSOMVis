package strafford_SOM_PKG.straff_RawDataHandling.raw_data;

import java.util.Date;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import base_Utils_Objects.MessageObject;

////////////////////////////////
//event-related raw data.  primary source of training data
//class to provide a description of an event read in from db-derived csv
public abstract class EventRawData extends BaseRawData {    
	//date this event occurred
	private Date eventDate;
	//event id
	private Integer eventID;
	//event type
	private String eventType;

	public EventRawData(MessageObject _msgObj,String _id, String _json, ObjectMapper _mapper, String _typ, boolean hasJson) {
		super(_msgObj,_id, _json, _mapper,_typ, hasJson);
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
	protected JSONDescr buildDescrObjectFromJsonMap(Map<String, Object> jsonMap) {return new EventDescr(msgObj, jsonMap,getRelevantExactKeys());    }

	@Override
	public String toString() {
		String res = super.toString() + " Event Type : " +eventType + " Event Date : " + eventDate + " EventID " + eventID;
		return res;
	}
}//EventRawData
