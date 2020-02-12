package strafford_SOM_PKG.straff_SOM_Examples;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import base_Math_Objects.vectorObjs.tuples.Tuple;
import base_SOM_Objects.SOM_MapManager;
import base_SOM_Objects.som_examples.SOM_Example;
import base_SOM_Objects.som_examples.SOM_FtrDataType;
import base_SOM_Objects.som_examples.SOM_MapNode;
import base_SOM_Objects.som_segments.SOM_MapNodeCategorySegMgr;
import base_SOM_Objects.som_segments.SOM_MapNodeClassSegMgr;
import base_SOM_Objects.som_segments.SOM_MapNodeSegMgr;
import base_SOM_Objects.som_segments.segments.SOM_MappedSegment;
import base_UI_Objects.my_procApplet;
import strafford_SOM_PKG.straff_Features.Straff_MonitorJpJpgrp;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.Straff_ProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

////this class represents a particular node in the SOM map, with specific customizations for strafford data
public class Straff_SOMMapNode extends SOM_MapNode{
	//reference to jp-jpg mapping/managing object
	protected static Straff_MonitorJpJpgrp jpJpgMon;

	//this manages the segment functionality for the class segments
	protected SOM_MapNodeSegMgr nonProdJPSegManager;
	
	//this manages the segment functionality for the category segments, which are collections of similar classes in a hierarchy
	protected SOM_MapNodeSegMgr nonProdJPGroupSegManager;	

	public Straff_SOMMapNode(SOM_MapManager _map, Tuple<Integer,Integer> _mapNode, SOM_FtrDataType _ftrTypeUsedToTrain, float[] _ftrs) {		super(_map, _mapNode, _ftrTypeUsedToTrain, _ftrs);	}//ctor w/float ftrs
	//build a map node from a string array of features
	public Straff_SOMMapNode(SOM_MapManager _map,Tuple<Integer,Integer> _mapNode, SOM_FtrDataType _ftrTypeUsedToTrain, String[] _strftrs) {	super(_map, _mapNode,_ftrTypeUsedToTrain, _strftrs);  }//ctor w/str ftrs	

	@Override
	/**
	 * this will map feature values to some representation of the underlying feature 
	 * description - this is specific to underlying data and is called from base class initMapNode
	 */
	protected void _initDataFtrMappings() {	
		jpJpgMon = ((Straff_SOMMapManager)mapMgr).jpJpgrpMon;
		
		//build structure that holds counts of classes mapped to this node
		nonProdJPSegManager = new SOM_MapNodeClassSegMgr(this);		
		//build structure that holds counts of categories mapped to this node (category is a collection of similar classes)
		nonProdJPGroupSegManager = new SOM_MapNodeCategorySegMgr(this);	

//		//build essential components of feature vector - moved to base class map node init code
//		buildAllNonZeroFtrIDXs();
//		buildNormFtrData();//once ftr map is built can normalize easily
//		_buildFeatureVectorEnd_Priv();
	}//_initDataFtrMappings
	//nearestMapNode.mapNodeCoord.toString() + " 
	@Override
	//manage instancing map node handling - specifically, handle using 2ndary features as node markers (like a product tag or a class)
	//in other words, this takes the passed example's "class" in our case all the order jps, and assigns them to this node
	protected void addTrainingExToBMUs_Priv(double dist, SOM_Example ex) {
		//following promoted to base map node class
//		//keyed by tuple of category/class (category == null if not used), value is count of class in example
//		TreeMap<Tuple<Integer, Integer>, Integer> trainExCatClassCounts = ex.getCatClassCountsForExample();
//		//for each category-class used in training example, assign segments
//
//		for (Tuple<Integer, Integer> catClass : trainExCatClassCounts.keySet()) {
//			Integer cat = catClass.x, cls = catClass.y;
//			//# of examples of class in training example
//			Float numClassInEx = 1.0f*trainExCatClassCounts.get(catClass);	
//			Float newCount = getClassSegManager().addSegDataFromTrainingEx(new Integer[] {cls}, numClassInEx, getClassSegName(), getClassSegDesc());
//			//newcount includes existing counts in this node, so needs to be used to map to category as well
//			if(null!=cat) {
//				Float dummy = getCategorySegManager().addSegDataFromTrainingEx(new Integer[] {cat,cls}, newCount, getCategorySegName(), getCategorySegDesc());
//			}
//		}		
		//now build structure for non-prod jps and jpgroups-based segments
		HashSet<Tuple<Integer,Integer>> nonProdJpgJps = ((Straff_ProspectExample) ex).getNonProdJpgJps();
		//if(nonProdJpgJps.size() > 0) {System.out.println("# of nonprodjpgpjps for node : " + ex.OID+" | "+nonProdJpgJps.size());}
		for(Tuple<Integer, Integer> npJpgJp :nonProdJpgJps) {
			Integer npJpg = npJpgJp.x, npJp = npJpgJp.y;
			//System.out.println("\t nonprodjpgpjps jp : " +npJp+" | non prod jpgroup :  "+npJpg);
			Float newCount = nonProdJPSegManager.addSegDataFromTrainingEx(new Integer[] {npJp}, 1.0f, "_NonProd_JPCount_JP_", "Non Prod JP present in examples :");
			//Float dummy = 
			nonProdJPGroupSegManager.addSegDataFromTrainingEx(new Integer[] {npJpg,npJp}, newCount, "_NonProd_JPGroupCount_JPG_", "Non Prod JPGroup present in examples :");
		}
		
	}//addTrainingExToBMUs_Priv	
	/**
	 * get salient name prefix for class segment for the objects mapped to this bmu
	 * @return
	 */
	@Override
	public final String getClassSegName() {return "_JPCount_JP_";}
	/**
	 * get salient descriptions for class segment for the objects mapped to this bmu
	 * @return
	 */
	@Override
	public final String getClassSegDesc() {return "JP Orders present for jp :";}
	
