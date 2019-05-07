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
	protected final int stIdx, endIdx, curMapFtrType, thdIDX, progressBnd;
	//calculate the exclusionary feature distance(only measure distance from map via features that the node has non-zero values in)
	protected final boolean useChiSqDist;
	protected final TreeMap<Tuple<Integer,Integer>, SOMMapNode> MapNodes;
	//map of ftr idx and all map nodes that have non-zero presence in that ftr
	protected TreeMap<Integer, HashSet<SOMMapNode>> MapNodesByFtr;

	protected String ftrTypeDesc, dataType;
	protected static final float progAmt = .2f;
	protected double progress = -progAmt;
	
	public MapDataToBMUs(SOMMapManager _mapMgr, int _stProdIDX, int _endProdIDX, int _thdIDX, String _type, boolean _useChiSqDist){
		mapMgr = _mapMgr;
		MapNodes = mapMgr.getMapNodes();
		MapNodesByFtr = mapMgr.getMapNodesByFtr();
		msgObj = mapMgr.buildMsgObj();//make a new one for every thread
		stIdx = _stProdIDX;
		endIdx = _endProdIDX;
		progressBnd = (int) ((endIdx-stIdx) * progAmt);
		thdIDX= _thdIDX;
		dataType = _type;
		curMapFtrType = mapMgr.getCurrentTestDataFormat();
		ftrTypeDesc = mapMgr.getDataDescFromCurFtrTestType();
		useChiSqDist = _useChiSqDist;		
	}//ctor
	
	protected void incrProgress(int idx) {
		if(((idx-stIdx) % progressBnd) == 0) {		
			progress += progAmt;	
			msgObj.dispInfoMessage("MapDataToBMUs","incrProgress::thdIDX=" + String.format("%02d", thdIDX)+" ", "Progress for dataType : " +dataType +" at : " + String.format("%.2f",progress));
		}
		if(progress > 1.0) {progress = 1.0;}
	}
	
	public double getProgress() {	return progress;}
	
	protected abstract boolean mapAllDataToBMUs();
	@Override
	public final Boolean call() throws Exception {	
		progress = -progAmt;
		boolean retCode = mapAllDataToBMUs();		
		return retCode;
	}
	
}//mapDataToBMUs

