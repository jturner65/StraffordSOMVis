package SOM_Strafford_PKG;

import java.util.*;
import java.util.Map.Entry;


/**
 * NOTE : None of the ADT's  in this file is thread safe so do not allow for opportunities for concurrent modification 
 * of any instanced object in this file. This decision was made for speed concerns - 
 * concurrency-protected objects have high overhead that proved greater than any gains in 10 execution threads.
 * 
 * This is the base class describing an example data point to be used to train a SOM
 * @author john
 */
public abstract class SOMExample extends baseDataPtVis{	
	//unique key field used for this example
	public final String OID;		
	//use a map to hold only sparse data frmt
	protected TreeMap<Integer, Float>[] ftrMaps;
	//idx's in feature vector that have non-zero values
	public ArrayList<Integer> allNonZeroFtrIDXs;	
	//magnitude of this feature vector
	public float ftrVecMag;	
	//keys for ftr map arrays
	protected static final int ftrMapTypeKey = SOMMapManager.useUnmoddedDat, normFtrMapTypeKey = SOMMapManager.useNormedDat, stdFtrMapTypeKey = SOMMapManager.useScaledDat;	
	protected static final Integer[] ftrMapTypeKeysAra = new Integer[] {ftrMapTypeKey, normFtrMapTypeKey, stdFtrMapTypeKey};

	private int[] stFlags;						//state flags - bits in array holding relevant process info
	public static final int
			debugIDX 			= 0,
			ftrsBuiltIDX		= 1,			//whether particular kind of feature was built or not
			stdFtrsBuiltIDX		= 2,			//..standardized (Across all examples per feature)
			normFtrsBuiltIDX	= 3,			//..normalized (feature vector scaled to have magnitude 1
			isBadTrainExIDX		= 4,			//whether this example is a good one or not - if all feature values == 0 then this is a useless example for training. only set upon feature vector calc
			ftrWtRptBuiltIDX	= 5;			//whether or not the structures used to calculate feature-based reports for this example have been calculated
	public static final int numFlags = 5;	
	
	//reference to map node that best matches this example node
	protected SOMMapNode bmu;			
	//this is the distance, using the chosen distance measure, to the best matching unit of the map for this example
	protected double _sqDistToBMU;
	//to hold 9 node neighborhood surrounding bmu - using array of nodes because nodes can be equidistant form multiple nodes
	//TODO set a list of these nodes for each SOMMapNodeExample upon their construction? will this speed up anything?
	private TreeMap<Double, ArrayList<SOMMapNode>> mapNodeNghbrs;
	//hash code for using in a map
	private int _hashCode;
	
	//these objects are for reporting on individual examples.  
	//use a map per feature type : unmodified, normalized, standardized,to hold the features sorted by weight as key, value is array of ftrs at a particular weight -submap needs to be instanced in descending key order
	private TreeMap<Float, ArrayList<Integer>>[] mapOfWtsToFtrIDXs;	
	//a map per feature type : unmodified, normalized, standardized, of ftr IDXs and their relative "rank" in this particular example, as determined by the weight calc
	private TreeMap<Integer,Integer>[] mapOfFtrIDXVsWtRank;	
	
