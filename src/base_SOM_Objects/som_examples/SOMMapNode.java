package base_SOM_Objects.som_examples;

import java.util.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_segments.SOM_MapNodeCategorySegMgr;
import base_SOM_Objects.som_segments.SOM_MapNodeClassSegMgr;
import base_SOM_Objects.som_segments.SOM_MapNodeSegMgr;
import base_SOM_Objects.som_segments.segmentData.SOM_MapNodeSegmentData;
import base_SOM_Objects.som_segments.segments.SOM_MappedSegment;
import base_SOM_Objects.som_segments.segments.SOM_CategorySegment;
import base_SOM_Objects.som_segments.segments.SOM_ClassSegment;
import base_SOM_Objects.som_segments.segments.SOM_FtrWtSegment;
import base_SOM_Objects.som_segments.segments.SOM_UMatrixSegment;
import base_UI_Objects.*;
import base_Utils_Objects.*;
import base_Utils_Objects.io.MsgCodes;
import base_Utils_Objects.vectorObjs.Tuple;

/**
* objects of inheritors to this abstract class represent nodes in the SOM.  
* The instancing class is responsible for managing any connections to underlying src data, which is project dependent
* @author john
*/
public abstract class SOMMapNode extends SOMExample{
	protected static float ftrThresh = 0.0f;			//change to non-zero value if wanting to clip very low values
	public Tuple<Integer,Integer> mapNodeCoord;	
	//structure to manage examples that consider this node a BMU
	protected SOMMapNodeBMUExamples[] BMUExampleNodes;//	
	
	//set from u matrix file built by som - the similarity of this node to its connected neighbors
	protected float uMatDist;
	protected float[] dispBoxDims;		//box upper left corner x,y and box width,height
	protected int[] uMatClr;
	//array of arrays of row x col of neighbors to this node.  This node is 1,1 - this is for square map to use bicubic interpolation
	//uses 1 higher because display is offset by 1/2 node in positive x, positive y (center of node square)
	public Tuple<Integer,Integer>[][] neighborMapCoords;				
	//similarity to neighbors as given by UMatrix calculation from SOM Exe
	public float[][] neighborUMatWts;
	//actual L2 distance to each neighbor comparing features
	//first dim is type of distance, 2nd dim is x (col) loc, 3rd dim is y (row) loc
	public double[][][] neighborSqDistVals;
	//non-zero ftr idxs in this node.
	private Integer[] nonZeroIDXs;
	
	//segment membership manager for UMatrix-based segments
	private SOM_MapNodeSegmentData uMatrixSegData;	
	//segment membership manager of ftr-index-based segments - will have 1 per ftr with non-zero wt
	//keyed by non-zero ftr index
	private TreeMap<Integer, SOM_MapNodeSegmentData> ftrWtSegData;	
	//this manages the segment functionality for the class segments
	private SOM_MapNodeSegMgr classSegManager;	
	//this manages the segment functionality for the category segments, which are collections of similar classes in a hierarchy
	private SOM_MapNodeSegMgr categorySegManager;
	
	//this node has examples of a particular type
	public boolean[] hasMappedExamples;
	//node color to display for type of data
	protected int[][] dispClrs;
	
	//build a map node from a float array of ftrs
	public SOMMapNode(SOM_MapManager _map, Tuple<Integer,Integer> _mapNodeLoc, float[] _ftrs) {
		super(_map, ExDataType.MapNode,"Node_"+_mapNodeLoc.x+"_"+_mapNodeLoc.y);
		if(_ftrs.length != 0){	setFtrsFromFloatAra(_ftrs);	}
		initMapNode( _mapNodeLoc);		
	}
	
	//build a map node from a string array of features
	public SOMMapNode(SOM_MapManager _map,Tuple<Integer,Integer> _mapNodeLoc, String[] _strftrs) {
		super(_map, ExDataType.MapNode, "Node_"+_mapNodeLoc.x+"_"+_mapNodeLoc.y);
		if(_strftrs.length != 0){	
			float[] _tmpFtrs = new float[_strftrs.length];		
			for (int i=0;i<_strftrs.length; ++i) {		_tmpFtrs[i] = Float.parseFloat(_strftrs[i]);	}
			setFtrsFromFloatAra(_tmpFtrs);	
		}
		initMapNode( _mapNodeLoc);
	}
	//build feature vector from passed feature array
	private void setFtrsFromFloatAra(float[] _ftrs) {
		clearFtrMap(ftrMapTypeKey);//ftrMaps[ftrMapTypeKey].clear();
		//ArrayList<Integer> nonZeroIDXList = new ArrayList<Integer>();
		float ftrVecSqMag = 0.0f;
		for(int i = 0; i < _ftrs.length; ++i) {	
			Float val =  _ftrs[i];
			if (val > ftrThresh) {
				ftrVecSqMag+=val*val;
				ftrMaps[ftrMapTypeKey].put(i, val);
				//nonZeroIDXList.add(i);
			}
		}
		//called after features are built because that's when we have all ftrs for this example determined
		ftrVecMag = (float) Math.sqrt(ftrVecSqMag);		
		nonZeroIDXs = ftrMaps[ftrMapTypeKey].keySet().toArray(new Integer[0]);//	nonZeroIDXList.toArray(new Integer[0]);
		setFlag(ftrsBuiltIDX, true);
		buildNormFtrData();		
	}//setFtrsFromFloatAra
	
