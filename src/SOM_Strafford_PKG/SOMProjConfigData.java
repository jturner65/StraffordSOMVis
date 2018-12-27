package SOM_Strafford_PKG;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

//structure to hold all the file names, file configurations and general program configurations required to run the SOM project
//will manage that all file names need to be reset when any are changed
public class SOMProjConfigData {
	//owning map manager
	public SOMMapManager mapMgr;
	//ref to SOM_MapDat SOM executiond descriptor object
	public SOM_MapDat SOMExeDat;	
	
	
	//this is redundant TODO merge the use of this with SOMFileNamesAra
	private String[] fnames; 
	private static final int numFiles = 5;		
	
	////////////////////////////////
	//debug info - default map to load - TODO move this to (or build this from) a default exp config file file
	//data format used in map training StraffSOM_2018_12_27_12_33_DebugRun
	private int _DBG_dataFrmt = 1;
	//date/time of debug pre-made map
	private String _DBG_Map_fileNow = "12_27_12_33";
	//date/time used in folder for debug of pre-made map
	private String _DBG_Map_fldrNow = "2018_"+_DBG_Map_fileNow+"_DebugRun";
	//map topology for debug
	private String _DBG_PreBuiltMapConfig = "_x20_y20_k2";	
	//prebuilt map values
	private int _DBG_Map_numSmpls = 459110, _DBG_Map_numTrain = 413199, _DBG_Map_numTest = 45911;
	///////////////////////////////////////
	
	//TODO these are a function of data stucture, so should be set via config file - will be constant for strafford
	public boolean useSparseTrainingData = true;
	public boolean useSparseTestingData = true;
	
	//fileNow date string array for most recent experiment
	private String[] dateTimeStrAra;


	//test/train partition - ratio of # of training data points to total # of data points
	private float trainTestPartition;	
	//current experiment # of samples total, train partition and test partition
	private int expNumSmpls, expNumTrain, expNumTest;
	//current type of data used to train map
	private String dataType;
	
	//calendar object to be used to query instancing time
	private final Calendar instancedNow;
	//os used by project - this is passed from map
	private final String OSUsed;
	//calcFileWeights is file holding config for calc object - get from config file
	private static final String calcWtFileName = "WeightEqConfig.csv";
	//source directory for reading all source csv files, writing intermediate outputs, executing SOM_MAP and writing results
	private String straffBasefDir;
	//name of som executable
	private final String SOMExeName_base = "straff_SOM";

	//short name to be used in file names to denote this project
	private final String SOMProjName = "straff";
	//string to invoke som executable - platform dependent
	private final String SOM_Map_EXECSTR;

	// file IO/location constructs - TODO should these be loaded from config file?
	//subdir to put preproc data files
	private static final String straffPreProcSubDir = "PreprocData" + File.separator;
	//subdir to hold source csv files
	private static final String straffSourceCSVSubDir = "source_csvs" + File.separator;
	//subdir for all sql info - connect config file
	private static final String straffSQLProcSubDir = "Sql"+ File.separator;
	//subdir for all SOM functionality
	private static final String straffSOMProcSubDir = "SOM"+ File.separator;
	//subdir holding calc object information
	private static final String straffCalcInfoSubDir = "Calc"+ File.separator;
	//actual directory where SOM data is located
	private String SOMDataDir;	
	
	//boolean flags
	private int[] stFlags;						//state flags - bits in array holding relevant process info
	private static final int 
		trainDatFNameIDX	= 0,				//training data file name has been set
		somResFPrfxIDX		= 1,				//som results file prefix has been set
		diffsFNameIDX		= 2,				//file name holding per-ftr max-min differences in training data
		minsFNameIDX		= 3,				//file name holding per ftr mins in training data
		csvSavFNameIDX		= 4,				//file name prefix holding class saving file prefixs
		isDirty				= 5,				//current fnames is dirty - not all files are in sync
		outputSffxSetIDX	= 6;				//output suffix has been set to match current experimental parameters
	private static final int numFlags = 7;		
	
