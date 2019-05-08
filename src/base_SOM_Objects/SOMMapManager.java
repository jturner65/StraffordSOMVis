package base_SOM_Objects;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_fileIO.*;
import base_SOM_Objects.som_ui.*;
import base_SOM_Objects.som_utils.*;
import base_SOM_Objects.som_vis.*;


import base_UI_Objects.*;
import base_Utils_Objects.*;

public abstract class SOMMapManager {
	//applet, if used in graphical context
	private my_procApplet pa;				
	//owning window
	public SOMMapUIWin win;		
	//manage IO in this object
	protected FileIOManager fileIO; 
	//struct maintaining complete project configuration and information from config files - all file name data and building needs to be done by this object
	public SOMProjConfigData projConfigData;	
	//object to manage messages for display and potentially logging
	private MessageObject msgObj;
	//object to manage interface with a UI, to make sure map data stays synchronized
	public SOMUIToMapCom mapUIAPI;
	
	//////////////////////////////
	//map descriptors
	
	//all nodes of som map, keyed by node location as tuple of row/col coordinates
	protected TreeMap<Tuple<Integer,Integer>, SOMMapNode> MapNodes;	
	//map of ftr idx and all map nodes that have non-zero presence in that ftr
	protected TreeMap<Integer, HashSet<SOMMapNode>> MapNodesByFtrIDX;
	
	//array of map clusters
	public ArrayList<SOMMapSegment> segments;
	//data values directly from the trained map, populated upon load
	private float[] 
			map_ftrsMean, 				
			map_ftrsVar, 
			map_ftrsDiffs, 
			map_ftrsMin;				//per feature mean, variance, difference, mins, in -map features- data
//	public float[] 		
//			td_ftrsMean, td_ftrsVar, 	//TODO perhaps remove these - we have this info already
//			in_ftrsMean, in_ftrsVar; 	//TODO perhaps remove these - we have this info already : per feature training and input data means and variances

	
	////////////////////////////////////////////////////////////////////////////////////////////////
	//data descriptions
	//full input data, data set to be training data and testing data (all of these examples 
	//are potential -training- data, in that they have all features required of training data)
	//testing data will be existing -customers- that will be matched against map - having these 
	//is not going to be necessary for most cases since this is unsupervised
	protected SOMExample[] inputData;
	protected SOMExample[] trainData;
	protected SOMExample[] testData;	
	//validationData are example records failing to meet the training criteria or otherwise desired to be mapped against SOM
	//these were not used to train the map	
	protected SOMExample[] validationData;		
	public int numInputData, numTrainData, numTestData, numValidationData;
	
	//values to return scaled values to actual data points - multiply wts by diffsVals, add minsVals
	//idx 0 is feature diffs/mins per jp (ftr idx); idx 1 is across all jps
	private Float[][] diffsVals, minsVals;	
	//# of training features (from "product" jp_jpg object); # of total jps seen (from "all" jp_jpg object)
	private int numTrnFtrs;
	
	private ConcurrentSkipListMap<ExDataType, HashSet<SOMMapNode>> nodesWithEx, nodesWithNoEx;	//map nodes that have/don't have training examples - for display only

	//array of per jp treemaps of nodes keyed by jp weight
	private TreeMap<Float,ArrayList<SOMMapNode>>[] PerFtrHiWtMapNodes;	
	//////////////////////////////
	//data in files created by SOM_MAP separated by spaces
	public static final String SOM_FileToken = " ", csvFileToken = "\\s*,\\s*";	

	////////////////////		
	//data type to use to describe/train map
	public static final int useUnmoddedDat = 0, useScaledDat = 1, useNormedDat = 2;
	public static final String[] uiMapTrainFtrTypeList = new String[] {"Unmodified","Standardized (0->1 per ftr)","Normalized (vector mag==1)"};
	//types of possible mappings to particular map node as bmu
	//corresponds to these values : all ExDataTypes except last 2
	private static String[] nodeBMUMapTypes;// = new String[] {"Training", "Testing", "Products"};
	//feature type used for training currently trained/loaded map
	protected int curMapTrainFtrType;	
	//feature type used for testing/finding proposals currently - comparing features to map
	protected int curMapTestFtrType;
	//distance to use :  1: chisq features or 0 : regular feature dists
	protected boolean useChiSqDist;	
	//map dims is used to calculate distances for BMUs - based on screen dimensions - need to change this?
	protected float[] mapDims;
	//# of nodes in x/y
	protected int mapNodeCols =0, mapNodeRows =0;
	//# of nodes / map dim  in x/y
	protected float nodeXPerPxl, nodeYPerPxl;
	//threshold of u-dist for nodes to belong to same segment
	protected static float nodeInSegDistThresh = .3f;
	protected float mapMadeWithSegThresh = 0.0f;
	
	private int[] stFlags;						//state flags - bits in array holding relevant process info
	private static final int
			debugIDX 					= 0,
			isMTCapableIDX				= 1,
			mapDataLoadedIDX			= 2,			//som map data is cleanly loaded
			loaderRtnIDX				= 3,			//dataloader has finished - wait on this to draw map
			denseTrainDataSavedIDX 		= 4,			//all current prospect data has been saved as a training data file for SOM (.lrn format) 
			sparseTrainDataSavedIDX		= 5,			//sparse data format using .svm file descriptions (basically a map with a key:value pair of ftr index : ftr value
			testDataSavedIDX			= 6,			//save test data in sparse format csv
		//data types mapped flags - ready to save results
			trainDataMappedIDX			= 7,
			prodDataMappedIDX			= 8,
			testDataMappedIDX			= 9,
			validateDataMappedIDX		= 10;
		
	public static final int numBaseFlags = 11;	
	//numFlags is set by instancing map manager
	
	//threading constructions - allow map manager to own its own threading executor
	protected ExecutorService th_exec;	//to access multithreading - instance from calling program
	protected final int numUsableThreads;		//# of threads usable by the application

	// String[] _dirs : idx 0 is config directory, as specified by cmd line; idx 1 is data directory, as specified by cmd line
	// String[] _args : command line arguments other than directory info
	public SOMMapManager(SOMMapUIWin _win, float[] _dims, TreeMap<String, Object> _argsMap) {
		pa=null;//assigned by win if it exists
		win=_win;			
		mapDims = _dims;
		mapUIAPI = buildSOM_UI_Interface();
		initFlags();		
		//message object manages displaying to screen and potentially to log files - needs to be built first
		long mapMgrBuiltTime  = Instant.now().toEpochMilli();
		setMsgObj(new MessageObject(pa,mapMgrBuiltTime));
		//this is to make sure we always save the log file - this will be executed on shutdown, similar to code in a destructor in c++
		Runtime.getRuntime().addShutdownHook(new Thread() {public void run() {	if(msgObj==null) {return;}msgObj.dispInfoMessage("SOMMapManager", "ctor->Shutdown Hook", "Running msgObj finish log code");	msgObj.FinishLog();}});
		
		//build project configuration data object - this manages all file locations and other configuration options
		//needs to have msgObj defined before called
		projConfigData = buildProjConfigData(_argsMap);
		Integer _logLevel = (Integer)_argsMap.get("logLevel");
		msgObj.setOutputMethod(projConfigData.getFullLogFileNameString(), _logLevel);
		
		//fileIO is used to load and save info from/to local files except for the raw data loading, which has its own handling
		fileIO = new FileIOManager(msgObj,"SOMMapManager");
		//want # of usable background threads.  Leave 2 for primary process (and potential draw loop)
		numUsableThreads = Runtime.getRuntime().availableProcessors() - 2;
		//set if this is multi-threaded capable - need more than 1 outside of 2 primary threads (i.e. only perform multithreaded calculations if 4 or more threads are available on host)
		setFlag(isMTCapableIDX, numUsableThreads>1);
		//th_exec = Executors.newCachedThreadPool();// Executors.newFixedThreadPool(numUsableThreads);
		if(getFlag(isMTCapableIDX)) {
			//th_exec = Executors.newFixedThreadPool(numUsableThreads+1);//fixed is better in that it will not block on the draw - this seems really slow on the prospect mapping
			th_exec = Executors.newCachedThreadPool();// this is performing much better even though it is using all available threads
		} else {//setting this just so that it doesn't fail somewhere - won't actually be exec'ed
			th_exec = Executors.newCachedThreadPool();// Executors.newFixedThreadPool(numUsableThreads);
		}
		
		resetTrainDataAras();
	}//ctor
	
	/**
	 * build instance-specific project file configuration - necessary if using project-specific config file
	 */	
	protected abstract SOMProjConfigData buildProjConfigData(TreeMap<String, Object> _argsMap);
	
	/**
	 * build an interface to manage communications between UI and SOM map dat
	 * This interface will need to include a reference to an application-specific UI window
	 */
	protected abstract SOMUIToMapCom buildSOM_UI_Interface();
	
	public static String[] getNodeBMUMapTypes() {
		String[] typeList = ExDataType.getListOfTypes();
		if (nodeBMUMapTypes==null) {
			nodeBMUMapTypes = new String[typeList.length-2];
			for(int i=0;i<nodeBMUMapTypes.length;++i) {	nodeBMUMapTypes[i]=typeList[i];	}			
		}
		return nodeBMUMapTypes;
	}
	
	//use this to set window/UI components, if exist
	public void setPADispWinData(SOMMapUIWin _win, my_procApplet _pa) {
		win=_win;
		pa=_pa;
		MessageObject.pa = _pa;
		projConfigData.setUIValsFromLoad();
	}//setPAWindowData

