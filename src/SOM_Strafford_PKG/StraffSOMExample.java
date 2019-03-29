package SOM_Strafford_PKG;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;


/**
 * NOTE : None of the data types in this file are thread safe so do not allow for opportunities for concurrent modification
 * of any instanced object in this file. This decision was made for speed concerns - 
 * concurrency-protected objects have high overhead that proved greater than any gains in 10 execution threads.
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
	//all jps seen in all occurrence structures - NOT IDX IN FEATURE VECTOR!
	protected HashSet<Integer> allJPs;

	//idxs corresponding to types of events
	
	public StraffSOMExample(StraffSOMMapManager _map, ExDataType _type, String _id) {
		super(_map, _type, _id);
		jpJpgMon = mapMgr.jpJpgrpMon;
		allJPs = new HashSet<Integer> ();		
	}//ctor
	
	public StraffSOMExample(StraffSOMExample _otr) {
		super(_otr);
		jpJpgMon = mapMgr.jpJpgrpMon;
		allJPs = _otr.allJPs;		
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
		case Source : { 	return new SrcEventTrainData((SourceEvent) _evntObj);}//link event
		default : {			return new OrderEventTrainData((OrderEvent) _evntObj);	}//default to order event object - probably will fail.  need to make sure type is accurately specified.
		}//switch
	}//buildNewTrainData
	
	//eventtype : 0 : order, 1 : opt, 2 : link
	protected StraffEvntTrainData buildNewTrainDataFromStr(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr, EvtDataType eventtype) {
		switch (eventtype) {
		case Order 	: { 	return new OrderEventTrainData(_evIDStr, _evTypeStr, _evDateStr, _evntStr);	}//order event object
		case Opt 	: {		return new OptEventTrainData(_evIDStr, _evTypeStr, _evDateStr, _evntStr);}//opt event  object
		case Link 	: {		return new LinkEventTrainData(_evIDStr, _evTypeStr, _evDateStr, _evntStr);}//opt event  object
		case Source : {		return new SrcEventTrainData(_evIDStr, _evTypeStr, _evDateStr, _evntStr);}//opt event  object
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
	
	//return a hash set of the jps represented by non-zero values in this object's feature vector.
	//needs to be governed by some threshold value
	protected HashSet<Integer> buildJPsFromFtrAra(float[] _ftrAra, float thresh){
		HashSet<Integer> jps = new HashSet<Integer>();
		for (int i=0;i<_ftrAra.length;++i) {
			Float ftr = ftrMaps[ftrMapTypeKey].get(i);
			if ((ftr!= null) && (ftr > thresh)) {jps.add(jpJpgMon.getJpByIdx(i));			}
		}
		return jps;	
	}//buildJPsFromFtrVec
	@Override
	//this is called after an individual example's features are built
	protected void _PostBuildFtrVec_Priv() {}		
	
	@Override
	//this is a mapping of non-zero source data elements to their idx in the underlying feature vector
	protected void buildAllNonZeroFtrIDXs() {
		allNonZeroFtrIDXs = new ArrayList<Integer>();
		for(Integer jp : allJPs) {
			Integer jpIDX = jpJpgMon.getJpToFtrIDX(jp);
			if(jpIDX==null) {mapMgr.dispMessage("StraffSOMExample","buildAllNonZeroFtrIDXs","ERROR!  null value in jpJpgMon.getJpToFtrIDX("+jp+")", MsgCodes.error2 ); }
			else {allNonZeroFtrIDXs.add(jpJpgMon.getJpToFtrIDX(jp));}
		}

	}//buildAllNonZeroFtrIDXs (was buildAllJPFtrIDXsJPs)
	
	@Override
	//build a string describing what a particular feature value is
	protected String dispFtrVal(TreeMap<Integer, Float> ftrs, Integer i) {
		Float ftr = ftrs.get(i);
		int jp = jpJpgMon.getJpByIdx(i);
		return "jp : " + jp + " | idx : " + i + " | val : " + String.format("%1.4g",  ftr) + " || ";
	}
	
}//class StraffSOMExample

/**
 * class to manage a prospect example, either a past customer or a potential prospect
 * @author john
 *
 */

abstract class prospectExample extends StraffSOMExample{
	//prospect last lookup date, if any specified
	public Date prs_LUDate;
	//structs to hold all event occurences   of each JP for this OID
	protected TreeMap<String, TreeMap<Integer, jpOccurrenceData>> JpOccurrences;
	//may have multiple events on same date/time, map by event ID 	
	protected TreeMap<String, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>>> eventsByDateMap;	
	
	//this object denotes a positive or non-positive opt-all event for a user (i.e. an opts occurrence with a jp of -9)
	private jpOccurrenceData posOptAllEventObj = null, negOptAllEventObj = null;

	//build this object based on prospectData object from raw data
	public prospectExample(StraffSOMMapManager _map, ExDataType _type, prospectData _prspctData) {
		super(_map,_type,_prspctData.OID);	
		initObjsData();
		prs_LUDate = _prspctData.getDate();
	}//prospectData ctor
	
	//build this object based on csv string - rebuild data from csv string columns 4+
	public prospectExample(StraffSOMMapManager _map, ExDataType _type, String _OID, String _csvDataStr) {
		super(_map,_type,_OID);		
		initObjsData();	
		String[] dataAra = _csvDataStr.split(",");
		//idx 0 : OID; idx 1 : date
		prs_LUDate = BaseRawData.buildDateFromString(dataAra[1]);
		//get # of events - need to accommodate source events
		int[] numEvsAra = _getCSVNumEvsAra(dataAra);
		//Build data here from csv strint
		buildDataFromCSVString(numEvsAra, _csvDataStr);		
	}//csv string ctor
		
	//copy ctor
	public prospectExample(prospectExample _otr) {
		super(_otr);
		prs_LUDate = _otr.prs_LUDate;
		JpOccurrences = _otr.JpOccurrences;
		eventsByDateMap = _otr.eventsByDateMap;
		posOptAllEventObj = _otr.posOptAllEventObj;
		negOptAllEventObj = _otr.negOptAllEventObj;
	}//copy ctor	
	
	//return configuration of expected counts of different events as stored in this example
	protected abstract int[] _getCSVNumEvsAra(String[] dataAra);
	
	//instancing class needs to have this
	protected void initObjsData() {
		rad = 3.0f;			//for display
		//occurrence structures - keyed by type, then by JP
		JpOccurrences = new TreeMap<String, TreeMap<Integer, jpOccurrenceData>> ();
		eventsByDateMap = new TreeMap<String, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>>>();
		_initObjsIndiv();
	}//initObjsData
	//build data from csv record
	protected final void buildEventTrainDataFromCSVStr(String evntType, int numEvents, String allEventsStr, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> mapToAdd, EvtDataType type) {
		//String evntType = eventMapTypeKeys[type.getVal()];
		//int numEventsToShow = 0; boolean dbgOutput = false;
		String [] allEventsStrAra = allEventsStr.trim().split("EvSt,");
		//will have an extra entry holding OPT key
		if(allEventsStrAra.length != (numEvents+1)) {//means not same number of event listings in csv as there are events counted in original event list - shouldn't be possible
			System.out.println("buildEventTrainDataFromCsvStr : Error building train data from csv file : " + evntType + " Event : "+allEventsStr + " string does not have expected # of events : " +allEventsStrAra.length + " vs. expected :" +numEvents );
			return;
		}
//		if ((type == EvtDataType.Order) &&(numEvents > numEventsToShow) && dbgOutput) {
//			System.out.println("type : " +evntType  +" | AllEventsStr : " + allEventsStr );
//		}
		for (String eventStr : allEventsStrAra) {
			if (eventStr.length() == 0 ) {continue;}
			String[] eventStrAra = eventStr.trim().split(",");
			String evType = eventStrAra[1];
			Integer evID = Integer.parseInt(eventStrAra[3]);
			String evDateStr = eventStrAra[5];
			//need to print out string to make sure that there is only a single instance of every event id in each record in csv files
			StraffEvntTrainData newEv = buildNewTrainDataFromStr(evID, evType, evDateStr, eventStr, type);
//			if ((type == EvtDataType.Opt) &&(numEvents > numEventsToShow) && dbgOutput) {
//				System.out.println("\tEvent : " + newEv.toString());
//			}
			Date addDate = newEv.getEventDate();			
			
			TreeMap<Integer, StraffEvntTrainData> eventsOnDate = mapToAdd.get(addDate);
			if(null == eventsOnDate) {eventsOnDate = new TreeMap<Integer, StraffEvntTrainData>();}
			StraffEvntTrainData tmpEvTrainData = eventsOnDate.get(evID);
			if(null != tmpEvTrainData) {System.out.println("Possible issue : event being written over : old : "+ tmpEvTrainData.toString() + "\n\t replaced by new : " + newEv.toString());}
			
			eventsOnDate.put(evID, newEv);
			mapToAdd.put(addDate, eventsOnDate);
		}		
	}//buildEventTrainDataFromCsvStr
	
	@Override
	//standardize this feature vector
	protected void buildStdFtrsMap() {		
		if (allNonZeroFtrIDXs.size() > 0) {ftrMaps[stdFtrMapTypeKey] = calcStdFtrVector(ftrMaps[ftrMapTypeKey], allNonZeroFtrIDXs);}
		else {ftrMaps[stdFtrMapTypeKey] = new TreeMap<Integer, Float>();}
		setFlag(stdFtrsBuiltIDX,true);
	}//buildStdFtrsMap
	
