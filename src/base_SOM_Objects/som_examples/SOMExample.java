package base_SOM_Objects.som_examples;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiFunction;

import base_SOM_Objects.*;
import base_SOM_Objects.som_segments.segmentData.SOM_MapNodeSegmentData;
import base_SOM_Objects.som_segments.segments.SOM_MappedSegment;
import base_UI_Objects.*;
import base_Utils_Objects.*;
import base_Utils_Objects.io.MessageObject;
import base_Utils_Objects.io.MsgCodes;
import base_Utils_Objects.vectorObjs.Tuple;
import base_Utils_Objects.vectorObjs.myPointf;


/**
 * NOTE : None of the data types in this file are thread safe so do not allow for opportunities for concurrent modification
 * of any instanced object in this file. This decision was made for speed concerns - 
 * concurrency-protected objects have high overhead that proved greater than any gains in 10 execution threads.
 * 
 * All multithreaded access of these objects should be designed such that any individual object is only accessed by 
 * a single thread.
 * 
 * This is the base class describing an example data point to be used to train a SOM
 * @author john turner
 */
public abstract class SOMExample extends baseDataPtVis{	
	//unique key field used for this example
	public final String OID;		
	//use a map to hold only sparse data of each frmt for feature vector
	protected TreeMap<Integer, Float>[] ftrMaps;	
	//this map is what is used by examples to compare for mappings - this may include combinations of other features or values
	//when all ftrs are calculated (unmodded, normalized and stdizd) they need to be mapped to this structure, possibly in
	//combination with an alternate set of features or other calculations
	protected TreeMap<Integer, Float>[] compFtrMaps;
	//idx's in feature vector that have non-zero values
	public ArrayList<Integer> allNonZeroFtrIDXs;	
	//magnitude of this feature vector
	public float ftrVecMag;	
	//keys for ftr map arrays
	protected static final int ftrMapTypeKey = SOM_MapManager.useUnmoddedDat, normFtrMapTypeKey = SOM_MapManager.useNormedDat, stdFtrMapTypeKey = SOM_MapManager.useScaledDat;	
	protected static final Integer[] ftrMapTypeKeysAra = new Integer[] {ftrMapTypeKey, normFtrMapTypeKey, stdFtrMapTypeKey};

	private int[] stFlags;						//state flags - bits in array holding relevant process info
	public static final int
			debugIDX 			= 0,
			ftrsBuiltIDX		= 1,			//whether particular kind of feature was built or not
			stdFtrsBuiltIDX		= 2,			//..standardized (Across all examples per feature)
			normFtrsBuiltIDX	= 3,			//..normalized (feature vector scaled to have magnitude 1
			isBadTrainExIDX		= 4,			//whether this example is a good one or not - if all feature values == 0 then this is a useless example for training. only set upon feature vector calc
			ftrWtRptBuiltIDX	= 5;			//whether or not the structures used to calculate feature-based reports for this example have been calculated
		
	public static final int numFlags = 6;	
	
	//reference to map node that best matches this example node
	private SOMMapNode bmu;			
	//this is the squared distance, using the chosen distance measure, to the best matching unit of the map for this example
	private double _sqDistToBMU;
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
	
	
	//is this datapoint used for training; whether this record has a source "event" attached to it
	protected boolean isTrainingData;
	//this is index for this data point in training/testing data array; original index in preshuffled array (reflecting build order)
	protected int testTrainDataIDX;
	//two maps of distances to each map node for each example, with each array either including unshared features or excluding unshared features in distance calc
	//keyed by distance, array list is list of map nodes at that distance
	protected TreeMap<Double,ArrayList<SOMMapNode>>[] allMapNodesDists;	
	//two kinds of maps to bmus available - all ftrs looks at all feature values for distances, 
	//while shared only measures distances where this example's wts are non-zero
	public static final int
		AllFtrsIDX = 0,				//looks at all features in this node for distance calculations
		SharedFtrsIDX = 1;			//looks only at non-zero features in this node for distance calculations
	protected static int numFtrCompVals = 2;

	//segments this product belongs to, based on features, classes(jp) and categories(jp groups)
	//there will be 1 entry for all ftrs, jps and jpgroups this example belongs to	
	private TreeMap<Integer, SOM_MappedSegment> ftrWtSegData;			//keyed by non-zero ftr index	
	private TreeMap<Integer, SOM_MappedSegment> class_SegData;			//segment membership manager class mapping
	private TreeMap<Integer, SOM_MappedSegment> categorys_SegData;		//segment membership manager category mapping

	
	public SOMExample(SOM_MapManager _map, ExDataType _type, String _id) {
		super(_map,_type);
		OID = _id;
		set_sqDistToBMU(0.0);
		initFlags();	
		ftrMaps = new TreeMap[ftrMapTypeKeysAra.length];
		for (int i=0;i<ftrMaps.length;++i) {			ftrMaps[i] = new TreeMap<Integer, Float>(); 		}
		compFtrMaps = new TreeMap[ftrMapTypeKeysAra.length];		
		for (int i=0;i<compFtrMaps.length;++i) {			compFtrMaps[i] = new TreeMap<Integer, Float>(); 		}
		String tmp = OID + "" + type;
		_hashCode = tmp.hashCode();
		_initSegStructs();
	}//ctor
	
	//copy ctor - shallow copy of _otr - used to provide casting between nearly identical example types
	//it is expected that _otr ref will be disposed
	public SOMExample(SOMExample _otr) {
		super(_otr);
		OID = _otr.OID;
		setBmu(_otr.getBmu());
		set_sqDistToBMU(_otr.get_sqDistToBMU());
		_hashCode = _otr._hashCode;
		ftrMaps = _otr.ftrMaps;
		compFtrMaps = _otr.compFtrMaps;
		ftrVecMag = _otr.ftrVecMag;
		allNonZeroFtrIDXs = _otr.allNonZeroFtrIDXs;
		mapOfWtsToFtrIDXs = _otr.mapOfWtsToFtrIDXs;
		mapOfFtrIDXVsWtRank = _otr.mapOfFtrIDXVsWtRank;
		mapNodeNghbrs = _otr.mapNodeNghbrs;
		isTrainingData = _otr.isTrainingData;
		testTrainDataIDX = _otr.testTrainDataIDX;
		allMapNodesDists = _otr.allMapNodesDists;
		ftrWtSegData = _otr.ftrWtSegData;	
		class_SegData  = _otr.class_SegData;		
		categorys_SegData  = _otr.categorys_SegData;		

		stFlags = _otr.stFlags;		
	}//copy ctor
	
	private void _initSegStructs() {
		ftrWtSegData = new TreeMap<Integer, SOM_MappedSegment>();		//keyed by non-zero ftr index	                   
		class_SegData	= new TreeMap<Integer, SOM_MappedSegment>();	//segment membership manager class mapping         
		categorys_SegData = new TreeMap<Integer, SOM_MappedSegment>();	//segment membership manager category mapping      
	}
	
