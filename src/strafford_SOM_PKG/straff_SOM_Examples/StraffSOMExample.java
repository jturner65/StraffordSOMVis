package strafford_SOM_PKG.straff_SOM_Examples;

import java.util.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_Utils_Objects.*;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.*;
import strafford_SOM_PKG.straff_SOM_Mapping.*;
import strafford_SOM_PKG.straff_Utils.MonitorJpJpgrp;


/**
 * NOTE : None of the data types in this file are thread safe so do not allow for opportunities for concurrent modification
 * of any instanced object in this file. This decision was made for speed concerns - 
 * concurrency-protected objects have high overhead that proved greater penalty than any gains in 10 execution threads.
 * 
 * All multithreaded access of these objects should be designed such that any individual object is only accessed by 
 * a single thread.
 * 
 * Objects of this class will hold the relevant data acquired from the db to build a datapoint used by the SOM
 * It will take raw data and build the appropriate feature vector, using the appropriate calculation 
 * to weight the various jpgroups/jps appropriately.  The ID of this construct should be such that it 
 * can be uniquely qualified/indexed by it and will either be the ID of a particular prospect in the 
 * prospect database or some other unique identifier if this is representing, say, a target product
 * @author john
 *
 */

public abstract class StraffSOMExample extends SOMExample{
	//reference to jp-jpg mapping/managing object
	protected static MonitorJpJpgrp jpJpgMon;
	
	//all jps in this example that correspond to actual products.
	//products are used for training vectors - these will be used to build feature vector used by SOM
	protected HashSet<Integer> allProdJPs;
	
	//all jps in this example that do not correspond to actual products - these are for intra-jpgroup comparisons, 
	//and for matching true prospects (who may have "virtual" and "venn" jps) to products - 
	//uses jpgjp tuple since actual comparison will be build by jp group 
	//these will only be seen in source event records
	protected HashSet<Tuple<Integer,Integer>> nonProdJpgJps; 
	
	//alternate comparison structure - used in conjunction with ftrVec of chosen format
	//use a map to hold only sparse data of each frmt for feature vector
	//each array element map corresponds to a type of ftr map - ftr, norm and std
	//each map has key == to _jpg_ and value == multiplier
	protected TreeMap<Integer, Float>[] compValMaps;		

	//idxs corresponding to types of events
	
	public StraffSOMExample(SOMMapManager _map, ExDataType _type, String _id) {
		super(_map, _type, _id);
		jpJpgMon = ((StraffSOMMapManager) mapMgr).jpJpgrpMon;
		allProdJPs = new HashSet<Integer> ();	
		nonProdJpgJps = new HashSet<Tuple<Integer,Integer>>();
		compValMaps = new TreeMap[ftrMapTypeKeysAra.length];
		for (int i=0;i<ftrMaps.length;++i) {			compValMaps[i] = new TreeMap<Integer, Float>(); 		}

	}//ctor
	
	public StraffSOMExample(StraffSOMExample _otr) {
		super(_otr);	
		allProdJPs = _otr.allProdJPs;		
		nonProdJpgJps = _otr.nonProdJpgJps;
		compValMaps = _otr.compValMaps;
	}//copy ctor
	
	////////////////
	// event processing 
	///////////////
	//eventtype : Order(0),Opt(1), Link(2), Source(3);
	protected StraffEvntTrainData buildNewTrainDataFromEv(EventRawData _evntObj, EvtDataType eventtype) {
		switch (eventtype) {
		case Order 	: {		return new OrderEventTrainData((OrderEvent) _evntObj);	}//order event object
		case Opt 	: {		return new OptEventTrainData((OptEvent) _evntObj);}//opt event  object
		case Link 	: { 	return new LinkEventTrainData((LinkEvent) _evntObj);}//link event
		case Source : { 	return new SrcEventTrainData((SourceEvent) _evntObj);}//source event
		default : {			return new OrderEventTrainData((OrderEvent) _evntObj);	}//default to order event object - probably will fail.  need to make sure type is accurately specified.
		}//switch
	}//buildNewTrainData
	
