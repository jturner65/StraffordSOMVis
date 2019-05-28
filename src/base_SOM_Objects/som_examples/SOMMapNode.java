package base_SOM_Objects.som_examples;

import java.util.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_utils.segments.*;
import base_UI_Objects.*;
import base_Utils_Objects.*;

/**
* objects of inheritors to this abstract class represent nodes in the SOM.  
* The instancing class is responsible for managing any connections to underlying src data, which is project dependent
* @author john
*/
public abstract class SOMMapNode extends SOMExample{
	protected static float ftrThresh = 0.0f;			//change to non-zero value if wanting to clip very low values
	public Tuple<Integer,Integer> mapNodeCoord;	

	protected SOMMapNodeBMUExamples[] BMUExampleNodes;//	
	
	//set from u matrix built by somoclu - the similarity of this node to its neighbors
	protected float uMatDist;
	protected float[] dispBoxDims;		//box upper left corner x,y and box width,height
	protected int[] uMatClr;
	//array of arrays of row x col of neighbors to this node.  This node is 1,1 - this is for square map to use bicubic interpolation
	//uses 1 higher because display is offset by 1/2 node in positive x, positive y (center of node square)
	public Tuple<Integer,Integer>[][] neighborMapCoords;				
	//similarity to neighbors as given by UMatrix calculation from SOM Exe
	public float[][] neighborUMatWts;
	//actual L2 distance to each neighbor comparing features - idx 1,1 should be 0
	public double[][] neighborSqDistVals;
	
	private Integer[] nonZeroIDXs;
	
	//segment membership manager for UMatrix-based segments
	private SOM_MapNodeSegmentData uMatrixSegData;
	
	//segment membership manager of ftr-index-based segments - will have 1 per ftr with non-zero wt
	//keyed by non-zero ftr index
	private TreeMap<Integer, SOM_MapNodeSegmentData> ftrWtSegData;
	
	//build a map node from a float array of ftrs
	public SOMMapNode(SOMMapManager _map, Tuple<Integer,Integer> _mapNodeLoc, float[] _ftrs) {
		super(_map, ExDataType.MapNode,"Node_"+_mapNodeLoc.x+"_"+_mapNodeLoc.y);
		if(_ftrs.length != 0){	setFtrsFromFloatAra(_ftrs);	}
		initMapNode( _mapNodeLoc);		
	}
	
	//build a map node from a string array of features
	public SOMMapNode(SOMMapManager _map,Tuple<Integer,Integer> _mapNodeLoc, String[] _strftrs) {
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
		ftrMaps[ftrMapTypeKey].clear();
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
		//called after features are built because that's when we have all jp's for this example determined
		ftrVecMag = (float) Math.sqrt(ftrVecSqMag);		
		nonZeroIDXs = ftrMaps[ftrMapTypeKey].keySet().toArray(new Integer[0]);//	nonZeroIDXList.toArray(new Integer[0]);
		setFlag(ftrsBuiltIDX, true);
		//buildNormFtrData();		
	}//setFtrsFromFloatAra
	
	public final Integer[] getNonZeroIDXs() {return nonZeroIDXs;}
	
