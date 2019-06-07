package base_SOM_Objects.som_utils;

import java.util.TreeMap;
import java.util.function.BiFunction;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.SOMExample;
import base_Utils_Objects.MsgCodes;

//this class will find the bmus for the passed dataset of test or validation examples - the passed reference is to 
//the entire dataset, each instance of this callable will process a subset of this dataset
public class MapExampleDataToBMUs extends MapDataToBMUs{
	protected SOMExample[] exs;
	
	public MapExampleDataToBMUs(SOMMapManager _mapMgr, int _stProdIDX, int _endProdIDX, SOMExample[] _exs, int _thdIDX, String _type, boolean _useChiSqDist) {
		super(_mapMgr, _stProdIDX,  _endProdIDX,  _thdIDX, _type, _useChiSqDist);			
		exs = _exs;		//make sure these are cast appropriately
	}	
	
	private void mapExample(int i, BiFunction<TreeMap<Integer, Float>, TreeMap<Integer, Float>, Double> _distFunc) {
		exs[i].findBMUFromFtrNodes(MapNodesByFtr,_distFunc, curMapFtrType);
		incrProgress(i);
		//example has probabilities for specific jps and jpgroups based on BMU
		exs[i].setSegmentsAndProbsFromBMU();
	}//mapExample
	
	
	@Override
	protected boolean mapAllDataToBMUs() {
		if(exs.length == 0) {
			msgObj.dispMessage("MapTestDataToBMUs", "Run Thread : " +thdIDX, ""+dataType+" Data["+stIdx+":"+endIdx+"] is length 0 so nothing to do. Aborting thread.", MsgCodes.info5);
			return true;}
		msgObj.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "Starting "+dataType+" Data["+stIdx+":"+endIdx+"]  (" + (endIdx-stIdx) + " exs), to BMU mapping using " + ftrTypeDesc + " Features and including only shared ftrs in distance.", MsgCodes.info5);
		if(MapNodesByFtr==null) {msgObj.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, " ALERT!!! MapNodesByFtr is null!!!", MsgCodes.error5);}
//		if (useChiSqDist) {		for (int i=stIdx;i<endIdx;++i) {exs[i].findBMUFromFtrNodes(MapNodesByFtr,exs[i]::getSqDistFromFtrType_ChiSq_Exclude, curMapFtrType);incrProgress(i);}} 
//		else {					for (int i=stIdx;i<endIdx;++i) {exs[i].findBMUFromFtrNodes(MapNodesByFtr,exs[i]::getSqDistFromFtrType_Exclude, curMapFtrType);incrProgress(i);}} 	
		if (useChiSqDist) {		for (int i=stIdx;i<endIdx;++i) {mapExample(i, exs[i]::getSqDistFromFtrType_ChiSq_Exclude);}} 
		else {					for (int i=stIdx;i<endIdx;++i) {mapExample(i, exs[i]::getSqDistFromFtrType_Exclude);}} 	
		
		msgObj.dispMessage("MapTestDataToBMUs", "Run Thread : " +thdIDX, "Finished "+dataType+" Data["+stIdx+":"+endIdx+"] to BMU mapping", MsgCodes.info5);		
		return true;
	}		
}//mapTestToBMUs


