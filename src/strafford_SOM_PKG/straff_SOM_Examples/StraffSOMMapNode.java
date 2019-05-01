package strafford_SOM_PKG.straff_SOM_Examples;

import java.util.ArrayList;
import java.util.TreeMap;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.SOMMapNode;
import base_Utils_Objects.Tuple;
import strafford_SOM_PKG.MonitorJpJpgrp;
import strafford_SOM_PKG.StraffSOMMapManager;

////this class represents a particular node in the SOM map, with specific customizations for strafford data
public class StraffSOMMapNode extends SOMMapNode{
	//reference to jp-jpg mapping/managing object
	protected static MonitorJpJpgrp jpJpgMon;
	////these objects are for reporting on individual examples.  They are built when features, keyed by ftr type, and are 
	////use a map per feature type : unmodified, normalized, standardized,to hold the features sorted by weight as key, value is array of jps at that weight -submap needs to be instanced in descending key order
	//private TreeMap<Float, ArrayList<Integer>>[] mapOfTopWtJps;	
	////a map per feature type : unmodified, normalized, standardized,  of jps and their relative "rank" in this particular example, as determined by the weight calc
	//private TreeMap<Integer,Integer>[] mapOfJpsVsWtRank;

	public StraffSOMMapNode(SOMMapManager _map, Tuple<Integer,Integer> _mapNode, float[] _ftrs) {
		super(_map, _mapNode, _ftrs);
		jpJpgMon = ((StraffSOMMapManager)mapMgr).jpJpgrpMon;
		_initDataFtrMappings();		
	}//ctor w/float ftrs

	//build a map node from a string array of features
	public StraffSOMMapNode(SOMMapManager _map,Tuple<Integer,Integer> _mapNode, String[] _strftrs) {
		super(_map, _mapNode, _strftrs);
		jpJpgMon = ((StraffSOMMapManager)mapMgr).jpJpgrpMon;
		_initDataFtrMappings();
	}//ctor w/str ftrs	

	//called after ftrs are built
	protected void _initDataFtrMappings() {	
		//	for (Integer idx : ftrMaps[ftrMapTypeKey].keySet()) {	
		//		float val = ftrMaps[ftrMapTypeKey].get(idx);
		//	}
		//build essential components of feature vector
		buildAllNonZeroFtrIDXs();
		buildNormFtrData();//once ftr map is built can normalize easily
		_PostBuildFtrVec_Priv();
	}//_initDataFtrMappings

	@Override
	//called by SOMDataLoader - these are standardized based on data mins and diffs seen in -map nodes- feature data, not in training data
	public void buildStdFtrsMapFromFtrData_MapNode(float[] minsAra, float[] diffsAra) {
		ftrMaps[stdFtrMapTypeKey] = new TreeMap<Integer, Float>();
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
		setFlag(stdFtrsBuiltIDX,true);
	}//buildStdFtrsMap_MapNode

	//by here ftrs for this map node have been built
	@Override
	protected void buildAllNonZeroFtrIDXs() {
		allNonZeroFtrIDXs = new ArrayList<Integer>();
		for(Integer idx : ftrMaps[ftrMapTypeKey].keySet()) {		allNonZeroFtrIDXs.add(idx);	}
	}//buildAllNonZeroFtrIDXs

	@Override
	protected void _PostBuildFtrVec_Priv() {}
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