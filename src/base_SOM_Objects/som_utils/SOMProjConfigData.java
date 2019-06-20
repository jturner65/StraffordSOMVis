package base_SOM_Objects.som_utils;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_fileIO.*;

import base_Utils_Objects.*;
import base_Utils_Objects.io.FileIOManager;
import base_Utils_Objects.io.MessageObject;
import base_Utils_Objects.io.MsgCodes;

//structure to hold all the file names, file configurations and general program configurations required to run the SOM project
//will manage that all file names need to be reset when any are changed
public abstract class SOMProjConfigData {
	//owning map manager
	protected SOM_MapManager mapMgr;
	//object to manage screen and log output
	protected MessageObject msgObj;
	//manage IO in this object
	protected FileIOManager fileIO;	
	//ref to SOM_MapDat SOM executiond descriptor object
	protected SOM_MapDat SOMExeDat;	
	
	//string delimiter defining a comment in a file
	public static final String fileComment = "#";
		
	//TODO these are a function of data stucture, so should be set via config file
	protected boolean useSparseTrainingData, useSparseTestingData;
	
	//fileNow date string array for most recent experiment
	protected String[] dateTimeStrAra;

	//test/train partition - ratio of # of training data points to total # of data points 0->1.0f
	protected float trainTestPartition = 1.0f;	
	//current experiment # of samples total, train partition and test partition
	protected int expNumSmpls, expNumTrain, expNumTest;
	//current type of data used to train map - unmodified features, normalized features (ftr vec sums to 1) or standardized features (normalized across all data examples per ftr)
	protected String dataType;
	
	//calendar object to be used to query instancing time
	protected final Calendar instancedNow;
	//os used by project - this is passed from map
	protected final String OSUsed;
	//whether current OS supports ansi terminal color settings
	public static boolean supportsANSITerm = false;
	
	//file name of project config file - these should not be changed 
	protected static final String projectConfigFile = "projectConfig.txt";
	//file name of experimental config for a particular experiment
	protected static final String expProjConfigFileName = "SOM_EXEC_Proj_Config.txt";
	
	//fully qualified source directory for reading all source csv files, writing intermediate outputs, executing SOM_MAP and writing results
	protected String SOM_QualifiedDataDir, SOM_QualifiedConfigDir;	
	//name of som executable without extension or path info, if any
	protected String SOMExeName_base;
	//short name to be used in file names to denote this project; first letter capitalized project name
	protected String SOMProjName, SOMProjNameCap;
	
	//string to invoke som executable - platform dependent
	protected final String SOM_Map_EXECSTR;

	// config file names; file IO/subdirectory locations
	protected HashMap<String,String> configFileNames, subDirLocs;
	
	//directory under SOM where prebuilt map resides that is desired to be loaded into UI - replaces dbg files - set in project config file
	protected String[] preBuiltMapDirAra;
	//index of default prebuilt map to use - defaults to 0
	protected int dfltPreBuiltMapIDX = 0;
	//array of information string arrays for each specified prebuilt map - for display
	protected String[][] _preBuiltMapInfoAra;
	
	public static final String[] SOMResExtAra = new String[]{".wts", ".bm",".umx"};			//extensions for different SOM output file types
	//idxs of different kinds of SOM output files
	public static final int
		wtsIDX = 0,
		bmuIDX = 1,
		umtxIDX = 2;	
	//all flags corresponding to file names required to run SOM
	//private int[] reqFileNameFlags = new int[]{trainDatFNameIDX, somResFPrfxIDX, diffsFNameIDX, minsFNameIDX, csvSavFNameIDX};
	//private String[] reqFileNames = new String[]{"trainDatFNameIDX", "somResFPrfxIDX", "diffsFNameIDX", "minsFNameIDX", "csvSavFNameIDX"};
	//
	private String[] SOMFileNamesAra;
	//indexes in fname array of individual file names/file name prefixes
	public final static int 
		fName_OutPrfx_IDX = 0,
		fName_TrainLRN_IDX = 1,
		fName_TrainSVM_IDX = 2,
		fName_TestSVM_IDX = 3,
		fName_MinsCSV_IDX = 4,
		fName_DiffsCSV_IDX = 5,
		fName_SOMImgPNG_IDX = 6,
		fName_SOMMapConfig_IDX = 7,
		fName_EXECProjConfig_IDX = 8,
		fName_SOMBaseDir_IDX = 9;	
	public final static int numFileNames = 10;
	
	
	//this string holds experiment-specific string used for output files - x,y and k of map used to generate output.  this is set 
	//separately from calls to setSOM_ExpFileNames because experimental parameters can change between the saving of training data and the running of the experiment
	private String SOMOutExpSffx = "x-1_y-1_k-1";//illegal values set on purpose, needs to be set/overridden by config
	
	public SOMProjConfigData(SOM_MapManager _mapMgr, TreeMap<String, Object> _argsMap) {
		mapMgr = _mapMgr;
		msgObj = _mapMgr.buildMsgObj();
		//_argsMap is map of command line/control params.  useful here for config and data dir
		String _configDir = (String) _argsMap.get("configDir");
		String _dataDir = (String) _argsMap.get("dataDir");
		//_dirs : idx 0 is config dir; idx 1 is data dir
		
		SOM_QualifiedConfigDir = buildQualifiedBaseDir(_configDir,"Specified Config");
		SOM_QualifiedDataDir = buildQualifiedBaseDir(_dataDir,"Specified Data");

		fileIO = new FileIOManager(msgObj,"SOMProjConfigData");		
		//load project configuration
		loadProjectConfig();

		//----accumulate and manage OS info ----//
		//find platform this is executing on supportsANSITerm
		OSUsed = System.getProperty("os.name");
		//set invoking string for map executable - is platform dependent
		String execStr = SOMExeName_base;
		if (OSUsed.toLowerCase().contains("windows")) {			execStr += ".exe";			} 
		supportsANSITerm = (System.console() != null && System.getenv().get("TERM") != null);		
		msgObj.dispMessage("SOMProjConfigData","Constructor","OS this application is running on : "  + OSUsed + " | SOM Exec String : " +  execStr +" | Supports ANSI Terminal colors : " + supportsANSITerm, MsgCodes.info5);		
		SOM_Map_EXECSTR = execStr;				
		//----end accumulate and manage OS info ----//		
		
		SOMOutExpSffx = "x-1_y-1_k-1";//illegal values, needs to be set by config
		dataType = "NONE";
		//get current time
		instancedNow = Calendar.getInstance();
//		fnames = new String[numFiles];
//		for(int i=0;i<numFiles;++i){fnames[i]="";}
		//initFlags();
		SOMExeDat = new SOM_MapDat(this, OSUsed);	
		//load default data for this map dat
		loadDefaultSOMExp_Config();
		
		dateTimeStrAra = getDateTimeString(false, "_");
		dispSpecifiedSubDirs();
		dispSpecifiedConfigFNames();
	}//ctor
	
