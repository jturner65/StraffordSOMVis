package base_SOM_Objects;

import java.util.*;
import java.util.concurrent.Callable;

import base_Utils_Objects.*;

//class to manage mapping of examples to bmus
public abstract class mapDataToBMUs implements Callable<Boolean>{
	protected SOMMapManager mapMgr;
	protected messageObject msgObj;
	protected final int stIdx, endIdx, curMapFtrType, thdIDX;
	//calculate the exclusionary feature distance(only measure distance from map via features that the node has non-zero values in)
	protected final boolean useChiSqDist;
	protected final TreeMap<Tuple<Integer,Integer>, SOMMapNode> MapNodes;
	//map of ftr idx and all map nodes that have non-zero presence in that ftr
	protected TreeMap<Integer, HashSet<SOMMapNode>> MapNodesByFtr;

	protected String ftrTypeDesc, dataType;
	
	public mapDataToBMUs(SOMMapManager _mapMgr, int _stProdIDX, int _endProdIDX, int _thdIDX, String _type, boolean _useChiSqDist){
		mapMgr = _mapMgr;
		MapNodes = mapMgr.getMapNodes();
		MapNodesByFtr = mapMgr.getMapNodesByFtr();
		msgObj = mapMgr.buildMsgObj();
		stIdx = _stProdIDX;
		endIdx = _endProdIDX;
		thdIDX= _thdIDX;
		dataType = _type;
		curMapFtrType = mapMgr.getCurrentTestDataFormat();
		ftrTypeDesc = mapMgr.getDataDescFromCurFtrTestType();
		useChiSqDist = _useChiSqDist;		
	}//ctor
	
	protected abstract boolean mapAllDataToBMUs();
	@Override
	public final Boolean call() throws Exception {	
		boolean retCode = mapAllDataToBMUs();		
		return retCode;
	}
	
}//mapDataToBMUs

//this class will find the bmus for the passed dataset - the passed reference is to 
//the entire dataset, each instance of this callable will process a subset of this dataset
class mapTestDataToBMUs extends mapDataToBMUs{
	protected SOMExample[] exs;
	
	public mapTestDataToBMUs(SOMMapManager _mapMgr, int _stProdIDX, int _endProdIDX, SOMExample[] _exs, int _thdIDX, String _type, boolean _useChiSqDist) {
		super(_mapMgr, _stProdIDX,  _endProdIDX,  _thdIDX, _type, _useChiSqDist);			
		exs = _exs;		//make sure these are cast appropriately
	}	
	@Override
	protected boolean mapAllDataToBMUs() {
		if(exs.length == 0) {
			msgObj.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, ""+dataType+" Data["+stIdx+":"+endIdx+"] is length 0 so nothing to do. Aborting thread.", MsgCodes.info5);
			return true;}
		msgObj.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "Starting "+dataType+" Data["+stIdx+":"+endIdx+"]  (" + (endIdx-stIdx) + " exs), to BMU mapping using " + ftrTypeDesc + " Features and including all features in distance.", MsgCodes.info5);
		if (useChiSqDist) {		for (int i=stIdx;i<endIdx;++i) {exs[i].findBMUFromNodes_ChiSq_Excl(MapNodes, curMapFtrType);}} 
		else {					for (int i=stIdx;i<endIdx;++i) {exs[i].findBMUFromNodes_Excl(MapNodes,  curMapFtrType); }}		
		msgObj.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "Finished "+dataType+" Data["+stIdx+":"+endIdx+"] to BMU mapping", MsgCodes.info5);		
		return true;
	}		
}//mapTestToBMUs	