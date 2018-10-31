package SOM_Strafford_PKG;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * This class will hold the relevant data acquired from the db to build a datapoint used by the SOM
 * It will take raw data and build the appropriate feature vector, using the appropriate calculation 
 * to weight the various jpgroups/jps appropriately.  The ID of this construct should be such that it 
 * can be uniquely qualified/indexed by it and will either be the ID of a particular prospect in the 
 * prospect database or some other unique identifier if this is representing, say, a target product
 * 
 * @author john
 *
 */
public abstract class StraffSOMExample extends baseDataPtVis{	
	protected static SOMMapData mapData;
	//corresponds to OID in prospect database - primary key of all this data is OID in prospects
	public final String OID;	
	
	//prefix to use for product example IDs
	protected static final String IDprfx = "EXAMPLE";	
	//vector of features and standardized (0->1) features for this Example
	//private float[] ftrData, stdFtrData, normFtrData;
	
	//use a map to hold only sparse data frmt
	public TreeMap<Integer, Float> ftrMap, stdFtrMap, normFtrMap;
	
	//designate whether feature vector built or not
	protected boolean ftrsBuilt, stdFtrsBuilt, normFtrsBuilt;
	///if all feature values == 0 then this is a useless example for training. only set upon feature vector calc
	protected boolean isBadTrainExample;
	
	//magnitude of this feature vector
	public float ftrVecMag;
	
	//all jps seen in all occurrence structures - NOT IDX IN FEATURE VECTOR!
	public HashSet<Integer> allJPs;
	//idx's in feature vector that have non-zero values
	public ArrayList<Integer> allJPFtrIDXs;
	
	/////////////////////////////
	// from old DataPoint data
	
	//reference to map node that best matches this node
	protected SOMMapNodeExample bmu;			
	protected dataClass label;
	
	public StraffSOMExample(SOMMapData _map, String _id) {
		super();
		mapData=_map;
		OID = _id;
		ftrsBuilt = false;
		stdFtrsBuilt = false;
		normFtrsBuilt = false;
		isBadTrainExample = false;	
		label = new dataClass(OID,"lbl:"+OID,"unitialized Description", null);
		label.clrVal = new int[] {255,0,0,255};

		
	}//ctor

	//build feature vector
	protected abstract void buildFeaturesMap();	
	//standardize this feature vector stdFtrData
	protected abstract void buildStdFtrsMap();
	//required info for this example to build feature data - use this so we don't have to reload data ever time
	public abstract String getRawDescrForCSV();	
	//column names of rawDescrForCSV data
	public abstract String getRawDescColNamesForCSV();
	//finalization after being loaded from baseRawData or from csv record
	public abstract void finalizeBuild();
	
	public boolean isBadExample() {return isBadTrainExample;}
	public void setIsBadExample(boolean val) { isBadTrainExample=val;}
	
	protected StraffEvntTrainData buildNewTrainDataFromEv(EventRawData _optObj, int type) {
		switch (type) {
		case 0 :{			return new OrderEventTrainData((OrderEvent) _optObj);	}//order event object
		case 1 : {			return new OptEventTrainData((OptEvent) _optObj);}//opt event  object
		default : {			return new OrderEventTrainData((OrderEvent) _optObj);	}//default to order event object - probably will fail.  need to make sure type is accurately specified.
		}//switch
	}//buildNewTrainData
	
	protected StraffEvntTrainData buildNewTrainDataFromStr(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr, int type) {
		switch (type) {
		case 0 :{			return new OrderEventTrainData(_evIDStr, _evTypeStr, _evDateStr, _evntStr);	}//order event object
		case 1 : {			return new OptEventTrainData(_evIDStr, _evTypeStr, _evDateStr, _evntStr);}//opt event  object
		default : {			return new OrderEventTrainData(_evIDStr, _evTypeStr, _evDateStr, _evntStr);	}//default to order event object - probably will fail.  need to make sure type is accurately specified.
		}//switch
	}//buildNewTrainData	
	