	//mapped segments for this example
	public synchronized void addFtrSegment(int idx, SOM_MappedSegment seg) {		ftrWtSegData.put(idx, seg);}
	public synchronized void addClassSegment(int idx, SOM_MappedSegment seg) {		class_SegData.put(idx, seg);}
	public synchronized void addCategorySegment(int idx, SOM_MappedSegment seg) {	categorys_SegData.put(idx, seg);}
	
	public synchronized SOM_MappedSegment getFtrSegment(int idx) {		return ftrWtSegData.get(idx);}
	public synchronized SOM_MappedSegment getClassSegment(int idx) {		return class_SegData.get(idx);}
	public synchronized SOM_MappedSegment getCategorySegment(int idx) {		return categorys_SegData.get(idx);}

	/**
	 * set this example's segment membership and probabilities from the mapped bmu - class/category label-driven examples won't use this function
	 */
	public abstract void setSegmentsAndProbsFromBMU();
	/**
	 * get CSV string representation of segment membership data
	 */
	public abstract String getCSVSegmentMembershipData();

	//clear instead of reinstance - if ftr maps are cleared then compFtrMaps should be cleared as well
	protected synchronized void clearAllFtrMaps() {for (int i=0;i<ftrMaps.length;++i) {	clearFtrMap(i);}}	
	protected synchronized void clearFtrMap(int idx) {ftrMaps[idx].clear(); clearCompFtrMap(idx);}
	//clear instead of reinstance
	protected synchronized void clearAllCompFtrMaps() {for (int i=0;i<compFtrMaps.length;++i) {	clearCompFtrMap(i);}}
	protected synchronized void clearCompFtrMap(int idx) {compFtrMaps[idx].clear();}

	//build feature vector
	protected abstract void buildFeaturesMap();	
	//standardize this feature vector stdFtrData
	protected abstract void buildStdFtrsMap();
	//required info for this example to build feature data - use this so we don't have to reload data ever time
	public abstract String getPreProcDescrForCSV();	
	//column names of rawDescrForCSV data
	public abstract String getRawDescColNamesForCSV();
	//finalization after being loaded from baseRawData or from csv record but before ftrs are calculated
	public abstract void finalizeBuildBeforeFtrCalc();
	
	public boolean isBadExample() {return getFlag(isBadTrainExIDX);}
	public void setIsBadExample(boolean val) { setFlag(isBadTrainExIDX,val);}
	
	//debugging tool to find issues behind occasional BMU seg faults
//	protected boolean checkForErrors(SOMMapNode _n, float[] dataVar){
//		if(mapMgr == null){					mapMgr.msgObj.dispMessage("SOMMapNodeExample","checkForErrors","FATAL ERROR : SOMMapData object is null!");		return true;}//if mapdata is null then stop - should have come up before here anyway
//		if(_n==null){							mapMgr.msgObj.dispMessage("SOMMapNodeExample","checkForErrors","_n is null!");		 return true;} 
//		if(_n.mapLoc == null){					mapMgr.msgObj.dispMessage("SOMMapNodeExample","checkForErrors","_n has no maploc!");	return true;}
//		if(dataVar == null){					mapMgr.msgObj.dispMessage("SOMMapNodeExample","checkForErrors","map variance not calculated : datavar is null!");	return true;	}
//		return false;
//	}//checkForErrors
	
	//add passed map node, with passed feature distance, to neighborhood nodes
	//using a map of arrays so that we can precalc distances 1 time
	protected final void addMapNodeToNeighbrhdMap(SOMMapNode _n, double _dist) {
		ArrayList<SOMMapNode> tmpMap = mapNodeNghbrs.get(_dist);
		if (null==tmpMap) {tmpMap = new ArrayList<SOMMapNode>();}
		tmpMap.add(_n);
		mapNodeNghbrs.put(_dist, tmpMap);		
	}//addMapUnitToNeighbrhdMap
	
	//once BMU and distToBMU is set, init map and add node to neighborhood map keyed by dist
	protected final void addBMUToNeighbrhdMap() {
		mapNodeNghbrs = new TreeMap<Double, ArrayList<SOMMapNode>>();
		ArrayList<SOMMapNode> tmpMap = new ArrayList<SOMMapNode>();
		tmpMap.add(getBmu());
		mapNodeLoc.set(getBmu().mapLoc);
		mapNodeNghbrs.put(get_sqDistToBMU(), tmpMap);		//to hold 9 neighbor nodes and their ftr distance		
	}//addBMUToNeighbrhdMap
	
	//assign passed map node to be bmu
	protected final void _setBMUAddToNeighborhood(SOMMapNode _n, double _dist) {
		//if (checkForErrors(_n, mapMgr.map_ftrsVar)) {return;}//if true then this is catastrophic error and should interrupt flow here
		setBmu(_n);	
		set_sqDistToBMU(_dist);
		//this is updated to exact location after neighbor hood map is built
		this.mapLoc.set(getBmu().mapLoc);		
		addBMUToNeighbrhdMap();
	}//_setBMUAddToNeighborhood
	
	//this example has no values in any feature and so has no bmu
	protected final void _setNullBMU() {
		setBmu(null);
		set_sqDistToBMU(Double.MAX_VALUE);
		mapLoc.set(0,0,0);
		mapNodeNghbrs = new TreeMap<Double, ArrayList<SOMMapNode>>();
	}
	
	public boolean isBmuNull() {return (null==bmu);}
	
	//get class probability from bmu for passed class
	//treat this example's probability for a particular class as the probability of its BMU for that class (# examples of that class divided by total # of class seen at that node)
	public float getBMUProbForClass(Integer cls) {
		if(null==bmu) {return 0.0f;}
		return bmu.getClassProb(cls);
	}
	
	public float getBMUProbForCategory(Integer category) {
		if(null==bmu) {return 0.0f;}
		return bmu.getCategoryProb(category);
	}
	//assumes bmu exists and is not null
	public Set<Integer> getBMUClassSegIDs(){			return bmu.getClassSegIDs();}
	public Set<Integer> getBMUCategorySegIDs(){			return bmu.getCategorySegIDs();}
	public SOM_MappedSegment getBMUClassSegment(int cls) {	return bmu.getClassSegment(cls);}
	public SOM_MappedSegment getBMUCategorySegment(int cat) {	return bmu.getCategorySegment(cat);}
	
	public Tuple<Integer,Integer> getBMUMapNodeCoord(){	return bmu.mapNodeCoord;}
	
	////////////////////////////////////
	// these always use ftr weight vectors because map nodes always only use ftrs
	