	/**
	 * get salient name prefix for category segment for the objects mapped to this bmu
	 * @return
	 */
	@Override
	public final String getCategorySegName() {return "_JPGroupCount_JPG_";}
	/**
	 * get salient descriptions for category segment for the objects mapped to this bmu
	 * @return
	 */
	@Override
	public final String getCategorySegDesc() {return "JPGroup Orders present for jpg :";}
	
	

	@Override
	//assign relevant info to this map node from neighboring map node(s) to cover for this node not having any training examples assigned
	//only copies ex's mappings, which might not be appropriate
	protected void addMapNodeExToBMUs_Priv(double dist, SOM_MapNode ex) {//copy structure 		
		getClassSegManager().copySegDataFromBMUMapNode(dist, ex.getMappedClassCounts(), "_JPCount_JP_", "JP Orders present for jp :");
		getCategorySegManager().copySegDataFromBMUMapNode(dist, ex.getMappedCategoryCounts(),"_JPGroupCount_JPG_","JPGroup Orders present for jpg :");
		nonProdJPSegManager.copySegDataFromBMUMapNode(dist, ((Straff_SOMMapNode) ex).getMappedNonProdJPCounts(), "_NonProd_JPCount_JP_", "Non Prod JP present in examples :");
		nonProdJPGroupSegManager.copySegDataFromBMUMapNode(dist, ((Straff_SOMMapNode) ex).getMappedNonProdJPGroupCounts(), "_NonProd_JPGroupCount_JPG_", "Non Prod JPGroup present in examples :");

	}//addMapNodeExToBMUs_Priv

	///////////////////
	// non-prod-jp-based segment data
	
	public final void clearNonProdJPSeg() {	 										nonProdJPSegManager.clearAllSegData();}
	public final void setNonProdJPSeg(Integer _cls, SOM_MappedSegment _clsSeg) {		nonProdJPSegManager.setSeg(_cls, _clsSeg);}	
	public final SOM_MappedSegment getNonProdJPSegment(Integer _cls) {					return nonProdJPSegManager.getSegment(_cls);	}	
	public final int getNonProdJPSegClrAsInt(Integer _cls) {						return nonProdJPSegManager.getSegClrAsInt(_cls);}		
	//for passed -class (not idx)- give this node's probability
	public final float getNonProdJPProb(Integer _cls) {								return nonProdJPSegManager.getSegProb(_cls);}
	public final Set<Integer> getNonProdJPSegIDs(){									return nonProdJPSegManager.getSegIDs();}	
	public final TreeMap<Integer, Float> getNonProdJP_SegDataRatio(){				return nonProdJPSegManager.getSegDataRatio();}
	public final Float getTtlNumMappedNonProdJpInstances() { 						return nonProdJPSegManager.getTtlNumMappedInstances();}
	//return map of classes mapped to counts present
	@SuppressWarnings("unchecked")
	public final TreeMap<Integer, Float> getMappedNonProdJPCounts() {				return nonProdJPSegManager.getMappedCounts();	}	
	public final Float getMappedNonProdJPCountAtSeg(Integer segID) {						return nonProdJPSegManager.getMappedCountAtSeg(segID);}
	protected SOM_MapNodeSegMgr getNonProdJpSegManager() {							return nonProdJPSegManager;}
	public String getNonProdJPSegment_CSVStr() {									return nonProdJPSegManager.getSegDataDescStringForNode();}
	protected final String getNonProdJpSegment_CSVStr_Hdr() {								return nonProdJPSegManager.getSegDataDescStrForNode_Hdr();}

	///////////////////
	// non-prod-jpgroup -based segment data

