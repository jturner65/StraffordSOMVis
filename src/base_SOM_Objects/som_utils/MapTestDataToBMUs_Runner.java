package base_SOM_Objects.som_utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.*;
import base_Utils_Objects.MsgCodes;
import base_Utils_Objects.Tuple;

/**
 * this will build a runnable to perform mapping in its own thread - fire and forget
 * @author john
 *
 */
public class MapTestDataToBMUs_Runner implements Runnable {
	SOMMapManager mapMgr;
	boolean canMultiThread;
	String dataTypName;
	ExDataType dataType;
	SOMExample[] exData;
	int numUsableThreads,curMapTestFtrType;
	boolean useChiSqDist;
	TreeMap<Tuple<Integer,Integer>, SOMMapNode> MapNodes;
	//map of ftr idx and all map nodes that have non-zero presence in that ftr
	protected TreeMap<Integer, HashSet<SOMMapNode>> MapNodesByFtr;
	//ref to thread executor
	protected ExecutorService th_exec;
	//ref to flags idx of boolean denoting this type of data is ready to save mapping results
	protected int flagsRdyToSaveIDX;
	//approx # per partition, divied up among the threads
	private final int rawNumPerPartition = 200000;
	
	protected double ttlProgress=-.1;
	
	List<Future<Boolean>> testMapperFtrs = new ArrayList<Future<Boolean>>();
	List<MapTestDataToBMUs> testMappers = new ArrayList<MapTestDataToBMUs>();
		
	public MapTestDataToBMUs_Runner(SOMMapManager _mapMgr, ExecutorService _th_exec, SOMExample[] _exData, String _dataTypName, ExDataType _dataType, int _readyToSaveIDX) {
		mapMgr = _mapMgr; 
		MapNodes = mapMgr.getMapNodes();
		MapNodesByFtr = mapMgr.getMapNodesByFtr();
		curMapTestFtrType = mapMgr.getCurrentTestDataFormat();		
		numUsableThreads = mapMgr.getNumUsableThreads()-1;
		useChiSqDist = mapMgr.getUseChiSqDist();
		canMultiThread = mapMgr.isMTCapable();
		th_exec = _th_exec;
		exData = _exData;
		dataTypName = _dataTypName;
		dataType = _dataType;
		flagsRdyToSaveIDX = _readyToSaveIDX;
	}//ctor

	//determine how many values should be per thread, if 
	public int calcNumPerThd(int numVals, int numThds) {
		//return (int) Math.round((numVals + Math.round(numThds/2))/(1.0*numThds));
		return (int) ((numVals -1)/(1.0*numThds)) + 1;
	}//calcNumPerThd
	
	//call 1 time for any particular type of data
	protected void _finalizeBMUProcessing(SOMExample[] _exs, ExDataType dataType) {
		int dataTypeVal = dataType.getVal();
		for(SOMMapNode mapNode : MapNodes.values()){mapNode.clearBMUExs(dataTypeVal);mapMgr.addExToNodesWithNoExs(mapNode, dataType);}	
		if(dataType==ExDataType.Training) {			for (int i=0;i<_exs.length;++i) {			_exs[i].mapTrainingToBMU(dataTypeVal);	}		} 
		else {										for (int i=0;i<_exs.length;++i) {			_exs[i].mapToBMU(dataTypeVal);		}		}		
		mapMgr._finalizeBMUProcessing(dataType);
	}//_finalizeBMUProcessing

	protected void incrTTLProgress(int len, int idx) {
		ttlProgress = idx/(1.0 * len);
		if((ttlProgress * 100) % 10 == 0) {
			mapMgr.getMsgObj().dispInfoMessage("MapTestDataToBMUs_Runner","incrTTLProgress", "Total Progress at : " + String.format("%.4f",ttlProgress));
		}
		if(ttlProgress > 1.0) {ttlProgress = 1.0;}	
	}
	