	public final Integer[] getNonZeroIDXs() {return nonZeroIDXs;}

	//called at end of base class construction
	private void initMapNode(Tuple<Integer,Integer> _mapNode){
		hasMappedExamples = new boolean[ExDataType.getNumVals()];
		dispClrs = new int[hasMappedExamples.length][];
		for(int i=0;i<hasMappedExamples.length;++i) {		hasMappedExamples[i]=false;		dispClrs[i]=nodeClrs;	}
		mapNodeCoord = _mapNode;		
		mapLoc = mapMgr.buildScaledLoc(mapNodeCoord);
		dispBoxDims = mapMgr.buildUMatBoxCrnr(mapNodeCoord);		//box around map node
		initNeighborMap();
		//these are the same for map nodes
		mapNodeLoc.set(mapLoc);
		uMatClr = new int[3];
		BMUExampleNodes = new SOMMapNodeBMUExamples[ExDataType.getNumVals()];
		for(int i=0;i<BMUExampleNodes.length;++i) {	BMUExampleNodes[i] = new SOMMapNodeBMUExamples(this,ExDataType.getVal(i));	}
		uMatrixSegData = new SOM_MapNodeSegmentData(this, this.OID+"_UMatrixData", "UMatrix Distance");
		ftrWtSegData = new TreeMap<Integer, SOM_MapNodeSegmentData>();
		for(Integer idx : ftrMaps[ftrMapTypeKey].keySet()) {
			//build feature weight segment data object for every non-zero weight present in this map node - this should NEVER CHANGE without reconstructing map nodes
			ftrWtSegData.put(idx, new SOM_MapNodeSegmentData(this, this.OID+"_FtrWtData_IDX_"+idx, "Feature Weight For Ftr IDX :"+idx));
		}
		//build structure that holds counts of classes mapped to this node
		classSegManager = new SOM_MapNodeClassSegMgr(this);		
		//build structure that holds counts of categories mapped to this node (category is a collection of similar classes)
		categorySegManager = new SOM_MapNodeCategorySegMgr(this);
		//instancing class-specific functionality
		_initDataFtrMappings();
	}//initMapNode
	
	/**
	 * this will map feature values to some representation of the underlying feature 
	 * description - this is specific to underlying data and is called from base class initMapNode
	 */
	protected abstract void _initDataFtrMappings();	
	
	///////////////////
	// class-based segment data
	
	public final void clearClassSeg() {	 										classSegManager.clearAllSegData();}//clearClassSeg()
	public final void setClassSeg(Integer _cls, SOM_ClassSegment _clsSeg) {		classSegManager.setSeg(_cls, _clsSeg);}	
	public final SOM_MappedSegment getClassSegment(Integer _cls) {				return classSegManager.getSegment(_cls);	}	
	public final int getClassSegClrAsInt(Integer _cls) {						return classSegManager.getSegClrAsInt(_cls);}		
	//for passed -class (not idx)- give this node's probability
	public final float getClassProb(Integer _cls) {								return classSegManager.getSegProb(_cls);}
	public final Set<Integer> getClassSegIDs(){									return classSegManager.getSegIDs();}	
	public final TreeMap<Integer, Float> getClass_SegDataRatio(){				return classSegManager.getSegDataRatio();}
	public final Float getTtlNumMappedClassInstances() { 						return classSegManager.getTtlNumMappedInstances();}
	//return map of classes mapped to counts present
	@SuppressWarnings("unchecked")
	public final TreeMap<Integer, Float> getMappedClassCounts() {				return classSegManager.getMappedCounts();	}
	public final Float getMappedClassCountAtSeg(Integer segID) {				return classSegManager.getMappedCountAtSeg(segID);}
	protected final SOM_MapNodeSegMgr getClassSegManager() {					return classSegManager;}
	protected final String getClassSegment_CSVStr() {								return classSegManager.getSegDataDescStringForNode();}
	protected final String getClassSegment_CSVStr_Hdr() {							return classSegManager.getSegDataDescStrForNode_Hdr();}
	
