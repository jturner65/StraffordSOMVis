package SOM_Strafford_PKG;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;


/**
 * NOTE : None of the ADT's  in this file is thread safe so do not allow for opportunities for concurrent modification 
 * of any instanced object in this file. This decision was made for speed concerns - 
 * concurrency-protected objects have high overhead that proved greater than any gains in 10 execution threads.
 * 
 * Objects of this class will hold the relevant data acquired from the db to build a datapoint used by the SOM
 * It will take raw data and build the appropriate feature vector, using the appropriate calculation 
 * to weight the various jpgroups/jps appropriately.  The ID of this construct should be such that it 
 * can be uniquely qualified/indexed by it and will either be the ID of a particular prospect in the 
 * prospect database or some other unique identifier if this is representing, say, a target product
 * 
 * @author john
 */
public abstract class StraffSOMExample extends baseDataPtVis{	
	protected static MonitorJpJpgrp jpJpgMon;
	//corresponds to OID in prospect database - primary key of all this data is OID in prospects
	public final String OID;		
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
	
	//these objects are for reporting on individual examples.  They are built when features, keyed by ftr type, and are 
	//use a map per feature type : unmodified, normalized, standardized,to hold the features sorted by weight as key, value is array of jps at that weight -submap needs to be instanced in descending key order
	private TreeMap<Float, ArrayList<Integer>>[] mapOfTopWtJps;	
	//a map per feature type : unmodified, normalized, standardized,  of jps and their relative "rank" in this particular example, as determined by the weight calc
	private TreeMap<Integer,Integer>[] mapOfJpsVsWtRank;
	//keys for above maps
	public static final int ftrMapTypeKey = 0, stdFtrMapTypeKey = 1, normFtrMapTypeKey = 2;	
	//public static final String[] jpMapTypeKeys = new String[] {"ftrMap", "stdFtrMap", "normFtrMap"};
	public static final Integer[] jpMapTypeKeys = new Integer[] {ftrMapTypeKey, stdFtrMapTypeKey, normFtrMapTypeKey};
	
	/////////////////////////////
	// from old DataPoint data
	
	//reference to map node that best matches this node
	protected SOMMapNode bmu;			
	//this is the distance, using the chosen distance measure, to the best matching unit of the map for this example
	protected double _sqDistToBMU;
	//to hold 9 node neighborhood surrounding bmu - using array of nodes because nodes can be equidistant form multiple nodes
	//TODO set a list of these nodes for each SOMMapNodeExample upon their construction? will this speed up anything?
	private TreeMap<Double, ArrayList<SOMMapNode>> mapNodeNghbrs;
	
	public StraffSOMExample(SOMMapManager _map, ExDataType _type, String _id) {
		super(_map,_type);
		mapMgr=_map;
		jpJpgMon = mapMgr.jpJpgrpMon;
		OID = _id;
		_sqDistToBMU = 0.0;
		ftrsBuilt = false;
		stdFtrsBuilt = false;
		normFtrsBuilt = false;
		isBadTrainExample = false;	
		ftrMap = new TreeMap<Integer, Float>();			//feature map for SOM Map Nodes may not correspond directly to magnitudes seen in training data. 
		stdFtrMap = new TreeMap<Integer, Float>();	
		normFtrMap=new TreeMap<Integer, Float>();	
		allJPs = new HashSet<Integer> ();
		
		//this map is a map of maps in descending order - called by calc object as well as buildNormFtrData
		mapOfTopWtJps = new TreeMap[jpMapTypeKeys.length];//(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}}); 
		mapOfJpsVsWtRank = new TreeMap[jpMapTypeKeys.length];
		TreeMap<Float, ArrayList<Integer>> tmpFltMap;
		TreeMap<Integer,Integer> tmpIntMap;
		for(Integer ftrType : jpMapTypeKeys) {//type of features
			tmpFltMap = new TreeMap<Float, ArrayList<Integer>>(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}});//descending key order
			tmpIntMap = new TreeMap<Integer,Integer>();
			mapOfTopWtJps[ftrType] = tmpFltMap;
			mapOfJpsVsWtRank[ftrType] = tmpIntMap;
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
	protected boolean checkForErrors(SOMMapNode _n, float[] dataVar){
		boolean inError = false;
		if(mapMgr == null){					mapMgr.dispMessage("SOMMapNodeExample","checkForErrors","FATAL ERROR : SOMMapData object is null!");		return true;}//if mapdata is null then stop - should have come up before here anyway
		if(_n==null){							mapMgr.dispMessage("SOMMapNodeExample","checkForErrors","_n is null!");		inError=true;} 
		else if(_n.mapLoc == null){				mapMgr.dispMessage("SOMMapNodeExample","checkForErrors","_n has no maploc!");	inError=true;}
		if(dataVar == null){					mapMgr.dispMessage("SOMMapNodeExample","checkForErrors","map variance not calculated : datavar is null!");	inError=true;	}
		return inError;
	}//checkForErrors
	
	//take map of jpwts to arrays of jps and build maps of jps to rank
	//This will give a map of all present JPs for this example and the rank of that jp.  null entries mean no rank
	//mapOfTopWtJps must be built for all types of weights by here
	public void buildMapOfJPsToRank() {
		for (Integer mapToGet : jpMapTypeKeys) {//for each type
			Integer rank = 0;
			TreeMap<Float, ArrayList<Integer>> mapOfAras = mapOfTopWtJps[mapToGet];
			if (mapOfAras == null) {mapMgr.dispMessage("StraffSOMExample", "buildMapOfJPsToRank", "For OID : "+ OID + " mapOfTopWtJps entry " +mapToGet + " does not exist yet.  Method called before all features are calculated.");return;}
			TreeMap<Integer,Integer> mapOfRanks = mapOfJpsVsWtRank[mapToGet];
			for (Float wtVal : mapOfAras.keySet()) {
				ArrayList<Integer> jpsAtRank = mapOfAras.get(wtVal);
				for (Integer jp : jpsAtRank) {	mapOfRanks.put(jp, rank);}
				++rank;
			}
			mapOfJpsVsWtRank[mapToGet] = mapOfRanks;//probably not necessary since already initialized (will never be empty or deleted)		
		}		
	}//buildMapOfJPsToRank
	
	//return the rank of the passed jp
	protected Integer getJPRankForMap(int mapToGetIDX, int jp) {
		Integer mapToGet = jpMapTypeKeys[mapToGetIDX];
		TreeMap<Integer,Integer> mapOfRanks = mapOfJpsVsWtRank[mapToGet];
		Integer rank = mapOfRanks.get(jp);
		if (rank==null) {rank = mapMgr.numFtrs;}
		return rank;
	}//getJPRankForMap
	
	//add passed map node, with passed feature distance, to neighborhood nodes
	protected void addMapUnitToNeighbrhdMap(SOMMapNode _n, double _dist) {
		ArrayList<SOMMapNode> tmpMap = mapNodeNghbrs.get(_dist);
		if (null==tmpMap) {tmpMap = new ArrayList<SOMMapNode>();}
		tmpMap.add(_n);
		mapNodeNghbrs.put(_dist, tmpMap);		
	}//addMapUnitToNeighbrhdMap
	
	//once BMU and distToBMU is set, init map and add node to neighborhood map keyed by dist
	protected void addBMUToNeighbrhdMap() {
		mapNodeNghbrs = new TreeMap<Double, ArrayList<SOMMapNode>>();
		ArrayList<SOMMapNode> tmpMap = new ArrayList<SOMMapNode>();
		tmpMap.add(bmu);
		mapNodeLoc.set(bmu.mapLoc);
		mapNodeNghbrs.put(_sqDistToBMU, tmpMap);		//to hold 9 neighbor nodes and their ftr distance		
	}//addBMUToNeighbrhdMap
	
	//this adds the passed node as this example's best matching unit on the map
	//this also adds this data point to the map's node with a key of the distance
	//dataVar is variance of feature weights of map nodes.  this is for chi-squared distance measurements
	public void setBMU(SOMMapNode _n, int _ftrType){
		setBMU(_n,getSqDistFromFtrType(_n,  _ftrType), _ftrType );
	}//setBMU
	public void setBMU(SOMMapNode _n, double _dist, int _ftrType){
		if (checkForErrors(_n, mapMgr.map_ftrsVar)) {return;}//if true then this is catastrophic error and should interrupt flow here
		bmu = _n;	
		_sqDistToBMU = _dist;//getSqDistFromFtrType_ChiSq(bmu,  _ftrType);
		this.mapLoc.set(bmu.mapLoc);
		
		addBMUToNeighbrhdMap();
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes( _ftrType);
		//dist here is distance of this training example to map node 
		//_n.addBMUExample(this);	//don't call this here so we can set the bmu of testing data without modifying bmu's themselves
	}//setBMU
	