	/**
	 * display to screen all specified subdirectory and config file variable names and their values
	 */
	private void dispSpecifiedSubDirs() {
		String res = "Variables specified in Project config for subdirectory Locations.";
		for(String key : subDirLocs.keySet()) {	res+=" Variable Name : " +key+" | Value : " + subDirLocs.get(key) + "\n";	}	
		msgObj.dispMultiLineInfoMessage("SOMProjConfigData", "ctor->dispSpecifiedSubDirs", res);
	}//dispSpecifiedSubDirs
	
	/**
	 * display to screen all specified subdirectory and config file variable names and their values
	 */
	private void dispSpecifiedConfigFNames() {
		String res = "Variables specified in Project config for config file names.";
		for(String key : configFileNames.keySet()) {	res+=" Variable Name : " +key+" | Value : " + configFileNames.get(key) + "\n";	}		
		msgObj.dispMultiLineInfoMessage("SOMProjConfigData", "ctor->dispSpecifiedConfigFNames", res);
	}//dispSpecifiedSubDirsAndFNames
	


	private String buildQualifiedBaseDir(String _dir, String _type) {
		String qualifiedDir = "";
		try {
			qualifiedDir = new File(_dir).getCanonicalPath() + File.separator ;
		} catch (Exception e) {
			qualifiedDir = _dir;
			msgObj.dispMessage("SOMProjConfigData","Constructor->buildQualifiedBaseDir","Failed to find " + _type + " directory "+ qualifiedDir + " due to : " + e + ". Exiting program.", MsgCodes.error1);
			System.exit(1);
		}
		msgObj.dispMessage("SOMProjConfigData","Constructor->buildQualifiedBaseDir","Canonical Path to " + _type + " directory : " + qualifiedDir, MsgCodes.info1);
		return qualifiedDir;
	}//buildQualifiedBaseDir
	
	//this will load the project config file and set initial project-wide settings
	private void loadProjectConfig() {
		msgObj.dispMessage("SOMProjConfigData","loadProjectConfig","Start loading project configuration.", MsgCodes.info5);
		//build config file name and load config data
		String configFileName = SOM_QualifiedConfigDir + projectConfigFile;
		String[] fileStrings = fileIO.loadFileIntoStringAra(configFileName, "SOMProjConfigData Main Project Config File loaded", "SOMProjConfigData Main Project Config File Failed to load");
		//init maps holding config data
		configFileNames = new HashMap<String,String>();
		subDirLocs = new HashMap<String,String>(); 
		int idx = 0; boolean found = false;
		//find start of first block of data - 
		while (!found && (idx < fileStrings.length)){if(fileStrings[idx].contains("#BEGIN CONFIG")){found=true;} else {++idx; } }
		
		if (idx == fileStrings.length) {msgObj.dispMessage("SOMProjConfigData","loadProjectConfig","Error in the "+projectConfigFile+" file - no begin tag found for config file names.", MsgCodes.error2); return;}
		// CONFIG FILE NAMES
		idx = _loadProjConfigData(fileStrings, configFileNames, idx, false);//returns next idx, fills config variables
		if(idx == -1) {msgObj.dispMessage("SOMProjConfigData","loadProjectConfig","Error after _loadProjConfigData with configFileNames : idx == -1.  This means an 'END' tag is probably missing in the "+projectConfigFile+" file.", MsgCodes.error2); return;}
		
		// SUBDIR DEFS - location under data dir where data subdirs are located
		idx = _loadProjConfigData(fileStrings, subDirLocs, idx, true);//returns next idx, fills subdir variables
		if(idx == -1) {msgObj.dispMessage("SOMProjConfigData","loadProjectConfig","Error after _loadProjConfigData with subDirLocs : idx == -1.   This means an 'END' tag is probably missing in the "+projectConfigFile+" file.", MsgCodes.error2); return;}
		
		// MISC GLOBAL VARS - read through individual config vars preBuiltMapDir
		idx = _loadIndivConfigVars(fileStrings, idx); 
		if((preBuiltMapDirAra == null)|| (preBuiltMapDirAra.length == 0)){
			msgObj.dispMessage("SOMProjConfigData","loadProjectConfig","No default preBuilt Map directories specified in config, so none will be used.", MsgCodes.info3);
		} else {
			msgObj.dispMessage("SOMProjConfigData","loadProjectConfig","Default preBuiltMapDir set to be : " + preBuiltMapDirAra[dfltPreBuiltMapIDX], MsgCodes.info3);
		}
		if(idx == -1) {msgObj.dispMessage("SOMProjConfigData","loadProjectConfig","Error after _loadIndivConfigVars : idx == -1", MsgCodes.error2); return;}
		msgObj.dispMessage("SOMProjConfigData","loadProjectConfig","Finished loading project configuration.", MsgCodes.info5);
	}//loadProjectConfig(
	
	/**
	 * load all file name/value pairs in config file into passed map; return idx of next section
	 * @param fileStrings array of strings from file being read
	 * @param map destination to put key-value pairs
	 * @param stIDX index in fileStrings array to start for this section
	 * @param useFileDelim whether or not to add the appropriate file delimiter for OS to end of every entry
	 * @return ending index in fileStrings of next section start (1+ this section ending)
	 */
	private int _loadProjConfigData(String[] fileStrings, HashMap<String,String> map, int stIDX, boolean useFileDelim) {
		String sfx = (useFileDelim ? File.separator : "");								//line suffix to attach to entries in config file - attach file/directory delimitor if this is reading file name section of config
		for(int i=stIDX; i<fileStrings.length;++i) {
			String s = fileStrings[i];		
			if(s.contains("END")) {return i+1;}											//move to next line and return, should be "begin" tag
			if((s.contains(fileComment))|| (s.trim().length() == 0)) {continue;}		//ignore comments or empty lines
			String[] tkns = s.trim().split(SOM_MapManager.csvFileToken);					//split on csv token (comma)
			//map has keys that describe what the values are (i.e. variable names)
			String key = tkns[0].trim(), val= tkns[1].trim().replace("\"", "")+sfx;		
			map.put(key,val);	
			msgObj.dispMessage("SOMProjConfigData","_loadProjConfigData","Key : "+key+" \t| Val : " + val, MsgCodes.info3);
		}		
		return -1;																		//shouldn't ever get here - will crash, means END tag missing in config file
	}//_loadProjConfigData
	