	//eventtype : 0 : order, 1 : opt, 2 : link
	protected StraffEvntTrainData buildNewTrainDataFromStr(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr, EvtDataType eventtype) {
		switch (eventtype) {
		case Order 	: { 	return new OrderEventTrainData(_evIDStr, _evTypeStr, _evDateStr, _evntStr);	}//order event object
		case Opt 	: {		return new OptEventTrainData(_evIDStr, _evTypeStr, _evDateStr, _evntStr);}//opt event  object
		case Link 	: {		return new LinkEventTrainData(_evIDStr, _evTypeStr, _evDateStr, _evntStr);}//link event  object
		case Source : {		return new SrcEventTrainData(_evIDStr, _evTypeStr, _evDateStr, _evntStr);}//source event  object
		default : {			return new OrderEventTrainData(_evIDStr, _evTypeStr, _evDateStr, _evntStr);	}//default to order event object - probably will fail.  need to make sure type is accurately specified.
		}//switch
	}//buildNewTrainData		
	
	/**
	 * add object keyed by addDate, either adding to existing list or building a new list if none exists
	 * @param _optObj : object to add
	 * @param map : date-keyed map to add object to
	 * @param type : int type of object
	 */
	protected void addDataToTrainMap(EventRawData _optObj, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> map, EvtDataType type){		
		Date addDate = _optObj.getDate();
		Integer eid = _optObj.getEventID();	//identical events may occur - multiple same event ids on same date, even with same jpg/jp data.  should all be recorded
		//get date's specific submap
		TreeMap<Integer, StraffEvntTrainData> objMap = map.get(addDate);
		if (null == objMap) {objMap = new TreeMap<Integer, StraffEvntTrainData>();}
		//get list of events from specific event id
		StraffEvntTrainData evtTrainData = objMap.get(eid);
		if (null == evtTrainData) {		evtTrainData = buildNewTrainDataFromEv(_optObj,type);	}//build new event train data component
		else { 							evtTrainData.addEventDataFromEventObj(_optObj);}		//augment existing event training data component
		//add list to obj map
		objMap.put(eid, evtTrainData);
		//add eventID object to 
		map.put(addDate, objMap);
	}//addDataToMap
	
	@Override
	//this is called after an individual example's features are built
	protected void _PostBuildFtrVec_Priv() {}		
	
	@Override
	//this is a mapping of non-zero source data elements to their idx in the underlying feature vector
	protected void buildAllNonZeroFtrIDXs() {
		allNonZeroFtrIDXs = new ArrayList<Integer>();
		for(Integer jp : allProdJPs) {
			Integer jpIDX = jpJpgMon.getFtrJpToIDX(jp);
			if(jpIDX==null) {msgObj.dispMessage("StraffSOMExample","buildAllNonZeroFtrIDXs","ERROR!  null value in jpJpgMon.getJpToFtrIDX("+jp+")", MsgCodes.error2 ); }
			else {allNonZeroFtrIDXs.add(jpIDX);}
		}
	}//buildAllNonZeroFtrIDXs (was buildAllJPFtrIDXsJPs)
	
	@Override
	//build a string describing what a particular feature value is
	protected String dispFtrVal(TreeMap<Integer, Float> ftrs, Integer i) {
		Float ftr = ftrs.get(i);
		int jp = jpJpgMon.getFtrJpByIdx(i);
		return "jp : " + jp + " | idx : " + i + " | val : " + String.format("%1.4g",  ftr) + " || ";
	}
	
	public final HashSet<Integer> getAllProdJPs(){return allProdJPs;}
	
	
}//class StraffSOMExample


/**
 * this class holds information from a single record.  It manages functionality to convert from the raw data to the format used to construct training examples
 * @author john
 *
 */
abstract class StraffTrainData{
	
	protected static final String jpgrpStTag = "JPG_St,", jpgrpEndTag = "JPG_End,";
	//magic value for opt key field in map, to use for non-opt records. 
	protected static final int FauxOptVal = 3;
	//array of jpg/jp records for this training data example
	protected ArrayList<JpgJpDataRecord> listOfJpgsJps;
	
	public StraffTrainData() {
		listOfJpgsJps = new ArrayList<JpgJpDataRecord>(); 
	}
	