	public SOMExample(SOMMapManager _map, ExDataType _type, String _id) {
		super(_map,_type);
		OID = _id;
		_sqDistToBMU = 0.0;
		initFlags();	
		ftrMaps = new TreeMap[ftrMapTypeKeysAra.length];
		for (int i=0;i<ftrMaps.length;++i) {
			ftrMaps[i] = new TreeMap<Integer, Float>(); 
		}
		String tmp = OID + "" + type;
		_hashCode = tmp.hashCode();
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
	
	public boolean isBadExample() {return getFlag(isBadTrainExIDX);}
	public void setIsBadExample(boolean val) { setFlag(isBadTrainExIDX,val);}
	
	//debugging tool to find issues behind occasional BMU seg faults
//	protected boolean checkForErrors(SOMMapNode _n, float[] dataVar){
//		if(mapMgr == null){					mapMgr.dispMessage("SOMMapNodeExample","checkForErrors","FATAL ERROR : SOMMapData object is null!");		return true;}//if mapdata is null then stop - should have come up before here anyway
//		if(_n==null){							mapMgr.dispMessage("SOMMapNodeExample","checkForErrors","_n is null!");		 return true;} 
//		if(_n.mapLoc == null){					mapMgr.dispMessage("SOMMapNodeExample","checkForErrors","_n has no maploc!");	return true;}
//		if(dataVar == null){					mapMgr.dispMessage("SOMMapNodeExample","checkForErrors","map variance not calculated : datavar is null!");	return true;	}
//		return false;
//	}//checkForErrors
	
	//add passed map node, with passed feature distance, to neighborhood nodes
	//using a map of arrays so that we can precalc distances 1 time
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
	
	//assign passed map node to be bmu
	protected void _setBMUAddToNeighborhood(SOMMapNode _n, double _dist) {
		//if (checkForErrors(_n, mapMgr.map_ftrsVar)) {return;}//if true then this is catastrophic error and should interrupt flow here
		bmu = _n;	
		_sqDistToBMU = _dist;
		this.mapLoc.set(bmu.mapLoc);		
		addBMUToNeighbrhdMap();
	}//_setBMUAddToNeighborhood
	
	//this adds the passed node as this example's best matching unit on the map
	//this also adds this data point to the map's node with a key of the distance
	//dataVar is variance of feature weights of map nodes.  this is for chi-squared distance measurements
	public void setBMU(SOMMapNode _n, int _ftrType){
		_setBMUAddToNeighborhood(_n,getSqDistFromFtrType(_n,  _ftrType));
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes( _ftrType);
		//dist here is distance of this training example to map node 
		//_n.addBMUExample(this);	//don't call this here so we can set the bmu of testing data without modifying bmu's themselves
	}//setBMU
	
	//this adds the passed node as this example's best matching unit on the map
	//this also adds this data point to the map's node with a key of the distance
	//dataVar is variance of feature weights of map nodes.  this is for chi-squared distance measurements
	public void setBMU_ChiSq(SOMMapNode _n, int _ftrType){
		_setBMUAddToNeighborhood(_n,getSqDistFromFtrType_ChiSq(_n,  _ftrType));
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes_ChiSq(_ftrType);		
		//dist here is distance of this training example to map node
		//_n.addBMUExample(this);
	}//setBMU
	
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
	
	//calculate appropriate modifier based on where neighbor node is related to where bmu node is - need to account for wrapping
	private float calcWrapMod(Integer bmuCoord, Integer neighborCoord, float mapDim) {
		float mod = 0.0f;
		if (neighborCoord > bmuCoord+1) {		mod = mapDim; }//this means that the example is actually lower than bmu, but due to wrap it is on the other side of the map, so add
		else if (neighborCoord < bmuCoord-1) {	mod = -mapDim;}//this means that the example is actually higher coord than bmu, but wrapped around to the other side, so subtract dim 
		return mod;
	}//calcWrapMod
	
	//return location of passed map node, with value added or subtracted based on whether it wraps around map
	private myPointf findNodeLocWrap(SOMMapNode mapNode, Integer bmuX, Integer bmuY, float mapW, float mapH) {
		Integer mapNode_X = mapNode.mapNodeCoord.x, mapNode_Y = mapNode.mapNodeCoord.y;
		myPointf loc = new myPointf(mapNode.mapLoc);
		float locXMod = calcWrapMod(bmuX, mapNode_X, mapW);		//subtract or add map width or height depending on whether neighborhood wraps around torroidal map
		float locYMod = calcWrapMod(bmuY, mapNode_Y, mapH);		
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
			for (SOMMapNode ex : tmpMap) {			ttlInvDist +=invDistP1;		}			
		}
		float mapW = mapMgr.getMapWidth(), mapH = mapMgr.getMapHeight();
		Integer bmuX = bmu.mapNodeCoord.x,  bmuY = bmu.mapNodeCoord.y;
		//scale by ttlInvDist so that all distance wts sum to 1
		for (double _dist : mapNodeNghbrs.keySet()) {
			invDistP1 = ((float) (1.0f/(1.0f+_dist))/ttlInvDist);					//handles 0 dist - max will be 0, min will be some fraction
			ArrayList<SOMMapNode> tmpMap = mapNodeNghbrs.get(_dist);
			for (SOMMapNode mapNode : tmpMap) {		
				//if ex is more than 1 in x or y from bmu, then wrap arround, need to add (or subtract) x or y dim of map
				locToAdd = findNodeLocWrap(mapNode, bmuX, bmuY, mapW, mapH);
				totalLoc._add(myPointf._mult(locToAdd, invDistP1));				
			}			
		}
		totalLoc.x += mapW;totalLoc.x %= mapW;//filter 
		totalLoc.y += mapH;totalLoc.y %= mapH;
		
		this.mapLoc.set(totalLoc);
	}//setExactMapLoc
	

	//build feature vector - call externally after finalize
	public final void buildFeatureVector() {//all jps seen by all examples must exist by here so that mapData.jpToFtrIDX has accurate data
		buildAllNonZeroFtrIDXs();
		buildFeaturesMap();
		setFlag(ftrsBuiltIDX,true);		
		buildNormFtrData();//once ftr map is built can normalize easily
		_PostBuildFtrVec_Priv();
	}//buildFeatureVector
	
	//these are called before and after an individual example's features are built
	protected abstract void buildAllNonZeroFtrIDXs();
	protected abstract void _PostBuildFtrVec_Priv();

	//build structures that require that the feature vector be built before hand
	public final void buildPostFeatureVectorStructs() {
		buildStdFtrsMap();
	}//buildPostFeatureVectorStructs

