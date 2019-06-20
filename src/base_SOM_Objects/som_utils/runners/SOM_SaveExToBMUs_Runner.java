package base_SOM_Objects.som_utils.runners;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import base_SOM_Objects.SOM_MapManager;
import base_SOM_Objects.som_examples.SOMExample;
import base_Utils_Objects.io.FileIOManager;
import base_Utils_Objects.io.MessageObject;
import base_Utils_Objects.io.MsgCodes;

public class SOM_SaveExToBMUs_Runner extends SOM_MapRunner {
	
	//approx # per partition, divied up among the threads
	private final int rawNumPerPartition;
	protected final String fileNamePrefix;
	
	List<Future<Boolean>> ExSaveBMUFutures = new ArrayList<Future<Boolean>>();
	List<SOM_ExToBMUs_CSVWriter> ExSaveBMUThds = new ArrayList<SOM_ExToBMUs_CSVWriter>();

	public SOM_SaveExToBMUs_Runner(SOM_MapManager _mapMgr, ExecutorService _th_exec, SOMExample[] _exData, String _dataTypName, boolean _forceST, String _fileNamePrefix, int _rawNumPerPartition) {
		super(_mapMgr, _th_exec, _exData, _dataTypName, _forceST);
		fileNamePrefix = _fileNamePrefix;
		rawNumPerPartition = _rawNumPerPartition;
	}

	@Override
	protected int getNumPerPartition() {return rawNumPerPartition;	}

	@Override
	protected void runMe_Indiv_MT(int numPartitions, int numPerPartition) {
		msgObj.dispMessage("SOM_SaveExToBMUs_Runner","runMe_Indiv_MT","Starting saving example-to-bmu mappings for " +exData.length + " "+dataTypName+" examples using " +numPartitions + " partitions of length " +numPerPartition +".", MsgCodes.info1);
		ExSaveBMUThds = new ArrayList<SOM_ExToBMUs_CSVWriter>();
		int dataSt = 0;
		int dataEnd = numPerPartition;
		for(int pIdx = 0; pIdx < numPartitions-1;++pIdx) {
			ExSaveBMUThds.add(new SOM_ExToBMUs_CSVWriter(mapMgr, dataSt, exData.length, exData, numPartitions-1, dataTypName, fileNamePrefix));
			dataSt = dataEnd;
			dataEnd +=numPerPartition;			
		}
		if(dataSt < exData.length) {ExSaveBMUThds.add(new SOM_ExToBMUs_CSVWriter(mapMgr, dataSt, exData.length, exData, numPartitions-1, dataTypName, fileNamePrefix));}	
		ExSaveBMUFutures = new ArrayList<Future<Boolean>>();
		try {ExSaveBMUFutures = th_exec.invokeAll(ExSaveBMUThds);for(Future<Boolean> f: ExSaveBMUFutures) { f.get(); }} catch (Exception e) { e.printStackTrace(); }
		msgObj.dispMessage("SOM_SaveExToBMUs_Runner","runMe_Indiv_MT","Finished saving example-to-bmu mappings for " +exData.length + " "+dataTypName+" examples using " +numPartitions + " partitions of length " +numPerPartition +".", MsgCodes.info1);
	}

	@Override
	protected void runMe_Indiv_ST() {
		SOM_ExToBMUs_CSVWriter saver = new SOM_ExToBMUs_CSVWriter(mapMgr, 0, exData.length, exData, 0, dataTypName, fileNamePrefix);
		saver.call();
	}

	@Override
	protected void runMe_Indiv_End() {	}

}//SOM_SaveExToBMUs_Runner


class SOM_ExToBMUs_CSVWriter implements Callable<Boolean>{
	protected final MessageObject msgObj;
	protected final int stIdx, endIdx, thdIDX, progressBnd;
	protected final FileIOManager fileIO;

	protected final String dataType;
	protected static final float progAmt = .2f;
	protected double progress = -progAmt;
	protected final SOMExample[] exs;	
	protected final String fileName;
	//list of files to write
	protected ArrayList<String> outStrs;
	
	public SOM_ExToBMUs_CSVWriter(SOM_MapManager _mapMgr, int _stExIDX, int _endExIDX, SOMExample[] _exs, int _thdIDX, String _datatype, String _fileNamePrfx) {
		msgObj = _mapMgr.buildMsgObj();//make a new one for every thread
		exs=_exs;
		stIdx = _stExIDX;
		endIdx = _endExIDX;
		progressBnd = (int) ((endIdx-stIdx) * progAmt);
		thdIDX= _thdIDX;
		dataType = _datatype;
		fileIO = new FileIOManager(msgObj,"SOM_ExToBMUCSVWriter TH_IDX_"+String.format("%02d", thdIDX));
		fileName = _fileNamePrfx + "_Ex_"+String.format("%07d", stIdx)+"_"+String.format("%07d", endIdx) +".csv";
		outStrs = new ArrayList<String>();
		outStrs.add(exs[0].getBMU_NHoodHdr_CSV());
	} 
	
	protected void incrProgress(int idx) {
		if(((idx-stIdx) % progressBnd) == 0) {		
			progress += progAmt;	
			msgObj.dispInfoMessage("MapFtrCalc","incrProgress::thdIDX=" + String.format("%02d", thdIDX)+" ", "Progress for saving BMU mappings for examples of type : " +dataType +" at : " + String.format("%.2f",progress));
		}
		if(progress > 1.0) {progress = 1.0;}
	}

	public double getProgress() {	return progress;}
	
	@Override
	public Boolean call() {
		if(exs.length == 0) {
			msgObj.dispMessage("SOM_ExToBMUCSVWriter", "Run Thread : " +thdIDX, ""+dataType+" Data["+stIdx+":"+endIdx+"] is length 0 so nothing to do. Aborting thread.", MsgCodes.info5);
			return true;}
		msgObj.dispMessage("SOM_ExToBMUCSVWriter", "Run Thread : " +thdIDX, "Starting saving BMU mappings for "+dataType+" example Data["+stIdx+":"+endIdx+"] | # saved records : " + (endIdx-stIdx), MsgCodes.info5);		
		//typeOfCalc==0 means build features
		for(SOMExample ex : exs) {
			String resStr= ex.getBMU_NHoodMbrship_CSV();
			outStrs.add(resStr);
		}	
		fileIO.saveStrings(fileName, outStrs);	
		msgObj.dispMessage("SOM_ExToBMUCSVWriter", "Run Thread : " +thdIDX, "Finished saving BMU mappings for "+dataType+" example Data["+stIdx+":"+endIdx+"] | # saved records : " + (endIdx-stIdx), MsgCodes.info5);		
		return true;
	}
	
}//class MapFtrCalc