	//this adds the passed node as this _training_ example's best matching unit on the map - this is used for -training- examples having
	//been set as bmus by the SOM executable, hence why it uses the full distance and does not exclude zero features in this example
	//also does not use comparison vector but actual ftrMaps/training vector for the above reason
	//this also adds this data point to the map's node with a key of the distance
	//dataVar is variance of feature weights of map nodes.  this is for chi-squared distance measurements
	public final void setTrainingExBMU(SOMMapNode _n, int _ftrType){
		_setBMUAddToNeighborhood(_n,getSqDistFromFtrType(_n.ftrMaps[_ftrType],  ftrMaps[_ftrType]));
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes( _ftrType, this::getSqDistFromFtrType);
	}//setBMU
	
	//this adds the passed node as this example's best matching unit on the map - this is used for -training- examples having
	//been set as bmus by the SOM executable, hence why it uses the full distance and does not exclude zero features in this example
	//also does not use comparison vector but actual ftrMaps/training vector for the above reason
	//this also adds this data point to the map's node with a key of the distance
	//dataVar is variance of feature weights of map nodes.  this is for chi-squared distance measurements
	public final void setTrainingExBMU_ChiSq(SOMMapNode _n, int _ftrType){
		_setBMUAddToNeighborhood(_n,getSqDistFromFtrType_ChiSq(_n.ftrMaps[_ftrType],  ftrMaps[_ftrType]));
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes( _ftrType, this::getSqDistFromFtrType_ChiSq);
	}//setBMU
	
	//use 9 map node neighborhood around this node, accounting for torroidal map, to find exact location on map
	//for this node (put in mapLoc) by using weighted average of mapNodeLocs of neighbor nodes,
	//where the weight is the inverse feature distance 
	// mapNodeNghbrs (9 node neighborood) must be set before this is called, and has bmu set as closest, with key being distance
	//distsToNodes is distance of this node to all map nodes in neighborhood
	protected final void buildNghbrhdMapNodes(int _ftrType, BiFunction<TreeMap<Integer, Float>, TreeMap<Integer, Float>, Double> _distFunc){
		int mapColsSize = mapMgr.getMapNodeCols(), mapRowSize = mapMgr.getMapNodeRows();
		int mapCtrX = bmu.mapNodeCoord.x, mapCtrY = bmu.mapNodeCoord.y;
		Integer xTup, yTup;
		TreeMap<Tuple<Integer,Integer>, SOMMapNode> mapNodes = mapMgr.getMapNodes();
		//measuring distance to map node neighbors around bmu
		for (int x=-1; x<2;++x) {//should be 3 cols
			xTup = (mapCtrX + x + mapColsSize) % mapColsSize;
			for (int y=-1; y<2;++y) {//3 rows
				if((y==0) && (x==0)){continue;}//ignore "center" node - this is bmu
				yTup = (mapCtrY + y + mapRowSize) % mapRowSize;		
				Tuple<Integer,Integer> key = new Tuple<Integer, Integer>(xTup, yTup);
				SOMMapNode mapNode = mapNodes.get(key);
				double dist = _distFunc.apply(mapNode.ftrMaps[_ftrType], ftrMaps[_ftrType]);
				addMapNodeToNeighbrhdMap(mapNode, dist);
			}//for each row/y
		}//for each column/x	
		setExactMapLoc();
	}//addAllMapNodeNeighbors
	
	//calculate appropriate modifier based on where neighbor node is related to where bmu node is - need to account for wrapping
	private final float calcWrapMod(Integer bmuCoord, Integer neighborCoord, float mapDim) {
		float mod = 0.0f;
		if (neighborCoord > bmuCoord+1) {		mod = mapDim; }//this means that the example is actually lower than bmu, but due to wrap it is on the other side of the map, so add
		else if (neighborCoord < bmuCoord-1) {	mod = -mapDim;}//this means that the example is actually higher coord than bmu, but wrapped around to the other side, so subtract dim 
		return mod;
	}//calcWrapMod
	
	//return location of passed map node, with value added or subtracted based on whether it wraps around map
	private final myPointf findNodeLocWrap(SOMMapNode mapNode, Integer bmuX, Integer bmuY, float mapW, float mapH) {
		Integer mapNode_X = mapNode.mapNodeCoord.x, mapNode_Y = mapNode.mapNodeCoord.y;
		myPointf loc = new myPointf(mapNode.mapLoc);
		float locXMod = calcWrapMod(bmuX, mapNode_X, mapW);		//subtract or add map width or height depending on whether neighborhood wraps around torroidal map
		float locYMod = calcWrapMod(bmuY, mapNode_Y, mapH);		
		loc._sub(locXMod,locYMod, 0.0f);
		return loc;		
	}//findNodeLocWrap
	
	//determine map location based on neighborhood nodes, accounting for torroidal wrap
	private final void setExactMapLoc() {
		myPointf totalLoc = new myPointf(), locToAdd;
		float ttlInvDist = 0.0f,invDistP1;
		for (double _dist : mapNodeNghbrs.keySet()) {
			invDistP1 = (float) (1.0f/(1.0f+_dist));					//handles 0 dist - max will be 0, min will be some fraction
			ArrayList<SOMMapNode> tmpMap = mapNodeNghbrs.get(_dist);
			for (SOMMapNode ex : tmpMap) {			ttlInvDist +=invDistP1;		}			
		}
		float mapW = mapMgr.getMapWidth(), mapH = mapMgr.getMapHeight();
		Integer bmuX = getBmu().mapNodeCoord.x,  bmuY = getBmu().mapNodeCoord.y;
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
	public final void buildFeatureVector() {
		buildAllNonZeroFtrIDXs();
		buildFeaturesMap();
		setFlag(ftrsBuiltIDX,true);		
		buildNormFtrData();//once ftr map is built can normalize easily
		_buildFeatureVectorEnd_Priv();
	}//buildFeatureVector
	
	public abstract void postFtrVecBuild();
	
	//these are called before and after an individual example's features are built
	protected abstract void buildAllNonZeroFtrIDXs();
	protected abstract void _buildFeatureVectorEnd_Priv();
	
	public final void setIsTrainingDataIDX(boolean val, int idx) {
		isTrainingData=val; 
		testTrainDataIDX=idx;
		setIsTrainingDataIDX_Priv();
	}//setIsTrainingDataIDX
	
	//whether this record was used to train the current map or not
	public final boolean getIsTrainingData() {return isTrainingData;}
	public final int getTestTrainIDX() {return testTrainDataIDX;}
	
	protected abstract void setIsTrainingDataIDX_Priv();
	
	//build structures that require that the feature vector be built before hand
	public final void buildAfterAllFtrVecsBuiltStructs() {
		buildStdFtrsMap();
		//default comparison vector setup - can be overridden
		buildCompFtrVector(0.0f);
	}//buildPostFeatureVectorStructs

	//build normalized vector of data - only after features have been set
	protected final void buildNormFtrData() {
		if(!getFlag(ftrsBuiltIDX)) {mapMgr.getMsgObj().dispMessage("SOMExample","buildNormFtrData","OID : " + OID + " : Features not built, cannot normalize feature data since marked as not built!", MsgCodes.warning2);return;}
		clearFtrMap(normFtrMapTypeKey);//ftrMaps[normFtrMapTypeKey].clear();
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
	protected final void calcStdFtrVector(TreeMap<Integer, Float> from_ftrs, TreeMap<Integer, Float> to_sclFtrs, Float[] mins, Float[] diffs) {
		to_sclFtrs.clear();
		for (Integer destIDX : from_ftrs.keySet()) {
			Float lb = mins[destIDX], 	diff = diffs[destIDX];
			Float val = 0.0f;
			if (diff==0) {//same min and max
				if (lb > 0) { val = 1.0f;}//only a single value same min and max-> set feature value to 1.0
				else {		  val = 0.0f;}
			} else {				val = (from_ftrs.get(destIDX)-lb)/diff;			}	
			to_sclFtrs.put(destIDX,val);
			
		}//for each non-zero ftr
	}//standardizeFeatureVector		getSqDistFromFtrType
		
	//initialize structures used to aggregate and report the ranking of particular ftrs for this example
	private final void initPerFtrObjs() {
		mapOfWtsToFtrIDXs = new TreeMap[ftrMapTypeKeysAra.length];
		mapOfFtrIDXVsWtRank = new TreeMap[ftrMapTypeKeysAra.length];
		for(Integer ftrType : ftrMapTypeKeysAra) {//type of features
			mapOfWtsToFtrIDXs[ftrType] = new TreeMap<Float, ArrayList<Integer>>(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}});//descending key order
			mapOfFtrIDXVsWtRank[ftrType] = new TreeMap<Integer,Integer>();
		}
	}//initPerFtrObjs()
	