	//build normalized vector of data - only after features have been set
	protected void buildNormFtrData() {
		if(!getFlag(ftrsBuiltIDX)) {mapMgr.dispMessage("StraffSOMExample","buildNormFtrData","OID : " + OID + " : Features not built, cannot normalize feature data");return;}
		ftrMaps[normFtrMapTypeKey]=new TreeMap<Integer, Float>();
		if(this.ftrVecMag == 0) {return;}
		for (Integer IDX : ftrMaps[ftrMapTypeKey].keySet()) {
			Float val  = ftrMaps[ftrMapTypeKey].get(IDX)/this.ftrVecMag;
			ftrMaps[normFtrMapTypeKey].put(IDX,val);
			//setMapOfSrcWts(IDX, val, normFtrMapTypeKey);			
		}	
		setFlag(normFtrsBuiltIDX,true);
	}//buildNormFtrData
	
	//scale each feature value to be between 0->1 based on min/max values seen for this feature
	//all examples features will be scaled with respect to seen calc results 0- do not use this for
	//exemplar objects (those that represent a particular product, for example)
	//MUST BE SET WITH APPROPRIATE MINS AND DIFFS
	protected TreeMap<Integer, Float> calcStdFtrVector(TreeMap<Integer, Float> ftrs, ArrayList<Integer> jpIdxs, Float[] mins, Float[] diffs) {
		TreeMap<Integer, Float> sclFtrs = new TreeMap<Integer, Float>();
		for (Integer destIDX : jpIdxs) {
			Float lb = mins[destIDX], 	diff = diffs[destIDX];
			Float val = 0.0f;
			if (diff==0) {//same min and max
				if (lb > 0) { val = 1.0f;}//only a single value same min and max-> set feature value to 1.0
				else {		  val = 0.0f;}
			} else {
				val = (ftrs.get(destIDX)-lb)/diff;
			}	
			sclFtrs.put(destIDX,val);
			//setMapOfSrcWts(destIDX, val, stdFtrMapTypeKey);
			
		}//for each jp
		return sclFtrs;
	}//standardizeFeatureVector		getSqDistFromFtrType
	
	//call this to aggregate wts information for reporting and debugging purposes
	//protected abstract void setMapOfSrcWts(int destIDX, float wt, int mapToGetIDX);
	