	public abstract void addJPG_JPDataFromCSVString(String _csvDataStr);
	protected void addJPG_JPDataRecsFromCSVStr(Integer optKey, String _csvDataStr) {
		//boolean isOptEvent = ((-2 <= optKey) && (optKey <= 2));
		listOfJpgsJps = new ArrayList<JpgJpDataRecord>(); 	//order of recs is priority of jpgs
		String[] strAra1 = _csvDataStr.split("numJPGs,");//use idx 1
		String[] strAraVals = strAra1[1].trim().split(","+jpgrpStTag);//1st element will be # of JPDataRecs, next elements will be Data rec vals
		Integer numDataRecs = Integer.parseInt(strAraVals[0].trim());
		for (int i=0;i<numDataRecs;++i) {
			String csvString = strAraVals[i+1];
			String[] csvVals = csvString.split(",");
			//typeVal is specific type of record - opt value for opt records, source kind for source event records
			Integer typeVal = Integer.parseInt(csvVals[1]), JPG = Integer.parseInt(csvVals[3]);
			JpgJpDataRecord rec = new JpgJpDataRecord(JPG,i, typeVal, csvString);
			listOfJpgsJps.add(rec);			
		}
	}//addEventDataFromCSVStrByKey	
	
	//TODO can the following 2 be aggregated/combined?  perhaps the set of int tuples is sufficient instead of set of ints
	//return a hash set of all the jps in this raw training data example
	public HashSet<Integer> getAllJpsInData(){
		HashSet<Integer> res = new HashSet<Integer>();
		for (JpgJpDataRecord jpgRec : listOfJpgsJps) {
			ArrayList<Integer> jps = jpgRec.getJPlist();
			for(Integer jp : jps) {res.add(jp);}
		}
		return res;		
	}//getAllJpsInData
	
	//return a hash set of all tuples of jpg,jp relations in data
	public HashSet<Tuple<Integer,Integer>> getAllJpgJpsInData(){
		HashSet<Tuple<Integer,Integer>> res = new HashSet<Tuple<Integer,Integer>>();
		for (JpgJpDataRecord jpgRec : listOfJpgsJps) {
			Integer jpg = jpgRec.getJPG();
			ArrayList<Integer> jps = jpgRec.getJPlist();
			for(Integer jp : jps) {res.add(new Tuple<Integer,Integer>(jpg, jp));}
		}
		return res;		
	}//getAllJpgJpsInData	
	
	public abstract void addEventDataFromEventObj(BaseRawData ev);	
	protected void addEventDataRecsFromRawData(Integer optVal, BaseRawData ev, boolean isOptEvent) {
		//boolean isOptEvent = ((-2 <= optVal) && (optVal <= 2));
		TreeMap<Integer, ArrayList<Integer>> newEvMapOfJPAras = ev.rawJpMapOfArrays;//keyed by jpg, or -1 for jpg order array, value is ordered list of jps/jpgs (again for -1 key)
		if (newEvMapOfJPAras.size() == 0) {					//if returns an empty list from event raw data then either unspecified, which is bad record, or infers entire list of jp data
			if (isOptEvent){							//for opt events, empty jpg-jp array means apply specified opt val to all jps						
				//Adding an opt event with an empty JPgroup-keyed JP list - means apply opt value to all jps;
				//use jpg -10, jp -9 as sentinel value to denote executing an opt on all jpgs/jps - usually means opt out on everything, but other opts possible
				ArrayList<Integer>tmp=new ArrayList<Integer>(Arrays.asList(-9));
				newEvMapOfJPAras.put(-10, tmp);				
			} else {										//should never happen, means empty jp array of non-opt events (like order events) - should always have an order jpg/jp data.
				System.out.println("StraffTrainData::addEventDataRecsFromRaw : Warning : Attempting to add a non-Opt event (" + ev.TypeOfData + "-type) with an empty JPgroup-keyed JP list - event had no corresponding usable data so being ignored.");
				return;
			}
		}			
		//-1 means this array is the list, in order, of JPGs in this eventrawdata.  if there's no entry then that means there's only 1 jpg in this eventrawdata
		ArrayList<Integer> newJPGOrderArray = newEvMapOfJPAras.get(-1);
		if(newJPGOrderArray == null) {//adding only single jpg, without existing preference order
			newJPGOrderArray = new ArrayList<Integer> ();
			newJPGOrderArray.add(newEvMapOfJPAras.firstKey());//this adds only jpg to array	
		}
		JpgJpDataRecord rec;
		for (int i = 0; i < newJPGOrderArray.size(); ++i) {					//for every jpg key in ordered array
			int jpg = newJPGOrderArray.get(i);								//get list of jps for this jpg				
			rec = new JpgJpDataRecord(jpg,i,optVal);						//build a jpg->jp record for this jpg, passing order of jps under this jpg
			ArrayList<Integer> jpgs = newEvMapOfJPAras.get(jpg);			//get list of jps		
			for (int j=0;j<jpgs.size();++j) {rec.addToJPList(jpgs.get(j));}	//add each in order
			listOfJpgsJps.add(rec);				
		}			
	}//_buildListOfJpgJps
	