	public static final String[] SOMResExtAra = new String[]{".wts",".fwts",".bm",".umx"};			//extensions for different SOM output file types
	//idxs of different kinds of SOM output files
	public static final int
		wtsIDX = 0,
		fwtsIDX = 1,
		bmuIDX = 2,
		umtxIDX = 3;	
	//all flags corresponding to file names required to run SOM
	private int[] reqFileNameFlags = new int[]{trainDatFNameIDX, somResFPrfxIDX, diffsFNameIDX, minsFNameIDX, csvSavFNameIDX};
	private String[] reqFileNames = new String[]{"trainDatFNameIDX", "somResFPrfxIDX", "diffsFNameIDX", "minsFNameIDX", "csvSavFNameIDX"};
	//idx 0 = file name for SOM training data -> .lrn file, 1 = file name for sphere sample data -> .csv file	
	private String[] SOMFileNamesAra;
	//this string holds experiment-specific string used for output files - x,y and k of map used to generate output.  this is set 
	//separately from calls to setSOM_ExpFileNames because experimental parameters can change between the saving of training data and the running of the experiment
	private String SOMOutExpSffx;

	
	public SOMProjConfigData(SOMMapManager _map) {
		mapMgr=_map;
		//----accumulate and manage OS info ----//
		//find platform this is executing on
		OSUsed = System.getProperty("os.name");
		mapMgr.dispMessage("SOMProjConfigData","Constructor","OS this application is running on : "  + OSUsed);
		//set invoking string for map executable - is platform dependent
		String execStr = SOMExeName_base;
		if (OSUsed.toLowerCase().contains("windows")) {
			execStr += ".exe";	
		}
		SOM_Map_EXECSTR = execStr;				
		//----end accumulate and manage OS info ----//		
		String baseDir = "StraffordProject" + File.separator;
		try {
			straffBasefDir = new File(baseDir).getCanonicalPath() + File.separator ;
		} catch (Exception e) {
			straffBasefDir =  baseDir;
			mapMgr.dispMessage("SOMProjConfigData","Constructor","Failed to find base application directory "+ baseDir + " due to : " + e);
		}
		mapMgr.dispMessage("SOMProjConfigData","Constructor","Canonical Path to application directory : " + straffBasefDir);
		mapMgr.dispMessage("SOMProjConfigData","Constructor","Loading Project configuration file. (Not yet implemented : TODO : build project config to facilitate turnkey operation.)");	
		
		SOMOutExpSffx = "x-1_y-1_k-1";//illegal values, needs to be set
		dataType = "NONE";
		
		instancedNow = Calendar.getInstance();
		fnames = new String[numFiles];
		for(int i=0;i<numFiles;++i){fnames[i]="";}
		initFlags();
		SOMExeDat = new SOM_MapDat(getOSUsed());			
	}//ctor
	
	//this will save all essential information for a SOM-based experimental run, to make duplication of experiment easier
	//Info saved : SOM_MapData; 
	public void saveSOM_Exp() {
		mapMgr.dispMessage("SOMProjConfigData","saveSOM_Exp","Saving SOM Exe config data.");
		//build file describing experiment and put at this location
		String expFileName = getSOMMapExpFileName();
		//get array of data describing SOM training exec info
		ArrayList<String> SOMDescAra = SOMExeDat.buildStringDescAra();
		mapMgr.dispMessage("SOMProjConfigData","saveSOM_Exp","Finished saving SOM Exe config data.");
		mapMgr.dispMessage("SOMProjConfigData","saveSOM_Exp","Saving project configuration data");
		mapMgr.saveStrings(expFileName,SOMDescAra);
		String configFileName = getSOMConfigFileName();
		//get array of data describing config info
		ArrayList<String> ConfigDescAra = getExpConfigData();
		mapMgr.saveStrings(configFileName,ConfigDescAra);		
		mapMgr.dispMessage("SOMProjConfigData","saveSOM_Exp","Finished saving project configuration data");
	}//saveSOM_Exp
		