	//build occurence structure for type of events in this data, including aggregating build-later opt event data
	private void _buildIndivOccStructs(String key, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> mapByDate, boolean usesOpt ) {
		TreeMap<Integer, jpOccurrenceData> occs = new TreeMap<Integer, jpOccurrenceData>();
		for (TreeMap<Integer, StraffEvntTrainData> map : mapByDate.values()) {
			for (StraffEvntTrainData ev : map.values()) {ev.procJPOccForAllJps(this, occs, key,usesOpt);}			
		}
		JpOccurrences.put(key, occs);
	}//_buildIndivOccStructs
	//build occurence structures based on mappings - must be called once mappings are completed but before the features are built
	//feature vec is built from occurrence structure
	protected void buildOccurrenceStructs(String[] eventMapTypeKeys, boolean[] eventMapUseOccData) { // should be executed when finished building all xxxEventsByDateMap(s)
		//occurrence structures - map keyed by event type of map of 
		JpOccurrences = new TreeMap<String, TreeMap<Integer, jpOccurrenceData>> ();
		//for orders and opts, pivot structure to build map holding occurrence records keyed by jp - must be done after all events are aggregated for each prospect
		//for every date, for every event, aggregate occurences		
		for(int i=0;i<eventMapTypeKeys.length;++i) {
			String key = eventMapTypeKeys[i];
			_buildIndivOccStructs(key, eventsByDateMap.get(key), eventMapUseOccData[i]);	
		}
		//for (String key : eventMapTypeKeys) {_buildIndivOccStructs(key, eventsByDateMap.get(key));			}		
	}//buildOccurrenceStructs
	
	//provide shallow copy of jpOcc struct - only copying passed event type key values
	protected TreeMap<String, TreeMap<Integer, jpOccurrenceData>> copyJPOccStruct(String[] eventMapTypeKeys) {
		TreeMap<String, TreeMap<Integer, jpOccurrenceData>> tmpJpOccurrences = new TreeMap<String, TreeMap<Integer, jpOccurrenceData>> ();
		for(String key : eventMapTypeKeys) {		tmpJpOccurrences.put(key, JpOccurrences.get(key)); 	}
		return tmpJpOccurrences;
	}//copyJPOccStruct
	
	//provide shallow copy of jpOcc struct - only copying passed event type key values
	protected TreeMap<String, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>>> copyEventsByDateMap(String[] eventMapTypeKeys) {
		TreeMap<String, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>>> tmpEventsByDateMap = new TreeMap<String, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>>> ();
		for(String key : eventMapTypeKeys) {		tmpEventsByDateMap.put(key, eventsByDateMap.get(key)); 	}
		return tmpEventsByDateMap;
	}//copyJPOccStruct
	
	
	protected String buildEventCSVString(Date date, TreeMap<Integer, StraffEvntTrainData> submap) {		
		String res = "";
		for (StraffEvntTrainData eventObj : submap.values()) {	res += eventObj.buildCSVString();}		
		return res;
	}//buildOrderCSVString
	
	///////////////////////////////////
	// getters/setters	
	
	//return # of values in data map
	protected int getSizeOfDataMap(TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> map) {
		int res = 0;
		for (Date date : map.keySet()) {for (Integer eid :  map.get(date).keySet()) {res+=1;}	}	
		return res;
	}
	protected abstract String[] getEventMapTypeKeys();
	protected abstract String[] getCSVSentinelLbls();
	//required info for this example to build feature data - use this so we don't have to reload data ever time 
	//this will build a single record (row) for each OID (prospect)
	@Override
	public final String getRawDescrForCSV() {
		//first build prospect data
		String dateStr = BaseRawData.buildStringFromDate(prs_LUDate);
		String res = ""+OID+","+dateStr+",";
		String[] eventMapTypeKeys = getEventMapTypeKeys();
		for (String key : eventMapTypeKeys) {
			res += getSizeOfDataMap(eventsByDateMap.get(key))+",";
		}
		//res += getSizeOfDataMap(orderEventsByDateMap)+"," + getSizeOfDataMap(optEventsByDateMap)+",";
		//now build res string for all event data objects
		String[] CSVSentinelLbls = getCSVSentinelLbls();
		for(int i=0;i<eventMapTypeKeys.length;++i) {
			String key = eventMapTypeKeys[i];
			res += CSVSentinelLbls[i];
			TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> eventsByDate = eventsByDateMap.get(key);
			for (Date date : eventsByDate.keySet()) {			res += buildEventCSVString(date, eventsByDate.get(date));		}				
		}		
		return res;
	}//getRawDescrForCSV()
	
	//return all jpg/jps in this prospect record 
	protected HashSet<Tuple<Integer,Integer>> getSetOfAllJpgJpData(){
		HashSet<Tuple<Integer,Integer>> res = new HashSet<Tuple<Integer,Integer>>();
		for(TreeMap<Integer, jpOccurrenceData> occs : JpOccurrences.values()) {
			for (jpOccurrenceData occ :occs.values()) {res.add(occ.getJpgJp());}	
		}
		return res;
	}///getSetOfAllJpgJpData
	
	//return occurence map
	public TreeMap<Integer, jpOccurrenceData> getOcccurenceMap(String key) {
		TreeMap<Integer, jpOccurrenceData> res = JpOccurrences.get(key);
		if (res==null) {mapMgr.dispMessage("ProspectExample","getOcccurenceMap","JpOccurrences map does not have key : " + key, MsgCodes.warning2); return null;}
		return res;
	}
	
	//remove occurences with passed jp
	public void removeAllJPOccs(Integer jp) {
		for(TreeMap<Integer, jpOccurrenceData> jpOccType : JpOccurrences.values()) {
			jpOccType.put(jp, null);
		}
	}//removeAllJPOccs
	
    //these set/get this StraffSOMExample's object that denotes a positive or non-positive opt setting for all jps
	public jpOccurrenceData getPosOptAllOccObj() {		return posOptAllEventObj;	}
	public void setPosOptAllOccObj(jpOccurrenceData _optAllOcc) {posOptAllEventObj = _optAllOcc;	}
		
	public jpOccurrenceData getNegOptAllOccObj() {		return negOptAllEventObj;	}
	public void setNegOptAllOccObj(jpOccurrenceData _optAllOcc) {negOptAllEventObj = _optAllOcc;	}

	
	//whether this record has any information to be used to train - presence of prospect jpg/jp can't be counted on
	//isBadExample means resulting ftr data is all 0's for this example.  can't learn from this, so no need to keep it.
	public final boolean hasNonSourceEvents() {return !getFlag(isBadTrainExIDX) && (hasRelelventTrainingEvents());}		
	//whether this record should be used as validation record - if it has no past order events, but does have source data 
	
	public final boolean isTrueProspect() {	
		return  ((JpOccurrences.get("orders").size() == 0) && (JpOccurrences.get("sources").size() > 0));
	}//isTrueProspect()	
	
	//whether this record has any source events specified - if no other events then this record would be a legitimate validation record (a true prospect)
	public final boolean hasOnlySourceEvents() {	return ((!hasRelelventTrainingEvents()) && (JpOccurrences.get("sources").size() > 0));}

	//instancing class specific new object initialization
	protected abstract void _initObjsIndiv();

	//this prospect is an actual customer - use as training data
	public abstract boolean isTrainablePastCustomer();
	//get status of a particular jp
	public abstract boolean[] hasJP(Integer _jp);
	
	public abstract boolean[] getExampleStatusOcc();
	public abstract boolean[] getExampleStatusEvt();
	
	//whether this record was used to train the current map or not, and where it exists within the test or train array (idx)
	public abstract boolean getIsTrainingData();
	public abstract int getTestTrainIDX();	

	//returns true if -any training-related- events are present in this record (i.e. not counting source "events")
	protected abstract boolean hasRelelventTrainingEvents();

	//read in data from record
	protected abstract void buildDataFromCSVString(int[] numEvntsAra, String _csvDataStr);
	
	//build an arraylist of all jps in this example
	protected final HashSet<Integer> buildJPListFromOccs(){
		HashSet<Integer> jps = new HashSet<Integer>();
		for(TreeMap<Integer, jpOccurrenceData> occs : JpOccurrences.values()) {
			for (Integer jp :occs.keySet()) {
				if (jp == -9) {continue;}//ignore -9 jp occ map - this is an entry to denote a positive opt - shouldn't be here anymore but just in case
				jps.add(jp);
			}	
		}
		return jps;
	}//buildJPListFromOccs
	

	protected String toStringDateMap(TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> map, String mapName) {
		String res = "\n# of " + mapName + " in Date Map (count of unique dates - multiple " + mapName + " per same date possible) : "+ map.size()+"\n";		
		for (Date dat : map.keySet() ) {
			res+="Date : " + dat+"\n";
			TreeMap<Integer, StraffEvntTrainData> evMap = map.get(dat);
			for (StraffEvntTrainData ev : evMap.values()) {	res += ev.toString();	}
		}		
		return res;
	}//toStringDateMap	
	
	protected String toStringOptOccMap(TreeMap<Integer, jpOccurrenceData> map, String mapName) {
		String res = "\n# of jp occurences in " + mapName + " : "+ map.size()+"\n";	
		for (Integer jp : map.keySet() ) {
			jpOccurrenceData occ = map.get(jp);
			res += occ.toString();			
		}	
		return res;		
	}//toStringOptOccMap

		
}//class prospectExample

/**
 * 	this class holds a customer prospect example, that will either be used to 
 * generate training data for the SOM, or else will only be queried against the SOM 
 * raw prospects default to custProspectExample, and are converted to 
 * trueProspectExample if they don't qualify as training data
 * @author john
 *
 */
class custProspectExample extends prospectExample{
	//column names for csv of this SOM example
	private static final String csvColDescrPrfx = "OID,Prospect_LU_Date,Num Order Event Dates,Num Opt Event Dates,Num Link Event Dates,Num Src Event Dates";
	