	//initialize structures used to aggregate and report the ranking of particular ftrs for this example
	private void initPerFtrObjs() {
		mapOfWtsToFtrIDXs = new TreeMap[ftrMapTypeKeysAra.length];
		mapOfFtrIDXVsWtRank = new TreeMap[ftrMapTypeKeysAra.length];
		for(Integer ftrType : ftrMapTypeKeysAra) {//type of features
			mapOfWtsToFtrIDXs[ftrType] = new TreeMap<Float, ArrayList<Integer>>(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}});//descending key order
			mapOfFtrIDXVsWtRank[ftrType] = new TreeMap<Integer,Integer>();
		}
	}//initPerFtrObjs()
	
	//to execute per-example ftr-based reporting, first call buildFtrRptStructs for all examples, then iterate through getters for ftr type == mapToGet, then call clearFtrRptStructs to release memory
	//called to report on this example's feature weight rankings 
	public void buildFtrRprtStructs() {
		if(getFlag(ftrWtRptBuiltIDX)) {return;}
		initPerFtrObjs();
		//for every feature type build 
		for(Integer mapToGet : ftrMapTypeKeysAra) {//type of features
			int rank = 0;
			TreeMap<Integer, Float> ftrMap = ftrMaps[mapToGet];
			//go through features ara, for each ftr idx find rank 
			TreeMap<Float, ArrayList<Integer>> mapOfFtrsToIdxs = mapOfWtsToFtrIDXs[mapToGet];
			//shouldn't be null - means using inappropriate key
			if(mapOfFtrsToIdxs == null) {mapMgr.dispMessage("SOMExample" + OID,"buildFtrRprtStructs","Using inappropriate key to access mapOfWtsToFtrIDXs : " + mapToGet + " No submap exists with this key."); return;}	
			for (Integer ftrIDX : ftrMap.keySet()) {
				float wt = ftrMap.get(ftrIDX);
				ArrayList<Integer> ftrIDXsAtWt = mapOfFtrsToIdxs.get(wt);
				if (ftrIDXsAtWt == null) {ftrIDXsAtWt = new ArrayList<Integer>(); }
				ftrIDXsAtWt.add(ftrIDX);
				mapOfFtrsToIdxs.put(wt, ftrIDXsAtWt);
			}
			//after every feature is built, then poll mapOfFtrsToIdxs for ranked features
			TreeMap<Integer,Integer> mapOfRanks = mapOfFtrIDXVsWtRank[mapToGet];
			for (Float wtVal : mapOfFtrsToIdxs.keySet()) {
				ArrayList<Integer> jpsAtRank = mapOfFtrsToIdxs.get(wtVal);
				for (Integer jp : jpsAtRank) {	mapOfRanks.put(jp, rank);}
				++rank;
			}
			mapOfFtrIDXVsWtRank[mapToGet] = mapOfRanks;//probably not necessary since already initialized (will never be empty or deleted)					
		}//for ftrType		
		setFlag(ftrWtRptBuiltIDX, true);
	}//buildFtrReports
	
	//return mapping of ftr IDXs to rank for this example
	public TreeMap<Integer,Integer> getMapOfFtrIDXBsWtRank(int mapToGet){
		if(!getFlag(ftrWtRptBuiltIDX)) {
			mapMgr.dispMessage("SOMExample" + OID,"getMapOfFtrIDXBsWtRank","Feature-based report structures not yet built. Aborting.");
			return null;
		}
		return mapOfFtrIDXVsWtRank[mapToGet];
	}//getMapOfFtrIDXBsWtRank
	
	public TreeMap<Float, ArrayList<Integer>> getMapOfWtsToFtrIDXs(int mapToGet){
		if(!getFlag(ftrWtRptBuiltIDX)) {
			mapMgr.dispMessage("SOMExample" + OID,"getMapOfWtsToFtrIDXs","Feature-based report structures not yet built. Aborting.");
			return null;
		}
		return mapOfWtsToFtrIDXs[mapToGet];
	}//getMapOfWtsToFtrIDXs
		
	//clears out structures used for reports, to minimize memory footprint
	public void clearFtrRprtStructs() {		
		mapOfWtsToFtrIDXs = null;
		mapOfFtrIDXVsWtRank = null;		
		setFlag(ftrWtRptBuiltIDX, false);		
	}//clearFtrReports
	
	
	//this is here so can more easily use the mins and diffs equations
	protected TreeMap<Integer, Float> calcStdFtrVector(TreeMap<Integer, Float> ftrs, ArrayList<Integer> jpIdxs){
		return calcStdFtrVector(ftrs, jpIdxs, mapMgr.minsVals, mapMgr.diffsVals);
	}

	////////////////////////////////
	//distance measures between nodes
	/**
	 * These functions will find the specified distance between the passed node and this node, using the specified feature type
	 * @param fromNode
	 * @param _ftrType
	 * @return
	 */
	//return the chi-sq distance from this node to passed node
	protected double getSqDistFromFtrType_ChiSq(SOMExample fromNode, int _ftrType){		
		TreeMap<Integer, Float> fromftrMap = fromNode.ftrMaps[_ftrType], toftrMap = ftrMaps[_ftrType];
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
	}//getDistFromFtrType
	//return the sq sdistance between this map's ftrs and the passed ftrMaps[ftrMapTypeKey]
	protected double getSqDistFromFtrType(SOMExample fromNode, int _ftrType){
		TreeMap<Integer, Float> fromftrMap = fromNode.ftrMaps[_ftrType], toftrMap = ftrMaps[_ftrType];
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
	}//getDistFromFtrType	


	//return the chi-sq distance from this node to passed node, only measuring non-zero features in this node
	protected double getSqDistFromFtrType_ChiSq_Exclude(SOMExample fromNode, int _ftrType){
		TreeMap<Integer, Float> fromftrMap = fromNode.ftrMaps[_ftrType], toftrMap = ftrMaps[_ftrType];
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
	}//getDistFromFtrType

	//return the distance between this map's ftrs and the passed ftrMaps[ftrMapTypeKey], only measuring -this- nodes non-zero features.
	protected double getSqDistFromFtrType_Exclude(SOMExample fromNode, int _ftrType){
		TreeMap<Integer, Float> fromftrMap = fromNode.ftrMaps[_ftrType], toftrMap = ftrMaps[_ftrType];
		double res = 0.0;
		Set<Integer> allIdxs = new HashSet<Integer>(toftrMap.keySet());
		for (Integer key : allIdxs) {//either map will have this key
			Float frmVal = fromftrMap.get(key);
			if(frmVal == null) {frmVal = 0.0f;}
			Float toVal = toftrMap.get(key), diff = toVal - frmVal;
			res += (diff * diff);
		}
		return res;
	}//getDistFromFtrType	
	
	//this value corresponds to training data type - we want to check training data type counts at each node
	private final int trainDataTypeIDX = ExDataType.ProspectTraining.getVal();
	//given a sqdistance-keyed map of lists of mapnodes, this will find the best matching unit (min distance), with favor given to units that have more examples
	private void _setBMUFromMapNodeDistMap(TreeMap<Double, ArrayList<SOMMapNode>> mapNodes) {
		ArrayList<Tuple<Integer,Integer>> bmuKeys = new ArrayList<Tuple<Integer,Integer>>();
		Entry<Double, ArrayList<SOMMapNode>> topEntry = mapNodes.firstEntry();
		Double bmuDist = topEntry.getKey();
		ArrayList<SOMMapNode>  bmuList = topEntry.getValue();
		int numBMUs = bmuList.size();
		for (int i=0;i<numBMUs;++i) {	bmuKeys.add(i, bmuList.get(i).mapNodeCoord);	}
		SOMMapNode bestUnit = null;//keep null to break on errors - shouldn't happen // bmuList.get(0);//default to first entry
		if (numBMUs > 1) {//if more than 1 entry with same distance, find entry with most examples - if no entries have examples, then this will default to first entry
			int maxNumExamples = 0;
			for (int i=0;i<numBMUs;++i) {//# of map nodes sharing distance to this node
				SOMMapNode node = bmuList.get(i);
				int numExamples = node.getNumExamples(trainDataTypeIDX);//want # of training examples
				if (numExamples >= maxNumExamples) {//need to manage if all map nodes that are "best" have no direct training examples (might happen on large maps), hence >= and not >
					maxNumExamples = numExamples;
					bestUnit = node;
				}		
			}
		} else {//only 1
			bestUnit = bmuList.get(0);
		}	
		_setBMUAddToNeighborhood(bestUnit, bmuDist);
	}//_setBMUFromMapNodeDistMap
		
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
		_setBMUFromMapNodeDistMap(mapNodes);//,all_MapNodeLocToSqDist);		
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes( _ftrtype);	
		return mapNodes;
	}//findBMUFromNodes 	
	
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
		_setBMUFromMapNodeDistMap(mapNodes);//,all_MapNodeLocToSqDist);		
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
		_setBMUFromMapNodeDistMap(mapNodes);//, all_MapNodeLocToSqDist);
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes_ChiSq( _ftrtype);	
		return mapNodes;
	}//findBMUFromNodes_ChiSq		
	
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
		_setBMUFromMapNodeDistMap(mapNodes);//, all_MapNodeLocToSqDist);
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
			case SOMMapManager.useUnmoddedDat : {return _toCSVString(ftrMaps[ftrMapTypeKey]); }
			case SOMMapManager.useNormedDat  : {return _toCSVString(getFlag(normFtrsBuiltIDX) ? ftrMaps[normFtrMapTypeKey] : ftrMaps[ftrMapTypeKey]);}
			case SOMMapManager.useScaledDat  : {return _toCSVString(getFlag(stdFtrsBuiltIDX) ? ftrMaps[stdFtrMapTypeKey] : ftrMaps[ftrMapTypeKey]); }
			default : {return _toCSVString(ftrMaps[ftrMapTypeKey]); }
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
			case SOMMapManager.useUnmoddedDat : {return _toLRNString(ftrMaps[ftrMapTypeKey], sep); }
			case SOMMapManager.useNormedDat   : {return _toLRNString(getFlag(normFtrsBuiltIDX) ? ftrMaps[normFtrMapTypeKey] : ftrMaps[ftrMapTypeKey], sep);}
			case SOMMapManager.useScaledDat   : {return _toLRNString(getFlag(stdFtrsBuiltIDX) ? ftrMaps[stdFtrMapTypeKey] : ftrMaps[ftrMapTypeKey], sep); }
			default : {return _toLRNString(ftrMaps[ftrMapTypeKey], sep); }
		}		
	}//toLRNString
	
	//for (Integer jpIdx : allJPFtrIDXs) {res += ""+jpIdx+":"+ftrs[jpIdx]+" ";}
	private String _toSVMString(TreeMap<Integer, Float> ftrs) {
		String res = "";
		for (Integer jpIdx : allNonZeroFtrIDXs) {res += "" + jpIdx + ":" + String.format("%1.7g", ftrs.get(jpIdx)) + " ";}
		return res;}//_toSVMString
	
	//return LRN-format (dense) string of this object's features, depending on which type is selected - check to make sure 2ndary features exist before attempting to build data strings
	public String toSVMString(int _type) {
		switch(_type){
			case SOMMapManager.useUnmoddedDat : {return _toSVMString(ftrMaps[ftrMapTypeKey]); }
			case SOMMapManager.useNormedDat   : {return _toSVMString(getFlag(normFtrsBuiltIDX) ? ftrMaps[normFtrMapTypeKey] : ftrMaps[ftrMapTypeKey]);}
			case SOMMapManager.useScaledDat   : {return _toSVMString(getFlag(stdFtrsBuiltIDX) ? ftrMaps[stdFtrMapTypeKey] : ftrMaps[ftrMapTypeKey]); }
			default : {return _toSVMString(ftrMaps[ftrMapTypeKey]); }
		}		
	}//toLRNString

	private float[] _getFtrsFromMap(TreeMap<Integer, Float> ftrMap) {
		float[] ftrs = new float[mapMgr.numFtrs];
		for (Integer ftrIdx : ftrMap.keySet()) {ftrs[ftrIdx]=ftrMap.get(ftrIdx);		}
		return ftrs;
	}
	
	//build feature vector on demand
	public float[] getFtrs() {return _getFtrsFromMap(this.ftrMaps[ftrMapTypeKey]);}
	//build stdfeature vector on demand
	public float[] getStdFtrs() {return _getFtrsFromMap(this.ftrMaps[stdFtrMapTypeKey]);}
	//build normfeature vector on demand
	public float[] getNormFtrs() {return _getFtrsFromMap(this.ftrMaps[normFtrMapTypeKey]);}	
	
	public TreeMap<Integer, Float> getCurrentFtrMap(int _type){
		switch(_type){
			case SOMMapManager.useUnmoddedDat : {return ftrMaps[ftrMapTypeKey]; }
			case SOMMapManager.useNormedDat   : {return (getFlag(normFtrsBuiltIDX) ? ftrMaps[normFtrMapTypeKey] : ftrMaps[ftrMapTypeKey]);}
			case SOMMapManager.useScaledDat   : {return (getFlag(stdFtrsBuiltIDX) ? ftrMaps[stdFtrMapTypeKey] : ftrMaps[ftrMapTypeKey]); }
			default : {return ftrMaps[ftrMapTypeKey]; }
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
		else {res +="\n\t";for(int i=0;i<mapMgr.numFtrs;++i){
			Float ftr = ftrs.get(i);
			res += String.format("%1.4g",  (ftr==null ? 0 : ftr)) + " | "; if((mapMgr.numFtrs > 40) && ((i+1)%30 == 0)){res +="\n\t";}}}
		return res;
	}
	protected String dispFtrMapVals(TreeMap<Integer, Float> ftrs) {
		String res = "";
		if((ftrs==null) || (ftrs.size() == 0)){res+=" None\n";} 
		else {			for(Integer i : ftrs.keySet()){				res += dispFtrVal(ftrs, i);}}		
		return res;
	}
	//return a string value corresponding to a specific feature index in the sparse ftr array
	protected abstract String dispFtrVal(TreeMap<Integer, Float> ftrs, Integer idx);
	@Override
	public int hashCode() {		return _hashCode;	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)			return true;
		if (obj == null)			return false;
		if (getClass() != obj.getClass())			return false;
		SOMExample other = (SOMExample) obj;
		if (_hashCode != other._hashCode)			return false;
		return true;
	}
	
	private void initFlags(){stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlag(i,false);}}
	public void setAllFlags(int[] idxs, boolean val) {for (int idx : idxs) {setFlag(idx, val);}}
	public void setFlag(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case debugIDX			: {break;}	
			case ftrsBuiltIDX		: {break;}
			case stdFtrsBuiltIDX	: {break;}
			case normFtrsBuiltIDX	: {break;}
			case isBadTrainExIDX	: {break;}
		}
	}//setFlag		
	public boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		

	@Override
	public String toString(){
		String res = "Example OID# : "+OID ;
		if(null!=mapLoc){res+="Location on SOM map : " + mapLoc.toStrBrf();}
		if (mapMgr.numFtrs > 0) {
			res += "\nUnscaled Features (" +mapMgr.numFtrs+ " ) :";
			res += dispFtrs(ftrMaps[ftrMapTypeKey]);
			res +="\nScaled Features : ";
			res += dispFtrs(ftrMaps[stdFtrMapTypeKey]);
			res +="\nNormed Features : ";
			res += dispFtrs(ftrMaps[normFtrMapTypeKey]);
		}
		return res;
	}
}//SOMExample 


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