	private int _loadIndivConfigVars(String[] fileStrings, int stIDX) {
		boolean endFound = false;
		ArrayList<String> preBuiltMapDirArrayList = new ArrayList<String> ();
		while((stIDX < fileStrings.length) && (!endFound)) {
			String s = fileStrings[stIDX];
			if(s.contains("END")) {++stIDX; break;}//move to next line, should be "begin" tag
			if((s.contains(fileComment)) || (s.trim().length() == 0)){++stIDX; continue;}
			String[] tkns = s.trim().split(SOM_MapManager.csvFileToken);
			String val = tkns[1].trim().replace("\"", "");
			String varName = tkns[0].trim();
			switch (varName) {
				case "preBuiltMapDir" 			: {	preBuiltMapDirArrayList.add( val + File.separator); break;			}
				case "dfltPreBuiltMapIDX" 		: {	dfltPreBuiltMapIDX = Integer.parseInt(val); break;}
				case "SOMExeName_base" 			: {	SOMExeName_base = val;		break;}
				case "SOMProjName" 				: {	SOMProjName = val;	SOMProjNameCap = val.substring(0, 1).toUpperCase() + val.substring(1);	break;}
				case "useSparseTrainingData" 	: {	useSparseTrainingData = Boolean.parseBoolean(val.toLowerCase());  break;}
				case "useSparseTestingData" 	: {	useSparseTestingData = Boolean.parseBoolean(val.toLowerCase());  break;}
				//add more variables here in instancing class - use string rep of name in config file, followed by a comma, followed by the string value (may include 2xquotes (") around string;) then can add more cases here
				default	 						: {_loadIndivConfigVarsPriv(varName,val);}
			}	
			msgObj.dispMessage("SOMProjConfigData","_loadIndivConfigVars","config file line : " +stIDX + " | key : " +varName +" value specified in config : " + val, MsgCodes.info3);
			++stIDX;
		}
		_preBuiltMapFinalSetup(preBuiltMapDirArrayList);
		return stIDX;			//
	}//_loadIndivConfigVars	
	protected abstract void _loadIndivConfigVarsPriv(String varName, String val);
	
	private void _preBuiltMapFinalSetup(ArrayList<String> preBuiltMapDirArrayList) {
		preBuiltMapDirAra = preBuiltMapDirArrayList.toArray(new String[0]);
		if((dfltPreBuiltMapIDX >= preBuiltMapDirAra.length) || (dfltPreBuiltMapIDX < 0)) {dfltPreBuiltMapIDX = 0;}	//verify default array value is legal
		if((preBuiltMapDirAra == null) || (preBuiltMapDirAra.length==0)) {	_preBuiltMapInfoAra = new String[0][];			return;}
		//build information describing desired pre-built maps noted in array
		ArrayList<String[]> resList = new ArrayList<String[]>();
		for(String s : preBuiltMapDirAra) {		resList.add(getPreBuiltMapInfoStr_Indiv(s));}		
		_preBuiltMapInfoAra = resList.toArray(new String[0][]);
	}//_preBuiltMapFinalSetup
	
	/**
	 * build relevant info for each specified pre-built map subdir in config file, building an array of relevant information about how the map was built
	 * @param _mapDir
	 * @return
	 */
	protected abstract String[] getPreBuiltMapInfoStr_Indiv(String _mapDir);
	
	/**
	 * build file names of raw data csv files - search each specified directory and return all csv files listed
	 * @param dataDirNames
	 * @return
	 */
	public TreeMap<String, String[]> buildRawFileNameMap(String[] dataDirNames) {
		msgObj.dispMessage("SOMProjConfigData","buildFileNameMap","Begin building list of raw data file names for each type of data.", MsgCodes.info3);
		TreeMap<String, String[]> rawDataFileNames = new TreeMap<String, String[]>();
		//for each directory, find all file names present, stripping ".csv" from name
		for (int dIdx = 0; dIdx < dataDirNames.length;++dIdx) {
			String dirName = SOM_QualifiedDataDir + subDirLocs.get("SOM_SourceCSV") + dataDirNames[dIdx];			
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
			rawDataFileNames.put(dataDirNames[dIdx], resList.toArray(new String[0]));
		}//		
		msgObj.dispMessage("SOMProjConfigData","buildFileNameMap","Finished building list of raw data file names for each type of data.", MsgCodes.info3);
		return rawDataFileNames;
	}//buildFileNameMap
	
	//public MessageObject buildMsgObj() {return new MessageObject(msgObj);}
	public MessageObject buildMsgObj() {return MessageObject.buildMe();}
	
	//this will save all essential information for a SOM-based experimental run, to make duplication of experiment easier
	//Info saved : SOM_MapData; 
	public void saveSOM_Exp() {
		msgObj.dispMessage("SOMProjConfigData","saveSOM_Map Config","Saving SOM Exe Map config data.", MsgCodes.info5);
		//build file describing experiment and put at this location
		String mapConfigFileName = getSOMMapConfigFileName();
		//get array of data describing SOM training exec info
		ArrayList<String> SOMDescAra = SOMExeDat.buildStringDescAra();
		fileIO.saveStrings(mapConfigFileName,SOMDescAra);
		msgObj.dispMessage("SOMProjConfigData","saveSOM_Exp","Finished saving SOM Exe Map config data.", MsgCodes.info5);
		msgObj.dispMessage("SOMProjConfigData","saveSOM_Exp","Saving project configuration data for current SOM execution.", MsgCodes.info5);
		String configFileName = getProjConfigForSOMExeFileName();
		//get array of data describing config info
		ArrayList<String> ConfigDescAra = getExpConfigData();
		fileIO.saveStrings(configFileName,ConfigDescAra);		
		
		//instance-specific data to save - custom file name specified in instancing config file		
		ArrayList<String> custSOMConfigData = buildCustSOMConfigData();
		//if specified then save
		if((null != custSOMConfigData) && (custSOMConfigData.size() > 0)) {
			String custConfigSOMFileName = getSOMExpCustomConfigFileName();
			fileIO.saveStrings(custConfigSOMFileName,custSOMConfigData);	
		}
		
		msgObj.dispMessage("SOMProjConfigData","saveSOM_Exp","Finished saving project configuration data for current SOM execution.", MsgCodes.info5);
	}//saveSOM_Exp
	/**
	 * Build instance-specific information regarding the SOM to save to specific file within SOM Trained Directory
	 */
	protected abstract ArrayList<String> buildCustSOMConfigData();	
	