	//////////////////////////////////////
	//Training data description
	//////////////////////////////////////
	//all kinds of events present
	public static final String[] eventMapTypeKeys = new String[] {"orders", "opts", "links", "sources"};
	//events allowable for training (source info can be used if jps present in other events as well, but should be ignored otherwise)
	private static final String[] trainingEventMapTypeKeys = new String[] {"orders", "opts", "links"};//events used only to determine training
	//event mappings use the occurrence data value (opt/source)
	private static final boolean[] eventMapUseOccData = new boolean[] {false,true,false,true};
	//csv labels
	private static final String[] CSVSentinelLbls = new String[] {"ORD|,","OPT|,", "LNK|,", "SRC|," };	
	//is this datapoint used for training; whether this record has a source "event" attached to it
	private boolean isTrainingData;
	//this is index for this data point in training/testing data array; original index in preshuffled array (reflecting build order)
	private int testTrainDataIDX;
	
	//build this object based on prospectData object from raw data
	public custProspectExample(StraffSOMMapManager _map,prospectData _prspctData) {	super(_map,ExDataType.customerTraining, _prspctData);}//prospectData ctor	
	//build this object based on csv string - rebuild data from csv string columns 4+
	public custProspectExample(StraffSOMMapManager _map,String _OID, String _csvDataStr) {super(_map,ExDataType.customerTraining, _OID,_csvDataStr);	}//csv string ctor
	
	public custProspectExample(prospectExample ex) {
		super(ex);
		//TODO : if ex is this type then don't have to rebuild JpOccurrences and eventsByDateMap;	
	}//copy ctor
	
	@Override
	protected void buildDataFromCSVString(int[] numEvntsAra, String _csvDataStr) {
		//each type of event list exists between the sentinel flag and the subsequent sentinel flag
		for (int i = 0; i<numEvntsAra.length;++i) {
			if (numEvntsAra[i] > 0) {
				String key = eventMapTypeKeys[i];
				String stSentFlag = CSVSentinelLbls[i];
				String endSntnlFlag = (i <eventMapTypeKeys.length-1 ? CSVSentinelLbls[i+1] : null );
				String [] strAraBegin = _csvDataStr.trim().split(Pattern.quote(stSentFlag)); //idx 1 holds all event data	
				String strBegin = (strAraBegin.length < 2) ? "" : strAraBegin[1];
				String strEvents = (endSntnlFlag != null ? strBegin.trim().split(Pattern.quote(endSntnlFlag))[0] : strBegin).trim(); //idx 0 holds relevant data
				buildEventTrainDataFromCSVStr(key, numEvntsAra[i],strEvents,eventsByDateMap.get(key), EvtDataType.getVal(i));				
			}			
		}
	}//buildDataFromCSVString	
	
	//this is customer-specific 
	@Override
	protected void _initObjsIndiv() {
		for (String key : eventMapTypeKeys) {eventsByDateMap.put(key, new TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> ());	}
		//set these when this data is partitioned into testing and training data
		isTrainingData = false;
		testTrainDataIDX = -1;
	}//initObjsData


	//any processing that must occur once all constituent data records are added to this example - must be called externally, before ftr vec is built
	@Override
	public void finalizeBuild() {
		buildOccurrenceStructs(eventMapTypeKeys, eventMapUseOccData);	
		//all jps holds all jps in this example based on occurences; will not reference jps implied by opt-all records
		allJPs = buildJPListFromOccs();
		if(allJPs.size() == 0) {setIsBadExample(true);		}//means there's no valid jps in this record's occurence data
	}//finalize
	
	//return boolean array describing this example's occurence structure
	//idxs : 0==if in an order; 1==if in an opt, 2 == if is in link, 3==if in source event,4 if in any non-source event (denotes action by customer), 5 if in any event, including source, 
	//needs to follow format of eventMapTypeKeys
	@Override
	public boolean[] getExampleStatusOcc() {
		boolean	has_ordr = (JpOccurrences.get("orders").size() !=0) ,
				has_opt = (JpOccurrences.get("opts").size() !=0),				//seems to not correspond to size of eventsByDateMap.get("opts").size()
				has_link = (JpOccurrences.get("links").size() !=0),	
				has_source = (JpOccurrences.get("sources").size() !=0),			//do not treat source data as an event in this case
				has_event = has_ordr || has_opt || has_link;					//not counting source data
		boolean[] res = new boolean[] {has_ordr, has_opt, has_link, has_source,has_event};
		return res;		
	}//

	//return boolean array describing this example's eventsByDate structure -should match occurence structure
	//idxs : 0==if in an order; 1==if in an opt, 2 == if is in link, 3==if in source event,4 if in any non-source event (denotes action by customer), 5 if in any event, including source, 
	//needs to follow format of eventMapTypeKeys
	@Override
	public boolean[] getExampleStatusEvt() {
		boolean	has_ordr = (eventsByDateMap.get("orders").size() !=0) ,
				has_opt = (eventsByDateMap.get("opts").size() !=0),
				has_link = (eventsByDateMap.get("links").size() !=0),	
				has_source = (eventsByDateMap.get("sources").size() !=0),			//do not treat source data as an event in this case
				has_event = has_ordr || has_opt || has_link;					//not counting source data
		boolean[] res = new boolean[] {has_ordr, has_opt, has_link, has_source,has_event};
		return res;		
	}//check if JP exists, and where

	//check whether this prospect has a particular jp in either his prospect data, events or opts
	//idxs : 0==if in an order; 1==if in an opt, 2 == if is in link, 3==if in source event,4 if in any non-source event (denotes action by customer), 5 if in any event, including source, 
	//it is assumed that hasJP_event is after all event booleans (if future bools are added)
	//needs to follow format of eventMapTypeKeys
	@Override
	public boolean[] hasJP(Integer _jp) {
		boolean	hasJP_ordr = (JpOccurrences.get("orders").get(_jp) != null) ,
				hasJP_opt = (JpOccurrences.get("opts").get(_jp) != null),
				hasJP_link = (JpOccurrences.get("links").get(_jp) != null),	
				hasJP_source = (JpOccurrences.get("sources").get(_jp) != null),			//do not treat source data as an event in this case
				hasJP_event = hasJP_ordr || hasJP_opt || hasJP_link,					//not counting source data
				hasJP = hasJP_event || hasJP_source;
		boolean[] res = new boolean[] {hasJP_ordr, hasJP_opt, hasJP_link, hasJP_source, hasJP_event, hasJP};
		return res;		
	}//check if JP exists, and where
	
	//add event data to this customer prospect
	public void addEventObj(BaseRawData obj, int type) {
		switch(type) {
		case StraffSOMMapManager.prspctIDX 	: 	{mapMgr.dispMessage("custProspectExample","addObj","ERROR attempting to add prospect raw data as event data. Ignored", MsgCodes.error2);return;}
		case StraffSOMMapManager.orderEvntIDX : 	{		addDataToTrainMap((OrderEvent)obj,eventsByDateMap.get(eventMapTypeKeys[0]), EvtDataType.Order); 		return;}
		case StraffSOMMapManager.optEvntIDX 	: 	{		addDataToTrainMap((OptEvent)obj,eventsByDateMap.get(eventMapTypeKeys[1]), EvtDataType.Opt); 		return;}
		case StraffSOMMapManager.linkEvntIDX 	: 	{		addDataToTrainMap((LinkEvent)obj,eventsByDateMap.get(eventMapTypeKeys[2]), EvtDataType.Link); 		return;}
		case StraffSOMMapManager.srcEvntIDX 	: 	{		addDataToTrainMap((SourceEvent)obj,eventsByDateMap.get(eventMapTypeKeys[3]), EvtDataType.Source); 		return;}
		default :{mapMgr.dispMessage("custProspectExample","addObj","ERROR attempting to add unknown raw data type : " + type + " as event data. Ignored", MsgCodes.error2);return;}
		}		
	}//addObj
	
	public void setIsTrainingDataIDX(boolean val, int idx) {
		isTrainingData=val; 
		testTrainDataIDX=idx;
		type= isTrainingData ? ExDataType.customerTraining : ExDataType.customerTesting;
		nodeClrs = mapMgr.getClrVal(type);
	}//setIsTrainingDataIDX
	//
	//whether this record was used to train the current map or not
	@Override
	public boolean getIsTrainingData() {return isTrainingData;}
	@Override
	public int getTestTrainIDX() {return testTrainDataIDX;}
	//column names for raw descriptorCSV output
	@Override
	public String getRawDescColNamesForCSV(){	return csvColDescrPrfx + ",";	}
	@Override
	//get #'s of events from partitioned csv data held in dataAra - need to accommodate source events - idxs depend on how the data was originally built
	protected int[] _getCSVNumEvsAra(String[] dataAra) {return new int[] {Integer.parseInt(dataAra[2]),Integer.parseInt(dataAra[3]),Integer.parseInt(dataAra[4]),Integer.parseInt(dataAra[5])};}
	@Override
	protected String[] getEventMapTypeKeys() {	return eventMapTypeKeys;}
	@Override
	protected String[] getCSVSentinelLbls() {return CSVSentinelLbls;}
	//this prospect is an actual customer - use as training data - not all custPropsects will have this true, since this is the initial class that is used to build the data
	@Override
	public boolean isTrainablePastCustomer() { return !getFlag(isBadTrainExIDX) && (JpOccurrences.get("orders").size() > 0) ;}
	