	///////////////////
	// category order-based segment data
	
	public final void clearCategorySeg() {												categorySegManager.clearAllSegData();	}
	public final void setCategorySeg(Integer _cat, SOM_CategorySegment _catSeg) {		categorySegManager.setSeg(_cat, _catSeg);}	
	public final SOM_MappedSegment getCategorySegment(Integer _cat) {					return categorySegManager.getSegment(_cat);}
	public final int getCategorySegClrAsInt(Integer _cat) {								return categorySegManager.getSegClrAsInt(_cat);}		
	//for passed -Category label (not cat idx)- give this node's probability
	public final float getCategoryProb(Integer _cat) {                       			return categorySegManager.getSegProb(_cat);}            
	public final Set<Integer> getCategorySegIDs(){		                      			return categorySegManager.getSegIDs();}	             
	public final TreeMap<Integer, Float> getCategory_SegDataRatio(){          			return categorySegManager.getSegDataRatio();}           
	public final Float getTtlNumMappedCategoryInstances() {                   			return categorySegManager.getTtlNumMappedInstances();} 	
	//return map of categories to classes within category and counts of each class
	@SuppressWarnings("unchecked")
	public final TreeMap<Integer, TreeMap<Integer, Float>> getMappedCategoryCounts(){	return categorySegManager.getMappedCounts();}
	public final Float getMappedCategoryCountAtSeg(Integer segID) {						return categorySegManager.getMappedCountAtSeg(segID);}
	protected final SOM_MapNodeSegMgr getCategorySegManager() {							return categorySegManager;}
	protected final String getCategorySegment_CSVStr() {									return categorySegManager.getSegDataDescStringForNode();}
	protected final String getCategorySegment_CSVStr_Hdr() {								return categorySegManager.getSegDataDescStrForNode_Hdr();}

	///////////////////
	// ftr-wt based segment data
	
	public final void clearFtrWtSeg() {	for(Integer idx : ftrWtSegData.keySet()) {ftrWtSegData.get(idx).clearSeg();	}	}
	public final void setFtrWtSeg(Integer idx, SOM_FtrWtSegment _ftrWtSeg) {ftrWtSegData.get(idx).setSeg(_ftrWtSeg);}		//should always exist - if doesn't is bug, so no checking to expose bug
	
	public final SOM_MappedSegment getFtrWtSegment(Integer idx) {
		SOM_MapNodeSegmentData ftrWtMgrAtIdx = ftrWtSegData.get(idx);
		if(null==ftrWtMgrAtIdx) {return null;}			//does not have weight at this feature index
		return ftrWtMgrAtIdx.getSegment();
	}
	public final int getFtrWtSegClrAsInt(Integer idx) {
		SOM_MapNodeSegmentData ftrWtMgrAtIdx = ftrWtSegData.get(idx);
		if(null==ftrWtMgrAtIdx) {return 0;}			//does not have weight at this feature index	
		return ftrWtMgrAtIdx.getSegClrAsInt();
	}	
	
	//descriptor string for ftr data
	protected final String getFtrWtSegment_CSVStr() {//ftrMaps[normFtrMapTypeKey].get(ftrIDX)
		TreeMap<Float, ArrayList<String>> mapNodeProbs = new TreeMap<Float, ArrayList<String>>(Collections.reverseOrder());
		//use normalized ftr data so that the ftr vector provides probabilities
		for(Integer segID : ftrMaps[normFtrMapTypeKey].keySet()) {
			float prob = ftrMaps[normFtrMapTypeKey].get(segID);
			ArrayList<String> valsAtProb = mapNodeProbs.get(prob);
			if(null==valsAtProb) {valsAtProb = new ArrayList<String>();}
			valsAtProb.add(""+segID);
			mapNodeProbs.put(prob, valsAtProb);				
		}	
		String res = getFtrWtSegment_CSVStr_Indiv(mapNodeProbs);
		return res;	
	}