	//save configuration data describing
	private ArrayList<String> getExpConfigData(){
		ArrayList<String> res = new ArrayList<String>();
		res.add("### This file holds config data for a particular experimental setup ###");
		res.add("### Base Configuration Data ###");
		res.add("useSparseTrainingData,"+useSparseTrainingData);
		res.add("useSparseTestingData,"+useSparseTestingData);
		res.add("trainTestPartition,"+String.format("%.6f", trainTestPartition));
		res.add("expNumSmpls,"+expNumSmpls);
		res.add("expNumTrain,"+expNumTrain);
		res.add("expNumTest,"+expNumTest);
		res.add("dataType,"+dataType);
		res.add("dateTimeStrAra[0],"+dateTimeStrAra[0]);
		res.add("dateTimeStrAra[1],"+dateTimeStrAra[1]);		
		res.add("### End Configuration Data ###");
		return res;
	}//getExpConfigData()	
	
	//this will load all essential information for a SOM-based experimental run, from loading preprocced data, configuring map
	//uses currently set file names ! 
	public void loadSOM_Exp() {
		mapMgr.dispMessage("SOMProjConfigData","loadSOM_Exp","Loading SOM Exe config data.");
		//build file describing experiment and put at this location
		String expFileName = getSOMMapExpFileName();
		String[] expStrAra = mapMgr.loadFileIntoStringAra(expFileName, "SOM_MapDat Config File loaded", "SOM_MapDat Config File Failed to load");
		SOMExeDat.buildFromStringArray(expStrAra);
		mapMgr.dispMessage("SOMProjConfigData","loadSOM_Exp","Finished loading SOM Exe config data.");
		mapMgr.dispMessage("SOMProjConfigData","loadSOM_Exp","Loading project configuration data");

		String configFileName = getSOMConfigFileName();
		String[] configStrAra = mapMgr.loadFileIntoStringAra(configFileName, "SOMProjConfigData Config File loaded", "SOMProjConfigData Config File Failed to load");
		setExpConfigData(configStrAra);
		mapMgr.dispMessage("SOMProjConfigData","loadSOM_Exp","Finished loading project configuration data");		
	}//loadSOM_Exp
	
	//set experiment vars based on data saved in config file
	private void setExpConfigData(String[] dataAra) {
		dateTimeStrAra = new String[2];
		for (int i=0;i<dataAra.length;++i) {
			String str = dataAra[i];
			String[] strToks = str.trim().split(",");
			switch(strToks[0]) {
			case "useSparseTrainingData" : 	{    
				useSparseTrainingData = Boolean.parseBoolean(strToks[1].trim());
				break;}
			case "useSparseTestingData" : 	{      
				useSparseTestingData = Boolean.parseBoolean(strToks[1].trim());
				break;}
			case "trainTestPartition" : 	{    
				trainTestPartition = Float.parseFloat(strToks[1].trim());
				break;}
			case "dataType" : 				{     
				dataType = strToks[1].trim();
				break;}
			case "expNumSmpls" : 			{      
				expNumSmpls = Integer.parseInt(strToks[1].trim());
				break;}
			case "expNumTrain" : 			{      
				expNumTrain = Integer.parseInt(strToks[1].trim());
				break;}
			case "expNumTest" : 			{      
				expNumTest = Integer.parseInt(strToks[1].trim());
				break;}
			case "dateTimeStrAra[0]" : 		{     
				dateTimeStrAra[0] = strToks[1].trim();
				break;}
			case "dateTimeStrAra[1]" : 		{   
				dateTimeStrAra[1] = strToks[1].trim();
				break;}			
			default : {}		
			}
		}//for each line	
		setSOM_ExpFileNames(expNumSmpls, expNumTrain, expNumTest);
	}//setExpConfigData	
		
