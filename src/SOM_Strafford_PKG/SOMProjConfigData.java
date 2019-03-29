package SOM_Strafford_PKG;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;


//structure to hold all the file names, file configurations and general program configurations required to run the SOM project
//will manage that all file names need to be reset when any are changed
public class SOMProjConfigData {
	//owning map manager
	protected StraffSOMMapManager mapMgr;
	//manage IO in this object
	private FileIOManager fileIO;
	
	//ref to SOM_MapDat SOM executiond descriptor object
	protected SOM_MapDat SOMExeDat;	
	//string delimiter defining a comment in a file
	public static final String fileComment = "#";
	
	//this is redundant TODO merge the use of this with SOMFileNamesAra
	private String[] fnames; 
	private static final int numFiles = 5;		
		
	//TODO these are a function of data stucture, so should be set via config file - will be constant for strafford
	public boolean useSparseTrainingData = true;
	public boolean useSparseTestingData = true;
	
	//fileNow date string array for most recent experiment
	private String[] dateTimeStrAra;

	//test/train partition - ratio of # of training data points to total # of data points
	private float trainTestPartition;	
	//current experiment # of samples total, train partition and test partition
	private int expNumSmpls, expNumTrain, expNumTest;
	//current type of data used to train map - unmodified features, normalized features (ftr vec sums to 1) or standardized features (normalized across all data examples per ftr)
	private String dataType;
	
	//calendar object to be used to query instancing time
	private final Calendar instancedNow;
	//os used by project - this is passed from map
	private final String OSUsed;
	//whether current OS supports ansi terminal color settings
	public static boolean supportsANSITerm = false;
	//base sub directory where the source data, SOM and output will be found
	private static final String _baseDir = "StraffordProject" + File.separator;
	//sub directory where project config files are found
	private static final String configDir = "config" + File.separator;
	//file name of project config file
	private static final String projectConfigFile = "projectConfig.txt";
	//file name of experimental config for a particular experiment
	private static final String expProjConfigFileName = "SOM_EXEC_Proj_Config.txt";
	
	//fully qualified source directory for reading all source csv files, writing intermediate outputs, executing SOM_MAP and writing results
	private String straff_QualifedBaseDir;
	//name of som executable
	private final String SOMExeName_base = "straff_SOM";

	//short name to be used in file names to denote this project
	private final String SOMProjName = "straff";
	//string to invoke som executable - platform dependent
	private final String SOM_Map_EXECSTR;

	// config file names; file IO/subdirectory locations
	private HashMap<String,String> configFileNames, subDirLocs;
	
	//directory under SOM where prebuilt map resides that is desired to be loaded into UI - replaces dbg files - set in project config file
	private String preBuiltMapDir;
	
	//type of event membership that defines a prospect as a customer and as a true prospect (generally will be cust has order event, prospect doesnt)
	private int custTruePrsTypeEvents;

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
	
	public static final String[] SOMResExtAra = new String[]{".wts", ".bm",".umx"};			//extensions for different SOM output file types
	//idxs of different kinds of SOM output files
	public static final int
		wtsIDX = 0,
		bmuIDX = 1,
		umtxIDX = 2;	
	//all flags corresponding to file names required to run SOM
	private int[] reqFileNameFlags = new int[]{trainDatFNameIDX, somResFPrfxIDX, diffsFNameIDX, minsFNameIDX, csvSavFNameIDX};
	private String[] reqFileNames = new String[]{"trainDatFNameIDX", "somResFPrfxIDX", "diffsFNameIDX", "minsFNameIDX", "csvSavFNameIDX"};
	//idx 0 = file name for SOM training data -> .lrn file, 1 = file name for sphere sample data -> .csv file	
	private String[] SOMFileNamesAra;
	//this string holds experiment-specific string used for output files - x,y and k of map used to generate output.  this is set 
	//separately from calls to setSOM_ExpFileNames because experimental parameters can change between the saving of training data and the running of the experiment
	private String SOMOutExpSffx;
	
	public SOMProjConfigData(StraffSOMMapManager _map) {
		mapMgr=_map;
		try {
			straff_QualifedBaseDir = new File(_baseDir).getCanonicalPath() + File.separator ;
		} catch (Exception e) {
			straff_QualifedBaseDir = _baseDir;
			mapMgr.dispMessage("SOMProjConfigData","Constructor","Failed to find base application directory "+ straff_QualifedBaseDir + " due to : " + e + ". Exiting program.", MsgCodes.error1);
			System.exit(1);
		}
		mapMgr.dispMessage("SOMProjConfigData","Constructor","Canonical Path to application directory : " + straff_QualifedBaseDir, MsgCodes.info1);
		fileIO = new FileIOManager(mapMgr,"SOMProjConfigData");		
		//----accumulate and manage OS info ----//
		//find platform this is executing on supportsANSITerm
		OSUsed = System.getProperty("os.name");
		//set invoking string for map executable - is platform dependent
		String execStr = SOMExeName_base;
		if (OSUsed.toLowerCase().contains("windows")) {			execStr += ".exe";			} 
		supportsANSITerm = (System.console() != null && System.getenv().get("TERM") != null);		
		mapMgr.dispMessage("SOMProjConfigData","Constructor","OS this application is running on : "  + OSUsed + " | Supports ANSI Terminal colors : " + supportsANSITerm, MsgCodes.info5);		
		SOM_Map_EXECSTR = execStr;				
		//----end accumulate and manage OS info ----//		
		//load project configuration
		loadProjectConfig();
		
		SOMOutExpSffx = "x-1_y-1_k-1";//illegal values, needs to be set
		dataType = "NONE";
		//get current time
		instancedNow = Calendar.getInstance();
		fnames = new String[numFiles];
		for(int i=0;i<numFiles;++i){fnames[i]="";}
		initFlags();
		SOMExeDat = new SOM_MapDat(getOSUsed());	
		dateTimeStrAra = getDateTimeString(false, "_");
	}//ctor
	