//this class will hold a structure to aggregate and process the examples of a particular type that consider the owning node a BMU
class SOMMapNodeBMUExamples{
	//owning node of these examples
	private SOMMapNode node;
	//map of examples that consider node to be their bmu; keyed by euclidian distance
	private TreeMap<Double,ArrayList<SOMExample>> examplesBMU;

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
		examplesBMU = new TreeMap<Double,ArrayList<SOMExample>>(); 
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
	public void addExample(SOMExample straffSOMExample) {addExample(straffSOMExample._sqDistToBMU,straffSOMExample);}
	public void addExample(double dist, SOMExample straffSOMExample) {
		ArrayList<SOMExample> tmpList = examplesBMU.get(dist);
		if(tmpList == null) {tmpList = new ArrayList<SOMExample>();}
		tmpList.add(straffSOMExample);		
		examplesBMU.put(dist, tmpList);		
		numMappedEx = examplesBMU.size();		
	}//addExample
	
	//finalize calculations - perform after all examples are mapped - used for visualizations
	public void finalize() {	
		logExSize = (float) Math.log(numMappedEx + 1)*1.5f;	
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
	public HashMap<SOMExample, Double> getExsAndDist(){
		HashMap<SOMExample, Double> res = new HashMap<SOMExample, Double>();
		for(double dist : examplesBMU.keySet() ) {
			ArrayList<SOMExample> tmpList = examplesBMU.get(dist);
			if(tmpList == null) {continue;}//should never happen			
			for (SOMExample ex : tmpList) {res.put(ex, dist);}
		}
		return res;
	}//getExsAndDist()
	
	//return all example OIDs in array of CSV form, with key being distance and columns holding all examples that distance away
	public String[] getAllExampleDescs() {
		ArrayList<String> tmpRes = new ArrayList<String>();
		for(double dist : examplesBMU.keySet() ) {
			ArrayList<SOMExample> tmpList = examplesBMU.get(dist);
			if(tmpList == null) {continue;}//should never happen
			String tmpStr = String.format("%.6f", dist);
			for (SOMExample ex : tmpList) {tmpStr += "," + ex.OID;	}
			tmpRes.add(tmpStr);
		}		
		return tmpRes.toArray(new String[1]);		
	}//getAllTestExamples
}//class SOMMapNodeExamples

/**
 * objects of inheritors to this abstract class represent nodes in the SOM.  
 * The instancing class is responsible for managing any connections to underlying src data, which is project dependent
 * @author john
 */
abstract class SOMMapNode extends SOMExample{
	protected static float ftrThresh = 0.0f;			//change to non-zero value if wanting to clip very low values
	public Tuple<Integer,Integer> mapNodeCoord;	
	
