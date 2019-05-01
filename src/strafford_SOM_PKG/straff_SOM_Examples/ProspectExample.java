package strafford_SOM_PKG.straff_SOM_Examples;

import java.util.*;
import java.util.regex.Pattern;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_Utils_Objects.MsgCodes;
import base_Utils_Objects.Tuple;
import strafford_SOM_PKG.straff_RawDataHandling.*;

/**
 * class to manage a prospect example, either a past customer or a potential prospect
 * @author john
 *
 */

public abstract class ProspectExample extends StraffSOMExample{
	//this is to control whether we build pre-proc data from event map or from jpoccurences map TODO
	//want to change to this so that we can drastically reduce the memory footprint - 
	//shouldn't ever need to keep "eventsByDateMap" around once we have occurrence structure built
	//public static final boolean useJPOccToPreProc = true;
	//prospect last lookup date, if any specified
	public Date prs_LUDate;
	//structs to hold all event occurences of each JP for this OID, keyed by event type
	protected TreeMap<String, TreeMap<Integer, JP_OccurrenceData>> JpOccurrences;
	//may have multiple events on same date/time, map by event ID 	
	protected TreeMap<String, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>>> eventsByDateMap;	
	//sets of existing, production jps for jpgroups seen in non-prod jps (may be present in prod jps as well, in fact better be);
	protected TreeMap<Integer, TreeSet<Integer>> prodJPsForNonProdJPGroups;
	
	//this object denotes a positive or non-positive opt-all event for a user (i.e. an opts occurrence with a jp of -9)
	private JP_OccurrenceData posOptAllEventObj = null, negOptAllEventObj = null;
	
	//boolean that tells whether jpOcc struct is built or not; this is to prevent empty event data map structure from clobbering existing jpOcc map
	protected boolean jpOccNotBuiltYet;

	//this is a debugging structure to monitor how many opt all occurrences happen (where an opt is processed with a jpg of -10 and a jp of -9
	//idx 0 is all, idx 1 is # of negative opts-across-all-jps; idx 2 is # of positive opts-across-all-jps
	public static int[] _numOptAllOccs = new int[] {0,0,0};

	//build this object based on prospectData object from raw data
	public ProspectExample(SOMMapManager _map, ExDataType _type, ProspectData _prspctData) {
		super(_map,_type,_prspctData.OID);	
		prodJPsForNonProdJPGroups = new TreeMap<Integer, TreeSet<Integer>>();
		initObjsData();
		prs_LUDate = _prspctData.getDate();
		jpOccNotBuiltYet = true;
	}//prospectData ctor
	
	//build this object based on csv string - rebuild data from csv string columns 4+
	public ProspectExample(SOMMapManager _map, ExDataType _type, String _OID, String _csvDataStr) {
		super(_map,_type,_OID);		
		prodJPsForNonProdJPGroups = new TreeMap<Integer, TreeSet<Integer>>();
		initObjsData();	
		jpOccNotBuiltYet = false;		//is built directly from saved data
	}//csv string ctor
		
	//copy ctor
	public ProspectExample(ProspectExample _otr) {
		super(_otr);
		prs_LUDate = _otr.prs_LUDate;
		JpOccurrences = _otr.JpOccurrences;
		eventsByDateMap = _otr.eventsByDateMap;
		posOptAllEventObj = _otr.posOptAllEventObj;
		negOptAllEventObj = _otr.negOptAllEventObj;
		prodJPsForNonProdJPGroups = _otr.prodJPsForNonProdJPGroups;
	}//copy ctor	
	
	//return configuration of expected counts of different events as stored in this example
	protected abstract int[] _getCSVNumEvsAra(String[] dataAra);
	