	protected abstract String getFtrWtSegment_CSVStr_Indiv(TreeMap<Float, ArrayList<String>> mapNodeProbs);
	protected abstract String getFtrWtSegment_CSVStr_Hdr();
	
	
	/**
	 * get per-bmu segment descriptor, with key being either "class","category", "ftrwt" or something managed by instancing class
	 * @param segmentType "class","category", "ftrwt" or something managed by instancing class
	 * @return descriptor of this map node's full weight profile for the passed segment
	 */
	public final String getSegment_CSVStr(String segmentType) {
		switch(segmentType.toLowerCase()) {
		case "class" 	: {	return getClassSegment_CSVStr();}
		case "category" : {	return getCategorySegment_CSVStr();}
		case "ftrwt"	: {	return getFtrWtSegment_CSVStr();}
		default 		: {	return getSegment_CSVStr_Indiv(segmentType);}
		}
	}
	protected abstract String getSegment_CSVStr_Indiv(String segmentType);
	/**
	 * get per-bmu segment descriptor, with key being either "class","category", "ftrwt" or something managed by instancing class
	 * @param segmentType "class","category", "ftrwt" or something managed by instancing class
	 * @return descriptor of this map node's full weight profile for the passed segment
	 */
	public final String getSegment_Hdr_CSVStr(String segmentType) {
		switch(segmentType.toLowerCase()) {
		case "class" 	: {	return getClassSegment_CSVStr_Hdr();}
		case "category" : {	return getCategorySegment_CSVStr_Hdr();}
		case "ftrwt"	: {	return getFtrWtSegment_CSVStr_Hdr();}
		default 		: {	return getSegment_Hdr_CSVStr_Indiv(segmentType);}
		}
	}
	protected abstract String getSegment_Hdr_CSVStr_Indiv(String segmentType);
	
	////////////////////
	// u matrix segment data
	//provides default values for colors if no segument is defined
	public final void clearUMatrixSeg() {		uMatrixSegData.clearSeg();}	
	//called by segment itself
	public final void setUMatrixSeg(SOM_UMatrixSegment _uMatrixSeg) {	uMatrixSegData.setSeg(_uMatrixSeg);	}

	public final SOM_MappedSegment getUMatrixSegment() {return  uMatrixSegData.getSegment();}
	public final int getUMatrixSegClrAsInt() {return uMatrixSegData.getSegClrAsInt();}
	
	//UMatrix distance as calculated by SOM Executable
	public final void setUMatDist(float _d) {uMatDist = (_d < 0 ? 0.0f : _d > 1.0f ? 1.0f : _d); int clr=(int) (255*uMatDist); uMatClr = new int[] {clr,clr,clr};}
	public final float getUMatDist() {return uMatDist;}	
	
	/**
	 * map nodes don't have bmus and so this functionality would be meaningless for them
	 */
	public void setSegmentsAndProbsFromBMU() {};
	
	//////////////////////////////
	// neighborhood construction and calculations
	//initialize 4-neighbor node neighborhood - grid of adjacent 4x4 nodes
	//this is for individual visualization calculations - 
	//1 node lesser and 2 nodes greater than this node, with location in question being > this node's location
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
	
	/**
	 * build a structure to hold the SQ L2 distance between this map node and its neighbor map nodes, ftr-wise
	 * @param mapNodes - map of all SOM map nodes
	 */
	public final void buildMapNodeNeighborSqDistVals(TreeMap<Tuple<Integer,Integer>, SOMMapNode> mapNodes) {//only build immediate neighborhood
		neighborSqDistVals = new double[ftrMapTypeKeysAra.length][][];
		//TreeMap<Tuple<Integer,Integer>, SOMMapNode> mapNodes = mapMgr.getMapNodes();
		for(int ftrIDX = 0;ftrIDX<neighborSqDistVals.length;++ftrIDX) {
			neighborSqDistVals[ftrIDX]=new double[neighborMapCoords.length][];
			for(int row=0;row<neighborSqDistVals[ftrIDX].length;++row) {			
				neighborSqDistVals[ftrIDX][row]=new double[neighborMapCoords[row].length];
				for(int col=0;col<neighborSqDistVals[ftrIDX][row].length;++col) {
					neighborSqDistVals[ftrIDX][row][col] = getSqDistFromFtrType(mapNodes.get(neighborMapCoords[row][col]).ftrMaps[ftrIDX],ftrMaps[ftrIDX]);
				}
			}		
		}
	}//buildMapNodeNeighborSqDistVals
	
	/**
	 * 2d array of all umatrix weights and L2 Distances for neighors of this node, for bi-cubic interp
	 * @param mapNodes - map of all SOM map nodes
	 */
	public final void buildMapNodeNeighborUMatrixVals(TreeMap<Tuple<Integer,Integer>, SOMMapNode> mapNodes) {
		neighborUMatWts = new float[neighborMapCoords.length][];				
		//TreeMap<Tuple<Integer,Integer>, SOMMapNode> mapNodes
		for(int row=0;row<neighborUMatWts.length;++row) {
			neighborUMatWts[row]=new float[neighborMapCoords[row].length];			
			for(int col=0;col<neighborUMatWts[row].length;++col) {
				neighborUMatWts[row][col] = mapNodes.get(neighborMapCoords[row][col]).getUMatDist();				
			}
		}
	}//buildNeighborWtVals
	
