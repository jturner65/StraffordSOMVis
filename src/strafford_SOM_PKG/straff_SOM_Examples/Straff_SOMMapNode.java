package strafford_SOM_PKG.straff_SOM_Examples;

import java.util.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_segments.SOM_CategorySegMgr;
import base_SOM_Objects.som_segments.SOM_ClassSegMgr;
import base_SOM_Objects.som_segments.SOM_SegmentManager;
import base_SOM_Objects.som_segments.segmentData.SOM_MapNodeSegmentData;
import base_SOM_Objects.som_segments.segments.SOMMapSegment;
import base_SOM_Objects.som_segments.segments.SOM_CategorySegment;
import base_SOM_Objects.som_segments.segments.SOM_ClassSegment;
import base_SOM_Objects.som_segments.segments.SOM_FtrWtSegment;
import base_UI_Objects.my_procApplet;
import base_Utils_Objects.*;
import strafford_SOM_PKG.straff_Features.MonitorJpJpgrp;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.CustProspectExample;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.ProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

////this class represents a particular node in the SOM map, with specific customizations for strafford data
public class Straff_SOMMapNode extends SOMMapNode{
	//reference to jp-jpg mapping/managing object
	protected static MonitorJpJpgrp jpJpgMon;

	//this manages the segment functionality for the class segments
	protected SOM_SegmentManager nonProdJPSegManager;
	
	//this manages the segment functionality for the category segments, which are collections of similar classes in a hierarchy
	protected SOM_SegmentManager nonProdJPGroupSegManager;	

	public Straff_SOMMapNode(SOMMapManager _map, Tuple<Integer,Integer> _mapNode, float[] _ftrs) {		super(_map, _mapNode, _ftrs);	}//ctor w/float ftrs
	//build a map node from a string array of features
	public Straff_SOMMapNode(SOMMapManager _map,Tuple<Integer,Integer> _mapNode, String[] _strftrs) {	super(_map, _mapNode, _strftrs);  }//ctor w/str ftrs	

	@Override
	/**
	 * this will map feature values to some representation of the underlying feature 
	 * description - this is specific to underlying data and is called from base class initMapNode
	 */
	protected void _initDataFtrMappings() {	
		jpJpgMon = ((Straff_SOMMapManager)mapMgr).jpJpgrpMon;
		
		//build structure that holds counts of classes mapped to this node
		nonProdJPSegManager = new SOM_ClassSegMgr(this);		
		//build structure that holds counts of categories mapped to this node (category is a collection of similar classes)
		nonProdJPGroupSegManager = new SOM_CategorySegMgr(this);	

		//build essential components of feature vector
		buildAllNonZeroFtrIDXs();
		buildNormFtrData();//once ftr map is built can normalize easily
		_buildFeatureVectorEnd_Priv();
	}//_initDataFtrMappings
	//nearestMapNode.mapNodeCoord.toString() + " 
	@Override
	//manage instancing map node handling - specifically, handle using 2ndary features as node markers (like a product tag or a class)
	//in other words, this takes the passed example's "class" in our case all the order jps, and assigns them to this node
	protected void addTrainingExToBMUs_Priv(double dist, SOMExample ex) {
		TreeMap<Tuple<Integer, Integer>, Integer> trainExOrderCounts = ((CustProspectExample)ex).getOrderCountsForExample();
		//for each jpg-jp used in training example, assign 
		//TreeMap<Integer, Integer> jpCountsAtJpGrp, npJpCountsAtJpGrp;
		for (Tuple<Integer, Integer> jpgJp : trainExOrderCounts.keySet()) {
			Integer jpg = jpgJp.x, jp = jpgJp.y;
			Float numOrders = 1.0f*trainExOrderCounts.get(jpgJp);	
			Float newCount = getClassSegManager().addSegDataFromTrainingEx(new Integer[] {jp}, numOrders, "_JPCount_JP_", "JP Orders present for jp :");
			//newcount includes existing counts in this node, so needs to be used to map to category as well
			Float dummy = getCategorySegManager().addSegDataFromTrainingEx(new Integer[] {jpg,jp}, newCount, "_JPGroupCount_JPG_", "JPGroup Orders present for jpg :");
		}		
		//now build structure for non-prod jps and jpgroups-based segments
		HashSet<Tuple<Integer,Integer>> nonProdJpgJps = ((ProspectExample) ex).getNonProdJpgJps();
		for(Tuple<Integer, Integer> npJpgJp :nonProdJpgJps) {
			Integer npJpg = npJpgJp.x, npJp = npJpgJp.y;
			Float newCount = nonProdJPSegManager.addSegDataFromTrainingEx(new Integer[] {npJpg}, 1.0f, "_NonProd_JPCount_JP_", "Non Prod JP present in examples :");
			Float dummy = nonProdJPGroupSegManager.addSegDataFromTrainingEx(new Integer[] {npJpg,npJp}, newCount, "_NonProd_JPGroupCount_JPG_", "Non Prod JPGroup present in examples :");
		}
		
	}//addTrainingExToBMUs_Priv	