	//add object keyed by addDate, either adding to existing list or building a new list if none exists
	protected void addDataToTrainMap(EventRawData _optObj, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> map, int type){
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
	
	//debugging tool to find issues behind occasional BMU seg faults
	protected boolean checkForErrors(SOMMapNodeExample _n, float[] dataVar){
		boolean inError = false;
		if(mapData == null){						System.out.println("SOMMapNodeExample::checkForErrors : FATAL ERROR : SOMMapData object is null!");		return true;}//if mapdata is null then stop - should have come up before here anyway
		if(_n==null){							mapData.dispMessage("SOMMapNodeExample::checkForErrors : _n is null!");		inError=true;} 
		else if(_n.mapLoc == null){				mapData.dispMessage("SOMMapNodeExample::checkForErrors : _n has no maploc!");	inError=true;}
		if(dataVar == null){					mapData.dispMessage("SOMMapNodeExample::checkForErrors : map variance not calculated : datavar is null!");	inError=true;	}
		return inError;
	}//checkForErrors
	
	public void setBMU(SOMMapNodeExample _n, float[] dataVar){
		if (checkForErrors(_n, dataVar)) {return;}
		bmu = _n;	
		mapLoc.set(_n.mapLoc);
		double dist = mapData.dpDistFunc(_n, this,dataVar);
		_n.addBMUExample(dist, this);
	}//setBMU
	
	//build the SOM datapoint values used to train the SOM - features should be set and scaled and/or normed by here if appropriate
	public void buildSOMDataPoint() {
		if(label==null){label = new dataClass(OID,"unitialized label","unitialized Description", null);}
		bmu = null;
		//map.dispMessage("Building DATA POINT chugga : "+OID);
		
	}//buildSOMDataPoint	

	//build feature vector - call externally after finalize
	public void buildFeatureVector() {//allJPs must exist by here
		allJPFtrIDXs = new ArrayList<Integer>();
		for(Integer jp : allJPs) {allJPFtrIDXs.add(mapData.jpToFtrIDX.get(jp));}		
		buildFeaturesMap();
		ftrsBuilt = true;		
		buildNormFtrData();
		normFtrsBuilt = true;
	}//buildFeatureVector
	//build structures that require that the feature vector be built before hand
	public void buildPostFeatureVectorStructs() {
		buildStdFtrsMap();
		stdFtrsBuilt = true;
	}//buildPostFeatureVectorStructs

	//return a hash set of the jps represented by non-zero values in this object's feature vector.
	//needs to be governed by some threshold value
	protected HashSet<Integer> buildJPsFromFtrAra(float[] _ftrAra, float thresh){
		HashSet<Integer> jps = new HashSet<Integer>();
		for (int i=0;i<_ftrAra.length;++i) {
			Float ftr = ftrMap.get(i);
			if ((ftr!= null) && (ftr > thresh)) {jps.add(mapData.jpByIdx[i]);			}
		}
		return jps;	
	}//buildJPsFromFtrVec
	
	//build normalized vector of data - only after features have been set
	private void buildNormFtrData() {
		if(!ftrsBuilt) {mapData.dispMessage("OID : " + OID + " : Features not built, cannot normalize feature data");return;}
		normFtrMap=new TreeMap<Integer, Float>();
		if(this.ftrVecMag == 0) {return;}
		for (Integer IDX : allJPFtrIDXs) {normFtrMap.put(IDX,ftrMap.get(IDX)/this.ftrVecMag);}	
	}//buildNormFtrData
	
	public dataClass getLabel(){return label;}
	
	private String _toCSVString(TreeMap<Integer, Float> ftrs) {
		String res = ""+OID+",";
		for(int i=0;i<mapData.numFtrs;++i){
			Float ftr = ftrs.get(i);			
			res += String.format("%1.7g", (ftr==null ? 0 : ftr)) + ",";
			}
		return res;}
	//return csv string of this object's features, depending on which type is selected - check to make sure 2ndary features exist before attempting to build data strings
	public String toCSVString(int _type) {
		switch(_type){
			case 0 : {return _toCSVString(ftrMap); }
			case 1 : {return _toCSVString(normFtrsBuilt ? normFtrMap : ftrMap);}
			case 2 : {return _toCSVString(stdFtrsBuilt ? stdFtrMap : ftrMap); }
			default : {return _toCSVString(ftrMap); }
		}
	}//toCSVString

	private String _toLRNString(TreeMap<Integer, Float> ftrs, String sep) {
		String res = ""+OID+sep;
		for(int i=0;i<mapData.numFtrs;++i){
			Float ftr = ftrs.get(i);			
			res += String.format("%1.7g", (ftr==null ? 0 : ftr)) + sep;
		}
		return res;}	
	//return LRN-format (dense) string of this object's features, depending on which type is selected - check to make sure 2ndary features exist before attempting to build data strings
	public String toLRNString(int _type, String sep) {
		switch(_type){
			case 0 : {return _toLRNString(ftrMap, sep); }
			case 1 : {return _toLRNString(normFtrsBuilt ? normFtrMap : ftrMap, sep);}
			case 2 : {return _toLRNString(stdFtrsBuilt ? stdFtrMap : ftrMap, sep); }
			default : {return _toLRNString(ftrMap, sep); }
		}		
	}//toLRNString
	
	//for (Integer jpIdx : allJPFtrIDXs) {res += ""+jpIdx+":"+ftrs[jpIdx]+" ";}
	private String _toSVMString(TreeMap<Integer, Float> ftrs) {
		String res = "";
		for (Integer jpIdx : allJPFtrIDXs) {res += "" + jpIdx + ":" + String.format("%1.7g", ftrs.get(jpIdx)) + " ";}
		return res;}//_toSVMString
	
	//return LRN-format (dense) string of this object's features, depending on which type is selected - check to make sure 2ndary features exist before attempting to build data strings
	public String toSVMString(int _type) {
		switch(_type){
			case 0 : {return _toSVMString(ftrMap); }
			case 1 : {return _toSVMString(normFtrsBuilt ? normFtrMap : ftrMap);}
			case 2 : {return _toSVMString(stdFtrsBuilt ? stdFtrMap : ftrMap); }
			default : {return _toSVMString(ftrMap); }
		}		
	}//toLRNString

	private float[] _getFtrsFromMap(TreeMap<Integer, Float> ftrMap) {
		float[] ftrs = new float[mapData.numFtrs];
		for (Integer ftrIdx : ftrMap.keySet()) {ftrs[ftrIdx]=ftrMap.get(ftrIdx);		}
		return ftrs;
	}
	
	//build feature vector on demand
	public float[] getFtrs() {return _getFtrsFromMap(this.ftrMap);}
	//build stdfeature vector on demand
	public float[] getStdFtrs() {return _getFtrsFromMap(this.stdFtrMap);}
	//build normfeature vector on demand
	public float[] getNormFtrs() {return _getFtrsFromMap(this.normFtrMap);}
	
	//return map of features unmodified
	public TreeMap<Integer, Float> getFtrMap() {return (this.ftrMap);}
	//return map of std features
	public TreeMap<Integer, Float> getStdFtrMap() {return (this.stdFtrMap);}
	//return map of norm features
	public TreeMap<Integer, Float> getNormFtrMap() {return (this.normFtrMap);}

	
	protected TreeMap<Integer, Float> buildMapFromAra(float[] ara, float thresh) {
		TreeMap<Integer, Float> ftrs = new TreeMap<Integer, Float>();
		for (int i=0;i<ara.length;++i) {if(ara[i]> thresh) {ftrs.put(i, ara[i]);}}	
		return ftrs;
	}
	
	private String dispFtrs(TreeMap<Integer, Float> ftrs) {
		String res = "";
		if((ftrs==null) || (ftrs.size() == 0)){res+=" None\n";} 
		//else {res +="\n\t";for(int i=0;i<ftrs.length;++i){res += ""+String.format("%03d", i) +":"+ String.format("%1.4g", ftrs[i]) + " | "; if((numFtrs > 40) && ((i+1)%30 == 0)){res +="\n\t";}}}
		else {res +="\n\t";for(int i=0;i<mapData.numFtrs;++i){
			Float ftr = ftrs.get(i);
			res += String.format("%1.4g",  (ftr==null ? 0 : ftr)) + " | "; if((mapData.numFtrs > 40) && ((i+1)%30 == 0)){res +="\n\t";}}}
		return res;
	}

	@Override
	public String toString(){
		String res = "Example OID# : "+OID+ (  "" != OID ? " Dense format lrnID : " + OID + "\t" : "" ) + (null == label ?  "Unknown DataClass\t" : "DataClass : " + label.toString() +"\t");
		if(null!=mapLoc){res+="Location on SOM map : " + mapLoc.toStrBrf();}
		if (mapData.numFtrs > 0) {
			res += "\nUnscaled Features (" +mapData.numFtrs+ " ) :";
			res += dispFtrs(ftrMap);
			res +="Scaled Features : ";
			res += dispFtrs(stdFtrMap);
			res +="Normed Features : ";
			res += dispFtrs(normFtrMap);
		}
		return res;
	}
}//straffSOMExample 


/**
 * 	this class holds a prospect example, that will either be used to generate 
 * training or testing data for the SOM, or else will be queried against the SOM
 * @author john
 *
 */
class ProspectExample extends StraffSOMExample{
	//labels for csv to denote the beginning of a particular event section
	public static final String orderCSVSentinelLbl = "ORD|,";
	public static final String optCSVSentinelLbl = "OPT|,";
	
	//column names for csv of this SOM example
	private static final String csvColDescrPrfx = "OID,Prospect_LU_Date,Prospect_JPG,Prospect_JP,Num Order Event Dates,Num Opt Event Dates";
	
	//////////////////////////////////////
	//Training data description
	//////////////////////////////////////
	
    //prospect job practice and job practice group if any specified
	public int prs_JPGrp = 0, prs_JP = 0;
	//prospect last lookup date, if any specified
	public Date prs_LUDate;
	//object used by all training/testing data examples to determine the actual feature vector used to interact with the SOM
	protected static StraffWeightCalc wtCalc;		
	//may have multiple events on same date/time, map by event ID 
	private TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> orderEventsByDateMap, optEventsByDateMap; //add link objs eventually	

	//structs to hold all order occurences and opt occurences of each JP for this OID
	public TreeMap<String, TreeMap<Integer, jpOccurrenceData>> JpOccurrences;//orderJpOccurrences, optJpOccurrences;
	//list of jpgjp records from opt records that need to be rebuilt once all jp presences have been specified.
	//these records denote positive opt choices by the user applying to -all- jps.
	//private TreeMap<String, ArrayList<JpgJpDataRecord>> mapOfListsOfJpgsJpsToRebuild;
	private TreeMap<String, TreeMap<Date,Integer>> mapOfListsOfJpgsJpsToRebuild;
	
	//build this object based on prospect object
	public ProspectExample(SOMMapData _map,prospectData _prspctData) {
		super(_map,_prspctData.OID);	
		if( _prspctData.rawJpMapOfArrays.size() > 0) {
			prs_JPGrp = _prspctData.rawJpMapOfArrays.firstKey();
			prs_JP = _prspctData.rawJpMapOfArrays.get(prs_JPGrp).get(0);
		}
		prs_LUDate = _prspctData.getDate();
		initObjsData() ;
	}//prospectData ctor
	