	//this will save an easily human readable report describing a SOM training execution in the directory of the SOM data
	//externalVals are strings of specific report results, keyed by their description
	public void saveSOM_ExecReport(TreeMap<String, String> externalVals) {
		String fullQualDir  = getCurrSOMFullSubDir();
		String fileName = fullQualDir + "SOM Execution Report.txt";
		ArrayList<String> reportData = new ArrayList<String>();
		reportData.add("SOM Report for SOM Trained in directory :" + fullQualDir + "\nThe SOM represented in this directory was trained with the following configuration : ");
		reportData.add("SOM Map Configuration : " + SOMExeDat.toString());
		reportData.add("\n");
		//data 
		reportData.add("SOM Training Data configuration :");
		reportData.add("Uses Sparse Training Data : "+useSparseTrainingData);
		reportData.add("# of Training Examples : " + expNumTrain);
		reportData.add("Nature of the features used to train the SOM : "+dataType);
		reportData.add("Date and time of Map Training : "+dateTimeStrAra[1] + " : " + dateTimeStrAra[0]);
		reportData.add("\n");
		reportData.add("SOM Processing results : ");
		for(String desc : externalVals.keySet()) {		reportData.add(desc + " : " + externalVals.get(desc));}
		reportData.add("\n");
		//instance specific execution report data
		saveSOM_ExecReport_Indiv(reportData);
		fileIO.saveStrings(fileName,reportData);	
	}//saveSOM_ExecReport
	/**
	 * This will save any application-specific reporting data
	 * @param reportData
	 */
	protected abstract void saveSOM_ExecReport_Indiv(ArrayList<String> reportData);
	
	/**
	 * Set the default pre-built map to use in situations where prebuilt maps are to be consumed
	 * @param mapID
	 */
	public void setSOM_DefaultPreBuiltMap(int mapID) {	dfltPreBuiltMapIDX = mapID;}
	/**
	 * this loads prebuilt map configurations
	 */
	public boolean setSOM_UsePreBuilt() {
		//load map values from pre-trained map using this data - IGNORES VALUES SET IN UI	
		//build file name to load
		if((preBuiltMapDirAra == null) || (preBuiltMapDirAra.length == 0)){
			msgObj.dispMessage("SOMProjConfigData","setSOM_UsePreBuilt","No pre-built map directories specified (preBuiltMapDirAra.length == 0).  Aborting.", MsgCodes.warning1);
			return false;
		}
		if((dfltPreBuiltMapIDX >= preBuiltMapDirAra.length) || (dfltPreBuiltMapIDX<0)) {
			msgObj.dispMessage("SOMProjConfigData","setSOM_UsePreBuilt","Invalid default pre-built map dir array index specified : " + dfltPreBuiltMapIDX +" so being forced to 0.", MsgCodes.warning1);
			dfltPreBuiltMapIDX = 0;
		}
		String preBuiltMapDir = preBuiltMapDirAra[dfltPreBuiltMapIDX];
		//attempt to build config file name holding map configuration
		String configFileName = getDirNameAndBuild(subDirLocs.get("SOM_MapProc") + preBuiltMapDir, true) + expProjConfigFileName;
		msgObj.dispMessage("SOMProjConfigData","setSOM_UsePreBuilt","Attempting to load pre-built map from directory : "+ configFileName , MsgCodes.info5);
		//load project config for this SOM execution
		loadProjConfigForSOMExe(configFileName);		
		//load and map config
		loadSOMMap_Config();
		//structure holding SOM_MAP specific cmd line args and file names and such
		SOMExeDat.updateMapDescriptorState();
		//now load instancing-project-specific configurations from files saved when SOM was trained
		setSOM_UsePreBuilt_Indiv();
		//now set flag to false before we launch loader to load results from SOM and map values		
		mapMgr.setLoaderRTN(false);		
		return true;
	}//setSOM_UsePreBuilt
	
	/**
	 * for display purposes - return pre-built map data descriptions for each pre-built map specified
	 * @return
	 */
	public String[][] getPreBuiltMapInfoAra() {	return _preBuiltMapInfoAra;}	
	
