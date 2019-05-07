package base_SOM_Objects.som_examples;

import java.util.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_vis.*;
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
	protected int[] uMatClr, segClr;
	protected int segClrAsInt;
	public Tuple<Integer,Integer>[][] neighborMapCoords;				//array of arrays of row x col of neighbors to this node.  This node is 1,1 - this is for square map to use bicubic interpolation
	public float[][] neighborUMatWts;
	
	private Integer[] nonZeroIDXs;
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
		ArrayList<Integer> nonZeroIDXList = new ArrayList<Integer>();
		float ftrVecSqMag = 0.0f;
		for(int i = 0; i < _ftrs.length; ++i) {	
			Float val =  _ftrs[i];
			if (val > ftrThresh) {
				ftrVecSqMag+=val*val;
				ftrMaps[ftrMapTypeKey].put(i, val);
				nonZeroIDXList.add(i);
			}
		}
		//called after features are built because that's when we have all jp's for this example determined
		ftrVecMag = (float) Math.sqrt(ftrVecSqMag);		
		nonZeroIDXs = nonZeroIDXList.toArray(new Integer[0]);
		setFlag(ftrsBuiltIDX, true);
		//buildNormFtrData();		
	}//setFtrsFromFloatAra
	
	public Integer[] getNonZeroIDXs() {return nonZeroIDXs;}
	
	//this will map feature values to some representation of the underlying feature description - this is specific to undelrying data
	//and should be called from instance class ctor
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
		clearSeg();
	}//initMapNode
	
	public void clearSeg() {		seg = null;segClr = new int[4]; segClrAsInt = 0x0;}	
	//add this node to a segment, and its neighbors - must be done after neighbors are found
	public void addToSeg(SOMMapSegment _seg) {
		seg=_seg;
		seg.addNode(this);
		segClr = seg.getSegClr();
		segClrAsInt = seg.getSegClrAsInt();
		TreeMap<Tuple<Integer,Integer>, SOMMapNode> mapNodes = mapMgr.getMapNodes();
		int row = 1, col = 1;//1,1 is this node for neighbor hood
		SOMMapNode ex = mapNodes.get(neighborMapCoords[row][col+1]);
		if(ex.shouldAddToSegment(seg.thresh)) {ex.addToSeg(seg);}
		ex = mapNodes.get(neighborMapCoords[row][col-1]);
		if(ex.shouldAddToSegment(seg.thresh)) {ex.addToSeg(seg);}
		ex = mapNodes.get(neighborMapCoords[row+1][col]);
		if(ex.shouldAddToSegment(seg.thresh)) {ex.addToSeg(seg);}
		ex = mapNodes.get(neighborMapCoords[row-1][col]);
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
	public final void buildCompFtrVector(float _ignored) {
		compFtrMaps = ftrMaps;
	}
	
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
	
//	//add passed example to appropriate bmu construct depending on what type of example is passed (training, testing, product)
//	public void addExToBMUs(SOMExample ex) {
//		int _typeIDX = ex.type.getVal();
//		BMUExampleNodes[_typeIDX].addExample(ex.get_sqDistToBMU(),ex);
//	}//addToBMUs 
	
	//add passed example to appropriate bmu construct depending on what type of example is passed (training, testing, product)
	public void addExToBMUs(SOMExample ex, int _typeIDX) {
		BMUExampleNodes[_typeIDX].addExample(ex.get_sqDistToBMU(),ex);
	}//addToBMUs 
	
	//add passed example to appropriate bmu construct depending on what type of example is passed (training, testing, product)
	public void addExToBMUs(double dist, SOMExample ex) {
		int _typeIDX = ex.type.getVal();
		BMUExampleNodes[_typeIDX].addExample(dist,ex);
	}//addToBMUs 
	
	//finalize all calculations for examples using this node as a bmu - this calculates quantities based on totals derived, used for visualizations
	//MUST BE CALLED after adding all examples but before any visualizations will work
	public void finalizeAllBmus(int _typeIDX) {		BMUExampleNodes[_typeIDX].finalize();	}
	
	//get # of requested type of examples mapping to this node
	public int getNumExamples(int _typeIDX) {	return BMUExampleNodes[_typeIDX].getNumExamples();	}		
	//get a map of all examples of specified type near this bmu and the distances for the example
	public HashMap<SOMExample, Double> getAllExsAndDist(int _typeIDX){	return BMUExampleNodes[_typeIDX].getExsAndDist();}//getAllExsAndDist	
	//return string array of descriptions for the requested kind of examples mapped to this node
	public String[] getAllExampleDescs(int _typeIDX) {return BMUExampleNodes[_typeIDX].getAllExampleDescs();}
	
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
	
	//draw a box around this node of uMatD color
	public void drawMeUMatDist(my_procApplet p){drawMeClrRect(p,uMatClr, 255);}
	public void drawMeSegClr(my_procApplet p){drawMeClrRect(p,segClr, segClr[3]);}
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