	//returns true if -any training-related- events are present in this record (i.e. not counting source "events")
	@Override
	protected boolean hasRelelventTrainingEvents() {
		boolean res = false;
		for (String key : trainingEventMapTypeKeys) {if (eventsByDateMap.get(key).size() > 0) {return true;}	}
		return res;
	}//hasRelelventEvents	
    //take loaded data and convert to feature data via calc object
	@Override
	protected void buildFeaturesMap() {
		//access calc object
		if (allJPs.size() > 0) {
			ftrMaps[ftrMapTypeKey] = mapMgr.ftrCalcObj.calcFeatureVector(this,allJPs,JpOccurrences.get("orders"), JpOccurrences.get("links"), JpOccurrences.get("opts"), JpOccurrences.get("sources"));
		}
		else {ftrMaps[ftrMapTypeKey] = new TreeMap<Integer, Float>();}
		//now, if there's a non-null posOptAllEventObj then for all jps who haven't gotten an opt conribution to calculation, add positive opt-all result
	}//buildFeaturesMap

	@Override
	public String toString() {	
		String res = "Customer : " + super.toString();
		for (String key : eventMapTypeKeys) {
			res += toStringDateMap(eventsByDateMap.get(key), key);
			res += toStringOptOccMap(JpOccurrences.get(key), key + " occurences");
		}
		return res;
	}


}//class prospectExample


/**
 * This class will hold a reduced prospect that is defined as a true prospect - most likely defined as a prospect without any actual orders
 * won't be used for training, but will be used for validation.  There are going to be many of these - many more than customer (base) prospects,
 * and they have much less overhead, hence they have a separate class with some overlapping functionality.
 * 
 * @author john
 */

class trueProspectExample extends prospectExample{	
	//column names for csv of this SOM example - won't have events
	private static final String csvColDescrPrfx = "OID,Prospect_LU_Date,Num Opt Event Dates,Num Link Event Dates,Num Src Event Dates";
	//all kinds of events present
	public static final String[] eventMapTypeKeys = new String[] {"opts", "links", "sources"};
	//event mappings use the occurrence data value (opt/source)
	public static final boolean[] eventMapUseOccData = new boolean[] {true,false,true};
	//csv labels
	private static final String[] CSVSentinelLbls = new String[] {"OPT|,", "LNK|,", "SRC|," };
	
	//build this object based on prospectData object 
	public trueProspectExample(StraffSOMMapManager _map,prospectData _prspctData) {
		super(_map,ExDataType.trueProspect,_prspctData);
	}//prospectData ctor
	
	//build this object based on csv string - rebuild data from csv string columns 4+
	public trueProspectExample(StraffSOMMapManager _map,String _OID, String _csvDataStr) {
		super(_map,ExDataType.trueProspect,_OID,_csvDataStr);	
	}//csv string ctor
	
	public trueProspectExample(prospectExample ex) {
		super(ex);		
		//set this type
		type = ExDataType.trueProspect;
		//provide shallow copy of jpOcc struct - only copying passed event type key values
		JpOccurrences = ex.copyJPOccStruct(eventMapTypeKeys);		
		//provide shallow copy of jpOcc struct - only copying passed event type key values
		eventsByDateMap = ex.copyEventsByDateMap(eventMapTypeKeys);
	}//copy ctor
	
	@Override
	//get #'s of events from partitioned csv data held in dataAra - need to accommodate source events - idxs depend on how the data was originally built - true prospects have no orders
	protected int[] _getCSVNumEvsAra(String[] dataAra) {return new int[] {Integer.parseInt(dataAra[2]),Integer.parseInt(dataAra[3]),Integer.parseInt(dataAra[4])};}
	
	@Override
	protected void buildDataFromCSVString(int[] numEvntsAra, String _csvDataStr) {
		//each type of event list exists between the sentinel flag and the subsequent sentinel flag
		for (int i = 0; i<numEvntsAra.length;++i) {
			if (numEvntsAra[i] > 0) {
				String key = eventMapTypeKeys[i];
				String stSentFlag = CSVSentinelLbls[i];
				String endSntnlFlag = (i <eventMapTypeKeys.length-1 ? CSVSentinelLbls[i+1] : null );
				String [] strAraBegin = _csvDataStr.trim().split(Pattern.quote(stSentFlag)); //idx 1 holds all event data	
				String strBegin = (strAraBegin.length < 2) ? "" : strAraBegin[1];
				String strEvents = (endSntnlFlag != null ? strBegin.trim().split(Pattern.quote(endSntnlFlag))[0] : strBegin).trim(); //idx 0 holds relevant data
				buildEventTrainDataFromCSVStr(key, numEvntsAra[i],strEvents,eventsByDateMap.get(key), EvtDataType.getVal(i));				
			}			
		}
	}//buildDataFromCSVString
	
   //take loaded data and convert to feature data via calc object
	@Override
	protected void buildFeaturesMap() {	//TODO do we wish to modify this for prospects?  probably
		//access calc object
		if (allJPs.size() > 0) {//getting from orders should yield empty list, might yield null
			ftrMaps[ftrMapTypeKey] = mapMgr.ftrCalcObj.calcFeatureVector(this,allJPs, JpOccurrences.get("links"), JpOccurrences.get("opts"), JpOccurrences.get("sources"));
		}
		else {ftrMaps[ftrMapTypeKey] = new TreeMap<Integer, Float>();}
		//now, if there's a non-null posOptAllEventObj then for all jps who haven't gotten an opt conribution to calculation, add positive opt-all result
	}//buildFeaturesMap	

	@Override
	protected void _initObjsIndiv() {	for (String key : eventMapTypeKeys) {eventsByDateMap.put(key, new TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> ());	}	}//_initObjsIndiv()

	
	@Override
	//overriding default behavior, this is a mapping of non-zero source data elements to their idx in the underlying feature vector
	//if jp is not prsent, then will just quietly remove it
	protected void buildAllNonZeroFtrIDXs() {
		allNonZeroFtrIDXs = new ArrayList<Integer>();
		HashSet<Integer> jpsToRemove = new HashSet<Integer>();
		for(Integer jp : allJPs) {
			Integer jpIDX = jpJpgMon.getJpToFtrIDX(jp);
			if(jpIDX==null) {		jpsToRemove.add(jp);}//if not present then remove
			else {allNonZeroFtrIDXs.add(jpJpgMon.getJpToFtrIDX(jp));}
		}
		if(jpsToRemove.size()>0) {			
			for(Integer jp : jpsToRemove) { 	//virtual jp that has no representation in real data - remove this from occs structure and from allJPs structure	
				removeAllJPOccs(jp);
				allJPs.remove(jp);	
			}
		}
	}//buildAllNonZeroFtrIDXs (was buildAllJPFtrIDXsJPs)
	
	//is never training data
	@Override
	public boolean getIsTrainingData() {return false;}
	//is never in test/train partition
	@Override
	public int getTestTrainIDX() { return -1;}	
	//this prospect is an actual customer - use as training data
	@Override
	public boolean isTrainablePastCustomer() { return false ;}
	//returns true if -any training-related- events are present in this record (i.e. not counting source "events")
	//we don't ever want to train with a true prospect so this should always be false
	@Override
	protected boolean hasRelelventTrainingEvents() { return false;}//hasRelelventEvents	
	
	@Override
	public void finalizeBuild() {
		buildOccurrenceStructs(eventMapTypeKeys, eventMapUseOccData);	
		//all jps holds all jps in this example based on occurences; will not reference jps implied by opt-all records
		allJPs = buildJPListFromOccs();
		if(allJPs.size() == 0) {setIsBadExample(true);		}//means there's no valid jps in this record's occurence data
	}//finalize

	//column names for raw descriptorCSV output
	@Override
	public String getRawDescColNamesForCSV(){
		String csvColDescr = csvColDescrPrfx + ",";
		//add extra column descriptions for orders if using any		
		return csvColDescr;	
	}//getRawDescColNamesForCSV
	
	@Override
	protected String[] getEventMapTypeKeys() {	return eventMapTypeKeys;}
	@Override
	protected String[] getCSVSentinelLbls() {return CSVSentinelLbls;}

	//return boolean array describing this example's occurence structure
	//idxs : 0==if in an order; 1==if in an opt, 2 == if is in link, 3==if in source event,4 if in any non-source event (denotes action by customer), 5 if in any event, including source, 
	//needs to follow format of eventMapTypeKeys
	@Override
	public boolean[] getExampleStatusOcc() {
		boolean	has_ordr = false ,
				has_opt = (JpOccurrences.get("opts").size() !=0),				//seems to not correspond to size of eventsByDateMap.get("opts").size()
				has_link = (JpOccurrences.get("links").size() !=0),	
				has_source = (JpOccurrences.get("sources").size() !=0),			//do not treat source data as an event in this case
				has_event = has_ordr || has_opt || has_link;					//not counting source data
		boolean[] res = new boolean[] {has_ordr, has_opt, has_link, has_source,has_event};
		return res;		
	}//

	//return boolean array describing this example's eventsByDate structure -should match occurence structure
	//idxs : 0==if in an order; 1==if in an opt, 2 == if is in link, 3==if in source event,4 if in any non-source event (denotes action by customer), 5 if in any event, including source, 
	//needs to follow format of eventMapTypeKeys
	@Override
	public boolean[] getExampleStatusEvt() {
		boolean	has_ordr = false ,
				has_opt = (eventsByDateMap.get("opts").size() !=0),
				has_link = (eventsByDateMap.get("links").size() !=0),	
				has_source = (eventsByDateMap.get("sources").size() !=0),			//do not treat source data as an event in this case
				has_event = has_ordr || has_opt || has_link;					//not counting source data
		boolean[] res = new boolean[] {has_ordr, has_opt, has_link, has_source,has_event};
		return res;		
	}//check if JP exists, and where