	//determine how many values should be per thread, if 
	public int calcNumPerThd(int numVals, int numThds) {
		//return (int) Math.round((numVals + Math.round(numThds/2))/(1.0*numThds));
		return (int) ((numVals -1)/(1.0*numThds)) + 1;
		//=\operatorname{round}\left(\frac{x+\operatorname{floor}\left(\frac{7}{2}\right)}{7}\ \right)
	}//calcNumPerThd
	
	
	protected void resetTrainDataAras() {
		msgObj.dispMessage("SOMMapManager","resetTrainDataAras","Init Called", MsgCodes.info5);
		inputData = new SOMExample[0];
		testData = new SOMExample[0];
		trainData = new SOMExample[0];
		numInputData=0;
		numTrainData=0;
		numTestData=0;		
		nodesWithEx = new ConcurrentSkipListMap<ExDataType, HashSet<SOMMapNode>>();
		nodesWithNoEx = new ConcurrentSkipListMap<ExDataType, HashSet<SOMMapNode>>();
		for (ExDataType _type : ExDataType.values()) {
			nodesWithEx.put(_type, new HashSet<SOMMapNode>());
			nodesWithNoEx.put(_type, new HashSet<SOMMapNode>());		
		}
		msgObj.dispMessage("SOMMapManager","resetTrainDataAras","Init Finished", MsgCodes.info5);
	}//resetTrainDataAras()
	
	public String getDataTypeNameFromCurFtrTrainType() {return getDataTypeNameFromInt(curMapTrainFtrType);}	
	public String getDataTypeNameFromCurFtrTestType() {return getDataTypeNameFromInt(curMapTestFtrType);}	
	//useUnmoddedDat = 0, useScaledDat = 1, useNormedDat
	public String getDataTypeNameFromInt(int dataFrmt) {
		switch(dataFrmt) {
		case useUnmoddedDat : {return "unModFtrs";}
		case useScaledDat : {return "stdFtrs";}
		case useNormedDat : {return "normFtrs";}
		default : {return null;}		//unknown data frmt type
		}
	}//getDataTypeNameFromInt
	
	public String getDataDescFromCurFtrTrainType()  {return getDataDescFromInt(curMapTrainFtrType);}
	public String getDataDescFromCurFtrTestType()  {return getDataDescFromInt(curMapTestFtrType);}
	public String  getDataDescFromInt(int dataFrmt) {
		switch(dataFrmt) {
		case useUnmoddedDat : {return "Unmodified";}
		case useScaledDat : {return "Standardized (across all examples per feature)";}
		case useNormedDat : {return "Normalized (across all features per example)";}
		default : {return null;}		//unknown data frmt type
		}
	}//getDataTypeNameFromInt
	
	//return data format enum val based on string name
	public int getDataFrmtTypeFromName(String dataFrmtName) {
		String comp = dataFrmtName.toLowerCase();
		switch(comp) {
		case "unmodftrs": {return useUnmoddedDat;}
		case "stdftrs"	: {return useScaledDat;}
		case "normftrs"	: {return useNormedDat;}
		default : {return -1;}		//unknown data frmt type
		}		
	}//getDataFrmtTypeFromName

	//set input data and shuffle it; partition test and train arrays 
	protected void setInputTestTrainDataArasShuffle(SOMExample[] _inData, float trainTestPartition, boolean isBuildingNewMap) {		
		msgObj.dispMessage("SOMMapManager","setInputTestTrainDataArasShuffle","Shuffling Input, Building Training and Testing Partitions.", MsgCodes.info5);
		//set partition size in project config
		projConfigData.setTrainTestPartition(trainTestPartition);
		inputData = _inData;		
		//shuffleProspects(ProspectExample[] _list, long seed) -- performed in place - use same key so is reproducible training, always has same shuffled order
		inputData = shuffleProspects(inputData, 12345L);
		numTrainData = (int) (inputData.length * trainTestPartition);			
		numTestData = inputData.length - numTrainData;		
		//build train and test partitions
		trainData = new SOMExample[numTrainData];	
		msgObj.dispMessage("SOMMapManager","setInputTestTrainDataArasShuffle","# of training examples : " + numTrainData + " inputData size : " + inputData.length, MsgCodes.info3);
		for (int i=0;i<trainData.length;++i) {trainData[i]=inputData[i];trainData[i].setIsTrainingDataIDX(true, i);}
		testData = new SOMExample[numTestData];
		for (int i=0;i<testData.length;++i) {testData[i]=inputData[i+numTrainData];testData[i].setIsTrainingDataIDX(false, i);}		
		//build file names, including info for data type used to train map
		if (isBuildingNewMap) {//will save results to new directory
			projConfigData.buildDateTimeStrAraAndDType(getDataTypeNameFromCurFtrTrainType());
			projConfigData.launchTestTrainSaveThrds(th_exec, curMapTrainFtrType, numTrnFtrs,trainData,testData);				//save testing and training data
		} 
		msgObj.dispMessage("SOMMapManager","setInputTestTrainDataArasShuffle","Finished Shuffling Input, Building Training and Testing Partitions. Train size : " + numTrainData+ " Testing size : " + numTestData+".", MsgCodes.info5);
	}//setInputTestTrainDataArasShuffle
	
//	//load _all_ preprocessed data
//	protected abstract void loadAllPreprocData();
	
	//load all preprocessed data from default data location
	protected abstract void loadPreProcTestTrainData();	
	protected abstract void loadPreProcTestTrainData(String subDir);
	//using the passed map, build the testing and training data partitions and save them to files
	protected abstract void buildTestTrainFromPartition(float trainTestPartition, boolean isBuildingNewMap);
	
	//load preproc customer csv and build training and testing partitions - testing partition not necessary 
	public void loadPreprocAndBuildTestTrainPartitions() {loadPreprocAndBuildTestTrainPartitions(projConfigData.getTrainTestPartition());}//whatever most recent setting is
	public void loadPreprocAndBuildTestTrainPartitions(float trainTestPartition) {
		msgObj.dispMessage("SOMMapManager","loadPreprocAndBuildTestTrainPartitions","Start Loading all CSV example Data to train map.", MsgCodes.info5);
		loadPreProcTestTrainData();
		//build SOM data
		buildTestTrainFromPartition(trainTestPartition, true);	
		saveMinsAndDiffs();		
		msgObj.dispMessage("SOMMapManager","loadPreprocAndBuildTestTrainPartitions","Finished Loading all CSV example Data to train map.", MsgCodes.info5);
	}//loadPreprocAndBuildTestTrainPartitions
	
	//load the data used to build a map as well as existing map results
	//NOTE this may break if different data is used to build the map than the current data being loaded
	public void loadPretrainedExistingMap() {
		//load customer data into preproc  -this must be data used to build map
		loadPreProcTestTrainData();
		//for prebuilt map
		projConfigData.setSOM_UsePreBuilt();	
		//build data partitions - use partition size set via constants in debug
		buildTestTrainFromPartition(projConfigData.getTrainTestPartition(), false);	
		msgObj.dispMultiLineInfoMessage("SOMMapManager","loadPretrainedExistingMap","Current projConfigData before dataLoader Call : " + projConfigData.toString());
		//th_exec.execute(new SOMDataLoader(this,projConfigData));//fire and forget load task 
		SOMDataLoader ldr = new SOMDataLoader(this,projConfigData);//fire and forget load task 
		ldr.run();
	}//dbgBuildExistingMap
	
	
	//build new SOM_MAP map using UI-entered values, then load resultant data
	//with maps of required SOM exe params
	//TODO this will be changed to not pass values from UI, but rather to finalize and save values already set in SOM_MapDat object from UI or other user input
	public void updateAllMapArgsFromUI(HashMap<String, Integer> mapInts, HashMap<String, Float> mapFloats, HashMap<String, String> mapStrings){
		//set and save configurations
		projConfigData.setSOM_MapArgs(mapInts, mapFloats, mapStrings);
	}
		
	public boolean runSOMExperiment() {
		boolean runSuccess = projConfigData.runSOMExperiment();
		if(!runSuccess) {			return false;		}
		msgObj.dispMessage("SOMMapManager","buildNewSOMMap","Current projConfigData before dataLoader Call : " + projConfigData.toString(), MsgCodes.info1);
		//th_exec.execute(new SOMDataLoader(this,projConfigData));//fire and forget load task 
		SOMDataLoader ldr = new SOMDataLoader(this,projConfigData);//fire and forget load task 
		ldr.run();
		return true;
	}//buildNewSOMMap	
	
	//this will load the default map training configuration
	public void loadSOMConfig() {	projConfigData.loadDefaultSOMExp_Config();	}//loadSOMConfig
	
	//load all training data, default map configuration, and build map
	public void loadTrainDataMapConfigAndBuildMap() {
		msgObj.dispMessage("SOMMapManager","loadTrainDataMapConfigAndBuildMap","Start Loading training data and building map.", MsgCodes.info1);
		loadPreprocAndBuildTestTrainPartitions();
		projConfigData.loadDefaultSOMExp_Config();		
		boolean success = projConfigData.runSOMExperiment();
		msgObj.dispMessage("SOMMapManager","loadTrainDataMapConfigAndBuildMap","Finished Loading training data and building map. Success : " + success, MsgCodes.info1);
	}//loadTrainDataMapConfigAndBuildMap()
	