	//protected SOMMapNodeBMUExamples trainEx, prospectEx, prodEx;
	protected SOMMapNodeBMUExamples[] BMUExampleNodes;//	
	
	//set from u matrix built by somoclu - the similarity of this node to its neighbors
	protected float uMatDist;
	protected float[] dispBoxDims;		//box upper left corner x,y and box width,height
	protected int[] uMatClr, segClr;
	protected int segClrAsInt;
	public Tuple<Integer,Integer>[][] neighborMapCoords;				//array of arrays of row x col of neighbors to this node.  This node is 1,1 - this is for square map to use bicubic interpolation
	public float[][] neighborUMatWts;
	
	//owning segment for this map node
	private SOMMapSegment seg; 
	
	//build a map node from a float array of ftrs
	public SOMMapNode(SOMMapManager _map, Tuple<Integer,Integer> _mapNode, float[] _ftrs) {
		super(_map, ExDataType.MapNode,"Node_"+_mapNode.x+"_"+_mapNode.y);
		if(_ftrs.length != 0){	setFtrsFromFloatAra(_ftrs);	}
		initMapNode( _mapNode);
	}
	
	//build a map node from a string array of features
	public SOMMapNode(SOMMapManager _map,Tuple<Integer,Integer> _mapNode, String[] _strftrs) {
		super(_map, ExDataType.MapNode, "Node_"+_mapNode.x+"_"+_mapNode.y);
		if(_strftrs.length != 0){	
			float[] _tmpFtrs = new float[_strftrs.length];		
			for (int i=0;i<_strftrs.length; ++i) {		_tmpFtrs[i] = Float.parseFloat(_strftrs[i]);	}
			setFtrsFromFloatAra(_tmpFtrs);	
		}
		initMapNode( _mapNode);
	}
	//build feature vector from passed feature array
	private void setFtrsFromFloatAra(float[] _ftrs) {
		ftrMaps[ftrMapTypeKey] = new TreeMap<Integer, Float>();
		float ftrVecSqMag = 0.0f;
		for(int i = 0; i < _ftrs.length; ++i) {	
			Float val =  _ftrs[i];
			if (val > ftrThresh) {
				ftrVecSqMag+=val*val;
				ftrMaps[ftrMapTypeKey].put(i, val);
			}
		}
		//called after features are built because that's when we have all jp's for this example determined
		ftrVecMag = (float) Math.sqrt(ftrVecSqMag);		
		
		setFlag(ftrsBuiltIDX, true);
		//buildNormFtrData();		
	}//setFtrsFromFloatAra	
	