	//check whether this prospect has a particular jp in either his prospect data, events or opts
	//idxs : 0==if in an order; 1==if in an opt, 2 == if is in link, 3==if in source event,4 if in any non-source event (denotes action by customer), 5 if in any event, including source, 
	//it is assumed that hasJP_event is after all event booleans (if future bools are added)
	//needs to follow format of eventMapTypeKeys
	@Override
	public boolean[] hasJP(Integer _jp) {
		boolean	hasJP_ordr = false ,
				hasJP_opt = (JpOccurrences.get("opts").get(_jp) != null),
				hasJP_link = (JpOccurrences.get("links").get(_jp) != null),	
				hasJP_source = (JpOccurrences.get("sources").get(_jp) != null),			//do not treat source data as an event in this case
				hasJP_event = hasJP_ordr || hasJP_opt || hasJP_link,					//not counting source data
				hasJP = hasJP_event || hasJP_source;
		boolean[] res = new boolean[] {hasJP_ordr, hasJP_opt, hasJP_link, hasJP_source, hasJP_event, hasJP};
		return res;		
	}//check if JP exists, and where
	
	
	@Override
	public String toString() {	
		String res = "True Prospect : " + super.toString();
		for (String key : eventMapTypeKeys) {
			res += toStringDateMap(eventsByDateMap.get(key), key);
			res += toStringOptOccMap(JpOccurrences.get(key), key + " occurences");
		}
		return res;
	}


}//pureProspectExample



/**
 * this class implements a product example, to be used to query the SOM and to illuminate relevant regions on the map.  
 * The product can be specified by a single jp, or by the span of jps' related to a particular jpg, or even to multiple
 * Unlike prospect examples, which are inferred to have 0 values in features that are not explicitly populated, 
 * product examples might not be considered strictly exclusionary (in other words, non-populated features aren't used for distance calcs)
 * This is an important distinction for the SOM-from this we will learn about folks whose interest overlap into multiple jp regions.
 * @author john
 */
class ProductExample extends StraffSOMExample{
//		//column names for csv output of this SOM example
	private static final String csvColDescrPrfx = "ID,NumJPs";
	protected TcTagTrainData trainPrdctData;		
	//this array holds float reps of "sumtorial" of idx vals, used as denominators of ftr vectors so that 
	//arrays of jps of size idx will use this value as denominator, and (idx - jp idx)/denominator as weight value for ftr vec 
	private static float[] ordrWtAraPerSize;
	//this is a vector of all seen mins and maxs for wts for every product. Only used for debugging display of spans of values
	private static TreeMap<Integer, Float> wtMins, wtMaxs, wtDists;		
	//two maps of distances to each map node for each product, including unshared features and excluding unshared features in distance calc
	private TreeMap<Double,ArrayList<SOMMapNode>>[] allMapNodesDists;	
	//two kinds of maps to bmus available - all ftrs looks at all feature values for distances, 
	//while shared only measures distances where this example's wts are non-zero
	public static final int
		AllFtrsIDX = 0,				//looks at all features in this node for distance calculations
		SharedFtrsIDX = 1;			//looks only at non-zero features in this node for distance calculations
	private static int numFtrCompVals = 2;
	
	//types to conduct similarity mapping
	private static int[] prospectTypes_idxs = new int[] {ExDataType.customerTraining.getVal(), ExDataType.customerTesting.getVal()};

	//color to illustrate map (around bmu) region corresponding to this product - use distance as alpha value
	private int[] prodClr;
		
	public ProductExample(StraffSOMMapManager _map, TcTagData data) {
		super(_map,ExDataType.Product,data.OID);
		trainPrdctData = new TcTagTrainData(data);	
		initProdBMUMaps();		
	}//ctor
	
	public ProductExample(StraffSOMMapManager _map,String _OID, String _csvDataStr) {
		super(_map,ExDataType.Product,_OID);
		trainPrdctData = new TcTagTrainData(_csvDataStr);
		initProdBMUMaps();
	}//ctor
	
	private void initProdBMUMaps() {
		prodClr = mapMgr.getRndClr();
		rad = 3.0f;
		prodClr[3]=255;
		allMapNodesDists = new TreeMap[numFtrCompVals];
		for (Integer i=0; i<numFtrCompVals;++i) {			allMapNodesDists[i] = new TreeMap<Double,ArrayList<SOMMapNode>>();		}
	}	
	
	//Only used for products since products extend over non-exclusive zones of the map
	//distMeasType : "AllFtrs" : looks at all features for distances; "SharedFtrs" : looks at only features that are non-zero in the product example
	public void setMapNodesStruct(int mapNodeIDX, TreeMap<Double, ArrayList<SOMMapNode>> mapNodes) {
		allMapNodesDists[mapNodeIDX] =  mapNodes;
	}
	
	//call this before any data loading that will over-write the existing product examples is performed
	public static void initAllStaticProdData() {
		ordrWtAraPerSize = new float[100];	//100 is arbitrary but much more than expected # of jps per product. dont expect a product to have anywhere near this many jps
		for (int i =1;i<ordrWtAraPerSize.length;++i) {ordrWtAraPerSize[i]=1.0f*(ordrWtAraPerSize[i-1]+i);}
		//manage mins and maxes seen for ftrs, keyed by ftr idx
		wtMins = new TreeMap<Integer, Float>();
		wtMaxs = new TreeMap<Integer, Float>();		
		wtDists = new TreeMap<Integer, Float>();		
	}//initAllProdData()

	public static String[] getMinMaxDists() {
		String[] res = new String[wtMins.size()+1];
		int i=0;
		res[i++] = "idx |\tMin|\tMax|\tDist";
		for(Integer idx : wtMins.keySet()) {res[i++]=""+ idx + "\t" + String.format("%6.4f",wtMins.get(idx)) + "\t" + String.format("%6.4f",wtMaxs.get(idx)) + "\t" + String.format("%6.4f",wtDists.get(idx));}
		return res;
	}//getMinMaxDists

	@Override
	public void finalizeBuild() {		allJPs = trainPrdctData.getAllJpsInData();	}
	@Override
	protected HashSet<Tuple<Integer, Integer>> getSetOfAllJpgJpData() {
		HashSet<Tuple<Integer,Integer>> res = trainPrdctData.getAllJpgJpsInData();
		return res;
	}//getSetOfAllJpgJpData
	
	@Override
	//this is called after an individual example's features are built
	protected void _PostBuildFtrVec_Priv() {
		//features and std ftrs should be the same, since we only assign a 1 to values that are present
		buildStdFtrsMap();
	}//_PostBuildFtrVec_Priv

	
	//required info for this example to build feature data  - this is ignored. these objects can be rebuilt on demand.
	@Override
	public String getRawDescrForCSV() {
		String res = ""+OID+","+allJPs.size()+",";
		res += trainPrdctData.buildCSVString();
		return res;	
	}//getRawDescrForCSV
	//column names for raw descriptorCSV output
	@Override
	public String getRawDescColNamesForCSV(){		
		String csvColDescr = csvColDescrPrfx + ",";	
		return csvColDescr;	
	}//getRawDescColNamesForCSV	
	
	private void setFtrMinMax(int idx, float val) {
		//min is always 0
		wtMins.put(idx, 0.0f);
//			Float getVal = wtMins.get(idx);
//			if(getVal == null) {getVal = 0.0f;}
//			wtMins.put(idx, (val<getVal) ? val : getVal);
		
		Float getVal = wtMaxs.get(idx);
		if(getVal == null) {getVal = -100000000.0f;}
		wtMaxs.put(idx,(val>getVal) ? val : getVal);	
		wtDists.put(idx, wtMaxs.get(idx)- wtMins.get(idx));		
	}//setFtrMinMax

	
	//draw all map nodes this product exerts influence on, with color alpha reflecting inverse distance, above threshold value set when nodesToDraw map was built
	public void drawProdMapExtent(SOM_StraffordMain p, int distType, int numProds, double _maxDist) {
		p.pushMatrix();p.pushStyle();		
		NavigableMap<Double, ArrayList<SOMMapNode>> subMap = allMapNodesDists[distType].headMap(_maxDist, true);
		//float mult = 255.0f/(numProds);//with multiple products maybe scale each product individually by total #?
		for (Double dist : subMap.keySet()) {
			ArrayList<SOMMapNode> nodeList = subMap.get(dist);
			prodClr[3]=(int) ((1-(dist/_maxDist))*255);
			for (SOMMapNode n : nodeList) {			n.drawMeProdBoxClr(p, prodClr);		}
		}
		p.popStyle();p.popMatrix();		
	}//drawProdMapExtent
	
	//convert distances to confidences so that for [_maxDist <= _dist <= 0] :
	//this returns [0 <= conf <= _maxDist*_maxDistScale]; gives 0->1 as confidence
	private static double distToConf(double _dist, double _maxDist) { 	return (_maxDist - _dist)/_maxDist;	}
	
	//return a map of all map nodes as keys and their Confidences as values to this product - inv
	public HashMap<SOMMapNode, Double> getMapNodeConf(int distType, double _maxDist) {
		NavigableMap<Double, ArrayList<SOMMapNode>> subMap = allMapNodesDists[distType].headMap(_maxDist, true);
		HashMap<SOMMapNode, Double> resMap = new HashMap<SOMMapNode, Double>();
		for (Double dist : subMap.keySet()) {
			double conf = distToConf(dist, _maxDist);
			ArrayList<SOMMapNode> mapNodeList = subMap.get(dist);		
			for (SOMMapNode n : mapNodeList) {		resMap.put(n, conf);		}			
		}
		return resMap;
	}//getAllMapNodDists
	
