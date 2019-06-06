package strafford_SOM_PKG.straff_SOM_Examples;

import java.util.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_utils.segments.SOMMapSegment;
import base_SOM_Objects.som_utils.segments.SOM_CategorySegment;
import base_SOM_Objects.som_utils.segments.SOM_ClassSegment;
import base_SOM_Objects.som_utils.segments.SOM_FtrWtSegment;
import base_SOM_Objects.som_utils.segments.SOM_MapNodeSegmentData;
import base_UI_Objects.my_procApplet;
import base_Utils_Objects.*;
import strafford_SOM_PKG.straff_Features.MonitorJpJpgrp;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.CustProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

////this class represents a particular node in the SOM map, with specific customizations for strafford data
public class Straff_SOMMapNode extends SOMMapNode{
	//reference to jp-jpg mapping/managing object
	protected static MonitorJpJpgrp jpJpgMon;
	
	//this holds classes and count of all training examples with this class mapped to this node
	protected TreeMap<Integer, Integer> mappedNonProdJPCounts;
	//segment membership manager of class-index-based segments - will have 1 per class present and 1 per category
	//keyed by nonProdJp index
	protected TreeMap<Integer, SOM_MapNodeSegmentData> nonProdJp_SegData;	//segment membership manager of class mapping - key is class
	private TreeMap<Integer, Float> nonProdJp_SegDataRatio;			//this is the ratio of # of a particular class to the total # of classes mapped to this map node - these should of course sum to 1
	private Float ttlNumMappedNonProdJpInstances;							//total # of training/customer example non-prod jp segments mapped to this map node - float to make sure non-int division when consumed
	//this holds category as key, value is classes in that category and counts mapped to the node of that class(subtree)
	protected TreeMap<Integer, TreeMap<Integer, Integer>> mappedNonProdJPGroupCounts;
	//keyed by jpgroup index
	protected TreeMap<Integer, SOM_MapNodeSegmentData> nonProdJpGroup_SegData;	//category is a collection of similar classes
	private TreeMap<Integer, Float> nonProdJpGroup_SegDataRatio;			//this is the ratio of # of a particular category to the total # of categories mapped to this map node - these should of course sum to 1
	private Float ttlNumMappedNonProdJpGroupInstances;							//total # of training example categories mapped to this map node

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
		mappedNonProdJPCounts = new TreeMap<Integer, Integer>();
		//build structure that holds counts of categories mapped to this node (category is a collection of similar classes)
		mappedNonProdJPGroupCounts = new TreeMap<Integer, TreeMap<Integer, Integer>>();
		nonProdJp_SegData = new TreeMap<Integer, SOM_MapNodeSegmentData>();
		nonProdJp_SegDataRatio = new TreeMap<Integer, Float>();
		ttlNumMappedNonProdJpInstances =0.0f;
		nonProdJpGroup_SegData = new TreeMap<Integer, SOM_MapNodeSegmentData>();	
		nonProdJpGroup_SegDataRatio = new TreeMap<Integer, Float>();
		ttlNumMappedNonProdJpGroupInstances = 0.0f;

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
		TreeMap<Integer, Integer> jpCountsAtJpGrp;
		for (Tuple<Integer, Integer> jpgJp : trainExOrderCounts.keySet()) {
			Integer jpg = jpgJp.x, jp = jpgJp.y;
			Integer jpCount = mappedClassCounts.get(jp);
			//for each jp
			if(null==jpCount) {
				//on initial mapping for this jp, build the jp_SegData object for this jp
				jpCount = 0;
				class_SegData.put(jp, new SOM_MapNodeSegmentData(this, this.OID+"_JPCount_JP_"+jp, "JP Orders present for jp :"+jp));
			}
			++jpCount;
			mappedClassCounts.put(jp, jpCount);
			
			//for each jpgroup
			jpCountsAtJpGrp = mappedCategoryCounts.get(jpg);
			if(null==jpCountsAtJpGrp) {
				jpCountsAtJpGrp = new TreeMap<Integer, Integer>(); 
				//on initial mapping for this jpg, build the jpGroup_SegData object for this jpg
				category_SegData.put(jpg, new SOM_MapNodeSegmentData(this, this.OID+"_JPGroupCount_JPG_"+jpg, "JPGroup Orders present for jpg :"+jpg));	
			}
			jpCountsAtJpGrp.put(jp, jpCount);
			mappedCategoryCounts.put(jpg, jpCountsAtJpGrp);
		}		
		//now build structure for non-prod jps and jpgroups-based segments
		
	}//addTrainingExToBMUs_Priv	

	@Override
	//assign relevant info to this map node from neighboring map node(s) to cover for this node not having any training examples assigned
	//only copies ex's mappings, which might not be appropriate
	protected void addMapNodeExToBMUs_Priv(double dist, SOMMapNode ex) {//copy structure 
		TreeMap<Integer, Integer> otrMappedJPCounts = ex.getMappedClassCounts(),
				otrJPCounts,
				Jpg_jpCounts;
		TreeMap<Integer, TreeMap<Integer, Integer>> otrMappedJPGroupCounts = ex.getMappedCategoryCounts();
		for(Integer jp : otrMappedJPCounts.keySet()) {			
			mappedClassCounts.put(jp, otrMappedJPCounts.get(jp));	
			class_SegData.put(jp, new SOM_MapNodeSegmentData(this, this.OID+"_JPCount_JP_"+jp, "JP Orders present for jp :"+jp));
		}
		for(Integer jpgrp : otrMappedJPGroupCounts.keySet()) { 
			otrJPCounts = otrMappedJPGroupCounts.get(jpgrp);
			Jpg_jpCounts = mappedCategoryCounts.get(jpgrp);
			if(Jpg_jpCounts==null) { 
				Jpg_jpCounts = new TreeMap<Integer, Integer>(); 
				//on initial mapping for this jpg, build the jpGroup_SegData object for this jpg
				category_SegData.put(jpgrp, new SOM_MapNodeSegmentData(this, this.OID+"_JPGroupCount_JPG_"+jpgrp, "JPGroup Orders present for jpg :"+jpgrp));	
			}
			for(Integer jp : otrJPCounts.keySet()) {Jpg_jpCounts.put(jp, otrJPCounts.get(jp));}
			mappedCategoryCounts.put(jpgrp, Jpg_jpCounts);
		}		
	}//addMapNodeExToBMUs_Priv

	///////////////////
	// non-prod-jp-based segment data

	public final void clearNonProdJPSeg() {	
		nonProdJp_SegDataRatio.clear();
		ttlNumMappedNonProdJpInstances = 0.0f;
		//aggregate total count of all classes seen by this node
		if(mappedNonProdJPCounts.size()!=nonProdJp_SegData.size()) {
			mapMgr.getMsgObj().dispInfoMessage("Straff_SOMMapNode", "clearNonProdJPSeg", "Error : mappedNonProdJPCounts.size() : " + mappedNonProdJPCounts.size() + " is not equal to class_SegData.size() : " + class_SegData.size());
		}
		for(Integer count : mappedNonProdJPCounts.values()) {ttlNumMappedNonProdJpInstances += count;}
		for(Integer cls : nonProdJp_SegData.keySet()) {
			nonProdJp_SegData.get(cls).clearSeg();			//clear each class's segment manager
			nonProdJp_SegDataRatio.put(cls, mappedNonProdJPCounts.get(cls)/ttlNumMappedNonProdJpInstances);
		}	
//		if((compLoc.x==this.mapNodeCoord.x) && (compLoc.y==this.mapNodeCoord.y)) {mapMgr.getMsgObj().dispInfoMessage("SOMMapNode", "clearClassSeg","47,8 Info :  mappedClassCounts.size() : " + mappedClassCounts.size() + " | class_SegData.size() : " + class_SegData.size()+ " | class_SegDataRatio.size() : " + class_SegDataRatio.size());}
		
	}//clearClassSeg()
	public final void setNonProdJPSeg(Integer _cls, SOM_ClassSegment _clsSeg) {		
		SOM_MapNodeSegmentData segData = nonProdJp_SegData.get(_cls);
		if(segData==null) {		mapMgr.getMsgObj().dispInfoMessage("Straff_SOMMapNode","setNonProdJPSeg", "Null segData for map node : " + OID +" | non prod jp : " + _cls);	}
		segData.setSeg(_clsSeg);
	}		//should always exist - if doesn't is bug, so no checking to expose bug
	
	public final SOMMapSegment getNonProdJPSegment(Integer _cls) {
		SOM_MapNodeSegmentData clsMgrAtIdx = nonProdJp_SegData.get(_cls);
		if(null==clsMgrAtIdx) {return null;}			//does not have class 
		return clsMgrAtIdx.getSegment();
	}
	public final int getNonProdJPSegClrAsInt(Integer _cls) {
		SOM_MapNodeSegmentData clsMgrAtIdx = nonProdJp_SegData.get(_cls);
		if(null==clsMgrAtIdx) {return 0;}			//does not have class 
		return clsMgrAtIdx.getSegClrAsInt();
	}	
	
	//for passed -class (not idx)- give this node's probability
	public final float getNonProdJPProb(Integer _cls) {
		Float prob = nonProdJp_SegDataRatio.get(_cls);
		if(null==prob) {return 0.0f;}
		return prob;
	}
	public final Set<Integer> getNonProdJPSegIDs(){	return nonProdJp_SegData.keySet();}
	/**
	 * return class segment ratios (probabilities of each class) mapped to this map node
	 * @return
	 */
	public final TreeMap<Integer, Float> getNonProdJP_SegDataRatio(){return nonProdJp_SegDataRatio;}
	public final Float getTtlNumMappedNonProdJpInstances() { return ttlNumMappedNonProdJpInstances;}
	
	///////////////////
	// non-prod-jpgroup -based segment data

	public final void clearNonProdJpGroupSeg() {
		nonProdJpGroup_SegDataRatio.clear();
		if(mappedNonProdJPGroupCounts.size()!=nonProdJpGroup_SegData.size()) {
			mapMgr.getMsgObj().dispInfoMessage("Straff_SOMMapNode", "clearNonProdJpGroupSeg", "Error : mappedNonProdJPGroupCounts.size() : " + mappedNonProdJPGroupCounts.size() + " is not equal to nonProdJpGroup_SegData.size() : " + nonProdJpGroup_SegData.size());
		}
		ttlNumMappedNonProdJpGroupInstances = 0.0f;		//should be the same as ttlNumMappedClassInstances - measures same # of orders)
		Float ttlPerCategoryCount = 0.0f;
		TreeMap<Integer, Float> ttlPerCategoryCountsMap = new TreeMap<Integer, Float>();
		for(Integer category : mappedNonProdJPGroupCounts.keySet()) {
			TreeMap<Integer, Integer> classCountsPresent = mappedNonProdJPGroupCounts.get(category);
			ttlPerCategoryCount = 0.0f;			
			for(Integer count : classCountsPresent.values()) {//aggregate counts of all classes seen for this category
				ttlPerCategoryCount += count;
				ttlNumMappedNonProdJpGroupInstances += count;	
			}
			ttlPerCategoryCountsMap.put(category, ttlPerCategoryCount);//set total count per category
		}
		
		//compute weighting for each category - proportion of this category's # of classes against total count of classes across all categories
		for(Integer category : category_SegData.keySet()) {
			nonProdJpGroup_SegData.get(category).clearSeg();	
			nonProdJpGroup_SegDataRatio.put(category, ttlPerCategoryCountsMap.get(category)/ttlNumMappedNonProdJpGroupInstances);
		}	
	}//clearCategorySeg
	public final void setNonProdJpGroupSeg(Integer cat, SOM_CategorySegment _catSeg) {
		nonProdJpGroup_SegData.get(cat).setSeg(_catSeg);
	}		//should always exist - if doesn't is bug, so no checking to expose bug
	
	public final SOMMapSegment getNonProdJpGroupSegment(Integer category) {
		SOM_MapNodeSegmentData categoryMgrAtIdx = nonProdJpGroup_SegData.get(category);
		if(null==categoryMgrAtIdx) {return null;}			//does not have weight at this feature index
		return categoryMgrAtIdx.getSegment();
	}
	public final int getNonProdJpGroupSegClrAsInt(Integer category) {
		SOM_MapNodeSegmentData categoryMgrAtIdx = nonProdJpGroup_SegData.get(category);
		if(null==categoryMgrAtIdx) {return 0;}			//does not have weight at this feature index	
		return categoryMgrAtIdx.getSegClrAsInt();
	}	
		
	//for passed -Category label (not cat idx)- give this node's probability
	public final float getNonProdJpGroupProb(Integer category) {
		Float prob = nonProdJpGroup_SegDataRatio.get(category);
		if(null==prob) {return 0.0f;}
		return prob;
	}
	public final Set<Integer> getNonProdJpGroupSegIDs(){	return nonProdJpGroup_SegData.keySet();}
	/**
	 * return category segment ratios (probabilities of each category) mapped to this map node
	 * @return
	 */	
	public final TreeMap<Integer, Float> getNonProdJpGroup_SegDataRatio(){return nonProdJpGroup_SegDataRatio;}
	public final Float getTtlNumMappedNonProdJpGroupInstances() { return ttlNumMappedNonProdJpGroupInstances;}
	
	//////////////////////////////////////
	// end non-prod segments	
	
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