	@Override
	//assign relevant info to this map node from neighboring map node(s) to cover for this node not having any training examples assigned
	//only copies ex's mappings, which might not be appropriate
	protected void addMapNodeExToBMUs_Priv(double dist, SOMMapNode ex) {//copy structure 		
		getClassSegManager().copySegDataFromBMUMapNode(dist, ex.getMappedClassCounts(), "_JPCount_JP_", "JP Orders present for jp :");
		getCategorySegManager().copySegDataFromBMUMapNode(dist, ex.getMappedCategoryCounts(),"_JPGroupCount_JPG_","JPGroup Orders present for jpg :");
		nonProdJPSegManager.copySegDataFromBMUMapNode(dist, ((Straff_SOMMapNode) ex).getMappedNonProdJPCounts(), "_NonProd_JPCount_JP_", "Non Prod JP present in examples :");
		nonProdJPGroupSegManager.copySegDataFromBMUMapNode(dist, ((Straff_SOMMapNode) ex).getMappedNonProdJPGroupCounts(), "_NonProd_JPGroupCount_JPG_", "Non Prod JPGroup present in examples :");

	}//addMapNodeExToBMUs_Priv


	///////////////////
	// non-prod-jp-based segment data
	
	public final void clearNonProdJPSeg() {	 										nonProdJPSegManager.clearAllSegData();}//clearClassSeg()
	public final void setNonProdJPSeg(Integer _cls, SOM_ClassSegment _clsSeg) {		nonProdJPSegManager.setSeg(_cls, _clsSeg);}	
	public final SOMMapSegment getNonProdJPSegment(Integer _cls) {					return nonProdJPSegManager.getSegment(_cls);	}	
	public final int getNonProdJPSegClrAsInt(Integer _cls) {						return nonProdJPSegManager.getSegClrAsInt(_cls);}		
	//for passed -class (not idx)- give this node's probability
	public final float getNonProdJPProb(Integer _cls) {								return nonProdJPSegManager.getSegProb(_cls);}
	public final Set<Integer> getNonProdJPSegIDs(){									return nonProdJPSegManager.getSegIDs();}	
	public final TreeMap<Integer, Float> getNonProdJP_SegDataRatio(){				return nonProdJPSegManager.getSegDataRatio();}
	public final Float getTtlNumMappedNonProdJpInstances() { 						return nonProdJPSegManager.getTtlNumMappedInstances();}
	//return map of classes mapped to counts present
	@SuppressWarnings("unchecked")
	public final TreeMap<Integer, Integer> getMappedNonProdJPCounts() {				return nonProdJPSegManager.getMappedCounts();	}	

	
	///////////////////
	// non-prod-jpgroup -based segment data

	///////////////////
	// category order-based segment data
	