	//build a map keyed by distance to each map node of arrays of maps of arrays of that mapnode's examples, each array keyed by their distance:
	//Outer map is keyed by distance from prod to map node, value is array (1 per map node @ dist) of maps keyed by distance from example to map node, value is array of all examples at that distance)
	//x : this product's distance to map node ; y : each example's distance to map node
	//based on their bmu mappings, to map nodes within _maxDist threshold of this node
	//subMap holds all map nodes within _maxDist of this node
	private TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>> getExamplesNearThisProd(NavigableMap<Double, ArrayList<SOMMapNode>> subMap, int typeIDX) {
		TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>> nodesNearProd = new TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>>();
		HashMap<SOMExample, Double> tmpMapOfNodes;
		for (Double dist : subMap.keySet()) {								//get all map nodes of certain distance from this node; - this dist is distance from this node to map node
			ArrayList<SOMMapNode> mapNodeList = subMap.get(dist);				//all ara of all map nodes of specified distance from this product node
			TreeMap<Double, ArrayList<SOMExample>> tmpMapOfArrays = nodesNearProd.get(dist);			
			if (tmpMapOfArrays==null) {tmpMapOfArrays = new TreeMap<Double, ArrayList<SOMExample>>();}				
			for (SOMMapNode n : mapNodeList) {									//for each map node get all examples of ExDataType that consider that map node BMU
				tmpMapOfNodes = n.getAllExsAndDist(typeIDX);					//each prospect example and it's distance from the bmu map node				
				for(SOMExample exN : tmpMapOfNodes.keySet()) {		//for each example that treats map node as bmu
					Double distFromBMU = tmpMapOfNodes.get(exN);
					ArrayList<SOMExample> exsAtDistFromMapNode = tmpMapOfArrays.get(distFromBMU);
					if (exsAtDistFromMapNode==null) {exsAtDistFromMapNode = new ArrayList<SOMExample>();}
					exsAtDistFromMapNode.add(exN);
					tmpMapOfArrays.put(distFromBMU,exsAtDistFromMapNode);
				}//for each node at this map node
			}//for each map node at distance dist			
			nodesNearProd.put(dist, tmpMapOfArrays);
		}
		return nodesNearProd;
	}//getExamplesNearThisProd	
	
	//returns a map of dists from this product as keys and values as maps of distance of examples from their bmus as keys and example itself as value
	//primary key is distance to a map node from this node, map holds distance-keyed lists of examples from their bmus.  
	//multiple map nodes may lie on same distance from this node but example will only have 1 bmu
	private TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>> getAllExamplesNearThisProd(int distType, double _maxDist) {
		NavigableMap<Double, ArrayList<SOMMapNode>> subMap = allMapNodesDists[distType].headMap(_maxDist, true);		
		TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>> allNodesAndDists = new TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>>();		
		TreeMap<Double, ArrayList<SOMExample>> srcMapNodeAra, destMapNodeAra;
		ArrayList<SOMExample> srcExAra, destExAra;
		for(int _typeIDX : prospectTypes_idxs) {
			TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>> tmpMapOfAllNodes = getExamplesNearThisProd(subMap, _typeIDX);
			for (Double dist1 : tmpMapOfAllNodes.keySet()) {//this is dist from product to map node source of these examples
				srcMapNodeAra = tmpMapOfAllNodes.get(dist1);				
				destMapNodeAra = allNodesAndDists.get(dist1);
				if(destMapNodeAra==null) {destMapNodeAra = new TreeMap<Double, ArrayList<SOMExample>>();}
				for (Double dist2 : srcMapNodeAra.keySet()) {
					srcExAra = srcMapNodeAra.get(dist2);
					destExAra = destMapNodeAra.get(dist2);
					if(destExAra==null) {destExAra = new ArrayList<SOMExample>(); }
					destExAra.addAll(srcExAra);
					destMapNodeAra.put(dist2, destExAra);
				}
				allNodesAndDists.put(dist1, destMapNodeAra);
			}		
		}//per type
		return allNodesAndDists;		
	}//getExListNearThisProd
	
	//get string array representation of this single product - built on demand
	public String[] getAllExmplsPerProdStrAra(int distType,double _maxDist) {
		ArrayList<String> resAra = new ArrayList<String>();
		TreeMap<Double, ArrayList<SOMExample>> exmplsAtDist;		
		String ttlStr = "Product ID : " + this.OID + " # of JPs covered : " + allJPs.size()+" : ";
		for (Integer jp : allJPs) {	ttlStr += "" + jp + " : " + jpJpgMon.getJPNameFromJP(jp) + ", ";	}		
		resAra.add(ttlStr);
		resAra.add("OID,Confidence at Map Node,Error at Map Node");
		TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>> allNodesAndConfs = getAllExamplesNearThisProd(distType, _maxDist);
		String confStr, dist2BMUStr;
		for(Double dist : allNodesAndConfs.keySet()) { 
			exmplsAtDist = allNodesAndConfs.get(dist);
			confStr = String.format("%.6f",distToConf(dist, _maxDist));
			for(Double dist2BMU : exmplsAtDist.keySet()){		
				ArrayList<SOMExample> exsAtDistFromBMU = exmplsAtDist.get(dist2BMU);
				dist2BMUStr = String.format("%.6f",dist2BMU);
				for (SOMExample exN : exsAtDistFromBMU) {				
					resAra.add("" + exN.OID + "," + confStr + ","+dist2BMUStr);	
				}//for all examples at bmu
			}//for all bmus at certain distance
		}//for all distances/all bmus
		return resAra.toArray(new String[0]);	
	}//getBestExsStrAra	
	
	//take loaded data and convert to output data
	@Override
	protected void buildFeaturesMap() {
		ftrMaps[ftrMapTypeKey] = new TreeMap<Integer, Float>();	
		//order map gives order value of each jp - provide multiplier for higher vs lower priority jps
		TreeMap<Integer, Integer> orderMap = trainPrdctData.getJPOrderMap();
		int numJPs = orderMap.size();
		//verify # of jps as expected
		if (numJPs != allNonZeroFtrIDXs.size()) {	
			mapMgr.dispMessage("ProductExample", "buildFeaturesMap", "Problem with size of expected jps from trainPrdctData vs. allJPFtrIDXs : trainPrdctData says # jps == " +numJPs + " | allJPFtrIDXs.size() == " +allNonZeroFtrIDXs.size(), MsgCodes.warning2);
		}
		if(numJPs == 1) {
			Integer ftrIDX = allNonZeroFtrIDXs.get(0);
			ftrMaps[ftrMapTypeKey].put(ftrIDX, 1.0f);
			setFtrMinMax(ftrIDX, 1.0f);
			this.ftrVecMag = 1.0f;			
		} else {//more than 1 jp for this product
			float val, ttlVal = 0.0f, denom = ordrWtAraPerSize[numJPs];
			for (Integer IDX : allNonZeroFtrIDXs) {
				Integer jp = jpJpgMon.getJpByIdx(IDX);
				val = (numJPs - orderMap.get(jp))/denom;
				ftrMaps[ftrMapTypeKey].put(IDX,val);
				setFtrMinMax(IDX, val);
				ttlVal += val;
			}	
			this.ftrVecMag = (float) Math.sqrt(ttlVal);
		}
	}//buildFeaturesMap
	
	@Override
	protected void buildStdFtrsMap() {
		ftrMaps[stdFtrMapTypeKey] = new TreeMap<Integer, Float>();
		for (Integer IDX : ftrMaps[ftrMapTypeKey].keySet()) {ftrMaps[stdFtrMapTypeKey].put(IDX,ftrMaps[ftrMapTypeKey].get(IDX));}//since features are all weighted to sum to 1, can expect ftrmap == strdizedmap
		setFlag(stdFtrsBuiltIDX,true);
	}//buildStdFtrsMap

	@Override
	public String toString(){
		String res = "Example OID# : "+OID;
		if(null!=mapLoc){res+="Location on SOM map : " + mapLoc.toStrBrf();}
		if (mapMgr.numFtrs > 0) {			res += "\n\tFeature Val(s) : " + dispFtrMapVals(ftrMaps[ftrMapTypeKey]);		} 
		else {								res += "No Features for this product example";		}
		return res;
	}

}//class productExample

////this class represents a particular node in the SOM map, with specific customizations for strafford data
class StraffSOMMapNode extends SOMMapNode{
	//reference to jp-jpg mapping/managing object
	protected static MonitorJpJpgrp jpJpgMon;
//	//these objects are for reporting on individual examples.  They are built when features, keyed by ftr type, and are 
//	//use a map per feature type : unmodified, normalized, standardized,to hold the features sorted by weight as key, value is array of jps at that weight -submap needs to be instanced in descending key order
//	private TreeMap<Float, ArrayList<Integer>>[] mapOfTopWtJps;	
//	//a map per feature type : unmodified, normalized, standardized,  of jps and their relative "rank" in this particular example, as determined by the weight calc
//	private TreeMap<Integer,Integer>[] mapOfJpsVsWtRank;
	
	public StraffSOMMapNode(StraffSOMMapManager _map, Tuple<Integer,Integer> _mapNode, float[] _ftrs) {
		super(_map, _mapNode, _ftrs);
		jpJpgMon = mapMgr.jpJpgrpMon;
		_initDataFtrMappings();		
	}//ctor w/float ftrs
	
	//build a map node from a string array of features
	public StraffSOMMapNode(StraffSOMMapManager _map,Tuple<Integer,Integer> _mapNode, String[] _strftrs) {
		super(_map, _mapNode, _strftrs);
		jpJpgMon = mapMgr.jpJpgrpMon;
		_initDataFtrMappings();
	}//ctor w/str ftrs	
	