	//instancing class needs to have this
	protected void initObjsData() {
		rad = 3.0f;			//for display
		//occurrence structures - keyed by type, then by JP
		JpOccurrences = new TreeMap<String, TreeMap<Integer, JP_OccurrenceData>> ();
		eventsByDateMap = new TreeMap<String, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>>>();
		_initObjsIndiv();
	}//initObjsData
	
//	//build data from csv record for type of event
//	protected final void buildEventTrainDataFromCSVStr(String evntType, int numEvents, String allEventsStr, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> mapToAdd, EvtDataType type) {
//		//String evntType = eventMapTypeKeys[type.getVal()];
//		//int numEventsToShow = 0; boolean dbgOutput = false;
//		String [] allEventsStrAra = allEventsStr.trim().split("EvSt,");
//		//will have an extra entry holding OPT key
//		if(allEventsStrAra.length != (numEvents+1)) {//means not same number of event listings in csv as there are events counted in original event list - shouldn't be possible
//			msgObj.dispMessage("prospectExample","buildEventTrainDataFromCSVStr","Error building train data from csv file : " + evntType + " Event : "+allEventsStr + " string does not have expected # of events : " +allEventsStrAra.length + " vs. expected :" +numEvents,MsgCodes.error1 );
//			return;
//		}
////		if ((type == EvtDataType.Order) &&(numEvents > numEventsToShow) && dbgOutput) {
////			System.out.println("type : " +evntType  +" | AllEventsStr : " + allEventsStr );
////		}
//		for (String eventStr : allEventsStrAra) {
//			if (eventStr.length() == 0 ) {continue;}
//			String[] eventStrAra = eventStr.trim().split(",");
//			String evType = eventStrAra[1];
//			Integer evID = Integer.parseInt(eventStrAra[3]);
//			String evDateStr = eventStrAra[5];
//			//need to print out string to make sure that there is only a single instance of every event id in each record in csv files
//			StraffEvntTrainData newEv = buildNewTrainDataFromStr(evID, evType, evDateStr, eventStr, type);
////			if ((type == EvtDataType.Opt) &&(numEvents > numEventsToShow) && dbgOutput) {
////				System.out.println("\tEvent : " + newEv.toString());
////			}
//			Date addDate = newEv.getEventDate();			
//			
//			TreeMap<Integer, StraffEvntTrainData> eventsOnDate = mapToAdd.get(addDate);
//			if(null == eventsOnDate) {eventsOnDate = new TreeMap<Integer, StraffEvntTrainData>();}
//			StraffEvntTrainData tmpEvTrainData = eventsOnDate.get(evID);
//			if(null != tmpEvTrainData) {msgObj.dispMessage("prospectExample","buildEventTrainDataFromCSVStr","Possible issue : event being written over : old : "+ tmpEvTrainData.toString() + "\n\t replaced by new : " + newEv.toString(),MsgCodes.warning2 );}
//			
//			eventsOnDate.put(evID, newEv);
//			mapToAdd.put(addDate, eventsOnDate);
//		}		
//	}//buildEventTrainDataFromCsvStr
	
	//build jpOccurrence struct from type-specific occurrence record
	//example of csv string
	//pr_000000331,2016-11-21 16:15:51,0,1,7,OPT|,LNK|,Occ_St,364,4,0,DtOccSt,2012-04-25 15:59:14,1,3,2,DtOccEnd,DtOccSt,2016-02-13 07:49:31,1,3,1,DtOccEnd,Occ_End,SRC|,Occ_St,69,4,1,DtOccSt,2007-09-21 21:53:32,1,11,1,DtOccEnd,DtOccSt,2009-02-06 05:53:38,1,57,1,DtOccEnd,Occ_End,Occ_St,131,4,1,DtOccSt,2009-02-06 05:53:49,1,57,1,DtOccEnd,DtOccSt,2017-10-03 03:07:09,1,92,1,DtOccEnd,Occ_End,Occ_St,227,20,1,DtOccSt,2010-01-04 22:22:49,1,41,1,DtOccEnd,Occ_End,Occ_St,231,64,1,DtOccSt,2010-03-05 01:49:47,1,41,1,DtOccEnd,Occ_End,Occ_St,232,8,1,DtOccSt,2009-12-18 23:15:20,1,41,1,DtOccEnd,Occ_End,Occ_St,237,1,1,DtOccSt,2009-12-18 23:15:03,1,41,1,DtOccEnd,Occ_End,Occ_St,274,64,1,DtOccSt,2017-10-03 03:07:09,1,92,1,DtOccEnd,Occ_End,
	//jpOccType : string name of type of occurrence data
	//csvJPOccData : string of all occurrrence csv data for this type : example : Occ_St,129,19,1,DtOccSt,2017-02-16 20:48:37,1,30,1,DtOccEnd,Occ_End,Occ_St,395,19,1,DtOccSt,2019-03-30 23:05:31,1,65,1,DtOccEnd,Occ_End,Occ_St,397,19,1,DtOccSt,2018-11-11 00:26:01,1,65,1,DtOccEnd,Occ_End,
	protected final void buildJPOccTrainDataFromCSVStr(String jpOccType, int numOccs, String csvJPOccData) {
		//build new occurrence map of this type if necessary		
		TreeMap<Integer, JP_OccurrenceData> jpOccs = JpOccurrences.get(jpOccType);
		if(jpOccs == null) {jpOccs = new TreeMap<Integer, JP_OccurrenceData>();JpOccurrences.put(jpOccType, jpOccs);}
		String [] allOccsData = csvJPOccData.trim().split(JP_OccurrenceData.occRecStTag);
		//String dispString = "jpOccType : " + jpOccType + " | # occs : " + numOccs +" | csvJPOccData Str :"+csvJPOccData+"\n";
		if(allOccsData[0].trim().length() != 0) {msgObj.dispMessage("prospectExample","buildJPOccTrainDataFromCSVStr","Issue with csvJPOccData : " +csvJPOccData+" being incorrect format.",MsgCodes.warning2 );}
		for(int i=1;i<allOccsData.length;++i) {
			JP_OccurrenceData occ = new JP_OccurrenceData(allOccsData[i]);
			jpOccs.put(occ.jp, occ);		
			//dispString += "\tallOccsData["+i+"] : " + allOccsData[i]+  " | occ obj : " + occ.toString() + "\n";
		}			
	}//buildJPOccTrainDataFromCSVStr
	