	/**
	 *  this will build the comparison feature vector array that is used as the comparison vector 
	 *  in distance measurements - for most cases this will just be a copy of the ftr vector array
	 *  but in some instances, there might be an alternate vector to be used to handle when, for 
	 *  example, an example has ftrs that do not appear on the map
	 * @param _ignored : ignored
	 */
	public final void buildCompFtrVector(float _ignored) {		compFtrMaps = ftrMaps;	}
	
	//////////////////////////////////
	// interpolation for UMatrix dists
	//return bicubic interpolation of each neighbor's UMatWt 
	public final float biCubicInterp_UMatrix(float tx, float ty) {	return _biCubicInterpFrom2DArray(neighborUMatWts, tx, ty);	}//biCubicInterp_UMatrix
	public final double biCubicInterp_SqDist(int ftrType, float tx, float ty) { return _biCubicInterpFrom2DArray(neighborSqDistVals[ftrType], tx, ty);	}//biCubicInterp_UMatrix
	
	//cubic formula in 1 dim
	private float findCubicVal(float[] p, float t) { 	return p[1]+0.5f*t*(p[2]-p[0] + t*(2.0f*p[0]-5.0f*p[1]+4.0f*p[2]-p[3] + t*(3.0f*(p[1]-p[2])+p[3]-p[0]))); }
	private float _biCubicInterpFrom2DArray(float[][] wtMat, float tx, float ty) {
		float [] aAra = new float[wtMat.length];
		for (int row=0;row<wtMat.length;++row) {aAra[row]=findCubicVal(wtMat[row], tx);}
		float val = findCubicVal(aAra, ty);
		return ((val <= 0.0f) ? 0.0f : (val >= 1.0f) ? 1.0f : val);		
	}//_biCubicInterpFrom2DArray
	
	//double cubic formula in 1 dim
	private double findCubicValDbl(double[] p, float t) { 	return p[1]+0.5*t*(p[2]-p[0] + t*(2.0*p[0]-5.0*p[1]+4.0*p[2]-p[3] + t*(3.0*(p[1]-p[2])+p[3]-p[0]))); }
	private double _biCubicInterpFrom2DArray(double[][] wtMat, float tx, float ty) {
		double [] aAra = new double[wtMat.length];
		for (int row=0;row<wtMat.length;++row) {aAra[row]=findCubicValDbl(wtMat[row], tx);}
		double val = findCubicValDbl(aAra, ty);
		return ((val <= 0.0) ? 0.0 : (val >= 1.0) ? 1.0 : val);		
	}//_biCubicInterpFrom2DArray	
	
	/**
	 * clear specific type of example aggregation structure
	 * @param _typeIDX
	 */
	public void clearBMUExs(int _typeIDX) {		BMUExampleNodes[_typeIDX].init();	}//addToBMUs
	
	//add passed example to appropriate bmu construct depending on what type of example is passed (training, testing, product)
	public void addTrainingExToBMUs(SOMExample ex, int _typeIDX) {
		//same as adding any example
		double sqDist = ex.get_sqDistToBMU();
		BMUExampleNodes[_typeIDX].addExample(sqDist,ex);
		hasMappedExamples[_typeIDX]=true;
		//add relelvant tags/classes, if any, for training examples
		addTrainingExToBMUs_Priv(sqDist,ex);
	}//addTrainingExToBMUs 
	
	//add passed example to appropriate bmu construct depending on what type of example is passed (training, testing, product)
	public void addExToBMUs(SOMExample ex, int _typeIDX) {
		double sqDist = ex.get_sqDistToBMU();
		BMUExampleNodes[_typeIDX].addExample(sqDist,ex);
		hasMappedExamples[_typeIDX]=true;
	}//addExToBMUs 
	
	//copy passed map node example to appropriate bmu construct depending on what type of example is passed (training, testing, product)
	//will only be called one time, so no need to synchrnonize
	public void copyMapNodeExamples(double dist, SOMMapNode ex, int _typeIDX) {
		//int _typeIDX = ex.type.getVal();
		//BMUExampleNodes[_typeIDX].addExample(dist,ex);		
		BMUExampleNodes[_typeIDX].setCopyOfMapNode(dist, ex.BMUExampleNodes[_typeIDX]);
		hasMappedExamples[_typeIDX]=false;
		//add relelvant tags, if any, for training examples - only call if being called by training examples
		if(ExDataType.Training == ExDataType.getVal(_typeIDX)) {	addMapNodeExToBMUs_Priv(dist,ex);}
	}//addMapNodeExToBMUs 
	