	//called after ftrs are built
	protected void _initDataFtrMappings() {	
		for (Integer idx : ftrMaps[ftrMapTypeKey].keySet()) {
			float val = ftrMaps[ftrMapTypeKey].get(idx);
		}
		//build essential components of feature vector
		buildAllNonZeroFtrIDXs();
		buildNormFtrData();//once ftr map is built can normalize easily
		_PostBuildFtrVec_Priv();
	}//_initDataFtrMappings
	
	@Override
	//called by SOMDataLoader - these are standardized based on data mins and diffs seen in -map nodes- feature data, not in training data
	public void buildStdFtrsMapFromFtrData_MapNode(float[] minsAra, float[] diffsAra) {
		ftrMaps[stdFtrMapTypeKey] = new TreeMap<Integer, Float>();
		if (ftrMaps[ftrMapTypeKey].size() > 0) {
			for(Integer destIDX : ftrMaps[ftrMapTypeKey].keySet()) {
				Float lb = minsAra[destIDX], diff = diffsAra[destIDX];
				float val = 0.0f;
				if (diff==0) {//same min and max
					if (lb > 0) {	val = 1.0f;}//only a single value same min and max-> set feature value to 1.0
					else {val= 0.0f;}
				} else {				val = (ftrMaps[ftrMapTypeKey].get(destIDX)-lb)/diff;				}
				ftrMaps[stdFtrMapTypeKey].put(destIDX,val);
			}//for each jp
		}
		setFlag(stdFtrsBuiltIDX,true);
	}//buildStdFtrsMap_MapNode
	
	//by here ftrs for this map node have been built
	@Override
	protected void buildAllNonZeroFtrIDXs() {
		allNonZeroFtrIDXs = new ArrayList<Integer>();
		for(Integer idx : ftrMaps[ftrMapTypeKey].keySet()) {		allNonZeroFtrIDXs.add(idx);	}
	}//buildAllNonZeroFtrIDXs

	@Override
	protected void _PostBuildFtrVec_Priv() {}
	

	@Override
	protected String dispFtrVal(TreeMap<Integer, Float> ftrs, Integer i) {
		Float ftr = ftrs.get(i);
		int jp = jpJpgMon.getJpByIdx(i);
		return "jp : " + jp + " | idx : " + i + " | val : " + String.format("%1.4g",  ftr) + " || ";
	}

}//SOMMapNodeExample


//this class is for a simple object to just represent a mouse-over on the visualization of the map
class DispSOMMapExample extends StraffSOMExample{
	private float ftrThresh;
	private int mapType;
	private int[] clrVal = new int[] {255,255,0,255};
	private String[] mseLabelAra;
	private float[] mseLabelDims;

	//need to support all ftr types from map - what type of ftrs are these?
	public DispSOMMapExample(StraffSOMMapManager _map, myPointf ptrLoc, TreeMap<Integer, Float> _ftrs, float _thresh) {
		super(_map, ExDataType.MouseOver,"Mse_"+ptrLoc.toStrBrf());//(" + String.format("%.4f",this.x) + ", " + String.format("%.4f",this.y) + ", " + String.format("%.4f",this.z)+")
		//type of features used for currently trained map
		mapType = mapMgr.getCurrentTrainDataFormat();
		
		ftrMaps[ftrMapTypeKey] = new TreeMap<Integer, Float>();	
		ftrThresh = _thresh;
		allJPs = new HashSet<Integer>();
		allNonZeroFtrIDXs = new ArrayList<Integer>();
		//decreasing order
		TreeMap<Float, String> strongestFtrs = new TreeMap<Float, String>(Collections.reverseOrder());
		for(Integer ftrIDX : _ftrs.keySet()) {
			Float ftr = _ftrs.get(ftrIDX);
			if(ftr >= ftrThresh) {	
				Integer jp = jpJpgMon.getJpByIdx(ftrIDX);
				allJPs.add(jp);
				allNonZeroFtrIDXs.add(ftrIDX);	
				ftrMaps[ftrMapTypeKey].put(ftrIDX, ftr);
				strongestFtrs.put(ftr, ""+jp);
			}
		}	
		ArrayList<String> _mseLblDat = new ArrayList<String>();
		int longestLine = 4;
		String line = "JPs : ";
		//descriptive mouse-over label - top x jp's
		if (allJPs.size()== 0) {	_mseLblDat.add(line + "None");	}
		else {
			int jpOnLine = 0, jpPerLine = 3;
			for (Float ftr : strongestFtrs.keySet()) {
				String jpName = strongestFtrs.get(ftr);
				line += ""+jpName+":" + String.format("%03f", ftr);
				if(jpOnLine < jpPerLine-1) {				line += " | ";			}
				longestLine = longestLine >= line.length() ? longestLine : line.length();
				++jpOnLine;
				if (jpOnLine >= jpPerLine) {
					_mseLblDat.add(line);
					line="";
					jpOnLine = 0;
				}
			}
		}	
		mseLabelAra = _mseLblDat.toArray(new String[1]);
		mseLabelDims = new float[] {10, -10.0f,longestLine*6.0f, mseLabelAra.length*10.0f + 15.0f};
	}//ctor	
	//need to support all ftr types from map
	public DispSOMMapExample(StraffSOMMapManager _map, myPointf ptrLoc, float distData, float _thresh) {
		super(_map, ExDataType.MouseOver,"Mse_"+ptrLoc.toStrBrf());//(" + String.format("%.4f",this.x) + ", " + String.format("%.4f",this.y) + ", " + String.format("%.4f",this.z)+")
		//type of features used for currently trained map
		mapType = mapMgr.getCurrentTrainDataFormat();
		
		ftrMaps[ftrMapTypeKey] = new TreeMap<Integer, Float>();	
		ftrThresh = _thresh;
		allJPs = new HashSet<Integer>();
		allNonZeroFtrIDXs = new ArrayList<Integer>();

		ArrayList<String> _mseLblDat = new ArrayList<String>();
		String line = "Dist : " + String.format("%05f", distData);
		int longestLine = line.length();
		_mseLblDat.add(line);

		mseLabelAra = _mseLblDat.toArray(new String[1]);
		mseLabelDims = new float[] {10, -10.0f,longestLine*6.0f+10, mseLabelAra.length*10.0f + 15.0f};
	}//ctor				
	//not used by this object
	@Override
	protected HashSet<Tuple<Integer, Integer>> getSetOfAllJpgJpData() {	return null;}//getSetOfAllJpgJpData
	@Override
	protected void buildFeaturesMap() { }	
	@Override
	public String getRawDescrForCSV() {	return "Should not save DispSOMMapExample to CSV";}
	@Override
	public String getRawDescColNamesForCSV() {return "Do not save DispSOMMapExample to CSV";}

	@Override
	public void finalizeBuild() {}

	public void drawMeLblMap(SOM_StraffordMain p){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label	
		//p.showBox(mapLoc, rad, 5, clrVal,clrVal, SOM_StraffordMain.gui_LightGreen, mseLabelDat);
		//(myPointf P, float rad, int det, int[] clrs, String[] txtAra, float[] rectDims)
		p.showBox(mapLoc, 5, 5,nodeClrs, mseLabelAra, mseLabelDims);
		p.popStyle();p.popMatrix();		
	}//drawLabel	

	@Override
	protected void buildStdFtrsMap() {	
		ftrMaps[stdFtrMapTypeKey] = new TreeMap<Integer, Float>();
		if (allNonZeroFtrIDXs.size() > 0) {ftrMaps[stdFtrMapTypeKey] = calcStdFtrVector(ftrMaps[ftrMapTypeKey], allNonZeroFtrIDXs);}
		setFlag(stdFtrsBuiltIDX,true);
	}

}//DispSOMMapExample

/**
 * enum used to specify each kind of example data point, primarily for visualization purposes
 * @author john
 */
enum ExDataType {
	customerTraining(0), customerTesting(1), trueProspect(2), Product(3), MapNode(4), MouseOver(5);
	private int value; 
	private static Map<Integer, ExDataType> map = new HashMap<Integer, ExDataType>(); 
	static { for (ExDataType enumV : ExDataType.values()) { map.put(enumV.value, enumV);}}
	private ExDataType(int _val){value = _val;} 
	public int getVal(){return value;}
	public static ExDataType getVal(int idx){return map.get(idx);}
	public static int getNumVals(){return map.size();}						//get # of values in enum
	@Override
    public String toString() { return ""+value; }	
}//enum ExDataType

/**
 * enum used to specify the type of event responsible for data
 */
enum EvtDataType {
	Order(0), Opt(1), Link(2), Source(3);
	private int value; 
	private static Map<Integer, EvtDataType> map = new HashMap<Integer, EvtDataType>(); 
	static { for (EvtDataType enumV : EvtDataType.values()) { map.put(enumV.value, enumV);}}
	private EvtDataType(int _val){value = _val;} 
	public int getVal(){return value;}
	public static EvtDataType getVal(int idx){return map.get(idx);}
	public static int getNumVals(){return map.size();}						//get # of values in enum
	@Override
    public String toString() { return ""+value; }	
}//enum EvtDataType


/**
 * this class is a simple struct to hold a single jp's jpg, and the count and date of all occurrences for a specific OID
 */
class jpOccurrenceData{
	//this jp
	public final Integer jp;
	//owning jpg
	public final Integer jpg;
	//keyed by date, value is # of occurences at date for this jp and opt value/src type (ignored unless opt or source record)
	//private TreeMap<Date, Tuple<Integer, Integer>> occurrences;
	//keyed by date, value is map of opt/src value and value is # of occurences at date for this jp and opt value/src type (ignored unless opt or source record)
	private TreeMap<Date, TreeMap<Integer, Integer>> occurrences;
	//whether or not opt/src is used for this occurence
	private boolean usesOpt;
	