	//to execute per-example ftr-based reporting, first call buildFtrRptStructs for all examples, then iterate through getters for ftr type == mapToGet, then call clearFtrRptStructs to release memory
	//called to report on this example's feature weight rankings 
	public final void buildFtrRprtStructs() {
		if(getFlag(ftrWtRptBuiltIDX)) {return;}
		initPerFtrObjs();
		//for every feature type build 
		for(Integer mapToGet : ftrMapTypeKeysAra) {//type of features
			int rank = 0;
			TreeMap<Integer, Float> ftrMap = ftrMaps[mapToGet];
			//go through features ara, for each ftr idx find rank 
			TreeMap<Float, ArrayList<Integer>> mapOfFtrsToIdxs = mapOfWtsToFtrIDXs[mapToGet];
			//shouldn't be null - means using inappropriate key
			if(mapOfFtrsToIdxs == null) {mapMgr.getMsgObj().dispMessage("SOMExample" + OID,"buildFtrRprtStructs","Using inappropriate key to access mapOfWtsToFtrIDXs : " + mapToGet + " No submap exists with this key.", MsgCodes.warning2); return;}	
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
				ArrayList<Integer> ftrsAtRank = mapOfFtrsToIdxs.get(wtVal);
				for (Integer ftr : ftrsAtRank) {	mapOfRanks.put(ftr, rank);}
				++rank;
			}
			mapOfFtrIDXVsWtRank[mapToGet] = mapOfRanks;//probably not necessary since already initialized (will never be empty or deleted)					
		}//for ftrType		
		setFlag(ftrWtRptBuiltIDX, true);
	}//buildFtrReports
	
	//return mapping of ftr IDXs to rank for this example
	public final TreeMap<Integer,Integer> getMapOfFtrIDXBsWtRank(int mapToGet){
		if(!getFlag(ftrWtRptBuiltIDX)) {
			mapMgr.getMsgObj().dispMessage("SOMExample" + OID,"getMapOfFtrIDXBsWtRank","Feature-based report structures not yet built. Aborting.", MsgCodes.warning2);
			return null;
		}
		return mapOfFtrIDXVsWtRank[mapToGet];
	}//getMapOfFtrIDXBsWtRank
	
	public final TreeMap<Float, ArrayList<Integer>> getMapOfWtsToFtrIDXs(int mapToGet){
		if(!getFlag(ftrWtRptBuiltIDX)) {
			mapMgr.getMsgObj().dispMessage("SOMExample" + OID,"getMapOfWtsToFtrIDXs","Feature-based report structures not yet built. Aborting.", MsgCodes.warning2);
			return null;
		}
		return mapOfWtsToFtrIDXs[mapToGet];
	}//getMapOfWtsToFtrIDXs
		
	//clears out structures used for reports, to minimize memory footprint
	public final void clearFtrRprtStructs() {		
		mapOfWtsToFtrIDXs = null;
		mapOfFtrIDXVsWtRank = null;		
		setFlag(ftrWtRptBuiltIDX, false);		
	}//clearFtrReports
	

	////////////////////////////////
	//distance measures between nodes
	/**
	 * These functions will find the specified distance between the passed node and this node, using the specified feature type
	 * chiSq is sqDist with each component divided by ftr variance
	 * @param fromNode
	 * @param _ftrType
	 * @return
	 */
	//return the chi-sq distance from this node to passed node
	//public final double getSqDistFromFtrType_ChiSq(SOMExample fromNode, int _ftrType){		
	public final double getSqDistFromFtrType_ChiSq(TreeMap<Integer, Float> fromftrMap, TreeMap<Integer, Float> thisFtrMap){
		double res = 0.0f;
		float[] mapFtrVar = mapMgr.getMap_ftrsVar();//divide by variance for chi-sq dist
//		Set<Integer> allIdxs = new HashSet<Integer>(fromftrMap.keySet());
//		allIdxs.addAll(thisFtrMap.keySet());
		Float frmVal,toVal, diff;
		for (Integer key : fromftrMap.keySet()) {//either map will have this key
			frmVal = fromftrMap.get(key);
			toVal = thisFtrMap.get(key);if(toVal == null) {toVal = 0.0f;}
			diff = toVal - frmVal;
			res += (diff * diff)/mapFtrVar[key];
		}
		for (Integer key : thisFtrMap.keySet()) {//either map will have this key
			frmVal = fromftrMap.get(key);if(frmVal == null) {frmVal = 0.0f;}
			toVal = thisFtrMap.get(key);
			diff = toVal - frmVal;
			res += (diff * diff)/mapFtrVar[key];
		}
		return res;
	}//getDistFromFtrType

	//return the chi-sq distance from this node to passed node, only measuring non-zero features in this node
	//public final double getSqDistFromFtrType_ChiSq_Exclude(SOMExample fromNode, int _ftrType){
	public final double getSqDistFromFtrType_ChiSq_Exclude(TreeMap<Integer, Float> fromftrMap, TreeMap<Integer, Float> thisFtrMap){
		double res = 0.0f;
		float[] mapFtrVar = mapMgr.getMap_ftrsVar();//divide by variance for chi-sq dist
		//Set<Integer> allIdxs = new HashSet<Integer>(thisFtrMap.keySet());
		Float frmVal,toVal, diff;
		for (Integer key : thisFtrMap.keySet()) {//either map will have this key
			frmVal = fromftrMap.get(key);	if(frmVal == null) {frmVal = 0.0f;}
			toVal = thisFtrMap.get(key);
			diff = toVal - frmVal;
			res += (diff * diff)/mapFtrVar[key];
		}
		return res;
	}//getDistFromFtrType
	
	//return the sq sdistance between this map's ftrs and the passed ftrMaps[ftrMapTypeKey]
	//public final double getSqDistFromFtrType(SOMExample fromNode, int _ftrType){
	public final double getSqDistFromFtrType(TreeMap<Integer, Float> fromftrMap, TreeMap<Integer, Float> thisFtrMap){
		double res = 0.0;
//		Set<Integer> allIdxs = new HashSet<Integer>(fromftrMap.keySet());
//		allIdxs.addAll(thisFtrMap.keySet());
		Float frmVal,toVal, diff;
		for (Integer key : fromftrMap.keySet()) {//either map will have this key
			frmVal = fromftrMap.get(key);
			toVal = thisFtrMap.get(key);if(toVal == null) {toVal = 0.0f;}
			diff = toVal - frmVal;
			res += (diff * diff);
		}
		for (Integer key : thisFtrMap.keySet()) {//either map will have this key
			frmVal = fromftrMap.get(key);if(frmVal == null) {frmVal = 0.0f;}
			toVal = thisFtrMap.get(key);
			diff = toVal - frmVal;
			res += (diff * diff);
		}
		return res;
	}//getDistFromFtrType	
	//return the distance between this map's ftrs and the passed ftrMaps[ftrMapTypeKey], only measuring -this- nodes non-zero features.
	//public double getSqDistFromFtrType_Exclude(SOMExample fromNode, int _ftrType){
	public final double getSqDistFromFtrType_Exclude(TreeMap<Integer, Float> fromftrMap, TreeMap<Integer, Float> thisFtrMap){
		double res = 0.0;
		//Set<Integer> allIdxs = new HashSet<Integer>(thisFtrMap.keySet());//this map's features - only ones that matter
		Float frmVal,toVal, diff;
		for (Integer key : thisFtrMap.keySet()) {//either map will have this key
			frmVal = fromftrMap.get(key);if(frmVal == null) {frmVal = 0.0f;}
			toVal = thisFtrMap.get(key);
			diff = toVal - frmVal;
			res += (diff * diff);
		}
		return res;
	}//getDistFromFtrType	

	////////////////////////////////
	//end distance measures between nodes
	
	/////////////////////////////////
	// bmu handling/mapping
	
	//override for multi-example training data
	public void mapToBMU(int dataTypeVal) {
		if(null==bmu) {return;}
		bmu.addExToBMUs(this,dataTypeVal);	
		mapMgr.addExToNodesWithExs(bmu, type);		
	}
	//override for multi-example training data
	public void mapTrainingToBMU(int trainingDataTypeVal) {
		if(null==bmu) {return;}
		bmu.addTrainingExToBMUs(this,trainingDataTypeVal);	
		mapMgr.addExToNodesWithExs(bmu, type);		
	}
	
	//this will return the training label(s) of this example
	//the training label corresponds to a tag or a class referring to the data that can be assigned to a map node - a vote about the bmu from this example
	//if not training data then no label will exist;  might not exist if it is training data either, if fully unsupervised
	public abstract TreeMap<Integer,Integer> getTrainingLabels();	
	
	/**
	 *  this will build the comparison feature vector array that is used as the comparison vector 
	 *  in distance measurements - for most cases this will just be a copy of the ftr vector array
	 *  but in some instances, there might be an alternate vector to be used to handle when, for 
	 *  example, an example has ftrs that do not appear on the map
	 * @param _ratio
	 */
	public abstract void buildCompFtrVector(float _ratio);
	
	//this value corresponds to training data type - we want to check training data type counts at each node
	private final int _trainDataTypeIDX = ExDataType.Training.getVal();
	//given a sqdistance-keyed map of lists of mapnodes, this will find the best matching unit (min distance), with favor given to equi-distant units that have more examples
	private final void _setBMUFromMapNodeDistMap(TreeMap<Double, ArrayList<SOMMapNode>> mapNodes) {
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
				int numExamples = node.getNumExamples(_trainDataTypeIDX);//want # of training examples - use training examples as map node relevance weight
				if (numExamples >= maxNumExamples) {//need to manage if all map nodes that are "best" have no direct training examples (might happen on large maps), hence >= and not >
					maxNumExamples = numExamples;
					bestUnit = node;
				}		
			}
		} else {	bestUnit = bmuList.get(0);	}	
		_setBMUAddToNeighborhood(bestUnit, bmuDist);
	}//_setBMUFromMapNodeDistMap		
	
	/**
	 * references current map of nodes, finds best matching unit and returns map of all map node tuple addresses and their ftr distances from this node     
	 * also build neighborhood nodes                                                                                                                        
	 * two methods to minimize if calls for chisq dist vs regular euclidean dist                                                                            
	 * Passing map of nodes keyed by non-zero ftr idx. 
	 * 
	 * @param _MapNodesByFtr : map of all mapnodes keyed by feature idx with non-zero features
	 * @param _ftrtype : kind of features (unmod, normed, stdized) to be used for comparison/distance calc
	 * @return
	 */