	//this will map feature values to some representation of the underlying feature description - this is specific to undelrying data
	protected abstract void _initDataFtrMappings();
	
	private void initMapNode(Tuple<Integer,Integer> _mapNode){
		mapNodeCoord = _mapNode;		
		mapLoc = mapMgr.buildScaledLoc(mapNodeCoord);
		dispBoxDims = mapMgr.buildUMatBoxCrnr(mapNodeCoord);		//box around map node
		initNeighborMap();
		//these are the same for map nodes
		mapNodeLoc.set(mapLoc);
		uMatClr = new int[3];
		BMUExampleNodes = new SOMMapNodeBMUExamples[ExDataType.getNumVals()];
		for(int i=0;i<BMUExampleNodes.length;++i) {
			BMUExampleNodes[i] = new SOMMapNodeBMUExamples(this);
		}
//		trainEx = new SOMMapNodeBMUExamples(this);
//		prospectEx  = new SOMMapNodeBMUExamples(this);
//		prodEx = new SOMMapNodeBMUExamples(this);
		//allJPs should be made by here - map nodes only have features assigned in constructor, since they are built by map loader from trained map data
		//buildFeatureVector();
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
	//feature is already made in constructor, read from map, so this is ignored
	protected void buildFeaturesMap() {	}
	@Override
	public String getRawDescrForCSV() {	return "Should not save SOMMapNode to intermediate CSV";}
	@Override
	public String getRawDescColNamesForCSV() {return "Do not save SOMMapNode to intermediate CSV";}
	//map nodes do not use finalize
	@Override
	public void finalizeBuild() {	}
	@Override
	protected HashSet<Tuple<Integer, Integer>> getSetOfAllJpgJpData() {		return null;}//getSetOfAllJpgJpData	
	//this should not be used - should build stdFtrsmap based on ranges of each ftr value in trained map
	@Override
	protected void buildStdFtrsMap() {
		mapMgr.dispMessage("SOMMapNode","buildStdFtrsMap","Calling inappropriate buildStdFtrsMap for SOMMapNode : should call buildStdFtrsMap_MapNode from SOMDataLoader using trained map w/arrays of per feature mins and diffs");		
	}
	//call this instead of buildStdFtrsMap, passing mins and diffs
	
	//called by SOMDataLoader - these are standardized based on data mins and diffs seen in -map nodes- feature data, not in training data
	public abstract void buildStdFtrsMapFromFtrData_MapNode(float[] minsAra, float[] diffsAra);		
	
//	public void clearBMUExs(ExDataType _type) {
//		switch (_type) { //trainEx, prospectEx, prodEx
//			case ProspectTraining 	: {trainEx.init(); 		return;}//case 0 is training data
//			case ProspectTesting 	: {prospectEx.init(); 	return;}//case 1 is test data
//			case Product 			: {prodEx.init(); 		return;}//case 4 is product data		
//			case MapNode			: {return;}
//			case MouseOver			: {return;}
//			default					: {return;}
//		}
//	}//addToBMUs

	public void clearBMUExs(int _typeIDX) {
		BMUExampleNodes[_typeIDX].init();
	}//addToBMUs

	
	//add passed example to appropriate bmu construct depending on what type of example is passed (training, testing, product)
	public void addExToBMUs(SOMExample ex) {
		int _typeIDX = ex.type.getVal();
		BMUExampleNodes[_typeIDX].addExample(ex._sqDistToBMU,ex);
	}//addToBMUs 
	
	//add passed example to appropriate bmu construct depending on what type of example is passed (training, testing, product)
	public void addExToBMUs(double dist, SOMExample ex) {
		int _typeIDX = ex.type.getVal();
		BMUExampleNodes[_typeIDX].addExample(dist,ex);
	}//addToBMUs 
	
	//finalize all calculations for examples using this node as a bmu - this calculates quantities based on totals derived, used for visualizations
	//MUST BE CALLED after adding all examples but before any visualizations will work
	public void finalizeAllBmus(int _typeIDX) {
		BMUExampleNodes[_typeIDX].finalize();
	}
	
	//get # of requested type of examples mapping to this node
	public int getNumExamples(int _typeIDX) {
		return BMUExampleNodes[_typeIDX].getNumExamples();
	}	
	
	//get a map of all examples of specified type near this bmu and the distances for the example
	public HashMap<SOMExample, Double> getAllExsAndDist(int _typeIDX){
		return BMUExampleNodes[_typeIDX].getExsAndDist();
	}//getAllExsAndDist

	
	//return string array of descriptions for the requested kind of examples mapped to this node
	public String[] getAllExampleDescs(int _typeIDX) {
		return BMUExampleNodes[_typeIDX].getAllExampleDescs();
	}
	
	public void drawMePopLbl(SOM_StraffordMain p, int _typeIDX) {
		BMUExampleNodes[_typeIDX].drawMapNodeWithLabel(p);
	}
	
	public void drawMePopNoLbl(SOM_StraffordMain p, int _typeIDX) {
		BMUExampleNodes[_typeIDX].drawMapNodeNoLabel(p);
	}
	
	public void drawMeSmallWt(SOM_StraffordMain p, int jpIDX){
		p.pushMatrix();p.pushStyle();
		Float wt = this.ftrMaps[stdFtrMapTypeKey].get(jpIDX);
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
	
}//class SOMMapNode

//class description for a data point - used to distinguish different jp-jpg members - class membership is determined by comparing the label
//TODO change this to some other structure, or other comparison mechanism?  allow for subset membership check?
class dataClass implements Comparable<dataClass> {
	
	public String label;
	public String lrnKey;
	private String cls;
	
	public int jpGrp, jp;
	//color of this class, for vis rep
	public int[] clrVal;
	
	public dataClass(String _lrnKey, String _lbl, String _cls, int[] _clrVal){
		lrnKey=_lrnKey;
		label = _lbl;	
		cls = _cls;
		clrVal = _clrVal;
	}	
	public dataClass(dataClass _o){this(_o.lrnKey, _o.label,_o.cls, _o.clrVal);}//copy ctor
	//set the defini
	public void setJpJPG(int _jpGrp, int _jp) {jpGrp=_jpGrp;jp=_jp;}
		
	//this will guarantee that, so long as a string has only one period, the value returned will be in the appropriate format for this mocapClass to match it
	//reparses and recalcs subject and clip from passed val
	public static String getPrfxFromData(String val){
		String[] valTkns = val.trim().split("\\.");
		return String.format("%03d",(Integer.parseInt(valTkns[0]))) + "."+ String.format("%03d",(Integer.parseInt(valTkns[1])));		
	}	
	@Override
	public int compareTo(dataClass o) {	return label.compareTo(o.label);}
	public String toCSVString(){String res = "" + lrnKey +","+label+","+cls;	return res;}
	public String getFullLabel(){return label +"|"+cls;}
	//public static String buildPrfx(int val){return (val < 100 ? (val < 10 ? "00" : "0") : "") + val;}//handles up to 999 val to be prefixed with 0's	
	public String toString(){
		String res = "Label :  " +label + "\tLrnKey : " + lrnKey  + "\tJPGroup # : " + String.format("%03d",jpGrp)+ "\tJP # : "+String.format("%03d",jp)+"\tDesc : "+cls;
		return res;		
	}	
}//dataClass