	/**
	 * this will map feature values to some representation of the underlying feature 
	 * description - this is specific to underlying data and must be called from instance 
	 * class ctor
	 */
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
		for(int i=0;i<BMUExampleNodes.length;++i) {	BMUExampleNodes[i] = new SOMMapNodeBMUExamples(this);	}
		uMatrixSegData = new SOM_MapNodeSegmentData(this, this.OID+"_UMatrixData", "UMatrix Distance");
		ftrWtSegData = new TreeMap<Integer, SOM_MapNodeSegmentData>();
		for(Integer idx : ftrMaps[ftrMapTypeKey].keySet()) {
			//build feature weight segment data object for every non-zero weight present in this map node - this should NEVER CHANGE without reconstructing map nodes
			ftrWtSegData.put(idx, new SOM_MapNodeSegmentData(this, this.OID+"_FtrWtData_IDX_"+idx, "Feature Weight For Ftr IDX :"+idx));
		}
	}//initMapNode
	
	///////////////////
	// ftr-wt based segment data
	
	public final void clearFtrWtSeg() {	for(Integer idx : ftrWtSegData.keySet()) {ftrWtSegData.get(idx).clearSeg();	}	}
	public final void setFtrWtSeg(Integer idx, SOM_FtrWtSegment _ftrWtSeg) {ftrWtSegData.get(idx).setSeg(_ftrWtSeg);}		//should always exist - if doesn't is bug, so no checking to expose bug
	
	public final SOMMapSegment getFtrWtSegment(Integer idx) {
		SOM_MapNodeSegmentData ftrWtMgrAtIdx = ftrWtSegData.get(idx);
		if(null==ftrWtMgrAtIdx) {return null;}			//does not have weight at this feature index
		return ftrWtMgrAtIdx.getSegment();
	}
	public final int getFtrWtSegClrAsInt(Integer idx) {
		SOM_MapNodeSegmentData ftrWtMgrAtIdx = ftrWtSegData.get(idx);
		if(null==ftrWtMgrAtIdx) {return 0;}			//does not have weight at this feature index	
		return ftrWtMgrAtIdx.getSegClrAsInt();
	}	
	
	////////////////////
	// u matrix segment data
	//provides default values for colors if no segument is defined
	public final void clearUMatrixSeg() {		uMatrixSegData.clearSeg();}	
	//called by segment itself
	public final void setUMatrixSeg(SOM_UMatrixSegment _uMatrixSeg) {	uMatrixSegData.setSeg(_uMatrixSeg);	}

	public final SOMMapSegment getUMatrixSegment() {return  uMatrixSegData.getSegment();}
	public final int getUMatrixSegClrAsInt() {return uMatrixSegData.getSegClrAsInt();}
	
	//UMatrix distance as calculated by SOM Executable
	public final void setUMatDist(float _d) {uMatDist = (_d < 0 ? 0.0f : _d > 1.0f ? 1.0f : _d); int clr=(int) (255*uMatDist); uMatClr = new int[] {clr,clr,clr};}
	public final float getUMatDist() {return uMatDist;}	
	
	
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
	
	//build a structure to hold the SQ L2 distance between this map node and its neighbor map nodes
	public final void buildMapNodeNeighborSqDistVals() {//only build immediate neighborhood
		neighborSqDistVals = new double[3][];
		TreeMap<Tuple<Integer,Integer>, SOMMapNode> mapNodes = mapMgr.getMapNodes();
		for(int row=0;row<neighborSqDistVals.length;++row) {			
			neighborSqDistVals[row]=new double[3];
			for(int col=0;col<neighborSqDistVals[row].length;++col) {
				neighborSqDistVals[row][col] = getSqDistFromFtrType(mapNodes.get(neighborMapCoords[row][col]).ftrMaps[ftrMapTypeKey],ftrMaps[ftrMapTypeKey]);
			}
		}		
	}//buildMapNodeNeighborSqDistVals
	
	//2d array of all umatrix weights and L2 Distances for neighors of this node, for bi-cubic interp
	public final void buildMapNodeNeighborUMatrixVals() {
		neighborUMatWts = new float[neighborMapCoords.length][];				
		TreeMap<Tuple<Integer,Integer>, SOMMapNode> mapNodes = mapMgr.getMapNodes();
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
	
	//cubic formula in 1 dim
	private float findCubicVal(float[] p, float t) { 	return p[1]+0.5f*t*(p[2]-p[0] + t*(2.0f*p[0]-5.0f*p[1]+4.0f*p[2]-p[3] + t*(3.0f*(p[1]-p[2])+p[3]-p[0]))); }
	private float _biCubicInterpFrom2DArray(float[][] wtMat, float tx, float ty) {
		float [] aAra = new float[wtMat.length];
		for (int row=0;row<wtMat.length;++row) {aAra[row]=findCubicVal(wtMat[row], tx);}
		float val = findCubicVal(aAra, ty);
		return ((val <= 0.0f) ? 0.0f : (val >= 1.0f) ? 1.0f : val);		
	}//_biCubicInterpFrom2DArray
	
	//map nodes are never going to be training examples
	@Override
	protected void setIsTrainingDataIDX_Priv() {mapMgr.getMsgObj().dispMessage("SOMMapNode","setIsTrainingDataIDX_Priv","Calling inappropriate setIsTrainingDataIDX_Priv for SOMMapNode - should never have training index set.", MsgCodes.warning2);	}
	@Override
	//feature is already made in constructor, read from map, so this is ignored
	protected void buildFeaturesMap() {	}
	@Override
	public String getRawDescrForCSV() {	return "Should not save SOMMapNode to intermediate CSV";}
	@Override
	public String getRawDescColNamesForCSV() {return "Do not save SOMMapNode to intermediate CSV";}
	//map nodes do not use finalize
	@Override
	public void finalizeBuildBeforeFtrCalc() {	}
	@Override
	protected HashSet<Tuple<Integer, Integer>> getSetOfAllJpgJpData() {		return null;}//getSetOfAllJpgJpData	
	//this should not be used - should build stdFtrsmap based on ranges of each ftr value in trained map
	@Override
	protected void buildStdFtrsMap() {
		mapMgr.getMsgObj().dispMessage("SOMMapNode","buildStdFtrsMap","Calling inappropriate buildStdFtrsMap for SOMMapNode : should call buildStdFtrsMap_MapNode from SOMDataLoader using trained map w/arrays of per feature mins and diffs", MsgCodes.warning2);		
	}
	//call this instead of buildStdFtrsMap, passing mins and diffs
	
	//called by SOMDataLoader - these are standardized based on data mins and diffs seen in -map nodes- feature data, not in training data
	public abstract void buildStdFtrsMapFromFtrData_MapNode(float[] minsAra, float[] diffsAra);		

	public void clearBMUExs(int _typeIDX) {		BMUExampleNodes[_typeIDX].init();	}//addToBMUs
	
	//add passed example to appropriate bmu construct depending on what type of example is passed (training, testing, product)
	public void addTrainingExToBMUs(SOMExample ex, int _typeIDX) {
		double sqDist = ex.get_sqDistToBMU();
		BMUExampleNodes[_typeIDX].addExample(sqDist,ex);
		//add relelvant tags/classes, if any, for training examples
		addTrainingExToBMUs_Priv(sqDist,ex);
	}//addToBMUs 
	
	//add passed example to appropriate bmu construct depending on what type of example is passed (training, testing, product)
	public void addExToBMUs(SOMExample ex, int _typeIDX) {
		double sqDist = ex.get_sqDistToBMU();
		BMUExampleNodes[_typeIDX].addExample(sqDist,ex);
	}//addToBMUs 
	
	//add passed map node example to appropriate bmu construct depending on what type of example is passed (training, testing, product)
	public void addMapNodeExToBMUs(double dist, SOMMapNode ex, int _typeIDX) {
		//int _typeIDX = ex.type.getVal();
		BMUExampleNodes[_typeIDX].addExample(dist,ex);
		//add relelvant tags, if any, for training examples - 
		addMapNodeExToBMUs_Priv(dist,ex);
	}//addToBMUs 
	//manage instancing map node handlign - specifically, handle using 2ndary features as node markers (like a product tag)
	protected abstract void addTrainingExToBMUs_Priv(double dist, SOMExample ex);
	protected abstract void addMapNodeExToBMUs_Priv(double dist, SOMMapNode ex);
	
	//this will return the training label(s) of this example - a map node -never- is used as training
	//they should not be used for supervision during/after training (not sure how that could even happen)
	public TreeMap<Integer,Integer> getTrainingLabels() {return null;}
	
	//finalize all calculations for examples using this node as a bmu - this calculates quantities based on totals derived, used for visualizations
	//MUST BE CALLED after adding all examples but before any visualizations will work
	public void finalizeAllBmus(int _typeIDX) {		BMUExampleNodes[_typeIDX].finalize();	}
	
	//get # of requested type of examples mapping to this node
	public int getNumExamples(int _typeIDX) {	return BMUExampleNodes[_typeIDX].getNumExamples();	}		
	//get a map of all examples of specified type near this bmu and the distances for the example
	public HashMap<SOMExample, Double> getAllExsAndDist(int _typeIDX){	return BMUExampleNodes[_typeIDX].getExsAndDist();}//getAllExsAndDist	
	//return string array of descriptions for the requested kind of examples mapped to this node
	public String[] getAllExampleDescs(int _typeIDX) {return BMUExampleNodes[_typeIDX].getAllExampleDescs();}
	
	public float[] getDispBoxDims() {return dispBoxDims;}
	
	//////////////////////////
	// draw routines
	
	public void drawMePopLbl(my_procApplet p, int _typeIDX) {		BMUExampleNodes[_typeIDX].drawMapNodeWithLabel(p);	}	
	public void drawMePopNoLbl(my_procApplet p, int _typeIDX) {		BMUExampleNodes[_typeIDX].drawMapNodeNoLabel(p);	}	
	public void drawMeSmallWt(my_procApplet p, int jpIDX){
		p.pushMatrix();p.pushStyle();
		Float wt = this.ftrMaps[stdFtrMapTypeKey].get(jpIDX);
		if (wt==null) {wt=0.0f;}
		p.show(mapLoc, 2, 2, nodeClrs, new String[] {this.OID+":",String.format("%.4f", wt)}); 
		p.popStyle();p.popMatrix();		
	}	
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
	public void drawMeUMatSegClr(my_procApplet p){uMatrixSegData.drawMe(p);}
	
	//draw ftr weight segment contribution - use std ftr as alpha
	public void drawMeFtrWtSegClr(my_procApplet p, Integer idx, float wt) {
		SOM_MapNodeSegmentData ftrWtMgrAtIdx = ftrWtSegData.get(idx);
		if(null==ftrWtMgrAtIdx) {return;}			//does not have weight at this feature index
		ftrWtMgrAtIdx.drawMe(p,(int) (255*wt));
	}//drawMeFtrWtSegClr
	
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
	public void addExample(SOMExample _ex) {addExample(_ex.get_sqDistToBMU(),_ex);}
	public void addExample(double dist, SOMExample _ex) {
		ArrayList<SOMExample> tmpList = examplesBMU.get(dist);
		if(tmpList == null) {tmpList = new ArrayList<SOMExample>();}
		tmpList.add(_ex);		
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
	public void drawMapNodeWithLabel(my_procApplet p) {
		p.pushMatrix();p.pushStyle();	
		p.show(node.mapLoc, logExSize, nodeSphrDet, node.nodeClrs,  visLabel); 		
		p.popStyle();p.popMatrix();		
	}

	public void drawMapNodeNoLabel(my_procApplet p) {
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