	@Override
	//standardize this feature vector - across each feature, set value to be between 0 and 1
	protected void buildStdFtrsMap() {		
		if (allNonZeroFtrIDXs.size() > 0) {ftrMaps[stdFtrMapTypeKey] = calcStdFtrVector(ftrMaps[ftrMapTypeKey], allNonZeroFtrIDXs, mapMgr.getTrainFtrMins(), mapMgr.getTrainFtrDiffs());}
		else {ftrMaps[stdFtrMapTypeKey] = new TreeMap<Integer, Float>();}
		setFlag(stdFtrsBuiltIDX,true);
	}//buildStdFtrsMap
	

	//build occurence structure for type of events in this data, including aggregating build-later opt event data
	private void _buildIndivOccStructs(String key, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> mapByDate, boolean usesOpt ) {
		TreeMap<Integer, JP_OccurrenceData> occs = new TreeMap<Integer, JP_OccurrenceData>();
		for (TreeMap<Integer, StraffEvntTrainData> map : mapByDate.values()) {
			for (StraffEvntTrainData ev : map.values()) {ev.procJPOccForAllJps(this, occs, key,usesOpt,_numOptAllOccs);}			
		}
		JpOccurrences.put(key, occs);
	}//_buildIndivOccStructs
	//build occurence structures based on mappings - must be called once mappings are completed but before the features are built
	//feature vec is built from occurrence structure
	protected void buildOccurrenceStructs(String[] eventMapTypeKeys, boolean[] jpOccMapUseOccData) { // should be executed when finished building all xxxEventsByDateMap(s)
		//occurrence structures - map keyed by event type of map of 
		JpOccurrences = new TreeMap<String, TreeMap<Integer, JP_OccurrenceData>> ();
		//for orders and opts, pivot structure to build map holding occurrence records keyed by jp - must be done after all events are aggregated for each prospect
		//for every date, for every event, aggregate occurences		
		for(int i=0;i<eventMapTypeKeys.length;++i) {
			String key = eventMapTypeKeys[i];
			_buildIndivOccStructs(key, eventsByDateMap.get(key), jpOccMapUseOccData[i]);	
		}
		//for (String key : eventMapTypeKeys) {_buildIndivOccStructs(key, eventsByDateMap.get(key));			}		
	}//buildOccurrenceStructs
	
