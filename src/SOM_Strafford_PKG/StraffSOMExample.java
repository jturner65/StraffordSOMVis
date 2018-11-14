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
	protected TreeMap<Integer, Float> ftrMap, stdFtrMap, normFtrMap;
	
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
	//use a map to hold the features sorted by weight as key, value is array of jps at that weight - map needs to be instanced in descending key order
	//a map per feature type : unmodified, normalized, standardized
	public TreeMap<String, TreeMap<Float, ArrayList<Integer>>> mapOfTopJps;
	
	//a map of jps and their relative "rank" in this particular example, keyed by 
	public TreeMap<String, TreeMap<Integer,Integer>> mapOfJpsVsRank;
	//keys for above maps
	public static final String[] jpMapTypeKeys = new String[] {"ftrMap", "stdFtrMap", "normFtrMap"};
	public static final int ftrMapTypeKey = 0, stdFtrMapTypeKey = 1, normFtrMapTypeKey = 2;
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
		ftrMap = new TreeMap<Integer, Float>();			//feature map for SOM Map Nodes may not correspond directly to magnitudes seen in training data. 
		stdFtrMap = new TreeMap<Integer, Float>();	
		normFtrMap=new TreeMap<Integer, Float>();	
		allJPs = new HashSet<Integer> ();
		//this map is a map of maps in descending order - called by calc object as well as buildNormFtrData
		mapOfTopJps = new TreeMap<String, TreeMap<Float, ArrayList<Integer>>>();//(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}}); 
		mapOfJpsVsRank = new TreeMap<String, TreeMap<Integer,Integer>>();
		for(String key : jpMapTypeKeys) {
			TreeMap<Float, ArrayList<Integer>> tmpFltMap = new TreeMap<Float, ArrayList<Integer>>(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}});
			TreeMap<Integer,Integer> tmpIntMap = new TreeMap<Integer,Integer>();
			mapOfTopJps.put(key, tmpFltMap);
			mapOfJpsVsRank.put(key, tmpIntMap);
		}
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
	
	//eventtype : 0 : order, 1 : opt, 2 : link
	protected StraffEvntTrainData buildNewTrainDataFromEv(EventRawData _evntObj, int eventtype) {
		switch (eventtype) {
		case 0 :{			return new OrderEventTrainData((OrderEvent) _evntObj);	}//order event object
		case 1 : {			return new OptEventTrainData((OptEvent) _evntObj);}//opt event  object
		case 2 : { 			return new LinkEventTrainData((LinkEvent) _evntObj);}//link event
		default : {			return new OrderEventTrainData((OrderEvent) _evntObj);	}//default to order event object - probably will fail.  need to make sure type is accurately specified.
		}//switch
	}//buildNewTrainData
	
	//eventtype : 0 : order, 1 : opt, 2 : link
	protected StraffEvntTrainData buildNewTrainDataFromStr(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr, int eventtype) {
		switch (eventtype) {
		case 0 :{			return new OrderEventTrainData(_evIDStr, _evTypeStr, _evDateStr, _evntStr);	}//order event object
		case 1 : {			return new OptEventTrainData(_evIDStr, _evTypeStr, _evDateStr, _evntStr);}//opt event  object
		case 2 : {			return new LinkEventTrainData(_evIDStr, _evTypeStr, _evDateStr, _evntStr);}//opt event  object
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
	
	//take map of jpwts to arrays of jps and build maps of jps to rank
	//This will give a map of all present JPs for this example and the rank of that jp.  null entries mean no rank
	public void buildMapOfJPsToRank() {
		for (String mapToGet : jpMapTypeKeys) {//for each type
			Integer rank = 0;
			TreeMap<Float, ArrayList<Integer>> mapOfAras = mapOfTopJps.get(mapToGet);
			TreeMap<Integer,Integer> mapOfRanks = mapOfJpsVsRank.get(mapToGet);
			for (Float wtVal : mapOfAras.keySet()) {
				ArrayList<Integer> jpsAtRank = mapOfAras.get(wtVal);
				for (Integer jp : jpsAtRank) {	mapOfRanks.put(jp, rank);}
				++rank;
			}
			mapOfJpsVsRank.put(mapToGet, mapOfRanks);//probably not necessary		
		}		
	}//buildMapOfJPsToRank
	//return the rank of the passed jp
	protected Integer getJPRankForMap(int mapToGetIDX, int jp) {
		String mapToGet = jpMapTypeKeys[mapToGetIDX];
		TreeMap<Integer,Integer> mapOfRanks = mapOfJpsVsRank.get(mapToGet);
		Integer rank = mapOfRanks.get(jp);
		if (rank==null) {rank = mapData.jpByIdx.length;}
		return rank;
	}	
	
	public void setBMU(SOMMapNodeExample _n, float[] dataVar){
		if (checkForErrors(_n, dataVar)) {return;}
		bmu = _n;	
		mapLoc.set(_n.mapLoc);
		double dist = mapData.dpDistFunc(_n, this,dataVar);
		//dist here is distance of this training example to map node
		_n.addBMUExample(dist, this);
	}//setBMU
	
	//TODO : build the SOM datapoint values used to train the SOM - features should be set and scaled and/or normed by here if appropriate
	public void buildSOMDataPoint() {
		if(label==null){label = new dataClass(OID,"unitialized label","unitialized Description", null);}
		bmu = null;
		//map.dispMessage("Building DATA POINT chugga : "+OID);
		
	}//buildSOMDataPoint	
	
	//call from calc or from objects that manage norm/std ftrs - build structure registering weight of jps in ftr vector mapToGet in descending strength
	protected void setMapOfJpWts(int jp, float wt, int mapToGetIDX) {
		String mapToGet = jpMapTypeKeys[mapToGetIDX];
		TreeMap<Float, ArrayList<Integer>> map = mapOfTopJps.get(mapToGet);
		//shouldn't be null - means using inappropriate key
		if(map == null) {this.mapData.dispMessage("setMapOfJpWts : Using inappropriate key to access mapOfTopJps : " + mapToGet + " No submap exists with this key."); return;}		 
		ArrayList<Integer> jpIdxsAtWt = map.get(wt);
		if (jpIdxsAtWt == null) {jpIdxsAtWt = new ArrayList<Integer>(); }
		jpIdxsAtWt.add(jp);
		map.put(wt, jpIdxsAtWt);
	}//setMapOfJpWts

	//build feature vector - call externally after finalize
	public void buildFeatureVector() {//all jps seen by all examples must exist by here so that mapData.jpToFtrIDX has accurate data
		buildAllJPFtrIDXsJPs();
		buildFeaturesMap();
		ftrsBuilt = true;		
		buildNormFtrData();
	}//buildFeatureVector
	
	protected void buildAllJPFtrIDXsJPs() {
		allJPFtrIDXs = new ArrayList<Integer>();
		for(Integer jp : allJPs) {allJPFtrIDXs.add(mapData.jpToFtrIDX.get(jp));}
	}
	
	//build structures that require that the feature vector be built before hand
	public void buildPostFeatureVectorStructs() {
		buildStdFtrsMap();
		//by here all maps of per-type, per-feature val arrays of jps should be built
		buildMapOfJPsToRank();
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
	protected void buildNormFtrData() {
		if(!ftrsBuilt) {mapData.dispMessage("OID : " + OID + " : Features not built, cannot normalize feature data");return;}
		normFtrMap=new TreeMap<Integer, Float>();
		if(this.ftrVecMag == 0) {return;}
		for (Integer IDX : allJPFtrIDXs) {
			int jp = mapData.jpByIdx[IDX];
			Float val  = ftrMap.get(IDX)/this.ftrVecMag;
			normFtrMap.put(IDX,val);
			setMapOfJpWts(jp, val, normFtrMapTypeKey);			
		}	
		normFtrsBuilt = true;
	}//buildNormFtrData
	
	//this is here so can more easily use the mins and diffs equations
	protected TreeMap<Integer, Float> calcStdFtrVector(TreeMap<Integer, Float> ftrs, ArrayList<Integer> jpIdxs){
		return calcStdFtrVector(ftrs, jpIdxs, mapData.minsVals, mapData.diffsVals);
	}
	//scale each feature value to be between 0->1 based on min/max values seen for this feature
	//all examples features will be scaled with respect to seen calc results 0- do not use this for
	//exemplar objects (those that represent a particular product, for example)
	//MUST BE SET WITH APPROPRIATE MINS AND DIFFS
	private TreeMap<Integer, Float> calcStdFtrVector(TreeMap<Integer, Float> ftrs, ArrayList<Integer> jpIdxs, Float[] mins, Float[] diffs) {
		TreeMap<Integer, Float> sclFtrs = new TreeMap<Integer, Float>();
		for (Integer destIDX : jpIdxs) {
			int jp = mapData.jpByIdx[destIDX];
			Float lb = mins[destIDX], 	diff = diffs[destIDX];
			Float val = 0.0f;
			if (diff==0) {//same min and max
				if (lb > 0) { val = 1.0f;}//only a single value same min and max-> set feature value to 1.0
				else {		  val = 0.0f;}
			} else {
				val = (ftrs.get(destIDX)-lb)/diff;
			}	
			sclFtrs.put(destIDX,val);
			setMapOfJpWts(jp, val, stdFtrMapTypeKey);
			
		}//for each jp
		return sclFtrs;
	}//standardizeFeatureVector
	
	
	public dataClass getLabel(){return label;}
	
	private String _toCSVString(TreeMap<Integer, Float> ftrs) {
		String res = ""+OID+",";
		for(int i=0;i<mapData.numFtrs;++i){
			Float ftr = ftrs.get(i);			
			res += String.format("%1.7g", (ftr==null ? 0 : ftr)) + ",";
		}
		return res;}
	//return csv string of this object's features, depending on which type is selected - check to make sure 2ndary features exist before attempting to build data strings
	////useUnmoddedDat = 0, useScaledDat = 1, useNormedDat
	public String toCSVString(int _type) {
		switch(_type){
			case SOMMapData.useUnmoddedDat : {return _toCSVString(ftrMap); }
			case SOMMapData.useNormedDat  : {return _toCSVString(normFtrsBuilt ? normFtrMap : ftrMap);}
			case SOMMapData.useScaledDat  : {return _toCSVString(stdFtrsBuilt ? stdFtrMap : ftrMap); }
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
			case SOMMapData.useUnmoddedDat : {return _toLRNString(ftrMap, sep); }
			case SOMMapData.useNormedDat   : {return _toLRNString(normFtrsBuilt ? normFtrMap : ftrMap, sep);}
			case SOMMapData.useScaledDat   : {return _toLRNString(stdFtrsBuilt ? stdFtrMap : ftrMap, sep); }
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
			case SOMMapData.useUnmoddedDat : {return _toSVMString(ftrMap); }
			case SOMMapData.useNormedDat   : {return _toSVMString(normFtrsBuilt ? normFtrMap : ftrMap);}
			case SOMMapData.useScaledDat   : {return _toSVMString(stdFtrsBuilt ? stdFtrMap : ftrMap); }
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
	
	public TreeMap<Integer, Float> getCurrentFtrMap(int _type){
		switch(_type){
			case SOMMapData.useUnmoddedDat : {return ftrMap; }
			case SOMMapData.useNormedDat   : {return (normFtrsBuilt ? normFtrMap : ftrMap);}
			case SOMMapData.useScaledDat   : {return (stdFtrsBuilt ? stdFtrMap : ftrMap); }
			default : {return ftrMap; }
		}		
	}

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
			res +="\nScaled Features : ";
			res += dispFtrs(stdFtrMap);
			res +="\nNormed Features : ";
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
	//column names for csv of this SOM example
	private static final String csvColDescrPrfx = "OID,Prospect_LU_Date,Prospect_JPG,Prospect_JP,Num Order Event Dates,Num Opt Event Dates,Num Link Event Dates";
	
	//////////////////////////////////////
	//Training data description
	//////////////////////////////////////
	
    //prospect job practice and job practice group if any specified
	public final int prs_JPGrp,prs_JP;
	//prospect last lookup date, if any specified
	public Date prs_LUDate;
	
	private static final String[] mapKeys = new String[] {"orders", "opts", "links"};
	private static final String[] CSVSentinelLbls = new String[] {"ORD|,","OPT|,", "LNK|," };
	//may have multiple events on same date/time, map by event ID 	
	private TreeMap<String, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>>> eventsByDateMap;
	//structs to hold all order occurences and opt occurences of each JP for this OID
	private TreeMap<String, TreeMap<Integer, jpOccurrenceData>> JpOccurrences;//orderJpOccurrences, optJpOccurrences;

	//this object denotes a positive opt-all event for a user (i.e. an opts occurrence with a jp of -9)
	private jpOccurrenceData posOptAllEventObj = null;
	
	//build this object based on prospect object
	public ProspectExample(SOMMapData _map,prospectData _prspctData) {
		super(_map,_prspctData.OID);	
		if( _prspctData.rawJpMapOfArrays.size() > 0) {
			prs_JPGrp = _prspctData.rawJpMapOfArrays.firstKey();
			prs_JP = _prspctData.rawJpMapOfArrays.get(prs_JPGrp).get(0);
		} else {prs_JPGrp=0;prs_JP=0;}
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
		int[] numEvsAra = new int[] {Integer.parseInt(dataAra[4]),Integer.parseInt(dataAra[5]),Integer.parseInt(dataAra[6])};
		initObjsData();	
		//Build data here from csv strint
		buildDataFromCSVString(numEvsAra, _csvDataStr);		
	}//csv string ctor
	
	//build a single type of event's training data from Csv string
	private void buildEventTrainDataFromCSVStr(int numEvents, String allEventsStr, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> mapToAdd, int type) {
		String evntType = mapKeys[type];
		int numEventsToShow = 0; boolean dbgOutput = false;
		String [] allEventsStrAra = allEventsStr.trim().split("EvSt,");
		//will have an extra entry holding OPT key
		if(allEventsStrAra.length != (numEvents+1)) {//means not same number of event listings in csv as there are events counted in original event list - shouldn't be possible
			System.out.println("buildEventTrainDataFromCsvStr : Error building train data from csv file : " + evntType + " Event : "+allEventsStr + " string does not have expected # of events : " +allEventsStrAra.length + " vs. expected :" +numEvents );
			return;
		}
		if ((type == 1) &&(numEvents > numEventsToShow) && dbgOutput) {
			System.out.println("type : " +evntType  +" | AllEventsStr : " + allEventsStr );
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
	
	//this sets/gets this StraffSOMExample's object that denotes a positive opt setting for all jps
	public void setOptAllOccObj(jpOccurrenceData _optAllOcc) {posOptAllEventObj = _optAllOcc;	}//setOptAllOccObj
	public jpOccurrenceData getOptAllOccObj() {		return posOptAllEventObj;	}
		
	public  TreeMap<Integer, jpOccurrenceData> getOcccurenceMap(String key) {
		TreeMap<Integer, jpOccurrenceData> res = JpOccurrences.get(key);
		if (res==null) {mapData.dispMessage("ProspectExample::getOcccurenceMap : JpOccurrences map does not have key : " + key); return null;}
		return res;
	}
	
	protected void buildDataFromCSVString(int[] numEvntsAra, String _csvDataStr) {
		//each type of event list exists between the sentinel flag and the subsequent sentinel flag
		for (int i = 0; i<mapKeys.length;++i) {
			if (numEvntsAra[i] > 0) {
				String key = mapKeys[i];
				String stSentFlag = CSVSentinelLbls[i];
				String endSntnlFlag = (i <mapKeys.length-1 ? CSVSentinelLbls[i+1] : null );
				String [] strAraBegin = _csvDataStr.trim().split(Pattern.quote(stSentFlag)); //idx 1 holds all event data	
				String strBegin = (strAraBegin.length < 2) ? "" : strAraBegin[1];
				String strEvents = (endSntnlFlag != null ? strBegin.trim().split(Pattern.quote(endSntnlFlag))[0] : strBegin).trim(); //idx 0 holds relevant data
				buildEventTrainDataFromCSVStr(numEvntsAra[i],strEvents,eventsByDateMap.get(key), i);				
			}			
		}
	}//buildDataFromCSVString	

	protected void initObjsData() {
		eventsByDateMap = new TreeMap<String, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>>>();
		for (String key : mapKeys) {eventsByDateMap.put(key, new TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> ());	}
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
	
	//build occurence structure for type of events in this data, including aggregating build-later opt event data
	private void _buildIndivOccStructs(String key, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> mapByDate ) {
		TreeMap<Integer, jpOccurrenceData> occs = new TreeMap<Integer, jpOccurrenceData>();
		for (TreeMap<Integer, StraffEvntTrainData> map : mapByDate.values()) {
			for (StraffEvntTrainData ev : map.values()) {ev.procJPOccForAllJps(this, occs, key);}			
		}
		JpOccurrences.put(key, occs);
	}//_buildIndivOccStructs

	//build occurence structures based on mappings - must be called once mappings are completed but before the features are built
	private void buildOccurrenceStructs() { // should be executed when finished building both xxxEventsByDateMap(s)
		//occurrence structures
		JpOccurrences = new TreeMap<String, TreeMap<Integer, jpOccurrenceData>> ();
		//for orders and opts, pivot structure to build map holding occurrence records keyed by jp - must be done after all events are aggregated for each prospect
		//for every date, for every event, aggregate occurences		
		for (String key : mapKeys) {
			_buildIndivOccStructs(key, eventsByDateMap.get(key));			
		}
		
	}//buildOccurrenceStructs	
	
	//check whether this prospect has a particular jp in either his prospect data, events or opts
	//idxs : 0==whether present at all; 1==if prospect; 2==if any event; 3==if in an order; 4==if in an opt
	public boolean[] hasJP(Integer _jp) {
		boolean hasJP_prspct = (prs_JP == _jp),
				hasJP_ordr =  (JpOccurrences.get("orders").get(_jp) != null) ,
				hasJP_opt = (JpOccurrences.get("opts").get(_jp) != null),
				hasJP_link = (JpOccurrences.get("links").get(_jp) != null),				
				hasJP_ev = hasJP_ordr || hasJP_opt | hasJP_link,
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
		return res;
	}///getSetOfAllJpgJpData
	
	public void addObj(BaseRawData obj, int type) {
		switch(type) {
		case SOMMapData.prspctIDX : 	{mapData.dispMessage("ProspectExample::addObj : ERROR attempting to add prospect raw data as event data. Ignored");return;}
		case SOMMapData.orderEvntIDX : 	{
			addDataToTrainMap((OrderEvent)obj,eventsByDateMap.get(mapKeys[0]), 0); 
			return;}
		case SOMMapData.optEvntIDX : 	{
			addDataToTrainMap((OptEvent)obj,eventsByDateMap.get(mapKeys[1]), 1); 
			return;}
		case SOMMapData.linkEvntIDX : 	{
			addDataToTrainMap((LinkEvent)obj,eventsByDateMap.get(mapKeys[2]), 2); 
			return;}
		default :{mapData.dispMessage("ProspectExample::addObj : ERROR attempting to add unknown raw data type : " + type + " as event data. Ignored");return;}
		}		
	}
	
	//whether this record has any information to be used to train - presence of prospect jpg/jp can't be counted on
	//isBadExample means resulting ftr data is all 0's for this example.  can't learn from this, so no need to keep it.
//	public boolean isTrainableRecord() {return !isBadTrainExample && ((prs_JP != 0) || (orderEventsByDateMap.size()>0) || (optEventsByDateMap.size()> 0));}
//	public boolean isTrainableRecordEvent() {return !isBadTrainExample && ((orderEventsByDateMap.size()>0) || (optEventsByDateMap.size()> 0));}
	public boolean isTrainableRecord() {return !isBadTrainExample && ((prs_JP != 0) || hasRelelventEvents());}
	public boolean isTrainableRecordEvent() {return !isBadTrainExample && (hasRelelventEvents());}
	
	private boolean hasRelelventEvents() {
		boolean res = false;
		for (String key : mapKeys) {if (eventsByDateMap.get(key).size() > 0) {return true;}	}	
		return res;
	}//hasRelelventEvents	
	
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
		for (String key : mapKeys) {
			res += getSizeOfDataMap(eventsByDateMap.get(key))+",";
		}
		//res += getSizeOfDataMap(orderEventsByDateMap)+"," + getSizeOfDataMap(optEventsByDateMap)+",";
		//now build res string for all event data objects
		for(int i=0;i<mapKeys.length;++i) {
			String key = mapKeys[i];
			res += CSVSentinelLbls[i];
			TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> eventsByDate = eventsByDateMap.get(key);
			for (Date date : eventsByDate.keySet()) {			res += buildEventCSVString(date, eventsByDate.get(date));		}				
		}
//		res +=  orderCSVSentinelLbl;
//		for (Date date : orderEventsByDateMap.keySet()) {			res += buildEventCSVString(date, orderEventsByDateMap.get(date));		}		
//		res += optCSVSentinelLbl;
//		for (Date date : optEventsByDateMap.keySet()) {		res += buildEventCSVString(date, optEventsByDateMap.get(date));		}
//		res += linkCSVSentinelLbl;		
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
			for (Integer jp :occs.keySet()) {
				if (jp == -9) {continue;}//ignore -9 jp occ map - this is an entry to denote a positive opt - shouldn't be here anymore
				jps.add(jp);
			}	
		}
		return jps;
	}//buildJPListFromOccs
	
    //take loaded data and convert to feature data via calc object
	@Override
	protected void buildFeaturesMap() {
		//access calc object
		if (allJPs.size() > 0) {
			ftrMap = mapData.ftrCalcObj.calcFeatureVector(this,allJPs,JpOccurrences.get("orders"), JpOccurrences.get("links"), JpOccurrences.get("opts"));
		}
		else {ftrMap = new TreeMap<Integer, Float>();}
		//now, if there's a non-null posOptAllEventObj then for all jps who haven't gotten an opt conribution to calculation, add positive opt-all result
	}//buildFeaturesMap

	@Override
	//standardize this feature vector
	protected void buildStdFtrsMap() {		
		if (allJPFtrIDXs.size() > 0) {stdFtrMap = calcStdFtrVector(ftrMap, allJPFtrIDXs);}
		else {stdFtrMap = new TreeMap<Integer, Float>();}
		stdFtrsBuilt = true;
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
	
	@Override
	public String toString() {	
		String res = super.toString();
		for (String key : mapKeys) {
			res += toStringDateMap(eventsByDateMap.get(key), key);
			res += toStringOptOccMap(JpOccurrences.get(key), key + " occurences");
		}
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
	
	@Override
	protected void buildStdFtrsMap() {
		stdFtrMap = new TreeMap<Integer, Float>();
		for (Integer IDX : ftrMap.keySet()) {stdFtrMap.put(IDX,ftrMap.get(IDX));}
		stdFtrsBuilt = true;
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
		stdFtrMap = new TreeMap<Integer, Float>();
		if (allJPFtrIDXs.size() > 0) {stdFtrMap = calcStdFtrVector(ftrMap, allJPFtrIDXs);}
		stdFtrsBuilt = true;
	}
}//DispSOMMapExample


//this class represents a particular node in the SOM map
class SOMMapNodeExample extends StraffSOMExample{
	private static float ftrThresh = 0.0f;
	public Tuple<Integer,Integer> mapNodeLoc;	
	private TreeMap<Double,StraffSOMExample> examplesBMU;	//best training examples in this unit, keyed by distance
	private int numMappedTEx;						//# of mapped training examples to this node
	//private int ftrTypeMapBuilt;					//the feature type the map was built with - 0 == unmodded, 1 == std'ized, 2 == normalized  (or no normalized ?? , no way of telling what appropriate magnitude should be)
	
// ftrMap, stdFtrMap, normFtrMap;
	//feature type denotes what kind of features the tkns being sent represent - 0 is unmodded, 1 is standardized across all data for each feature, 2 is normalized across all features for single data point
	//TODO need to support normalized data by setting original magnitude of data
	public SOMMapNodeExample(SOMMapData _map, Tuple<Integer,Integer> _mapNode, float[] _ftrs) {
		super(_map, "MapNode_"+_mapNode.x+"_"+_mapNode.y);
		//ftrTypeMapBuilt = _ftrType;
		if(_ftrs.length != 0){	setFtrsFromFloatAra(_ftrs);	}
		//allJPs = buildJPsFromFtrAra(_ftrs, ftrThresh);
		initMapNode( _mapNode);
	}
	
	//feature type denotes what kind of features the tkns being sent represent
	public SOMMapNodeExample(SOMMapData _map,Tuple<Integer,Integer> _mapNode, String[] _strftrs) {
		super(_map, "MapNode_"+_mapNode.x+"_"+_mapNode.y);
		//ftrTypeMapBuilt = _ftrType;
		if(_strftrs.length != 0){	setFtrsFromStrAra(_strftrs);	}
		initMapNode( _mapNode);
	}
	
	private void setFtrsFromFloatAra(float[] _ftrs) {
		ftrMap = new TreeMap<Integer, Float>();
		float ftrVecSqMag = 0.0f;
		for(int i = 0; i < _ftrs.length; i++) {	
			Float val =  _ftrs[i];
			if (val > ftrThresh) {
				ftrVecSqMag+=val*val;
				ftrMap.put(i, val);
				int jp = mapData.jpByIdx[i];
				allJPs.add(jp);
				setMapOfJpWts(jp, val, ftrMapTypeKey);				
			}
		}
		buildAllJPFtrIDXsJPs();
		ftrVecMag = (float) Math.sqrt(ftrVecSqMag);		
		ftrsBuilt = true;		
		buildNormFtrData();		
	}//setFtrsFromFloatAra	
	
	private void setFtrsFromStrAra(String [] tkns){
		ftrMap = new TreeMap<Integer, Float>();
		float ftrVecSqMag = 0.0f;
		for(int i = 0; i < tkns.length; i++) {	
			Float val = Float.parseFloat(tkns[i]);
			if (val > ftrThresh) {
				ftrVecSqMag+=val*val;
				ftrMap.put(i, val);
				int jp = mapData.jpByIdx[i];
				allJPs.add(jp);
				setMapOfJpWts(jp, val, ftrMapTypeKey);
			}
		}
		buildAllJPFtrIDXsJPs();
		ftrVecMag = (float) Math.sqrt(ftrVecSqMag);	
		ftrsBuilt = true;		
		buildNormFtrData();			
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
		System.out.println("SOMMapNodeExample::buildStdFtrsMap : Calling inappropriate buildStdFtrsMap for SOMMapNodeExample : should call buildStdFtrsMap_MapNode from SOMDataLoader using trained map w/arrays of per feature mins and diffs");		
	}
	
	//called by SOMDataLoader - these are standardized based on data mins and diffs seen in -map nodes- feature data, not in training data
	//
	public void buildStdFtrsMapFromFtrData_MapNode(float[] minsAra, float[] diffsAra) {
		stdFtrMap = new TreeMap<Integer, Float>();
		if (allJPs.size() > 0) {
			Integer destIDX;
			for (Integer jp : allJPs) {
				destIDX = mapData.jpToFtrIDX.get(jp);
				Float lb = minsAra[destIDX], 
						diff = diffsAra[destIDX];
				float val = 0.0f;
				if (diff==0) {//same min and max
					if (lb > 0) {	val = 1.0f;}//only a single value same min and max-> set feature value to 1.0
					else {val= 0.0f;}
				} else {
					val = (ftrMap.get(destIDX)-lb)/diff;
				}
				stdFtrMap.put(destIDX,val);
				setMapOfJpWts(jp, val, stdFtrMapTypeKey);
			}//for each jp
		}
		stdFtrsBuilt = true;
		//by here all maps of per-type, per-feature val arrays of jps should be built
		buildMapOfJPsToRank();		
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
	
	public void drawMeSmall(SOM_StraffordMain p){
		p.pushMatrix();p.pushStyle();
		//show(myPointf P, float rad, int det, int[] fclr, int[] sclr, int tclr, String txt, boolean useBKGBox) 
		p.show(mapLoc, 2, 2, p.gui_Cyan, p.gui_Cyan, p.gui_White, this.OID); 		
		p.popStyle();p.popMatrix();		
	}
	//only draw bmu if lower than rankThresh
	@Override
	public void drawMeMap(SOM_StraffordMain p, int clr, dataClass lblBlnk){
//		Integer rank = getJPRankForMap(stdFtrMapTypeKey, curJP);
//		if (rank > rankThresh) {return;}//some map nodes won't have any structures yet if they haven't been mapped to
		p.pushMatrix();p.pushStyle();
		//p.show(mapLoc, getRad(), drawDet, label.clrVal,label.clrVal);		
		p.show(mapLoc, getRad(), drawDet, clr+1, clr+1);		
		p.popStyle();p.popMatrix();		
	}
	@Override
	public void drawMeWithWt(SOM_StraffordMain p, float wt, int clr, dataClass lblBlnk){
//		Integer rank = getJPRankForMap(stdFtrMapTypeKey, curJP);
//		if (rank > rankThresh) {return;}//some map nodes won't have any structures yet if they haven't been mapped to
		p.pushMatrix();p.pushStyle();
		//p.show(mapLoc, getRad(), drawDet, label.clrVal,label.clrVal);		
		//p.show(mapLoc, 10.0f*wt, drawDet, clr+1, clr+1);		
		p.show(mapLoc, 10.0f*wt, 2,  clr+1, clr+1, p.gui_White, ""+this.OID+":"+String.format("%.4f", wt)); 		
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
		rad = 1.0f;
		drawDet = 2;
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
	
	//set map location for this example
	public void setMapLoc(myPointf _pt){mapLoc = new myPointf(_pt);}
	
	//override drawing in map nodes
	public void drawMeMap(SOM_StraffordMain p, int clr, dataClass label){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at mapLoc
		p.show(mapLoc, rad,drawDet, label.clrVal,label.clrVal);
		p.popStyle();p.popMatrix();		
	}//drawMe
	
	public void drawMeWithWt(SOM_StraffordMain p, float wt, int clr, dataClass lblBlnk){
//		Integer rank = getJPRankForMap(stdFtrMapTypeKey, curJP);
//		if (rank > rankThresh) {return;}//some map nodes won't have any structures yet if they haven't been mapped to
		p.pushMatrix();p.pushStyle();
		//p.show(mapLoc, getRad(), drawDet, label.clrVal,label.clrVal);		
		p.show(mapLoc, 10.0f*wt, drawDet, clr+1, clr+1);		
		p.popStyle();p.popMatrix();		
	}
	
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
	
	public void drawMeRanked(SOM_StraffordMain p, String lbl, int[] clr, float rad, int rank){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label and no background box	
		p.show(mapLoc, rad, drawDet, clr, clr, SOM_StraffordMain.gui_White, lbl, false);
		p.popStyle();p.popMatrix();
	}
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
		String res = "JP : " + jp + " | JPGrp : " + jpg + (occurrences.size() > 1 ? "\n" : "");
		for (Date dat : occurrences.keySet()) {
			Tuple<Integer, Integer> occData = occurrences.get(dat);
			Integer opt = occData.y;
			String optStr = "";
			if ((-2 <= opt) && (opt <= 2)) {optStr = " | Opt : " + opt;} //is an opt occurence
			res += "\t# occurences : " + occData.x + optStr;		
		}
		return res;
	}		
}//class jpOccurenceData

/**
 * this class will hold either event data for a single OID for a single date
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
	public void procJPOccForAllJps(ProspectExample ex, TreeMap<Integer, jpOccurrenceData> jpOccMap, String type) {
		for (JpgJpDataRecord rec : listOfJpgsJps) {
			int opt = rec.getOptVal();
			Integer jpg = rec.getJPG();
			ArrayList<Integer> jps = rec.getJPlist();
			//this is sentinel value marker for opt events where all jpgs/jps are specified
			if((jpg == -10) && (jps.get(0) == -9)) {
				if  (opt <= 0) {continue;}		//this is negative opt across all records, so ignore, for training data purposes
				int jp = jps.get(0);			//from here we are processing a positive opt record across all jps
				jpOccurrenceData jpOcc = ex.getOptAllOccObj();
				if (jpOcc==null) {jpOcc = new jpOccurrenceData(jp, jpg);}
				jpOcc.addOccurence(eventDate, rec.getOptVal());		
				ex.setOptAllOccObj(jpOcc);				
			} else {
				for (Integer jp : jps) {
					jpOccurrenceData jpOcc = jpOccMap.get(jp);
					if (jpOcc==null) {jpOcc = new jpOccurrenceData(jp, jpg);}
					jpOcc.addOccurence(eventDate, rec.getOptVal());		
					//add this occurence object to map at idx jp
					jpOccMap.put(jp, jpOcc);
				}
			}//if is opt-across-alljps event or not
		}//for all jpgjp record data	
	}//procJPOccForAllJps
	 
	public abstract void addEventDataFromCSVString(String _csvDataStr);
	protected void addEventDataRecsFromCSVStr(Integer optKey, String _csvDataStr) {
		//boolean isOptEvent = ((-2 <= optKey) && (optKey <= 2));
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
		boolean isOptEvent = ((-2 <= optVal) && (optVal <= 2));
		TreeMap<Integer, ArrayList<Integer>> newEvMapOfJPAras = ev.rawJpMapOfArrays;
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
		addEventDataFromEventObj(ev);}	//put in child ctor in case child-event specific data needed for training	
	
	public OrderEventTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){
		super(_evIDStr, _evTypeStr, _evDateStr);
		addEventDataFromCSVString(_evntStr);	}//put in child ctor in case child-event specific data needed for training	
	
	@Override
	public void addEventDataFromEventObj(EventRawData ev) {	super.addEventDataRecsFromRaw(FauxOptVal,ev);}//addEventDataFromEventObj
	@Override
	public void addEventDataFromCSVString(String _csvDataStr) {super.addEventDataRecsFromCSVStr(FauxOptVal,_csvDataStr);	}//addEventDataFromCSVString
}//class OrderEventTrainData

class LinkEventTrainData extends StraffEvntTrainData{

	public LinkEventTrainData(LinkEvent ev) {
		super(ev);
		addEventDataFromEventObj(ev);}	//put in child ctor in case child-event specific data needed for training	
	
	public LinkEventTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){
		super(_evIDStr, _evTypeStr, _evDateStr);
		addEventDataFromCSVString(_evntStr);}	//put in child ctor in case child-event specific data needed for training		
	
	@Override
	public void addEventDataFromEventObj(EventRawData ev) {super.addEventDataRecsFromRaw(FauxOptVal,ev);}//addEventDataFromEventObj
	@Override
	public void addEventDataFromCSVString(String _csvDataStr) {	super.addEventDataRecsFromCSVStr(FauxOptVal,_csvDataStr);}//addEventDataFromCSVString
}//class LinkEventTrainData

class OptEventTrainData extends StraffEvntTrainData{
	public OptEventTrainData(OptEvent ev) {
		super(ev);		
		addEventDataFromEventObj(ev);}	//put in child ctor in case child-event specific data needed for training	
	
	public OptEventTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){
		super(_evIDStr, _evTypeStr, _evDateStr);
		addEventDataFromCSVString(_evntStr);}	//put in child ctor in case child-event specific data needed for training		
	
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
 * This structure corresponds to how a single prospects or event record is stored in the db
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