	//this adds the passed node as this example's best matching unit on the map
	//this also adds this data point to the map's node with a key of the distance
	//dataVar is variance of feature weights of map nodes.  this is for chi-squared distance measurements
	public void setBMU_ChiSq(SOMMapNode _n, int _ftrType){
		setBMU_ChiSq(_n,getSqDistFromFtrType_ChiSq(_n,  _ftrType),_ftrType);
	}
	public void setBMU_ChiSq(SOMMapNode _n, double _dist, int _ftrType){
		if (checkForErrors(_n, mapMgr.map_ftrsVar)) {return;}//if true then this is catastrophic error and should interrupt flow here
		bmu = _n;	
		_sqDistToBMU = _dist;//getSqDistFromFtrType_ChiSq(bmu,  _ftrType);
		this.mapLoc.set(bmu.mapLoc);
		
		addBMUToNeighbrhdMap();
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes_ChiSq(_ftrType);		
		//dist here is distance of this training example to map node
		//_n.addBMUExample(this);
	}//setBMU
		
	//use 9 map node neighborhood around this node, accounting for torroidal map, to find exact location on map, using chi_sq dist calc
	//for this node (put in mapLoc) by using weighted average of mapNodeLocs of neighbor nodes,
	//where the weight is the inverse feature distance 
	// mapNodeNghbrs (9 node neighborood) must be set before this is called, and has bmu set as closest, with key being distance
	//distsToNodes is distance of this node to all map nodes in neighborhood
	protected void buildNghbrhdMapNodes_ChiSq(int _ftrType){
		int mapColsSize = mapMgr.getMapNodeCols(), mapRowSize = mapMgr.getMapNodeRows();
		int mapCtrX = bmu.mapNodeCoord.x, mapCtrY = bmu.mapNodeCoord.y;
		Integer xTup, yTup;
		//go through all mapData.MapNodes 
		for (int x=-1; x<2;++x) {//should be 3 cols
			xTup = (mapCtrX + x + mapColsSize) % mapColsSize;
			for (int y=-1; y<2;++y) {//3 rows
				if((y==0) && (x==0)){continue;}//ignore "center" node - this is bmu
				yTup = (mapCtrY + y + mapRowSize) % mapRowSize;		
				Tuple<Integer,Integer> key = new Tuple<Integer, Integer>(xTup, yTup);
				SOMMapNode node = mapMgr.MapNodes.get(key);
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
		int mapColsSize = mapMgr.getMapNodeCols(), mapRowSize = mapMgr.getMapNodeRows();
		int mapCtrX = bmu.mapNodeCoord.x, mapCtrY = bmu.mapNodeCoord.y;
		Integer xTup, yTup;
		//go through all mapData.MapNodes 
		for (int x=-1; x<2;++x) {//should be 3 cols
			xTup = (mapCtrX + x + mapColsSize) % mapColsSize;
			for (int y=-1; y<2;++y) {//3 rows
				if((y==0) && (x==0)){continue;}//ignore "center" node - this is bmu
				yTup = (mapCtrY + y + mapRowSize) % mapRowSize;		
				Tuple<Integer,Integer> key = new Tuple<Integer, Integer>(xTup, yTup);
				SOMMapNode node = mapMgr.MapNodes.get(key);
				double dist = getSqDistFromFtrType(node, _ftrType);
				addMapUnitToNeighbrhdMap(node, dist);
			}//for each row/y
		}//for each column/x	
		setExactMapLoc();
	}//addAllMapNodeNeighbors
	
	//use 9 map node neighborhood around this node, accounting for torroidal map, to find exact location on map, using chi_sq dist calc
	//for this node (put in mapLoc) by using weighted average of mapNodeLocs of neighbor nodes,
	//where the weight is the inverse feature distance 
	// mapNodeNghbrs (9 node neighborood) must be set before this is called, and has bmu set as closest, with key being distance
	//distsToNodes is distance of this node to all map nodes in neighborhood
	//this method only measures non-zero features in this node
	protected void buildNghbrhdMapNodes_ChiSq_Exc(int _ftrType){
		int mapColsSize = mapMgr.getMapNodeCols(), mapRowSize = mapMgr.getMapNodeRows();
		int mapCtrX = bmu.mapNodeCoord.x, mapCtrY = bmu.mapNodeCoord.y;
		Integer xTup, yTup;
		//go through all mapData.MapNodes 
		for (int x=-1; x<2;++x) {//should be 3 cols
			xTup = (mapCtrX + x + mapColsSize) % mapColsSize;
			for (int y=-1; y<2;++y) {//3 rows
				if((y==0) && (x==0)){continue;}//ignore "center" node - this is bmu
				yTup = (mapCtrY + y + mapRowSize) % mapRowSize;		
				Tuple<Integer,Integer> key = new Tuple<Integer, Integer>(xTup, yTup);
				SOMMapNode node = mapMgr.MapNodes.get(key);
				double dist = getSqDistFromFtrType_ChiSq_Exclude(node, _ftrType);
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
	//this method only measures non-zero features in this node
	protected void buildNghbrhdMapNodes_Exc(int _ftrType){
		int mapColsSize = mapMgr.getMapNodeCols(), mapRowSize = mapMgr.getMapNodeRows();
		int mapCtrX = bmu.mapNodeCoord.x, mapCtrY = bmu.mapNodeCoord.y;
		Integer xTup, yTup;
		//go through all mapData.MapNodes 
		for (int x=-1; x<2;++x) {//should be 3 cols
			xTup = (mapCtrX + x + mapColsSize) % mapColsSize;
			for (int y=-1; y<2;++y) {//3 rows
				if((y==0) && (x==0)){continue;}//ignore "center" node - this is bmu
				yTup = (mapCtrY + y + mapRowSize) % mapRowSize;		
				Tuple<Integer,Integer> key = new Tuple<Integer, Integer>(xTup, yTup);
				SOMMapNode node = mapMgr.MapNodes.get(key);
				double dist = getSqDistFromFtrType_Exclude(node, _ftrType);
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
	private myPointf findNodeLocWrap(SOMMapNode ex, Integer bmuX, Integer bmuY, float mapW, float mapH) {
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
			ArrayList<SOMMapNode> tmpMap = mapNodeNghbrs.get(_dist);
			for (SOMMapNode ex : tmpMap) {//public static myPointf _mult(myPointf p, float n){ myPointf result = new myPointf(p.x * n, p.y * n, p.z * n); return result;}                          //1 pt, 1 float				
				ttlInvDist +=invDistP1;
			}			
		}
		float mapW = mapMgr.getMapWidth(), mapH = mapMgr.getMapHeight();
		Integer bmuX = bmu.mapNodeCoord.x,  bmuY = bmu.mapNodeCoord.y;
		//scale by ttlInvDist so that all distance wts sum to 1
		for (double _dist : mapNodeNghbrs.keySet()) {
			invDistP1 = ((float) (1.0f/(1.0f+_dist))/ttlInvDist);					//handles 0 dist - max will be 0, min will be some fraction
			ArrayList<SOMMapNode> tmpMap = mapNodeNghbrs.get(_dist);
			for (SOMMapNode ex : tmpMap) {//public static myPointf _mult(myPointf p, float n){ myPointf result = new myPointf(p.x * n, p.y * n, p.z * n); return result;}                          //1 pt, 1 float			
				//if ex is more than 1 in x or y from bmu, then wrap arround, need to add (or subtract) x or y dim of map
				locToAdd = findNodeLocWrap(ex, bmuX, bmuY, mapW, mapH);
				totalLoc._add(myPointf._mult(locToAdd, invDistP1));				
			}			
		}
		totalLoc.x += mapW;totalLoc.x %= mapW;
		totalLoc.y += mapH;totalLoc.y %= mapH;
		
		this.mapLoc.set(totalLoc);
	}//setExactMapLoc
	
	//call from calc or from objects that manage/modify norm/std ftrs - build structure registering weight of jps in ftr vector mapToGet in descending strength
	protected void setMapOfJpWts(int jp, float wt, int mapToGetIDX) {
		Integer mapToGet = jpMapTypeKeys[mapToGetIDX];
		TreeMap<Float, ArrayList<Integer>> map = mapOfTopWtJps[mapToGet];
		//shouldn't be null - means using inappropriate key
		if(map == null) {mapMgr.dispMessage("StraffSOMExample","setMapOfJpWts","Using inappropriate key to access mapOfTopJps : " + mapToGet + " No submap exists with this key."); return;}		 
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
			if(jpIDX==null) {mapMgr.dispMessage("StraffSOMExample","buildAllJPFtrIDXsJPs","ERROR!  null value in  jpJpgMon.getJpToFtrIDX("+jp+")" );}
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
		if(!ftrsBuilt) {mapMgr.dispMessage("StraffSOMExample","buildNormFtrData","OID : " + OID + " : Features not built, cannot normalize feature data");return;}
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
		return calcStdFtrVector(ftrs, jpIdxs, mapMgr.minsVals, mapMgr.diffsVals);
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

	////////////////////////////////
	//distance measures between nodes
	/**
	 * These functions will find the specified distance between the passed node and this node, using the specified feature type
	 * @param fromNode
	 * @param _ftrType
	 * @return
	 */
	//return the chi-sq distance from this node to passed node
	public double getSqDistFromFtrType_ChiSq(StraffSOMExample fromNode, int _ftrType){
		switch(_ftrType){
			case SOMMapManager.useUnmoddedDat : {return _getChiSqFtrDist(fromNode.ftrMap, ftrMap); }
			case SOMMapManager.useNormedDat  : {return _getChiSqFtrDist(fromNode.normFtrMap, normFtrMap);}
			case SOMMapManager.useScaledDat  : {return _getChiSqFtrDist(fromNode.stdFtrMap, stdFtrMap); }
			default : {return _getChiSqFtrDist(fromNode.ftrMap, ftrMap); }
		}	
	}//getDistFromFtrType
	//return the sq sdistance between this map's ftrs and the passed ftrMap
	public double getSqDistFromFtrType(StraffSOMExample fromNode, int _ftrType){
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
		float[] mapFtrVar = mapMgr.map_ftrsVar;
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
	}//_getChiSqFtrDist		

	//return the chi-sq distance from this node to passed node, only measuring non-zero features in this node
	public double getSqDistFromFtrType_ChiSq_Exclude(StraffSOMExample fromNode, int _ftrType){
		switch(_ftrType){
			case SOMMapManager.useUnmoddedDat : {return _getChiSqFtrDist_Exc(fromNode.ftrMap, ftrMap); }
			case SOMMapManager.useNormedDat  : {return _getChiSqFtrDist_Exc(fromNode.normFtrMap, normFtrMap);}
			case SOMMapManager.useScaledDat  : {return _getChiSqFtrDist_Exc(fromNode.stdFtrMap, stdFtrMap); }
			default : {return _getChiSqFtrDist(fromNode.ftrMap, ftrMap); }
		}	
	}//getDistFromFtrType

	//return the distance between this map's ftrs and the passed ftrMap, only measuring -this- nodes non-zero features.
	public double getSqDistFromFtrType_Exclude(StraffSOMExample fromNode, int _ftrType){
		switch(_ftrType){
			case SOMMapManager.useUnmoddedDat : {return _getSqFtrDist_Exc(fromNode.ftrMap, ftrMap); }
			case SOMMapManager.useNormedDat  : {return _getSqFtrDist_Exc(fromNode.normFtrMap, normFtrMap);}
			case SOMMapManager.useScaledDat  : {return _getSqFtrDist_Exc(fromNode.stdFtrMap, stdFtrMap); }
			default : {return _getSqFtrDist(fromNode.ftrMap, ftrMap); }
		}
	}//getDistFromFtrType	
	
	//get square distance between two feature map values, only measuring non-zero features in toftrMap
	private double _getSqFtrDist_Exc(TreeMap<Integer, Float> fromftrMap, TreeMap<Integer, Float> toftrMap) {
		double res = 0.0;
		Set<Integer> allIdxs = new HashSet<Integer>(toftrMap.keySet());
		for (Integer key : allIdxs) {//either map will have this key
			Float frmVal = fromftrMap.get(key);
			if(frmVal == null) {frmVal = 0.0f;}
			Float toVal = toftrMap.get(key), diff = toVal - frmVal;
			res += (diff * diff);
		}
		return res;
	}//_getSqFtrDist_Exc	
	
	//get square chi-distance between two feature map values - weighted by variance of data;  only measuring non-zero features in toftrMap
	private double _getChiSqFtrDist_Exc(TreeMap<Integer, Float> fromftrMap, TreeMap<Integer, Float> toftrMap) {
		double res = 0.0f;
		float[] mapFtrVar = mapMgr.map_ftrsVar;
		Set<Integer> allIdxs = new HashSet<Integer>(toftrMap.keySet());
		for (Integer key : allIdxs) {//either map will have this key
			Float frmVal = fromftrMap.get(key);
			if(frmVal == null) {frmVal = 0.0f;}
			Float toVal = toftrMap.get(key), diff = toVal - frmVal;
			res += (diff * diff)/mapFtrVar[key];
		}
		return res;
	}//_getChiSqFtrDist_Exc		

	//given a sqdistance-keyed map of lists of mapnodes, this will find the best matching unit (min distance), with favor given to units that have more examples
	private void _setBMUFromDistMapOfBMUs(TreeMap<Double, ArrayList<SOMMapNode>> mapNodes) {
		ArrayList<Tuple<Integer,Integer>> bmuKeys = new ArrayList<Tuple<Integer,Integer>>();
		Entry<Double, ArrayList<SOMMapNode>> topEntry = mapNodes.firstEntry();
		_sqDistToBMU = topEntry.getKey();
		ArrayList<SOMMapNode>  bmuList = topEntry.getValue();
		int numBMUs = bmuList.size();
		for (int i=0;i<numBMUs;++i) {	bmuKeys.add(i, bmuList.get(i).mapNodeCoord);	}
		SOMMapNode bestUnit = null;//keep null to break on errors // bmuList.get(0);//default to first entry
		if (numBMUs > 1) {//if more than 1 entry with same distance, find entry with most examples - if no entries have examples, then this will default to first entry
			int maxNumExamples = 0;
			for (int i=0;i<numBMUs;++i) {
				SOMMapNode node = bmuList.get(i);
				int numExamples = node.getNumExamples(ExDataType.ProspectTraining);//want # of training examples
				if (numExamples >= maxNumExamples) {//need to manage if all map nodes have no direct training examples (might happen on large maps), hence >= and not >
					maxNumExamples = numExamples;
					bestUnit = node;
				}		
			}
		} else {//only 1
			bestUnit = bmuList.get(0);
		}		
		bmu = bestUnit;	
		this.mapLoc.set(bmu.mapLoc);
		addBMUToNeighbrhdMap();
	}//
		
	//references current map of nodes, finds best matching unit and returns map of all map node tuple addresses and their ftr distances from this node
	//also build neighborhood nodes
	//two methods to minimize if calls for chisq dist vs regular euclidean dist
	public TreeMap<Double, ArrayList<SOMMapNode>> findBMUFromNodes(TreeMap<Tuple<Integer,Integer>, SOMMapNode> _MapNodes, int _ftrtype) {
		TreeMap<Double, ArrayList<SOMMapNode>> mapNodes = new TreeMap<Double, ArrayList<SOMMapNode>>();			
		for (SOMMapNode node : _MapNodes.values()) {
			double sqDistToNode = getSqDistFromFtrType(node, _ftrtype);
			ArrayList<SOMMapNode> tmpAra = mapNodes.get(sqDistToNode);
			if(tmpAra == null) {tmpAra = new ArrayList<SOMMapNode>();}
			tmpAra.add(node);
			mapNodes.put(sqDistToNode, tmpAra);		
		}				
		_setBMUFromDistMapOfBMUs(mapNodes);//,all_MapNodeLocToSqDist);		
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes( _ftrtype);	
		return mapNodes;
	}//findBMUFromNodes 	
	
	//references current map of nodes, finds best matching unit and returns map of all map node tuple addresses and their ftr distances from this node
	//using chi sq dist
	public TreeMap<Double, ArrayList<SOMMapNode>> findBMUFromNodes_ChiSq(TreeMap<Tuple<Integer,Integer>, SOMMapNode> _MapNodes, int _ftrtype) {
		TreeMap<Double, ArrayList<SOMMapNode>> mapNodes = new TreeMap<Double, ArrayList<SOMMapNode>>();
		for (SOMMapNode node : _MapNodes.values()) {
			double sqDistToNode = getSqDistFromFtrType_ChiSq(node, _ftrtype);
			ArrayList<SOMMapNode> tmpAra = mapNodes.get(sqDistToNode);
			if(tmpAra == null) {tmpAra = new ArrayList<SOMMapNode>();}
			tmpAra.add(node);
			mapNodes.put(sqDistToNode, tmpAra);		
		}				
		_setBMUFromDistMapOfBMUs(mapNodes);//, all_MapNodeLocToSqDist);
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes_ChiSq( _ftrtype);	
		return mapNodes;
	}//findBMUFromNodes_ChiSq		

	//references current map of nodes, finds distances from all map nodes, and finds and sets bmu
	//also build neighborhood nodes
	//this uses only non-zero features in this node when measuring distances.  two methods to minimize if calls for chisq dist vs regular euclidean dist
	public TreeMap<Double, ArrayList<SOMMapNode>> findBMUFromNodes_Excl(TreeMap<Tuple<Integer,Integer>, SOMMapNode> _MapNodes, int _ftrtype) {
		TreeMap<Double, ArrayList<SOMMapNode>> mapNodes = new TreeMap<Double, ArrayList<SOMMapNode>>();			
		for (SOMMapNode node : _MapNodes.values()) {
			double sqDistToNode = getSqDistFromFtrType_Exclude(node, _ftrtype);
			ArrayList<SOMMapNode> tmpAra = mapNodes.get(sqDistToNode);
			if(tmpAra == null) {tmpAra = new ArrayList<SOMMapNode>();}
			tmpAra.add(node);
			mapNodes.put(sqDistToNode, tmpAra);		
		}				
		_setBMUFromDistMapOfBMUs(mapNodes);//,all_MapNodeLocToSqDist);		
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes( _ftrtype);	
		return mapNodes;
	}//findBMUFromNodes 	
	
	//references current map of nodes, finds best matching unit and returns map of all map node tuple addresses and their ftr distances from this node
	//using chi sq dist
	public TreeMap<Double, ArrayList<SOMMapNode>> findBMUFromNodes_ChiSq_Excl(TreeMap<Tuple<Integer,Integer>, SOMMapNode> _MapNodes, int _ftrtype) {
		TreeMap<Double, ArrayList<SOMMapNode>> mapNodes = new TreeMap<Double, ArrayList<SOMMapNode>>();
		for (SOMMapNode node : _MapNodes.values()) {
			double sqDistToNode = getSqDistFromFtrType_ChiSq_Exclude(node, _ftrtype);
			ArrayList<SOMMapNode> tmpAra = mapNodes.get(sqDistToNode);
			if(tmpAra == null) {tmpAra = new ArrayList<SOMMapNode>();}
			tmpAra.add(node);
			mapNodes.put(sqDistToNode, tmpAra);		
		}				
		_setBMUFromDistMapOfBMUs(mapNodes);//, all_MapNodeLocToSqDist);
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes_ChiSq( _ftrtype);	
		return mapNodes;
	}//findBMUFromNodes_ChiSq		

	private String _toCSVString(TreeMap<Integer, Float> ftrs) {
		String res = ""+OID+",";
		for(int i=0;i<mapMgr.numFtrs;++i){
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
		for(int i=0;i<mapMgr.numFtrs;++i){
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
		float[] ftrs = new float[mapMgr.numFtrs];
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
		else {res +="\n\t";for(int i=0;i<mapMgr.numFtrs;++i){
			Float ftr = ftrs.get(i);
			res += String.format("%1.4g",  (ftr==null ? 0 : ftr)) + " | "; if((mapMgr.numFtrs > 40) && ((i+1)%30 == 0)){res +="\n\t";}}}
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
		String res = "Example OID# : "+OID ;
		if(null!=mapLoc){res+="Location on SOM map : " + mapLoc.toStrBrf();}
		if (mapMgr.numFtrs > 0) {
			res += "\nUnscaled Features (" +mapMgr.numFtrs+ " ) :";
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
	
	private static final String[] eventMapTypeKeys = new String[] {"orders", "opts", "links"};
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
		super(_map,ExDataType.ProspectTraining,_prspctData.OID);	
		if( _prspctData.rawJpMapOfArrays.size() > 0) {
			prs_JPGrp = _prspctData.rawJpMapOfArrays.firstKey();
			prs_JP = _prspctData.rawJpMapOfArrays.get(prs_JPGrp).get(0);
		} else {prs_JPGrp=0;prs_JP=0;}
		prs_LUDate = _prspctData.getDate();
		initObjsData() ;
	
	}//prospectData ctor
	
	//build this object based on csv string - rebuild data from csv string columns 4+
	public ProspectExample(SOMMapManager _map,String _OID, String _csvDataStr) {
		super(_map,ExDataType.ProspectTraining,_OID);		
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
		String evntType = eventMapTypeKeys[type];
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
		if (res==null) {mapMgr.dispMessage("ProspectExample","getOcccurenceMap","JpOccurrences map does not have key : " + key); return null;}
		return res;
	}
	
	protected void buildDataFromCSVString(int[] numEvntsAra, String _csvDataStr) {
		//each type of event list exists between the sentinel flag and the subsequent sentinel flag
		for (int i = 0; i<eventMapTypeKeys.length;++i) {
			if (numEvntsAra[i] > 0) {
				String key = eventMapTypeKeys[i];
				String stSentFlag = CSVSentinelLbls[i];
				String endSntnlFlag = (i <eventMapTypeKeys.length-1 ? CSVSentinelLbls[i+1] : null );
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
		for (String key : eventMapTypeKeys) {eventsByDateMap.put(key, new TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> ());	}
		//occurrence structures - keyed by type, then by JP
		JpOccurrences = new TreeMap<String, TreeMap<Integer, jpOccurrenceData>> ();
		//set these when this data is partitioned into testing and training data
		isTrainingData = false;
		testTrainDataIDX = -1;
	}//initObjsData
	
	public void setIsTrainingDataIDX(boolean val, int idx) {
		isTrainingData=val; 
		testTrainDataIDX=idx;
		type= isTrainingData ? ExDataType.ProspectTraining : ExDataType.ProspectTesting;
		nodeClrs = mapMgr.getClrVal(type);
	}//setIsTrainingDataIDX

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
		for (String key : eventMapTypeKeys) {
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
		case SOMMapManager.prspctIDX : 	{mapMgr.dispMessage("ProspectExample","addObj","ERROR attempting to add prospect raw data as event data. Ignored");return;}
		case SOMMapManager.orderEvntIDX : 	{		addDataToTrainMap((OrderEvent)obj,eventsByDateMap.get(eventMapTypeKeys[0]), 0); 		return;}
		case SOMMapManager.optEvntIDX : 	{		addDataToTrainMap((OptEvent)obj,eventsByDateMap.get(eventMapTypeKeys[1]), 1); 		return;}
		case SOMMapManager.linkEvntIDX : 	{		addDataToTrainMap((LinkEvent)obj,eventsByDateMap.get(eventMapTypeKeys[2]), 2); 		return;}
		default :{mapMgr.dispMessage("ProspectExample","addObj","ERROR attempting to add unknown raw data type : " + type + " as event data. Ignored");return;}
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
		for (String key : eventMapTypeKeys) {if (eventsByDateMap.get(key).size() > 0) {return true;}	}	
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
		for (String key : eventMapTypeKeys) {
			res += getSizeOfDataMap(eventsByDateMap.get(key))+",";
		}
		//res += getSizeOfDataMap(orderEventsByDateMap)+"," + getSizeOfDataMap(optEventsByDateMap)+",";
		//now build res string for all event data objects
		for(int i=0;i<eventMapTypeKeys.length;++i) {
			String key = eventMapTypeKeys[i];
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
			ftrMap = mapMgr.ftrCalcObj.calcFeatureVector(this,allJPs,JpOccurrences.get("orders"), JpOccurrences.get("links"), JpOccurrences.get("opts"));
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
		for (String key : eventMapTypeKeys) {
			res += toStringDateMap(eventsByDateMap.get(key), key);
			res += toStringOptOccMap(JpOccurrences.get(key), key + " occurences");
		}
		return res;
	}

}//class prospectExample


/**
 * this class implements a product example, to be used to query the SOM and to illuminate relevant regions on the map.  
 * The product can be specified by a single jp, or by the span of jps' related to a particular jpg, or even to multiple
 * Unlike prospect examples, which are inferred to have 0 values in features that are not explicitly populated, 
 * product examples might not be considered strictly exclusionary (in other words, non-populated features aren't used for distance calcs)
 * This is an important distinction for the SOM-from this we will learn about folks whose interest overlap into multiple jp regions.
 * @author john
 */
class ProductExample extends StraffSOMExample{
//	//column names for csv output of this SOM example
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
	private static ExDataType[] prospectTypes = new ExDataType[] {ExDataType.ProspectTraining, ExDataType.ProspectTesting};

	//color to illustrate map (around bmu) region corresponding to this product - use distance as alpha value
	private int[] prodClr;
		
	public ProductExample(SOMMapManager _map, TcTagData data) {
		//super(_map,ExDataType.Product,IDprfx + "_" +  String.format("%06d", IDcount++));
		super(_map,ExDataType.Product,data.OID);
		trainPrdctData = new TcTagTrainData(data);	
		initBMUMaps();		
	}//ctor
	
	public ProductExample(SOMMapManager _map,String _OID, String _csvDataStr) {
		super(_map,ExDataType.Product,_OID);
		trainPrdctData = new TcTagTrainData(_csvDataStr);
		initBMUMaps();
	}//ctor
	
	private void initBMUMaps() {
		prodClr = mapMgr.getRndClr();
		prodClr[3]=255;
		allMapNodesDists = new TreeMap[numFtrCompVals];
		for (Integer i=0; i<numFtrCompVals;++i) {			allMapNodesDists[i] = new TreeMap<Double,ArrayList<SOMMapNode>>();		}
	}	
	
	//Only used for products since products extend over non-exclusive zones of the map
	//distMeasType : "AllFtrs" : looks at all features for distances; "SharedFtrs" : looks at only features that are non-zero in the product example
	public void setMapNodesStruct(int mapNodeIDX, TreeMap<Double, ArrayList<SOMMapNode>> mapNodes) {
		allMapNodesDists[mapNodeIDX] =  mapNodes;
	}
	
	//call this once, before any load of data that will over-write the product examples is performed
	public static void initAllProdData() {
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
	private static double distToConf(double _dist, double _maxDist, double _maxDistScale) { return (_maxDist - _dist)*_maxDistScale;	}
	
	//build a map keyed by distance to each map node of arrays of maps of arrays of that mapnode's examples, each array keyed by their distance:
	//Outer map is keyed by distance from prod to map node, value is array (1 per map node @ dist) of maps keyed by distance from example to map node, value is array of all examples at that distance)
	//x : this product's distance to map node ; y : each example's distance to map node
	//based on their bmu mappings, to map nodes within _maxDist threshold of this node
	//subMap holds all map nodes within _maxDist of this node
	private TreeMap<Double, TreeMap<Double, ArrayList<StraffSOMExample>>> getExamplesNearThisProd(NavigableMap<Double, ArrayList<SOMMapNode>> subMap, ExDataType type) {
		TreeMap<Double, TreeMap<Double, ArrayList<StraffSOMExample>>> nodesNearProd = new TreeMap<Double, TreeMap<Double, ArrayList<StraffSOMExample>>>();
		HashMap<StraffSOMExample, Double> tmpMapOfNodes;
		for (Double dist : subMap.keySet()) {								//get all map nodes of certain distance from this node; - this dist is distance from this node to map node
			ArrayList<SOMMapNode> nodeList = subMap.get(dist);				//all ara of all map nodes of specified distance from this product node
			TreeMap<Double, ArrayList<StraffSOMExample>> tmpMapOfArrays = nodesNearProd.get(dist);			
			if (tmpMapOfArrays==null) {tmpMapOfArrays = new TreeMap<Double, ArrayList<StraffSOMExample>>();}				
			for (SOMMapNode n : nodeList) {									//for each map node get all examples of ExDataType that consider that map node BMU
				tmpMapOfNodes = n.getAllExsAndDist(type);					//each prospect example and it's distance from the bmu map node				
				for(StraffSOMExample exN : tmpMapOfNodes.keySet()) {		//for each example that treats map node as bmu
					Double distFromBMU = tmpMapOfNodes.get(exN);
					ArrayList<StraffSOMExample> exsAtDistFromMapNode = tmpMapOfArrays.get(distFromBMU);
					if (exsAtDistFromMapNode==null) {exsAtDistFromMapNode = new ArrayList<StraffSOMExample>();}
					exsAtDistFromMapNode.add(exN);
					tmpMapOfArrays.put(distFromBMU,exsAtDistFromMapNode);
				}//for each node at this map node
			}//for each map node at distance dist			
			nodesNearProd.put(dist, tmpMapOfArrays);
		}
		return nodesNearProd;
	}//getExamplesNearThisProd	
	
	//returns a map of dists from this product as keys and values as maps of distance of examples from their bmus as keys and example itself as value
	public TreeMap<Double, TreeMap<Double, ArrayList<StraffSOMExample>>> getAllExamplesNearThisProd(int distType, double _maxDist) {
		NavigableMap<Double, ArrayList<SOMMapNode>> subMap = allMapNodesDists[distType].headMap(_maxDist, true);		
		TreeMap<Double, TreeMap<Double, ArrayList<StraffSOMExample>>> allNodesAndDists = new TreeMap<Double, TreeMap<Double, ArrayList<StraffSOMExample>>>();		
		TreeMap<Double, ArrayList<StraffSOMExample>> srcMapNodeAra, destMapNodeAra;
		ArrayList<StraffSOMExample> srcExAra, destExAra;
		for(ExDataType _type : prospectTypes) {
			TreeMap<Double, TreeMap<Double, ArrayList<StraffSOMExample>>> tmpMapOfAllNodes = getExamplesNearThisProd(subMap, _type);
			for (Double dist1 : tmpMapOfAllNodes.keySet()) {//this is dist from product to map node source of these examples
				srcMapNodeAra = tmpMapOfAllNodes.get(dist1);				
				destMapNodeAra = allNodesAndDists.get(dist1);
				if(destMapNodeAra==null) {destMapNodeAra = new TreeMap<Double, ArrayList<StraffSOMExample>>();}
				for (Double dist2 : srcMapNodeAra.keySet()) {
					srcExAra = srcMapNodeAra.get(dist2);
					destExAra = destMapNodeAra.get(dist2);
					if(destExAra==null) {destExAra = new ArrayList<StraffSOMExample>(); }
					destExAra.addAll(srcExAra);
					destMapNodeAra.put(dist2, destExAra);
				}
				allNodesAndDists.put(dist1, destMapNodeAra);
			}		
		}//per type
		return allNodesAndDists;		
	}//getExListNearThisProd
	
	//get string array representation of this single product - built on demand
	public String[] getAllExsStrAra(int distType,double _maxDist) {
		ArrayList<String> resAra = new ArrayList<String>();
		TreeMap<Double, ArrayList<StraffSOMExample>> exmplsAtDist;		
		String ttlStr = "Product ID : " + this.OID + " JPs covered : ";
		for (Integer jp : allJPs) {	ttlStr += "" + jp + " : " + jpJpgMon.getJPNameFromJP(jp) + ", ";	}		
		resAra.add(ttlStr);
		resAra.add("OID,Confidence at Map Node,Error at Map Node");
		TreeMap<Double, TreeMap<Double, ArrayList<StraffSOMExample>>> allNodesAndConfs = getAllExamplesNearThisProd(distType, _maxDist);
		double _maxDistScale = 1.0/_maxDist;
		String confStr, dist2BMUStr;
		for(Double dist : allNodesAndConfs.keySet()) { 
			exmplsAtDist = allNodesAndConfs.get(dist);
			confStr = String.format("%.6f",distToConf(dist, _maxDist, _maxDistScale));
			for(Double dist2BMU : exmplsAtDist.keySet()){		
				ArrayList<StraffSOMExample> exsAtDistFromBMU = exmplsAtDist.get(dist2BMU);
				dist2BMUStr = String.format("%.6f",dist2BMU);
				for (StraffSOMExample exN : exsAtDistFromBMU) {				
					resAra.add("" + exN.OID + "," + confStr + ", "+dist2BMUStr);	
				}//for all examples at bmu
			}//for all bmus at certain distance
		}//for all distances/all bmus
		return resAra.toArray(new String[0]);	
	}//getBestExsStrAra	
	
	//take loaded data and convert to output data
	@Override
	protected void buildFeaturesMap() {
		ftrMap = new TreeMap<Integer, Float>();	
		//order map gives order value of each jp - provide multiplier for higher vs lower priority jps
		TreeMap<Integer, Integer> orderMap = trainPrdctData.getJPOrderMap();
		int numJPs = orderMap.size();
		//verify # of jps as expected
		if (numJPs != allJPFtrIDXs.size()) {	mapMgr.dispMessage("ProductExample", "buildFeaturesMap", "Problem with size of expected jps from trainPrdctData vs. allJPFtrIDXs : trainPrdctData says # jps == " +numJPs + " | allJPFtrIDXs.size() == " +allJPFtrIDXs.size());}
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
		String res = "Example OID# : "+OID;
		if(null!=mapLoc){res+="Location on SOM map : " + mapLoc.toStrBrf();}
		if (mapMgr.numFtrs > 0) {			res += "\n\tFeature Val(s) : " + dispFtrMapVals(ftrMap);		} 
		else {								res += "No Features for this product example";		}
		return res;
	}

}//class productExample

//this class is for a simple object to just represent a mouse-over on the visualization of the map
class DispSOMMapExample extends StraffSOMExample{
	private float ftrThresh;
	private int mapType;
	private int[] clrVal = new int[] {255,255,0,255};
	private String[] mseLabelAra;
	private float[] mseLabelDims;

	//need to support all ftr types from map - what type of ftrs are these?
	public DispSOMMapExample(SOMMapManager _map, myPointf ptrLoc, TreeMap<Integer, Float> _ftrs, float _thresh) {
		super(_map, ExDataType.MouseOver,"Mse_"+ptrLoc.toStrBrf());//(" + String.format("%.4f",this.x) + ", " + String.format("%.4f",this.y) + ", " + String.format("%.4f",this.z)+")
		//type of features used for currently trained map
		mapType = mapMgr.getCurrMapDataFrmt();
		
		ftrMap = new TreeMap<Integer, Float>();	
		ftrThresh = _thresh;
		allJPs = new HashSet<Integer>();
		allJPFtrIDXs = new ArrayList<Integer>();
		//decreasing order
		TreeMap<Float, String> strongestFtrs = new TreeMap<Float, String>(Collections.reverseOrder());
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
	public DispSOMMapExample(SOMMapManager _map, myPointf ptrLoc, float distData, float _thresh) {
		super(_map, ExDataType.MouseOver,"Mse_"+ptrLoc.toStrBrf());//(" + String.format("%.4f",this.x) + ", " + String.format("%.4f",this.y) + ", " + String.format("%.4f",this.z)+")
		//type of features used for currently trained map
		mapType = mapMgr.getCurrMapDataFrmt();
		
		ftrMap = new TreeMap<Integer, Float>();	
		ftrThresh = _thresh;
		allJPs = new HashSet<Integer>();
		allJPFtrIDXs = new ArrayList<Integer>();

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
		stdFtrMap = new TreeMap<Integer, Float>();
		if (allJPFtrIDXs.size() > 0) {stdFtrMap = calcStdFtrVector(ftrMap, allJPFtrIDXs);}
		stdFtrsBuilt = true;
	}

}//DispSOMMapExample

//this class will hold a structure to aggregate and process the examples of a particular type that consider the owning node a BMU
class SOMMapNodeBMUExamples{
	//owning node of these examples
	private SOMMapNode node;
	//map of examples that consider node to be their bmu; keyed by euclidian distance
	private TreeMap<Double,ArrayList<StraffSOMExample>> examplesBMU;

	//size of examplesBMU
	private int numMappedEx;
	//log size of examplesBMU +1, used for visualization radius
	private float logExSize;	
	//detail of rendered point representing parent node - should be based roughly on size of population
	private int nodeSphrDet;
	//string array holding relevant info for visualization
	private String[] visLabel;
	
	public SOMMapNodeBMUExamples(SOMMapNode _node) {	
		node = _node;
		examplesBMU = new TreeMap<Double,ArrayList<StraffSOMExample>>(); 
		init();
	}//ctor
	
	public void init() {
		examplesBMU.clear();
		numMappedEx = 0;
		logExSize = 0;
		nodeSphrDet = 2;
		visLabel = new String[] {""+node.OID+" : ", ""+numMappedEx};
	}//init
	
	//add passed example
	public void addExample(StraffSOMExample straffSOMExample) {addExample(straffSOMExample._sqDistToBMU,straffSOMExample);}
	public void addExample(double dist, StraffSOMExample straffSOMExample) {
		ArrayList<StraffSOMExample> tmpList = examplesBMU.get(dist);
		if(tmpList == null) {tmpList = new ArrayList<StraffSOMExample>();}
		tmpList.add(straffSOMExample);		
		examplesBMU.put(dist, tmpList);		
		numMappedEx = examplesBMU.size();		
	}//addExample
	
	//finalize calculations - perform after all examples are mapped - used for visualizations
	public void finalize() {	
		logExSize = (float) Math.log(numMappedEx + 1)*2;	
		nodeSphrDet = (int)( Math.log(logExSize+1)+2);
		visLabel = new String[] {""+node.OID+" : ", ""+numMappedEx};
	}
	
	public boolean hasMappings(){return numMappedEx != 0;}
	public int getNumExamples() {return numMappedEx;}

	/////////////////////
	// drawing routines for owning node
	public void drawMapNodeWithLabel(SOM_StraffordMain p) {
		p.pushMatrix();p.pushStyle();	
		p.show(node.mapLoc, logExSize, nodeSphrDet, node.nodeClrs,  visLabel); 		
		p.popStyle();p.popMatrix();		
	}

	public void drawMapNodeNoLabel(SOM_StraffordMain p) {
		p.pushMatrix();p.pushStyle();	
		p.show(node.mapLoc, logExSize, nodeSphrDet, node.nodeClrs); 		
		p.popStyle();p.popMatrix();		
	}
	
	//return a listing of all examples and their distance from this BMU
	public HashMap<StraffSOMExample, Double> getExsAndDist(){
		HashMap<StraffSOMExample, Double> res = new HashMap<StraffSOMExample, Double>();
		for(double dist : examplesBMU.keySet() ) {
			ArrayList<StraffSOMExample> tmpList = examplesBMU.get(dist);
			if(tmpList == null) {continue;}//should never happen			
			for (StraffSOMExample ex : tmpList) {res.put(ex, dist);}
		}
		return res;
	}//getExsAndDist()
	
	//return all example OIDs in array of CSV form, with key being distance and columns holding all examples that distance away
	public String[] getAllExampleDescs() {
		ArrayList<String> tmpRes = new ArrayList<String>();
		for(double dist : examplesBMU.keySet() ) {
			ArrayList<StraffSOMExample> tmpList = examplesBMU.get(dist);
			if(tmpList == null) {continue;}//should never happen
			String tmpStr = String.format("%.6f", dist);
			for (StraffSOMExample ex : tmpList) {tmpStr += "," + ex.OID;	}
			tmpRes.add(tmpStr);
		}		
		return tmpRes.toArray(new String[1]);		
	}//getAllTestExamples
}//class SOMMapNodeExamples


//this class represents a particular node in the SOM map
class SOMMapNode extends StraffSOMExample{
	private static float ftrThresh = 0.0f;
	public Tuple<Integer,Integer> mapNodeCoord;	
	
	private SOMMapNodeBMUExamples trainEx, prospectEx, prodEx;
	
	//set from u matrix built by somoclu - the similarity of this node to its neighbors
	private float uMatDist;
	private float[] dispBoxDims;		//box upper left corner x,y and box width,height
	private int[] uMatClr, segClr;
	private int segClrAsInt;
	public Tuple<Integer,Integer>[][] neighborMapCoords;				//array of arrays of row x col of neighbors to this node.  This node is 1,1 - this is for square map to use bicubic interpolation
	public float[][] neighborUMatWts;
	
	//owning segment for this map node
	private SOMMapSegment seg; 
	
	//feature type denotes what kind of features the tkns being sent represent - 0 is unmodded, 1 is standardized across all data for each feature, 2 is normalized across all features for single data point
	public SOMMapNode(SOMMapManager _map, Tuple<Integer,Integer> _mapNode, float[] _ftrs) {
		super(_map, ExDataType.MapNode,"Node_"+_mapNode.x+"_"+_mapNode.y);
		//ftrTypeMapBuilt = _ftrType;
		if(_ftrs.length != 0){	setFtrsFromFloatAra(_ftrs);	}
		//allJPs = buildJPsFromFtrAra(_ftrs, ftrThresh);
		initMapNode( _mapNode);
	}
	
	//feature type denotes what kind of features the tkns being sent represent
	public SOMMapNode(SOMMapManager _map,Tuple<Integer,Integer> _mapNode, String[] _strftrs) {
		super(_map, ExDataType.MapNode, "Node_"+_mapNode.x+"_"+_mapNode.y);
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
		mapNodeCoord = _mapNode;		
		mapLoc = mapMgr.buildScaledLoc(mapNodeCoord);
		dispBoxDims = mapMgr.buildUMatBoxCrnr(mapNodeCoord);		//box around map node
		initNeighborMap();
		//these are the same for map nodes
		mapNodeLoc.set(mapLoc);
		uMatClr = new int[3];
		
		trainEx = new SOMMapNodeBMUExamples(this);//, ExDataType.ProspectTraining);
		prospectEx  = new SOMMapNodeBMUExamples(this);//, ExDataType.ProspectTesting); 
		prodEx = new SOMMapNodeBMUExamples(this);//, ExDataType.Product);
		//allJPs should be made by here
		buildFeatureVector();
		clearSeg();
	}//initMapNode
	
	public void clearSeg() {		seg = null;segClr = new int[4]; segClrAsInt = 0x0;}	
	//add this node to a segment, and its neighbors - must be done after neighbors are found
	public void addToSeg(SOMMapSegment _seg) {
		seg=_seg;
		seg.addNode(this);
		segClr = seg.getSegClr();
		segClrAsInt = seg.getSegClrAsInt();
		int row = 1, col = 1;//1,1 is this node
		SOMMapNode ex = mapMgr.MapNodes.get(neighborMapCoords[row][col+1]);
		if(ex.shouldAddToSegment(seg.thresh)) {ex.addToSeg(seg);}
		ex = mapMgr.MapNodes.get(neighborMapCoords[row][col-1]);
		if(ex.shouldAddToSegment(seg.thresh)) {ex.addToSeg(seg);}
		ex = mapMgr.MapNodes.get(neighborMapCoords[row+1][col]);
		if(ex.shouldAddToSegment(seg.thresh)) {ex.addToSeg(seg);}
		ex = mapMgr.MapNodes.get(neighborMapCoords[row-1][col]);
		if(ex.shouldAddToSegment(seg.thresh)) {ex.addToSeg(seg);}
		
	}//	addToSeg
	
	//called by neighbor map node to see if this node should be added to segment
	public boolean shouldAddToSegment(float _thresh) {return (seg==null) && (uMatDist < _thresh);}	
	public SOMMapSegment getSegment() {return seg;}
	public int getSegClrAsInt() {return segClrAsInt;}
	
	public void setUMatDist(float _d) {uMatDist = _d; int clr=(int) (255*uMatDist); uMatClr = new int[] {clr,clr,clr};}
	public float getUMatDist() {return uMatDist;}	

	//initialize 4-neighbor node neighborhood - grid of adjacent 4x4 nodes
	//this is for individual visualization calculations - 
	//1 node lesser and 2 nodes greater than this node, with location in question being >= this node's location
	private void initNeighborMap() {
		int xLoc,yLoc;
		neighborMapCoords = new Tuple[4][];
		for(int row=-1;row<3;++row) {
			neighborMapCoords[row+1] = new Tuple[4];
			yLoc = row + mapNodeCoord.y;
			for(int col=-1;col<3;++col) {
				xLoc = col + mapNodeCoord.x;
				neighborMapCoords[row+1][col+1] = mapMgr.getMapLocTuple(xLoc, yLoc);
			}
		}		
	}//initNeighborMap()
	
	//2d array of all weights for neighors of this node, for bi-cubic interp
	public void buildNeighborWtVals() {
		neighborUMatWts = new float[neighborMapCoords.length][];		
		for(int row=0;row<neighborUMatWts.length;++row) {
			neighborUMatWts[row]=new float[neighborMapCoords[row].length];
			for(int col=0;col<neighborUMatWts[row].length;++col) {
				neighborUMatWts[row][col] = mapMgr.MapNodes.get(neighborMapCoords[row][col]).getUMatDist();
			}
		}
	}//buildNeighborWtVals
	
	//////////////////////////////////
	// interpolation for UMatrix dists
	//cubic formula in 1 dim
	private float findCubicVal(float[] p, float t) { 	return p[1]+0.5f*t*(p[2]-p[0] + t*(2.0f*p[0]-5.0f*p[1]+4.0f*p[2]-p[3] + t*(3.0f*(p[1]-p[2])+p[3]-p[0]))); }
	//return bicubic interpolation of each neighbor's UMatWt
	public float biCubicInterp(float tx, float ty) {
		float [] aAra = new float[neighborUMatWts.length];
		for (int row=0;row<neighborUMatWts.length;++row) {aAra[row]=findCubicVal(neighborUMatWts[row], tx);}
		float val = findCubicVal(aAra, ty);
		return ((val <= 0.0f) ? 0.0f : (val > 1.0f) ? 1.0f : val);
	}//biCubicInterp
		
	@Override
	//feature is already made in constructor, read from map
	protected void buildFeaturesMap() {	}
	@Override
	public String getRawDescrForCSV() {	return "Should not save SOMMapNodeExample to intermediate CSV";}
	@Override
	public String getRawDescColNamesForCSV() {return "Do not save SOMMapNodeExample to intermediate CSV";}
	@Override
	public void finalizeBuild() {	}
	@Override
	protected HashSet<Tuple<Integer, Integer>> getSetOfAllJpgJpData() {		return null;}//getSetOfAllJpgJpData	
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
				Float lb = minsAra[destIDX], diff = diffsAra[destIDX];
				float val = 0.0f;
				if (diff==0) {//same min and max
					if (lb > 0) {	val = 1.0f;}//only a single value same min and max-> set feature value to 1.0
					else {val= 0.0f;}
				} else {				val = (ftrMap.get(destIDX)-lb)/diff;				}
				stdFtrMap.put(destIDX,val);
				setMapOfJpWts(jp, val, stdFtrMapTypeKey);
			}//for each jp
		}
		stdFtrsBuilt = true;
		//by here all maps of per-type, per-feature val arrays of jps should be built
		buildMapOfJPsToRank();		
	}//buildStdFtrsMap_MapNode
	
	public void clearBMUExs(ExDataType _type) {
		switch (_type) { //trainEx, prospectEx, prodEx
			case ProspectTraining 	: {trainEx.init(); 		return;}//case 0 is training data
			case ProspectTesting 	: {prospectEx.init(); 	return;}//case 1 is test data
			case Product 			: {prodEx.init(); 		return;}//case 4 is product data		
			case MapNode			: {return;}
			case MouseOver			: {return;}
			default					: {return;}
		}
	}//addToBMUs
	
	//add passed example to appropriate bmu construct depending on what type of example is passed (training, testing, product)
	public void addExToBMUs(StraffSOMExample straffSOMExample) {
		switch (straffSOMExample.type) {
			case ProspectTraining 	: {trainEx.addExample(straffSOMExample._sqDistToBMU,straffSOMExample); 		return;}//case 0 is training data
			case ProspectTesting 	: {prospectEx.addExample(straffSOMExample._sqDistToBMU,straffSOMExample); 	return;}//case 1 is test data
			case Product 			: {prodEx.addExample(straffSOMExample._sqDistToBMU,straffSOMExample); 		return;}//case 4 is product data		
			case MapNode			: {System.out.println("Attempting to map unmappable example as map node : "+ straffSOMExample.toString());return;}
			case MouseOver			: {System.out.println("Attempting to map unmappable example as mouse node : "+ straffSOMExample.toString());return;}
			default					: {System.out.println("Attempting to map unmappable example in unhandled manner : "+ straffSOMExample.toString());return;}
		}
	}//addToBMUs 
	
	//add passed example to appropriate bmu construct depending on what type of example is passed (training, testing, product)
	public void addExToBMUs(double dist, StraffSOMExample straffSOMExample) {
		switch (straffSOMExample.type) {
			case ProspectTraining 	: {trainEx.addExample(dist,straffSOMExample); 		return;}//case 0 is training data
			case ProspectTesting 	: {prospectEx.addExample(dist,straffSOMExample); 	return;}//case 1 is test data
			case Product 			: {prodEx.addExample(dist,straffSOMExample); 		return;}//case 4 is product data		
			case MapNode			: {return;}
			case MouseOver			: {return;}
			default					: {return;}
		}
	}//addToBMUs 
	
	//finalize all calculations for examples using this node as a bmu - this calculates quantities based on totals derived, used for visualizations
	//MUST BE CALLED after adding all examples but before any visualizations will work
	public void finalizeAllBmus(ExDataType _type) {
		switch (_type) { //trainEx, prospectEx, prodEx
			case ProspectTraining 	: {trainEx.finalize();		return;}//case 0 is training data
			case ProspectTesting 	: {prospectEx.finalize(); 	return;}//case 1 is test data
			case Product 			: {prodEx.finalize(); 		return;}//case 4 is product data		
			case MapNode			: {return;}
			case MouseOver			: {return;}
			default					: {return;}
		}
	}
	
	//get # of requested type of examples mapping to this node
	public int getNumExamples(ExDataType _type) {
		switch (_type) { //trainEx, prospectEx, prodEx
			case ProspectTraining 	: {return trainEx.getNumExamples();}//case 0 is training data
			case ProspectTesting 	: {return prospectEx.getNumExamples();}//case 1 is test data
			case Product 			: {return prodEx.getNumExamples();}//case 4 is product data		
			case MapNode			: {return 0;}
			case MouseOver			: {return 0;}
			default					: {return -1;}
		}
	}	
	
	//get a map of all examples of specified type near this bmu and the distances for the example
	public HashMap<StraffSOMExample, Double> getAllExsAndDist(ExDataType _type){
		switch (_type) { //trainEx, prospectEx, prodEx
			case ProspectTraining 	: {return trainEx.getExsAndDist();}//case 0 is training data
			case ProspectTesting 	: {return prospectEx.getExsAndDist();}//case 1 is test data
			case Product 			: {return prodEx.getExsAndDist();}//case 4 is product data		
			case MapNode			: {return null;}
			case MouseOver			: {return null;}
		default					: {return null;}	
		}
	}//getAllExsAndDist

	
	//return string array of descriptions for the requested kind of examples mapped to this node
	public String[] getAllExampleDescs(ExDataType _type) {
		switch (_type) { //trainEx, prospectEx, prodEx
			case ProspectTraining 	: {return trainEx.getAllExampleDescs();}//case 0 is training data
			case ProspectTesting 	: {return prospectEx.getAllExampleDescs();}//case 1 is test data
			case Product 			: {return prodEx.getAllExampleDescs();}//case 4 is product data		
			case MapNode			: {return new String[0];}
			case MouseOver			: {return new String[0];}
			default					: {return new String[0];}
		}		
	}
	
	public void drawMePopLbl(SOM_StraffordMain p, ExDataType _type) {
		switch (_type) { //trainEx, prospectEx, prodEx
			case ProspectTraining 	: {trainEx.drawMapNodeWithLabel(p);return;}//case 0 is training data
			case ProspectTesting 	: {prospectEx.drawMapNodeWithLabel(p); return;}//case 1 is test data
			case Product 			: {prodEx.drawMapNodeWithLabel(p); return;}//case 4 is product data		
			case MapNode			: {return;}
			case MouseOver			: {return;}
			default					: {return;}
		}
	}
	
	public void drawMePopNoLbl(SOM_StraffordMain p, ExDataType _type) {
		switch (_type) { //trainEx, prospectEx, prodEx
		case ProspectTraining 	: {trainEx.drawMapNodeNoLabel(p);return;}//case 0 is training data
		case ProspectTesting 	: {prospectEx.drawMapNodeNoLabel(p); return;}//case 1 is test data
		case Product 			: {prodEx.drawMapNodeNoLabel(p); return;}//case 4 is product data		
		case MapNode			: {return;}
		case MouseOver			: {return;}
		default					: {return;}
		}		
	}
	
	public void drawMeSmallWt(SOM_StraffordMain p, int jpIDX){
		p.pushMatrix();p.pushStyle();
		Float wt = this.stdFtrMap.get(jpIDX);
		if (wt==null) {wt=0.0f;}
		p.show(mapLoc, 2, 2, nodeClrs, new String[] {this.OID+":",String.format("%.4f", wt)}); 
		p.popStyle();p.popMatrix();		
	}
	
	public void drawMeSmall(SOM_StraffordMain p){
		p.pushMatrix();p.pushStyle();
		p.show(mapLoc, 2, 2, nodeClrs, new String[] {this.OID}); 
		p.popStyle();p.popMatrix();		
	}		

	public void drawMeWithWt(SOM_StraffordMain p, float wt, String[] disp){
		p.pushMatrix();p.pushStyle();	
		p.show(mapLoc, wt, (int)wt+1, nodeClrs,  disp); 
		p.popStyle();p.popMatrix();		
	}//drawMeWithWt
	
	//draw a box around this node of uMatD color
	public void drawMeUMatDist(SOM_StraffordMain p){drawMeClrRect(p,uMatClr, 255);}
	public void drawMeSegClr(SOM_StraffordMain p){drawMeClrRect(p,segClr, segClr[3]);}
	public void drawMeProdBoxClr(SOM_StraffordMain p, int[] clr) {drawMeClrRect(p,clr, clr[3]);}
	//clr is 3 vals
	private void drawMeClrRect(SOM_StraffordMain p, int[] fclr, int alpha) {
		p.pushMatrix();p.pushStyle();
		p.setFill(fclr, alpha);
		p.noStroke();
		p.rect(dispBoxDims);		
		p.popStyle();p.popMatrix();	
	}//drawMeClrRect
	
	public String toString(){
		String res = "Node Loc : " + mapNodeCoord.toString()+"\t" + super.toString();
		return res;		
	}
}//SOMMapNodeExample

//this class holds functionality migrated from the DataPoint class for rendering on the map.  since this won't be always necessary, we're moving this code to different class so it can be easily ignored
abstract class baseDataPtVis{
	protected static SOMMapManager mapMgr;
	//type of example data this is
	protected ExDataType type;
	//location in mapspace most closely matching this node - actual map location (most likely between 4 map nodes)
	protected myPointf mapLoc;		
	//bmu map node location - this is same as mapLoc/ignored for map nodes
	protected myPointf mapNodeLoc;
	//draw-based vars
	protected float rad;
	protected static int drawDet;	
	//for debugging purposes, gives min and max radii of spheres that will be displayed on map for each node proportional to # of samples - only display related
	public static float minRad = 100000, maxRad = -100000;
	
	protected int[] nodeClrs;		//idx 0 ==fill, idx 1 == strk, idx 2 == txt
	
	public baseDataPtVis(SOMMapManager _map, ExDataType _type) {
		mapMgr = _map;type=_type;
		mapLoc = new myPointf();	
		mapNodeLoc = new myPointf();
		rad = 1.0f;
		drawDet = 2;
		nodeClrs = mapMgr.getClrVal(type);
	}//ctor	
	
	protected void setRad(float _rad){
		rad = _rad;//((float)(Math.log(2.0f*(_rad+1))));
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
		//show(myPointf P, float rad, int det, int[] clrs, String[] txtAra)
		p.show(mapLoc, _rad, drawDet, nodeClrs, new String[] {ID});
		//draw line to bmu location
		p.setColorValStroke(nodeClrs[1],255);
		p.strokeWeight(1.0f);
		p.line(mapLoc, mapNodeLoc);
		p.popStyle();p.popMatrix();		
	}//drawMeLinkedToBMU
	
	public void drawMeSmallNoLbl(SOM_StraffordMain p){
		p.pushMatrix();p.pushStyle();
		p.show(mapLoc, 2, 2, nodeClrs); 
		p.popStyle();p.popMatrix();		
	}	
		
	//override drawing in map nodes
	public final void drawMeMap(SOM_StraffordMain p){
		p.pushMatrix();p.pushStyle();	
		p.show(mapLoc, getRad(), drawDet, nodeClrs);		
		p.popStyle();p.popMatrix();		
	}//drawMeMap
	
	//override drawing in map nodes
	public final void drawMeMapClr(SOM_StraffordMain p, int[] clr){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at mapLoc
		p.show(mapLoc, rad,drawDet, clr, clr);
		p.popStyle();p.popMatrix();		
	}//drawMeMapClr
	
	public void drawMeRanked(SOM_StraffordMain p, String lbl, int[] clr, float rad, int rank){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label and no background box	
		p.showNoBox(mapLoc, rad, drawDet, clr, clr, SOM_StraffordMain.gui_White, lbl);
		p.popStyle();p.popMatrix();
	}
}//baseDataPtVis

/**
 * enum used to specify each kind of example data point, primarily for visualization purposes
 * @author john
 */
enum ExDataType {
	ProspectTraining(0),ProspectTesting(1),Product(2), MapNode(3), MouseOver(4);
	private int value; 
	private static Map<Integer, ExDataType> map = new HashMap<Integer, ExDataType>(); 
	static { for (ExDataType enumV : ExDataType.values()) { map.put(enumV.value, enumV);}}
	private ExDataType(int _val){value = _val;} 
	public int getVal(){return value;}
	public static ExDataType getVal(int idx){return map.get(idx);}
	public static int getNumVals(){return map.size();}						//get # of values in enum
};	



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