	//provide shallow copy of jpOcc struct - only copying passed event type key values
	protected TreeMap<String, TreeMap<Integer, JP_OccurrenceData>> copyJPOccStruct(String[] eventMapTypeKeys) {
		TreeMap<String, TreeMap<Integer, JP_OccurrenceData>> tmpJpOccurrences = new TreeMap<String, TreeMap<Integer, JP_OccurrenceData>> ();
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
	//public final String getRawDescrForCSV() {	return useJPOccToPreProc ? getRawDescrForCSV_JPOcc() : getRawDescrForCSV_Event() ;}//getRawDescrForCSV()
	public final String getRawDescrForCSV() {	return getRawDescrForCSV_JPOcc();}//getRawDescrForCSV()
	
//	//build off event map data 
//	private String getRawDescrForCSV_Event() {
//		//first build prospect data
//		String dateStr = BaseRawData.buildStringFromDate(prs_LUDate);
//		String res = ""+OID+","+dateStr+",";
//		String[] eventMapTypeKeys = getEventMapTypeKeys();
//		for (String key : eventMapTypeKeys) {
//			res += getSizeOfDataMap(eventsByDateMap.get(key))+",";
//		}
//		//res += getSizeOfDataMap(orderEventsByDateMap)+"," + getSizeOfDataMap(optEventsByDateMap)+",";
//		//now build res string for all event data objects
//		String[] CSVSentinelLbls = getCSVSentinelLbls();
//		for(int i=0;i<eventMapTypeKeys.length;++i) {
//			String key = eventMapTypeKeys[i];
//			res += CSVSentinelLbls[i];
//			TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> eventsByDate = eventsByDateMap.get(key);
//			for (Date date : eventsByDate.keySet()) {			res += buildEventCSVString(date, eventsByDate.get(date));		}				
//		}		
//		return res;		
//	}//getRawDescrForCSV_Event()
	
	//build off occurence structure - doing this to try to save memory space
	private String getRawDescrForCSV_JPOcc() {
		//first build prospect data
		String dateStr = BaseRawData.buildStringFromDate(prs_LUDate);
		String res = ""+OID+","+dateStr+",";
		String[] occMapTypeKeys = getEventMapTypeKeys();
		//get size of each type of occurrence structure
		for (String key : occMapTypeKeys) {	res += JpOccurrences.get(key).size()+",";	}
		String[] CSVSentinelLbls = getCSVSentinelLbls();
		for(int i=0;i<occMapTypeKeys.length;++i) {
			String key = occMapTypeKeys[i];
			res += CSVSentinelLbls[i];		//says start of type of occurrences
			TreeMap<Integer, JP_OccurrenceData> occs = JpOccurrences.get(key);
			for(JP_OccurrenceData occ : occs.values()) {		res += occ.toCSVString();	}//for each occurrence of type key\
		}
		return res;		
	}//getRawDescrForCSV_JPOcc
	
	//return all jpg/jps in this prospect record 
	public HashSet<Tuple<Integer,Integer>> getSetOfAllJpgJpData(){
		HashSet<Tuple<Integer,Integer>> res = new HashSet<Tuple<Integer,Integer>>();
		for(TreeMap<Integer, JP_OccurrenceData> occs : JpOccurrences.values()) {for (JP_OccurrenceData occ :occs.values()) {res.add(occ.getJpgJp());}	}
		return res;
	}///getSetOfAllJpgJpData
	
	//return occurence map
	public TreeMap<Integer, JP_OccurrenceData> getOcccurenceMap(String key) {
		TreeMap<Integer, JP_OccurrenceData> res = JpOccurrences.get(key);
		if (res==null) {msgObj.dispMessage("ProspectExample","getOcccurenceMap","JpOccurrences map does not have key : " + key, MsgCodes.warning2); return null;}
		return res;
	}
	
	//remove occurences with passed jp
	public void removeAllJPOccs(Integer jp) {
		for(TreeMap<Integer, JP_OccurrenceData> jpOccType : JpOccurrences.values()) {
			jpOccType.put(jp, null);
		}
	}//removeAllJPOccs
	
    //these set/get this StraffSOMExample's object that denotes a positive or non-positive opt setting for all jps
	public JP_OccurrenceData getPosOptAllOccObj() {		return posOptAllEventObj;	}
	public void setPosOptAllOccObj(JP_OccurrenceData _optAllOcc) {posOptAllEventObj = _optAllOcc;	}
		
	public JP_OccurrenceData getNegOptAllOccObj() {		return negOptAllEventObj;	}
	public void setNegOptAllOccObj(JP_OccurrenceData _optAllOcc) {negOptAllEventObj = _optAllOcc;	}

	
	//whether this record has any information to be used to train - presence of prospect jpg/jp can't be counted on
	//isBadExample means resulting ftr data is all 0's for this example.  can't learn from this, so no need to keep it.
	public final boolean hasNonSourceEvents() {return !getFlag(isBadTrainExIDX) && (hasRelevantTrainingData());}		
	//whether this record should be used as validation record - if it has no past order events, but does have source data or other events 
	
	public final boolean isTrueProspect() {	
		return  ((JpOccurrences.get("orders").size() == 0) && ((JpOccurrences.get("sources").size() > 0) || (hasRelevantTrainingData())));
	}//isTrueProspect()	
	
	//whether this record has any source events specified - if no other events then this record would be a legitimate validation record (a true prospect)
	public final boolean hasOnlySourceEvents() {	return ((!hasRelevantTrainingData()) && (JpOccurrences.get("sources").size() > 0));}

	//instancing class specific new object initialization
	protected abstract void _initObjsIndiv();

	//if this prospect has any events
	public boolean hasEventData() {
		for (String key : eventsByDateMap.keySet()) {if (eventsByDateMap.get(key).size() > 0) {return true;}	}
		return false;
	}
	
	//this prospect is an actual customer - use as training data
	public abstract boolean isTrainablePastCustomer();
	//get status of a particular jp
	public abstract boolean[] hasJP(Integer _jp);
	
	public abstract boolean[] getExampleStatusOcc();
	
	//return boolean array describing this example's eventsByDate structure -should match occurence structure
	//idxs : 0==if in an order; 1==if in an opt, 2 == if is in link, 3==if in source event,4 if in any non-source event (denotes action by customer), 5 if in any event, including source, 
	//needs to follow format of eventMapTypeKeys
	public final boolean[] getExampleStatusEvt() {
		boolean	has_ordr = ((eventsByDateMap.get("orders") != null) && (eventsByDateMap.get("orders").size() !=0)) ,
				has_opt = (eventsByDateMap.get("opts").size() !=0),
				has_link = (eventsByDateMap.get("links").size() !=0),	
				has_source = (eventsByDateMap.get("sources").size() !=0),			//do not treat source data as an event in this case
				has_event = has_ordr || has_opt || has_link;					//not counting source data
		boolean[] res = new boolean[] {has_ordr, has_opt, has_link, has_source,has_event};
		return res;		
	}//check if JP exists, and where

	//returns true if -any training-related- events are present in this record (i.e. not counting source "events")
	protected abstract boolean hasRelevantTrainingData();

//	//read in data from record
//	protected final void buildDataFromCSVString_event(int[] numEvntsAra, String _csvDataStr, String[] _eventMapTypeKeys, String[] _CSVSentinelLbls) {
//		//each type of event list exists between the sentinel flag and the subsequent sentinel flag
//		for (int i = 0; i<numEvntsAra.length;++i) {
//			if (numEvntsAra[i] > 0) {
//				String key = _eventMapTypeKeys[i];
//				String stSentFlag = _CSVSentinelLbls[i];
//				String endSntnlFlag = (i <_eventMapTypeKeys.length-1 ? _CSVSentinelLbls[i+1] : null );
//				String [] strAraBegin = _csvDataStr.trim().split(Pattern.quote(stSentFlag)); //idx 1 holds all event data	
//				String strBegin = (strAraBegin.length < 2) ? "" : strAraBegin[1];
//				String strEvents = (endSntnlFlag != null ? strBegin.trim().split(Pattern.quote(endSntnlFlag))[0] : strBegin).trim(); //idx 0 holds relevant data
//				buildEventTrainDataFromCSVStr(key, numEvntsAra[i],strEvents,eventsByDateMap.get(key), EvtDataType.getVal(i));				
//			}			
//		}
//	}//buildDataFromCSVString_event	
	//read in data from record
	
	//example of csv string
	//pr_000000331,2016-11-21 16:15:51,0,1,7,OPT|,LNK|,Occ_St,364,4,0,DtOccSt,2012-04-25 15:59:14,1,3,2,DtOccEnd,DtOccSt,2016-02-13 07:49:31,1,3,1,DtOccEnd,Occ_End,SRC|,Occ_St,69,4,1,DtOccSt,2007-09-21 21:53:32,1,11,1,DtOccEnd,DtOccSt,2009-02-06 05:53:38,1,57,1,DtOccEnd,Occ_End,Occ_St,131,4,1,DtOccSt,2009-02-06 05:53:49,1,57,1,DtOccEnd,DtOccSt,2017-10-03 03:07:09,1,92,1,DtOccEnd,Occ_End,Occ_St,227,20,1,DtOccSt,2010-01-04 22:22:49,1,41,1,DtOccEnd,Occ_End,Occ_St,231,64,1,DtOccSt,2010-03-05 01:49:47,1,41,1,DtOccEnd,Occ_End,Occ_St,232,8,1,DtOccSt,2009-12-18 23:15:20,1,41,1,DtOccEnd,Occ_End,Occ_St,237,1,1,DtOccSt,2009-12-18 23:15:03,1,41,1,DtOccEnd,Occ_End,Occ_St,274,64,1,DtOccSt,2017-10-03 03:07:09,1,92,1,DtOccEnd,Occ_End,
	//build data directly into jpOcc structure - takes up a lot less space
	protected final void buildDataFromCSVString_jpOcc(int[] numOccsAra, String _csvDataStr, String[] _eventMapTypeKeys, String[] _CSVSentinelLbls) {
		//each type of event list exists between the sentinel flag and the subsequent sentinel flag
		for (int i = 0; i<numOccsAra.length;++i) {
			if (numOccsAra[i] > 0) {
				String key = _eventMapTypeKeys[i];
				String stSentFlag = _CSVSentinelLbls[i];
				String endSntnlFlag = (i <_eventMapTypeKeys.length-1 ? _CSVSentinelLbls[i+1] : null );
				String [] strAraBegin = _csvDataStr.trim().split(Pattern.quote(stSentFlag)); //idx 1 holds all event data	
				String strBegin = (strAraBegin.length < 2) ? "" : strAraBegin[1];
				String strOccs = (endSntnlFlag != null ? strBegin.trim().split(Pattern.quote(endSntnlFlag))[0] : strBegin).trim(); //idx 0 holds relevant data
				buildJPOccTrainDataFromCSVStr(key, numOccsAra[i], strOccs);				
			}			
		}
	}//buildDataFromCSVString_jpOcc
	
	//build the lists of jps found in products and the other jps that have no product
	protected final void buildJPListsAndSetBadExample() {
		HashSet<Tuple<Integer,Integer>> alljpgjps = new HashSet<Tuple<Integer,Integer>>();
		for(TreeMap<Integer, JP_OccurrenceData> occs : JpOccurrences.values()) {
			for (Integer jp :occs.keySet()) {
				if (jp == -9) {continue;}//ignore -9 jp occ map - this is an entry to denote a positive opt - shouldn't be here anymore but just in case
				JP_OccurrenceData occ = occs.get(jp);
				alljpgjps.add(occ.getJpgJp());
			}	
		}
		if(alljpgjps.size() == 0) {setIsBadExample(true);		}//means there's no valid jps in this record's occurence data
		//build allprodJps from allJps
		allProdJPs = new HashSet<Integer>();
		nonProdJpgJps = new HashSet<Tuple<Integer,Integer>>();
		prodJPsForNonProdJPGroups = new TreeMap<Integer, TreeSet<Integer>>();
		for(Tuple<Integer,Integer> jpgJp : alljpgjps) {
			Integer jp = jpgJp.y;
			Integer jpIDX = jpJpgMon.getFtrJpToIDX(jp);
			if(jpIDX==null) {
				nonProdJpgJps.add(jpgJp); 
				prodJPsForNonProdJPGroups.put(jpgJp.x, jpJpgMon.getProdJPsforSpecifiedJpgrp(jpgJp.x));
			} else {allProdJPs.add(jp);}
		}		
	}//buildJPListsAndSetBadExample

	protected String toStringDateMap(TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> map, String mapName) {
		String res = "\n# of " + mapName + " in Date Map (count of unique dates - multiple " + mapName + " per same date possible) : "+ map.size()+"\n";		
		for (Date dat : map.keySet() ) {
			res+="Date : " + dat+"\n";
			TreeMap<Integer, StraffEvntTrainData> evMap = map.get(dat);
			for (StraffEvntTrainData ev : evMap.values()) {	res += ev.toString();	}
		}		
		return res;
	}//toStringDateMap	
	
	protected String toStringOptOccMap(TreeMap<Integer, JP_OccurrenceData> map, String mapName) {
		String res = "\n# of jp occurences in " + mapName + " : "+ map.size()+"\n";	
		for (Integer jp : map.keySet() ) {
			JP_OccurrenceData occ = map.get(jp);
			res += occ.toString();			
		}	
		return res;		
	}//toStringOptOccMap

		
}//class prospectExample