	//build this object based on csv string - rebuild data from csv string columns 4+
	public ProspectExample(SOMMapData _map,String _OID, String _csvDataStr) {
		super(_map,_OID);		
		String[] dataAra = _csvDataStr.split(",");
		//idx 0 : OID; idx 1,2, 3 are date, prspct_JPG, prsPct_JP
		prs_LUDate = BaseRawData.buildDateFromString(dataAra[1]);
		prs_JPGrp = Integer.parseInt(dataAra[2]);
		prs_JP = Integer.parseInt(dataAra[3]);
		//System.out.println("_csvDataStr : " + _csvDataStr+"\n\t|prs_LUDate : " +prs_LUDate + "|prs_JPGrp "+prs_JPGrp+"|prs_JP "+ prs_JP);
		int numOrderEvs = Integer.parseInt(dataAra[4]);
		int numOptEvs = Integer.parseInt(dataAra[5]);
//		if ((numOrderEvs == 0) || (numOptEvs == 0)){
//			System.out.println("numOrderEvs : " +numOrderEvs + "|numOptEvs : "+numOptEvs+ "|_csvDataStr : " + _csvDataStr);
//		}
		initObjsData();	
		//Build data here from csv strint
		buildDataFromCSVString(numOrderEvs,numOptEvs, _csvDataStr);		
	}//csv string ctor
	
	//build a single type of event's training data from Csv string
	private void buildEventTrainDataFromCSVStr(int numEvents, String allEventsStr, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> mapToAdd, int type) {
		int numEventsToShow = 0; boolean dbgOutput = false;
		String [] allEventsStrAra = allEventsStr.trim().split("EvSt,");
		//will have an extra entry holding OPT key
		if(allEventsStrAra.length != (numEvents+1)) {//means not same number of event listings in csv as there are events counted in original event list - shouldn't be possible
			System.out.println("buildEventTrainDataFromCsvStr : Error building train data from csv file : " + (type==0 ? "Order" : "Opt") + " Event : "+allEventsStr + " string does not have expected # of events : " +allEventsStrAra.length + " vs. expected :" +numEvents );
			return;
		}
		if ((type == 1) &&(numEvents > numEventsToShow) && dbgOutput) {
			System.out.println("type : " +type  +" | AllEventsStr : " + allEventsStr );
		}
			//for(String s : allEventsStrAra) {System.out.println("\t-"+s+"-");		}
		//EvSt,EvType,opt,EvID,118282,EvDt,2014-07-01 16:46:53,JPGJP_Start,optKey,1,JPG,26,JPst,220,JPEnd,JPG,27,JPst,56,58,50,54,JPEnd,JPGJP_End,EvEnd,
		//buildNewTrainDataFromStr(String _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr, int type)
		for (String eventStr : allEventsStrAra) {
			if (eventStr.length() == 0 ) {continue;}
			String[] eventStrAra = eventStr.trim().split(",");
			String evType = eventStrAra[1];
			Integer evID = Integer.parseInt(eventStrAra[3]);
			String evDateStr = eventStrAra[5];
			//need to print out string to make sure that there is only a single instance of every event id in each record in csv files
			//TODO
			StraffEvntTrainData newEv = buildNewTrainDataFromStr(evID, evType, evDateStr, eventStr, type);
			if ((type == 1) &&(numEvents > numEventsToShow) && dbgOutput) {
				System.out.println("\tEvent : " + newEv.toString());
			}
			Date addDate = newEv.getEventDate();			
			
			TreeMap<Integer, StraffEvntTrainData> eventsOnDate = mapToAdd.get(addDate);
			if(null == eventsOnDate) {eventsOnDate = new TreeMap<Integer, StraffEvntTrainData>();}
			StraffEvntTrainData tmpEvTrainData = eventsOnDate.get(evID);
			if(null != tmpEvTrainData) {System.out.println("Possible issue : event being written over : old : "+ tmpEvTrainData.toString() + "\n\t replaced by new : " + newEv.toString());}
			
			eventsOnDate.put(evID, newEv);
			mapToAdd.put(addDate, eventsOnDate);
		}		
	}//buildEventTrainDataFromCsvStr
	
	public  TreeMap<Integer, jpOccurrenceData> getOcccurenceMap(String key) {
		TreeMap<Integer, jpOccurrenceData> res = JpOccurrences.get(key);
		if (res==null) {mapData.dispMessage("ProspectExample::getOcccurenceMap : JpOccurrences map does not have key : " + key); return null;}
		return res;
	}
	
	protected void buildDataFromCSVString(int numOrderEvnts, int numOptEvnts, String _csvDataStr) {
		String [] strAraBegin = _csvDataStr.trim().split(Pattern.quote(orderCSVSentinelLbl)); //idx 1 holds all event data
		String [] strAraEvents = strAraBegin[1].trim().split(Pattern.quote(optCSVSentinelLbl)); //idx 0 holds order event data string, idx 1 holds all opt event data string
		//for(String s : strAraEvents) {System.out.println("-"+s+"-");		}
		String orderEventsDataStr ="", optEventsDataStr="";
		if (numOrderEvnts > 0) {
			orderEventsDataStr = strAraEvents[0].trim();
			buildEventTrainDataFromCSVStr(numOrderEvnts,orderEventsDataStr,orderEventsByDateMap, 0);
		}
		if (numOptEvnts > 0) {
			optEventsDataStr = strAraEvents[1].trim();
			if (numOptEvnts > 3) {System.out.println("OPT : _csvDataStr : " + _csvDataStr);}
			buildEventTrainDataFromCSVStr(numOptEvnts,optEventsDataStr,optEventsByDateMap, 1);	
		}
		//there are numOrderEvents encoded in orderEventsDataStr, and numOptEvnts in optEventsDataStr
	}//buildDataFromCSVString	

	protected void initObjsData() {
		orderEventsByDateMap = new TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> (); //add link objs eventually	
		optEventsByDateMap = new TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>>();
		//occurrence structures - keyed by type, then by JP
		JpOccurrences = new TreeMap<String, TreeMap<Integer, jpOccurrenceData>> ();
	}//initObjsData