	/**
	 * Instance-specific loading necessary for proper consumption of pre-built SOM
	 */
	protected abstract void setSOM_UsePreBuilt_Indiv();

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
		String dfltSOMConfigFName = SOM_QualifiedConfigDir + configFileNames.get("SOMDfltConfigFileName"); 
		msgObj.dispMessage("SOMProjConfigData","loadDefaultSOMExp_Config","Default file name  :" + dfltSOMConfigFName, MsgCodes.info1);
		loadSOMMap_Config(dfltSOMConfigFName);
	}
	public void loadSOMMap_Config() {loadSOMMap_Config(getSOMMapConfigFileName());}  
	public void loadSOMMap_Config(String expFileName) {
		msgObj.dispMessage("SOMProjConfigData","loadSOMMap_Config","Start loading SOM Exe config data from " + expFileName, MsgCodes.info5);
		//build file describing experiment and put at this location
		String[] expStrAra = fileIO.loadFileIntoStringAra(expFileName, "SOM_MapDat Config File loaded", "SOM_MapDat Config File Failed to load");
		//set values from string array from file read
		SOMExeDat.buildFromStringArray(expStrAra);
		//send current map data to map mgr to set ui values
		setUIValsFromLoad();
		msgObj.dispMessage("SOMProjConfigData","loadSOMMap_Config","Finished loading SOM Exe config data from " + expFileName, MsgCodes.info5);
	}//loadSOM_Exp
	//send current map data to map mgr to set ui values
	public void setUIValsFromLoad(){mapMgr.setUIValsFromLoad(SOMExeDat);}
	
	//load a specific configuration based on a previously run experiment
	public void loadProjConfigForSOMExe() {	loadProjConfigForSOMExe( getProjConfigForSOMExeFileName());}
	private void loadProjConfigForSOMExe(String configFileName) {
		msgObj.dispMessage("SOMProjConfigData","loadProjConfigForSOMExe","Start loading project config data for SOM Execution : "+ configFileName, MsgCodes.info5);
		//NOTE! if running a debug run, be sure to have the line dateTimeStrAra[0],<date_time_value>_DebugRun in proj config file, otherwise will crash
		String[] configStrAra = fileIO.loadFileIntoStringAra(configFileName, "SOMProjConfigData project config data for SOM Execution File loaded", "SOMProjConfigData project config data for SOM Execution Failed to load");
		setExpConfigData(configStrAra);
		msgObj.dispMessage("SOMProjConfigData","loadProjConfigForSOMExe","Finished loading project config data for SOM Execution", MsgCodes.info5);				
	}//loadProg_Congfig
		
	//set experiment vars based on data saved in config file
	private void setExpConfigData(String[] dataAra) {
		dateTimeStrAra = new String[2];
		ArrayList<String> somFileNamesAraTmp = new ArrayList<String>();
		for (int i=0;i<dataAra.length;++i) {
			String str = dataAra[i];
			if(str.contains(this.fileComment)) {continue;}
			String[] strToks = str.trim().split(SOM_MapManager.csvFileToken);
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
		//override value set in config
		if((expNumTest==0) || (expNumSmpls==0)){trainTestPartition = 1.0f;} else {trainTestPartition = expNumTrain/(1.0f*expNumSmpls);}
		if(somFileNamesAraTmp.size() > 0) {		SOMFileNamesAra = somFileNamesAraTmp.toArray(new String[0]);} 
		else {									setSOM_ExpFileNames(expNumSmpls, expNumTrain, expNumTest);}
	}//setExpConfigData	
	
	/**
	 * build a map keyed by variable name of SOM exec config file name values
	 * @param dataAra
	 * @return map of variable-keyed string representations of variable values in config file
	 */	
	protected TreeMap<String,String> getSOMExpConfigData(String configFileName,String descPrfx){
		String[] configStrAra = fileIO.loadFileIntoStringAra(configFileName, "getSOMExpConfigData::SOMProjConfigData " + descPrfx + " File loaded", "getSOMExpConfigData::SOMProjConfigData " + descPrfx + " Failed to load");
		TreeMap<String,String> dataRes = new TreeMap<String,String>();
		for (int i=0;i<configStrAra.length;++i) {
			String str = configStrAra[i];
			if(str.contains(this.fileComment)) {continue;}
			String[] strToks = str.trim().split(SOM_MapManager.csvFileToken);
			dataRes.put(strToks[0].trim(), strToks[1].trim());
		}//for each line
		return dataRes;
	}
		
	//save test/train data in multiple threads
	public void launchTestTrainSaveThrds(ExecutorService th_exec, int curMapFtrType, int numTrainFtrs, SOMExample[] trainData, SOMExample[] testData) {
		//set exp names
		int numTtlEx = trainData.length + testData.length;
		setSOM_ExpFileNames(numTtlEx, trainData.length, testData.length);
		
		String saveFileName = "";
		List<Future<Boolean>> SOMDataWriteFutures = new ArrayList<Future<Boolean>>();
		List<SOMTrainDataWriter> SOMDataWrite = new ArrayList<SOMTrainDataWriter>();
		//call threads to instance and save different file formats
		if (expNumTrain > 0) {//save training data
			if (useSparseTrainingData) {
				saveFileName = getSOMMapSVMFileName();
				SOMDataWrite.add(new SOMTrainDataWriter(mapMgr, curMapFtrType, numTrainFtrs, saveFileName, "sparseSVMData", trainData));	
			} else {
				saveFileName = getSOMMapLRNFileName();
				SOMDataWrite.add(new SOMTrainDataWriter(mapMgr, curMapFtrType, numTrainFtrs, saveFileName, "denseLRNData", trainData));	
			}
		}
		if (expNumTest > 0) {//save testing data
			saveFileName = getSOMMapTestFileName();
			SOMDataWrite.add(new SOMTrainDataWriter(mapMgr, curMapFtrType, numTrainFtrs, saveFileName, useSparseTestingData ? "sparseSVMData" : "denseLRNData", testData));
		}
		try {SOMDataWriteFutures = th_exec.invokeAll(SOMDataWrite);for(Future<Boolean> f: SOMDataWriteFutures) { f.get(); }} catch (Exception e) { e.printStackTrace(); }			
	}//launchTestTrainSaveThrds	
	
	//call this before running SOM experiment
	public void setSOM_MapArgs(HashMap<String, Integer> mapInts, HashMap<String, Float> mapFloats, HashMap<String, String> mapStrings) {		
		SOMExeDat.setArgsMapData(mapInts, mapFloats, mapStrings);		
	}//setSOM_MapArgs
	
	//return if map is toroidal
	public boolean isToroidal(){if(null==SOMExeDat){return false;}	return SOMExeDat.isToroidal();	}
	
	/**
	 * update map descriptor Float values
	 * @param key : key descriptor of value
	 * @param val
	 * @param updateMap : whether this update should be passed to map config
	 * @param updateUI : whether this update should be passed to UI
	 */
	public void updateMapDat_Integer(String key, Integer val, boolean updateMap, boolean updateUI) {
		if(updateMap) {		SOMExeDat.updateMapDat_Integer(key,val);	}		//updating map data
		if(updateUI) {		mapMgr.updateUIMapData_Integer(key,val);	}		//update UI with change
	}//updateMapDat_Int
	public void updateMapDat_Integer_MapCols(Integer val, boolean updateMap, boolean updateUI) {updateMapDat_Integer("mapCols", val, updateMap, updateUI);}
	public void updateMapDat_Integer_MapRows(Integer val, boolean updateMap, boolean updateUI) {updateMapDat_Integer("mapRows", val, updateMap, updateUI);}
	
	/**
	 * update map descriptor Float values
	 * @param key : key descriptor of value
	 * @param val
	 * @param updateMap : whether this update should be passed to map config
	 * @param updateUI : whether this update should be passed to UI
	 */
	public void updateMapDat_Float(String key, Float val, boolean updateMap, boolean updateUI) {
		if(updateMap) {		SOMExeDat.updateMapDat_Float(key,val);	}		//updating map data
		if(updateUI) {		mapMgr.updateUIMapData_Float(key,val);	}		//update UI with change
	}//updateMapDat_Float
	
	/**
	 * update map descriptor String values
	 * @param key : key descriptor of value
	 * @param val
	 * @param updateMap : whether this update should be passed to map config
	 * @param updateUI : whether this update should be passed to UI
	 */
	public void updateMapDat_String(String key, String val, boolean updateMap, boolean updateUI) {
		if(updateMap) {		SOMExeDat.updateMapDat_String(key,val);	}		//updating map data
		if(updateUI) {		mapMgr.updateUIMapData_String(key,val);	}		//update UI with change
	}//updateMapDat_String
	
	
	//build a date with each component separated by token
	protected String[] getDateTimeString(){return getDateTimeString(false,".");}
	protected String[] getDateTimeString(boolean toSecond, String token){
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
		msgObj.dispMessage("SOMProjConfigData","setSOM_ExpFileNames","Start setting file names and example counts", MsgCodes.info5);
		expNumSmpls = _numSmpls;
		expNumTrain = _numTrain;
		expNumTest = _numTest;
		String nowSubDirNoSep = SOMProjNameCap + "SOM_"+dateTimeStrAra[0];
		String nowDir = getDirNameAndBuild(subDirLocs.get("SOM_MapProc") + nowSubDirNoSep, true);
		String fileNow = dateTimeStrAra[1];
		//setSOM_ExpFileNames( fileNow, nowDir);		
		setSOM_ExpFileNames( fileNow, nowSubDirNoSep);		//lacking hardcoded ref to 	subDirLocs.get("SOM_MapProc") 
		msgObj.dispMessage("SOMProjConfigData","setSOM_ExpFileNames","Finished setting file names and example counts", MsgCodes.info5);
	}//setSOM_ExpFileNames	
			
	//file names used specifically for SOM data
	private void setSOM_ExpFileNames(String fileNow, String nowDir){
		SOMFileNamesAra = new String[numFileNames];
		SOMFileNamesAra[fName_SOMBaseDir_IDX] = nowDir;
		SOMFileNamesAra[fName_OutPrfx_IDX] = "Out_"+SOMProjName+"_Smp_"+expNumSmpls;
		SOMFileNamesAra[fName_TrainLRN_IDX] = "Train_"+SOMProjName+"_Smp_"+expNumTrain+"_of_"+expNumSmpls+"_typ_" +dataType + "_Dt_"+fileNow+".lrn";
		SOMFileNamesAra[fName_TrainSVM_IDX] = "Train_"+SOMProjName+"_Smp_"+expNumTrain+"_of_"+expNumSmpls+"_typ_" + dataType+"_Dt_"+fileNow+".svm";				
		SOMFileNamesAra[fName_TestSVM_IDX] = "Test_"+SOMProjName+"_Smp_"+expNumTest+"_of_"+expNumSmpls+"_typ_" +dataType +"_Dt_"+fileNow+".svm";
		SOMFileNamesAra[fName_MinsCSV_IDX] = "Mins_"+SOMProjName+"_Smp_"+expNumSmpls+"_Dt_"+fileNow+"_typ_" +dataType +".csv";
		SOMFileNamesAra[fName_DiffsCSV_IDX] = "Diffs_"+SOMProjName+"_Smp_"+expNumSmpls+"_Dt_"+fileNow+"_typ_" +dataType +".csv";
		SOMFileNamesAra[fName_SOMImgPNG_IDX] = "SOMImg_"+SOMProjName+"_Smp_"+expNumSmpls+"_Dt_"+fileNow+"_typ_" +dataType +".png";
		SOMFileNamesAra[fName_SOMMapConfig_IDX] = "SOM_MapConfig_"+SOMProjName+"_Smp_"+expNumSmpls+"_Dt_"+fileNow+".txt";						//map configuration
		SOMFileNamesAra[fName_EXECProjConfig_IDX] = expProjConfigFileName;					//
					;
		for(int i =0; i<SOMFileNamesAra.length;++i){	
			//build dir as well
			msgObj.dispMessage("SOMProjConfigData","setSOM_ExpFileNames","Built File Name : " + SOMFileNamesAra[i], MsgCodes.info3);
		}				
	}//setSOM_ExpFileNames	
	
	public String getSOMMapOutFileBase(String sffx) {	SOMOutExpSffx = sffx; return getSOMMapOutFileBase();}
	private String getSOMMapOutFileBase() { 	return getCurrSOMFullSubDir() + SOMFileNamesAra[fName_OutPrfx_IDX] + SOMOutExpSffx;}
	public String getSOMMapLRNFileName(){	return getCurrSOMFullSubDir() + SOMFileNamesAra[fName_TrainLRN_IDX];	}
	public String getSOMMapSVMFileName(){	return getCurrSOMFullSubDir() + SOMFileNamesAra[fName_TrainSVM_IDX];	}
	public String getSOMMapTestFileName(){	return getCurrSOMFullSubDir() + SOMFileNamesAra[fName_TestSVM_IDX];	}
	public String getSOMMapMinsFileName(){	return getCurrSOMFullSubDir() + SOMFileNamesAra[fName_MinsCSV_IDX];	}
	public String getSOMMapDiffsFileName(){	return getCurrSOMFullSubDir() + SOMFileNamesAra[fName_DiffsCSV_IDX];	}	
	
	//return local directories lacking base directory structure - these are all relative to baseDir + subDirLocs.get("SOM_MapProc")
	public String getSOMMap_lclLRNFileName(){	return SOMFileNamesAra[fName_TrainLRN_IDX];	}
	public String getSOMMap_lclSVMFileName(){	return SOMFileNamesAra[fName_TrainSVM_IDX];	}
	public String getSOMMap_lclTestFileName(){	return SOMFileNamesAra[fName_TestSVM_IDX];	}
	public String getSOMMap_lclMinsFileName(){	return SOMFileNamesAra[fName_MinsCSV_IDX];	}
	public String getSOMMap_lclDiffsFileName(){	return SOMFileNamesAra[fName_DiffsCSV_IDX];	}	
	public String getSOMMap_lclOutFileBase(String sffx) {	SOMOutExpSffx = sffx; return getSOMMap_lclOutFileBase();}
	private String getSOMMap_lclOutFileBase() { 			return SOMFileNamesAra[fName_OutPrfx_IDX] + SOMOutExpSffx;}
	
	//get fully qualified current SOM subdirectory
	public String getCurrSOMFullSubDir() {return getSOMExec_FullPath() + getSOMMap_CurrSubDir();}
	
	public String getSOMLocClrImgForFtrFName(int ftr) {return "ftr_"+ftr+"_"+SOMFileNamesAra[fName_SOMImgPNG_IDX];}	
	//ref to file name for map configuration setup
	public String getSOMMapConfigFileName() {return getCurrSOMFullSubDir()+ SOMFileNamesAra[fName_SOMMapConfig_IDX];	}	
	//ref to file name for custom instance-specific file name
	public String getSOMExpCustomConfigFileName() {return getCurrSOMFullSubDir()+getSOMExpCustomConfigFileName_Indiv(); }
	/**
	 * file name of custom config used to save instance-specific implementation details/files 
	 * @return file name of config file for custom config variables, under SOM Exec dir
	 */
	protected abstract String getSOMExpCustomConfigFileName_Indiv();
	
	//ref to file name for data and project configuration relevant for current SOM execution
	public String getProjConfigForSOMExeFileName() {return getCurrSOMFullSubDir()+ SOMFileNamesAra[fName_EXECProjConfig_IDX];	}	

	//log file name
	public String getFullLogFileNameString() {
		String [] tmpNow = getDateTimeString(false, "_");
		String logDirName= getDirNameAndBuild(subDirLocs.get("SOM_Logs") + "log_"+tmpNow[1] +File.separator, false);
		return logDirName + "SOM_Run_Log.txt";
	}
	
	/**
	 * this will return the fully qualified path to the file in a sub directory in SOM_QualifiedConfigDir or SOM_QualifiedDataDir
	 * specified in fNameKey - subDirs contains sub directories to query under SOM_QualifiedConfigDir 
	 * 
	 * This is intended to be consumed by the instancing class to call directories specific to the instancing application
	 * 
	 * @param fNameKey the key in the configFileNames dir containing the file name in question
	 * @param subDirs array of keys of subdirectory names under SOM_QualifiedConfigDir to use to build directory
	 * @return fully qualified path to specified config file name
	 */
	protected String getFullPathConfigFileName(String fNameKey, String[] subDirs) {	return _getFullPathFN(SOM_QualifiedConfigDir,fNameKey,subDirs);}//getFullPathConfigFileName	
	
	protected String getFullPathDataFileName(String fNameKey, String[] subDirs) {	return _getFullPathFN(SOM_QualifiedDataDir,fNameKey,subDirs);}//getFullPathDataFileName
	
	private String _getFullPathFN(String _baseDir, String fNameKey, String[] subDirs) {
		String resDir = _baseDir;
		for(int i=0;i<subDirs.length;++i) {	resDir += subDirLocs.get(subDirs[i]) + File.separator;	}
		return resDir + configFileNames.get(fNameKey);		
	}
	
	public String getClassSegmentFileNamePrefix() {	return getSegmentFileNamePrefix("class","");	}
	public String getCategorySegmentFileNamePrefix() {	return getSegmentFileNamePrefix("category","");	}
	public String getFtrWtSegmentFileNamePrefix() {	return getSegmentFileNamePrefix("ftrwt","");	}
	public String getExampleToBMUFileNamePrefix(String _exTypeString) {	return getSegmentFileNamePrefix("example",_exTypeString);	}
	
	/**
	 * segment report file name prefix - calling code needs to add segement identifier and extension
	 * @param segType descriptive string of type of segment
	 * @return fully qualified file name prefix corresponding to where desired segment should be saved/located
	 */
	public String getSegmentFileNamePrefix(String segType, String fNamePrefix) {	
		switch(segType.toLowerCase()) {
				//in config file : 
				//		SOM_ProposalDir, "ProposalReports"
				//		SOM_FtrProposalSubDir, "FeatureWeightProposals"
				//		SOM_ClassProposalSubDir,"JobPracticeProposals"
				//		SOM_CategoryProposalSubDir,"JobPracticGroupProposals"	
			case "ftrwt" 	: {		return _getSegmentDirNameFromDirKey("SOM_FtrMappingsSubDir",fNamePrefix); 		}
			case "class" 	: { 	return _getSegmentDirNameFromDirKey("SOM_ClassMappingsSubDir",fNamePrefix);		}
			case "category" : { 	return _getSegmentDirNameFromDirKey("SOM_CategoryMappingsSubDir",fNamePrefix);	}
			case "example" : {		return _getSegmentDirNameFromDirKey("SOM_ExampleToBMUMappingsSubDir",fNamePrefix);	}
				//any instancing application-specific segment reports
			default : {return getSegmentFileNamePrefix_Indiv(segType,fNamePrefix);}
		}	
	}//getSegmentFileNamePrefix
	
	protected String _getSegmentDirNameFromDirKey(String dirKey, String fNamePrefix) {
		String ProposalBaseDir = getDirNameAndBuild(subDirLocs.get("SOM_ProposalDir"), true);
		String [] tmpNow = getDateTimeString(false, "_");
		String ProposalNowDir = getDirNameAndBuild(ProposalBaseDir, "mappings_"+tmpNow[1] +File.separator,true);
		String dirName = subDirLocs.get(dirKey);
		String fileName = dirName.substring(0, dirName.length()-2);
		if(fNamePrefix.trim().length() == 0) {
			return getDirNameAndBuild(ProposalNowDir, dirName, true) + fileName;			
		} else {
			return getDirNameAndBuild(ProposalNowDir, dirName, true) + fNamePrefix+"_"+fileName;
		}
	}//_getSegmentDirNameFromDirKey
	
	/**
	 * Instancing application-specific segment file name handling
	 * @param segType descriptive string of type of segment
	 * @return fully qualified file name prefix corresponding to where desired segment should be saved/located
	 */
	protected abstract String getSegmentFileNamePrefix_Indiv(String segType, String fNamePrefix);
	
	public String SOM_MapDat_ToString() {return SOMExeDat.toString();}
	
	public abstract String getRawDataLoadInfo(boolean fromFiles, String baseDirName, String baseFName); 
	//return the subdirectory under subDirLocs.get("SOM_PreProc")) to the desired preprocessed data to use to train/compare to the map
	//TODO perhaps replace this with info from global project config data ?
	//keeping as "default" right now to keep raw data processing output and map training data reading in separate directories
	public String getPreProcDataDesiredSubDirName() {	return getPreProcDataDesToMapSubDirName(true);	}	
	//return desired subdirectory to use to get custs and true prospects data for mapping
	public String getPreProcDataDesToMapSubDirName(boolean _forceDefault) { return getDesExCSVDataSubDir("SOM_PreProc","SOM_PreProcPrspctSrc",  _forceDefault);}
	
	//build pre-processed data directory structures based on current date - this is for saving pre-processed data
	public String[] buildPreProccedDataCSVFNames_Save(String _desSuffix) {	return buildCSVFileNamesAra("preprocData_" + getDateTimeString(false, "_")[0] + File.separator, _desSuffix,"SOM_PreProc");}
	/**
	 * Build the file names for the csv files used to load intermediate data - raw data that has been preprocced - this returns the directory we wish to load from
	 * @param subDir sub directory to load from
	 * @param _desSuffix text string describing file type
	 * @return array with 3 elements holding [destination file name, _desSuffix (being returned), rootDestDir]
	 */
	public String[] buildPreProccedDataCSVFNames_Load(String subDir, String _desSuffix) {return buildCSVFileNamesAra(subDir, _desSuffix,"SOM_PreProc");}
	
	/**
	 * build desired target directory for specified data type, and default to "default" if improperly specified or  == true
	 * @param _dataLocKey subdir for this type of data
	 * @param _dataSubDirLocKey subdir under _dataLocKey to use, if exists
	 * @param _forceDefault force to use "default" subdir
	 * @return
	 */
	public String getDesExCSVDataSubDir(String _dataLocKey, String _dataSubDirLocKey, boolean _forceDefault) {
		if (_forceDefault) {return "default" + File.separator;}
		String res = subDirLocs.get(_dataSubDirLocKey);
		if((res==null) || (!checkIfSubDirExists(SOM_QualifiedDataDir, subDirLocs.get(_dataLocKey) + res))) {	res = "default" + File.separator;}	
		return res;
	}
	
	/**
	 * build csv file names array for specified _fileTypeKey
	 * @param subDir : subdirectory within specified file directory to use for csv data
	 * @param _desSuffix
	 * @param _fileTypeKey
	 * @return
	 */
	protected String[] buildCSVFileNamesAra(String subDir, String _desSuffix, String _fileTypeKey) {
		return _buildDataCSVFNames(getDirNameAndBuild(subDirLocs.get(_fileTypeKey), true), subDir, _desSuffix);	
	}//buildCSVFileNamesAra	
	
	private String[] _buildDataCSVFNames(String rootDestDir, String subDir, String _desSuffix) {
		//build subdir based on date, if doesn't exist
		String destDir = getDirNameAndBuild(rootDestDir, subDir, true);
		String destForFName = destDir + _desSuffix;				
		return new String[] {destForFName, _desSuffix, rootDestDir};
	}//_buildDataCSVFNames	
	
	//this will return whether the passed subdir exists under the passed base directory
	public boolean checkIfSubDirExists(String baseDir, String subdir) {
		String dirName = baseDir +subdir;
		File directory = new File(dirName);
		return directory.exists();		
	}//checkIfDirExists
	
	//this will retrieve a subdirectory name under the main directory of this project and build the subdir if it doesn't exist
	//subdir assumed to have file.separator already appended (might not be necessary)
	protected String getDirNameAndBuild(String subdir, boolean _buildDir) {return getDirNameAndBuild(SOM_QualifiedDataDir,subdir,_buildDir);} 
	//baseDir must exist already
	protected String getDirNameAndBuild(String baseDir, String subdir, boolean _buildDir) {
		String dirName = baseDir +subdir;
		File directory = new File(dirName);
	    if (_buildDir && (! directory.exists())){ directory.mkdir(); }
		return dirName;
	}//getDirNameAndBuild
	
	public String getOSUsed() {return OSUsed;}
	public String getSOM_Map_EXECSTR() {return SOM_Map_EXECSTR;}	
	//get instancedNow calendar, for time keeping
	public Calendar getInstancedNow() {return instancedNow;}
	
	public String getSOMProjName() { return SOMProjName;}
	public String getSOMExec_FullPath() { return getDirNameAndBuild(subDirLocs.get("SOM_MapProc"), true);}//full path to where SOM executable lives/where specific instance sub directory lives
	public String getSOMMap_CurrSubDir() {return SOMFileNamesAra[fName_SOMBaseDir_IDX]+File.separator;}
	public String getSOMMap_CurrSubDirNoSep() {return SOMFileNamesAra[fName_SOMBaseDir_IDX];}	//return directory name without file sep, for inter-OS useage
	public String getCurrLog_FileName() { return getDirNameAndBuild(subDirLocs.get("SOM_Logs"), true);}
	
	//set training size partition -> fraction of entire data set that is training data
	public void setTrainTestPartition(float _ratio) {trainTestPartition = _ratio;}
	public float getTrainTestPartition() {return trainTestPartition;}
	
	//whether uses sparse or dense training/testing data
	public void setUseSparseTrainingData(boolean val) {useSparseTrainingData = val;}
	public void setUseSparseTestingData(boolean val) {useSparseTestingData = val;}
	
	public boolean isUseSparseTrainingData() {return useSparseTrainingData;}
	public boolean isUseSparseTestingData() {return useSparseTestingData;}	
	
	public SOM_MapDat getSOMExeDat() {return SOMExeDat;}
	public String getSOMOutExpSffx() {return SOMOutExpSffx;}

	
	//file name to save record of bad events
	public String getBadEventFName(String dataFileName) { return SOM_QualifiedDataDir + dataFileName +"_bad_OIDs.csv";	}
	
	
	public String getSOMResFName(int ext){return (getSOMMapOutFileBase() + SOMResExtAra[ext]);}
	
//	private void initFlags(){stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlag(i,false);}}
//	public void setFlag(int idx, boolean val){
//		boolean oldVal = getFlag(idx);
//		int flIDX = idx/32, mask = 1<<(idx%32);
//		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
//		switch (idx) {//special actions for each flag
//			//case isDirty 				: {if((oldVal != val) && (val)){setDirty();}break;}	//if transition to true, run setDirty 				
//			case trainDatFNameIDX		: {break;}//
//			case somResFPrfxIDX			: {break;}//
//			case diffsFNameIDX			: {break;}//
//			case minsFNameIDX			: {break;}//
//			case csvSavFNameIDX			: {break;}//
//			case outputSffxSetIDX		: {break;}//output file name suffix has been set
//		}
//	}//setFlag	
//	private boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		
	@Override
	public String toString(){//TODO this needs to be rebuilt
		String res = "SOM Project Data Config Cnstrct : \n";
//		res += "\ttrainDatFNameIDX = " + fnames[trainDatFNameIDX]+"\n" +"\tsomResFNameIDX = " + fnames[somResFPrfxIDX]+"\n";
//		res += "\tdiffsFNameIDX = " + fnames[diffsFNameIDX]+"\n"+  "\tminsFNameIDX = " + fnames[minsFNameIDX]+"\n";
//		res += "\tcsvSavFNameIDX = " + fnames[csvSavFNameIDX]+"\n";
		return res;	
	}

}//class SOMProjConfigData