	//save test/train data in multiple threads
	public void launchTestTrainSaveThrds(ExecutorService th_exec, int curMapFtrType) {
		String saveFileName = "";
		List<Future<Boolean>> straffSOMDataWriteFutures = new ArrayList<Future<Boolean>>();
		List<straffDataWriter> straffSOMDataWrite = new ArrayList<straffDataWriter>();
		//call threads to instance and save different file formats
		if (expNumTrain > 0) {
			if (useSparseTrainingData) {
				saveFileName = getSOMMapSVMFileName();
				straffSOMDataWrite.add(new straffDataWriter(mapMgr, curMapFtrType, SOMMapManager.sparseTrainDataSavedIDX, saveFileName, "sparseSVMData", mapMgr.trainData));	
			} else {
				saveFileName = getSOMMapLRNFileName();
				straffSOMDataWrite.add(new straffDataWriter(mapMgr, curMapFtrType, SOMMapManager.denseTrainDataSavedIDX, saveFileName, "denseLRNData", mapMgr.trainData));	
			}
		}
		if (expNumTest > 0) {
			saveFileName = getSOMMapTestFileName();
			straffSOMDataWrite.add(new straffDataWriter(mapMgr, curMapFtrType, SOMMapManager.testDataSavedIDX, saveFileName, useSparseTestingData ? "sparseSVMData" : "denseLRNData", mapMgr.testData));
		}
		try {straffSOMDataWriteFutures = th_exec.invokeAll(straffSOMDataWrite);for(Future<Boolean> f: straffSOMDataWriteFutures) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		
		
	}//launchTestTrainSaveThrds	
	//
	
	//build a date with each component separated by token
	private String[] getDateTimeString(Calendar now){return getDateTimeString(false,".", now);}
	private String[] getDateTimeString(boolean toSecond, String token,Calendar now){
		String res, resWithYear="", resWithoutYear="";
		int val;
		val = now.get(Calendar.YEAR);	
		resWithYear += ""+val+token;
		val = now.get(Calendar.MONTH)+1;		res = String.format("%02d", val) + token;	resWithYear += res;		resWithoutYear += res;		//(val < 10 ? "0"+val : ""+val)+ token;
		val = now.get(Calendar.DAY_OF_MONTH);	res = String.format("%02d", val) + token; 	resWithYear += res;		resWithoutYear += res;
		val = now.get(Calendar.HOUR_OF_DAY);	res = String.format("%02d", val) + token; 	resWithYear += res;		resWithoutYear += res;
		val = now.get(Calendar.MINUTE);			res = String.format("%02d", val);	 		resWithYear += res;		resWithoutYear += res;		
		if(toSecond){val = now.get(Calendar.SECOND);res = token + String.format("%02d", val) + token;	 resWithYear += res;		resWithoutYear += res;}
		
		return new String[] {resWithYear,resWithoutYear};
	}//getDateTimeString
			
	//build array with and without year of string representations of dates, used for file name access
	public void buildDateTimeStrAraAndDType(String _dType) {	dateTimeStrAra = getDateTimeString(false, "_", instancedNow); dataType = _dType;}//idx 0 has year, idx 1 does not
	
	//call when src data are first initialized - sets file names for .lrn file  and testing file output database query; also call when loading saved exp
	//dataFrmt : format used to train SOM == 0:unmodded; 1:std'ized; 2:normed
	public void setSOM_ExpFileNames(int _numSmpls, int _numTrain, int _numTest){
		//enable these to be set manually based on passed "now"		
		mapMgr.dispMessage("SOMProjConfigData","setSOM_ExpFileNames","Start setting file names and example counts");
		expNumSmpls = _numSmpls;
		expNumTrain = _numTrain;
		expNumTest = _numTest;
		String nowDir = getDirNameAndBuild(straffSOMProcSubDir+ "StraffSOM_"+dateTimeStrAra[0]+File.separator);
		String fileNow = dateTimeStrAra[1];
		setSOM_ExpFileNames( fileNow, nowDir);		
		mapMgr.dispMessage("SOMProjConfigData","setSOM_ExpFileNames","Finished setting file names and example counts");
	}//setSOM_ExpFileNames
	
	private void setSOM_ExpFileNames(String fileNow, String nowDir){
		String[] tmp = {"Out_"+SOMProjName+"_Smp_"+expNumSmpls,
						"Train_"+SOMProjName+"_Smp_"+expNumTrain+"_of_"+expNumSmpls+"_typ_" +dataType + "_Dt_"+fileNow+".lrn",
						"Train_"+SOMProjName+"_Smp_"+expNumTrain+"_of_"+expNumSmpls+"_typ_" + dataType+"_Dt_"+fileNow+".svm",				
						"Test_"+SOMProjName+"_Smp_"+expNumTest+"_of_"+expNumSmpls+"_typ_" +dataType +"_Dt_"+fileNow+".svm",
						"Mins_"+SOMProjName+"_Smp_"+expNumSmpls+"_Dt_"+fileNow+"_typ_" +dataType +".csv",
						"Diffs_"+SOMProjName+"_Smp_"+expNumSmpls+"_Dt_"+fileNow+"_typ_" +dataType +".csv",
						"SOMImg_"+SOMProjName+"_Smp_"+expNumSmpls+"_Dt_"+fileNow+"_typ_" +dataType +".png",
						"SOM_EXP_Format_"+SOMProjName+"_Smp_"+expNumSmpls+"_Dt_"+fileNow+".txt",
						"SOM_Config_"+SOMProjName+"_Smp_"+expNumSmpls+"_Dt_"+fileNow+".txt"
						};
		SOMFileNamesAra = new String[tmp.length];
		
		for(int i =0; i<SOMFileNamesAra.length;++i){	
			//build dir as well
			SOMFileNamesAra[i] = nowDir + tmp[i];
			mapMgr.dispMessage("SOMProjConfigData","setSOM_ExpFileNames","Built Dir : " + SOMFileNamesAra[i]);
		}				
	}//setSOM_ExpFileNames
	
	public String getFullCalcInfoFileName(){ return getDirNameAndBuild(straffCalcInfoSubDir) + calcWtFileName;}
	//file name to save record of bad events
	public String getBadEventFName(String dataFileName) { return straffBasefDir + dataFileName +"_bad_OIDs.csv";	}
	
	//call this to set up debug map call - TODO replace this with just loading from a default config file
	public void setSOM_UseDBGMap() {		//TOOD replace this when saved file with appropriate format
		//load map values from pre-trained map using this data - IGNORES VALUES SET IN UI	
		//setSOMNamesDBG(_DBG_Map_numSmpls, _DBG_Map_numTrain, _DBG_Map_numTest, _DBG_Map_fileNow, mapMgr.getDataTypeNameFromInt(_DBG_dataFrmt));
		dateTimeStrAra = new String[] {_DBG_Map_fldrNow, _DBG_Map_fileNow};
		dataType = mapMgr.getDataTypeNameFromInt(_DBG_dataFrmt);		
		//set to debug suffix on output 
		SOMOutExpSffx = _DBG_PreBuiltMapConfig;	
		setSOM_ExpFileNames(_DBG_Map_numSmpls, _DBG_Map_numTrain,_DBG_Map_numTest);
		//with file names built, now can load pre-made SOM exe config and program config files
		loadSOM_Exp();
		//structure holding SOM_MAP specific cmd line args and file names and such
		
		//now load new map data and configure SOMMapManager obj to hold all appropriate data
		mapMgr.setLoaderRtnFalse();
		setAllFileNames();		
	}//setSOM_UseDBGMap
	
	//call this before running SOM experiment
	public boolean setSOM_ExperimentRun(HashMap<String, Integer> mapInts, HashMap<String, Float> mapFloats, HashMap<String, String> mapStrings) {		
		boolean res = true;
		SOMExeDat.setArgsMapData(this, mapInts, mapFloats, mapStrings);		
		mapMgr.dispMessage("SOMProjConfigData","setSOM_ExperimentRun","SOM map descriptor : \n" + SOMExeDat.toString() + "SOMOutExpSffx str : " + SOMOutExpSffx);		
		//save configuration data
		saveSOM_Exp();
		//launch in a thread? - needs to finish to be able to proceed, so not necessary
		boolean runSuccess = mapMgr.buildNewMap(SOMExeDat);
		if(!runSuccess) {
			return false;
		}
		mapMgr.setLoaderRtnFalse();
		setAllFileNames();
		return res;	
	}//setSOM_ExperimentRun
	
	//return if map is toroidal
	public boolean isToroidal(){
		if(null==SOMExeDat){return false;}
		return SOMExeDat.isToroidal();
	}
	
	//moved from map manager
	public void setCurDataDir() {SOMDataDir = getDirNameAndBuild(straffSOMProcSubDir);	}
	
	//get location for raw data files
	public String[] getRawDataLoadInfo(boolean fromFiles, String baseFName) {
		String dataLocStrData = "";
		if (fromFiles) {
			dataLocStrData = straffBasefDir + straffSourceCSVSubDir + baseFName + File.separator + baseFName+".csv";
		} else {//SQL connection configuration needs to be determined/designed
			dataLocStrData = straffBasefDir + straffSQLProcSubDir + "sqlConnData_"+baseFName+".csv";
			mapMgr.dispMessage("SOMProjConfigData","getLoadRawDataStrs","Need to construct appropriate sql connection info and put in text config file : " + dataLocStrData);
		}
		return new String[] {baseFName,dataLocStrData};
	}//getRawDataLoadInfo
	
	//build prospect data directory structures based on current date
	public String[] buildProccedDataCSVFNames(boolean eventsOnly, String _desSuffix) {
		String[] dateTimeStrAra = getDateTimeString(false, "_", instancedNow);
		String subDir = "preprocData_" + dateTimeStrAra[0] + File.separator;
		return buildProccedDataCSVFNames(subDir, eventsOnly,_desSuffix);
	}//buildPrspctDataCSVFNames
	
	//build the file names for the csv files used to save intermediate data from db that has been partially preprocessed
	public String[] buildProccedDataCSVFNames(String subDir, boolean eventsOnly, String _desSuffix) {
		//build root preproc data dir if doesn't exist
		String rootDestDir = getDirNameAndBuild(straffPreProcSubDir);
		//build subdir based on date, if doesn't exist
		String destDir = getDirNameAndBuild(rootDestDir, subDir);
		String suffix;
		if (eventsOnly) {	suffix = _desSuffix + "Evnts";}
		else {				suffix = _desSuffix;}		
		String destForPrspctFName = destDir + suffix;				
		return new String[] {destForPrspctFName, suffix, rootDestDir};
	}//buildPrspctDataCSVFNames	
	
	//this will retrieve a subdirectory name under the main directory of this project and build the subdir if it doesn't exist
	//subdir assumed to have file.separator already appended (might not be necessary)
	private String getDirNameAndBuild(String subdir) {return getDirNameAndBuild(straffBasefDir,subdir);} 
	//baseDir must exist already
	public String getDirNameAndBuild(String baseDir, String subdir) {
		String dirName = baseDir +subdir;
		File directory = new File(dirName);
	    if (! directory.exists()){ directory.mkdir(); }
		return dirName;
	}//getDirNameAndBuild
	
	public String getOSUsed() {return OSUsed;}
	public String getSOM_Map_EXECSTR() {return SOM_Map_EXECSTR;}	
	//get instancedNow calendar, for time keeping
	public Calendar getInstancedNow() {return instancedNow;}
	
	public String getSOMProjName() { return SOMProjName;}
	public String getSOM_FullExecPath() { return getDirNameAndBuild(straffSOMProcSubDir);}
	
	public String getSOMMapOutFileBase(String sffx) {	SOMOutExpSffx = sffx; return getSOMMapOutFileBase();}
	private String getSOMMapOutFileBase() { 			return SOMFileNamesAra[0] + SOMOutExpSffx;}
	public String getSOMMapLRNFileName(){	return SOMFileNamesAra[1];	}
	public String getSOMMapSVMFileName(){	return SOMFileNamesAra[2];	}
	public String getSOMMapTestFileName(){	return SOMFileNamesAra[3];	}
	public String getSOMMapMinsFileName(){	return SOMFileNamesAra[4];	}
	public String getSOMMapDiffsFileName(){	return SOMFileNamesAra[5];	}	
	
	public String getSOMLocClrImgForJPFName(int jpIDX) {return "jp_"+mapMgr.jpJpgrpMon.getJpByIdx(jpIDX)+"_"+SOMFileNamesAra[6];}	
	//ref to file name for experimental setup
	public String getSOMMapExpFileName() {return SOMFileNamesAra[7];	}	
	//ref to file name for config setup
	public String getSOMConfigFileName() {return SOMFileNamesAra[8];	}	
	
	//set training size partition -> fraction of entire data set that is training data
	public void setTrainTestPartition(float _ratio) {trainTestPartition = _ratio;}
	public float getTrainTestPartition() {return trainTestPartition;}
	//use preset data partitions
	public void setTrainTestPartitionDBG() {trainTestPartition = _DBG_Map_numTrain/(1.0f*_DBG_Map_numSmpls);}
	
	//sets all file names to be loaded - assumes names to be valid
	private void setAllFileNames(){
		String _somResBaseFName = getSOMMapOutFileBase();
		mapMgr.dispMessage("SOMProjConfigData","setAllFileNames","Names being set using SOMOutExpSffx str : " + SOMOutExpSffx);
		String somCSVBaseFName = _somResBaseFName + "_outCSV";
		
		String diffsFileName = getSOMMapDiffsFileName(),	//diffs data percol .csv file
				minsFileName = getSOMMapMinsFileName();		//mins data per col csv
		/**
		 * load data to represent map results
		 * @param cFN class file name
		 * @param diffsFileName per-feature differences file name
		 * @param minsFileName per-feature mins file name
		 * @param lrnFileName som training data file name 
		 * @param outFilePrfx
		 * @param csvOutBaseFName file name prefix used to save class data in multiple formats to csv files
		 */
		String _trainDataFName = (useSparseTrainingData) ?  getSOMMapSVMFileName() : getSOMMapLRNFileName();
		setFileName(trainDatFNameIDX, _trainDataFName);
		setFileName(somResFPrfxIDX, _somResBaseFName);
		setFileName(diffsFNameIDX, diffsFileName);
		setFileName(minsFNameIDX, minsFileName);
		setFileName(csvSavFNameIDX, somCSVBaseFName);
	}//setAllFileNames
	
	private void setFileName(int _typ, String _val){
		if(!fnames[_typ].equals(_val)){
			fnames[_typ] = _val;		//set appropriate file name
			setFlag(isDirty, true);		//"false"'s all flags for each file, if first time dirty is set
		}
		setFlag(_typ,true);		//flag idx is same as string idx
		setFlag(isDirty,!allReqFilesLoaded());	//should clear dirty flag when all files are loaded
	}//setFileName
	
	//call only 1 time, when setting isDirty to true - only run once per dirty flag being in true state
	private void setDirty(){for(int i=0;i<reqFileNameFlags.length;++i){setFlag(reqFileNameFlags[i],false);}}	
	//return true if all required files are loaded
	public boolean allReqFilesLoaded(){for(int i=0;i<reqFileNameFlags.length;++i){if(!getFlag(reqFileNameFlags[i])){return false;}}return true;}
	
	public String getAllDirtyFiles(){
		String res = "";
		for(int i=0;i<reqFileNameFlags.length;++i){if(getFlag(reqFileNameFlags[i])){res += reqFileNames[i]+", ";}}
		return res;
	}
		
	public String getSOMResFName(int ext){return (fnames[somResFPrfxIDX] + SOMResExtAra[ext]);}
	
	private void initFlags(){stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlag(i,false);}}
	public void setFlag(int idx, boolean val){
		boolean oldVal = getFlag(idx);
		int flIDX = idx/32, mask = 1<<(idx%32);
		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case isDirty 				: {if((oldVal != val) && (val)){setDirty();}break;}	//if transition to true, run setDirty 				
			case trainDatFNameIDX		: {break;}//
			case somResFPrfxIDX			: {break;}//
			case diffsFNameIDX			: {break;}//
			case minsFNameIDX			: {break;}//
			case csvSavFNameIDX			: {break;}//
			case outputSffxSetIDX		: {break;}//output file name suffix has been set
		}
	}//setFlag	
	private boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		
	@Override
	public String toString(){
		String res = "SOM Project Data Config Cnstrct : \n";
		res += "\ttrainDatFNameIDX = " + fnames[trainDatFNameIDX]+"\n";
		res += "\tsomResFNameIDX = " + fnames[somResFPrfxIDX]+"\n";
		res += "\tdiffsFNameIDX = " + fnames[diffsFNameIDX]+"\n";
		res += "\tminsFNameIDX = " + fnames[minsFNameIDX]+"\n";
		res += "\tcsvSavFNameIDX = " + fnames[csvSavFNameIDX]+"\n";
		return res;	
	}

}