	//any processing that must occur once all constituent data records are added to this example - must be called externally, before ftr vec is built
	@Override
	public void finalizeBuild() {
		buildOccurrenceStructs();	
		//all jps holds all jps in this example
		allJPs = buildJPListFromOccs();
		if(allJPs.size() == 0) {//means there's no valid jp's 
			if (OID.toUpperCase().equals("PR_000000019")) {
				System.out.println("No jps exist for OID : " + OID + " so setting to be bad example : \n" + this.toString());
			}
			setIsBadExample(true);		}//no mapped jps
	}//finalize
	
//	//build 
//	public void processHoldOutOptRecs() {//private TreeMap<String, TreeMap<Date,ArrayList<JpgJpDataRecord>>> mapOfListsOfJpgsJpsToRebuild;
//		if(!hasHoldOuts) {return;}
//		//go through this and rebuild mapOfListsOfJpgsJpsToRebuild
//		for (String typeOfEvents : mapOfListsOfJpgsJpsToRebuild.keySet()) {
//			TreeMap<Date,Integer> rebuildsAllDates = mapOfListsOfJpgsJpsToRebuild.get(typeOfEvents);
//			if (!typeOfEvents.equals("opts")){
////				//if (rebuildsAllDates.size() > 0){this.mapData.dispMessage("processHoldOutOptRecs : possible error : type of events : " + typeOfEvents + " - non-opt event applied to -all- jps : verify : " + this.toString());} 
////				for (Date date : rebuildsAllDates.keySet()) {
////					ArrayList<JpgJpDataRecord> rebuilds = rebuildsAllDates.get(date);
////					if (rebuilds.size() > 0) {mapData.dispMessage("processHoldOutOptRecs : : type of events : " + typeOfEvents + " - non-opt event applied to -all- jps  : # : " +rebuilds.size());	}
////				}
//				continue;
//			} else {//rebuild opts records with empty lists to have all jps
//				TreeMap<Integer, jpOccurrenceData> jpOccMap = JpOccurrences.get(typeOfEvents);
//				for (Date date : rebuildsAllDates.keySet()) {
//					Integer optVal = rebuildsAllDates.get(date);		
//					mapData.buildAllJpgJpOccMap(jpOccMap, date, optVal);
//					continue;
//				}			
//			}
//		}	
//	}//processHoldOutOptRecs
	
//	private boolean hasHoldOuts = false;
//	public static int countofRebuild = 0;
	//build occurence structure for type of events in this data, including aggregating build-later opt event data
	private void _buildIndivOccStructs(String key, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> mapByDate ) {
		TreeMap<Date, Integer> rebuildsAllDates = new TreeMap<Date,Integer>();		
		TreeMap<Integer, jpOccurrenceData> occs = new TreeMap<Integer, jpOccurrenceData>();
		for (TreeMap<Integer, StraffEvntTrainData> map : mapByDate.values()) {
			for (StraffEvntTrainData ev : map.values()) {
				Integer curOpt = rebuildsAllDates.get(ev.eventDate);
				if(curOpt == null) {curOpt = -10;}
				Integer[] rebuilds = new Integer[] {curOpt};
				//hasHoldOuts = hasHoldOuts || ev.procJPOccForAllJps(occs, rebuilds, key);
				ev.procJPOccForAllJps(occs, rebuilds, key);
				if(rebuilds[0] > curOpt) {	rebuildsAllDates.put(ev.eventDate, rebuilds[0]);}
			}			
		}
//		for (Integer rebuilds : rebuildsAllDates.values()) {
//			countofRebuild += 1;
//		}		
		if(rebuildsAllDates.size()>0) {		mapOfListsOfJpgsJpsToRebuild.put(key, rebuildsAllDates);}
		JpOccurrences.put(key, occs);
	}//_buildIndivOccStructs
	
	//build occurence structures based on mappings - must be called once mappings are completed but before the features are built
	private void buildOccurrenceStructs() { // should be executed when finished building both xxxEventsByDateMap(s)
		//occurrence structures
		JpOccurrences = new TreeMap<String, TreeMap<Integer, jpOccurrenceData>> ();
		//for orders and opts, pivot structure to build map holding occurrence records keyed by jp - must be done after all events are aggregated for each prospect
		//for every date, for every event, aggregate occurences		
		mapOfListsOfJpgsJpsToRebuild = new TreeMap<String, TreeMap<Date,Integer>>();
		_buildIndivOccStructs("orders", orderEventsByDateMap);
		_buildIndivOccStructs("opts", optEventsByDateMap);
		
	}//buildOccurrenceStructs	
	
	//check whether this prospect has a particular jp in either his prospect data, events or opts
	//idxs : 0==whether present at all; 1==if prospect; 2==if any event; 3==if in an order; 4==if in an opt
	public boolean[] hasJP(Integer _jp) {
		boolean hasJP_prspct = (prs_JP == _jp),
				hasJP_ordr =  (JpOccurrences.get("orders").get(_jp) != null) ,
				hasJP_opt = (JpOccurrences.get("opts").get(_jp) != null),
				hasJP_ev = hasJP_ordr || hasJP_opt,
				hasJP = hasJP_prspct || hasJP_ev;
		boolean[] res = new boolean[] {hasJP, hasJP_prspct, hasJP_ev, hasJP_ordr,hasJP_opt};
		return res;		
	}//check if JP exists, and where
	
	//return all jpg/jps in this prospect record
	public HashSet<Tuple<Integer,Integer>> getSetOfAllJpgJpData(){
		HashSet<Tuple<Integer,Integer>> res = new HashSet<Tuple<Integer,Integer>>();
		//for prospect rec
		if ((prs_JPGrp != 0) && (prs_JP != 0)) {res.add(new Tuple<Integer,Integer>(prs_JPGrp, prs_JP));}
		for(TreeMap<Integer, jpOccurrenceData> occs : JpOccurrences.values()) {
			for (jpOccurrenceData occ :occs.values()) {res.add(occ.getJpgJp());}	
		}
//		for (jpOccurrenceData occ : JpOccurrences.get("orders").values()) {		res.add(occ.getJpgJp());}		
//		for (jpOccurrenceData occ : JpOccurrences.get("opts").values()) {		res.add(occ.getJpgJp());}	
		return res;
	}
	
	public void addOrderObj(OrderEvent _obj) {addDataToTrainMap(_obj,orderEventsByDateMap, 0);}		
	public void addOptObj(OptEvent _obj) {addDataToTrainMap(_obj,optEventsByDateMap, 1);}
	
	//whether this record has any information to be used to train - presence of prospect jpg/jp can't be counted on
	//isBadExample means resulting ftr data is all 0's for this example.  can't learn from this, so no need to keep it.
	public boolean isTrainableRecord() {return !isBadTrainExample && ((prs_JP != 0) || (orderEventsByDateMap.size()>0) || (optEventsByDateMap.size()> 0));}
	public boolean isTrainableRecordEvent() {return !isBadTrainExample && ((orderEventsByDateMap.size()>0) || (optEventsByDateMap.size()> 0));}
	
	private String buildEventCSVString(Date date, TreeMap<Integer, StraffEvntTrainData> submap) {		
		String res = "";
		for (StraffEvntTrainData eventObj : submap.values()) {	res += eventObj.buildCSVString();}		
		return res;
	}//buildOrderCSVString
	
	private int getSizeOfDataMap(TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> map) {
		int res = 0;
		for (Date date : map.keySet()) {for (Integer eid :  map.get(date).keySet()) {res+=1;}	}	
		return res;
	}
		
	//required info for this example to build feature data - use this so we don't have to reload data ever time 
	//this will build a single record (row) for each OID (prospect)
	@Override
	public String getRawDescrForCSV() {
		//first build prospect data
		String dateStr = BaseRawData.buildStringFromDate(prs_LUDate);//(prs_LUDate == null) ? "" : BaseRawData.buildStringFromDate(prs_LUDate);
		String res = ""+OID+","+dateStr+","+prs_JPGrp+","+prs_JP+",";
		res += getSizeOfDataMap(orderEventsByDateMap)+"," + getSizeOfDataMap(optEventsByDateMap)+",";
		res +=  orderCSVSentinelLbl;
		for (Date date : orderEventsByDateMap.keySet()) {			res += buildEventCSVString(date, orderEventsByDateMap.get(date));		}		
		res += optCSVSentinelLbl;
		for (Date date : optEventsByDateMap.keySet()) {		res += buildEventCSVString(date, optEventsByDateMap.get(date));		}				
		return res;
	}	

	//column names for raw descriptorCSV output
	@Override
	public String getRawDescColNamesForCSV(){
		String csvColDescr = csvColDescrPrfx + ",";
		//add extra column descriptions for orders if using any		
		return csvColDescr;	
	}
	
