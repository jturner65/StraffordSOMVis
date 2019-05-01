package base_SOM_Objects.som_utils;

import java.util.*;
import java.util.concurrent.Callable;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_Utils_Objects.*;

//class to manage mapping of examples to bmus
public abstract class MapDataToBMUs implements Callable<Boolean>{
	protected SOMMapManager mapMgr;
	protected MessageObject msgObj;
	protected final int stIdx, endIdx, curMapFtrType, thdIDX;
	//calculate the exclusionary feature distance(only measure distance from map via features that the node has non-zero values in)
	protected final boolean useChiSqDist;
	protected final TreeMap<Tuple<Integer,Integer>, SOMMapNode> MapNodes;
	//map of ftr idx and all map nodes that have non-zero presence in that ftr
	protected TreeMap<Integer, HashSet<SOMMapNode>> MapNodesByFtr;

	protected String ftrTypeDesc, dataType;
	
	public MapDataToBMUs(SOMMapManager _mapMgr, int _stProdIDX, int _endProdIDX, int _thdIDX, String _type, boolean _useChiSqDist){
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

