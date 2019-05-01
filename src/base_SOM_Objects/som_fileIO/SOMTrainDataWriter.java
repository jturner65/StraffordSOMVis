package base_SOM_Objects.som_fileIO;

import java.util.concurrent.Callable;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_Utils_Objects.*;

//save all training/testing data to appropriate format for SOM
public class SOMTrainDataWriter implements Callable<Boolean>{
	//public SOM_StraffordMain pa;
	private SOMMapManager mapMgr;	
	private MessageObject msgObj;
	private int dataFrmt, numFtrs,numSmpls;
	private SOMExample[] exAra;
	private String savFileFrmt, fileName;
	//manage IO in this object
	private FileIOManager fileIO;
	
	public SOMTrainDataWriter(SOMMapManager _mapData, int _dataFrmt, int _numTrainFtrs, String _fileName, String _savFileFrmt, SOMExample[] _exAra) {
		mapMgr = _mapData; msgObj = mapMgr.buildMsgObj();
		dataFrmt = _dataFrmt;		//either unmodified, standardized or normalized -> 0,1,2
		exAra = _exAra;
		numFtrs = _numTrainFtrs;
		numSmpls = exAra.length;
		savFileFrmt = _savFileFrmt;
		fileName = _fileName;
		fileIO = new FileIOManager(msgObj, "SOMTrainDataWriter");
	}//ctor

	//build LRN file header
	private String[] buildInitLRN() {
		String[] outStrings = new String[numSmpls + 4];
		//# of data points
		outStrings[0]="% "+numSmpls;
		//# of features per data point +1
		outStrings[1]="% "+numFtrs;
		//9 + 1's * smplDim
		String str1="% 9", str2 ="% Key";
		for(int i=0; i< numFtrs; ++i) {
			str1 +=" 1";
			str2 +=" c"+(i+1);
		}
		outStrings[2]=str1;
		//'Key' + c{i} where i is 1->smplDim
		outStrings[3]=str2;		
		return outStrings;
	}//buildInitLRN
	
	//write file to save all data samples in appropriate format for 
	private void saveLRNData() {
		String[] outStrings = buildInitLRN();
		int strIDX = 4;
		for (int i=0;i<exAra.length; ++i) {outStrings[i+strIDX]=exAra[i].toLRNString(dataFrmt, " ");	}
		fileIO.saveStrings(fileName,outStrings);		
		msgObj.dispMessage("SOMTrainDataWriter","saveLRNData","Finished saving .lrn file with " + outStrings.length+ " elements to file : "+ fileName, MsgCodes.info5);			
	}//save lrn train data
	
	//save data in csv format
	private void saveCSVData() {
		//use buildInitLRN for test and train
		String[] outStrings = buildInitLRN();
		int strIDX = 4;
		for (int i=0;i<exAra.length; ++i) {outStrings[i+strIDX]=exAra[i].toCSVString(dataFrmt);	}
		fileIO.saveStrings(fileName,outStrings);		
		msgObj.dispMessage("SOMTrainDataWriter","saveCSVData","Finished saving .csv file with " + outStrings.length+ " elements to file : "+ fileName, MsgCodes.info5);			
	}//save csv test data
	
	//save data in SVM record form - each record is like a map/dict -> idx: val pair.  designed for sparse data
	private void saveSVMData() {
		//need to save a vector to determine the 
		String[] outStrings = new String[numSmpls];
		for (int i=0;i<exAra.length; ++i) {outStrings[i]=exAra[i].toSVMString(dataFrmt);	}
		fileIO.saveStrings(fileName,outStrings);		
		msgObj.dispMessage("SOMTrainDataWriter","saveSVMData","Finished saving .svm (sparse) file with " + outStrings.length+ " elements to file : "+ fileName, MsgCodes.info5);			
	}

	//write all sphere data to appropriate files
	@Override
	public Boolean call() {		
		//save to lrnFileName - build lrn file
		//4 extra lines that describe dense .lrn file - started with '%'
		//0 : # of examples
		//1 : # of features + 1 for name column
		//2 : format of columns -> 9 1 1 1 1 ...
		//3 : names of columns (not used by SOM_MAP)
		//format : 0 is training data to lrn, 1 is training data to svm format, 2 is testing data
		switch (savFileFrmt) {
			case "denseLRNData" : {
				saveLRNData();
				mapMgr.setDenseTrainDataSaved(true);	
				break;
			}
			case "sparseSVMData" : {
				saveSVMData();
				mapMgr.setSparseTrainDataSaved(true);		
				break;
			}
			case "denseCSVData" : {				
				saveCSVData();
				mapMgr.setCSVTestDataSaved(true);
				break;
			}
			default :{//default to save data in lrn format
				saveLRNData();
				mapMgr.setDenseTrainDataSaved(true);					
			}
		}
		return true;
	}//call
}//SOMTrainDataWriter


