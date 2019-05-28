package strafford_SOM_PKG.straff_SOM_Examples.products;

import java.util.*;

import base_SOM_Objects.som_examples.ExDataType;
import base_SOM_Objects.som_examples.SOMExample;
import base_SOM_Objects.som_examples.SOMMapNode;
import base_UI_Objects.my_procApplet;
import base_Utils_Objects.MsgCodes;
import base_Utils_Objects.Tuple;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.TcTagData;
import strafford_SOM_PKG.straff_SOM_Examples.Straff_SOMExample;
import strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.TcTagRawToTrainData;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;


/**
 * this class implements a product example, to be used to query the SOM and to illuminate relevant regions on the map.  
 * The product can be specified by a single jp, or by the span of jps' related to a particular jpg, or even to multiple
 * Unlike prospect examples, which are inferred to have 0 values in features that are not explicitly populated, 
 * product examples might not be considered strictly exclusionary (in other words, non-populated features aren't used for distance calcs)
 * This is an important distinction for the SOM-from this we will learn about folks whose interest overlap into multiple jp regions.
 * @author john
 */
public class ProductExample extends Straff_SOMExample{
//		//column names for csv output of this SOM example
	private static final String csvColDescrPrfx = "ID,NumJPs";
	protected TcTagRawToTrainData trainPrdctData;		
	//this array holds float reps of "sumtorial" of idx vals, used as denominators of ftr vectors so that 
	//arrays of jps of size idx will use this value as denominator, and (idx - jp idx)/denominator as weight value for ftr vec 
	private static float[] ordrWtAraPerSize;
	//this is a vector of all seen mins and maxs for wts for every product. Only used for debugging display of spans of values
	private static TreeMap<Integer, Float> wtMins, wtMaxs, wtDists;		
	
	//types to conduct similarity mapping
	private static int[] prospectTypes_idxs = new int[] {ExDataType.Training.getVal(), ExDataType.Testing.getVal(), ExDataType.Validation.getVal()};

	//color to illustrate map (around bmu) region corresponding to this product - use distance as alpha value
	private int[] prodClr;
		
	public ProductExample(Straff_SOMMapManager _map, TcTagData data) {
		super(_map,ExDataType.Product,data.OID);
		trainPrdctData = new TcTagRawToTrainData(data);	
		initProdBMUMaps();		
	}//ctor
	
	public ProductExample(Straff_SOMMapManager _map,String _OID, String _csvDataStr) {
		super(_map,ExDataType.Product,_OID);
		trainPrdctData = new TcTagRawToTrainData(_csvDataStr);
		initProdBMUMaps();
	}//ctor
	
	private void initProdBMUMaps() {
		prodClr = mapMgr.getRndClr();
		rad = 3.0f;
		prodClr[3]=255;
		allMapNodesDists = new TreeMap[numFtrCompVals];
		for (Integer i=0; i<numFtrCompVals;++i) {			allMapNodesDists[i] = new TreeMap<Double,ArrayList<SOMMapNode>>();		}
	}	
	
	//Only used for products since products extend over non-exclusive zones of the map
	//distMeasType : "AllFtrs" : looks at all features for distances; "SharedFtrs" : looks at only features that are non-zero in the product example
	public void setMapNodesStruct(int mapNodeIDX, TreeMap<Double, ArrayList<SOMMapNode>> mapNodes) {
		allMapNodesDists[mapNodeIDX] =  mapNodes;
	}
	