	//this will load the project config file and set initial project-wide settings
	private void loadProjectConfig() {
		mapMgr.dispMessage("SOMProjConfigData","loadProjectConfig","Start loading project configuration.", MsgCodes.info5);
		//build config file name and load config data
		String configFileName = getDirNameAndBuild(configDir) + projectConfigFile;
		String[] fileStrings = fileIO.loadFileIntoStringAra(configFileName, "SOMProjConfigData Main Project Config File loaded", "SOMProjConfigData Main Project Config File Failed to load");
		//init maps holding config data
		configFileNames = new HashMap<String,String>();
		subDirLocs = new HashMap<String,String>(); 
		int idx = 0; boolean found = false;
		//find start of first block of data
		while (!found && (idx < fileStrings.length)){if(fileStrings[idx].contains(fileComment)) {++idx; } else {found=true;}}
		// CONFIG FILE NAMES
		idx = _loadProjConfigData(fileStrings, configFileNames, idx, false);//returns next idx, fills config variables
			if(idx == -1) {mapMgr.dispMessage("SOMProjConfigData","loadProjectConfig","Error after _loadProjConfigData with configFileNames : idx == -1", MsgCodes.error2); return;}
		// SUBDIR DEFS
		idx = _loadProjConfigData(fileStrings, subDirLocs, idx, true);//returns next idx, fills subdir variables
			if(idx == -1) {mapMgr.dispMessage("SOMProjConfigData","loadProjectConfig","Error after _loadProjConfigData with subDirLocs : idx == -1", MsgCodes.error2); return;}
		
		// MISC GLOBAL VARS
		//read through individual config vars
		idx = _loadIndivConfigVars(fileStrings, idx); 
		mapMgr.dispMessage("SOMProjConfigData","loadProjectConfig","preBuiltMapDir set to be : " + preBuiltMapDir, MsgCodes.info3);
			if(idx == -1) {mapMgr.dispMessage("SOMProjConfigData","loadProjectConfig","Error after _loadIndivConfigVars : idx == -1", MsgCodes.error2); return;}
		mapMgr.dispMessage("SOMProjConfigData","loadProjectConfig","Finished loading project configuration.", MsgCodes.info5);
	}//loadProjectConfig
	
	//load all file name/value pairs in config file into passed map; return idx of next section
	private int _loadProjConfigData(String[] fileStrings, HashMap<String,String> map, int stIDX, boolean useFileDelim) {
		String sfx = (useFileDelim ? File.separator : "");
		for(int i=stIDX; i<fileStrings.length;++i) {
			String s = fileStrings[i];		
			if(s.contains("END")) {return i+1;}//move to next line, should be "begin" tag
			if((s.contains(fileComment))|| (s.trim().length() == 0)) {continue;}
			String[] tkns = s.trim().split(mapMgr.csvFileToken);
			//map has keys that describe what the values are
			String key = tkns[0].trim(), val= tkns[1].trim().replace("\"", "")+sfx;
			map.put(key,val);	
			mapMgr.dispMessage("SOMProjConfigData","_loadProjConfigData","Key : "+key+" | Val : " + val, MsgCodes.info3);
		}		
		return -1;	
	}//_loadProjConfigData
	
	private int _loadIndivConfigVars(String[] fileStrings, int stIDX) {
		boolean endFound = false;
		while((stIDX < fileStrings.length) && (!endFound)) {
			String s = fileStrings[stIDX];
			if(s.contains("END")) {return stIDX+1;}//move to next line, should be "begin" tag
			if((s.contains(fileComment)) || (s.trim().length() == 0)){++stIDX; continue;}
			String[] tkns = s.trim().split(mapMgr.csvFileToken);
			switch (tkns[0].trim()) {
				case "preBuiltMapDir" : { 		preBuiltMapDir = tkns[1].trim().replace("\"", "") + File.separator; break;}
				case "custTruePrsTypeEvents": {	custTruePrsTypeEvents = Integer.parseInt(tkns[1].trim().replace("\"", ""));		break;}
			}	
			++stIDX;
		}
		return -1;			
	}//_loadIndivConfigVars
	