	//different event types will have different record formats to write/read
	protected abstract String getRecCSVString(JpgJpDataRecord rec);
	
	protected String buildJPGJP_CSVString() {
		String res = "";	
		for (int i=0;i<listOfJpgsJps.size();++i) {
//			JpgJpDataRecord rec = listOfJpgsJps.get(i);
//			res += "JPGJP_Start,optKey,"+rec.getOptVal()+","+rec.getCSVString()+"JPGJP_End,";			
			res += getRecCSVString(listOfJpgsJps.get(i));
		}		
		return res;		
	}//buildCSVString	
	
	public abstract String buildCSVString();
	
	@Override
	public String toString() {
		String res="";
		for (JpgJpDataRecord jpRec : listOfJpgsJps) { res += "\t" + jpRec.toString()+"\n";}
		return res;
 	}
}//class StraffTrainData

/**
 * this class corresponds to the data for a training/testing data point for a product.  It is built from relevant data from TC_Taggings
 * we can treat it like an event-based data point, but doesn't have any date so wont be added to any kind of jpoccurence structure.
 * Also, this is designed expecting that TC data will ever only have 1 JPGroup, with 1 or more, priority-ordered jps from that group.
 * @author john
 *
 */
class TcTagTrainData extends StraffTrainData{
	public TcTagTrainData(TcTagData ev) {
		super();
		addEventDataFromEventObj(ev);
	}	//put in child ctor in case child-event specific data needed for training	
	
	public TcTagTrainData(String _taggingCSVStr){
		super();
		addJPG_JPDataFromCSVString(_taggingCSVStr);	
	}//put in child ctor in case child-event specific data needed for training		}//ctor from rawDataObj
	
	//get map of jps and their order as specified in raw data.  NOTE : this is assuming there is only a single JPgroup represented by this TCTagData.  If there are >1 then this data will fail
	public TreeMap<Integer, Integer> getJPOrderMap(){
		int priority=0;
		TreeMap<Integer,Integer> orderMap = new TreeMap<Integer,Integer>();
		for(int i=0;i<listOfJpgsJps.size();++i) {
			JpgJpDataRecord jpRec = listOfJpgsJps.get(i);
			ArrayList<Integer> jpList = jpRec.getJPlist();
			for(int j=0;j<jpList.size(); ++j) {		orderMap.put(jpList.get(j), priority++);}
		}
		return orderMap;
	}//getJPOrderMap()	
	@Override
	protected String getRecCSVString(JpgJpDataRecord rec) {		return jpgrpStTag+"optKey,"+rec.getOptVal()+","+rec.getCSVString()+jpgrpEndTag;};	
	@Override
	public void addEventDataFromEventObj(BaseRawData ev) {	super.addEventDataRecsFromRawData(FauxOptVal,ev, false);}//addEventDataFromEventObj
	@Override
	public void addJPG_JPDataFromCSVString(String _csvDataStr) {super.addJPG_JPDataRecsFromCSVStr(FauxOptVal,_csvDataStr);	}//addJPG_JPDataFromCSVString