	//build an arraylist of all jps in this example
	protected HashSet<Integer> buildJPListFromOccs(){
		HashSet<Integer> jps = new HashSet<Integer>();
		if (prs_JP > 0) {jps.add(prs_JP);}//if prospect jp specified, add this
		for(TreeMap<Integer, jpOccurrenceData> occs : JpOccurrences.values()) {
			for (Integer jp :occs.keySet()) {jps.add(jp);}	
		}
//		
//		for (Integer jp : orderJpOccurrences.keySet()) {jps.add(jp);}//add all order occs jps
//		for (Integer jp : optJpOccurrences.keySet()) {jps.add(jp);}//add all opt occs jps
		return jps;
	}
	//take loaded data and convert to feature data via calc object
	@Override
	protected void buildFeaturesMap() {
		//access calc object
		if (allJPs.size() > 0) {ftrMap = mapData.ftrCalcObj.calcFeatureVector(this,allJPs,JpOccurrences.get("orders"), JpOccurrences.get("opts"));}
		else {ftrMap = new TreeMap<Integer, Float>();}
	}//buildFeaturesMap
	@Override
	//standardize this feature vector
	protected void buildStdFtrsMap() {		
		if (allJPs.size() > 0) {stdFtrMap = mapData.calcStdFtrVector(this, allJPs);}
		else {stdFtrMap = new TreeMap<Integer, Float>();}
	}//buildStdFtrsMap
	
	private String toStringDateMap(TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> map, String mapName) {
		String res = "\n# of " + mapName + " in Date Map (count of unique dates - multiple " + mapName + " per same date possible) : "+ map.size()+"\n";		
		for (Date dat : map.keySet() ) {
			res+="Date : " + dat+"\n";
			TreeMap<Integer, StraffEvntTrainData> evMap = map.get(dat);
			for (StraffEvntTrainData ev : evMap.values()) {	res += ev.toString();	}
		}		
		return res;
	}//toStringDateMap	
	
	private String toStringOptOccMap(TreeMap<Integer, jpOccurrenceData> map, String mapName) {
		String res = "\n# of jp occurences in " + mapName + " : "+ map.size()+"\n";	
		for (Integer jp : map.keySet() ) {
			jpOccurrenceData occ = map.get(jp);
			res += occ.toString();			
		}	
		return res;		
	}
		//optJpOccurrences = new TreeMap<Integer, jpOccurrenceData>();
	
	@Override
	public String toString() {	
		String res = super.toString();
		res += toStringDateMap(orderEventsByDateMap, "orders");
		res += toStringOptOccMap(JpOccurrences.get("orders"), "order occurences");
		res += toStringDateMap(optEventsByDateMap, "opts");
		res += toStringOptOccMap(JpOccurrences.get("opts"), "opt occurences");
		return res;
	}
}//class prospectExample

/**
 * this class implements a product example, to be used to query the SOM and to illuminate relevant regions on the map.  the product can be specified by a single jp, or by the span of jps' related to a particular jpg
 * @author john
 *
 */
class ProductExample extends StraffSOMExample{
//	//column names for csv of this SOM example
//	private static final String csvColDescrPrfx = "";
	private static int IDcount = 0;	//incrementer so that all examples have unique ID
	private int exJPGProd;
	
	public ProductExample(SOMMapData _map,int _jpgrpID, ArrayList<Integer> _jpID) {
		super(_map,IDprfx + "_" +  String.format("%09d", IDcount++));
		exJPGProd = _jpgrpID;			
		allJPs = new HashSet<Integer>();
		updateJP(_jpID);
	}
	public ProductExample(SOMMapData _map, int _jpgrpID, ArrayList<Integer> _jpID, String _csvDataStr) {
		super(_map,IDprfx + "_" +  String.format("%09d", IDcount++));		
		exJPGProd = _jpgrpID;			
		allJPs = new HashSet<Integer>();
		updateJP(_jpID);
	}
	//update to hold 1 or more jpgs and jps for this product
	public void updateJP( ArrayList<Integer> _jpID) {
		for (Integer jp : _jpID) {allJPs.add(jp);}
		buildFeatureVector();
		buildPostFeatureVectorStructs();
	}
	
	//no need to finalize this build
	@Override
	public void finalizeBuild() {}//
	
	//required info for this example to build feature data  - this is ignored. these objects can be rebuilt on demand.
	@Override
	public String getRawDescrForCSV() {return "";	};
	//column names for raw descriptorCSV output
	@Override
	public String getRawDescColNamesForCSV(){		return "";	};

	//take loaded data and convert to output data
	@Override
	protected void buildFeaturesMap() {
		ftrMap = new TreeMap<Integer, Float>();	
		int count = 0;
		for (Integer IDX : allJPFtrIDXs) {ftrMap.put(IDX,1.0f);count++;}	
		this.ftrVecMag = (float) Math.sqrt(count);
	}
//	/TreeMap<Integer, Float> ftrMap, stdFtrMap, normFtrMap;

	@Override
	protected void buildStdFtrsMap() {
		stdFtrMap = new TreeMap<Integer, Float>();
		for (Integer IDX : ftrMap.keySet()) {stdFtrMap.put(IDX,ftrMap.get(IDX));}	
	}

}//class productExample

//this class is for a simple object to just represent a mouse-over on the visualization of the map
class DispSOMMapExample extends StraffSOMExample{
	private float ftrThresh;
	private int[] clrVal = new int[] {255,255,0,255};
	private String labelDat;
	private TreeMap<Float, String> strongestFtrs;

	public DispSOMMapExample(SOMMapData _map, myPointf ptrLoc, TreeMap<Integer, Float> _ftrs, float _thresh) {
		super(_map, "TempEx_"+ptrLoc.toStrBrf());
		ftrMap = new TreeMap<Integer, Float>();	
		ftrThresh = _thresh;
		allJPs = new HashSet<Integer>();
		allJPFtrIDXs = new ArrayList<Integer>();
		//decreasing order
		strongestFtrs = new TreeMap<Float, String>(Collections.reverseOrder());
		for(Integer ftrIDX : _ftrs.keySet()) {
			Float ftr = _ftrs.get(ftrIDX);
			if(ftr >= ftrThresh) {	
				Integer jp = mapData.jpByIdx[ftrIDX];
				allJPs.add(jp);
				allJPFtrIDXs.add(ftrIDX);	
				ftrMap.put(ftrIDX, ftr);
				strongestFtrs.put(ftr, ""+jp);
			}
		}		
 		//descriptive mouse-over label - top x jp's
		labelDat = "JPs : ";
		if (allJPs.size()== 0) {	labelDat += "None";	}
		else {
			for (Float ftr : strongestFtrs.keySet()) {
				String jpName = strongestFtrs.get(ftr);
				labelDat +=""+jpName+":" + String.format("%03f", ftr)+ " | ";
			}
		}
	}

	@Override
	protected void buildFeaturesMap() { }
	@Override
	public String getRawDescrForCSV() {	return "Should not save DispSOMMapExample to CSV";}
	@Override
	public String getRawDescColNamesForCSV() {return "Do not save DispSOMMapExample to CSV";}

	@Override
	public void finalizeBuild() {}
	@Override
	public void drawMeLblMap(SOM_StraffordMain p, dataClass _notUsed, boolean mseOvr){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label	
		p.show(mapLoc, rad, 5, clrVal,clrVal, SOM_StraffordMain.gui_LightGreen, labelDat, mseOvr);
		p.popStyle();p.popMatrix();		
	}//drawLabel