	public TreeMap<String, String[]> buildFileNameMap(String[] dataDirNames) {
		mapMgr.dispMessage("SOMProjConfigData","buildFileNameMap","Begin building list of raw data file names for each type of data.", MsgCodes.info3);
		TreeMap<String, String[]> straffDataFileNames = new TreeMap<String, String[]>();
		//for each directory, find all file names present, stripping ".csv" from name
		for (int dIdx = 0; dIdx < dataDirNames.length;++dIdx) {
			String dirName = straff_QualifedBaseDir + subDirLocs.get("straffSourceCSV") + dataDirNames[dIdx];
			File folder = new File(dirName);
			File[] listOfFiles = folder.listFiles();
			ArrayList<String> resList = new ArrayList<String>();
			for (File file : listOfFiles) {
			    if (file.isFile()) {
			    	String baseFileName = file.getName();
			    	String[] fileNameTkns = baseFileName.split("\\.");
			    	if(fileNameTkns[fileNameTkns.length-1].contains("csv")) {
			    		String fileName = "";
			    		for (int i=0;i<fileNameTkns.length-2;++i) { 			fileName += fileNameTkns[i] +".";  		}
			    		fileName += fileNameTkns[fileNameTkns.length-2];
			    		resList.add(fileName);
			    	}
			    }//if is file and not dir
			}//for each dir/file in list
			straffDataFileNames.put(dataDirNames[dIdx], resList.toArray(new String[0]));
		}//		
		mapMgr.dispMessage("SOMProjConfigData","buildFileNameMap","Finished building list of raw data file names for each type of data.", MsgCodes.info3);
		return straffDataFileNames;
	}//buildFileNameMap
	
	//return int representing type of events that should be used to define a prospect as a customer (generally has a order event in history) and a true prospect (lacks orders but has sources)
	public int getTypeOfEventsForCustAndProspect(){		return custTruePrsTypeEvents;}//getTypeOfEventsForCustAndProspect()
	
	//this will save all essential information for a SOM-based experimental run, to make duplication of experiment easier
	//Info saved : SOM_MapData; 
	public void saveSOM_Exp() {
		mapMgr.dispMessage("SOMProjConfigData","saveSOM_Map Config","Saving SOM Exe Map config data.", MsgCodes.info5);
		//build file describing experiment and put at this location
		String mapConfigFileName = getSOMMapConfigFileName();
		//get array of data describing SOM training exec info
		ArrayList<String> SOMDescAra = SOMExeDat.buildStringDescAra();
		fileIO.saveStrings(mapConfigFileName,SOMDescAra);
		mapMgr.dispMessage("SOMProjConfigData","saveSOM_Exp","Finished saving SOM Exe Map config data.", MsgCodes.info5);
		mapMgr.dispMessage("SOMProjConfigData","saveSOM_Exp","Saving project configuration data for current SOM execution.", MsgCodes.info5);
		String configFileName = getProjConfigForSOMExeFileName();
		//get array of data describing config info
		ArrayList<String> ConfigDescAra = getExpConfigData();
		fileIO.saveStrings(configFileName,ConfigDescAra);		
		mapMgr.dispMessage("SOMProjConfigData","saveSOM_Exp","Finished saving project configuration data for current SOM execution.", MsgCodes.info5);
	}//saveSOM_Exp
		
	//save configuration data describing
	private ArrayList<String> getExpConfigData(){
		ArrayList<String> res = new ArrayList<String>();
		res.add(SOMProjConfigData.fileComment + " Below is config data for a particular experimental setup");
		res.add(SOMProjConfigData.fileComment + " Base Configuration Data");
		res.add("useSparseTrainingData,"+useSparseTrainingData);
		res.add("useSparseTestingData,"+useSparseTestingData);
		res.add("trainTestPartition,"+String.format("%.6f", trainTestPartition));
		res.add("expNumSmpls,"+expNumSmpls);
		res.add("expNumTrain,"+expNumTrain);
		res.add("expNumTest,"+expNumTest);
		res.add("dataType,"+dataType);
		res.add("dateTimeStrAra[0],"+dateTimeStrAra[0]);
		res.add("dateTimeStrAra[1],"+dateTimeStrAra[1]);
		for(int i =0; i<SOMFileNamesAra.length;++i) {res.add("SOMFileNamesAra["+i+"],"+SOMFileNamesAra[i]);		}
		res.add(SOMProjConfigData.fileComment + " End Configuration Data");
		return res;
	}//getExpConfigData()	
	
	//SOM_EXP_Format_default
	//this will load all essential information for a SOM-based experimental run, from loading preprocced data, configuring map
	public void loadDefaultSOMExp_Config() {
		String dfltSOMConfigFName = getDirNameAndBuild(configDir) + configFileNames.get("SOMDfltConfigFileName"); 
		mapMgr.dispMessage("SOMProjConfigData","loadDefaultSOMExp_Config","Default file name  :" + dfltSOMConfigFName, MsgCodes.info1);
		loadSOMMap_Config(dfltSOMConfigFName);
	}
	public void loadSOMMap_Config() {loadSOMMap_Config(getSOMMapConfigFileName());}  
	public void loadSOMMap_Config(String expFileName) {
		mapMgr.dispMessage("SOMProjConfigData","loadSOMMap_Config","Start loading SOM Exe config data from " + expFileName, MsgCodes.info5);
		//build file describing experiment and put at this location
		//NOTE! if running a debug run, be sure to have the line dateTimeStrAra[0],<date_time_value>_DebugRun in proj config file, otherwise will crash
		String[] expStrAra = fileIO.loadFileIntoStringAra(expFileName, "SOM_MapDat Config File loaded", "SOM_MapDat Config File Failed to load");
		SOMExeDat.buildFromStringArray(expStrAra);
		mapMgr.setUIValsFromLoad(SOMExeDat);
		mapMgr.dispMessage("SOMProjConfigData","loadSOMMap_Config","Finished loading SOM Exe config data from " + expFileName, MsgCodes.info5);
	}//loadSOM_Exp
	//load a specific configuration based on a previously run experiment
	public void loadProjConfigForSOMExe() {	loadProjConfigForSOMExe( getProjConfigForSOMExeFileName());}
	private void loadProjConfigForSOMExe(String configFileName) {
		mapMgr.dispMessage("SOMProjConfigData","loadProjConfigForSOMExe","Start loading project config data for SOM Execution : "+ configFileName, MsgCodes.info5);
		//NOTE! if running a debug run, be sure to have the line dateTimeStrAra[0],<date_time_value>_DebugRun in proj config file, otherwise will crash
		String[] configStrAra = fileIO.loadFileIntoStringAra(configFileName, "SOMProjConfigData project config data for SOM Execution File loaded", "SOMProjConfigData project config data for SOM Execution Failed to load");
		setExpConfigData(configStrAra);
		mapMgr.dispMessage("SOMProjConfigData","loadProjConfigForSOMExe","Finished loading project config data for SOM Execution", MsgCodes.info5);				
	}//loadProg_Congfig
		