	//call this before any data loading that will over-write the existing product examples is performed
	public static void initAllStaticProdData() {
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
	protected void setIsTrainingDataIDX_Priv() { msgObj.dispMessage("ProductExample","setIsTrainingDataIDX_Priv","Calling inappropriate setIsTrainingDataIDX_Priv for ProductExample - should never have training index set.", MsgCodes.warning2);	}//products are never going to be training examples

	@Override
	public void finalizeBuildBeforeFtrCalc() {		allProdJPs = trainPrdctData.getAllJpsInData();	}
	
	public HashSet<Tuple<Integer, Integer>> getSetOfAllJpgJpData() {
		HashSet<Tuple<Integer,Integer>> res = trainPrdctData.getAllJpgJpsInData();
		return res;
	}//getSetOfAllJpgJpData

	@Override
	//called after all features of this kind of object are built
	public void postFtrVecBuild() {}
	
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

	//this will return the training label of this example - all jps
	//the training label corresponds to a tag or a class referring to the data that can be assigned to a map node - a vote about the bmu from this example
	//if not training data then no label will exist;  might not exist if it is training data either, if fully unsupervised
	//NOTE!!!! this would only be used by this product if this product was being used to train a map
	public TreeMap<Integer,Integer> getTrainingLabels() {	
		TreeMap<Integer,Integer> res = new TreeMap<Integer,Integer>();
		for(Integer jp : allProdJPs) {		res.put(jp, 1);	}
		return res;
	}
	
	//required info for this example to build feature data  - this is ignored. these objects can be rebuilt on demand.
	@Override
	public String getRawDescrForCSV() {
		String res = ""+OID+","+allProdJPs.size()+",";
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
//			Float getVal = wtMins.get(idx);
//			if(getVal == null) {getVal = 0.0f;}
//			wtMins.put(idx, (val<getVal) ? val : getVal);
		
		Float getVal = wtMaxs.get(idx);
		if(getVal == null) {getVal = -100000000.0f;}
		wtMaxs.put(idx,(val>getVal) ? val : getVal);	
		wtDists.put(idx, wtMaxs.get(idx)- wtMins.get(idx));		
	}//setFtrMinMax

	
	//draw all map nodes this product exerts influence on, with color alpha reflecting inverse distance, above threshold value set when nodesToDraw map was built
	public void drawProdMapExtent(my_procApplet p, int distType, int numProds, double _maxDist) {
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
	private static double distToConf(double _dist, double _maxDist) { 	return (_maxDist - _dist)/_maxDist;	}
	
	//return a map of all map nodes as keys and their Confidences as values to this product - inv
	public HashMap<SOMMapNode, Double> getMapNodeConf(int distType, double _maxDist) {
		NavigableMap<Double, ArrayList<SOMMapNode>> subMap = allMapNodesDists[distType].headMap(_maxDist, true);
		HashMap<SOMMapNode, Double> resMap = new HashMap<SOMMapNode, Double>();
		for (Double dist : subMap.keySet()) {
			double conf = distToConf(dist, _maxDist);
			ArrayList<SOMMapNode> mapNodeList = subMap.get(dist);		
			for (SOMMapNode n : mapNodeList) {		resMap.put(n, conf);		}			
		}
		return resMap;
	}//getAllMapNodDists
	
	//build a map keyed by distance to each map node of arrays of maps of arrays of that mapnode's examples, each array keyed by their distance:
	//Outer map is keyed by distance from prod to map node, value is array (1 per map node @ dist) of maps keyed by distance from example to map node, value is array of all examples at that distance)
	//x : this product's distance to map node ; y : each example's distance to map node
	//based on their bmu mappings, to map nodes within _maxDist threshold of this node
	//subMap holds all map nodes within _maxDist of this node
	private TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>> getExamplesNearThisProd(NavigableMap<Double, ArrayList<SOMMapNode>> subMap, int typeIDX) {
		TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>> nodesNearProd = new TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>>();
		HashMap<SOMExample, Double> tmpMapOfNodes;
		for (Double dist : subMap.keySet()) {								//get all map nodes of certain distance from this node; - this dist is distance from this node to map node
			ArrayList<SOMMapNode> mapNodeList = subMap.get(dist);				//all ara of all map nodes of specified distance from this product node
			TreeMap<Double, ArrayList<SOMExample>> tmpMapOfArrays = nodesNearProd.get(dist);			
			if (tmpMapOfArrays==null) {tmpMapOfArrays = new TreeMap<Double, ArrayList<SOMExample>>();}				
			for (SOMMapNode n : mapNodeList) {									//for each map node get all examples of ExDataType that consider that map node BMU
				tmpMapOfNodes = n.getAllExsAndDist(typeIDX);					//each prospect example and it's distance from the bmu map node				
				for(SOMExample exN : tmpMapOfNodes.keySet()) {		//for each example that treats map node as bmu
					Double distFromBMU = tmpMapOfNodes.get(exN);
					ArrayList<SOMExample> exsAtDistFromMapNode = tmpMapOfArrays.get(distFromBMU);
					if (exsAtDistFromMapNode==null) {exsAtDistFromMapNode = new ArrayList<SOMExample>();}
					exsAtDistFromMapNode.add(exN);
					tmpMapOfArrays.put(distFromBMU,exsAtDistFromMapNode);
				}//for each node at this map node
			}//for each map node at distance dist			
			nodesNearProd.put(dist, tmpMapOfArrays);
		}
		return nodesNearProd;
	}//getExamplesNearThisProd	
	
	//returns a map of dists from this product as keys and values as maps of distance of examples from their bmus as keys and example itself as value
	//primary key is distance to a map node from this node, map holds distance-keyed lists of examples from their bmus.  
	//multiple map nodes may lie on same distance from this node but example will only have 1 bmu
	private TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>> getAllExamplesNearThisProd(int distType, double _maxDist) {
		NavigableMap<Double, ArrayList<SOMMapNode>> subMap = allMapNodesDists[distType].headMap(_maxDist, true);		
		TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>> allNodesAndDists = new TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>>();		
		TreeMap<Double, ArrayList<SOMExample>> srcMapNodeAra, destMapNodeAra;
		ArrayList<SOMExample> srcExAra, destExAra;
		for(int _typeIDX : prospectTypes_idxs) {
			TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>> tmpMapOfAllNodes = getExamplesNearThisProd(subMap, _typeIDX);
			for (Double dist1 : tmpMapOfAllNodes.keySet()) {//this is dist from product to map node source of these examples
				srcMapNodeAra = tmpMapOfAllNodes.get(dist1);				
				destMapNodeAra = allNodesAndDists.get(dist1);
				if(destMapNodeAra==null) {destMapNodeAra = new TreeMap<Double, ArrayList<SOMExample>>();}
				for (Double dist2 : srcMapNodeAra.keySet()) {
					srcExAra = srcMapNodeAra.get(dist2);
					destExAra = destMapNodeAra.get(dist2);
					if(destExAra==null) {destExAra = new ArrayList<SOMExample>(); }
					destExAra.addAll(srcExAra);
					destMapNodeAra.put(dist2, destExAra);
				}
				allNodesAndDists.put(dist1, destMapNodeAra);
			}		
		}//per type
		return allNodesAndDists;		
	}//getExListNearThisProd
	
	//get string array representation of this single product - built on demand
	public String[] getAllExmplsPerProdStrAra(int distType,double _maxDist) {
		ArrayList<String> resAra = new ArrayList<String>();
		TreeMap<Double, ArrayList<SOMExample>> exmplsAtDist;		
		String ttlStr = "Product ID : " + this.OID + " # of JPs covered : " + allProdJPs.size()+" : ";
		for (Integer jp : allProdJPs) {	ttlStr += "" + jp + " : " + jpJpgMon.getJPNameFromJP(jp) + ", ";	}		
		resAra.add(ttlStr);
		resAra.add("OID,Confidence at Map Node,Error at Map Node");
		TreeMap<Double, TreeMap<Double, ArrayList<SOMExample>>> allNodesAndConfs = getAllExamplesNearThisProd(distType, _maxDist);
		String confStr, dist2BMUStr;
		for(Double dist : allNodesAndConfs.keySet()) { 
			exmplsAtDist = allNodesAndConfs.get(dist);
			confStr = String.format("%.6f",distToConf(dist, _maxDist));
			for(Double dist2BMU : exmplsAtDist.keySet()){		
				ArrayList<SOMExample> exsAtDistFromBMU = exmplsAtDist.get(dist2BMU);
				dist2BMUStr = String.format("%.6f",dist2BMU);
				for (SOMExample exN : exsAtDistFromBMU) {				
					resAra.add("" + exN.OID + "," + confStr + ","+dist2BMUStr);	
				}//for all examples at bmu
			}//for all bmus at certain distance
		}//for all distances/all bmus
		return resAra.toArray(new String[0]);	
	}//getBestExsStrAra	
	
	//take loaded data and convert to output data
	@Override
	protected void buildFeaturesMap() {
		ftrMaps[ftrMapTypeKey].clear();	
		//order map gives order value of each jp - provide multiplier for higher vs lower priority jps
		TreeMap<Integer, Integer> orderMap = trainPrdctData.getJPOrderMap();
		int numJPs = orderMap.size();
		//verify # of jps as expected
		if (numJPs != allNonZeroFtrIDXs.size()) {	
			msgObj.dispMessage("ProductExample", "buildFeaturesMap", "Problem with size of expected jps from trainPrdctData vs. allJPFtrIDXs : trainPrdctData says # jps == " +numJPs + " | allJPFtrIDXs.size() == " +allNonZeroFtrIDXs.size(), MsgCodes.warning2);
		}
		if(numJPs == 1) {
			Integer ftrIDX = allNonZeroFtrIDXs.get(0);
			ftrMaps[ftrMapTypeKey].put(ftrIDX, 1.0f);
			setFtrMinMax(ftrIDX, 1.0f);
			this.ftrVecMag = 1.0f;			
		} else {//more than 1 jp for this product
			float val, ttlVal = 0.0f, denom = ordrWtAraPerSize[numJPs];
			for (Integer IDX : allNonZeroFtrIDXs) {
				Integer jp = jpJpgMon.getFtrJpByIdx(IDX);
				val = (numJPs - orderMap.get(jp))/denom;
				ftrMaps[ftrMapTypeKey].put(IDX,val);
				setFtrMinMax(IDX, val);
				ttlVal += val;
			}	
			this.ftrVecMag = (float) Math.sqrt(ttlVal);
		}
	}//buildFeaturesMap
	

//	/**
//	 * this function is not relevant for product example
//	 * @param _MapNodesByFtr
//	 * @return
//	 */
//	@Override
//	protected final void buildMapNodeDistsFromGroupings(TreeMap<Double, ArrayList<SOMMapNode>> _mapNodesByDist,TreeMap<Integer, HashSet<SOMMapNode>> _MapNodesByFtr){}

	@Override
	protected void buildStdFtrsMap() {
		ftrMaps[stdFtrMapTypeKey].clear();
		for (Integer IDX : ftrMaps[ftrMapTypeKey].keySet()) {ftrMaps[stdFtrMapTypeKey].put(IDX,ftrMaps[ftrMapTypeKey].get(IDX));}//since features are all weighted to sum to 1, can expect ftrmap == strdizedmap
		setFlag(stdFtrsBuiltIDX,true);
		//set comparator == to feature vectors
		buildCompFtrVector(0.0f);
	}//buildStdFtrsMap

	@Override
	public String toString(){
		String res = "Example OID# : "+OID;
		if(null!=mapLoc){res+="Location on SOM map : " + mapLoc.toStrBrf();}
		if (mapMgr.getNumTrainFtrs() > 0) {			res += "\n\tFeature Val(s) : " + dispFtrMapVals(ftrMaps[ftrMapTypeKey]);		} 
		else {								res += "No Features for this product example";		}
		return res;
	}

}//class productExample