	@Override
	protected void buildStdFtrsMap() {	
		if (allJPs.size() > 0) {stdFtrMap = mapData.calcStdFtrVector(this, allJPs);}
	}
}//DispSOMMapExample


//this class represents a particular node in the SOM map
class SOMMapNodeExample extends StraffSOMExample{
	private static float ftrThresh = 0.0f;
	public Tuple<Integer,Integer> mapNodeLoc;	
	private TreeMap<Double,StraffSOMExample> examplesBMU;	//best training examples in this unit, keyed by distance
	private int numMappedTEx;						//# of mapped training examples to this node
	
// ftrMap, stdFtrMap, normFtrMap;
	public SOMMapNodeExample(SOMMapData _map, Tuple<Integer,Integer> _mapNode, float[] _ftrs) {
		super(_map, "MapNode_"+_mapNode.x+"_"+_mapNode.y);
		ftrMap = new TreeMap<Integer, Float>();	
		allJPs = buildJPsFromFtrAra(_ftrs, ftrThresh);
		for (Integer jp : allJPs) {int idx = mapData.jpToFtrIDX.get(jp);ftrMap.put(idx, _ftrs[idx]);}
		initMapNode( _mapNode);
	}
	public SOMMapNodeExample(SOMMapData _map,Tuple<Integer,Integer> _mapNode, String[] _strftrs) {
		super(_map, "MapNode_"+_mapNode.x+"_"+_mapNode.y);
		allJPs = new HashSet<Integer> ();
		if(_strftrs.length != 0){	setFtrsFromStrAra(_strftrs);	}
		initMapNode( _mapNode);
	}
	
	private void setFtrsFromStrAra(String [] tkns){
		int numFtrs = tkns.length;
		ftrMap = new TreeMap<Integer, Float>();
		float ftrVecSqMag = 0.0f;
		for(int i = 0; i < tkns.length; i++) {	
			Float val = Float.parseFloat(tkns[i]);
			if (val > ftrThresh) {
				ftrVecSqMag+=val*val;
				ftrMap.put(i, val);
				allJPs.add(mapData.jpByIdx[i]);
			}
		}
		ftrVecMag = (float) Math.sqrt(ftrVecSqMag);
	}//setFtrsFromStrAra

	private void initMapNode(Tuple<Integer,Integer> _mapNode){
		mapNodeLoc = _mapNode;
		//this will never be scaled or normed...?
		mapLoc = mapData.buildScaledLoc(mapNodeLoc);
		numMappedTEx = 0;
		examplesBMU = new TreeMap<Double,StraffSOMExample>();		//defaults to small->large ordering	
		//allJPs should be made by here
		buildFeatureVector();
	}//initMapNode
	
	@Override
	//feature is already made in constructor
	protected void buildFeaturesMap() {	}
	@Override
	public String getRawDescrForCSV() {	return "Should not save SOMMapNodeExample to intermediate CSV";}
	@Override
	public String getRawDescColNamesForCSV() {return "Do not save SOMMapNodeExample to intermediate CSV";}
	@Override
	public void finalizeBuild() {	}
	
	//this should not be used - should build stdFtrsmap based on ranges of each ftr value in trained map
	@Override
	protected void buildStdFtrsMap() {
		System.out.println("Calling inappropriate buildStdFtrsMap for SOMMapNodeExample : should call buildStdFtrsMap_MapNode from data loader from trained map w/arrays of per feature mins and diffs");
//		if (allJPs.size() > 0) {stdFtrMap = map.calcStdFtrVector(this, allJPs);}
//		else {stdFtrMap = new TreeMap<Integer, Float>();}		
	}
	
	public void buildStdFtrsMap_MapNode(float[] minsAra, float[] diffsAra) {
		stdFtrMap = new TreeMap<Integer, Float>();
		if (allJPs.size() > 0) {
			Integer destIDX;
			for (Integer jp : allJPs) {
				destIDX = mapData.jpToFtrIDX.get(jp);
				Float lb = minsAra[destIDX], 
						diff = diffsAra[destIDX];
				if (diff==0) {//same min and max
					if (lb > 0) {	stdFtrMap.put(destIDX,1.0f);}//only a single value same min and max-> set feature value to 1.0
					else {			stdFtrMap.put(destIDX,0.0f);}
				} else {
					float val = (ftrMap.get(destIDX)-lb)/diff;
					stdFtrMap.put(destIDX,val);
				}			
			}//for each jp
		}
	}//buildStdFtrsMap_MapNode
	
	public void addBMUExample(double dist, StraffSOMExample straffSOMExample){
		examplesBMU.put(dist, straffSOMExample);		
		numMappedTEx = examplesBMU.size();
		setRad( 2*numMappedTEx);// PApplet.sqrt(numMappedTEx) + 1;
		label = examplesBMU.firstEntry().getValue().getLabel();
	}
	
	public boolean hasMappings(){return numMappedTEx != 0;}
	@Override
	public dataClass getLabel(){
		if(numMappedTEx == 0){
			mapData.dispMessage("Mapnode :"+mapNodeLoc.toString()+" has no mapped BMU examples.");
			return null;
		}
		return examplesBMU.firstEntry().getValue().getLabel();}
	public int getExmplBMUSize() {return  examplesBMU.size();}
	public void drawMeSmallBk(SOM_StraffordMain p){
		p.pushMatrix();p.pushStyle();
		p.show(mapLoc, 2, 2, 0, 0);		
		p.popStyle();p.popMatrix();		
	}
	@Override
	public void drawMeMap(SOM_StraffordMain p, dataClass lblBlnk){
		p.pushMatrix();p.pushStyle();
		p.show(mapLoc, getRad(), drawDet, label.clrVal,label.clrVal);		
		p.popStyle();p.popMatrix();		
	}
	@Override
	public void drawMeLblMap(SOM_StraffordMain p, dataClass lblBlnk, boolean showBKGBox){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label	
		p.show(mapLoc, getRad(), 5, label.clrVal,label.clrVal, SOM_StraffordMain.gui_FaintGray, label.label,showBKGBox);
		p.popStyle();p.popMatrix();		
	}//drawLabel

	
	public String toString(){
		String res = "Node Loc : " + mapNodeLoc.toString()+"\t" + super.toString();
		return res;		
	}	
	
}//SOMMapNodeExample

//this class holds functionality migrated from the DataPoint class for rendering on the map.  since this won't be always necessary, we're moving this code to different class so it can be easily ignored
abstract class baseDataPtVis{
	//location in mapspace most closely matching this node - set to bmu map node location, needs to be actual map location
	protected myPointf mapLoc;		
	//draw-based vars
	protected float rad;
	protected int drawDet;	

	
	public baseDataPtVis() {
		mapLoc = new myPointf();	
		setRad( 2.0f);	
	}
	//for debugging purposes, gives min and max radii of spheres that will be displayed on map for each node proportional to # of samples - only display related
	public static float minRad = 100000, maxRad = -100000;
	protected void setRad(float _rad){
		rad = ((float)(Math.log(2.0f*(_rad+1))));
		minRad = minRad > rad ? rad : minRad;
		maxRad = maxRad < rad ? rad : maxRad;
		drawDet = ((int)(Math.log(2*(rad+1)))+1);
	}
	public float getRad(){return rad;}

	public void setMapLoc(myPointf _pt){mapLoc = new myPointf(_pt);}
	
	//override drawing in map nodes
	public void drawMeMap(SOM_StraffordMain p, dataClass label){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at mapLoc
		p.show(mapLoc, rad,drawDet, label.clrVal,label.clrVal);
		p.popStyle();p.popMatrix();		
	}//drawMe
	
	public void drawMeLblMap(SOM_StraffordMain p, dataClass label, boolean mseOvr){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label	
		p.show(mapLoc, rad, 5, label.clrVal,label.clrVal, SOM_StraffordMain.gui_LightGreen, label.label, mseOvr);
		p.popStyle();p.popMatrix();		
	}//drawLabel
	
	
	//override drawing in map nodes
	public void drawMeMapClr(SOM_StraffordMain p, int[] clr){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at mapLoc
		p.show(mapLoc, rad,drawDet, clr, clr);
		p.popStyle();p.popMatrix();		
	}//drawMe
	
