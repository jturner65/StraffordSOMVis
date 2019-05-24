package strafford_SOM_PKG.straff_SOM_Examples;

import java.util.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_Utils_Objects.*;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.CustProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
import strafford_SOM_PKG.straff_Utils.MonitorJpJpgrp;

////this class represents a particular node in the SOM map, with specific customizations for strafford data
public class Straff_SOMMapNode extends SOMMapNode{
	//reference to jp-jpg mapping/managing object
	protected static MonitorJpJpgrp jpJpgMon;
	
	//this holds jp and count of all training examples mapped to this node
	private TreeMap<Integer, Integer> mappedJPCounts;
	//this holds jpg as key, value is jps in that jpg and counts (subtree)
	private TreeMap<Integer, TreeMap<Integer, Integer>> mappedJPGroupCounts;

	public Straff_SOMMapNode(SOMMapManager _map, Tuple<Integer,Integer> _mapNode, float[] _ftrs) {
		super(_map, _mapNode, _ftrs);
		jpJpgMon = ((Straff_SOMMapManager)mapMgr).jpJpgrpMon;
		_initDataFtrMappings();		
	}//ctor w/float ftrs

	//build a map node from a string array of features
	public Straff_SOMMapNode(SOMMapManager _map,Tuple<Integer,Integer> _mapNode, String[] _strftrs) {
		super(_map, _mapNode, _strftrs);
		jpJpgMon = ((Straff_SOMMapManager)mapMgr).jpJpgrpMon;
		_initDataFtrMappings();
	}//ctor w/str ftrs	

	@Override
	//called after ftrs are built
	protected void _initDataFtrMappings() {			
		//build structure that holds counts of jps mapped to this node
		mappedJPCounts = new TreeMap<Integer, Integer>();
		mappedJPGroupCounts = new TreeMap<Integer, TreeMap<Integer, Integer>>();
		
		//build essential components of feature vector
		buildAllNonZeroFtrIDXs();
		buildNormFtrData();//once ftr map is built can normalize easily
		_buildFeatureVectorEnd_Priv();
	}//_initDataFtrMappings
	
	@Override
	//manage instancing map node handling - specifically, handle using 2ndary features as node markers (like a product tag or a class)
	protected void addTrainingExToBMUs_Priv(double dist, SOMExample ex) {
		TreeMap<Tuple<Integer, Integer>, Integer> trainExOrderCounts = ((CustProspectExample)ex).getOrderCountsForExample();
		//for each jpg-jp used in training example, assign 
		TreeMap<Integer, Integer> jpCountsAtJpGrp;
		for (Tuple<Integer, Integer> jpgJp : trainExOrderCounts.keySet()) {
			Integer jpg = jpgJp.x, jp = jpgJp.y;
			Integer jpCount = mappedJPCounts.get(jp);
			//for each jp
			if(null==jpCount) {jpCount = 0;}
			++jpCount;
			mappedJPCounts.put(jp, jpCount);
			//for each jpgroup
			jpCountsAtJpGrp = mappedJPGroupCounts.get(jpg);
			if(null==jpCountsAtJpGrp) {jpCountsAtJpGrp = new TreeMap<Integer, Integer>(); mappedJPGroupCounts.put(jpg, jpCountsAtJpGrp);}
			jpCountsAtJpGrp.put(jp, jpCount);
		}		
	}//addExToBMUs_Priv
	
	@Override
	//assign relelvant info to this map node from neighboring map node(s) to cover for this node not having any training examples assigned
	protected void addMapNodeExToBMUs_Priv(double dist, SOMMapNode ex) {//copy structure 
		TreeMap<Integer, Integer> otrMappedJPCounts = ((Straff_SOMMapNode)ex).mappedJPCounts,otrJPCounts,jpCounts;
		TreeMap<Integer, TreeMap<Integer, Integer>> otrMappedJPGroupCounts = ((Straff_SOMMapNode)ex).mappedJPGroupCounts;
		for(Integer jp : otrMappedJPCounts.keySet()) {			mappedJPCounts.put(jp, otrMappedJPCounts.get(jp));		}
		for(Integer jpgrp : otrMappedJPGroupCounts.keySet()) { 
			otrJPCounts = otrMappedJPGroupCounts.get(jpgrp);
			jpCounts = mappedJPGroupCounts.get(jpgrp);
			if(jpCounts==null) { jpCounts = new TreeMap<Integer, Integer>(); mappedJPGroupCounts.put(jpgrp, jpCounts);}
			for(Integer jp : otrJPCounts.keySet()) {jpCounts.put(jp, otrJPCounts.get(jp));}
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