	//finalize all calculations for examples using this node as a bmu - this calculates quantities based on totals derived, used for visualizations
	//will only be called 1 time per map node, so no need for synch
	//MUST BE CALLED after adding all examples but before any visualizations will work
	public synchronized void finalizeAllBmus(int _typeIDX) {		
		BMUExampleNodes[_typeIDX].finalize();	
		dispClrs[_typeIDX] = hasMappedExamples[_typeIDX] ? nodeClrs : altClrs;
	}
	
	/**
	 * manage instancing map node handlign - specifically, handle using 2ndary features as node markers (like a product tag)
	 * these functions will both build class and category-specific data in instancing map node, if any exists
	 * @param dist
	 * @param ex
	 */
	protected abstract void addTrainingExToBMUs_Priv(double dist, SOMExample ex);
	//add map node with examples to map node without any
	protected abstract void addMapNodeExToBMUs_Priv(double dist, SOMMapNode ex);
	
	//call this instead of buildStdFtrsMap, passing mins and diffs
	//called by SOMDataLoader - these are standardized based on data mins and diffs seen in -map nodes- feature data, not in training data
	public abstract void buildStdFtrsMapFromFtrData_MapNode(float[] minsAra, float[] diffsAra);			
	
	//this will return the training label(s) of this example - a map node -never- is used as training
	//they should not be used for supervision during/after training (not sure how that could even happen)
	public TreeMap<Integer,Integer> getTrainingLabels() {return null;}

	public boolean getHasMappedExamples(int _typeIDX) { return BMUExampleNodes[_typeIDX].hasMappedExamples();}
	//get # of requested type of examples mapping to this node
	public int getNumExamples(int _typeIDX) {	return BMUExampleNodes[_typeIDX].getNumExamples();	}		
	//get a map of all examples of specified type near this bmu and the distances for the example
	public HashMap<SOMExample, Double> getAllExsAndDist(int _typeIDX){	return BMUExampleNodes[_typeIDX].getExsAndDist();}//getAllExsAndDist	
	//return string array of descriptions for the requested kind of examples mapped to this node
	public String[] getAllExampleDescs(int _typeIDX) {return BMUExampleNodes[_typeIDX].getAllExampleDescs();}
	
	public float[] getDispBoxDims() {return dispBoxDims;}
	
	//get class probability from bmu for passed class
	//treat this example's probability for a particular class as the probability of its BMU for that class (# examples of that class divided by total # of class seen at that node)
	//override these base class functions to be aliases for map node functions
	@Override
	public final float getBMUProbForClass(Integer cls) {	return getClassProb(cls);}
	@Override
	public final float getBMUProbForCategory(Integer category) {	return getCategoryProb(category);	}
	//assumes bmu exists and is not null
	@Override
	public final Set<Integer> getBMUClassSegIDs(){			return getClassSegIDs();}
	@Override
	public final Set<Integer> getBMUCategorySegIDs(){			return getCategorySegIDs();}
	@Override
	public final SOM_MappedSegment getBMUClassSegment(int cls) {	return getClassSegment(cls);}
	@Override
	public final SOM_MappedSegment getBMUCategorySegment(int cat) {	return getCategorySegment(cat);}	
	@Override
	public final Tuple<Integer,Integer> getBMUMapNodeCoord(){	return mapNodeCoord;}

	
	//////////////////////////
	// draw routines
	
	public void drawMePopLbl(my_procApplet p, int _typeIDX) {		BMUExampleNodes[_typeIDX].drawMapNodeWithLabel(p);	}	
	public void drawMePopNoLbl(my_procApplet p, int _typeIDX) {		BMUExampleNodes[_typeIDX].drawMapNodeNoLabel(p);	}	
//	public void drawMeSmallWt(my_procApplet p, int ftrIDX){
//		p.pushMatrix();p.pushStyle();
//		Float wt = ftrMaps[normFtrMapTypeKey].get(ftrIDX);
//		if (wt==null) {wt=0.0f;}
//		p.show(mapLoc, 2, 2, nodeClrs, new String[] {this.OID+":",String.format("%.4f", wt)}); 
//		p.popStyle();p.popMatrix();		
//	}	
	public void drawMeSmall(my_procApplet p){
		p.pushMatrix();p.pushStyle();
		p.show(mapLoc, 2, 2, nodeClrs, new String[] {this.OID}); 
		p.popStyle();p.popMatrix();		
	}		
	public void drawMeWithWt(my_procApplet p, float wt, String[] disp){
		p.pushMatrix();p.pushStyle();	
		p.show(mapLoc, wt, (int)wt+1, nodeClrs,  disp); 
		p.popStyle();p.popMatrix();		
	}//drawMeWithWt

	//draw segment contribution
	public final void drawMeUMatSegClr(my_procApplet p){uMatrixSegData.drawMe(p);}
	