//	public final TreeMap<Double, ArrayList<SOMMapNode>> findBMUFromFtrNodes_ftrMaps(TreeMap<Integer, HashSet<SOMMapNode>> _MapNodesByFtr,  BiFunction<TreeMap<Integer, Float>, TreeMap<Integer, Float>, Double> _distFunc, int _ftrType) {
//		HashSet<SOMMapNode> _RelevantMapNodes = new HashSet<SOMMapNode>();
//		buildRelevantMapNodes(_RelevantMapNodes, ftrMaps[_ftrType].keySet(), _MapNodesByFtr);
//		TreeMap<Double, ArrayList<SOMMapNode>> mapNodesByDist = new TreeMap<Double, ArrayList<SOMMapNode>>();	
//		for (SOMMapNode mapNode : _RelevantMapNodes) {
//			double sqDistToNode = _distFunc.apply(mapNode.ftrMaps[_ftrType],  ftrMaps[_ftrType]);
//			ArrayList<SOMMapNode> tmpAra = mapNodesByDist.get(sqDistToNode);
//			if(tmpAra == null) {tmpAra = new ArrayList<SOMMapNode>();}
//			tmpAra.add(mapNode);
//			mapNodesByDist.put(sqDistToNode, tmpAra);		
//		}	
//		//handle if this node has no ftrs that map directly to map node ftrs - perhaps similarity groupings exist to build mappings from
//		//buildMapNodeDistsFromGroupings(mapNodesByDist, _MapNodesByFtr);
//		if(mapNodesByDist.size() == 0) {_setNullBMU(); return null;}
//		_setBMUFromMapNodeDistMap(mapNodesByDist);		
//		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
//		buildNghbrhdMapNodes( _ftrType, _distFunc);	
//		return mapNodesByDist;
//	}//findBMUFromNodes 
	
	/**
	 * relevant map nodes are nodes with non-zero values in 1 or more of the ftrs this example also has non-zero values in
	 * @param _RelevantMapNodes : pre-allocated empty set of map nodes that are relevant by direct feature membership
	 * @param _RelevantMapNodesByFtrGroup : pre-allocated empty set of map nodes that are relevant by having weight in features that belong to some app-specific grouping of features that this node also has weight in
	 * @param _MapNodesByFtr : map of all nodes keyed by feature index containing non-zero index
	 * @return set of nodes that have non-zero values in features this node also has non-zero values in
	 */
	private final void buildRelevantMapNodes(HashSet<SOMMapNode> _RelevantMapNodes, Set<Integer> nonZeroFtrIDXs, TreeMap<Integer, HashSet<SOMMapNode>> _MapNodesByFtr){
		//query _MapNodesByFtr by all non-zero features in this map
		HashSet<SOMMapNode> nodesWithNonZeroFtrs;
		for(Integer idx : nonZeroFtrIDXs) {
			nodesWithNonZeroFtrs = _MapNodesByFtr.get(idx);
			if(null==nodesWithNonZeroFtrs) {continue;}
			for(SOMMapNode node : nodesWithNonZeroFtrs) {		_RelevantMapNodes.add(node);}
		}		
	}//buildRelevantMapNodes
	
	/**
	 * returns a map keyed by distance with values being a list of nodes at key distance
	 * @param _MapNodesByFtr : map of all mapnodes keyed by feature idx with non-zero features
	 * @param _distFunc : function to use to calculate distance
	 * @param _ftrType : kind of features (unmod, normed, stdized) to be used for comparison/distance calc
	 * @return
	 */	
	public final TreeMap<Double, ArrayList<SOMMapNode>> findMapNodesByDist(TreeMap<Integer, HashSet<SOMMapNode>> _MapNodesByFtr,  BiFunction<TreeMap<Integer, Float>, TreeMap<Integer, Float>, Double> _distFunc, int _ftrType){
		HashSet<SOMMapNode> _RelevantMapNodes = new HashSet<SOMMapNode>();
		buildRelevantMapNodes(_RelevantMapNodes, compFtrMaps[_ftrType].keySet(), _MapNodesByFtr);
		TreeMap<Double, ArrayList<SOMMapNode>> mapNodesByDist = new TreeMap<Double, ArrayList<SOMMapNode>>();	
		for (SOMMapNode mapNode : _RelevantMapNodes) {
			double sqDistToNode = _distFunc.apply(mapNode.compFtrMaps[_ftrType], compFtrMaps[_ftrType]);
			ArrayList<SOMMapNode> tmpAra = mapNodesByDist.get(sqDistToNode);
			if(tmpAra == null) {tmpAra = new ArrayList<SOMMapNode>();}
			tmpAra.add(mapNode);
			mapNodesByDist.put(sqDistToNode, tmpAra);		
		}	
		return mapNodesByDist;
	}//findMapNodesByDist
	
	/**
	 * returns the entry in a distance-keyed map of lists of nodes for the list of 1 or more nodes that have the least distance
	 * Should not be called in a multi-threaded context
	 * @param _MapNodesByFtr : map of all mapnodes keyed by feature idx with non-zero features
	 * @param _distFunc : function to use to calculate distance
	 * @param _ftrType : kind of features (unmod, normed, stdized) to be used for comparison/distance calc
	 * @return
	 */
	public final Entry<Double, ArrayList<SOMMapNode>> findClosestMapNodes(TreeMap<Integer, HashSet<SOMMapNode>> _MapNodesByFtr,  BiFunction<TreeMap<Integer, Float>, TreeMap<Integer, Float>, Double> _distFunc, int _ftrType){
		TreeMap<Double, ArrayList<SOMMapNode>> mapNodesByDist = findMapNodesByDist(_MapNodesByFtr,_distFunc, _ftrType );
		return mapNodesByDist.firstEntry();	
	}
	
	/**
	 * references current map of nodes, finds best matching unit and returns map of all map node tuple addresses and their ftr distances from this node     
	 * also build neighborhood nodes                                                                                                                        
	 * two methods to minimize if calls for chisq dist vs regular euclidean dist                                                                            
	 * Passing map of nodes keyed by non-zero ftr idx. 
	 * 
	 * @param _MapNodesByFtr : map of all mapnodes keyed by feature idx with non-zero features
	 * @param _distFunc : function to use to calculate distance
	 * @param _ftrType : kind of features (unmod, normed, stdized) to be used for comparison/distance calc
	 * @return
	 */
	public synchronized final TreeMap<Double, ArrayList<SOMMapNode>> findBMUFromFtrNodes(TreeMap<Integer, HashSet<SOMMapNode>> _MapNodesByFtr,  BiFunction<TreeMap<Integer, Float>, TreeMap<Integer, Float>, Double> _distFunc, int _ftrType) {
		TreeMap<Double, ArrayList<SOMMapNode>> mapNodesByDist = findMapNodesByDist(_MapNodesByFtr,_distFunc, _ftrType );
		//handle if this node has no ftrs that map directly to map node ftrs - perhaps similarity groupings exist to build mappings from
		//buildMapNodeDistsFromGroupings(mapNodesByDist, _MapNodesByFtr);
		if(mapNodesByDist.size() == 0) {_setNullBMU(); return null;}
		_setBMUFromMapNodeDistMap(mapNodesByDist);		
		//find ftr distance to all 8 surrounding nodes and add them to mapNodeNeighbors
		buildNghbrhdMapNodes( _ftrType, _distFunc);	
		return mapNodesByDist;
	}//findBMUFromNodes 

	private final String _toCSVString(TreeMap<Integer, Float> ftrs) {
		String res = ""+OID+",";
		int numTrnFtrs = mapMgr.getNumTrainFtrs();
		for(int i=0;i<numTrnFtrs;++i){
			Float ftr = ftrs.get(i);			
			res += String.format("%1.7g", (ftr==null ? 0 : ftr)) + ",";
		}
		return res;}
	//return csv string of this object's features, depending on which type is selected - check to make sure 2ndary features exist before attempting to build data strings
	////useUnmoddedDat = 0, useScaledDat = 1, useNormedDat
	public final String toCSVString(int _type) {
		switch(_type){
			case SOM_MapManager.useUnmoddedDat : {return _toCSVString(ftrMaps[ftrMapTypeKey]); }
			case SOM_MapManager.useNormedDat  : {return _toCSVString(getFlag(normFtrsBuiltIDX) ? ftrMaps[normFtrMapTypeKey] : ftrMaps[ftrMapTypeKey]);}
			case SOM_MapManager.useScaledDat  : {return _toCSVString(getFlag(stdFtrsBuiltIDX) ? ftrMaps[stdFtrMapTypeKey] : ftrMaps[ftrMapTypeKey]); }
			default : {return _toCSVString(ftrMaps[ftrMapTypeKey]); }
		}
	}//toCSVString

	private final String _toLRNString(TreeMap<Integer, Float> ftrs, String sep) {
		String res = ""+OID+sep;
		int numTrnFtrs = mapMgr.getNumTrainFtrs();
		for(int i=0;i<numTrnFtrs;++i){
			Float ftr = ftrs.get(i);			
			res += String.format("%1.7g", (ftr==null ? 0 : ftr)) + sep;
		}
		return res;}	
	//return LRN-format (dense) string of this object's features, depending on which type is selected - check to make sure 2ndary features exist before attempting to build data strings
	public final String toLRNString(int _type, String sep) {
		switch(_type){
			case SOM_MapManager.useUnmoddedDat : {return _toLRNString(ftrMaps[ftrMapTypeKey], sep); }
			case SOM_MapManager.useNormedDat   : {return _toLRNString(getFlag(normFtrsBuiltIDX) ? ftrMaps[normFtrMapTypeKey] : ftrMaps[ftrMapTypeKey], sep);}
			case SOM_MapManager.useScaledDat   : {return _toLRNString(getFlag(stdFtrsBuiltIDX) ? ftrMaps[stdFtrMapTypeKey] : ftrMaps[ftrMapTypeKey], sep); }
			default : {return _toLRNString(ftrMaps[ftrMapTypeKey], sep); }
		}		
	}//toLRNString
	
	private final String _toSVMString(TreeMap<Integer, Float> ftrs) {
		String res = "";
		for (Integer ftrIdx : allNonZeroFtrIDXs) {res += "" + ftrIdx + ":" + String.format("%1.7g", ftrs.get(ftrIdx)) + " ";}
		return res;}//_toSVMString
	
	//return SVM-format (sparse) string of this object's features, depending on which type is selected - check to make sure 2ndary features exist before attempting to build data strings
	public final String toSVMString(int _type) {
		switch(_type){
			case SOM_MapManager.useUnmoddedDat : {return _toSVMString(ftrMaps[ftrMapTypeKey]); }
			case SOM_MapManager.useNormedDat   : {return _toSVMString(getFlag(normFtrsBuiltIDX) ? ftrMaps[normFtrMapTypeKey] : ftrMaps[ftrMapTypeKey]);}
			case SOM_MapManager.useScaledDat   : {return _toSVMString(getFlag(stdFtrsBuiltIDX) ? ftrMaps[stdFtrMapTypeKey] : ftrMaps[ftrMapTypeKey]); }
			default : {return _toSVMString(ftrMaps[ftrMapTypeKey]); }
		}		
	}//toLRNString
	//build a feature vector from the map of ftr values
	private final float[] _getFtrsFromMap(TreeMap<Integer, Float> ftrMap) {
		float[] ftrs = new float[mapMgr.getNumTrainFtrs()];
		for (Integer ftrIdx : ftrMap.keySet()) {ftrs[ftrIdx]=ftrMap.get(ftrIdx);		}
		return ftrs;
	}
	
	//build feature vector on demand
	public final float[] getFtrs() {return _getFtrsFromMap(ftrMaps[ftrMapTypeKey]);}
	//build stdfeature vector on demand
	public final float[] getStdFtrs() {return _getFtrsFromMap(ftrMaps[stdFtrMapTypeKey]);}
	//build normfeature vector on demand
	public final float[] getNormFtrs() {return _getFtrsFromMap(ftrMaps[normFtrMapTypeKey]);}	
	
	public final TreeMap<Integer, Float> getCurrentFtrMap(int _type){
		switch(_type){
			case SOM_MapManager.useUnmoddedDat : {return ftrMaps[ftrMapTypeKey]; }
			case SOM_MapManager.useNormedDat   : {return (getFlag(normFtrsBuiltIDX) ? ftrMaps[normFtrMapTypeKey] : ftrMaps[ftrMapTypeKey]);}
			case SOM_MapManager.useScaledDat   : {return (getFlag(stdFtrsBuiltIDX) ? ftrMaps[stdFtrMapTypeKey] : ftrMaps[ftrMapTypeKey]); }
			default : {return ftrMaps[ftrMapTypeKey]; }
		}		
	}

	protected final TreeMap<Integer, Float> buildMapFromAra(float[] ara, float thresh) {
		TreeMap<Integer, Float> ftrs = new TreeMap<Integer, Float>();
		for (int i=0;i<ara.length;++i) {if(ara[i]> thresh) {ftrs.put(i, ara[i]);}}	
		return ftrs;
	}
	
	private final String dispFtrs(TreeMap<Integer, Float> ftrs) {
		String res = "";
		if((ftrs==null) || (ftrs.size() == 0)){res+=" None\n";} 
		else {
			int numFtrs = mapMgr.getNumTrainFtrs();
			res +="\n\t";for(int i=0;i<numFtrs;++i){
			Float ftr = ftrs.get(i);
			res += String.format("%1.4g",  (ftr==null ? 0 : ftr)) + " | "; if((numFtrs > 40) && ((i+1)%30 == 0)){res +="\n\t";}}}
		return res;
	}
	
	protected final String dispFtrMapVals(TreeMap<Integer, Float> ftrs) {
		String res = "";
		if((ftrs==null) || (ftrs.size() == 0)){res+=" None\n";} 
		else {			for(Integer i : ftrs.keySet()){				res += dispFtrVal(ftrs, i);}}		
		return res;
	}
	
	//return a string value corresponding to a specific feature index in the sparse ftr array
	protected abstract String dispFtrVal(TreeMap<Integer, Float> ftrs, Integer idx);
	@Override
	public final int hashCode() {		return _hashCode;	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)			return true;
		if (obj == null)			return false;
		if (getClass() != obj.getClass())			return false;
		SOMExample other = (SOMExample) obj;
		if (_hashCode != other._hashCode)			return false;
		return true;
	}
	
	private final void initFlags(){stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlag(i,false);}}
	public final void setAllFlags(int[] idxs, boolean val) {for (int idx : idxs) {setFlag(idx, val);}}
	public final void setFlag(int idx, boolean val){
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
	public final boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		
	public final double get_sqDistToBMU() {	return _sqDistToBMU;}
	public final void set_sqDistToBMU(double _sqDistToBMU) {this._sqDistToBMU = _sqDistToBMU;}
	public final SOMMapNode getBmu() {	return bmu;	}
	public final void setBmu(SOMMapNode _bmu) {	bmu = _bmu;}
	
	/**
	 * this will return the bmu membership and neighborhood membership of this node
	 * @return
	 */
	public String getBMU_NHoodMbrship_CSV() {
		String res = ""+OID+",";
		if(null==bmu) {return res + "No Mapped BMU For this example";}
		res += ""+String.format("%.8f", _sqDistToBMU)+","+bmu.mapNodeCoord.toCSVString()+",";
		for(Double _dist : mapNodeNghbrs.keySet()) {
			String distRes = ""+String.format("%.8f", _dist)+",";
			ArrayList<SOMMapNode> _list = mapNodeNghbrs.get(_dist);
			for(SOMMapNode _mapNode : _list) {		res += distRes+_mapNode.mapNodeCoord.toCSVString()+",";	}
		}
		return res;
	}//getBMU_NHoodMbrshipString
	
	/**
	 * get descriptive header string for csv output of nodes and their bmus
	 * @return descriptive header string describing format of bmu neighborhood output
	 */
	public String getBMU_NHoodHdr_CSV() {
		String res = "OID,Sq Dist to BMU, BMU Map Loc,";
		//add heading for each neighbor
		for(int i =0;i<mapNodeNghbrs.size();++i) {	res +="Sq Dist to Nbr Map Node, Neighbor Node Map Loc,";		}
		return res;
	}//getBMU_NHoodHdr_CSV()

	@Override
	public String toString(){
		String res = "Example OID# : "+OID ;
		if(null!=mapLoc){res+="Location on SOM map : " + mapLoc.toStrBrf();}
		int numTrnFtrs = mapMgr.getNumTrainFtrs();
		if (numTrnFtrs > 0) {
			res += "\nUnscaled Features (" +numTrnFtrs+ " ) :";
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
	public static SOM_MapManager mapMgr;
	//message object manages logging/printing to screen
	protected static MessageObject msgObj;
	//type of example data this is
	protected ExDataType type;
	//location in mapspace most closely matching this node - actual map location (most likely between 4 map nodes)
	public myPointf mapLoc;		
	//bmu map node location - this is same as mapLoc/ignored for map nodes
	protected myPointf mapNodeLoc;
	//draw-based vars
	protected float rad;
	protected static int drawDet;	
	//for debugging purposes, gives min and max radii of spheres that will be displayed on map for each node proportional to # of samples - only display related
	public static float minRad = 100000, maxRad = -100000;
	//array of color IDXs for specific color roles : idx 0 ==fill, idx 1 == strk, idx 2 == txt
	//alt is for displaying alternate state
	protected int[] nodeClrs, altClrs;		
	
	public baseDataPtVis(SOM_MapManager _map, ExDataType _type) {
		mapMgr = _map;type=_type;
		if(msgObj==null) {msgObj=mapMgr.buildMsgObj();}
		mapLoc = new myPointf();	
		mapNodeLoc = new myPointf();
		rad = 1.0f;
		drawDet = 2;
		nodeClrs = mapMgr.getClrFillStrkTxtAra(type);
		altClrs = mapMgr.getAltClrFillStrkTxtAra();
	}//ctor	
	
	//copy ctor
	public baseDataPtVis(baseDataPtVis _otr) { 
		this(_otr.mapMgr,_otr.type);	
		mapLoc = _otr.mapLoc;
		mapNodeLoc = _otr.mapNodeLoc;
		nodeClrs = _otr.nodeClrs;
		altClrs = _otr.altClrs;
	}//
	
	protected void setRad(float _rad){
		rad = _rad;//((float)(Math.log(2.0f*(_rad+1))));
		minRad = minRad > rad ? rad : minRad;
		maxRad = maxRad < rad ? rad : maxRad;
		//drawDet = ((int)(Math.log(2.0f*(rad+1)))+1);
	}
	public float getRad(){return rad;}
	
	public int getTypeVal() {return type.getVal();}
	public ExDataType getType() {return type;}
	
	//set map location for this example
	public final void setMapLoc(myPointf _pt){mapLoc.set(_pt.x,_pt.y,_pt.z);}
	
	//draw this example with a line linking it to its best matching unit
	public final void drawMeLinkedToBMU(my_procApplet p, float _rad, String ID){
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
	
	public void drawMeSmallNoLbl(my_procApplet p){
		p.pushMatrix();p.pushStyle();
		p.show(mapLoc, 2, 2, nodeClrs); 
		p.popStyle();p.popMatrix();		
	}	
		
	//override drawing in map nodes
	public final void drawMeMap(my_procApplet p){
		p.pushMatrix();p.pushStyle();	
		p.show(mapLoc, getRad(), drawDet, nodeClrs);		
		p.popStyle();p.popMatrix();		
	}//drawMeMap
	
	//override drawing in map nodes
	public final void drawMeMapClr(my_procApplet p, int[] clr){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at mapLoc
		p.show_ClrAra(mapLoc, rad,drawDet, clr, clr);
		p.popStyle();p.popMatrix();		
	}//drawMeMapClr
	
	public void drawMeRanked(my_procApplet p, String lbl, int[] clr, float rad, int rank){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label and no background box	
		p.showNoBox_ClrAra(mapLoc, rad, drawDet, clr, clr, my_procApplet.gui_White, lbl);
		p.popStyle();p.popMatrix();
	}
}//baseDataPtVis



//class description for a data point -
//TODO change this to some other structure, or other comparison mechanism?  allow for subset membership check?
class dataClass implements Comparable<dataClass> {
	
	public String label;
	public String lrnKey;
	private String cls;
	//color of this class, for vis rep
	public int[] clrVal;
	
	public dataClass(String _lrnKey, String _lbl, String _cls, int[] _clrVal){
		lrnKey=_lrnKey;
		label = _lbl;	
		cls = _cls;
		clrVal = _clrVal;
	}	
	public dataClass(dataClass _o){this(_o.lrnKey, _o.label,_o.cls, _o.clrVal);}//copy ctor
		
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
		String res = "Label :  " +label + "\tLrnKey : " + lrnKey  +"\tDesc : "+cls;
		return res;		
	}	
}//dataClass