	//set experiment vars based on data saved in config file
	private void setExpConfigData(String[] dataAra) {
		dateTimeStrAra = new String[2];
		ArrayList<String> somFileNamesAraTmp = new ArrayList<String>();
		for (int i=0;i<dataAra.length;++i) {
			String str = dataAra[i];
			String[] strToks = str.trim().split(mapMgr.csvFileToken);
			if(strToks[0].trim().contains("SOMFileNamesAra")){//read in pre-saved directory and file names
				String[] araStrToks_1 = str.trim().split("\\[");
				String[] araStrToks_2 = araStrToks_1[1].trim().split("\\]");
				int idx = Integer.parseInt(araStrToks_2[0]);				
				somFileNamesAraTmp.add(idx, strToks[1].trim());
			} else {
				switch(strToks[0].trim()) {
					case "useSparseTrainingData" : 	{  	 useSparseTrainingData = Boolean.parseBoolean(strToks[1].trim());break;}
					case "useSparseTestingData" : 	{    useSparseTestingData = Boolean.parseBoolean(strToks[1].trim());break;}
					case "trainTestPartition" : 	{    trainTestPartition = Float.parseFloat(strToks[1].trim());break;}
					case "dataType" : 				{    dataType = strToks[1].trim();break;}
					case "expNumSmpls" : 			{    expNumSmpls = Integer.parseInt(strToks[1].trim());	break;}
					case "expNumTrain" : 			{    expNumTrain = Integer.parseInt(strToks[1].trim());	break;}
					case "expNumTest" : 			{    expNumTest = Integer.parseInt(strToks[1].trim());	break;}
					case "dateTimeStrAra[0]" : 		{    dateTimeStrAra[0] = strToks[1].trim();				break;}
					case "dateTimeStrAra[1]" : 		{    dateTimeStrAra[1] = strToks[1].trim();				break;}			
					default : {}		
				}
			}
		}//for each line	
		trainTestPartition = expNumTrain/(1.0f*expNumSmpls);
		if(somFileNamesAraTmp.size() > 0) {		SOMFileNamesAra = somFileNamesAraTmp.toArray(new String[0]);} 
		else {									setSOM_ExpFileNames(expNumSmpls, expNumTrain, expNumTest);}
	}//setExpConfigData	
		