	public void drawMeLblMapClr(SOM_StraffordMain p, dataClass label, int[] clr){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label	
		p.show(mapLoc, rad, drawDet, clr, clr, SOM_StraffordMain.gui_LightGreen, label.label, false);
		p.popStyle();p.popMatrix();		
	}//drawLabel

}//baseDataPtVis


/**
 * this class is a simple struct to hold a single jp's jpg, and the count and date of all occurences for a specific OID
 */
class jpOccurrenceData{
	//this jp
	public final Integer jp;
	//owning jpg
	public final Integer jpg;
	//keyed by date, value is # of occurences and opt value (ignored unless opt record)
	private TreeMap<Date, Tuple<Integer, Integer>> occurrences;
	
	public jpOccurrenceData(Integer _jp, Integer _jpg) {jp=_jp;jpg=_jpg;occurrences = new TreeMap<Date, Tuple<Integer, Integer>>();}	
	//add an occurence on date
	public void addOccurence(Date date, int opt) {
		Tuple<Integer, Integer> oldTup = occurrences.get(date);
		//shouldn't have different opt values for same jp for same date; issue warning if this is the case
		if ((oldTup!=null) && (oldTup.y != opt)) {System.out.println("jpOccurenceData::addOccurence : !!!Warning!!! : JP : " + jp + " on Date : " + date + " has 2 different opt values set in data : " +  oldTup.y + " and "+ opt +" | Using :"+ opt);}
		if(oldTup==null) {oldTup = new Tuple<Integer,Integer>(0,0); }
		Tuple<Integer, Integer> newDatTuple = new Tuple<Integer,Integer>(1 + oldTup.x,opt);		
		occurrences.put(date, newDatTuple);
	}//addOccurence
	
	//accessors
	public Date[] getDatesInOrder() {return occurrences.keySet().toArray(new Date[0]);}	
	
	//get occurrence with largest date
	public Entry<Date, Tuple<Integer,Integer>> getLastOccurrence(){		return occurrences.lastEntry();	}//
	
	//get the occurence count and opt value for a specific date
	public Tuple<Integer, Integer> getOccurrences(Date date){
		Tuple<Integer, Integer> oldTup = occurrences.get(date);
		//should never happen - means querying a date that has no record. 
		if(oldTup==null) {oldTup = new Tuple<Integer,Integer>(0,0); }
		return oldTup;
	}//getOccurrences
	
	//get tuple of jpg and jp
	public Tuple<Integer, Integer> getJpgJp(){return new Tuple<Integer,Integer>(jpg, jp);}
	
	public TreeMap<Date, Tuple<Integer, Integer>> getOccurrenceMap(){return occurrences;}
	
	public String toString() {
		String res = "JP : " + jp + " | JPGrp : " + jpg + "\n";
		for (Date dat : occurrences.keySet()) {
			Tuple<Integer, Integer> occData = occurrences.get(dat);
			Integer opt = occData.y;
			String optStr = "";
			if ((-2 <= opt) && (opt <= 2)) {optStr = " | Opt : " + opt;} //is an opt occurence
			res += "\t# occurences : " + occData.x + optStr+"\n";		
		}
		return res;
	}
		
}//class jpOccurenceData

/**
 * this class will hold either opt or order event data for a single OID for a single date
 * multiple events might have occurred on a single date, this will aggregate all relevant event data of a particular type
 * @author john
 *
 */
abstract class StraffEvntTrainData {
	//every unique eventID seems to occur only on 1 date
	protected Integer eventID;	
	protected Date eventDate;
	protected String eventType;	
	//array of jpg/jp records for this training data example
	protected ArrayList<JpgJpDataRecord> listOfJpgsJps;
	//magic value for opt key field in map, to use for non-opt records. 
	protected static final int FauxOptVal = 3;
	
	public StraffEvntTrainData(EventRawData ev) {
		eventID = ev.getEventID();//should be the same event type for event ID
		eventType = ev.getEventType();
		eventDate = ev.getDate();
		listOfJpgsJps = new ArrayList<JpgJpDataRecord>(); 
	}//ctor from rawDataObj
	
	public StraffEvntTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr) {
		eventID = _evIDStr;//should be the same event type for event ID
		eventType = _evTypeStr;
		eventDate = BaseRawData.buildDateFromString(_evDateStr);		
		listOfJpgsJps = new ArrayList<JpgJpDataRecord>(); 
	}//ctor from indiv data	
	
	//process JP occurence data for this event
	//passed is ref to map of all occurrences of jps in this kind of event's data for a specific prospect
	public void procJPOccForAllJps(TreeMap<Integer, jpOccurrenceData> jpOccMap,Integer[] araOfOptVals, String type) {
		//for each existing JPDataRecord, process occurences 
		//boolean hasHoldOuts = false;
		for (JpgJpDataRecord rec : listOfJpgsJps) {
			int opt = rec.getOptVal();
			Integer jpg = rec.getJPG();
			ArrayList<Integer> jps = rec.getJPlist();
			if((jpg == -10) && (jps.get(0) == -9) && (opt <= 0)) {continue;}				//PR_000870572 has opt 1 empty jpg/jp list				
//				if (opt <= 0) {continue;}				//don't consider a negative opt of all jps as an occurence for purposes of the occurrence map - occurence map is used to build training data, don't want to train off these folks' opts.
//				//here means some kind of opt all that's not negative (not an opt out all).  User chose to opt for all jps, so probably has some relevance
//				if(opt > araOfOptVals[0]) {		araOfOptVals[0] = opt;	}
//				//this is an opt event with opt in/out for all jps - apply opt value in rec to all jps/jpgs
//				//System.out.println("Ignoring opt event with only jpg==-2 and empty jps");
//				continue;
//			}
			for (Integer jp : jps) {
				jpOccurrenceData jpOcc = jpOccMap.get(jp);
				if (jpOcc==null) {jpOcc = new jpOccurrenceData(jp, jpg);}
				jpOcc.addOccurence(eventDate, rec.getOptVal());		
				//add this occurence object to map at idx jp
				jpOccMap.put(jp, jpOcc);
			}			
		}	
		//return hasHoldOuts;
	}//procJPOccForAllJps
	 
	public abstract void addEventDataFromCSVString(String _csvDataStr);
	protected void addEventDataRecsFromCSVStr(Integer optKey, String _csvDataStr) {
		boolean isOptEvent = ((-2 <= optKey) && (optKey <= 2));
		listOfJpgsJps = new ArrayList<JpgJpDataRecord>(); 
		String[] strAra1 = _csvDataStr.split("numJPGs,");//use idx 1
		String[] strAraVals = strAra1[1].trim().split(",JPGJP_Start,");//1st element will be # of JPDataRecs, next elements will be Data rec vals
		Integer numDataRecs = Integer.parseInt(strAraVals[0].trim());
		for (int i=0;i<numDataRecs;++i) {
			String csvString = strAraVals[i+1];
			String[] csvVals = csvString.split(",");
			Integer optVal = Integer.parseInt(csvVals[1]), JPG = Integer.parseInt(csvVals[3]);
			JpgJpDataRecord rec = new JpgJpDataRecord(JPG,i, optVal, csvString);
			listOfJpgsJps.add(rec);			
		}
	}//addEventDataFromCSVStrByKey
	
