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
	protected static SOMMapManager mapData;
	protected static MonitorJpJpgrp jpJpgMon;
	//corresponds to OID in prospect database - primary key of all this data is OID in prospects
	public final String OID;	
	
	//prefix to use for product example IDs
	protected static final String IDprfx = "TC_Tag";	
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
	
	//use a map per feature type : unmodified, normalized, standardized, of a map to hold the features sorted by weight as key, value is array of jps at that weight -submap needs to be instanced in descending key order
	private TreeMap<String, TreeMap<Float, ArrayList<Integer>>> mapOfTopWtJps;	
	//a map per feature type : unmodified, normalized, standardized, of a map of jps and their relative "rank" in this particular example, as determined by the weight calc
	private TreeMap<String, TreeMap<Integer,Integer>> mapOfJpsVsWtRank;
	//keys for above maps
	public static final String[] jpMapTypeKeys = new String[] {"ftrMap", "stdFtrMap", "normFtrMap"};
	public static final int ftrMapTypeKey = 0, stdFtrMapTypeKey = 1, normFtrMapTypeKey = 2;

	/////////////////////////////
	// from old DataPoint data
	
	//reference to map node that best matches this node
	protected SOMMapNodeExample bmu;			
	//this is the distance, using the chosen distance measure, to the best matching unit of the map for this example
	protected double _distToBMU;
	//to hold 9 node neighborhood surrounding bmu - using array of nodes because nodes can be equidistant form multiple nodes
	//TODO set a list of these nodes for each SOMMapNodeExample upon their construction? will this speed up anything?
	private TreeMap<Double, ArrayList<SOMMapNodeExample>> mapNodeNghbrs;
	//display label describing this example TODO this needs refinement for strafford-specific data
	protected dataClass label;
	
	public StraffSOMExample(SOMMapManager _map, String _id) {
		super();
		mapData=_map;
		jpJpgMon = mapData.jpJpgrpMon;
		OID = _id;
		_distToBMU = 0.0;
		ftrsBuilt = false;
		stdFtrsBuilt = false;
		normFtrsBuilt = false;
		isBadTrainExample = false;	
		ftrMap = new TreeMap<Integer, Float>();			//feature map for SOM Map Nodes may not correspond directly to magnitudes seen in training data. 
		stdFtrMap = new TreeMap<Integer, Float>();	
		normFtrMap=new TreeMap<Integer, Float>();	
		allJPs = new HashSet<Integer> ();
		
		//this map is a map of maps in descending order - called by calc object as well as buildNormFtrData
		mapOfTopWtJps = new TreeMap<String, TreeMap<Float, ArrayList<Integer>>>();//(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}}); 
		mapOfJpsVsWtRank = new TreeMap<String, TreeMap<Integer,Integer>>();
		for(String ftrType : jpMapTypeKeys) {//type of features
			TreeMap<Float, ArrayList<Integer>> tmpFltMap = new TreeMap<Float, ArrayList<Integer>>(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}});//descending key order
			TreeMap<Integer,Integer> tmpIntMap = new TreeMap<Integer,Integer>();
			mapOfTopWtJps.put(ftrType, tmpFltMap);
			mapOfJpsVsWtRank.put(ftrType, tmpIntMap);
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
	//return all jpg/jps in this example record
	protected abstract HashSet<Tuple<Integer,Integer>> getSetOfAllJpgJpData();
	
	public boolean isBadExample() {return isBadTrainExample;}
	public void setIsBadExample(boolean val) { isBadTrainExample=val;}
	////////////////
	// event processing 
	///////////////
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
	
	/**
	 * add object keyed by addDate, either adding to existing list or building a new list if none exists
	 * @param _optObj : object to add
	 * @param map : date-keyed map to add object to
	 * @param type : int type of object
	 */
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
		if(mapData == null){					mapData.dispMessage("SOMMapNodeExample","checkForErrors","FATAL ERROR : SOMMapData object is null!");		return true;}//if mapdata is null then stop - should have come up before here anyway
		if(_n==null){							mapData.dispMessage("SOMMapNodeExample","checkForErrors","_n is null!");		inError=true;} 
		else if(_n.mapLoc == null){				mapData.dispMessage("SOMMapNodeExample","checkForErrors","_n has no maploc!");	inError=true;}
		if(dataVar == null){					mapData.dispMessage("SOMMapNodeExample","checkForErrors","map variance not calculated : datavar is null!");	inError=true;	}
		return inError;
	}//checkForErrors
	
	//take map of jpwts to arrays of jps and build maps of jps to rank
	//This will give a map of all present JPs for this example and the rank of that jp.  null entries mean no rank
	//mapOfTopWtJps must be built for all types of weights by here
	public void buildMapOfJPsToRank() {
		for (String mapToGet : jpMapTypeKeys) {//for each type
			Integer rank = 0;
			TreeMap<Float, ArrayList<Integer>> mapOfAras = mapOfTopWtJps.get(mapToGet);
			if (mapOfAras == null) {mapData.dispMessage("StraffSOMExample", "buildMapOfJPsToRank", "For OID : "+ OID + " mapOfTopWtJps entry " +mapToGet + " does not exist yet.  Method called before all features are calculated.");return;}
			TreeMap<Integer,Integer> mapOfRanks = mapOfJpsVsWtRank.get(mapToGet);
			for (Float wtVal : mapOfAras.keySet()) {
				ArrayList<Integer> jpsAtRank = mapOfAras.get(wtVal);
				for (Integer jp : jpsAtRank) {	mapOfRanks.put(jp, rank);}
				++rank;
			}
			mapOfJpsVsWtRank.put(mapToGet, mapOfRanks);//probably not necessary		
		}		
	}//buildMapOfJPsToRank
	
	//return the rank of the passed jp
	protected Integer getJPRankForMap(int mapToGetIDX, int jp) {
		String mapToGet = jpMapTypeKeys[mapToGetIDX];
		TreeMap<Integer,Integer> mapOfRanks = mapOfJpsVsWtRank.get(mapToGet);
		Integer rank = mapOfRanks.get(jp);
		if (rank==null) {rank = mapData.numFtrs;}
		return rank;
	}//getJPRankForMap
	
	//add passed map node, with passed feature distance, to neighborhood nodes
	protected void addMapUnitToNeighbrhdMap(SOMMapNodeExample _n, double _dist) {
		ArrayList<SOMMapNodeExample> tmpMap = mapNodeNghbrs.get(_dist);
		if (null==tmpMap) {tmpMap = new ArrayList<SOMMapNodeExample>();}
		tmpMap.add(_n);
		mapNodeNghbrs.put(_dist, tmpMap);		
	}//addMapUnitToNeighbrhdMap
	
	//once BMU and distToBMU is set, init map and add node to neighborhood map keyed by dist
	protected void addBMUToNeighbrhdMap() {
		mapNodeNghbrs = new TreeMap<Double, ArrayList<SOMMapNodeExample>>();
		ArrayList<SOMMapNodeExample> tmpMap = new ArrayList<SOMMapNodeExample>();
		tmpMap.add(bmu);
		mapNodeLoc.set(bmu.mapLoc);
		mapNodeNghbrs.put(_distToBMU, tmpMap);		//to hold 9 neighbor nodes and their ftr distance		
	}//addBMUToNeighbrhdMap
	
	//this adds the passed node as this example's best matching unit on the map
	//this also adds this data point to the map's node with a key of the distance
	//dataVar is variance of feature weights of map nodes.  this is for chi-squared distance measurements
	public void setBMU(SOMMapNodeExample _n, int _ftrType){
		if (checkForErrors(_n, mapData.map_ftrsVar)) {return;}//if true then this is catastrophic error and should interrupt flow here
		bmu = _n;	
		_distToBMU = getSqDistFromFtrType(bmu,  _ftrType);// mapData.dpDistFunc(bmu, this);
		addBMUToNeighbrhdMap();
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes( _ftrType);
		//dist here is distance of this training example to map node
		_n.addBMUExample(_distToBMU, this);
	}//setBMU
	
	//this adds the passed node as this example's best matching unit on the map
	//this also adds this data point to the map's node with a key of the distance
	//dataVar is variance of feature weights of map nodes.  this is for chi-squared distance measurements
	public void setBMU_ChiSq(SOMMapNodeExample _n, int _ftrType){
		if (checkForErrors(_n, mapData.map_ftrsVar)) {return;}//if true then this is catastrophic error and should interrupt flow here
		bmu = _n;	
		_distToBMU = getSqDistFromFtrType_ChiSq(bmu,  _ftrType);// mapData.dpDistFunc(bmu, this);
		addBMUToNeighbrhdMap();
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes_ChiSq(_ftrType);		
		//dist here is distance of this training example to map node
		_n.addBMUExample(_distToBMU, this);
	}//setBMU
		
	//use 9 map node neighborhood around this node, accounting for torroidal map, to find exact location on map, using chi_sq dist calc
	//for this node (put in mapLoc) by using weighted average of mapNodeLocs of neighbor nodes,
	//where the weight is the inverse feature distance 
	// mapNodeNghbrs (9 node neighborood) must be set before this is called, and has bmu set as closest, with key being distance
	//distsToNodes is distance of this node to all map nodes in neighborhood
	protected void buildNghbrhdMapNodes_ChiSq(int _ftrType){
		int mapColsSize = mapData.getMapNodeCols(), mapRowSize = mapData.getMapNodeRows();
		int mapCtrX = bmu.mapNodeCoord.x, mapCtrY = bmu.mapNodeCoord.y;
		Integer xTup, yTup;
		//go through all mapData.MapNodes 
		for (int x=-1; x<2;++x) {//should be 3 cols
			for (int y=-1; y<2;++y) {//3 rows
				if((y==0) && (x==0)){continue;}//ignore "center" node - this is bmu
				xTup = (mapCtrX + x + mapColsSize) % mapColsSize;
				yTup = (mapCtrY + y + mapRowSize) % mapRowSize;		
				Tuple<Integer,Integer> key = new Tuple<Integer, Integer>(xTup, yTup);
				SOMMapNodeExample node = mapData.MapNodes.get(key);
				double dist = getSqDistFromFtrType_ChiSq(node, _ftrType);
				addMapUnitToNeighbrhdMap(node, dist);
			}//for each row/y
		}//for each column/x	
		setExactMapLoc();
	}//addAllMapNodeNeighbors
	
	//use 9 map node neighborhood around this node, accounting for torroidal map, to find exact location on map
	//for this node (put in mapLoc) by using weighted average of mapNodeLocs of neighbor nodes,
	//where the weight is the inverse feature distance 
	// mapNodeNghbrs (9 node neighborood) must be set before this is called, and has bmu set as closest, with key being distance
	//distsToNodes is distance of this node to all map nodes in neighborhood
	protected void buildNghbrhdMapNodes(int _ftrType){
		int mapColsSize = mapData.getMapNodeCols(), mapRowSize = mapData.getMapNodeRows();
		int mapCtrX = bmu.mapNodeCoord.x, mapCtrY = bmu.mapNodeCoord.y;
		Integer xTup, yTup;
		//go through all mapData.MapNodes 
		for (int x=-1; x<2;++x) {//should be 3 cols
			for (int y=-1; y<2;++y) {//3 rows
				if((y==0) && (x==0)){continue;}//ignore "center" node - this is bmu
				xTup = (mapCtrX + x + mapColsSize) % mapColsSize;
				yTup = (mapCtrY + y + mapRowSize) % mapRowSize;		
				Tuple<Integer,Integer> key = new Tuple<Integer, Integer>(xTup, yTup);
				SOMMapNodeExample node = mapData.MapNodes.get(key);
				double dist = getSqDistFromFtrType(node, _ftrType);
				addMapUnitToNeighbrhdMap(node, dist);
			}//for each row/y
		}//for each column/x	
		setExactMapLoc();
	}//addAllMapNodeNeighbors
	
	private float calcWrapMod(Integer bmuCoord, Integer exCoord, float mapDim) {
		float mod = 0.0f;
		if (exCoord > bmuCoord+1) {//this means that the example is actually lower than bmu, but due to wrap it is on the other side of the map, so add
			mod = mapDim;
		} else if (exCoord < bmuCoord-1) {//this means that the example is actually higher coord than bmu, but wrapped around to the other side, so subtract dim 
			mod = -mapDim;
		}		
		return mod;
	}//calcWrapMod
	
	//return location of passed map node, with value added or subtracted based on whether it wraps around map
	private myPointf findNodeLocWrap(SOMMapNodeExample ex, Integer bmuX, Integer bmuY, float mapW, float mapH) {
		Integer ex_X = ex.mapNodeCoord.x, ex_Y = ex.mapNodeCoord.y;
		myPointf loc = new myPointf(ex.mapLoc);
		float locXMod = calcWrapMod(bmuX, ex_X, mapW);
		float locYMod = calcWrapMod(bmuY, ex_Y, mapH);		
		loc._sub(locXMod,locYMod, 0.0f);
		return loc;		
	}//findNodeLocWrap
	
	//determine map location based on neighborhood nodes, accounting for torroidal wrap
	private void setExactMapLoc() {
		myPointf totalLoc = new myPointf(), locToAdd;
		float ttlInvDist = 0.0f,invDistP1;
		for (double _dist : mapNodeNghbrs.keySet()) {
			invDistP1 = (float) (1.0f/(1.0f+_dist));					//handles 0 dist - max will be 0, min will be some fraction
			ArrayList<SOMMapNodeExample> tmpMap = mapNodeNghbrs.get(_dist);
			for (SOMMapNodeExample ex : tmpMap) {//public static myPointf _mult(myPointf p, float n){ myPointf result = new myPointf(p.x * n, p.y * n, p.z * n); return result;}                          //1 pt, 1 float				
				ttlInvDist +=invDistP1;
			}			
		}
		float mapW = mapData.getMapWidth(), mapH = mapData.getMapHeight();
		Integer bmuX = bmu.mapNodeCoord.x,  bmuY = bmu.mapNodeCoord.y;
		//scale by ttlInvDist so that all distance wts sum to 1
		for (double _dist : mapNodeNghbrs.keySet()) {
			invDistP1 = ((float) (1.0f/(1.0f+_dist))/ttlInvDist);					//handles 0 dist - max will be 0, min will be some fraction
			ArrayList<SOMMapNodeExample> tmpMap = mapNodeNghbrs.get(_dist);
			for (SOMMapNodeExample ex : tmpMap) {//public static myPointf _mult(myPointf p, float n){ myPointf result = new myPointf(p.x * n, p.y * n, p.z * n); return result;}                          //1 pt, 1 float			
				//if ex is more than 1 in x or y from bmu, then wrap arround, need to add (or subtract) x or y dim of map
				locToAdd = findNodeLocWrap(ex, bmuX, bmuY, mapW, mapH);
				totalLoc._add(myPointf._mult(locToAdd, invDistP1));				
			}			
		}
		totalLoc.x += mapW;totalLoc.x %= mapW;
		totalLoc.y += mapH;totalLoc.y %= mapH;
		
		this.mapLoc.set(totalLoc);
	}//setExactMapLoc
	
	//call from calc or from objects that manage norm/std ftrs - build structure registering weight of jps in ftr vector mapToGet in descending strength
	protected void setMapOfJpWts(int jp, float wt, int mapToGetIDX) {
		String mapToGet = jpMapTypeKeys[mapToGetIDX];
		TreeMap<Float, ArrayList<Integer>> map = mapOfTopWtJps.get(mapToGet);
		//shouldn't be null - means using inappropriate key
		if(map == null) {mapData.dispMessage("StraffSOMExample","setMapOfJpWts","Using inappropriate key to access mapOfTopJps : " + mapToGet + " No submap exists with this key."); return;}		 
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
		buildNormFtrData();//once ftr map is built can normalize easily
	}//buildFeatureVector
	
	protected void buildAllJPFtrIDXsJPs() {
		allJPFtrIDXs = new ArrayList<Integer>();
		for(Integer jp : allJPs) {
			Integer jpIDX = jpJpgMon.getJpToFtrIDX(jp);
			if(jpIDX==null) {mapData.dispMessage("StraffSOMExample","buildAllJPFtrIDXsJPs","ERROR!  null value in  jpJpgMon.getJpToFtrIDX("+jp+")" );}
			allJPFtrIDXs.add(jpJpgMon.getJpToFtrIDX(jp));
		}
	}//buildAllJPFtrIDXsJPs
	
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
			if ((ftr!= null) && (ftr > thresh)) {jps.add(jpJpgMon.getJpByIdx(i));			}
		}
		return jps;	
	}//buildJPsFromFtrVec
	//build normalized vector of data - only after features have been set
	protected void buildNormFtrData() {
		if(!ftrsBuilt) {mapData.dispMessage("StraffSOMExample","buildNormFtrData","OID : " + OID + " : Features not built, cannot normalize feature data");return;}
		normFtrMap=new TreeMap<Integer, Float>();
		if(this.ftrVecMag == 0) {return;}
		for (Integer IDX : allJPFtrIDXs) {
			int jp = jpJpgMon.getJpByIdx(IDX);
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
			int jp = jpJpgMon.getJpByIdx(destIDX);
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
	}//standardizeFeatureVector		getSqDistFromFtrType
	
	protected double getSqDistFromFtrType_ChiSq(StraffSOMExample fromNode, int _ftrType){
		switch(_ftrType){
			case SOMMapManager.useUnmoddedDat : {return _getChiSqFtrDist(fromNode.ftrMap, ftrMap); }
			case SOMMapManager.useNormedDat  : {return _getChiSqFtrDist(fromNode.normFtrMap, normFtrMap);}
			case SOMMapManager.useScaledDat  : {return _getChiSqFtrDist(fromNode.stdFtrMap, stdFtrMap); }
			default : {return _getChiSqFtrDist(fromNode.ftrMap, ftrMap); }
		}	
	}//getDistFromFtrType
	
	//return the distance between this map's ftrs and the passed ftrMap
	protected double getSqDistFromFtrType(StraffSOMExample fromNode, int _ftrType){
		switch(_ftrType){
			case SOMMapManager.useUnmoddedDat : {return _getSqFtrDist(fromNode.ftrMap, ftrMap); }
			case SOMMapManager.useNormedDat  : {return _getSqFtrDist(fromNode.normFtrMap, normFtrMap);}
			case SOMMapManager.useScaledDat  : {return _getSqFtrDist(fromNode.stdFtrMap, stdFtrMap); }
			default : {return _getSqFtrDist(fromNode.ftrMap, ftrMap); }
		}
	}//getDistFromFtrType
	
	//get square distance between two feature map values
	private double _getSqFtrDist(TreeMap<Integer, Float> fromftrMap, TreeMap<Integer, Float> toftrMap) {
		double res = 0.0;
		Set<Integer> allIdxs = new HashSet<Integer>(fromftrMap.keySet());
		allIdxs.addAll(toftrMap.keySet());
		for (Integer key : allIdxs) {//either map will have this key
			Float frmVal = fromftrMap.get(key);
			if(frmVal == null) {frmVal = 0.0f;}
			Float toVal = toftrMap.get(key);
			if(toVal == null) {toVal = 0.0f;}
			Float diff = toVal - frmVal;
			res += (diff * diff);
		}
		return res;
	}//_getSqFtrDist	
	
	//get square chi-distance between two feature map values - weighted by variance of data
	private double _getChiSqFtrDist(TreeMap<Integer, Float> fromftrMap, TreeMap<Integer, Float> toftrMap) {
		double res = 0.0f;
		float[] mapFtrVar = mapData.map_ftrsVar;
		Set<Integer> allIdxs = new HashSet<Integer>(fromftrMap.keySet());
		allIdxs.addAll(toftrMap.keySet());
		for (Integer key : allIdxs) {//either map will have this key
			Float frmVal = fromftrMap.get(key);
			if(frmVal == null) {frmVal = 0.0f;}
			Float toVal = toftrMap.get(key);
			if(toVal == null) {toVal = 0.0f;}
			Float diff = toVal - frmVal;
			res += (diff * diff)/mapFtrVar[key];
		}
		return res;
	}//_getSqFtrDist		

	
	public dataClass getLabel(){return label;}	
	public final void buildLabel() {
		label = new dataClass(OID,"lbl:"+OID,"unitialized Description", null);
		label.clrVal = new int[] {255,0,0,255};
		//class-specific customization of label
		buildLabelIndiv();	
	}//buildLabel() 

	//implement this for all inheriting classes - use already-known data to build label for example
	public abstract void buildLabelIndiv();
	
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
			case SOMMapManager.useUnmoddedDat : {return _toCSVString(ftrMap); }
			case SOMMapManager.useNormedDat  : {return _toCSVString(normFtrsBuilt ? normFtrMap : ftrMap);}
			case SOMMapManager.useScaledDat  : {return _toCSVString(stdFtrsBuilt ? stdFtrMap : ftrMap); }
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
			case SOMMapManager.useUnmoddedDat : {return _toLRNString(ftrMap, sep); }
			case SOMMapManager.useNormedDat   : {return _toLRNString(normFtrsBuilt ? normFtrMap : ftrMap, sep);}
			case SOMMapManager.useScaledDat   : {return _toLRNString(stdFtrsBuilt ? stdFtrMap : ftrMap, sep); }
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
			case SOMMapManager.useUnmoddedDat : {return _toSVMString(ftrMap); }
			case SOMMapManager.useNormedDat   : {return _toSVMString(normFtrsBuilt ? normFtrMap : ftrMap);}
			case SOMMapManager.useScaledDat   : {return _toSVMString(stdFtrsBuilt ? stdFtrMap : ftrMap); }
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
			case SOMMapManager.useUnmoddedDat : {return ftrMap; }
			case SOMMapManager.useNormedDat   : {return (normFtrsBuilt ? normFtrMap : ftrMap);}
			case SOMMapManager.useScaledDat   : {return (stdFtrsBuilt ? stdFtrMap : ftrMap); }
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
	protected String dispFtrMapVals(TreeMap<Integer, Float> ftrs) {
		String res = "";
		if((ftrs==null) || (ftrs.size() == 0)){res+=" None\n";} 
		//else {res +="\n\t";for(int i=0;i<ftrs.length;++i){res += ""+String.format("%03d", i) +":"+ String.format("%1.4g", ftrs[i]) + " | "; if((numFtrs > 40) && ((i+1)%30 == 0)){res +="\n\t";}}}
		else {
			for(Integer i : ftrs.keySet()){
				Float ftr = ftrs.get(i);
				int jp = jpJpgMon.getJpByIdx(i);
				res += "jp : " + jp + " | idx : " + i + " | val : " + String.format("%1.4g",  ftr) + " || ";}}
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
	
	//is this datapoint used for training 
	private boolean isTrainingData;
	//this is index for this data point in training/testing data array; original index in preshuffled array (reflecting build order)
	private int testTrainDataIDX;
	
	//build this object based on prospect object
	public ProspectExample(SOMMapManager _map,prospectData _prspctData) {
		super(_map,_prspctData.OID);	
		if( _prspctData.rawJpMapOfArrays.size() > 0) {
			prs_JPGrp = _prspctData.rawJpMapOfArrays.firstKey();
			prs_JP = _prspctData.rawJpMapOfArrays.get(prs_JPGrp).get(0);
		} else {prs_JPGrp=0;prs_JP=0;}
		prs_LUDate = _prspctData.getDate();
		initObjsData() ;
	
	}//prospectData ctor
	
	//build this object based on csv string - rebuild data from csv string columns 4+
	public ProspectExample(SOMMapManager _map,String _OID, String _csvDataStr) {
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
	

	@Override
	public void buildLabelIndiv() {
		// TODO Auto-generated method stub
		
	}

	
	//this sets/gets this StraffSOMExample's object that denotes a positive opt setting for all jps
	public void setOptAllOccObj(jpOccurrenceData _optAllOcc) {posOptAllEventObj = _optAllOcc;	}//setOptAllOccObj
	public jpOccurrenceData getOptAllOccObj() {		return posOptAllEventObj;	}
		
	public  TreeMap<Integer, jpOccurrenceData> getOcccurenceMap(String key) {
		TreeMap<Integer, jpOccurrenceData> res = JpOccurrences.get(key);
		if (res==null) {mapData.dispMessage("ProspectExample","getOcccurenceMap","JpOccurrences map does not have key : " + key); return null;}
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
		rad = 3.0f;
		eventsByDateMap = new TreeMap<String, TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>>>();
		for (String key : mapKeys) {eventsByDateMap.put(key, new TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> ());	}
		//occurrence structures - keyed by type, then by JP
		JpOccurrences = new TreeMap<String, TreeMap<Integer, jpOccurrenceData>> ();
		//set these when this data is partitioned into testing and training data
		isTrainingData = false;
		testTrainDataIDX = -1;
	}//initObjsData
	
	public void setIsTrainingDataIDX(boolean val, int idx) {isTrainingData=val; testTrainDataIDX=idx;}
	public boolean getIsTrainingData() {return isTrainingData;}
	public int getTestTrainIDX() {return testTrainDataIDX;}

	//any processing that must occur once all constituent data records are added to this example - must be called externally, before ftr vec is built
	@Override
	public void finalizeBuild() {
		buildOccurrenceStructs();	
		//all jps holds all jps in this example
		allJPs = buildJPListFromOccs();
		if(allJPs.size() == 0) {setIsBadExample(true);		}//means there's no valid jp's 
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
				hasJP_ev = hasJP_ordr || hasJP_opt || hasJP_link,
				hasJP = hasJP_prspct || hasJP_ev;
		boolean[] res = new boolean[] {hasJP, hasJP_prspct, hasJP_ev, hasJP_ordr,hasJP_opt, hasJP_link};
		return res;		
	}//check if JP exists, and where
	
	//return all jpg/jps in this prospect record
	protected HashSet<Tuple<Integer,Integer>> getSetOfAllJpgJpData(){
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
		case SOMMapManager.prspctIDX : 	{mapData.dispMessage("ProspectExample","addObj","ERROR attempting to add prospect raw data as event data. Ignored");return;}
		case SOMMapManager.orderEvntIDX : 	{
			addDataToTrainMap((OrderEvent)obj,eventsByDateMap.get(mapKeys[0]), 0); 
			return;}
		case SOMMapManager.optEvntIDX : 	{
			addDataToTrainMap((OptEvent)obj,eventsByDateMap.get(mapKeys[1]), 1); 
			return;}
		case SOMMapManager.linkEvntIDX : 	{
			addDataToTrainMap((LinkEvent)obj,eventsByDateMap.get(mapKeys[2]), 2); 
			return;}
		default :{mapData.dispMessage("ProspectExample","addObj","ERROR attempting to add unknown raw data type : " + type + " as event data. Ignored");return;}
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
	public void drawMeLblMap(SOM_StraffordMain p) {
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label	
		p.showNoBox(mapLoc, getRad(), 5, label.clrVal,label.clrVal, SOM_StraffordMain.gui_FaintGray, label.label);
		p.popStyle();p.popMatrix();			}
	
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
	private static final String csvColDescrPrfx = "ID,NumJPs";
	private static int IDcount = 0;	//incrementer so that all examples have unique ID	
	protected TcTagTrainData trainPrdctData;	
	
	//this array holds float reps of "sumtorial" of idx vals, used as denominators of ftr vectors so that 
	//arrays of jps of size idx will use this value as denominator, and (idx - jp idx)/denominator as weight value for ftr vec 
	private static float[] ordrWtAraPerSize;
	//this is a vector of all seen mins and maxs for wts for every product. Only used to calculate 
	private static TreeMap<Integer, Float> wtMins, wtMaxs, wtDists;	
	
	//keys of best matching units
	protected ArrayList<Tuple<Integer,Integer>> bmuKeys;
	//array of best matching units, if more than 1 has same distance from product
	protected ArrayList<SOMMapNodeExample> bmuList;
	//# of bmus this example has
	protected int numBMUs;
	
	public ProductExample(SOMMapManager _map, TcTagData data) {
		super(_map,IDprfx + "_" +  String.format("%09d", IDcount++));
		trainPrdctData = new TcTagTrainData(data);	
	}//ctor
	
	public ProductExample(SOMMapManager _map,String _OID, String _csvDataStr) {
		super(_map,_OID);
		trainPrdctData = new TcTagTrainData(_csvDataStr);
	}//ctor
	
	//call this once, before any load of data that will over-write the product examples is performed
	public static void initAllProdData() {
		ordrWtAraPerSize = new float[100];	//100 is arbitrary but much more than expected # of jps per product. dont expect a product to have anywhere near this many jps
		for (int i =1;i<ordrWtAraPerSize.length;++i) {ordrWtAraPerSize[i]=1.0f*(ordrWtAraPerSize[i-1]+i);}
		//manage mins and maxes seen for ftrs, keyed by ftr idx
		wtMins = new TreeMap<Integer, Float>();
		wtMaxs = new TreeMap<Integer, Float>();		
		wtDists = new TreeMap<Integer, Float>();		
	}//initAllProdData()

	@Override
	public void buildLabelIndiv() {
		//TODO build descriptive label based on product info		
	}
	
	//references current map of nodes, finds best matching unit and returns map of all map node tuple addresses and their ftr distances from this node
	//also build neighborhood nodes
	//two methods to minimize if calls for chisq dist vs regular euclidean dist
	public void findBMUFromNodes(TreeMap<Tuple<Integer,Integer>, SOMMapNodeExample> _MapNodes, int _type) {
		TreeMap<Double, ArrayList<SOMMapNodeExample>> mapNodes = new TreeMap<Double, ArrayList<SOMMapNodeExample>>();
		TreeMap<Tuple<Integer,Integer>, Double> all_MapNodeLocToSqDist = new TreeMap<Tuple<Integer,Integer>, Double>();			
		for (SOMMapNodeExample node : _MapNodes.values()) {
			double sqDistToNode = getSqDistFromFtrType(node, _type);
			ArrayList<SOMMapNodeExample> tmpAra = mapNodes.get(sqDistToNode);
			if(tmpAra == null) {tmpAra = new ArrayList<SOMMapNodeExample>();}
			tmpAra.add(node);
			mapNodes.put(sqDistToNode, tmpAra);		
			all_MapNodeLocToSqDist.put(node.mapNodeCoord, sqDistToNode);
		}				
		bmuKeys = new ArrayList<Tuple<Integer,Integer>>();
		Entry<Double, ArrayList<SOMMapNodeExample>> topEntry = mapNodes.firstEntry();
		_distToBMU = topEntry.getKey();
		bmuList = topEntry.getValue();
		numBMUs = bmuList.size();
		for (int i=0;i<numBMUs;++i) {	bmuKeys.add(i, bmuList.get(i).mapNodeCoord);	}
		SOMMapNodeExample bestUnit = null;
		if (numBMUs > 1) {
			//mapData.dispMessage("ProductExample", "addAllMapNodeNeighbors", "Product : " + this.OID +" has more than 1 bmu (unit with identical distance) : "+numBMUs);
			int maxNumExamples = 0;
			for (int i=0;i<numBMUs;++i) {
				SOMMapNodeExample node = bmuList.get(i);
				int numExamples = node.getNumExamples();
				if (numExamples > maxNumExamples) {
					maxNumExamples = numExamples;
					bestUnit = node;
				}
				//mapData.dispMessage("ProductExample", "addAllMapNodeNeighbors", "\tMap Node " + node.OID + " has "+node.getNumExamples() + " training examples.");				
			}
		} else {//only 1
			bestUnit = bmuList.get(0);
		}
		bmu = bestUnit;										//best unit to this product - if multiples then weighted by # of examples at this unit
		addBMUToNeighbrhdMap();
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes( _type);		
	}//findBMUFromNodes 	
	
	//references current map of nodes, finds best matching unit and returns map of all map node tuple addresses and their ftr distances from this node
	//using chi sq dist
	public void findBMUFromNodes_ChiSq(TreeMap<Tuple<Integer,Integer>, SOMMapNodeExample> _MapNodes, int _type) {
		TreeMap<Double, ArrayList<SOMMapNodeExample>> mapNodes = new TreeMap<Double, ArrayList<SOMMapNodeExample>>();
		TreeMap<Tuple<Integer,Integer>, Double> all_MapNodeLocToSqDist = new TreeMap<Tuple<Integer,Integer>, Double>();
		for (SOMMapNodeExample node : _MapNodes.values()) {
			double sqDistToNode = getSqDistFromFtrType_ChiSq(node, _type);
			ArrayList<SOMMapNodeExample> tmpAra = mapNodes.get(sqDistToNode);
			if(tmpAra == null) {tmpAra = new ArrayList<SOMMapNodeExample>();}
			tmpAra.add(node);
			mapNodes.put(sqDistToNode, tmpAra);		
			all_MapNodeLocToSqDist.put(node.mapNodeCoord, sqDistToNode);
		}				
		bmuKeys = new ArrayList<Tuple<Integer,Integer>>();
		Entry<Double, ArrayList<SOMMapNodeExample>> topEntry = mapNodes.firstEntry();
		_distToBMU = topEntry.getKey();
		bmuList = topEntry.getValue();
		numBMUs = bmuList.size();
		for (int i=0;i<numBMUs;++i) {			bmuKeys.add(i, bmuList.get(i).mapNodeCoord);		}
		SOMMapNodeExample bestUnit = null;
		if (numBMUs > 1) {
			//mapData.dispMessage("ProductExample", "addAllMapNodeNeighbors", "Product : " + this.OID +" has more than 1 bmu (unit with identical distance) : "+numBMUs);
			int maxNumExamples = 0;
			for (int i=0;i<numBMUs;++i) {
				SOMMapNodeExample node = bmuList.get(i);
				int numExamples = node.getNumExamples();
				if (numExamples > maxNumExamples) {
					maxNumExamples = numExamples;
					bestUnit = node;
				}
				//mapData.dispMessage("ProductExample", "addAllMapNodeNeighbors", "\tMap Node " + node.OID + " has "+node.getNumExamples() + " training examples.");				
			}
		} else {//only 1
			bestUnit = bmuList.get(0);
		}
		bmu = bestUnit;										//best unit to this product - if multiples then weighted by # of examples at this unit
		addBMUToNeighbrhdMap();
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes_ChiSq( _type);		
	}//findBMUFromNodes_ChiSq	
	
	public static String[] getMinMaxDists() {
		String[] res = new String[wtMins.size()+1];
		int i=0;
		res[i++] = "idx |\tMin|\tMax|\tDist";
		for(Integer idx : wtMins.keySet()) {			
			res[i++]=""+ idx + "\t" + String.format("%6.4f",wtMins.get(idx)) + "\t" + String.format("%6.4f",wtMaxs.get(idx)) + "\t" + String.format("%6.4f",wtDists.get(idx));}
		return res;
	}

	@Override
	public void finalizeBuild() {
		allJPs = trainPrdctData.getAllJpsInData();
	}//

	@Override
	protected HashSet<Tuple<Integer, Integer>> getSetOfAllJpgJpData() {
		HashSet<Tuple<Integer,Integer>> res = trainPrdctData.getAllJpgJpsInData();
		return res;
	}//getSetOfAllJpgJpData

	@Override
	public void buildFeatureVector() {
		buildAllJPFtrIDXsJPs();
		buildFeaturesMap();
		ftrsBuilt = true;		
		buildNormFtrData();
		//features and std ftrs should be the same, since we only assign a 1 to values that are present
		buildStdFtrsMap();
		//by here all maps of per-type, per-feature val arrays of jps should be built
		buildMapOfJPsToRank();
	}//buildFeatureVector
	
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
//		Float getVal = wtMins.get(idx);
//		if(getVal == null) {getVal = 0.0f;}
//		wtMins.put(idx, (val<getVal) ? val : getVal);
		
		Float getVal = wtMaxs.get(idx);
		if(getVal == null) {getVal = -100000000.0f;}
		wtMaxs.put(idx,(val>getVal) ? val : getVal);	
		wtDists.put(idx, wtMaxs.get(idx)- wtMins.get(idx));		
	}//setFtrMinMax
	
	@Override
	public void drawMeLblMap(SOM_StraffordMain p){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label	
		p.showNoBox(mapLoc, getRad(), 5, label.clrVal,label.clrVal, SOM_StraffordMain.gui_FaintGray, label.label);
		p.popStyle();p.popMatrix();		
	}//drawLabel
	
	//take loaded data and convert to output data
	@Override
	protected void buildFeaturesMap() {
		ftrMap = new TreeMap<Integer, Float>();	
		//order map gives order value of each jp - provide multiplier for higher vs lower priority jps
		TreeMap<Integer, Integer> orderMap = trainPrdctData.getJPOrderMap();
		int numJPs = orderMap.size();
		//verify # of jps as expected
		if (numJPs != allJPFtrIDXs.size()) {	mapData.dispMessage("ProductExample", "buildFeaturesMap", "Problem with size of expected jps from trainPrdctData vs. allJPFtrIDXs : trainPrdctData says # jps == " +numJPs + " | allJPFtrIDXs.size() == " +allJPFtrIDXs.size());}
		if(numJPs == 1) {
			Integer ftrIDX = allJPFtrIDXs.get(0);
			ftrMap.put(ftrIDX, 1.0f);
			setFtrMinMax(ftrIDX, 1.0f);
			this.ftrVecMag = 1.0f;			
		} else {//more than 1 jp for this product
			float val, ttlVal = 0.0f, denom = ordrWtAraPerSize[numJPs];
			for (Integer IDX : allJPFtrIDXs) {
				Integer jp = jpJpgMon.getJpByIdx(IDX);
				val = (numJPs - orderMap.get(jp))/denom;
				ftrMap.put(IDX,val);
				setFtrMinMax(IDX, val);
				ttlVal += val;
			}	
			this.ftrVecMag = (float) Math.sqrt(ttlVal);
		}
	}//buildFeaturesMap
	
	@Override
	protected void buildStdFtrsMap() {
		stdFtrMap = new TreeMap<Integer, Float>();
		for (Integer IDX : ftrMap.keySet()) {stdFtrMap.put(IDX,ftrMap.get(IDX));}//since features are all weighted to sum to 1, can expect ftrmap == strdizedmap
		stdFtrsBuilt = true;
	}//buildStdFtrsMap
	
	@Override
	public String toString(){
		String res = "Example OID# : "+OID + (null == label ?  " Unknown DataClass\t" : " DataClass : " + label.toString() +"\t");
		if(null!=mapLoc){res+="Location on SOM map : " + mapLoc.toStrBrf();}
		if (mapData.numFtrs > 0) {
			res += "\n\tFeature Val(s) : " + dispFtrMapVals(ftrMap);
		} else {
			res += "No Features for this product example";
		}
		return res;
	}

}//class productExample

//this class is for a simple object to just represent a mouse-over on the visualization of the map
class DispSOMMapExample extends StraffSOMExample{
	private float ftrThresh;
	private int mapType;
	private int[] clrVal = new int[] {255,255,0,255};
	private String mseLabelDat;
	private TreeMap<Float, String> strongestFtrs;
	//need to support all ftr types from map - what type of ftrs are these?
	public DispSOMMapExample(SOMMapManager _map, myPointf ptrLoc, TreeMap<Integer, Float> _ftrs, float _thresh) {
		super(_map, "TempEx_"+ptrLoc.toStrBrf());//(" + String.format("%.4f",this.x) + ", " + String.format("%.4f",this.y) + ", " + String.format("%.4f",this.z)+")
		//type of features used for currently trained map
		mapType = mapData.getCurrMapDataFrmt();
		
		ftrMap = new TreeMap<Integer, Float>();	
		ftrThresh = _thresh;
		allJPs = new HashSet<Integer>();
		allJPFtrIDXs = new ArrayList<Integer>();
		//decreasing order
		strongestFtrs = new TreeMap<Float, String>(Collections.reverseOrder());
		for(Integer ftrIDX : _ftrs.keySet()) {
			Float ftr = _ftrs.get(ftrIDX);
			if(ftr >= ftrThresh) {	
				Integer jp = jpJpgMon.getJpByIdx(ftrIDX);
				allJPs.add(jp);
				allJPFtrIDXs.add(ftrIDX);	
				ftrMap.put(ftrIDX, ftr);
				strongestFtrs.put(ftr, ""+jp);
			}
		}		
 		//descriptive mouse-over label - top x jp's
		mseLabelDat = "JPs : ";
		if (allJPs.size()== 0) {	mseLabelDat += "None";	}
		else {
			for (Float ftr : strongestFtrs.keySet()) {
				String jpName = strongestFtrs.get(ftr);
				mseLabelDat +=""+jpName+":" + String.format("%03f", ftr)+ " | ";
			}
		}
	}//ctor
	
	//not used by this object
	@Override
	protected HashSet<Tuple<Integer, Integer>> getSetOfAllJpgJpData() {
		return null;
	}//getSetOfAllJpgJpData

	@Override
	public void buildLabelIndiv() {}//should be sufficient with base class version, for now
	@Override
	protected void buildFeaturesMap() { }	
	@Override
	public String getRawDescrForCSV() {	return "Should not save DispSOMMapExample to CSV";}
	@Override
	public String getRawDescColNamesForCSV() {return "Do not save DispSOMMapExample to CSV";}

	@Override
	public void finalizeBuild() {}
	@Override
	public void drawMeLblMap(SOM_StraffordMain p){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label	
		p.showBox(mapLoc, rad, 5, clrVal,clrVal, SOM_StraffordMain.gui_LightGreen, mseLabelDat);
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
	public Tuple<Integer,Integer> mapNodeCoord;	
	public Tuple<Integer,Integer>[][] neighborMapCoords;				//array of arrays of row x col of neighbors to this node.  This node is 1,1.  
	public float[][] neighborUMatWts;
	private TreeMap<Double,ArrayList<StraffSOMExample>> examplesBMU;	//best training examples in this unit, keyed by distance ; may be equidistant, so put in array at each distance
	private float logExSize;						//log of size of examples + 1, used to display nodes with examples with visual cue to population
	private int numMappedTEx;						//# of mapped training examples to this node
	//set from u matrix built by somoclu - the similarity of this node to its neighbors
	private float uMatDist;
	private float[] uMatBoxDims;		//box upper left corner x,y and box width,height
	private int[] uMatClr;
	//feature type denotes what kind of features the tkns being sent represent - 0 is unmodded, 1 is standardized across all data for each feature, 2 is normalized across all features for single data point
	public SOMMapNodeExample(SOMMapManager _map, Tuple<Integer,Integer> _mapNode, float[] _ftrs) {
		super(_map, "MapNode_"+_mapNode.x+"_"+_mapNode.y);
		//ftrTypeMapBuilt = _ftrType;
		if(_ftrs.length != 0){	setFtrsFromFloatAra(_ftrs);	}
		//allJPs = buildJPsFromFtrAra(_ftrs, ftrThresh);
		initMapNode( _mapNode);
	}
	
	//feature type denotes what kind of features the tkns being sent represent
	public SOMMapNodeExample(SOMMapManager _map,Tuple<Integer,Integer> _mapNode, String[] _strftrs) {
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
				int jp = jpJpgMon.getJpByIdx(i);
				allJPs.add(jp);
				setMapOfJpWts(jp, val, ftrMapTypeKey);				
			}
		}
		buildAllJPFtrIDXsJPs();
		ftrVecMag = (float) Math.sqrt(ftrVecSqMag);		
		ftrsBuilt = true;		
		buildNormFtrData();		
	}//setFtrsFromFloatAra	
	
	public void setUMatDist(float _d) {uMatDist = _d; int clr=(int) (255*uMatDist); uMatClr = new int[] {clr,clr,clr};}
	public float getUMatDist() {return uMatDist;}
	@Override
	protected HashSet<Tuple<Integer, Integer>> getSetOfAllJpgJpData() {
		// TODO Auto-generated method stub
		return null;
	}//getSetOfAllJpgJpData
	private void setFtrsFromStrAra(String [] tkns){
		ftrMap = new TreeMap<Integer, Float>();
		float ftrVecSqMag = 0.0f;
		for(int i = 0; i < tkns.length; i++) {	
			Float val = Float.parseFloat(tkns[i]);
			if (val > ftrThresh) {
				ftrVecSqMag+=val*val;
				ftrMap.put(i, val);
				int jp = jpJpgMon.getJpByIdx(i);
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
		uMatClr = new int[3];
		mapNodeCoord = _mapNode;		
		mapLoc = mapData.buildScaledLoc(mapNodeCoord);
		uMatBoxDims = mapData.buildUMatBoxCrnr(mapNodeCoord);
		int xLoc,yLoc;
		neighborMapCoords = new Tuple[4][];
		for(int row=-1;row<3;++row) {
			neighborMapCoords[row+1] = new Tuple[4];
			yLoc = row + mapNodeCoord.y;
			for(int col=-1;col<3;++col) {
				xLoc = col + mapNodeCoord.x;
				neighborMapCoords[row+1][col+1] = mapData.getMapLocTuple(xLoc, yLoc);
			}
		}
		//these are the same for map nodes
		mapNodeLoc.set(mapLoc);
		numMappedTEx = 0;
		examplesBMU = new TreeMap<Double,ArrayList<StraffSOMExample>>();		//defaults to small->large ordering	
		//allJPs should be made by here
		buildFeatureVector();
	}//initMapNode
	
	//build 4x4 float array of neighbor wt vals
	public void buildNeighborWtVals() {
		neighborUMatWts = new float[4][];		
		for(int row=0;row<4;++row) {
			neighborUMatWts[row]=new float[4];
			for(int col=0;col<4;++col) {
				neighborUMatWts[row][col] = mapData.MapNodes.get(neighborMapCoords[row][col]).getUMatDist();
			}
		}
	}//buildNeighborWtVals
	
	//cubic formula in 1 dim
	private float findCubicVal(float[] p, float t) { 	return p[1]+0.5f*t*(p[2]-p[0] + t*(2.0f*p[0]-5.0f*p[1]+4.0f*p[2]-p[3] + t*(3.0f*(p[1]-p[2])+p[3]-p[0]))); }
	//return bicubic interpolation of each neighbor's UMatWt
	public float biCubicInterp(float tx, float ty) {
		float [] aAra = new float[4];
		for (int row=0;row<4;++row) {aAra[row]=findCubicVal(neighborUMatWts[row], tx);}
		float val = findCubicVal(aAra, ty);
		return ((val <= 0.0f) ? 0.0f : (val > 1.0f) ? 1.0f : val);
	}//biCubicInterp
	
	@Override
	//feature is already made in constructor
	protected void buildFeaturesMap() {	}
	@Override
	public String getRawDescrForCSV() {	return "Should not save SOMMapNodeExample to intermediate CSV";}
	@Override
	public String getRawDescColNamesForCSV() {return "Do not save SOMMapNodeExample to intermediate CSV";}
	@Override
	public void finalizeBuild() {	}
	
	//som map node-specific label building
	@Override
	public void buildLabelIndiv() {	}
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
				destIDX = jpJpgMon.getJpToFtrIDX(jp);
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
	
	//this adds every training example to a particular map node based on distance
	public void addBMUExample(double dist, StraffSOMExample straffSOMExample){
		ArrayList<StraffSOMExample> tmpList = examplesBMU.get(dist);
		if(tmpList == null) {tmpList = new ArrayList<StraffSOMExample>();}
		tmpList.add(straffSOMExample);		
		examplesBMU.put(dist, tmpList);		
		numMappedTEx = examplesBMU.size();
		setRad( 2*numMappedTEx);// PApplet.sqrt(numMappedTEx) + 1;
		//TODO : this label is minimally descriptive. 
		//Ideal situation would be to build map node lable by having vote of all nodes that are closest to this node
		label = examplesBMU.firstEntry().getValue().get(0).getLabel();
		logExSize = (float) Math.log(examplesBMU.size() + 1);
	}//addBMUExample
	
	
	//# of training examples that mapped to this node
	public int getNumExamples() {return examplesBMU.size();}
	
	public boolean hasMappings(){return numMappedTEx != 0;}
	@Override
	public dataClass getLabel(){
		if(numMappedTEx == 0){
			mapData.dispMessage("SOMMapNodeExample","getLabel","Mapnode :"+mapNodeCoord.toString()+" has no mapped BMU examples.");
			return null;
		}
		return label;}
	public int getExmplBMUSize() {return  examplesBMU.size();}
	public float getLogExmplBMUSize() {return logExSize * 3.0f;}
	
	public void drawMeSmall(SOM_StraffordMain p, int jpIDX){
		p.pushMatrix();p.pushStyle();
		Float wt = this.stdFtrMap.get(jpIDX);
		if (wt==null) {wt=0.0f;}
		//show(myPointf P, float rad, int det, int[] fclr, int[] sclr, int tclr, String txt, boolean useBKGBox) 
		p.show(mapLoc, 2, 2, p.gui_Cyan, p.gui_Cyan, p.gui_Green, new String[] {this.OID+":",String.format("%.4f", wt)}); 
		p.popStyle();p.popMatrix();		
	}
	
	public void drawMeSmall(SOM_StraffordMain p){
		p.pushMatrix();p.pushStyle();
		//show(myPointf P, float rad, int det, int[] fclr, int[] sclr, int tclr, String txt, boolean useBKGBox) 
		p.show(mapLoc, 2, 2, p.gui_Cyan, p.gui_Cyan, p.gui_Green, new String[] {this.OID}); 
		p.popStyle();p.popMatrix();		
	}	
	
	
	//draw a box around this node of uMatD color
	public void drawMeUMatDist(SOM_StraffordMain p){
		p.pushMatrix();p.pushStyle();
		p.setFill(uMatClr, 255);
		p.rect(uMatBoxDims);		
		p.popStyle();p.popMatrix();	
	}//drawMeUMatDist

	@Override
	public void drawMeLblMap(SOM_StraffordMain p){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label	
		p.showNoBox(mapLoc, getRad(), 5, label.clrVal,label.clrVal, SOM_StraffordMain.gui_FaintGray, label.label);
		p.popStyle();p.popMatrix();		
	}//drawLabel

	
	public String toString(){
		String res = "Node Loc : " + mapNodeCoord.toString()+"\t" + super.toString();
		return res;		
	}
}//SOMMapNodeExample

//this class holds functionality migrated from the DataPoint class for rendering on the map.  since this won't be always necessary, we're moving this code to different class so it can be easily ignored
abstract class baseDataPtVis{
	//location in mapspace most closely matching this node - actual map location (most likely between 4 map nodes)
	protected myPointf mapLoc;		
	//bmu map node location - this is same as mapLoc/ignored for map nodes
	protected myPointf mapNodeLoc;
	//draw-based vars
	protected float rad;
	protected static int drawDet;	
	//for debugging purposes, gives min and max radii of spheres that will be displayed on map for each node proportional to # of samples - only display related
	public static float minRad = 100000, maxRad = -100000;
	
	public baseDataPtVis() {
		mapLoc = new myPointf();	
		mapNodeLoc = new myPointf();
		rad = 1.0f;
		drawDet = 2;
	}
	protected void setRad(float _rad){
		rad = ((float)(Math.log(2.0f*(_rad+1))));
		minRad = minRad > rad ? rad : minRad;
		maxRad = maxRad < rad ? rad : maxRad;
		//drawDet = ((int)(Math.log(2.0f*(rad+1)))+1);
	}
	public float getRad(){return rad;}
	
	//set map location for this example
	public void setMapLoc(myPointf _pt){mapLoc = new myPointf(_pt);}
	
	//draw this example with a line linking it to its best matching unit
	public final void drawMeLinkedToBMU(SOM_StraffordMain p, float _rad, String ID){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at mapLoc - actual location on map
		p.show(mapLoc, _rad, drawDet, p.gui_Yellow,p.gui_Yellow, p.gui_White, new String[] {ID});
		//draw line to bmu location
		p.setColorValStroke(p.gui_Yellow,255);
		p.strokeWeight(1.0f);
		p.line(mapLoc, mapNodeLoc);
		p.popStyle();p.popMatrix();		
	}//drawMeLinkedToBMU
	
	//override drawing in map nodes
	public final void drawMeMap(SOM_StraffordMain p, int clr){
		p.pushMatrix();p.pushStyle();	
		p.show(mapLoc, getRad(), drawDet, clr+1, clr+1);		
		p.popStyle();p.popMatrix();		
	}//drawMeMap
	
	//override drawing in map nodes
	public final void drawMeMapClr(SOM_StraffordMain p, int[] clr){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at mapLoc
		p.show(mapLoc, rad,drawDet, clr, clr);
		p.popStyle();p.popMatrix();		
	}//drawMeMapClr
	
	public final void drawMeWithWt(SOM_StraffordMain p, float wt, int clr, int txtClr, String ID){
		p.pushMatrix();p.pushStyle();	
		p.show(mapLoc, wt, 2,  clr, clr, txtClr,  new String[] {ID+" : ",String.format("%.4f", wt)}); 
		p.popStyle();p.popMatrix();		
	}//drawMeWithWt
	
	public abstract void drawMeLblMap(SOM_StraffordMain p);
	
	public void drawMeRanked(SOM_StraffordMain p, String lbl, int[] clr, float rad, int rank){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label and no background box	
		p.showNoBox(mapLoc, rad, drawDet, clr, clr, SOM_StraffordMain.gui_White, lbl);
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
			Integer optVal = Integer.parseInt(csvVals[1]), JPG = Integer.parseInt(csvVals[3]);
			JpgJpDataRecord rec = new JpgJpDataRecord(JPG,i, optVal, csvString);
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
	protected void addEventDataRecsFromRawData(Integer optVal, BaseRawData ev) {
		boolean isOptEvent = ((-2 <= optVal) && (optVal <= 2));
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
	
	protected String buildJPGJP_CSVString() {
		String res = "";	
		for (int i=0;i<listOfJpgsJps.size();++i) {
			JpgJpDataRecord rec = listOfJpgsJps.get(i);
			res += "JPGJP_Start,optKey,"+rec.getOptVal()+","+rec.getCSVString()+"JPGJP_End,";			
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
	public void addEventDataFromEventObj(BaseRawData ev) {	super.addEventDataRecsFromRawData(FauxOptVal,ev);}//addEventDataFromEventObj
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
	public void addEventDataFromEventObj(BaseRawData ev) {	super.addEventDataRecsFromRawData(FauxOptVal,ev);}//addEventDataFromEventObj
	@Override
	public void addJPG_JPDataFromCSVString(String _csvDataStr) {super.addJPG_JPDataRecsFromCSVStr(FauxOptVal,_csvDataStr);	}//addJPG_JPDataFromCSVString
}//class OrderEventTrainData

class LinkEventTrainData extends StraffEvntTrainData{

	public LinkEventTrainData(LinkEvent ev) {
		super(ev);
		addEventDataFromEventObj(ev);}	//put in child ctor in case child-event specific data needed for training	
	
	public LinkEventTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){
		super(_evIDStr, _evTypeStr, _evDateStr);
		addJPG_JPDataFromCSVString(_evntStr);}	//put in child ctor in case child-event specific data needed for training		
	
	@Override
	public void addEventDataFromEventObj(BaseRawData ev) {super.addEventDataRecsFromRawData(FauxOptVal,ev);}//addEventDataFromEventObj
	@Override
	public void addJPG_JPDataFromCSVString(String _csvDataStr) {	super.addJPG_JPDataRecsFromCSVStr(FauxOptVal,_csvDataStr);}//addJPG_JPDataFromCSVString
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
		super.addEventDataRecsFromRawData(optValKey, ev);
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
}//class OptEventTrainData

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
	//optVal if this is an opt record, otherwise some value outside the opt range (-2->2 currently)
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