	//save test/train data in multiple threads
	public void launchTestTrainSaveThrds(ExecutorService th_exec, int curMapFtrType) {
		String saveFileName = "";
		List<Future<Boolean>> straffSOMDataWriteFutures = new ArrayList<Future<Boolean>>();
		List<straffDataWriter> straffSOMDataWrite = new ArrayList<straffDataWriter>();
		//call threads to instance and save different file formats
		if (expNumTrain > 0) {//save training data
			if (useSparseTrainingData) {
				saveFileName = getSOMMapSVMFileName();
				straffSOMDataWrite.add(new straffDataWriter(mapMgr, curMapFtrType, StraffSOMMapManager.sparseTrainDataSavedIDX, saveFileName, "sparseSVMData", mapMgr.trainData));	
			} else {
				saveFileName = getSOMMapLRNFileName();
				straffSOMDataWrite.add(new straffDataWriter(mapMgr, curMapFtrType, StraffSOMMapManager.denseTrainDataSavedIDX, saveFileName, "denseLRNData", mapMgr.trainData));	
			}
		}
		if (expNumTest > 0) {//save testing data
			saveFileName = getSOMMapTestFileName();
			straffSOMDataWrite.add(new straffDataWriter(mapMgr, curMapFtrType, StraffSOMMapManager.testDataSavedIDX, saveFileName, useSparseTestingData ? "sparseSVMData" : "denseLRNData", mapMgr.testData));
		}
		try {straffSOMDataWriteFutures = th_exec.invokeAll(straffSOMDataWrite);for(Future<Boolean> f: straffSOMDataWriteFutures) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		
		
	}//launchTestTrainSaveThrds	
	
	//build a date with each component separated by token
	private String[] getDateTimeString(){return getDateTimeString(false,".");}
	private String[] getDateTimeString(boolean toSecond, String token){
		Calendar now = instancedNow;
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
	public void buildDateTimeStrAraAndDType(String _dType) {	dateTimeStrAra = getDateTimeString(false, "_"); dataType = _dType;}//idx 0 has year, idx 1 does not
	
	//call when src data are first initialized - sets file names for .lrn file  and testing file output database query; also call when loading saved exp
	//dataFrmt : format used to train SOM == 0:unmodded; 1:std'ized; 2:normed
	public void setSOM_ExpFileNames(int _numSmpls, int _numTrain, int _numTest){
		//enable these to be set manually based on passed "now"		
		mapMgr.dispMessage("SOMProjConfigData","setSOM_ExpFileNames","Start setting file names and example counts", MsgCodes.info5);
		expNumSmpls = _numSmpls;
		expNumTrain = _numTrain;
		expNumTest = _numTest;
		String nowDir = getDirNameAndBuild(subDirLocs.get("straffSOMProc") + "StraffSOM_"+dateTimeStrAra[0]+File.separator);
		String fileNow = dateTimeStrAra[1];
		setSOM_ExpFileNames( fileNow, nowDir);		
		mapMgr.dispMessage("SOMProjConfigData","setSOM_ExpFileNames","Finished setting file names and example counts", MsgCodes.info5);
	}//setSOM_ExpFileNames
	
	//file names used specifically for SOM data
	private void setSOM_ExpFileNames(String fileNow, String nowDir){
		String[] tmp = {"Out_"+SOMProjName+"_Smp_"+expNumSmpls,
						"Train_"+SOMProjName+"_Smp_"+expNumTrain+"_of_"+expNumSmpls+"_typ_" +dataType + "_Dt_"+fileNow+".lrn",
						"Train_"+SOMProjName+"_Smp_"+expNumTrain+"_of_"+expNumSmpls+"_typ_" + dataType+"_Dt_"+fileNow+".svm",				
						"Test_"+SOMProjName+"_Smp_"+expNumTest+"_of_"+expNumSmpls+"_typ_" +dataType +"_Dt_"+fileNow+".svm",
						"Mins_"+SOMProjName+"_Smp_"+expNumSmpls+"_Dt_"+fileNow+"_typ_" +dataType +".csv",
						"Diffs_"+SOMProjName+"_Smp_"+expNumSmpls+"_Dt_"+fileNow+"_typ_" +dataType +".csv",
						"SOMImg_"+SOMProjName+"_Smp_"+expNumSmpls+"_Dt_"+fileNow+"_typ_" +dataType +".png",
						"SOM_MapConfig_"+SOMProjName+"_Smp_"+expNumSmpls+"_Dt_"+fileNow+".txt",						//map configuration
						expProjConfigFileName					//
						};
		SOMFileNamesAra = new String[tmp.length];		
		for(int i =0; i<SOMFileNamesAra.length;++i){	
			//build dir as well
			SOMFileNamesAra[i] = nowDir + tmp[i];
			mapMgr.dispMessage("SOMProjConfigData","setSOM_ExpFileNames","Built Dir : " + SOMFileNamesAra[i], MsgCodes.info3);
		}				
	}//setSOM_ExpFileNames	

	
	//get 
	//return subdirectory to use to write results for product with passed OID
	public String getPerProdOutSubDirName(String fullBaseDir,  String OID) {return getDirNameAndBuild(fullBaseDir, OID+File.separator);}
	public String getFullProdOutMapperBaseDir(String sfx) {
		String [] tmpNow = getDateTimeString(false, "_");
		return getDirNameAndBuild(subDirLocs.get("straffProdSuggest") + "PerProdMaps_"+sfx+"_"+ tmpNow[1] + "_data_" +dateTimeStrAra[0]+File.separator);}
	//these file names are specified above but may be modified/set via a config file in future
	public String getFullCalcInfoFileName(){ return getDirNameAndBuild(configDir) + configFileNames.get("calcWtFileName");}
	public String getFullProdOutMapperInfoFileName(){ return getDirNameAndBuild(configDir) + configFileNames.get("reqProdConfigFileName");}
	//call this to set up debug map call - TODO replace this with just loading from a default config file
	public void setSOM_UsePreBuilt() {		//TOOD replace this when saved file with appropriate format
		//load map values from pre-trained map using this data - IGNORES VALUES SET IN UI	
		//setSOMNamesDBG(_DBG_Map_numSmpls, _DBG_Map_numTrain, _DBG_Map_numTest, _DBG_Map_fileNow, mapMgr.getDataTypeNameFromInt(_DBG_dataFrmt));
//		dateTimeStrAra = new String[] {_DBG_Map_fldrNow, _DBG_Map_fileNow};
//		dataType = mapMgr.getDataTypeNameFromInt(_DBG_dataFrmt);		
//		//set to debug suffix on output 
//		SOMOutExpSffx = _DBG_PreBuiltMapConfig;	
//		setSOM_ExpFileNames(_DBG_Map_numSmpls, _DBG_Map_numTrain,_DBG_Map_numTest);
		//with file names built, now can load pre-made SOM exe experimental config...
		//build file name to load
		String configFileName = getDirNameAndBuild(subDirLocs.get("straffSOMProc") + preBuiltMapDir) + expProjConfigFileName;
		//load project config for this SOM execution
		loadProjConfigForSOMExe(configFileName);		
		//load and map config
		loadSOMMap_Config();
		//structure holding SOM_MAP specific cmd line args and file names and such
		SOMExeDat.setAllDirs(this);
		//now load new map data and configure SOMMapManager obj to hold all appropriate data
		mapMgr.setLoaderRtnFalse();
		setAllFileNames();		
	}//setSOM_UseDBGMap
	
	//call this before running SOM experiment
	public boolean setSOM_ExperimentRun(HashMap<String, Integer> mapInts, HashMap<String, Float> mapFloats, HashMap<String, String> mapStrings) {		
		boolean res = true;
		SOMExeDat.setArgsMapData(this, mapInts, mapFloats, mapStrings);		
		mapMgr.dispMessage("SOMProjConfigData","setSOM_ExperimentRun","SOM map descriptor : \n" + SOMExeDat.toString() + "SOMOutExpSffx str : " + SOMOutExpSffx, MsgCodes.info5);		
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
	
	//get location for raw data files
	//baseDirName : directory/file type name
	//baseFName : specific file base name (without extension) - will be same as baseDirName unless multiple files for specific file type were necessary for raw data due to size of data set
	public String getRawDataLoadInfo(boolean fromFiles, String baseDirName, String baseFName) {
		String dataLocStrData = "";
		if (fromFiles) {
			dataLocStrData = straff_QualifedBaseDir + subDirLocs.get("straffSourceCSV") + baseDirName + File.separator + baseFName+".csv";
		} else {//SQL connection configuration needs to be determined/designed
			dataLocStrData = straff_QualifedBaseDir + subDirLocs.get("straffSQLProc") + "sqlConnData_"+baseDirName+".csv";
			mapMgr.dispMessage("SOMProjConfigData","getLoadRawDataStrs","Need to construct appropriate sql connection info and put in text config file : " + dataLocStrData, MsgCodes.warning2);
		}
		return dataLocStrData;
	}//getRawDataLoadInfo
	//return the directory to the most recent prospect data as specified in config file (under project directory/
	//TODO replace this with info from global project config data 
	public String getRawDataDesiredDirName() {	return "default" + File.separator;	}

	
	//build prospect data directory structures based on current date
	public String[] buildProccedDataCSVFNames(boolean eventsOnly, String _desSuffix) {
		String[] dateTimeStrAra = getDateTimeString(false, "_");
		String subDir = "preprocData_" + dateTimeStrAra[0] + File.separator;
		return _buildDataCSVFNames(getDirNameAndBuild(subDirLocs.get("straffPreProc")),subDir, eventsOnly,_desSuffix);
	}//buildPrspctDataCSVFNames
	
	//build the file names for the csv files used to save intermediate data from db that has been partially preprocessed
	//subdir is just sub directory within root project directory; eventsOnly is old flag use to denote 
	//if examples were chosen based on having events for a particular jp or if prospects without any events could also be considered valid training examples
	//_desSuffix is text suffix describing file type
	public String[] buildProccedDataCSVFNames(String subDir, boolean eventsOnly, String _desSuffix) {
		//build root preproc data dir if doesn't exist
		return _buildDataCSVFNames(getDirNameAndBuild(subDirLocs.get("straffPreProc")), subDir, eventsOnly, _desSuffix);
	}
	private String[] _buildDataCSVFNames(String rootDestDir, String subDir, boolean eventsOnly, String _desSuffix) {
		//build subdir based on date, if doesn't exist
		String destDir = getDirNameAndBuild(rootDestDir, subDir);
		String suffix;
		if (eventsOnly) {	suffix = _desSuffix + "Evnts";}
		else {				suffix = _desSuffix;}		
		String destForFName = destDir + suffix;				
		return new String[] {destForFName, suffix, rootDestDir};
	}//buildPrspctDataCSVFNames	
	
	//this will retrieve a subdirectory name under the main directory of this project and build the subdir if it doesn't exist
	//subdir assumed to have file.separator already appended (might not be necessary)
	private String getDirNameAndBuild(String subdir) {return getDirNameAndBuild(straff_QualifedBaseDir,subdir);} 
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
	public String getSOM_FullExecPath() { return getDirNameAndBuild(subDirLocs.get("straffSOMProc"));}
	
	public String getSOMMapOutFileBase(String sffx) {	SOMOutExpSffx = sffx; return getSOMMapOutFileBase();}
	private String getSOMMapOutFileBase() { 			return SOMFileNamesAra[0] + SOMOutExpSffx;}
	public String getSOMMapLRNFileName(){	return SOMFileNamesAra[1];	}
	public String getSOMMapSVMFileName(){	return SOMFileNamesAra[2];	}
	public String getSOMMapTestFileName(){	return SOMFileNamesAra[3];	}
	public String getSOMMapMinsFileName(){	return SOMFileNamesAra[4];	}
	public String getSOMMapDiffsFileName(){	return SOMFileNamesAra[5];	}	
	
	public String getSOMLocClrImgForJPFName(int jpIDX) {return "jp_"+mapMgr.jpJpgrpMon.getJpByIdx(jpIDX)+"_"+SOMFileNamesAra[6];}	
	//ref to file name for map configuration setup
	public String getSOMMapConfigFileName() {return SOMFileNamesAra[7];	}	
	//ref to file name for data and project configuration relevant for current SOM execution
	public String getProjConfigForSOMExeFileName() {return SOMFileNamesAra[8];	}	
	
	//set training size partition -> fraction of entire data set that is training data
	public void setTrainTestPartition(float _ratio) {trainTestPartition = _ratio;}
	public float getTrainTestPartition() {return trainTestPartition;}
	
	//file name to save record of bad events
	public String getBadEventFName(String dataFileName) { return straff_QualifedBaseDir + dataFileName +"_bad_OIDs.csv";	}
	
	
	//sets all file names to be loaded - assumes names to be valid
	private void setAllFileNames(){
		String _somResBaseFName = getSOMMapOutFileBase();
		mapMgr.dispMessage("SOMProjConfigData","setAllFileNames","Names being set using SOMOutExpSffx str : " + SOMOutExpSffx, MsgCodes.info1);
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
		res += "\ttrainDatFNameIDX = " + fnames[trainDatFNameIDX]+"\n" +"\tsomResFNameIDX = " + fnames[somResFPrfxIDX]+"\n";
		res += "\tdiffsFNameIDX = " + fnames[diffsFNameIDX]+"\n"+  "\tminsFNameIDX = " + fnames[minsFNameIDX]+"\n";
		res += "\tcsvSavFNameIDX = " + fnames[csvSavFNameIDX]+"\n";
		return res;	
	}

}//class SOMProjConfigData


//this class will manage file io
class FileIOManager{
	//owning map manager
	protected StraffSOMMapManager mapMgr;
	//name of owning class of the instance of this object, for display
	protected String owner;
	
	public FileIOManager(StraffSOMMapManager _mapMgr, String _owner) {mapMgr = _mapMgr; owner=_owner;}	
	
	private String buildClrStr(ConsoleCLR bk, ConsoleCLR clr, String str) {return bk.toString() + clr.toString() + str + ConsoleCLR.RESET.toString();	}
	private String _processMsgCode(String src, MsgCodes useCode) {
		if (!SOMProjConfigData.supportsANSITerm) {return src;}
		switch(useCode) {//add background + letter color for messages
			//info messages
			case info1 : {		return  buildClrStr(ConsoleCLR.BLACK_BACKGROUND, ConsoleCLR.WHITE, src);}		//basic informational printout
			case info2 : {		return  buildClrStr(ConsoleCLR.BLACK_BACKGROUND, ConsoleCLR.CYAN, src);}
			case info3 : {		return  buildClrStr(ConsoleCLR.BLACK_BACKGROUND, ConsoleCLR.YELLOW, src);}		//informational output from som EXE
			case info4 : {		return  buildClrStr(ConsoleCLR.BLACK_BACKGROUND, ConsoleCLR.GREEN, src);}
			case info5 : {		return  buildClrStr(ConsoleCLR.BLACK_BACKGROUND, ConsoleCLR.CYAN_BOLD, src);}	//beginning or ending of processing chuck/function
			//warning messages                                                 , 
			case warning1 : {	return  buildClrStr(ConsoleCLR.WHITE_BACKGROUND, ConsoleCLR.BLACK_BOLD, src);}
			case warning2 : {	return  buildClrStr(ConsoleCLR.WHITE_BACKGROUND, ConsoleCLR.BLUE_BOLD, src);}	//warning info re: ui does not exist
			case warning3 : {	return  buildClrStr(ConsoleCLR.WHITE_BACKGROUND, ConsoleCLR.BLACK_UNDERLINED, src);}
			case warning4 : {	return  buildClrStr(ConsoleCLR.WHITE_BACKGROUND, ConsoleCLR.BLUE_UNDERLINED, src);}	//info message about unexpected behavior
			case warning5 : {	return  buildClrStr(ConsoleCLR.WHITE_BACKGROUND, ConsoleCLR.BLUE_BRIGHT, src);}
			//error messages                                                   , 
			case error1 : {		return  buildClrStr(ConsoleCLR.BLACK_BACKGROUND, ConsoleCLR.RED_UNDERLINED, src);}//try/catch error
			case error2 : {		return  buildClrStr(ConsoleCLR.BLACK_BACKGROUND, ConsoleCLR.RED_BOLD, src);}		//code-based error
			case error3 : {		return  buildClrStr(ConsoleCLR.RED_BACKGROUND_BRIGHT, ConsoleCLR.BLACK_BOLD, src);}	//file load error
			case error4 : {		return  buildClrStr(ConsoleCLR.WHITE_BACKGROUND_BRIGHT, ConsoleCLR.RED_BRIGHT, src);}	//error message thrown by som executable
			case error5 : {		return  buildClrStr(ConsoleCLR.BLACK_BACKGROUND, ConsoleCLR.RED_BOLD_BRIGHT, src);}
		}
		return src;
	}//_processMsgCode	
	private void dispMessage(String srcClass, String srcMethod, String msgText, MsgCodes useCode){_dispMessage_base(mapMgr.getTimeStrFromProcStart() +"|" + srcClass,srcMethod,msgText, useCode,true);	}	
	private void dispMessage(String srcClass, String srcMethod, String msgText, MsgCodes useCode, boolean onlyConsole) {_dispMessage_base(mapMgr.getTimeStrFromProcStart() +"|" + srcClass,srcMethod,msgText, useCode,onlyConsole);	}	
	private void _dispMessage_base(String srcClass, String srcMethod, String msgText, MsgCodes useCode, boolean onlyConsole) {		
		String msg = _processMsgCode(srcClass + "::" + srcMethod + " : " + msgText, useCode);
		if((onlyConsole) || (mapMgr.pa == null)) {		System.out.println(msg);	} else {		mapMgr.pa.outStr2Scr(msg);	}
	}//dispMessage


	
	//write data to file
	public void saveStrings(String fname, String[] data) {
		PrintWriter pw = null;
		try {
		     File file = new File(fname);
		     FileWriter fw = new FileWriter(file, false);
		     pw = new PrintWriter(fw);
		     for (int i=0;i<data.length;++i) { pw.println(data[i]);}
		     
		} catch (IOException e) {	e.printStackTrace();}
		finally {			if (pw != null) {pw.close();}}
	}//saveStrings

	public void saveStrings(String fname, ArrayList<String> data) {
		PrintWriter pw = null;
		try {
		     File file = new File(fname);
		     FileWriter fw = new FileWriter(file, false);
		     pw = new PrintWriter(fw);
		     for (int i=0;i<data.size();++i) { pw.println(data.get(i));}
		     
		} catch (IOException e) {	e.printStackTrace();}
		finally {			if (pw != null) {pw.close();}}
	}//saveStrings
	
	public String[] loadFileIntoStringAra(String fileName, String dispYesStr, String dispNoStr) {try {return _loadFileIntoStringAra(fileName, dispYesStr, dispNoStr);} catch (Exception e) {e.printStackTrace(); } return new String[0];}
	//stream read the csv file and build the data objects
	private String[] _loadFileIntoStringAra(String fileName, String dispYesStr, String dispNoStr) throws IOException {		
		FileInputStream inputStream = null;
		Scanner sc = null;
		List<String> lines = new ArrayList<String>();
		String[] res = null;
	    //int line = 1, badEntries = 0;
		try {
		    inputStream = new FileInputStream(fileName);
		    sc = new Scanner(inputStream);
		    while (sc.hasNextLine()) {lines.add(sc.nextLine()); }
		    //Scanner suppresses exceptions
		    if (sc.ioException() != null) { throw sc.ioException(); }
		    dispMessage("fileIOManager:"+owner, "_loadFileIntoStringAra",dispYesStr+"\tLength : " +  lines.size(), MsgCodes.info3);
		    res = lines.toArray(new String[0]);		    
		} catch (Exception e) {	
			e.printStackTrace();
			mapMgr.dispMessage("fileIOManager:"+owner, "_loadFileIntoStringAra","!!"+dispNoStr, MsgCodes.error3);
			res= new String[0];
		} 
		finally {
		    if (inputStream != null) {inputStream.close();		    }
		    if (sc != null) { sc.close();		    }
		}
		return res;
	}//loadFileContents	
	
	//load into multiple arrays for multi-threaded processing
	public String[][] loadFileIntoStringAra_MT(String fileName, String dispYesStr, String dispNoStr, int numHdrLines, int numThds) {
		try {return _loadFileIntoStringAra_MT(fileName, dispYesStr, dispNoStr, numHdrLines, numThds);} 
		catch (Exception e) {e.printStackTrace(); } 
		return new String[0][];
	}
	//load files into multiple arrays for multi-threaded processing
	private String[][] _loadFileIntoStringAra_MT(String fileName, String dispYesStr, String dispNoStr, int numHdrLines, int numThds) throws IOException {		
		FileInputStream inputStream = null;
		Scanner sc = null;
		List<String>[] lines = new ArrayList[numThds];
		for (int i=0;i<numThds;++i) {lines[i]=new ArrayList<String>();	}
		String[][] res = new String[numThds+1][];
		String[] hdrRes = new String[numHdrLines];
		int idx = 0, count = 0;
		try {
		    inputStream = new FileInputStream(fileName);
		    sc = new Scanner(inputStream);
		    for(int i=0;i<numHdrLines;++i) {    	hdrRes[i]=sc.nextLine();   }		    
		    while (sc.hasNextLine()) {
		    	lines[idx].add(sc.nextLine()); 
		    	idx = (idx + 1)%numThds;
		    	++count;
		    }
		    //Scanner suppresses exceptions
		    if (sc.ioException() != null) { throw sc.ioException(); }
		    mapMgr.dispMessage("fileIOManager:"+owner, "_loadFileIntoStringAra_MT",dispYesStr+"\tLength : " +  count + " distributed into "+lines.length+" arrays.", MsgCodes.info1);
		    for (int i=0;i<lines.length;++i) {res[i] = lines[i].toArray(new String[0]);	 }
		    res[res.length-1]=hdrRes;
		} catch (Exception e) {	
			e.printStackTrace();
			mapMgr.dispMessage("fileIOManager:"+owner, "_loadFileIntoStringAra_MT","!!"+dispNoStr, MsgCodes.error2);
			res= new String[0][];
		} 
		finally {
		    if (inputStream != null) {inputStream.close();		    }
		    if (sc != null) { sc.close();		    }
		}
		return res;
	}//_loadFileIntoStringAra_MT

}//class fileIOManager