	//load currently specified map in config file, load preproc train data used to build map
	//load specified prospects and products, and map them
	public void loadMapProcAllData(Double prodZoneDistThresh) {
		msgObj.dispMessage("SOMMapManager","loadMapProcAllData","Start loading Map and data to build proposals.", MsgCodes.info1);
		//load preproc data used to train map - it is assumed this data is in default directory
		msgObj.dispMessage("SOMMapManager","loadMapProcAllData","First load training data used to build map - it is assumed this data is in default preproc directory.", MsgCodes.info1);
		//load customer data into preproc  -this must be data used to build map
		loadPreProcTestTrainData();
		//for prebuilt map
		projConfigData.setSOM_UsePreBuilt();	
		//build data partitions - use partition size set via constants in debug
		buildTestTrainFromPartition(projConfigData.getTrainTestPartition(), false);	
		msgObj.dispMessage("SOMMapManager","loadPretrainedExistingMap","Current projConfigData before dataLoader Call : " + projConfigData.toString(), MsgCodes.info3);
		//don't execute in a thread, execute synchronously so we can use results
		//th_exec.execute(new SOMDataLoader(this,projConfigData));//fire and forget load task 
		SOMDataLoader ldr = new SOMDataLoader(this,projConfigData);//fire and forget load task 
		ldr.run();
		msgObj.dispMessage("SOMMapManager","loadPretrainedExistingMap","Data loader finished loading map nodes and matching training data and products to BMUs." , MsgCodes.info3);
		//by here map is loaded and customers and products are mapped.  Now map prospects
		loadMapProcAllData_Indiv(prodZoneDistThresh);
		
		msgObj.dispMessage("SOMMapManager","loadMapProcAllData","Finished Loading Map and data to build proposals.", MsgCodes.info1);
	}//loadMapProcAllData()
	
	protected abstract void loadMapProcAllData_Indiv(Double prodZoneDistThresh);
	
	
	//Build map from data by aggregating all training data, building SOM exec string from UI input, and calling OS cmd to run SOM_MAP
	public boolean buildNewMap(SOM_MapDat mapExeDat){
		boolean success = false;
		try {					success = _buildNewMap(mapExeDat);			} 
		catch (IOException e){	msgObj.dispMessage("SOMMapManager","buildNewMap","Error running map defined by : " + mapExeDat.toString() + " :\n " + e.getMessage(), MsgCodes.error1);	return false;}		
		return success;
	}//buildNewMap
	//launch process to exec SOM_MAP
	private boolean _buildNewMap(SOM_MapDat mapExeDat) throws IOException{
		boolean showDebug = getIsDebug(), 
				success = true;		
		msgObj.dispMessage("SOMMapManager","_buildNewMap","buildNewMap Starting", MsgCodes.info5);
		msgObj.dispMessage("SOMMapManager","_buildNewMap","Execution String for running manually : \n"+mapExeDat.getDbgExecStr(), MsgCodes.warning2);
		String[] cmdExecStr = mapExeDat.getExecStrAra();
		//if(showDebug){
		msgObj.dispMessage("SOMMapManager","_buildNewMap","Execution Arguments passed to SOM, parsed by flags and values: ", MsgCodes.info2);
		msgObj.dispMessageAra(cmdExecStr,"SOMMapManager","_buildNewMap",2, MsgCodes.info2);//2 strings per line, display execution command	
		//}
		//http://stackoverflow.com/questions/10723346/why-should-avoid-using-runtime-exec-in-java		
		String wkDirStr = mapExeDat.getExeWorkingDir(), 
				cmdStr = mapExeDat.getExename(),
				argsStr = "";
		String[] execStr = new String[cmdExecStr.length +1];
		execStr[0] = wkDirStr + cmdStr;
		//for(int i =2; i<cmdExecStr.length;++i){execStr[i-1] = cmdExecStr[i]; argsStr +=cmdExecStr[i]+" | ";}
		for(int i = 0; i<cmdExecStr.length;++i){execStr[i+1] = cmdExecStr[i]; argsStr +=cmdExecStr[i]+" | ";}
		msgObj.dispMessage("SOMMapManager","_buildNewMap","\nwkDir : "+ wkDirStr + "\ncmdStr : " + cmdStr + "\nargs : "+argsStr, MsgCodes.info1);
		
		//monitor in multiple threads, either msgs or errors
		List<Future<Boolean>> procMsgMgrsFtrs = new ArrayList<Future<Boolean>>();
		List<ProcConsoleMsgMgr> procMsgMgrs = new ArrayList<ProcConsoleMsgMgr>(); 
		
		ProcessBuilder pb = new ProcessBuilder(execStr);		
		File wkDir = new File(wkDirStr); 
		pb.directory(wkDir);
		
		String resultIn = "",resultErr = "";
		try {
			final Process process=pb.start();			
			ProcConsoleMsgMgr inMsgs = new ProcConsoleMsgMgr(this,process,new InputStreamReader(process.getInputStream()), "Input" );
			ProcConsoleMsgMgr errMsgs = new ProcConsoleMsgMgr(this,process,new InputStreamReader(process.getErrorStream()), "Error" );
			procMsgMgrs.add(inMsgs);
			procMsgMgrs.add(errMsgs);			
			procMsgMgrsFtrs = th_exec.invokeAll(procMsgMgrs);for(Future<Boolean> f: procMsgMgrsFtrs) { f.get(); }

			resultIn = inMsgs.getResults(); 
			resultErr = errMsgs.getResults() ;//results of running map TODO save to log?	
			if(resultErr.toLowerCase().contains("error:")) {throw new InterruptedException("SOM Executable aborted");}
		} catch (IOException e) {
			msgObj.dispMessage("SOMMapManager","_buildNewMap","buildNewMap Process failed with IOException : \n" + e.toString() + "\n\t"+ e.getMessage(), MsgCodes.error1);success = false;
	    } catch (InterruptedException e) {
	    	msgObj.dispMessage("SOMMapManager","_buildNewMap","buildNewMap Process failed with InterruptedException : \n" + e.toString() + "\n\t"+ e.getMessage(), MsgCodes.error1);success = false;	    	
	    } catch (ExecutionException e) {
	    	msgObj.dispMessage("SOMMapManager","_buildNewMap","buildNewMap Process failed with ExecutionException : \n" + e.toString() + "\n\t"+ e.getMessage(), MsgCodes.error1);success = false;
		}		
		
		msgObj.dispMessage("SOMMapManager","_buildNewMap","buildNewMap Finished", MsgCodes.info5);	
		return success;
	}//_buildNewMap
	
	protected abstract int _getNumSecondaryMaps();
	//only appropriate if using UI
	public void setSaveLocClrImg(boolean val) {if (win != null) { win.setPrivFlags(SOMMapUIWin.saveLocClrImgIDX,val);}}
	//whether or not distances between two datapoints assume that absent features in smaller-length datapoints are 0, or to ignore the values in the larger datapoints
	public abstract void setMapExclZeroFtrs(boolean val);

	//take existing map and use U-Matrix-distances to determine segment membership.Large distances > thresh (around .7) mean nodes are on a boundary
	public void buildSegmentsOnMap() {//need to find closest
		if (nodeInSegDistThresh == mapMadeWithSegThresh) {return;}
		if ((MapNodes == null) || (MapNodes.size() == 0)) {return;}
		msgObj.dispMessage("SOMMapManager","buildSegmentsOnMap","Started building cluster map", MsgCodes.info5);	
		//clear existing segments
		for (SOMMapNode ex : MapNodes.values()) {ex.clearSeg();}
		segments = new ArrayList<SOMMapSegment>();
		SOMMapSegment seg;
		for (SOMMapNode ex : MapNodes.values()) {
			if(ex.shouldAddToSegment(nodeInSegDistThresh)) {
				seg = new SOMMapSegment(this, nodeInSegDistThresh);
				ex.addToSeg(seg);
				segments.add(seg);
			}
		}		
		mapMadeWithSegThresh = nodeInSegDistThresh;
		if(win!=null) {win.setMapSegmentImgClrs();}
		msgObj.dispMessage("SOMMapManager","buildSegmentsOnMap","Finished building cluster map", MsgCodes.info5);			
	}//buildSegmentsOnMap()
	
	public abstract ISOMMap_DispExample buildTmpDataExampleFtrs(myPointf ptrLoc, TreeMap<Integer, Float> ftrs, float sens);
	public abstract ISOMMap_DispExample buildTmpDataExampleDists(myPointf ptrLoc, float dist, float sens);
	
	/////////////////////////////////////
	// map node management - map nodes are represented by SOMExample objects called SOMMapNodes
	// and are classified as either having examples that map to them (they are bmu's for) or not having any
	//   furthermore, they can have types of examples mapping to them - training, product, validation
	
	//products are zone/segment descriptors corresponding to certain feature configurations
	protected abstract void setProductBMUs();
	
	//once map is built, find bmus on map for each test data example
	protected void setTestBMUs() {
		msgObj.dispMessage("SOMMapManager","setTestBMUs","Start processing test data examples for BMUs.", MsgCodes.info1);	
		if(testData.length > 0) {			_setExamplesBMUs(testData, "Testing", ExDataType.Testing,testDataMappedIDX);		} 
		else {			msgObj.dispMessage("SOMMapManager","setTestBMUs","No Test data to map to BMUs. Aborting.", MsgCodes.warning5);		}
		msgObj.dispMessage("SOMMapManager","setTestBMUs","Finished processing test data examples for BMUs.", MsgCodes.info1);	
	}//setProductBMUs
	
	//incrementally load true prospect data, processing each 
	protected void setValidationDataBMUs() {
		msgObj.dispMessage("SOMMapManager","setValidationDataBMUs","Start processing "+validationData.length+" validation data examples for BMUs.", MsgCodes.info1);	
		//save true prospect-to-product mappings
		if(validationData.length > 0) {		_setExamplesBMUs(validationData, "Validation", ExDataType.Validation,validateDataMappedIDX);		} 		
		else {			msgObj.dispMessage("SOMMapManager","setValidationDataBMUs","Unable to process due to no validation examples loaded to map to BMUs. Aborting.", MsgCodes.warning5);		}	
		msgObj.dispMessage("SOMMapManager","setValidationDataBMUs","Finished processing "+validationData.length+" validation data examples for BMUs", MsgCodes.info1);	
	}//setTrueProspectBMUs
	
	//set examples - either test data or 
	protected void _setExamplesBMUs(SOMExample[] exData, String dataTypName, ExDataType dataType, int _rdyToSaveFlagIDX) {
		msgObj.dispMessage("SOMMapManager","_setExamplesBMUs","Start Mapping " +exData.length + " "+dataTypName+" data to best matching units.", MsgCodes.info5);
		//launch a MapTestDataToBMUs_Runner - keep in main thread to enable more proc threads
		MapTestDataToBMUs_Runner rnr = new MapTestDataToBMUs_Runner(this, th_exec, exData, dataTypName, dataType, _rdyToSaveFlagIDX);	
		rnr.run();
	}//_setExamplesBMUs
	