	public jpOccurrenceData(Integer _jp, Integer _jpg, boolean _usesOpt) {jp=_jp;jpg=_jpg; usesOpt=_usesOpt;occurrences = new TreeMap<Date, TreeMap<Integer, Integer>>();}	
//	//add an occurence on date
//	public void addOccurrence(Date date, int opt) {
//		Tuple<Integer, Integer> oldTup = occurrences.get(date);
//		
//		//THIS MUST CHANGE - must support multiple values since source data is using this field to mark multiple sources
//		//shouldn't have different opt values for same jp for same date; issue warning if this is the case
//		if ((oldTup!=null) && (oldTup.y != opt)) {System.out.println("jpOccurrenceData::addOccurrence : !!!Warning!!! : JP : " + jp + " on Date : " + date + " has 2 different opt values set in data : " +  oldTup.y + " and "+ opt +" | Using :"+ opt);}
//		if(oldTup==null) {oldTup = new Tuple<Integer,Integer>(0,0); }
//		Tuple<Integer, Integer> newDatTuple = new Tuple<Integer,Integer>(1 + oldTup.x,opt);		
//		occurrences.put(date, newDatTuple);
//	}//addOccurence
	
	//add an occurence on date
	public void addOccurrence(Date date, int val) {
		TreeMap<Integer, Integer> valsAtDate = occurrences.get(date);
		if(valsAtDate==null) {valsAtDate = new TreeMap<Integer, Integer>(); }
		Integer count = valsAtDate.get(val);
		if(count == null) {count = 0;}
		++count;
		valsAtDate.put(val, count);
		occurrences.put(date, valsAtDate);
	}//addOccurence
	//accessors
	public Date[] getDatesInOrder() {return occurrences.keySet().toArray(new Date[0]);}	
	
	//get occurrence with largest date
	public Entry<Date, TreeMap<Integer, Integer>> getLastOccurrence(){		return occurrences.lastEntry();	}//
	
	//get the occurence count and opt value for a specific date
	public TreeMap<Integer, Integer> getOccurrences(Date date){
		TreeMap<Integer, Integer> valsAtDate = occurrences.get(date);
		//should never happen - means querying a date that has no record. 
		if(valsAtDate==null) {valsAtDate = new TreeMap<Integer, Integer>(); }
		return valsAtDate;
	}//getOccurrences
	
	//get tuple of jpg and jp
	public Tuple<Integer, Integer> getJpgJp(){return new Tuple<Integer,Integer>(jpg, jp);}
	
	//public TreeMap<Date, Tuple<Integer, Integer>> getOccurrenceMap(){return occurrences;}
	
	public String toString() {
		String res = "JP : " + jp + " | JPGrp : " + jpg + (occurrences.size() > 1 ? "\n" : "");
		for (Date dat : occurrences.keySet()) {
			TreeMap<Integer, Integer> occData = occurrences.get(dat);
			for (Integer opt : occData.keySet()) {
				Integer count = occData.get(opt);
				String optStr = "";
				if(usesOpt) { optStr = " | Opt : " + opt;} //is an opt occurence
				res += "\t# occurences : " + count + optStr;
			}			
		}
		return res;
	}		
}//class jpOccurenceData

/**
 * this class holds information from a single record.  It manages functionality to convert from the raw data to the format used to construct training examples
 * @author john
 *
 */
abstract class StraffTrainData{
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
		String[] strAraVals = strAra1[1].trim().split(",JPGJP_Start,");//1st element will be # of JPDataRecs, next elements will be Data rec vals
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
				System.out.println("StraffEvntTrainData::addEventDataRecsFromRaw : Warning : Attempting to add a non-Opt event (" + ev.TypeOfData + "-type) with an empty JPgroup-keyed JP list - event had no corresponding usable data so being ignored.");
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
	protected String getRecCSVString(JpgJpDataRecord rec) {		return "JPGJP_Start,optKey,"+rec.getOptVal()+","+rec.getCSVString()+"JPGJP_End,";};

	
	
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
	
	public static int numOptAllIncidences = 0, numPosOptAllIncidences = 0, numNegOptAllIncidences = 0;
	//process JP occurence data for this event
	//passed is ref to map of all occurrences of jps in this kind of event's data for a specific prospect
	public void procJPOccForAllJps(prospectExample ex, TreeMap<Integer, jpOccurrenceData> jpOccMap, String type, boolean usesOpt) {
		for (JpgJpDataRecord rec : listOfJpgsJps) {
			int opt = rec.getOptVal();
			Integer jpg = rec.getJPG();
			ArrayList<Integer> jps = rec.getJPlist();
			//this is sentinel value marker for opt events where all jpgs/jps are specified to have same opt value
			if((jpg == -10) && (jps.get(0) == -9)) {// opt choice covering all jpgs/jps
				++numOptAllIncidences;
				if  (opt <= 0) {//this is negative opt across all records 
					++numNegOptAllIncidences;
					//from here we are processing a positive opt record across all jps
					jpOccurrenceData jpOcc = ex.getNegOptAllOccObj();
					if (jpOcc==null) {jpOcc = new jpOccurrenceData(-9, -10, usesOpt);}
					jpOcc.addOccurrence(eventDate, rec.getOptVal());		
					ex.setNegOptAllOccObj(jpOcc);					
				} else {		//this is a non-negative opt across all records
					++numPosOptAllIncidences;
					//from here we are processing a positive opt record across all jps
					jpOccurrenceData jpOcc = ex.getPosOptAllOccObj();
					if (jpOcc==null) {jpOcc = new jpOccurrenceData(-9, -10, usesOpt);}
					jpOcc.addOccurrence(eventDate, rec.getOptVal());		
					ex.setPosOptAllOccObj(jpOcc);				
				}
			} else {
				for (Integer jp : jps) {
					jpOccurrenceData jpOcc = jpOccMap.get(jp);
					if (jpOcc==null) {jpOcc = new jpOccurrenceData(jp, jpg,usesOpt);}
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

	public OrderEventTrainData(OrderEvent ev) {
		super(ev);
		addEventDataFromEventObj(ev);}	//put in child ctor in case child-event specific data needed for training	
	
	public OrderEventTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){
		super(_evIDStr, _evTypeStr, _evDateStr);
		addJPG_JPDataFromCSVString(_evntStr);	}//put in child ctor in case child-event specific data needed for training	
	
	@Override
	public void addEventDataFromEventObj(BaseRawData ev) {	super.addEventDataRecsFromRawData(FauxOptVal,ev, false);}//addEventDataFromEventObj
	@Override
	public void addJPG_JPDataFromCSVString(String _csvDataStr) {super.addJPG_JPDataRecsFromCSVStr(FauxOptVal,_csvDataStr);	}//addJPG_JPDataFromCSVString
	//get the output string holding the relevant info for an individual event record of this kind of data
	@Override
	protected String getRecCSVString(JpgJpDataRecord rec) {		return "JPGJP_Start,optKey,"+rec.getOptVal()+","+rec.getCSVString()+"JPGJP_End,";};

}//class OrderEventTrainData

class LinkEventTrainData extends StraffEvntTrainData{

	public LinkEventTrainData(LinkEvent ev) {
		super(ev);
		addEventDataFromEventObj(ev);}	//put in child ctor in case child-event specific data needed for training	
	
	public LinkEventTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){
		super(_evIDStr, _evTypeStr, _evDateStr);
		addJPG_JPDataFromCSVString(_evntStr);}	//put in child ctor in case child-event specific data needed for training		
	
	@Override
	public void addEventDataFromEventObj(BaseRawData ev) {super.addEventDataRecsFromRawData(FauxOptVal,ev, false);}//addEventDataFromEventObj
	@Override
	public void addJPG_JPDataFromCSVString(String _csvDataStr) {	super.addJPG_JPDataRecsFromCSVStr(FauxOptVal,_csvDataStr);}//addJPG_JPDataFromCSVString
	//get the output string holding the relevant info for an individual event record of this kind of data
	@Override
	protected String getRecCSVString(JpgJpDataRecord rec) {		return "JPGJP_Start,optKey,"+rec.getOptVal()+","+rec.getCSVString()+"JPGJP_End,";};
}//class LinkEventTrainData

class OptEventTrainData extends StraffEvntTrainData{
	public OptEventTrainData(OptEvent ev) {
		super(ev);		
		addEventDataFromEventObj(ev);}	//put in child ctor in case child-event specific data needed for training	
	
	public OptEventTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){
		super(_evIDStr, _evTypeStr, _evDateStr);
		addJPG_JPDataFromCSVString(_evntStr);}	//put in child ctor in case child-event specific data needed for training		
	
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
	protected String getRecCSVString(JpgJpDataRecord rec) {		return "JPGJP_Start,optKey,"+rec.getOptVal()+","+rec.getCSVString()+"JPGJP_End,";};
}//class OptEventTrainData


class SrcEventTrainData extends StraffEvntTrainData{
	public SrcEventTrainData(SourceEvent ev) {
		super(ev);		
		addEventDataFromEventObj(ev);}	//put in child ctor in case child-event specific data needed for training	
	
	public SrcEventTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){
		super(_evIDStr, _evTypeStr, _evDateStr);
		addJPG_JPDataFromCSVString(_evntStr);}	//put in child ctor in case child-event specific data needed for training		
	
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
	protected String getRecCSVString(JpgJpDataRecord rec) {		return "JPGJP_Start,srcType,"+rec.getOptVal()+","+rec.getCSVString()+"JPGJP_End,";};
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