	@Override
	public String buildCSVString() {
		String res = "TCTagSt,numJPGs,1,";	//needs numJPGs tag for parsing - expected to always only have a single jp group
		res += buildJPGJP_CSVString();
		res += "TCTagEnd,";			
		return res;		
	}	
}//TcTagTrainData

/**
 * this class will hold event data for a single OID for a single date
 * multiple events might have occurred on a single date, this will aggregate all relevant event data of a particular type
 * @author john
 *
 */
abstract class StraffEvntTrainData extends StraffTrainData{
	//every unique eventID seems to occur only on 1 date
	protected Integer eventID;	
	protected Date eventDate;
	protected String eventType;	
	
	public StraffEvntTrainData(EventRawData ev) {
		super();
		eventID = ev.getEventID();//should be the same event type for event ID
		eventType = ev.getEventType();
		eventDate = ev.getDate();
	}//ctor from rawDataObj
	
	public StraffEvntTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr) {
		super();
		eventID = _evIDStr;//should be the same event type for event ID
		eventType = _evTypeStr;
		eventDate = BaseRawData.buildDateFromString(_evDateStr);		
	}//ctor from indiv data	via csv string

	//process JP occurence data for this event
	//passed is ref to map of all occurrences of jps in this kind of event's data for a specific prospect
	public void procJPOccForAllJps(ProspectExample ex, TreeMap<Integer, JP_OccurrenceData> jpOccMap, String type, boolean usesOpt, int[] _dbg_numOptAllOccs) {
		for (JpgJpDataRecord rec : listOfJpgsJps) {
			int opt = rec.getOptVal();
			Integer jpg = rec.getJPG();
			ArrayList<Integer> jps = rec.getJPlist();
			//this is sentinel value marker for opt events where all jpgs/jps are specified to have same opt value
			if((jpg == -10) && (jps.get(0) == -9)) {// opt choice covering all jpgs/jps
				++_dbg_numOptAllOccs[0];
				if  (opt <= 0) {//this is negative opt across all records 
					++_dbg_numOptAllOccs[1];
					//from here we are processing a positive opt record across all jps
					JP_OccurrenceData jpOcc = ex.getNegOptAllOccObj();
					if (jpOcc==null) {jpOcc = new JP_OccurrenceData(-9, -10, usesOpt);}
					jpOcc.addOccurrence(eventDate, rec.getOptVal());		
					ex.setNegOptAllOccObj(jpOcc);					
				} else {		//this is a non-negative opt across all records
					++_dbg_numOptAllOccs[2];
					//from here we are processing a positive opt record across all jps
					JP_OccurrenceData jpOcc = ex.getPosOptAllOccObj();
					if (jpOcc==null) {jpOcc = new JP_OccurrenceData(-9, -10, usesOpt);}
					jpOcc.addOccurrence(eventDate, rec.getOptVal());		
					ex.setPosOptAllOccObj(jpOcc);				
				}
			} else {
				for (Integer jp : jps) {
					JP_OccurrenceData jpOcc = jpOccMap.get(jp);
					if (jpOcc==null) {jpOcc = new JP_OccurrenceData(jp, jpg,usesOpt);}
					jpOcc.addOccurrence(eventDate, rec.getOptVal());		
					//add this occurence object to map at idx jp
					jpOccMap.put(jp, jpOcc);
				}
			}//if is opt-across-alljps event or not
		}//for all jpgjp record data	
	}//procJPOccForAllJps

	@Override
	public String buildCSVString() {
		String res = "EvSt,EvType,"+eventType+",EvID,"+eventID+",EvDt,"+ BaseRawData.buildStringFromDate(eventDate)+",numJPGs,"+listOfJpgsJps.size()+",";	
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

class OrderEventTrainData extends StraffEvntTrainData{
	public OrderEventTrainData(OrderEvent ev) {		super(ev);		addEventDataFromEventObj(ev);}	//put in child ctor in case child-event specific data needed for training		
	public OrderEventTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){super(_evIDStr, _evTypeStr, _evDateStr);addJPG_JPDataFromCSVString(_evntStr);	}//put in child ctor in case child-event specific data needed for training	
	@Override
	public void addEventDataFromEventObj(BaseRawData ev) {	super.addEventDataRecsFromRawData(FauxOptVal,ev, false);}//addEventDataFromEventObj
	@Override
	public void addJPG_JPDataFromCSVString(String _csvDataStr) {super.addJPG_JPDataRecsFromCSVStr(FauxOptVal,_csvDataStr);	}//addJPG_JPDataFromCSVString
	//get the output string holding the relevant info for an individual event record of this kind of data
	@Override
	protected String getRecCSVString(JpgJpDataRecord rec) {		return jpgrpStTag+"optKey,"+rec.getOptVal()+","+rec.getCSVString()+jpgrpEndTag;};
}//class OrderEventTrainData