	//call 1 time for any particular type of data
	protected void _finalizeBMUProcessing(SOMExample[] _exs, ExDataType _type) {
		int dataTypeVal = _type.getVal();
		for(SOMMapNode mapNode : MapNodes.values()){mapNode.clearBMUExs(dataTypeVal);addExToNodesWithNoExs(mapNode, _type);}	
		for (int i=0;i<_exs.length;++i) {	
			SOMExample ex = _exs[i];
			SOMMapNode bmu = ex.getBmu();
			if(null!=bmu) {
				bmu.addExToBMUs(ex,dataTypeVal);	
				addExToNodesWithExs(bmu, _type);
			}
		}
		filterExFromNoEx(_type);		//clear out all nodes that have examples from struct holding no-example map nodes
		finalizeExMapNodes(_type);		
	}//_finalizeBMUProcessing
	
	public abstract void saveProductMappings(double prodZoneDistThresh);
	public abstract void saveTestTrainMappings(double prodZoneDistThresh);
	public abstract void saveValidationMappings(double prodZoneDistThresh);
	
	
	protected void _dispMappingNotDoneMsg(String callingClass, String callingMethod, String _datType) {
		msgObj.dispMessage(callingClass,callingMethod, "Mapping "+_datType+" examples to BMUs not yet complete so no mappings are being saved - please try again later", MsgCodes.warning4);		
	}
	
	public void clearBMUNodesWithExs(ExDataType _type) {							nodesWithEx.get(_type).clear();}
	public void clearBMUNodesWithNoExs(ExDataType _type) {							nodesWithNoEx.get(_type).clear();}
	public void addExToNodesWithExs(SOMMapNode node, ExDataType _type) {			nodesWithEx.get(_type).add(node);}	
	public void addExToNodesWithNoExs(SOMMapNode node, ExDataType _type) {			nodesWithNoEx.get(_type).add(node);}	
	public int getNumNodesWithBMUExs(ExDataType _type) {return nodesWithEx.get(_type).size();}
	public int getNumNodesWithNoBMUExs(ExDataType _type) {return nodesWithNoEx.get(_type).size();}
	public HashSet<SOMMapNode> getNodesWithExOfType(ExDataType _type){return nodesWithEx.get(_type);}
	public HashSet<SOMMapNode> getNodesWithNoExOfType(ExDataType _type){return nodesWithNoEx.get(_type);}
	//remove all examples in "with" struct from "without" struct
	public void filterExFromNoEx(ExDataType _type) {
		//remove all nodes that have been selected by some data point of type _type as a bmu from the "no examples" list
		HashSet<SOMMapNode> withMap = nodesWithEx.get(_type),withOutMap = nodesWithNoEx.get(_type);		
		for (SOMMapNode tmpMapNode : withMap) {			withOutMap.remove(tmpMapNode);		}
	}//filterExFromNoEx
	
	//finalize som map nodes that have examples tied to them
	public void finalizeExMapNodes(ExDataType _type) {
		HashSet<SOMMapNode> withMap = nodesWithEx.get(_type);
		int typeIDX = _type.getVal();
		for(SOMMapNode node : withMap){		node.finalizeAllBmus(typeIDX);	}
	}//finalizeExMapNodes
	
	
	///////////////////////////
	// mapNodes obj
	//called when som wts are first loaded
	public void initMapNodes() {
		MapNodes = new TreeMap<Tuple<Integer,Integer>, SOMMapNode>();
		//this will hold all map nodes keyed by the ftr idx where they have non-zero weight
		MapNodesByFtrIDX = new TreeMap<Integer, HashSet<SOMMapNode>>();
	}//initMapNodes()
	
	//only appropriate if using UI
	public void initMapFtrVisAras(int numTrainFtrs) {
		if (win != null) {
			int num2ndTrainFtrs = _getNumSecondaryMaps();
			msgObj.dispMessage("SOMMapManager","initMapAras","Initializing per-feature map display to hold : "+ numTrainFtrs +" primary feature and " +num2ndTrainFtrs + " secondary feature map images.", MsgCodes.info1);
			win.initMapAras(numTrainFtrs, num2ndTrainFtrs);
		} else {msgObj.dispMessage("SOMMapManager","initMapAras","Display window doesn't exist, can't build map visualization image arrays; ignoring.", MsgCodes.warning2);}
	}//initMapAras
	
	//process map node's ftr vals, add node to map, and add node to struct without any training examples (initial state for all map nodes)
	public void addToMapNodes(Tuple<Integer,Integer> key, SOMMapNode mapnode, float[] tmpMapMaxs, int numTrainFtrs) {
		float[] ftrData = mapnode.getFtrs();
		for(int d = 0; d<numTrainFtrs; ++d){
			map_ftrsMean[d] += ftrData[d];
			tmpMapMaxs[d] = (tmpMapMaxs[d] < ftrData[d] ? ftrData[d]  : tmpMapMaxs[d]);
			map_ftrsMin[d] = (map_ftrsMin[d] > ftrData[d] ? ftrData[d]  : map_ftrsMin[d]);
		}
		MapNodes.put(key, mapnode);	
		Integer[] nonZeroIDXs = mapnode.getNonZeroIDXs();
		for(Integer idx : nonZeroIDXs) {
			HashSet<SOMMapNode> nodeSet = MapNodesByFtrIDX.get(idx);
			if(null==nodeSet) {nodeSet = new HashSet<SOMMapNode>();MapNodesByFtrIDX.put(idx,nodeSet);}
			nodeSet.add(mapnode);
		}	
		//initialize : add all nodes to set, will remove nodes when they get mappings
		addExToNodesWithNoExs(mapnode, ExDataType.Training);//nodesWithNoTrainEx.add(dpt);				//initialize : add all nodes to set, will remove nodes when they get mappings
	}//addToMapNodes
	
	public SOMMapNode getMapNodeLoc(Tuple<Integer,Integer> key) {return MapNodes.get(key);}
	public TreeMap<Tuple<Integer,Integer>, SOMMapNode> getMapNodes(){return MapNodes;}
	public TreeMap<Integer, HashSet<SOMMapNode>> getMapNodesByFtr(){return MapNodesByFtrIDX;}
	//build all neighborhood values
	public void buildAllMapNodeNeighborhoods() {for(SOMMapNode ex : MapNodes.values()) {	ex.buildNeighborWtVals();	}}

	public float[] initMapMgrMeanMinVar(int numTrainFtrs) {
		map_ftrsMean = new float[numTrainFtrs];
		float[] tmpMapMaxs = new float[numTrainFtrs];
		map_ftrsMin = new float[numTrainFtrs];
		for(int l=0;l<map_ftrsMin.length;++l) {map_ftrsMin[l]=10000.0f;}//need to init to big number to get accurate min
		map_ftrsVar = new float[numTrainFtrs];
		return tmpMapMaxs;
	}//_initMapMgrMeanMinVar
	
	//set stats of map nodes based on passed features
	public void setMapNodeStatsFromFtr(float[] ftrData, float[] tmpMapMaxs, int numTrainFtrs) {
		for(int d = 0; d<numTrainFtrs; ++d){
			map_ftrsMean[d] += ftrData[d];
			tmpMapMaxs[d] = (tmpMapMaxs[d] < ftrData[d] ? ftrData[d]  : tmpMapMaxs[d]);
			map_ftrsMin[d] = (map_ftrsMin[d] > ftrData[d] ? ftrData[d]  : map_ftrsMin[d]);
		}
	}//setMapNodeStatsFromFtr

	@SuppressWarnings("unchecked")
	public void initPerFtrMapOfNodes(int numFtrs) {
		PerFtrHiWtMapNodes = new TreeMap[numFtrs];
		for (int i=0;i<PerFtrHiWtMapNodes.length; ++i) {PerFtrHiWtMapNodes[i] = new TreeMap<Float,ArrayList<SOMMapNode>>(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}});}
	}//initPerFtrMapOfNodes
	
	
	//put a map node in PerJPHiWtMapNodes per-jp array
	public void setMapNodeFtrStr(SOMMapNode mapNode) {
		TreeMap<Integer, Float> stdFtrMap = mapNode.getCurrentFtrMap(SOMMapManager.useScaledDat);
		for (Integer jpIDX : stdFtrMap.keySet()) {
			Float ftrVal = stdFtrMap.get(jpIDX);
			ArrayList<SOMMapNode> nodeList = PerFtrHiWtMapNodes[jpIDX].get(ftrVal);
			if (nodeList== null) {			nodeList = new ArrayList<SOMMapNode>();		}
			nodeList.add(mapNode);
			PerFtrHiWtMapNodes[jpIDX].put(ftrVal, nodeList);
		}		
	}//setMapNodeFtrStr
		
	//after all map nodes are loaded
	public void finalizeMapNodes(float[] tmpMapMaxs, int _numTrainFtrs, int _numEx) {
		//make sure both unmoddified features and std'ized features are built before determining map mean/var
		//need to have all features built to scale features		
		map_ftrsDiffs = new float[_numTrainFtrs];
		//initialize array of images to display map of particular feature with
		initMapFtrVisAras(_numTrainFtrs);
		
		for(int d = 0; d<map_ftrsMean.length; ++d){map_ftrsMean[d] /= 1.0f*_numEx;map_ftrsDiffs[d]=tmpMapMaxs[d]-map_ftrsMin[d];}
		//reset this to manage all map nodes
		PerFtrHiWtMapNodes = new TreeMap[_numTrainFtrs];
		for (int i=0;i<PerFtrHiWtMapNodes.length; ++i) {PerFtrHiWtMapNodes[i] = new TreeMap<Float,ArrayList<SOMMapNode>>(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}});}
		//build stats for map nodes
		float diff;
		float[] ftrData ;
		//for every node, now build standardized features 
		for(Tuple<Integer, Integer> key : MapNodes.keySet()){
			SOMMapNode tmp = MapNodes.get(key);
			tmp.buildStdFtrsMapFromFtrData_MapNode(map_ftrsMin, map_ftrsDiffs);
			//accumulate map ftr moments
			ftrData = tmp.getFtrs();
			for(int d = 0; d<map_ftrsMean.length; ++d){
				diff = map_ftrsMean[d] - ftrData[d];
				map_ftrsVar[d] += diff*diff;
			}
			setMapNodeFtrStr(tmp);
		}
		for(int d = 0; d<map_ftrsVar.length; ++d){map_ftrsVar[d] /= 1.0f*_numEx;}		
		setNumTrainFtrs(_numTrainFtrs); 

	}//finalizeMapNodes

	//build comparison array of vectors - this should be built before any comparisons or mappings are performed