	//draw ftr weight segment contribution - use std ftr as alpha
	public final void drawMeFtrWtSegClr(my_procApplet p, Integer idx, float wt) {
		SOM_MapNodeSegmentData ftrWtMgrAtIdx = ftrWtSegData.get(idx);
		if(null==ftrWtMgrAtIdx) {return;}			//does not have weight at this feature index
		ftrWtMgrAtIdx.drawMe(p,(int) (255*wt));
	}//drawMeFtrWtSegClr
	
	//draw class pop segment contribution 
	public final void drawMeClassClr(my_procApplet p, Integer cls) {classSegManager.drawMeSegClr(p,  cls);	}//drawMeFtrWtSegClr
	
	//draw category segment contribution - collection of classes
	public final void drawMeCategorySegClr(my_procApplet p, Integer category) { categorySegManager.drawMeSegClr(p, category);}//drawMeFtrWtSegClr
	
	//draw a box around this node of uMatD color
	public void drawMeUMatDist(my_procApplet p){drawMeClrRect(p,uMatClr, 255);}
	public void drawMeProdBoxClr(my_procApplet p, int[] clr) {drawMeClrRect(p,clr, clr[3]);}
	//clr is 3 vals
	private void drawMeClrRect(my_procApplet p, int[] fclr, int alpha) {
		p.pushMatrix();p.pushStyle();
		p.setFill(fclr, alpha);
		p.noStroke();
		p.rect(dispBoxDims);		
		p.popStyle();p.popMatrix();	
	}//drawMeClrRect
	
	//map nodes are never going to be training examples
	@Override
	protected void setIsTrainingDataIDX_Priv() {mapMgr.getMsgObj().dispMessage("SOMMapNode","setIsTrainingDataIDX_Priv","Calling inappropriate setIsTrainingDataIDX_Priv for SOMMapNode - should never have training index set.", MsgCodes.warning2);	}
	@Override
	//feature is already made in constructor, read from map, so this is ignored
	protected void buildFeaturesMap() {	}
	@Override
	public String getPreProcDescrForCSV() {	return "Should not save SOMMapNode to intermediate CSV";}
	@Override
	public String getRawDescColNamesForCSV() {return "Do not save SOMMapNode to intermediate CSV";}

	//map nodes do not use finalize
	@Override
	public void finalizeBuildBeforeFtrCalc() {	}
	//this should not be used - should build stdFtrsmap based on ranges of each ftr value in trained map
	@Override
	protected void buildStdFtrsMap() {
		mapMgr.getMsgObj().dispMessage("SOMMapNode","buildStdFtrsMap","Calling inappropriate buildStdFtrsMap for SOMMapNode : should call buildStdFtrsMap_MapNode from SOMDataLoader using trained map w/arrays of per feature mins and diffs", MsgCodes.warning2);		
	}
	
	public String toString(){
		String res = "Node Loc : " + mapNodeCoord.toString()+"\t" + super.toString();
		return res;		
	}
	
}//class SOMMapNode

//this class will hold a structure to aggregate and process the examples of a particular type that consider the owning node a BMU
class SOMMapNodeBMUExamples{
	//owning node of these examples
	private SOMMapNode node;
	//map of examples that consider node to be their bmu; keyed by euclidian distance
	private TreeMap<Double,HashSet<SOMExample>> examplesBMU;
	//size of examplesBMU
	private int numMappedEx;
	//log size of examplesBMU +1, used for visualization radius
	private float logExSize;	
	//detail of rendered point representing parent node - should be based roughly on size of population
	private int nodeSphrDet;
	//string array holding relevant info for visualization
	private String[] visLabel;
	//data type of examples this is managing
	private ExDataType dataType;
	//whether or not this BMU node has examples
	private boolean hasExamples;
	//color to show node with, based on whether it has examples or not
	private int[] dispClrs;
	//this is node we copied from, if this node is copied; otherwise it is the same as node
	private SOMMapNode copyNode;
	//this is the distance from copyNode that this object's owning node is
	private double sqDistToCopyNode;
	//
	public SOMMapNodeBMUExamples(SOMMapNode _node, ExDataType _dataType) {	
		node = _node;
		dataType = _dataType;
		examplesBMU = new TreeMap<Double,HashSet<SOMExample>>(); 
		init();
		copyNode = node;
	}//ctor
	/**
	 * (re)initialize this structure;
	 */
	public void init() {
		examplesBMU.clear();
		numMappedEx = 0;
		logExSize = 0;
		nodeSphrDet = 2;
		hasExamples = false;
		dispClrs = node.altClrs;
		sqDistToCopyNode = 0.0;
		copyNode = node;
		visLabel = new String[] {""+node.OID+" : ", ""+numMappedEx};
	}//init
	
