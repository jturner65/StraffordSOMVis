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
import strafford_SOM_PKG.straff_SOM_Examples.prospects.CustProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
import strafford_SOM_PKG.straff_Utils.MonitorJpJpgrp;

////this class represents a particular node in the SOM map, with specific customizations for strafford data
public class Straff_SOMMapNode extends SOMMapNode{
	//reference to jp-jpg mapping/managing object
	protected static MonitorJpJpgrp jpJpgMon;
	
//	//this holds jp and count of all training examples mapped to this node
//	private TreeMap<Integer, Integer> mappedJPCounts;
//	//this holds jpg as key, value is jps in that jpg and counts (subtree)
//	private TreeMap<Integer, TreeMap<Integer, Integer>> mappedJPGroupCounts;
	
//	//segment membership manager of ftr-index-based segments - will have 1 per ftr with non-zero wt
//	//keyed by non-zero ftr index
//	protected TreeMap<Integer, SOM_MapNodeSegmentData> jp_SegData;	//segment membership manager of ftr-index-based segments - will have 1 per ftr with non-zero wt
//	protected TreeMap<Integer, Float> jp_SegDataRatio;			//this is the ratio of # of a particular jp to the total # of jps mapped to this map node - these should of course sum to 1
//	//keyed by non-zero ftr index
//	protected TreeMap<Integer, SOM_MapNodeSegmentData> jpGroup_SegData;
//	protected TreeMap<Integer, Float> jpGroup_SegDataRatio;			//this is the ratio of # of a particular jpgroup to the total # of jpgroups mapped to this map node - these should of course sum to 1

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

	
	@Override
	//called by SOMDataLoader - these are standardized based on data mins and diffs seen in -map nodes- feature data, not in training data
	public void buildStdFtrsMapFromFtrData_MapNode(float[] minsAra, float[] diffsAra) {
		ftrMaps[stdFtrMapTypeKey].clear();
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