	public double getTtlProgress() {return ttlProgress;}
	
	
	private void runner(int dataSt, int dataEnd, int pIdx, int ttlParts) {
		//if(canMultiThread) {
			int numEx = dataEnd-dataSt;
//			testMappers = new ArrayList<MapTestDataToBMUs>();
			int numForEachThrd = calcNumPerThd(numEx, numUsableThreads);
			//use this many for every thread but last one
			int stIDX = dataSt;
			int endIDX = dataSt + numForEachThrd;		
			String partStr = " in partition "+pIdx+" of "+ttlParts;
			int numExistThds = testMappers.size();
			for (int i=0; i<(numUsableThreads-1);++i) {				
				testMappers.add(new MapTestDataToBMUs(mapMgr,stIDX, endIDX,  exData, i+numExistThds, dataTypName+partStr, useChiSqDist));
				stIDX = endIDX;
				endIDX += numForEachThrd;
			}
			//last one probably won't end at endIDX, so use length
			if(stIDX < dataEnd) {testMappers.add(new MapTestDataToBMUs(mapMgr,stIDX, dataEnd, exData, numUsableThreads-1 + numExistThds, dataTypName+partStr,useChiSqDist));}
	}//runner

	@Override
	public void run() {
		mapMgr.setFlag(flagsRdyToSaveIDX, false);
		if(canMultiThread) {
			testMappers = new ArrayList<MapTestDataToBMUs>();
			int numPartitions = exData.length/rawNumPerPartition;
			if(numPartitions < 1) {numPartitions = 1;}
			int numPerPartition = calcNumPerThd(exData.length,numPartitions);
			mapMgr.getMsgObj().dispMessage("MapTestDataToBMUs_Runner","run","Starting finding bmus for all " +exData.length + " "+dataTypName+" data using " +numPartitions + " partitions of length " +numPerPartition +".", MsgCodes.info1);
			int dataSt = 0;
			int dataEnd = numPerPartition;
			for(int pIdx = 0; pIdx < numPartitions-1;++pIdx) {
				runner(dataSt, dataEnd, pIdx,numPartitions);
				dataSt = dataEnd;
				dataEnd +=numPerPartition;			
			}
			runner(dataSt, exData.length, numPartitions-1,numPartitions);			
			
			testMapperFtrs = new ArrayList<Future<Boolean>>();
			try {testMapperFtrs = th_exec.invokeAll(testMappers);for(Future<Boolean> f: testMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		

		} else {//for every product find closest map node
			ttlProgress=-.1;
			if (useChiSqDist) {			for (int i=0;i<exData.length;++i){	exData[i].findBMUFromFtrNodes(MapNodesByFtr, exData[i]::getSqDistFromFtrType_ChiSq , curMapTestFtrType);incrTTLProgress(i,exData.length);		}}			
			else {						for (int i=0;i<exData.length;++i) {	exData[i].findBMUFromFtrNodes(MapNodesByFtr,exData[i]::getSqDistFromFtrType, curMapTestFtrType);	incrTTLProgress(i,exData.length);		}	}			
		}
		
		//go through every test example, if any, and attach prod to bmu - needs to be done synchronously because don't want to concurrently modify bmus from 2 different test examples		
		mapMgr.getMsgObj().dispMessage("MapTestDataToBMUs_Runner","run","Finished finding bmus for all " +exData.length + " "+dataTypName+" data. Start adding "+dataTypName+" data to appropriate bmu's list.", MsgCodes.info1);
		//below must be done when -all- dataType are done
		_finalizeBMUProcessing(exData, dataType);
		mapMgr.getMsgObj().dispMessage("MapTestDataToBMUs_Runner","run","Finished Mapping "+dataTypName+" data to best matching units.", MsgCodes.info5);		

		//Set some flag here stating that saving/further processing results is now available
		mapMgr.setFlag(flagsRdyToSaveIDX, true);
	}//run

}//MapTestDataToBMUs_Runner