	public abstract void addEventDataFromEventObj(EventRawData ev);
	protected void addEventDataRecsFromRaw(Integer optVal, EventRawData ev) {
		//if -2 <= optVal <= 2 then this is an opt event
		boolean isOptEvent = ((-2 <= optVal) && (optVal <= 2));
		TreeMap<Integer, ArrayList<Integer>> newEvMapOfJPAras = ev.rawJpMapOfArrays;
		if (newEvMapOfJPAras.size() == 0) {					//if returns an empty list from event raw data then either unspecified, which is bad record, or infers entire list of jp data
			if (!isOptEvent){								//should never happen, means empty jp array of non-opt events (like order events) - should always have an order jpg/jp data.
				System.out.println("Warning : Attempting to add a non-Opt event with an empty JPgroup-keyed JP list - event had no corresponding usable data");
				return;
			} else {										//empty jpg-jp array means apply to all
				//Adding an opt event with an empty JPgroup-keyed JP list - means apply opt value to all jps;
				//use jpg -10, jp -9 as sentinel value to denote executing an opt on all jpgs/jps - usually means opt out on everything, but other opts possible
				ArrayList<Integer>tmp=new ArrayList<Integer>(Arrays.asList(-9));
				newEvMapOfJPAras.put(-10, tmp);				
			}
		}			
		//-1 means this array is the list, in order, of JPGs in this eventrawdata.  if there's no entry then that means there's only 1 jpg in this eventrawdata
		ArrayList<Integer> newJPGOrderArray = newEvMapOfJPAras.get(-1);
		if(newJPGOrderArray == null) {//adding only single jpg, without existing preference order
			newJPGOrderArray = new ArrayList<Integer> ();
			Integer jpgPresent = newEvMapOfJPAras.firstKey();
			//if -2 then this means this is an empty list, means applies to all jps
			newJPGOrderArray.add(newEvMapOfJPAras.firstKey());
		} 	
		JpgJpDataRecord rec;
		for (int i = 0; i < newJPGOrderArray.size(); ++i) {
			int jpg = newJPGOrderArray.get(i);				
			ArrayList<Integer> jpgs = newEvMapOfJPAras.get(jpg);
			rec = new JpgJpDataRecord(jpg,i,optVal);
			for (Integer val : jpgs) {rec.addToJPList(val);}
			listOfJpgsJps.add(rec);				
		}			
	}//

	public String buildCSVString() {
		String res = "EvSt,EvType,"+eventType+",EvID,"+eventID+",EvDt,"+ BaseRawData.buildStringFromDate(eventDate)+",numJPGs,"+listOfJpgsJps.size()+",";	
		for (int i=0;i<listOfJpgsJps.size();++i) {
			JpgJpDataRecord rec = listOfJpgsJps.get(i);
			res += "JPGJP_Start,optKey,"+rec.getOptVal()+","+rec.getCSVString()+"JPGJP_End,";			
		}
		res+="EvEnd,";			
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
		for (JpgJpDataRecord jpRec : listOfJpgsJps) { res += "\t" + jpRec.toString()+"\n";}
		return res;
 	}
	
}//class StraffEvntTrainData

class OrderEventTrainData extends StraffEvntTrainData{

	public OrderEventTrainData(OrderEvent ev) {
		super(ev);
		addEventDataFromEventObj(ev);	//put in child ctor incase child-event specific data needed for training	
	}
	public OrderEventTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){
		super(_evIDStr, _evTypeStr, _evDateStr);
		addEventDataFromCSVString(_evntStr);	//put in child ctor incase child-event specific data needed for training	
	}
	
	@Override
	public void addEventDataFromEventObj(EventRawData ev) {
		super.addEventDataRecsFromRaw(FauxOptVal,ev);
	}//addEventDataFromEventObj
	@Override
	public void addEventDataFromCSVString(String _csvDataStr) {
		super.addEventDataRecsFromCSVStr(FauxOptVal,_csvDataStr);	
	}//addEventDataFromCSVString

}//class OptEventTrainData

class OptEventTrainData extends StraffEvntTrainData{
	public OptEventTrainData(OptEvent ev) {
		super(ev);		
		addEventDataFromEventObj(ev);	//put in child ctor incase child-event specific data needed for training	
	}
	
	public OptEventTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){
		super(_evIDStr, _evTypeStr, _evDateStr);
		addEventDataFromCSVString(_evntStr);	//put in child ctor incase child-event specific data needed for training	
	}
	
	@Override
	public void addEventDataFromEventObj(EventRawData ev) {
		Integer optValKey = ((OptEvent)ev).getOptType();
		super.addEventDataRecsFromRaw(optValKey, ev);
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
	public void addEventDataFromCSVString(String _csvDataStr) {
		Integer optValKey = getOptValFromCSVStr(_csvDataStr);
		super.addEventDataRecsFromCSVStr(optValKey, _csvDataStr);
	}

}//class OptEventTrainData

/////////////////////////////////////////////////////////////////////////////////
/**
 * class holds pertinent info for a job practice group/job practice record 
 * relating to 1 job practice group and 1 or more job practice ids for a single prospect
 * @author john
 *
 */
class JpgJpDataRecord implements Comparable<JpgJpDataRecord>{
	//lower priority is better - primarily relevant for OPT records, since they have 
	private int optPriority;
	//optVal if this is an opt record, otherwise some value outside the opt range
	private int optVal;
	
	private int JPG;
	private ArrayList<Integer> JPlist;

	public JpgJpDataRecord(int _jpg, int _priority, int _optVal) {
		JPG = _jpg;
		optPriority = _priority;
		optVal = _optVal;
		JPlist = new ArrayList<Integer>();
	}//ctor w/actual data
	
	//_csvString should be from JPst to JPend
	public JpgJpDataRecord(int _jpg, int _priority, int _optVal, String _csvString) {
		this(_jpg,  _priority,  _optVal);
		//parse _csvString and add components : //optKey,1,JPG,9,JPst,145,JPend,JPGJP_End
		String[] tmpStr = _csvString.trim().split(",JPst,"),
				tmpStr1 = tmpStr[1].trim().split(",JPend,"), 
				jpDataAra=tmpStr1[0].trim().split(",");
		for (int i=0;i<jpDataAra.length;++i) {	addToJPList(Integer.parseInt(jpDataAra[i]));}		
	}//ctor from csv string
	
	public void addToJPList(int val) {JPlist.add(val);}//addToJPList
	
	public String getCSVString() {
		if (JPlist.size() == 0){return "None,";}		
		String res = "JPG," +JPG+",JPst,";
		int szm1 = JPlist.size()-1;
		for (int i=0; i<szm1;++i) {res += JPlist.get(i)+",";}
		res += JPlist.get(szm1)+",JPend,";
		return res;
	}//getCSVString

	//for possible future json jackson serialization (?)
	public int getPriority() {	return optPriority;}
	public int getJPG() {		return JPG;	}
	public ArrayList<Integer> getJPlist() {return JPlist;}
	public int getOptVal() {return optVal;	}

	public void setOptVal(int _o) {optVal = _o;}
	public void setJPG(int jPG) {JPG = jPG;}
	public void setJPlist(ArrayList<Integer> jPlist) {JPlist = jPlist;}
	public void setPriority(int _p) {	optPriority = _p;}	

	@Override
	public int compareTo(JpgJpDataRecord otr) {
		if (this.optPriority == otr.optPriority) {return 0;}
		return (this.optPriority < otr.optPriority) ? 1 : -1;
	}//compareTo

	@Override
	public String toString() {
		String res = "JPG:"+JPG+" |Priority:"+optPriority+" |opt:"+optVal+" |JPList: [";
		int szm1 = JPlist.size()-1;
		for (int i=0; i<szm1;++i) {res += JPlist.get(i)+",";}
		res += JPlist.get(szm1)+"]";
		return res;
	}
	
	
}//JPDataRecord