	public final void clearNonProdJpGroupSeg() {											nonProdJPGroupSegManager.clearAllSegData();	}
	public final void setNonProdJpGroupSeg(Integer _cat, SOM_MappedSegment _catSeg) {		nonProdJPGroupSegManager.setSeg(_cat, _catSeg);}	
	public final SOM_MappedSegment getNonProdJpGroupSegment(Integer _cat) {						return nonProdJPGroupSegManager.getSegment(_cat);}
	public final int getNonProdJpGroupSegClrAsInt(Integer _cat) {							return nonProdJPGroupSegManager.getSegClrAsInt(_cat);}		
	//for passed -Category label (not cat idx)- give this node's probability	
	public final float getNonProdJpGroupProb(Integer _cat) {                       			return nonProdJPGroupSegManager.getSegProb(_cat);}            
	public final Set<Integer> getNonProdJpGroupSegIDs(){		                      		return nonProdJPGroupSegManager.getSegIDs();}	             
	public final TreeMap<Integer, Float> getNonProdJpGroup_SegDataRatio(){          		return nonProdJPGroupSegManager.getSegDataRatio();}           
	public final Float getTtlNumMappedNonProdJpGroupInstances() {                   		return nonProdJPGroupSegManager.getTtlNumMappedInstances();} 	
	//return map of categories to classes within category and counts of each class
	@SuppressWarnings("unchecked")
	public final TreeMap<Integer, TreeMap<Integer, Float>> getMappedNonProdJPGroupCounts(){	return nonProdJPGroupSegManager.getMappedCounts();}
	public final Float getMappedNonProdJPGroupCountAtSeg(Integer segID) {						return nonProdJPGroupSegManager.getMappedCountAtSeg(segID);}
	protected SOM_MapNodeSegMgr getNonProdJpGroupSegManager() {								return nonProdJPGroupSegManager;}
	public String getNonProdJpGroupSegment_CSVStr() {											return nonProdJPGroupSegManager.getSegDataDescStringForNode();}
	protected final String getNonProdJpGroupSegment_CSVStr_Hdr() {								return nonProdJPGroupSegManager.getSegDataDescStrForNode_Hdr();}
	
	
	/**
	 * get per-bmu segment descriptor, with key being either "class","category", "ftrwt" or something managed by instancing class
	 * @param segmentType "class","category", "ftrwt" or something managed by instancing class
	 * @return descriptor of this map node's full weight profile for the passed segment
	 */
	@Override
	protected final String getSegment_CSVStr_Indiv(String segmentType) {
		switch(segmentType.toLowerCase()) {
		default 		: {		return "";}//unknown type of segment returns empty string
		}
	}
	//descriptor string for any instance-specific segments
	@Override
	protected final String getSegment_Hdr_CSVStr_Indiv(String segmentType) {
		switch(segmentType.toLowerCase()) {
		default 		: {		return "";}//unknown type of segment returns empty string
		}
	}
	@Override	
	protected final String getFtrWtSegment_CSVStr_Indiv(TreeMap<Float, ArrayList<String>> mapNodeProbs) {//ftrMaps[normFtrMapTypeKey].get(ftrIDX)
		String res = "" + mapNodeCoord.toCSVString()+",";
		for (Float prob : mapNodeProbs.keySet()) {
			ArrayList<String> valsAtProb = mapNodeProbs.get(prob);
			String probString = ""+String.format("%1.7g", prob)+",";			
			for(String segStr : valsAtProb) {	
				Integer jpIDX = Integer.parseInt(segStr);
				res +=probString + segStr + "," + jpJpgMon.getFtrJpByIdx(jpIDX)+ "," + jpJpgMon.getFtrJpStrByIdx_Short(jpIDX)+ ",";
			}
		}			
		return res;	
	}
	@Override
	protected final String getFtrWtSegment_CSVStr_Hdr() {return "Map Node Loc,Probability,Ftr IDX, Prod JP, JP Name";}

	
	//////////////////////////////////////
	// end non-prod segments	
	
	
	//draw class pop segment contribution 
	public final void drawMeNonProdJPSegClr(my_procApplet p, Integer cls) {nonProdJPSegManager.drawMeSegClr(p,  cls);	}//drawMeFtrWtSegClr
	
	//draw category segment contribution - collection of classes
	public final void drawMeNonProdJpGroupSegClr(my_procApplet p, Integer category) { nonProdJPGroupSegManager.drawMeSegClr(p, category);}//drawMeFtrWtSegClr
	
	//by here ftrs for this map node have been built
	@Override
	protected void buildAllNonZeroFtrIDXs() {
		allNonZeroFtrIDXs = new ArrayList<Integer>();
		for(Integer idx : ftrMaps[rawftrMapTypeKey].keySet()) {		allNonZeroFtrIDXs.add(idx);	}
	}//buildAllNonZeroFtrIDXs
	
	//called after the features and normed features of this example are built
	@Override
	protected void _buildFeatureVectorEnd_Priv() {}
	@Override
	//called after all features of this kind of object are built
	public void postFtrVecBuild() {}

	/**
	 * return the appropriate string value for the dense training data - should be numeric key value to save in lrn or csv dense file
	 * Strafford will always use sparse data so this doesn't matter
	 * @return
	 */
	@Override
	protected String getDenseTrainDataKey() {
		return OID;
	}
	@Override
	protected String dispFtrVal(TreeMap<Integer, Float> ftrs, Integer i) {
		Float ftr = ftrs.get(i);
		int jp = jpJpgMon.getFtrJpByIdx(i);
		return "jp : " + jp + " | idx : " + i + " | val : " + String.format("%1.4g",  ftr) + " || ";
	}


}//SOMMapNodeExample