//	//ever example needs to have this set
//	public void buildComparisonFtrVector(float ratio) {
//		todo need to modify code to support  compFtrMaps defined in SOM_Example
//		
//		
//	}
	
	
	//build a map node that is formatted specifically for this project
	public abstract SOMMapNode buildMapNode(Tuple<Integer,Integer>mapLoc, String[] tkns);

	///////////////////////////
	// end mapNodes 
	
	///////////////////////////
	// map data <--> ui  update code
	
	/**
	 * update map descriptor Float values from UI
	 * @param key : key descriptor of value
	 * @param val
	 */
	public void updateMapDatFromUI_Integer(String key, Integer val) {	projConfigData.updateMapDat_Integer(key,val, true, false);	}//updateMapDatFromUI_Integer
	
	/**
	 * update map descriptor Float values
	 * @param key : key descriptor of value
	 * @param val
	 */
	public void updateMapDatFromUI_Float(String key, Float val) {	projConfigData.updateMapDat_Float(key,val, true, false);	}//updateMapDatFromUI_Float
	
	/**
	 * update map descriptor String values
	 * @param key : key descriptor of value
	 * @param val
	 */
	public void updateMapDatFromUI_String(String key, String val) {	projConfigData.updateMapDat_String(key,val, true, false);	}//updateMapDatFromUI_String
	
	
	/**
	 * update UI from map data change (called from projConfig only
	 * @param key : key descriptor of value
	 * @param val
	 */
	public void updateUIMapData_Integer(String key, Integer val) {	mapUIAPI.updateUIFromMapDat_Integer(key, val);}//updateUIMapData_Integer
	
	/**
	 * update UI from map data change (called from projConfig only
	 * @param key : key descriptor of value
	 * @param val
	 */
	public void updateUIMapData_Float(String key, Float val) {		mapUIAPI.updateUIFromMapDat_Float(key, val);}//updateUIMapData_Float
	
	/**
	 * update UI from map data change (called from projConfig only
	 * @param key : key descriptor of value
	 * @param val
	 */
	public void updateUIMapData_String(String key, String val) {	mapUIAPI.updateUIFromMapDat_String(key, val);}//updateUIMapData_String
	
	
	///////////////////////////
	// end map data <--> ui  update code
	

	
	private Float[] _convStrAraToFloatAra(String[] tkns) {
		ArrayList<Float> tmpData = new ArrayList<Float>();
		for(int i =0; i<tkns.length;++i){tmpData.add(Float.parseFloat(tkns[i]));}
		return tmpData.toArray(new Float[0]);		
	}//_convStrAraToFloatAra	
	
	//read file with scaling/min values for Map to convert data back to original feature space - single row of data
	private Float[][] loadCSVSrcDataPoint(String fileName){		
		if(fileName.length() < 1){return null;}
		String [] strs= fileIO.loadFileIntoStringAra(fileName, "Loaded data file : "+fileName, "Error reading file : "+fileName);
		if(strs==null){return null;}	
		//line 0 is # of entries in array
		int numEntries = Integer.parseInt(strs[0].trim());
		Float[][] resAra = new Float[numEntries][];
		for(int i=0;i<numEntries;++i) {		resAra[i] = _convStrAraToFloatAra(strs[i+1].split(csvFileToken));	}
		return resAra;
	}//loadCSVData
	
	public boolean loadDiffsMins() {
		String diffsFileName = projConfigData.getSOMMapDiffsFileName(), minsFileName = projConfigData.getSOMMapMinsFileName();
		//load normalizing values for datapoints in weights - differences and mins, used to scale/descale training and map data
		diffsVals = loadCSVSrcDataPoint(diffsFileName);
		if((null==diffsVals) || (diffsVals.length < 1)){msgObj.dispMessage("SOMMapManager","loadDiffsMins","!!error reading diffsFile : " + diffsFileName, MsgCodes.error2); return false;}
		minsVals = loadCSVSrcDataPoint(minsFileName);
		if((null==minsVals)|| (minsVals.length < 1)){msgObj.dispMessage("SOMMapManager","loadDiffsMins","!!error reading minsFile : " + minsFileName, MsgCodes.error2); return false;}	
		return true;
	}//loadMinsAndDiffs()
	
	protected void setMinsAndDiffs(Float[][] _mins, Float[][] _diffs) {
		String dispStr = "MinsVals and DiffsVall being set : Mins is 2d ara of len : " + _mins.length + " with each array of len : [";		
		for(int i=0;i<_mins.length;++i) {dispStr+= ""+i+":"+_mins[i].length+", ";}							
		dispStr+= "] | Diffs is 2D ara of len : "+_diffs.length + " with each array of len : [";
		for(int i=0;i<_diffs.length;++i) {dispStr+= ""+i+":"+_diffs[i].length+", ";}
		dispStr+="]";
		msgObj.dispMessage("SOMMapManager","setMinsAndDiffs",dispStr, MsgCodes.info2);
		minsVals = _mins;
		diffsVals = _diffs;
	}
	
	public Float[] getMinVals(int idx){return minsVals[idx];}
	public Float[] getDiffVals(int idx){return diffsVals[idx];}
	public abstract Float[] getTrainFtrMins();
	public abstract Float[] getTrainFtrDiffs();

	public SOMExample[] getTrainingData() {return trainData;}
	
	//save mins and diffs of current training data
	protected void saveMinsAndDiffs() {
		msgObj.dispMessage("SOMMapManager","saveMinsAndDiffs","Begin Saving Mins and Diffs Files", MsgCodes.info1);
		//save mins/maxes so this file data be reconstructed
		//save diffs and mins - csv files with each field value sep'ed by a comma
		//boundary region for training data
		String[] minsAra = new String[minsVals.length+1];
		String[] diffsAra = new String[diffsVals.length+1];	
		minsAra[0]=""+minsVals.length;
		diffsAra[0] = ""+diffsVals.length;
		for(int i =0; i<minsVals.length; ++i){
			minsAra[i+1] = "";
			diffsAra[i+1] = "";
			for(int j =0; j<minsVals[i].length; ++j){		minsAra[i+1] += String.format("%1.7g", minsVals[i][j]) + ",";	}
			for(int j =0; j<diffsVals[i].length; ++j){		diffsAra[i+1] += String.format("%1.7g", diffsVals[i][j]) + ",";	}
		}
		String minsFileName = projConfigData.getSOMMapMinsFileName();
		String diffsFileName = projConfigData.getSOMMapDiffsFileName();				
		fileIO.saveStrings(minsFileName,minsAra);		
		fileIO.saveStrings(diffsFileName,diffsAra);		
		msgObj.dispMessage("SOMMapManager","saveMinsAndDiffs","Finished Saving Mins and Diffs Files", MsgCodes.info1);	
	}//saveMinsAndDiffs

	
	///////////////////////////////////////
	// ftr interp routines
	
	//return interpolated feature vector on map at location given by x,y, where x,y is float location of map using mapnodes as integral locations
	//only uses training features here
	public TreeMap<Integer, Float> getInterpFtrs(float x, float y){
		float xInterp = (x+mapNodeCols) %1, yInterp = (y+mapNodeRows) %1;
		int xInt = (int) Math.floor(x+mapNodeCols)%mapNodeCols, yInt = (int) Math.floor(y+mapNodeRows)%mapNodeRows, xIntp1 = (xInt+1)%mapNodeCols, yIntp1 = (yInt+1)%mapNodeRows;		//assume torroidal map		
		//always compare standardized feature data in test/train data to standardized feature data in map
		TreeMap<Integer, Float> LowXLowYFtrs = MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt)).getCurrentFtrMap(curMapTrainFtrType), LowXHiYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xInt,yIntp1)).getCurrentFtrMap(curMapTrainFtrType),
				 HiXLowYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yInt)).getCurrentFtrMap(curMapTrainFtrType),  HiXHiYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yIntp1)).getCurrentFtrMap(curMapTrainFtrType);
		try{
			TreeMap<Integer, Float> ftrs = interpTreeMap(interpTreeMap(LowXLowYFtrs, LowXHiYFtrs,yInterp,1.0f),interpTreeMap(HiXLowYFtrs, HiXHiYFtrs,yInterp,1.0f),xInterp,255.0f);	
			return ftrs;
		} catch (Exception e){
			msgObj.dispMessage("mySOMMapUIWin","getInterpFtrs","Exception triggered in mySOMMapUIWin::getInterpFtrs : \n"+e.toString() + "\n\tMessage : "+e.getMessage(), MsgCodes.error1);
			return null;
		}		
	}//getInterpFtrs
	
	//return interpolated UMatrix value on map at location given by x,y, where x,y  is float location of map using mapnodes as integral locations
	public Float getBiLinInterpUMatVal(float x, float y){
		float xInterp = (x+mapNodeCols) %1, yInterp = (y+mapNodeRows) %1;
		int xInt = (int) Math.floor(x+mapNodeCols)%mapNodeCols, yInt = (int) Math.floor(y+mapNodeRows)%mapNodeRows, xIntp1 = (xInt+1)%mapNodeCols, yIntp1 = (yInt+1)%mapNodeRows;		//assume torroidal map		
		//always compare standardized feature data in test/train data to standardized feature data in map
		Float LowXLowYUMat = MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt)).getUMatDist(), LowXHiYUMat= MapNodes.get(new Tuple<Integer, Integer>(xInt,yIntp1)).getUMatDist(),
				HiXLowYUMat= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yInt)).getUMatDist(),  HiXHiYUMat= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yIntp1)).getUMatDist();
		try{
			Float uMatVal = linInterpVal(linInterpVal(LowXLowYUMat, LowXHiYUMat,yInterp,1.0f),linInterpVal(HiXLowYUMat, HiXHiYUMat,yInterp,1.0f),xInterp,255.0f);	
			return uMatVal;
		} catch (Exception e){
			msgObj.dispMessage("mySOMMapUIWin","getInterpFtrs","Exception triggered in mySOMMapUIWin::getInterpFtrs : \n"+e.toString() + "\n\tMessage : "+e.getMessage(), MsgCodes.error1 );
			return 0.0f;
		}
	}//getInterpUMatVal
	
	private float linInterpVal(float a, float b, float t, float mult) {
		float res = 0.0f, Onemt = 1.0f-t;
		return mult*((a*Onemt) + (b*t));		
	}//interpVal
	
	//return interpolated UMatrix value on map at location given by x,y, where x,y  is float location of map using mapnodes as integral locations
	public Float getBiCubicInterpUMatVal(float x, float y){
		float xInterp = (x+mapNodeCols) %1, yInterp = (y+mapNodeRows) %1;
		int xInt = (int) Math.floor(x+mapNodeCols)%mapNodeCols, yInt = (int) Math.floor(y+mapNodeRows)%mapNodeRows;		//assume torroidal map		
		SOMMapNode ex = MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt));
		try{
			Float uMatVal = 255.0f*(ex.biCubicInterp(xInterp, yInterp));
			return uMatVal;
		} catch (Exception e){
			msgObj.dispMessage("mySOMMapUIWin","getInterpFtrs","Exception triggered in mySOMMapUIWin::getInterpFtrs : \n"+e.toString() + "\n\tMessage : "+e.getMessage(), MsgCodes.error1 );
			return 0.0f;
		}
	}//getInterpUMatVal
	
	public int getSegementColorAtPxl(float x, float y) {
		float xInterp = (x+mapNodeCols) %1, yInterp = (y+mapNodeRows) %1;
		int xInt = (int) Math.floor(x+mapNodeCols)%mapNodeCols, yInt = (int) Math.floor(y+mapNodeRows)%mapNodeRows;		//assume torroidal map		
		SOMMapNode ex = MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt));
		try{
			Float uMatVal = (ex.biCubicInterp(xInterp, yInterp));
			return (uMatVal > nodeInSegDistThresh ? 0 : ex.getSegClrAsInt());
		} catch (Exception e){
			msgObj.dispMessage("mySOMMapUIWin","getInterpFtrs","Exception triggered in mySOMMapUIWin::getInterpFtrs : \n"+e.toString() + "\n\tMessage : "+e.getMessage() , MsgCodes.error1);
			return 0;
		}
	}
	
	//get treemap of features that interpolates between two maps of features
	private TreeMap<Integer, Float> interpTreeMap(TreeMap<Integer, Float> a, TreeMap<Integer, Float> b, float t, float mult){
		TreeMap<Integer, Float> res = new TreeMap<Integer, Float>();
		float Onemt = 1.0f-t;
		if(mult==1.0) {
			//first go through all a values
			for(Integer key : a.keySet()) {
				Float aVal = a.get(key), bVal = b.get(key);
				if(bVal == null) {bVal = 0.0f;}
				res.put(key, (aVal*Onemt) + (bVal*t));			
			}
			//next all b values
			for(Integer key : b.keySet()) {
				Float aVal = a.get(key);
				if(aVal == null) {aVal = 0.0f;} else {continue;}		//if aVal is not null then calced already
				Float bVal = b.get(key);
				res.put(key, (aVal*Onemt) + (bVal*t));			
			}
		} else {//scale by mult - precomputes color values
			float m1t = mult*Onemt, mt = mult*t;
			//first go through all a values
			for(Integer key : a.keySet()) {
				Float aVal = a.get(key), bVal = b.get(key);
				if(bVal == null) {bVal = 0.0f;}
				res.put(key, (aVal*m1t) + (bVal*mt));			
			}
			//next all b values
			for(Integer key : b.keySet()) {
				Float aVal = a.get(key);
				if(aVal == null) {aVal = 0.0f;} else {continue;}		//if aVal is not null then calced already
				Float bVal = b.get(key);
				res.put(key, (aVal*m1t) + (bVal*mt));			
			}			
		}		
		return res;
	}//interpolate between 2 tree maps	

	//build a string to display an array of floats
	protected String getFloatAraStr(float[] datAra, String fmtStr, int brk) {
		String res = "[";
		int numVals = datAra.length;
		for (int i =0;i<numVals-1;++i) {
			if(datAra[i] != 0) {res +=""+String.format(fmtStr, datAra[i])+", ";	} else {	res +="0, ";	}
			if((i+1) % brk == 0) {res+="\n\t";}
		}
		if(datAra[numVals-1] != 0) {	res +=""+String.format(fmtStr, datAra[numVals-1])+"]";} else {	res +="0]";	}
		return res;
	}	
	//provides a list of indexes 0->len-1 that are Durstenfeld shuffled
	protected int[] shuffleAraIDXs(int len) {
		int[] res = new int[len];
		for(int i=0;i<len;++i) {res[i]=i;}
		ThreadLocalRandom tr = ThreadLocalRandom.current();
		int swap = 0;
		for(int i=(len-1);i>0;--i){
			int j = tr.nextInt(i + 1);//find random lower idx somewhere below current position, and swap current with this idx
			swap = res[i];
			res[i]=res[j];			
			res[j]=swap;			
		}
		return res;	
	}//shuffleAraIDXs
	
	//performs Durstenfeld  shuffle, leaves 0->stIdx alone - for testing/training data
	protected String[] shuffleStrList(String[] _list, String type, int stIdx){
		String tmp = "";
		ThreadLocalRandom tr = ThreadLocalRandom.current();
		for(int i=(_list.length-1);i>stIdx;--i){
			int j = tr.nextInt(i + 1-stIdx)+stIdx;//find random lower idx somewhere below current position but greater than stIdx, and swap current with this idx
			tmp = _list[i];
			_list[i] = _list[j];
			_list[j] = tmp;
		}
		return _list;
	}//shuffleStrList		

	//shuffle all data passed
	public SOMExample[] shuffleProspects(SOMExample[] _list, long seed) {
		SOMExample tmp;
		Random tr = new Random(seed);
		for(int i=(_list.length-1);i>0;--i){
			int j = tr.nextInt(i + 1);//find random lower idx somewhere below current position but greater than stIdx, and swap current with this idx
			tmp = _list[i];
			_list[i] = _list[j];
			_list[j] = tmp;
		}
		return _list;
	}
	
	///////////////////////////////
	// draw routines
	
	private static int dispTrainDataFrame = 0, numDispTrainDataFrames = 20;
	//if connected to UI, draw data - only called from window
	public final void drawTrainData(my_procApplet pa) {
		pa.pushMatrix();pa.pushStyle();
		if (trainData.length < numDispTrainDataFrames) {	for(int i=0;i<trainData.length;++i){		trainData[i].drawMeMap(pa);	}	} 
		else {
			for(int i=dispTrainDataFrame;i<trainData.length-numDispTrainDataFrames;i+=numDispTrainDataFrames){		trainData[i].drawMeMap(pa);	}
			for(int i=(trainData.length-numDispTrainDataFrames);i<trainData.length;++i){		trainData[i].drawMeMap(pa);	}				//always draw these (small count < numDispDataFrames
			dispTrainDataFrame = (dispTrainDataFrame + 1) % numDispTrainDataFrames;
		}
		pa.popStyle();pa.popMatrix();
	}//drawTrainData
	private static int dispTestDataFrame = 0, numDispTestDataFrames = 20;
	//if connected to UI, draw data - only called from window
	public final void drawTestData(my_procApplet pa) {
		pa.pushMatrix();pa.pushStyle();
		if (testData.length < numDispTestDataFrames) {	for(int i=0;i<testData.length;++i){		testData[i].drawMeMap(pa);	}	} 
		else {
			for(int i=dispTestDataFrame;i<testData.length-numDispTestDataFrames;i+=numDispTestDataFrames){		testData[i].drawMeMap(pa);	}
			for(int i=(testData.length-numDispTestDataFrames);i<testData.length;++i){		testData[i].drawMeMap(pa);	}				//always draw these (small count < numDispDataFrames
			dispTestDataFrame = (dispTestDataFrame + 1) % numDispTestDataFrames;
		}
		pa.popStyle();pa.popMatrix();
	}//drawTrainData
	private static int dispTruPrxpctDataFrame = 0, numDispTruPrxpctDataFrames = 100;
	public final void drawTruPrspctData(my_procApplet pa) {
		pa.pushMatrix();pa.pushStyle();
		if (validationData.length < numDispTruPrxpctDataFrames) {	for(int i=0;i<validationData.length;++i){		validationData[i].drawMeMap(pa);	}	} 
		else {
			for(int i=dispTruPrxpctDataFrame;i<validationData.length-numDispTruPrxpctDataFrames;i+=numDispTruPrxpctDataFrames){		validationData[i].drawMeMap(pa);	}
			for(int i=(validationData.length-numDispTruPrxpctDataFrames);i<validationData.length;++i){		validationData[i].drawMeMap(pa);	}				//always draw these (small count < numDispDataFrames
			dispTruPrxpctDataFrame = (dispTruPrxpctDataFrame + 1) % numDispTruPrxpctDataFrames;
		}
		pa.popStyle();pa.popMatrix();		
	}//drawTruPrspctData
	
	//draw boxes around each node representing umtrx values derived in SOM code - deprecated, now drawing image
	public void drawUMatrixVals(my_procApplet pa) {
		pa.pushMatrix();pa.pushStyle();
		for(SOMMapNode node : MapNodes.values()){	node.drawMeUMatDist(pa);	}		
		pa.popStyle();pa.popMatrix();
	}//drawUMatrix
	//draw boxes around each node representing segments these nodes belong to
	public void drawSegments(my_procApplet pa) {
		pa.pushMatrix();pa.pushStyle();
		for(SOMMapNode node : MapNodes.values()){	node.drawMeSegClr(pa);	}		
		pa.popStyle();pa.popMatrix();
	}//drawUMatrix
	
	public void drawAllNodesWted(my_procApplet pa, int curJPIdx) {//, int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		//pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		for(SOMMapNode node : MapNodes.values()){	node.drawMeSmallWt(pa,curJPIdx);	}
		pa.popStyle();pa.popMatrix();
	} 
		
	public void drawAllNodes(my_procApplet pa) {//, int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		//pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		for(SOMMapNode node : MapNodes.values()){	node.drawMeSmall(pa);	}
		pa.popStyle();pa.popMatrix();
	} 
	
	public void drawAllNodesNoLbl(my_procApplet pa) {//, int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		//pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		for(SOMMapNode node : MapNodes.values()){	node.drawMeSmallNoLbl(pa);	}
		pa.popStyle();pa.popMatrix();
	} 
	
	public void drawNodesWithWt(my_procApplet pa, float valThresh, int curJPIdx) {//, int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		//pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		TreeMap<Float,ArrayList<SOMMapNode>> map = PerFtrHiWtMapNodes[curJPIdx];
		SortedMap<Float,ArrayList<SOMMapNode>> headMap = map.headMap(valThresh);
		for(Float key : headMap.keySet()) {
			ArrayList<SOMMapNode> ara = headMap.get(key);
			for (SOMMapNode node : ara) {		node.drawMeWithWt(pa, 10.0f*key, new String[] {""+node.OID+" : ",String.format("%.4f",key)});}
		}
		pa.popStyle();pa.popMatrix();
	}//drawNodesWithWt
	
	public void drawExMapNodes(my_procApplet pa, ExDataType _type) {
		HashSet<SOMMapNode> nodes = nodesWithEx.get(_type);
		pa.pushMatrix();pa.pushStyle();
		int _typeIDX = _type.getVal();
		for(SOMMapNode node : nodes){	node.drawMePopLbl(pa, _typeIDX);}
		pa.popStyle();pa.popMatrix();		
	}	
	public void drawExMapNodesNoLbl(my_procApplet pa, ExDataType _type) {
		HashSet<SOMMapNode> nodes = nodesWithEx.get(_type);
		pa.pushMatrix();pa.pushStyle();
		int _typeIDX = _type.getVal();
		for(SOMMapNode node : nodes){				node.drawMePopNoLbl(pa, _typeIDX);}
		pa.popStyle();pa.popMatrix();		
	}
	
	//get ftr name/idx/instance-specific value based to save an image of current map
	public abstract String getSOMLocClrImgForFtrFName(int ftrIDX);
	//draw right sidebar data
	public void drawResultBar(my_procApplet pa, float yOff) {
		yOff-=4;
		float sbrMult = 1.2f, lbrMult = 1.5f;//offsets multiplier for barriers between contextual ui elements
		pa.pushMatrix();pa.pushStyle();
		drawResultBarPriv1(pa, yOff);
		
		drawResultBarPriv2(pa, yOff);

		drawResultBarPriv3(pa, yOff);

		pa.popStyle();pa.popMatrix();	
	}//drawResultBar
	
	//draw app-specific sidebar data
	protected abstract void drawResultBarPriv1(my_procApplet pa, float yOff);
	protected abstract void drawResultBarPriv2(my_procApplet pa, float yOff);
	protected abstract void drawResultBarPriv3(my_procApplet pa, float yOff);
	
	//invoke multi-threading call to build map imgs - called from UI window
	public void invokeSOMFtrDispBuild(List<SOMFtrMapVisImgBuilder> mapImgBuilders) {		
		try {
			List<Future<Boolean>> mapImgFtrs = th_exec.invokeAll(mapImgBuilders);
			for(Future<Boolean> f: mapImgFtrs) { f.get(); }
		} catch (Exception e) { e.printStackTrace(); }	
	}//
	
	public int[] getRndClr() { 				if (win==null) {return new int[] {255,255,255,255};}return win.pa.getRndClr2();}
	public int[] getRndClr(int alpha) {		if (win==null) {return new int[] {255,255,255,alpha};}return win.pa.getRndClr2(alpha);}


	//////////////////////////////
	// getters/setters
	
	//return a copy of the message object - making a copy so that multiple threads can consume without concurrency issues
	public MessageObject buildMsgObj() {return new MessageObject(msgObj);}
	
	//this is called when map is loaded, to set all bmus - application specific as to what gets mapped to map nodes
	public abstract void setAllBMUsFromMap();
	
	public void setMapImgClrs(){if (win != null) {win.setMapImgClrs();} else {msgObj.dispMessage("SOMMapManager","setMapImgClrs","Display window doesn't exist, can't build visualization images; ignoring.", MsgCodes.warning2);}}

	public boolean getUseChiSqDist() {return useChiSqDist;}
	public void setUseChiSqDist(boolean _useChiSq) {useChiSqDist=_useChiSq;}
	
	//set current map ftr type, and update ui if necessary
	public void setCurrentTrainDataFormat(int _frmt) {	curMapTrainFtrType = _frmt; }//setCurrentDataFormat
	public int getCurrentTrainDataFormat() {	return curMapTrainFtrType;}
	
	public void setCurrentTestDataFormat(int _frmt) {	curMapTestFtrType = _frmt; }//setCurrentDataFormat
	public int getCurrentTestDataFormat() {	return curMapTestFtrType;}
	public MessageObject getMsgObj(){	return msgObj;}
	public void setMsgObj(MessageObject msgObj) {	this.msgObj = msgObj;}
	public float getMapWidth(){return mapDims[0];}
	public float getMapHeight(){return mapDims[1];}
	public int getMapNodeCols(){return mapNodeCols;}
	public int getMapNodeRows(){return mapNodeRows;}	
	
	public float getNodePerPxlCol() {return nodeXPerPxl;}
	public float getNodePerPxlRow() {return nodeYPerPxl;}	
	//mean/var,mins/diffs of features of map nodes
	public float[] getMap_ftrsMean() {return map_ftrsMean;}			
	public float[] getMap_ftrsVar() {return map_ftrsVar;}			
	public float[] getMap_ftrsDiffs() {return map_ftrsDiffs;}			
	public float[] getMap_ftrsMin() {return map_ftrsMin;}		
	
	//project config manages this information now
	public Calendar getInstancedNow() { return projConfigData.getInstancedNow();}
	
	//set flag that SOM file loader is finished to false
	public void setLoaderRtnFalse() {setFlag(loaderRtnIDX, false);}	
	public static float getNodeInSegThresh() {return nodeInSegDistThresh;}
	public static void setNodeInSegThresh(float _val) {nodeInSegDistThresh=_val;}	
	//getter/setter/convenience funcs to check for whether mt capable, and to return # of usable threads (total host threads minus some reserved for processing)
	public boolean isMTCapable() {return getFlag(isMTCapableIDX);}
	public int getNumUsableThreads() {return numUsableThreads;}
	public ExecutorService getTh_Exec() {return th_exec;}
		
	// use functions to easily access states
	public void setIsDebug(boolean val) {setFlag(debugIDX, val);}
	public boolean getIsDebug() {return getFlag(debugIDX);}
	
	public void setMapDataIsLoaded(boolean val) {setFlag(mapDataLoadedIDX, val);}
	public boolean getMapDataIsLoaded() {return getFlag(mapDataLoadedIDX);}	
	public void setLoaderRTNSuccess(boolean val) {setFlag(loaderRtnIDX, val);}
	public boolean getLoaderRTNSuccess() {return getFlag(loaderRtnIDX);}
	public void setDenseTrainDataSaved(boolean val) {setFlag(denseTrainDataSavedIDX, val);}
	public void setSparseTrainDataSaved(boolean val) {setFlag(sparseTrainDataSavedIDX, val);}
	public void setCSVTestDataSaved(boolean val) {setFlag(testDataSavedIDX, val);}
	
	//# of features used to train SOM
	public int getNumTrainFtrs() {return numTrnFtrs;}
	public void setNumTrainFtrs(int _numTrnFtrs) {numTrnFtrs = _numTrnFtrs;}

	//set all training/testing data save flags to val
	protected void setAllTrainDatSaveFlags(boolean val) {
		setFlag(denseTrainDataSavedIDX, val);
		setFlag(sparseTrainDataSavedIDX, val);
		setFlag(testDataSavedIDX, val);		
	}
	
	//////////////
	// private state flags
	protected abstract int getNumFlags();
	private void initFlags(){int _numFlags = getNumFlags(); stFlags = new int[1 + _numFlags/32]; for(int i = 0; i<_numFlags; ++i){setFlag(i,false);}}
	private void setAllFlags(int[] idxs, boolean val) {for (int idx : idxs) {setFlag(idx, val);}}
	public void setFlag(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case debugIDX : 				{break;}	
			case isMTCapableIDX : {						//whether or not the host architecture can support multiple execution threads
				break;}
			case mapDataLoadedIDX	: 		{break;}		
			case loaderRtnIDX : {break;}
			case denseTrainDataSavedIDX : {
				if (val) {msgObj.dispMessage("SOMMapManager","setFlag","All "+ this.numTrainData +" Dense Training data saved to .lrn file", MsgCodes.info5);}
				break;}				//all prospect examples saved as training data
			case sparseTrainDataSavedIDX : {
				if (val) {msgObj.dispMessage("SOMMapManager","setFlag","All "+ this.numTrainData +" Sparse Training data saved to .svm file", MsgCodes.info5);}
				break;}				//all prospect examples saved as training data
			case testDataSavedIDX : {
				if (val) {msgObj.dispMessage("SOMMapManager","setFlag","All "+ this.numTestData + " saved to " + projConfigData.getSOMMapTestFileName() + " using "+(projConfigData.isUseSparseTestingData() ? "Sparse ": "Dense ") + "data format", MsgCodes.info5);}
				break;		}	
			case trainDataMappedIDX		: {break;}
			case prodDataMappedIDX		: {break;}
			case testDataMappedIDX		: {break;}
			case validateDataMappedIDX		: {break;}
			
			default : { setFlag_Indiv(idx, val);}	//any flags not covered get set here in instancing class			
		}
	}//setFlag		
	public boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		
	protected abstract void setFlag_Indiv(int idx, boolean val);	
	
	public boolean getTrainDataBMUsRdyToSave() {return getFlag(trainDataMappedIDX);}
	public boolean getProdDataBMUsRdyToSave() {return getFlag(prodDataMappedIDX);}
	public boolean getTestDataBMUsRdyToSave() {return (getFlag(testDataMappedIDX) || testData.length==0);}
	public boolean getValidationDataBMUsRdyToSave() {return getFlag(validateDataMappedIDX);}
	//call on load of bmus
	public void setTrainDataBMUsRdyToSave(boolean val) {setFlag(trainDataMappedIDX,val);}
	public void setProdDataBMUsRdyToSave(boolean val) {setFlag(prodDataMappedIDX, val);}

	
	
	//getter/setter/convenience funcs
	public boolean mapCanBeTrained(int kVal) {
		System.out.println("denseTrainDataSavedIDX : " + getFlag(denseTrainDataSavedIDX));
		System.out.println("sparseTrainDataSavedIDX : " + getFlag(sparseTrainDataSavedIDX));
		boolean val = ((kVal <= 1) && (getFlag(denseTrainDataSavedIDX)) || ((kVal == 2) && getFlag(sparseTrainDataSavedIDX)));
		System.out.println("kVal : " + kVal + " | bool val : " + val);
		
		//eventually enable training map on existing files - save all file names, enable file names to be loaded and map built directly
		return ((kVal <= 1) && (getFlag(denseTrainDataSavedIDX)) || ((kVal == 2) && getFlag(sparseTrainDataSavedIDX)));
	}	
	//return true if loader is done and if data is successfully loaded
	public boolean isMapDrawable(){return getFlag(loaderRtnIDX) && getFlag(mapDataLoadedIDX);}
	public boolean isToroidal(){return projConfigData.isToroidal();}	
	//get fill, stroke and text color ID if win exists (to reference papplet) otw returns 0,0,0
	public int[] getClrVal(ExDataType _type) {
		if (win==null) {return new int[] {0,0,0};}															//if null then not going to be displaying anything
		switch(_type) {
			case Training : {		return new int[] {my_procApplet.gui_Cyan,my_procApplet.gui_Cyan,my_procApplet.gui_Blue};}			//corresponds to prospect training example
			case Testing : {		return new int[] {my_procApplet.gui_Magenta,my_procApplet.gui_Magenta,my_procApplet.gui_Red};}		//corresponds to prospect testing/held-out example
			case Validation : { 	return new int[] {my_procApplet.gui_Magenta,my_procApplet.gui_Magenta,my_procApplet.gui_Red};}		//corresponds to true prospect, with no "customer-defining" actions in history
			case Product : {		return new int[] {my_procApplet.gui_Yellow,my_procApplet.gui_Yellow,my_procApplet.gui_White};}		//corresponds to product example
			case MapNode : {		return new int[] {my_procApplet.gui_Green,my_procApplet.gui_Green,my_procApplet.gui_Cyan};}			//corresponds to map node example
			case MouseOver : {		return new int[] {my_procApplet.gui_White,my_procApplet.gui_White,my_procApplet.gui_White};}			//corresponds to mouse example
		}
		return new int[] {my_procApplet.gui_White,my_procApplet.gui_White,my_procApplet.gui_White};
	}//getClrVal
	
	
	//find distance on map
	public myPoint buildScaledLoc(float x, float y){		
		float xLoc = (x + .5f) * (mapDims[0]/mapNodeCols), yLoc = (y + .5f) * (mapDims[1]/mapNodeRows);
		myPoint pt = new myPoint(xLoc, yLoc, 0);
		return pt;
	}
	
	//distance on map	
	public myPointf buildScaledLoc(Tuple<Integer,Integer> mapNodeLoc){		
		float xLoc = (mapNodeLoc.x + .5f) * (mapDims[0]/mapNodeCols), yLoc = (mapNodeLoc.y + .5f) * (mapDims[1]/mapNodeRows);
		myPointf pt = new myPointf(xLoc, yLoc, 0);
		return pt;
	}
	//return upper left corner of umat box x,y and width,height
	public float[] buildUMatBoxCrnr(Tuple<Integer,Integer> mapNodeLoc) {
		float w =  (mapDims[0]/mapNodeCols), h = (mapDims[1]/mapNodeRows);		
		float[] res = new float[] {mapNodeLoc.x * w, mapNodeLoc.y * h, w, h};
		return res;
	}
	//mapNodeCols, mapNodeRows
	public Tuple<Integer,Integer> getMapLocTuple(int xLoc, int yLoc){return new Tuple<Integer,Integer>((xLoc +mapNodeCols)%mapNodeCols, (yLoc+mapNodeRows)%mapNodeRows );}
	//set UI values from loaded map data, if UI is in use
	public void setUIValsFromLoad(SOM_MapDat mapDat) {if (win != null) {		win.setUIValues(mapDat);	}}//setUIValsFromLoad
	
	public void resetButtonState() {if (win != null) {	win.resetButtonState();}}

	public void setMapNumCols(int _x){
		//need to update UI value in win
		mapNodeCols = _x;
		nodeXPerPxl = mapNodeCols/this.mapDims[0];
		projConfigData.updateMapDat_Integer_MapCols(_x, true,true);
//		if (win != null) {			
//			boolean didSet = win.setWinToUIVals(SOMMapUIWin.uiMapColsIDX, mapNodeCols);
//			if(!didSet){msgObj.dispMessage("SOMMapManager","setMapX","Setting ui map x value failed for x = " + _x, MsgCodes.error2);}
//		}
	}//setMapX
	public void setMapNumRows(int _y){
		//need to update UI value in win
		mapNodeRows = _y;
		nodeYPerPxl = mapNodeRows/this.mapDims[1];
		projConfigData.updateMapDat_Integer_MapRows(_y, true,true);
//		if (win != null) {			
//			boolean didSet = win.setWinToUIVals(SOMMapUIWin.uiMapRowsIDX, mapNodeRows);
//			if(!didSet){msgObj.dispMessage("SOMMapManager","setMapY","Setting ui map y value failed for y = " + _y, MsgCodes.error2);}
//		}
	}//setMapY

	public String toString(){
		String res = "Map Manager : ";
		res += "PApplet is :"+(pa==null ? "null " : "present and non-null " ) +  " | UI Window class is : "+(win==null ? "null " : "present and non-null " );

		
		
		return res;	
	}	
	
}//abstract class SOMMapManager



