package base_SOM_Objects.som_utils;

import java.util.*;
import java.util.concurrent.*;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.SOMExample;
import base_Utils_Objects.*;

public class MapExFtrCalcs_Runner implements Runnable {
	SOMMapManager mapMgr;
	boolean canMultiThread;
	String dataTypName;
	SOMExample[] exData;
	int numUsableThreads;
	protected static final String[] typeAra = new String[] {"Feature Calc","Post Indiv Feature Calc","Calcs called After All Example Ftrs built"};

	//ref to thread executor
	protected ExecutorService th_exec;
	//approx # per partition, divied up among the threads
	public static final int rawNumPerPartition = 40000;
	//type of execution - 0 is build features, 1 is postftrveccalc
	private final int typeOfProc;
	private final String calcTypeStr;
	List<Future<Boolean>> testCalcMapperFtrs = new ArrayList<Future<Boolean>>();
	List<MapFtrCalc> testCalcMappers = new ArrayList<MapFtrCalc>();

	public MapExFtrCalcs_Runner(SOMMapManager _mapMgr, ExecutorService _th_exec, SOMExample[] _exData, String _dataTypName, int _typeOfProc) {
		mapMgr = _mapMgr; 
		numUsableThreads = mapMgr.getNumUsableThreads()-1;
		canMultiThread = mapMgr.isMTCapable();
		th_exec = _th_exec;
		exData = _exData;
		dataTypName = _dataTypName;
		typeOfProc = _typeOfProc;
		calcTypeStr = typeAra[typeOfProc];
	}
	
	//determine how many values should be per thread, if 
	public int calcNumPerThd(int numVals, int numThds) {
		//return (int) Math.round((numVals + Math.round(numThds/2))/(1.0*numThds));
		return (int) ((numVals -1)/(1.0*numThds)) + 1;
	}//calcNumPerThd

	@Override
	public void run() {
		testCalcMappers = new ArrayList<MapFtrCalc>();
		int numPartitions = exData.length/rawNumPerPartition;
		if(numPartitions < 1) {numPartitions = 1;}
		int numPerPartition = calcNumPerThd(exData.length,numPartitions);
		mapMgr.getMsgObj().dispMessage("MapExFtrCalcs_Runner","run","Starting performing "+calcTypeStr+" calcs for all " +exData.length + " "+dataTypName+" examples using " +numPartitions + " partitions of length " +numPerPartition +".", MsgCodes.info1);
		int dataSt = 0;
		int dataEnd = numPerPartition;
		for(int pIdx = 0; pIdx < numPartitions-1;++pIdx) {
			testCalcMappers.add(new MapFtrCalc(mapMgr, dataSt, dataEnd, exData, pIdx, dataTypName, calcTypeStr, typeOfProc));
			dataSt = dataEnd;
			dataEnd +=numPerPartition;			
		}
		if(dataSt < exData.length) {testCalcMappers.add(new MapFtrCalc(mapMgr, dataSt, exData.length, exData, numPartitions-1, dataTypName, calcTypeStr, typeOfProc));}
		
		testCalcMapperFtrs = new ArrayList<Future<Boolean>>();
		try {testCalcMapperFtrs = th_exec.invokeAll(testCalcMappers);for(Future<Boolean> f: testCalcMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		

		mapMgr.getMsgObj().dispMessage("MapExFtrCalcs_Runner","run","Finished performing "+calcTypeStr+" calcs for all " +exData.length + " "+dataTypName+" examples using " +numPartitions + " partitions of length " +numPerPartition +".", MsgCodes.info1);
	}//run

}//MapExFtrCalcs_Runner


class MapFtrCalc implements Callable<Boolean>{
	protected MessageObject msgObj;
	protected final int stIdx, endIdx, thdIDX, progressBnd, typeOfCalc;

	protected String dataType, calcType;
	protected static final float progAmt = .2f;
	protected double progress = -progAmt;
	protected SOMExample[] exs;
	
	
	public MapFtrCalc(SOMMapManager _mapMgr, int _stExIDX, int _endExIDX, SOMExample[] _exs, int _thdIDX, String _datatype, String _calcType,int _typeOfCalc) {
		msgObj = _mapMgr.buildMsgObj();//make a new one for every thread
		exs=_exs;
		stIdx = _stExIDX;
		endIdx = _endExIDX;
		progressBnd = (int) ((endIdx-stIdx) * progAmt);
		thdIDX= _thdIDX;
		dataType = _datatype;
		typeOfCalc = _typeOfCalc;	
		calcType = _calcType;
	} 
	
	protected void incrProgress(int idx) {
		if(((idx-stIdx) % progressBnd) == 0) {		
			progress += progAmt;	
			msgObj.dispInfoMessage("MapFtrCalc","incrProgress::thdIDX=" + String.format("%02d", thdIDX)+" ", "Progress for "+calcType+ " with dataType : " +dataType +" at : " + String.format("%.2f",progress));
		}
		if(progress > 1.0) {progress = 1.0;}
	}

	public double getProgress() {	return progress;}
	
	@Override
	public Boolean call() throws Exception {
		if(exs.length == 0) {
			msgObj.dispMessage("MapFtrCalc", "Run Thread : " +thdIDX, ""+dataType+" Data["+stIdx+":"+endIdx+"] is length 0 so nothing to do. Aborting thread.", MsgCodes.info5);
			return true;}
		msgObj.dispMessage("MapFtrCalc", "Run Thread : " +thdIDX, "Starting "+calcType+" on "+dataType+" Data["+stIdx+":"+endIdx+"]  (" + (endIdx-stIdx) + " exs), ", MsgCodes.info5);
		//typeOfCalc==0 means build features
		if(typeOfCalc==0) {			for (int i=stIdx;i<endIdx;++i) {exs[i].buildFeatureVector();incrProgress(i);}} 							//build ftrs
		else if(typeOfCalc==1) {	for (int i=stIdx;i<endIdx;++i) {exs[i].postFtrVecBuild();incrProgress(i);}	}							//typeOfCalc==1 means post ftr build calc - (Per example finalizing)
		else if(typeOfCalc==2) {	for (int i=stIdx;i<endIdx;++i) {exs[i].buildAfterAllFtrVecsBuiltStructs();incrProgress(i);}	}			//typeOfCalc==2 means after all ftr vecs have been build 			
		msgObj.dispMessage("MapFtrCalc", "Run Thread : " +thdIDX, "Finished "+calcType+" on "+dataType+" Data["+stIdx+":"+endIdx+"] calc : ", MsgCodes.info5);		
		return true;
	}
	
}//class MapFtrCalc
