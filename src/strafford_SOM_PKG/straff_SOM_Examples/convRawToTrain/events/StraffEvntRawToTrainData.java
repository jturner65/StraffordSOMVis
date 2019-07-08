package strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.events;

import java.util.ArrayList;
import java.util.Date;
import java.util.TreeMap;

import strafford_SOM_PKG.straff_RawDataHandling.raw_data.Straff_BaseRawData;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.Straff_EventRawData;
import strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.Straff_JpgJpDataRecord;
import strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.Straff_RawToTrainData;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.Straff_JP_OccurrenceData;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.Straff_ProspectExample;

/**
 * this class will hold event data for a single OID for a single date
 * multiple events might have occurred on a single date, this will aggregate all relevant event data of a particular type
 * @author john
 *
 */
public abstract class StraffEvntRawToTrainData extends Straff_RawToTrainData{
	//every unique eventID seems to occur only on 1 date
	protected Integer eventID;	
	protected Date eventDate;
	protected String eventType;	
	
	public StraffEvntRawToTrainData(Straff_EventRawData ev, String _srcTypeName) {
		super(_srcTypeName);
		eventID = ev.getEventID();//should be the same event type for event ID
		eventType = ev.getEventType();
		eventDate = ev.getDate();
	}//ctor from rawDataObj
	
	public StraffEvntRawToTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _srcTypeName) {
		super(_srcTypeName);
		eventID = _evIDStr;//should be the same event type for event ID
		eventType = _evTypeStr;
		eventDate = Straff_BaseRawData.buildDateFromString(_evDateStr);		
	}//ctor from indiv data	via csv string

	//process JP occurence data for this event
	//passed is ref to map of all occurrences of jps in this kind of event's data for a specific prospect
	public void procJPOccForAllJps(Straff_ProspectExample ex, TreeMap<Integer, Straff_JP_OccurrenceData> jpOccMap, String type, boolean usesOpt, int[] _dbg_numOptAllOccs) {
		for (Straff_JpgJpDataRecord rec : listOfJpgsJps) {
			int opt = rec.getOptVal();
			Integer jpg = rec.getJPG();
			ArrayList<Integer> jps = rec.getJPlist();
			//this is sentinel value marker for opt events where all jpgs/jps are specified to have same opt value
			if((jpg == -10) && (jps.get(0) == -9)) {// opt choice covering all jpgs/jps
				++_dbg_numOptAllOccs[0];
				if  (opt <= 0) {//this is negative opt across all records 
					++_dbg_numOptAllOccs[1];
					//from here we are processing a positive opt record across all jps
					Straff_JP_OccurrenceData jpOcc = ex.getNegOptAllOccObj();
					if (jpOcc==null) {jpOcc = new Straff_JP_OccurrenceData(-9, -10, usesOpt);}
					jpOcc.addOccurrence(eventDate, rec.getOptVal());		
					ex.setNegOptAllOccObj(jpOcc);					
				} else {		//this is a non-negative opt across all records
					++_dbg_numOptAllOccs[2];
					//from here we are processing a positive opt record across all jps
					Straff_JP_OccurrenceData jpOcc = ex.getPosOptAllOccObj();
					if (jpOcc==null) {jpOcc = new Straff_JP_OccurrenceData(-9, -10, usesOpt);}
					jpOcc.addOccurrence(eventDate, rec.getOptVal());		
					ex.setPosOptAllOccObj(jpOcc);				
				}
			} else {
				for (Integer jp : jps) {
					Straff_JP_OccurrenceData jpOcc = jpOccMap.get(jp);
					if (jpOcc==null) {jpOcc = new Straff_JP_OccurrenceData(jp, jpg,usesOpt);}
					jpOcc.addOccurrence(eventDate, rec.getOptVal());		
					//add this occurence object to map at idx jp
					jpOccMap.put(jp, jpOcc);
				}
			}//if is opt-across-alljps event or not
		}//for all jpgjp record data	
	}//procJPOccForAllJps

	@Override
	public String buildCSVString() {
		String res = "EvSt,EvType,"+eventType+",EvID,"+eventID+",EvDt,"+ Straff_BaseRawData.buildStringFromDate(eventDate)+",numJPGs,"+listOfJpgsJps.size()+",";	
		res += buildJPGJP_CSVString();
		res += "EvEnd,";			
		return res;		
	}//buildCSVString	
	public Date getEventDate() {return eventDate;}	
	public Integer getEventID() {return eventID;}
	public String getEventType() {return eventType;}
	public void setEventDate(Date eventDate) {					this.eventDate = eventDate;}
	public void setEventID(Integer eventID) {					this.eventID = eventID;	}
	public void setEventType(String eventType) {				this.eventType = eventType;	}
	@Override
	public String toString() {
		String res = "EventID : " + eventID + "|Event Date : " +  eventDate + "|Event Type : " +  eventType + "\n";
		res += super.toString();
		return res;
 	}	
}//class StraffEvntTrainData