	//set this example to be a copy of passed example
	public void setCopyOfMapNode(double sqdist, SOMMapNodeBMUExamples otrEx) {
		examplesBMU.clear();
		TreeMap<Double,HashSet<SOMExample>> otrExamples = otrEx.examplesBMU;
		for(Double dist : otrExamples.keySet()) {
			HashSet<SOMExample> otrExs = otrExamples.get(dist);
			HashSet<SOMExample> myExs = new HashSet<SOMExample>();
			for(SOMExample ex : otrExs) {myExs.add(ex);}
			examplesBMU.put(dist, myExs);
		}		
		sqDistToCopyNode = sqdist;
		copyNode = otrEx.node;
		hasExamples = false;
	}
	
	//add passed example
	/**
	 * add passed example to this map node as an example that considers this map node to be its bmu
	 * @param sqdist the squared distance from ex to the map node
	 * @param _ex the example treating this map node as bmu
	 */
	public void addExample(double sqdist, SOMExample _ex) {
		//synching on examplesBMU since owning map node might be called multiple times by different examples in a multi-threaded environment
		synchronized(examplesBMU) {
			HashSet<SOMExample> tmpList = examplesBMU.get(sqdist);
			if(tmpList == null) {tmpList = new HashSet<SOMExample>();}
			tmpList.add(_ex);		
			examplesBMU.put(sqdist, tmpList);	
		}
		hasExamples = true;
	}//addExample
	
	//finalize calculations - perform after all examples are mapped - used for visualizations
	public void finalize() {	
		int numEx = 0;
		for(Double sqDist : examplesBMU.keySet()) {		numEx += examplesBMU.get(sqDist).size();	}
		numMappedEx = numEx;		
		logExSize = (float) Math.log(numMappedEx + 1)*1.5f;	
		nodeSphrDet = (int)( Math.log(logExSize+1)+2);
		visLabel = new String[] {""+node.OID+" : ", ""+numMappedEx};
		dispClrs = hasExamples ? node.nodeClrs : node.altClrs;
		if(!hasExamples && (dataType==ExDataType.Training)) {
			node.mapMgr.getMsgObj().dispInfoMessage("SOMMapNodeBMUExamples", "finalize", "Finalize for " +dataType.getName() + " non-example map node in SOMMapNodeBMUExamples with "+numMappedEx+" copied ex | dispClrs : ["+dispClrs[0]+","+dispClrs[1]+","+dispClrs[2]+"] | node addr : " + node.mapNodeCoord +" | copied node addr : "+copyNode.mapNodeCoord+" | dist to copy node : " + sqDistToCopyNode+".");
		}
	}
	//whether this map node is a copy of another or not
	public boolean hasMappedExamples(){return hasExamples;}
	public int getNumExamples() {return numMappedEx;}

	/////////////////////
	// drawing routines for owning node
	public void drawMapNodeWithLabel(my_procApplet p) {
		p.pushMatrix();p.pushStyle();	
		p.show(node.mapLoc, logExSize, nodeSphrDet, dispClrs,  visLabel); 		
		p.popStyle();p.popMatrix();		
	}

	public void drawMapNodeNoLabel(my_procApplet p) {
		p.pushMatrix();p.pushStyle();	
		p.show(node.mapLoc, logExSize, nodeSphrDet, dispClrs); 		
		p.popStyle();p.popMatrix();		
	}
	
	//return a listing of all examples and their distance from this BMU
	public HashMap<SOMExample, Double> getExsAndDist(){
		HashMap<SOMExample, Double> res = new HashMap<SOMExample, Double>();
		for(double dist : examplesBMU.keySet() ) {
			HashSet<SOMExample> tmpList = examplesBMU.get(dist);
			if(tmpList == null) {continue;}//should never happen			
			for (SOMExample ex : tmpList) {res.put(ex, dist);}
		}
		return res;
	}//getExsAndDist()
	
	//return all example OIDs in array of CSV form, with key being distance and columns holding all examples that distance away
	public String[] getAllExampleDescs() {
		ArrayList<String> tmpRes = new ArrayList<String>();
		for(double dist : examplesBMU.keySet() ) {
			HashSet<SOMExample> tmpList = examplesBMU.get(dist);
			if(tmpList == null) {continue;}//should never happen
			String tmpStr = String.format("%.6f", dist);
			for (SOMExample ex : tmpList) {tmpStr += "," + ex.OID;	}
			tmpRes.add(tmpStr);
		}		
		return tmpRes.toArray(new String[1]);		
	}//getAllTestExamples
}//class SOMMapNodeExamples