class LinkEventTrainData extends StraffEvntTrainData{
	public LinkEventTrainData(LinkEvent ev) {	super(ev);		addEventDataFromEventObj(ev);}	//put in child ctor in case child-event specific data needed for training		
	public LinkEventTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){	super(_evIDStr, _evTypeStr, _evDateStr);addJPG_JPDataFromCSVString(_evntStr);}	//put in child ctor in case child-event specific data needed for training		
	@Override
	public void addEventDataFromEventObj(BaseRawData ev) {super.addEventDataRecsFromRawData(FauxOptVal,ev, false);}//addEventDataFromEventObj
	@Override
	public void addJPG_JPDataFromCSVString(String _csvDataStr) {	super.addJPG_JPDataRecsFromCSVStr(FauxOptVal,_csvDataStr);}//addJPG_JPDataFromCSVString
	//get the output string holding the relevant info for an individual event record of this kind of data
	@Override
	protected String getRecCSVString(JpgJpDataRecord rec) {		return jpgrpStTag+"optKey,"+rec.getOptVal()+","+rec.getCSVString()+jpgrpEndTag;};
}//class LinkEventTrainData

class OptEventTrainData extends StraffEvntTrainData{
	public OptEventTrainData(OptEvent ev) {		super(ev);		addEventDataFromEventObj(ev);}	//put in child ctor in case child-event specific data needed for training		
	public OptEventTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){super(_evIDStr, _evTypeStr, _evDateStr);		addJPG_JPDataFromCSVString(_evntStr);}	//put in child ctor in case child-event specific data needed for training	
	@Override
	public void addEventDataFromEventObj(BaseRawData ev) {
		Integer optValKey = ((OptEvent)ev).getOptType();
		super.addEventDataRecsFromRawData(optValKey, ev, true);
	}
	//opt value in csv record between tags optKeyTag and JPG tag
	private Integer getOptValFromCSVStr(String _csvDataStr) {
		Integer res=  0;
		String[] resAra = _csvDataStr.trim().split("optKey,");
		String[] resAra2 = resAra[1].trim().split(",JPG");		
		res = Integer.parseInt(resAra2[0]);
		return res;
	}//getOptValFromCSVStr	
	@Override
	public void addJPG_JPDataFromCSVString(String _csvDataStr) {
		Integer optValKey = getOptValFromCSVStr(_csvDataStr);
		super.addJPG_JPDataRecsFromCSVStr(optValKey, _csvDataStr);
	}
	//get the output string holding the relevant info for an individual event record of this kind of data
	@Override
	protected String getRecCSVString(JpgJpDataRecord rec) {		return jpgrpStTag+"optKey,"+rec.getOptVal()+","+rec.getCSVString()+jpgrpEndTag;};
}//class OptEventTrainData


class SrcEventTrainData extends StraffEvntTrainData{
	public SrcEventTrainData(SourceEvent ev) {	super(ev);		addEventDataFromEventObj(ev);}	//put in child ctor in case child-event specific data needed for training
	