	public final void clearNonProdJpGroupSeg() {											nonProdJPGroupSegManager.clearAllSegData();	}
	public final void setNonProdJpGroupSeg(Integer _cat, SOM_CategorySegment _catSeg) {		nonProdJPGroupSegManager.setSeg(_cat, _catSeg);}	
	public final SOMMapSegment getNonProdJpGroupSegment(Integer _cat) {						return nonProdJPGroupSegManager.getSegment(_cat);}
	public final int getNonProdJpGroupSegClrAsInt(Integer _cat) {							return nonProdJPGroupSegManager.getSegClrAsInt(_cat);}		
	//for passed -Category label (not cat idx)- give this node's probability	
	public final float getNonProdJpGroupProb(Integer _cat) {                       			return nonProdJPGroupSegManager.getSegProb(_cat);}            
	public final Set<Integer> getNonProdJpGroupSegIDs(){		                      		return nonProdJPGroupSegManager.getSegIDs();}	             
	public final TreeMap<Integer, Float> getNonProdJpGroup_SegDataRatio(){          		return nonProdJPGroupSegManager.getSegDataRatio();}           
	public final Float getTtlNumMappedNonProdJpGroupInstances() {                   		return nonProdJPGroupSegManager.getTtlNumMappedInstances();} 	
	//return map of categories to classes within category and counts of each class
	@SuppressWarnings("unchecked")
	public final TreeMap<Integer, TreeMap<Integer, Integer>> getMappedNonProdJPGroupCounts(){	return nonProdJPGroupSegManager.getMappedCounts();}
	
	
	//////////////////////////////////////
	// end non-prod segments	
	
	
	//draw class pop segment contribution 
	public final void drawMeNonProdJPSegClr(my_procApplet p, Integer cls) {nonProdJPSegManager.drawMeSegClr(p,  cls);	}//drawMeFtrWtSegClr
	
	//draw category segment contribution - collection of classes
	public final void drawMeNonProdJpGroupSegClr(my_procApplet p, Integer category) { nonProdJPGroupSegManager.drawMeSegClr(p, category);}//drawMeFtrWtSegClr
	
	
	@Override
	//called by SOMDataLoader - these are standardized based on data mins and diffs seen in -map nodes- feature data, not in training data
	public void buildStdFtrsMapFromFtrData_MapNode(float[] minsAra, float[] diffsAra) {
		clearFtrMap(stdFtrMapTypeKey);//ftrMaps[stdFtrMapTypeKey].clear();
		if (ftrMaps[ftrMapTypeKey].size() > 0) {
			for(Integer destIDX : ftrMaps[ftrMapTypeKey].keySet()) {
				Float lb = minsAra[destIDX], diff = diffsAra[destIDX];
				float val = 0.0f;
				if (diff==0) {//same min and max
					if (lb > 0) {	val = 1.0f;}//only a single value same min and max-> set feature value to 1.0
					else {val= 0.0f;}
				} else {				val = (ftrMaps[ftrMapTypeKey].get(destIDX)-lb)/diff;				}
				ftrMaps[stdFtrMapTypeKey].put(destIDX,val);
			}//for each jp
		}
		//just set the comparator vector array == to the actual feature vector array
		buildCompFtrVector(0.0f);
		setFlag(stdFtrsBuiltIDX,true);
	}//buildStdFtrsMap_MapNode
	
	//by here ftrs for this map node have been built
	@Override
	protected void buildAllNonZeroFtrIDXs() {
		allNonZeroFtrIDXs = new ArrayList<Integer>();
		for(Integer idx : ftrMaps[ftrMapTypeKey].keySet()) {		allNonZeroFtrIDXs.add(idx);	}
	}//buildAllNonZeroFtrIDXs

	@Override
	protected void _buildFeatureVectorEnd_Priv() {}
	@Override
	//called after all features of this kind of object are built
	public void postFtrVecBuild() {}


	@Override
	protected String dispFtrVal(TreeMap<Integer, Float> ftrs, Integer i) {
		Float ftr = ftrs.get(i);
		int jp = jpJpgMon.getFtrJpByIdx(i);
		return "jp : " + jp + " | idx : " + i + " | val : " + String.format("%1.4g",  ftr) + " || ";
	}

}//SOMMapNodeExample