//manage a message stream from a launched external process - used to manage output from som training process
class ProcConsoleMsgMgr implements Callable<Boolean> {
	SOMMapManager mapMgr;
	final Process process;
	BufferedReader rdr;
	StringBuilder strbld;
	String type;
	MsgCodes msgType;//for display of output
	int iter = 0;
	public ProcConsoleMsgMgr(SOMMapManager _mapMgr, final Process _process, Reader _in, String _type) {
		mapMgr = _mapMgr;
		process=_process;
		rdr = new BufferedReader(_in); 
		strbld = new StringBuilder();
		type=_type;
		msgType = (type.equals("Input")) ? MsgCodes.info3 : MsgCodes.error4;
	}//ctor	
	//SOM outputs info about time to train each epoch in stderr instead of stdout despite it not being an error, so we don't want to display these messages as being errors
	private String getStreamType(String rawStr) {	return (rawStr.toLowerCase().contains("time for epoch") ? "Input" : type);}
	//access owning map manager's message display function if it exists, otherwise just print to console
	private void  dispMessage(String str, MsgCodes useCode) {
		if(mapMgr != null) {
			String typStr = getStreamType(str);			
			mapMgr.getMsgObj().dispMessage("messageMgr","call ("+typStr+" Stream Handler)", str, useCode);}
		else {				System.out.println(str);	}
	}//msgObj.dispMessage
	
	public String getResults() {	return strbld.toString();	}
	@Override
	public Boolean call() throws Exception {
		String sIn = null;
		try {
			while ((sIn = rdr.readLine()) != null) {
				String typStr = getStreamType(sIn);		
				dispMessage("Stream " + typStr+" Line : " + String.format("%04d",iter++) + " | Msg : " + sIn, msgType);
				strbld.append(sIn);			
				strbld.append(System.getProperty("line.separator"));				
			}
		} catch (IOException e) { 
			e.printStackTrace();
			dispMessage("Process IO failed with exception : " + e.toString() + "\n\t"+ e.getMessage(), MsgCodes.error1);
		}
		return true;
	}//call
	
}//messageMgr