	public SrcEventTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){	super(_evIDStr, _evTypeStr, _evDateStr);addJPG_JPDataFromCSVString(_evntStr);}	//put in child ctor in case child-event specific data needed for training			
	@Override
	public void addEventDataFromEventObj(BaseRawData ev) {
		Integer srcValKey = ((SourceEvent)ev).getSourceType();
		super.addEventDataRecsFromRawData(srcValKey, ev, false);
	}
	//source type value in csv record between tags srcType and JPG tag
	private Integer getSrcValFromCSVStr(String _csvDataStr) {
		Integer res=  0;
		String[] resAra = _csvDataStr.trim().split("srcType,");
		String[] resAra2 = resAra[1].trim().split(",JPG");		
		res = Integer.parseInt(resAra2[0]);
		return res;
	}//getOptValFromCSVStr	
	@Override
	public void addJPG_JPDataFromCSVString(String _csvDataStr) {
		Integer srcValKey = getSrcValFromCSVStr(_csvDataStr);
		super.addJPG_JPDataRecsFromCSVStr(srcValKey, _csvDataStr);
	}
	//get the output string holding the relevant info for an individual event record of this kind of data
	@Override
	protected String getRecCSVString(JpgJpDataRecord rec) {		return jpgrpStTag+"srcType,"+rec.getOptVal()+","+rec.getCSVString()+jpgrpEndTag;};
}//class SrcEventTrainData


/////////////////////////////////////////////////////////////////////////////////
/**
 * class holds pertinent info for a job practice group/job practice record 
 * relating to 1 job practice group and 1 or more job practice ids for a single prospect
 * This structure corresponds to how a single prospects or event record is stored in the db
 * @author john
 *
 */
class JpgJpDataRecord {
	//priority here means order of jpg in list from source data
	private int ordrPriority;
	//typeVal is specific type of record - opt value for opt records  (in range -2->2 currently), source kind for source event records
	private int optVal;
	//for jpg, ordrPriority is the order of this JPG in source data
	private int JPG;
	//preserves jp hierarchy for data record
	private ArrayList<Integer> JPlist;	

	public JpgJpDataRecord(int _jpg, int _priority, int _optVal) {
		JPG = _jpg;
		ordrPriority = _priority;
		optVal = _optVal;
		JPlist = new ArrayList<Integer>();
	}//ctor w/actual data
	
	//_csvString should be from JPst to JPend
	public JpgJpDataRecord(int _jpg, int _priority, int _optVal, String _csvString) {
		this(_jpg,  _priority,  _optVal);
		String[] tmpStr = _csvString.trim().split(",JPst,"),
				tmpStr1 = tmpStr[1].trim().split(",JPend,"), 
				jpDataAra=tmpStr1[0].trim().split(",");
		for (int i=0;i<jpDataAra.length;++i) {	addToJPList(Integer.parseInt(jpDataAra[i]));}		
	}//ctor from csv string
	
	public void addToJPList(int val) {JPlist.add(val);}//addToJPList
	
	//return a map of jp and order
	public TreeMap<Integer, Integer> getJPOrderMap(){
		if (JPlist.size()==0) {return null;}
		TreeMap<Integer, Integer> res = new TreeMap<Integer, Integer>();
		for(int i=0;i<JPlist.size();++i) {res.put(JPlist.get(i), i);}		
		return res;
	}
	
	public String getCSVString() {
		if (JPlist.size() == 0){return "None,";}		
		String res = "JPG," +JPG+",JPst,";
		int szm1 = JPlist.size()-1;
		for (int i=0; i<szm1;++i) {res += JPlist.get(i)+",";}
		res += JPlist.get(szm1)+",JPend,";
		return res;
	}//getCSVString

	//for possible future json jackson serialization (?)
	public int getPriority() {	return ordrPriority;}
	public int getJPG() {		return JPG;	}
	public ArrayList<Integer> getJPlist() {return JPlist;}
	public int getOptVal() {return optVal;	}

	public void setOptVal(int _o) {optVal = _o;}
	public void setJPG(int jPG) {JPG = jPG;}
	public void setJPlist(ArrayList<Integer> jPlist) {JPlist = jPlist;}
	public void setPriority(int _p) {	ordrPriority = _p;}	

	@Override
	public String toString() {
		String res = "JPG:"+JPG+" |Priority:"+ordrPriority+" |opt:"+optVal+" |JPList: [";
		int szm1 = JPlist.size()-1;
		for (int i=0; i<szm1;++i) {res += JPlist.get(i)+",";}
		res += JPlist.get(szm1)+"]";
		return res;
	}
}//JPDataRecord
