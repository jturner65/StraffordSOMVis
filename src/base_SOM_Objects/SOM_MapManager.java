package base_SOM_Objects;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;

import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_fileIO.*;
import base_SOM_Objects.som_segments.segments.SOM_MappedSegment;
import base_SOM_Objects.som_segments.segments.SOM_CategorySegment;
import base_SOM_Objects.som_segments.segments.SOM_ClassSegment;
import base_SOM_Objects.som_segments.segments.SOM_FtrWtSegment;
import base_SOM_Objects.som_segments.segments.SOM_UMatrixSegment;
import base_SOM_Objects.som_ui.*;
import base_SOM_Objects.som_utils.*;
import base_SOM_Objects.som_utils.runners.SOM_CalcExFtrs_Runner;
import base_SOM_Objects.som_utils.runners.SOM_MapExDataToBMUs_Runner;
import base_SOM_Objects.som_utils.runners.SOM_SaveExToBMUs_Runner;
import base_UI_Objects.*;
import base_Utils_Objects.*;
import base_Utils_Objects.io.FileIOManager;
import base_Utils_Objects.io.MessageObject;
import base_Utils_Objects.io.MsgCodes;
import base_Utils_Objects.vectorObjs.Tuple;
import base_Utils_Objects.vectorObjs.myPoint;
import base_Utils_Objects.vectorObjs.myPointf;

public abstract class SOM_MapManager {
	//applet, if used in graphical context
	private my_procApplet pa;				
	//owning window
	public SOM_MapUIWin win;		
	//manage IO in this object
	protected FileIOManager fileIO; 
	//struct maintaining complete project configuration and information from config files - all file name data and building needs to be done by this object
	public SOMProjConfigData projConfigData;	
	//object to manage messages for display and potentially logging
	private MessageObject msgObj;
	//object to manage interface with a UI, to make sure map data stays synchronized
	public SOM_UIToMapCom mapUIAPI;
	
	//////////////////////////////
	//map descriptors	
	//all nodes of som map, keyed by node location as tuple of row/col coordinates
	protected TreeMap<Tuple<Integer,Integer>, SOM_MapNode> MapNodes;	
	//map of ftr idx and all map nodes that have non-zero presence in that ftr
	protected TreeMap<Integer, HashSet<SOM_MapNode>> MapNodesByFtrIDX;
	//map nodes that have/don't have  examples of specified type
	private ConcurrentSkipListMap<SOM_ExDataType, HashSet<SOM_MapNode>> nodesWithEx, nodesWithNoEx;	
	//array of per ftr idx treemaps of map nodes keyed by ftr weight
	private TreeMap<Float,ArrayList<SOM_MapNode>>[] PerFtrHiWtMapNodes;	
	
	//array of map clusters based in UMatrix Distance
	protected ArrayList<SOM_MappedSegment> UMatrix_Segments;
	//array of map clusters based on ftr distance, keyed by Ftr IDX
	protected TreeMap<Integer, SOM_MappedSegment> FtrWt_Segments;
	
	//////////////////
	// class and category mapping
	// classes belong to training data - they are assigned to map nodes that are bmus for a particular training example
	// they are then used to define the nature of segments/clusters on the map, as well as give a probability of class 
	// membership to an unclassified sample that maps to a praticular BMU
	// categories are collections of similar classes
	//Map of classes to segment
	protected TreeMap<Integer, SOM_MappedSegment> Class_Segments;
	//map with key being class and with value being collection of map nodes with that class present in mapped examples
	protected TreeMap<Integer,Collection<SOM_MapNode>> MapNodesWithMappedClasses;
	//probabilities for each class for each map node
	protected ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>> MapNodeClassProbs;

	//map of categories to segment
	protected TreeMap<Integer, SOM_MappedSegment> Category_Segments;
	//map with key being category and with value being collection of map nodes with that category present in mapped examples
	protected TreeMap<Integer,Collection<SOM_MapNode>> MapNodesWithMappedCategories;
	//probabilities for each category for each map node
	protected ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>> MapNodeCategoryProbs;
	
	//data values directly from the trained map, populated upon load
	private float[] 
			map_ftrsMean, 				
			map_ftrsVar, 
			map_ftrsDiffs, 
			map_ftrsMin;				//per feature mean, variance, difference, mins, in -map features- data
	
	////////////////////////////////////////////////////////////////////////////////////////////////
	//data descriptions
	//this map of mappers will manage the different kinds of raw data.  the instancing class should specify the keys and instancing members for this map
	protected ConcurrentSkipListMap<String, SOM_ExampleManager> exampleDataMappers;	
	
	//full input data, data set to be training data and testing data (all of these examples 
	//are potential -training- data, in that they have all features required of training data)
	//testing data will be otherwise valid training data that will be matched against map - having these 
	//is not going to be necessary for most cases since this is unsupervised, but can be used to measure consistency
	protected SOM_Example[] inputData;
	protected SOM_Example[] trainData;
	protected SOM_Example[] testData;	
	//validationData are example records failing to meet the training criteria or otherwise desired to be mapped against SOM
	//these were not used to train the map	
	protected SOM_Example[] validationData;		
	//sizes of above data arrays
	public int numInputData, numTrainData, numTestData, numValidationData;
	
	//values to return scaled values to actual data points - multiply wts by diffsVals, add minsVals
	//idx 0 is feature diffs/mins per (ftr idx); idx 1 is across all ftrs
	private Float[][] diffsVals, minsVals;	
	//# of training features 
	private int numTrnFtrs;
	
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
	protected int mapNodeCols = 0, mapNodeRows = 0;
	//# of nodes / map dim  in x/y
	protected float nodeXPerPxl, nodeYPerPxl;
	//threshold of u-dist for nodes to belong to same segment
	protected static float nodeInSegUMatrixDistThresh = .3f;
	protected float mapMadeWithUMatrixSegThresh = 0.0f;
	//threshold for distance for ftr weight segment construction
	protected static float nodeInSegFtrWtDistThresh = .01f;
	protected float mapMadeWithFtrWtSegThresh = 0.0f;
	
	private int[] stFlags;						//state flags - bits in array holding relevant process info
	public static final int
			debugIDX 					= 0,
			isMTCapableIDX				= 1,
			SOMmapNodeDataLoadedIDX		= 2,			//som map data is cleanly loaded
			loaderFinishedRtnIDX		= 3,			//dataloader has finished - wait on this to draw map
			denseTrainDataSavedIDX 		= 4,			//all current prospect data has been saved as a training data file for SOM (.lrn format) 
			sparseTrainDataSavedIDX		= 5,			//sparse data format using .svm file descriptions (basically a map with a key:value pair of ftr index : ftr value
			testDataSavedIDX			= 6,			//save test data in sparse format csv
		//data types mapped flags - ready to save results
			trainDataMappedIDX			= 7,
			prodDataMappedIDX			= 8,
			testDataMappedIDX			= 9,
			validateDataMappedIDX		= 10,
			dispMseDataSideBarIDX		= 11,			//whether to display mouse data on side bar
			dispLdPreBuitMapsIDX		= 12;
		
	public static final int numBaseFlags = 13;	
	//numFlags is set by instancing map manager getMseOvrLblArray()
	
	//threading constructions - allow map manager to own its own threading executor
	protected ExecutorService th_exec;	//to access multithreading - instance from calling program
	protected final int numUsableThreads;		//# of threads usable by the application

	protected SOM_MseOvrDisplay mseOverExample;
	
	// String[] _dirs : idx 0 is config directory, as specified by cmd line; idx 1 is data directory, as specified by cmd line
	// String[] _args : command line arguments other than directory info
	public SOM_MapManager(SOM_MapUIWin _win, float[] _dims, TreeMap<String, Object> _argsMap) {
		pa=null;//assigned by win if it exists
		win=_win;			
		mapDims = _dims;
		mapUIAPI = buildSOM_UI_Interface();
		initFlags();		
		//message object manages displaying to screen and potentially to log files - needs to be built first
		setMsgObj(MessageObject.buildMe(pa));
		
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
		//default to have mouse location display on side of screen
		setFlag(dispMseDataSideBarIDX, true);
		//default to have # of prebuilt map directories loaded display on side of screen
		setFlag(dispLdPreBuitMapsIDX, true);
		//th_exec = Executors.newCachedThreadPool();// Executors.newFixedThreadPool(numUsableThreads);
		if(getFlag(isMTCapableIDX)) {
			//th_exec = Executors.newFixedThreadPool(numUsableThreads+1);//fixed is better in that it will not block on the draw - this seems really slow on the prospect mapping
			th_exec = Executors.newCachedThreadPool();// this is performing much better even though it is using all available threads
		} else {//setting this just so that it doesn't fail somewhere - won't actually be exec'ed
			th_exec = Executors.newCachedThreadPool();// Executors.newFixedThreadPool(numUsableThreads);
		}
		
		//data mappers - eventually will replace all example maps, and will derive all training data arrays
		exampleDataMappers = new ConcurrentSkipListMap<String, SOM_ExampleManager>();
		//build mappers that will manage data read from disk in order to calculate features and build data arrays used by SOM
		buildExampleDataMappers();
		
		resetTrainDataAras();
		mseOverExample = buildMseOverExample();
	}//ctor
	
	/**
	 * build the map of example mappers used to manage all the data the SOM will consume
	 */
	protected abstract void buildExampleDataMappers();

	/**
	 * build instance-specific project file configuration - necessary if using project-specific config file
	 */	
	protected abstract SOMProjConfigData buildProjConfigData(TreeMap<String, Object> _argsMap);
	
	/**
	 * build mouse-over example
	 */
	protected abstract SOM_MseOvrDisplay buildMseOverExample();
	
	/**
	 * build an interface to manage communications between UI and SOM map dat
	 * This interface will need to include a reference to an application-specific UI window
	 */
	protected abstract SOM_UIToMapCom buildSOM_UI_Interface();
	
	public static String[] getNodeBMUMapTypes() {
		String[] typeList = SOM_ExDataType.getListOfTypes();
		if (nodeBMUMapTypes==null) {
			nodeBMUMapTypes = new String[typeList.length-2];
			for(int i=0;i<nodeBMUMapTypes.length;++i) {	nodeBMUMapTypes[i]=typeList[i];	}			
		}
		return nodeBMUMapTypes;
	}
	
	//use this to set window/UI components, if exist
	public void setPADispWinData(SOM_MapUIWin _win, my_procApplet _pa) {
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
	
	public void resetTrainDataAras() {
		msgObj.dispMessage("SOMMapManager","resetTrainDataAras","Init Called to reset all train and test data.", MsgCodes.info5);
		inputData = new SOM_Example[0];
		testData = new SOM_Example[0];
		trainData = new SOM_Example[0];
		numInputData=0;
		numTrainData=0;
		numTestData=0;		
		nodesWithEx = new ConcurrentSkipListMap<SOM_ExDataType, HashSet<SOM_MapNode>>();
		nodesWithNoEx = new ConcurrentSkipListMap<SOM_ExDataType, HashSet<SOM_MapNode>>();
		for (SOM_ExDataType _type : SOM_ExDataType.values()) {
			nodesWithEx.put(_type, new HashSet<SOM_MapNode>());
			nodesWithNoEx.put(_type, new HashSet<SOM_MapNode>());		
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
	public String getDataDescFromInt(int dataFrmt) {
		switch(dataFrmt) {
		case useUnmoddedDat : {return "Unmodified";}
		case useScaledDat : {return "Standardized (across all examples per feature)";}
		case useNormedDat : {return "Normalized (across all features per example)";}
		default : {return null;}		//unknown data frmt type
		}
	}//getDataTypeNameFromInt
	
	public String getDataDescFromInt_Short(int dataFrmt) {
		switch(dataFrmt) {
		case useUnmoddedDat : {return "Unmodified";}
		case useScaledDat : {return "Standardized";}
		case useNormedDat : {return "Normalized";}
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
	//load raw data and preprocess, partitioning into different data types as appropriate
	public abstract void loadAndPreProcAllRawData(boolean fromCSVFiles);

	//execute post-feature vector build code in multiple threads if supported
	public void _ftrVecBuild(Collection<SOM_Example> exs, int _typeOfProc, String exType, boolean forceST) {
		getMsgObj().dispMessage("SOMMapManager","_ftrVecBuild : " + exType + " Examples","Begin "+exs.size()+" example processing.", MsgCodes.info1);
		boolean canMultiThread=isMTCapable() && !forceST;//if false this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		//if((canMultiThread) && (exs.size()>0)) {//MapExFtrCalcs_Runner.rawNumPerPartition*10)){
		if((canMultiThread) && (exs.size()>SOM_CalcExFtrs_Runner.rawNumPerPartition*10)){//force all to be single threaded - something is not working with multi-threaded customer ftr calc
			//MapExFtrCalcs_Runner(SOMMapManager _mapMgr, ExecutorService _th_exec, SOMExample[] _exData, String _dataTypName, ExDataType _dataType, int _typeOfProc)
			//shuffling examples to attempt to spread out calculations more evenly - the examples that require the alt comp vector calc are expensive to calculate
			//should not be multithread until concurrency issue pertaining to ftr calc can be determined
			SOM_CalcExFtrs_Runner calcRunner = new SOM_CalcExFtrs_Runner(this, th_exec, shuffleTrainingData(exs.toArray(new SOM_Example[0]),12345L) , exType, _typeOfProc, false);
			calcRunner.runMe();
		} else {//called after all features of this kind of object are built - this calculates alternate compare object
			int curIDX = 0, ttlNum = exs.size(), modAmt = ttlNum/10;
			if(_typeOfProc==0) {
				getMsgObj().dispMessage("SOMMapManager","_ftrVecBuild : " + exType + " Examples","Begin build "+exs.size()+" feature vector.", MsgCodes.info1);
				for (SOM_Example ex : exs) {			ex.buildFeatureVector();	++curIDX; if(curIDX % modAmt == 0) {_dbg_ftrVecBuild_dispProgress(curIDX, ttlNum,exType,"build "+ttlNum+" feature vector.");}}
				getMsgObj().dispMessage("SOMMapManager","_ftrVecBuild : " + exType + " Examples","Finished build "+exs.size()+" feature vector.", MsgCodes.info1);
			} else if(_typeOfProc==1) {
				getMsgObj().dispMessage("SOMMapManager","_ftrVecBuild : " + exType + " Examples","Begin "+exs.size()+" After Feature Vector Build (Per example finalizing).", MsgCodes.info1);
				for (SOM_Example ex : exs) {			ex.postFtrVecBuild();	++curIDX; if(curIDX % modAmt == 0) {_dbg_ftrVecBuild_dispProgress(curIDX, ttlNum,exType,"build "+ttlNum+" After Feature Vector Build (Per example finalizing).");}}		
				getMsgObj().dispMessage("SOMMapManager","_ftrVecBuild : " + exType + " Examples","Finished "+exs.size()+" After Feature Vector Build (Per example finalizing).", MsgCodes.info1);
			} else if(_typeOfProc==2) {
				getMsgObj().dispMessage("SOMMapManager","_ftrVecBuild : " + exType + " Examples","Begin "+exs.size()+" Post Feature Vector Structures (STD Vecs) Build.", MsgCodes.info1);
				for (SOM_Example ex : exs) {			ex.buildAfterAllFtrVecsBuiltStructs();	++curIDX; if(curIDX % modAmt == 0) {_dbg_ftrVecBuild_dispProgress(curIDX, ttlNum,exType,"build "+ttlNum+" Post Feature Vector Structures (STD Vecs and possibly alternate comparison ftrs).");}}		
				getMsgObj().dispMessage("SOMMapManager","_ftrVecBuild : " + exType + " Examples","Finished "+exs.size()+" Post Feature Vector Structures (STD Vecs) Build.", MsgCodes.info1);
			}
		}
		getMsgObj().dispMessage("SOMMapManager","_ftrVecBuild : " + exType + " Examples","Finished "+exs.size()+" example processing.", MsgCodes.info1);
	}//_postFtrVecBuild
	
	private void _dbg_ftrVecBuild_dispProgress(int curVal, int ttlNum, String exType, String proc) {
		getMsgObj().dispMessage("SOMMapManager","_ftrVecBuild : " + exType + " Examples"," Finished :  " +Math.round((100.0f*curVal)/ttlNum) + "% : " + curVal+ " of " + ttlNum +" for process : " + proc,MsgCodes.info1);		
	}
	
//	//execute post-feature vector build code in multiple threads if supported
//	public void _ftrVecBuild(Collection<SOMExample> exs, int _typeOfProc, String exType) {
//		getMsgObj().dispMessage("SOMMapManager","_ftrVecBuild : " + exType + " Examples","Begin "+exs.size()+" example processing.", MsgCodes.info1);
//		//shuffling examples to attempt to spread out calculations more evenly - the examples that require the alt comp vector calc are expensive to calculate
//		//runner handles whether ST or MT - force to single thread
//		MapExFtrCalcs_Runner calcRunner = new MapExFtrCalcs_Runner(this, th_exec, shuffleTrainingData(exs.toArray(new SOMExample[0]),12345L) , exType, _typeOfProc, true);
//		calcRunner.runMe();
//		getMsgObj().dispMessage("SOMMapManager","_ftrVecBuild : " + exType + " Examples","Finished "+exs.size()+" example processing.", MsgCodes.info1);
//	}//_postFtrVecBuild
	
	//this function will build the input data used by the SOM - this will be partitioned by some amount into test and train data (usually will use 100% train data, but may wish to test label mapping)
	protected abstract SOM_Example[] buildSOM_InputData();
	//set input data and shuffle it; partition test and train arrays 
	protected void setInputTrainTestShuffleDataAras(float trainTestPartition) {		
		msgObj.dispMessage("SOMMapManager","setInputTestTrainDataArasShuffle","Shuffling Input, Building Training and Testing Partitions.", MsgCodes.info5);
		//set partition size in project config
		projConfigData.setTrainTestPartition(trainTestPartition);
		//build input data appropriately for project
		inputData = buildSOM_InputData();		
		//shuffleProspects(ProspectExample[] _list, long seed) -- performed in place - use same key so is reproducible training, always has same shuffled order
		inputData = shuffleTrainingData(inputData, 12345L);
		numTrainData = (int) (inputData.length * trainTestPartition);			
		numTestData = inputData.length - numTrainData;		
		//build train and test partitions
		trainData = new SOM_Example[numTrainData];	
		msgObj.dispMessage("SOMMapManager","setInputTestTrainDataArasShuffle","# of training examples : " + numTrainData + " inputData size : " + inputData.length, MsgCodes.info3);
		for (int i=0;i<trainData.length;++i) {trainData[i]=inputData[i];trainData[i].setIsTrainingDataIDX(true, i);}
		testData = new SOM_Example[numTestData];
		for (int i=0;i<testData.length;++i) {testData[i]=inputData[i+numTrainData];testData[i].setIsTrainingDataIDX(false, i+numTrainData);}		

		msgObj.dispMessage("SOMMapManager","setInputTestTrainDataArasShuffle","Finished Shuffling Input, Building Training and Testing Partitions. Train size : " + numTrainData+ " Testing size : " + numTestData+".", MsgCodes.info5);
	}//setInputTestTrainDataArasShuffle
	
	//build file names, including info for data type used to train map
	protected void initNewSOMDirsAndSaveData() {
		msgObj.dispMessage("SOMMapManager","initNewSOMDirsAndSaveData","Begin building new directories, saving Train, Test data and data Mins and Diffs. Train size : " + numTrainData+ " Testing size : " + numTestData+".", MsgCodes.info5);	
		//build directories for this experiment
		projConfigData.buildDateTimeStrAraAndDType(getDataTypeNameFromCurFtrTrainType());
		//save partitioned data in built directories
		projConfigData.launchTestTrainSaveThrds(th_exec, curMapTrainFtrType, numTrnFtrs,trainData,testData);				//save testing and training data	
		//save mins and diffs of current training data
		saveMinsAndDiffs();		
		msgObj.dispMessage("SOMMapManager","initNewSOMDirsAndSaveData","Finished building new directories, saving Train, Test data and data Mins and Diffs. Train size : " + numTrainData+ " Testing size : " + numTestData+".", MsgCodes.info5);		
	}

	protected abstract void loadPreProcTrainData(String subDir, boolean forceLoad);
	//build the testing and training data partitions and save them to files
	protected abstract void buildTrainTestFromPartition(float trainTestPartition);
	
	//load preproc customer csv and build training and testing partitions - testing partition not necessary 
	public void loadPreprocAndBuildTestTrainPartitions(float trainTestPartition, boolean forceLoad) {
		msgObj.dispMessage("SOMMapManager","loadPreprocAndBuildTestTrainPartitions","Start Loading all CSV example Data to train map.", MsgCodes.info5);
		loadPreProcTrainData(projConfigData.getPreProcDataDesiredSubDirName(),forceLoad);
		//build test/train data partitions
		buildTrainTestFromPartition(trainTestPartition);	
		msgObj.dispMessage("SOMMapManager","loadPreprocAndBuildTestTrainPartitions","Finished Loading all CSV example Data to train map.", MsgCodes.info5);
	}//loadPreprocAndBuildTestTrainPartitions
	
	//build new SOM_MAP map using UI-entered values, then load resultant data
	//with maps of required SOM exe params
	//TODO this will be changed to not pass values from UI, but rather to finalize and save values already set in SOM_MapDat object from UI or other user input
	public void updateAllMapArgsFromUI(HashMap<String, Integer> mapInts, HashMap<String, Float> mapFloats, HashMap<String, String> mapStrings){
		//set and save configurations
		projConfigData.setSOM_MapArgs(mapInts, mapFloats, mapStrings);
	}
	
	//this will load the map into memory, bmus, umatrix, etc - this is necessary to consume map
	protected void loadMapAndBMUs() {
		msgObj.dispMessage("SOMMapManager","loadMapAndBMUs_Synch","Building Mappings synchronously.", MsgCodes.info1);
		SOM_DataLoader ldr = new SOM_DataLoader(this,projConfigData);//can be run in separate thread, but isn't here
		boolean success = ldr.callMe();	
		msgObj.dispMessage("SOMMapManager","loadMapAndBMUs_Synch","Finished data loader : SOM Data Loaded successfully : " + success, MsgCodes.info5 );		
	}//loadMapAndBMUs_Synch
	
	/**
	 * Load a prebuilt map - this is called only from UI version
	 * Load preprocessed training and product data and process it (assumed to be data used to build map, if not, then map will be corrupted)
	 * Load SOM data; partition pretrained data; map loaded training data and products to map nodes
	 */
	public void loadPretrainedExistingMap(int mapID, boolean forceReLoad) {
		//load preproc data used to train map - it is assumed this data is in default directory
		msgObj.dispMessage("SOMMapManager","loadPretrainedExistingMap","First load pretrained map # " + mapID + " using directory specified in project config file.", MsgCodes.info1);
		//set default map id
		projConfigData.setSOM_DefaultPreBuiltMap(mapID);
		//for prebuilt map - load config used in prebuilt map
		boolean dfltmapLoaded = projConfigData.setSOM_UsePreBuilt();	
		if(!dfltmapLoaded) {
			msgObj.dispMessage("SOMMapManager","loadPretrainedExistingMap","No Default map loaded, probably due to no default map directories specified in config file.  Aborting ", MsgCodes.info1);
			return;
		}
		msgObj.dispMessage("SOMMapManager","loadPretrainedExistingMap","Next load training data used to build map - it is assumed this data is in default preproc directory.", MsgCodes.info1);
		//load customer data into preproc  -this must be data used to build map and build data partitions - use partition size set via constants in debug
		loadPreprocAndBuildTestTrainPartitions(projConfigData.getTrainTestPartition(),forceReLoad);
		
		msgObj.dispMultiLineInfoMessage("SOMMapManager","loadPretrainedExistingMap","Now map all training data to loaded map.");
		//don't execute in a thread, execute synchronously so we can use results immediately upon return
		loadMapAndBMUs();
		msgObj.dispMessage("SOMMapManager","loadPretrainedExistingMap","Data loader finished loading map nodes and matching training data and products to BMUs." , MsgCodes.info3);
	}//loadPretrainedExistingMap
	
	/**
	 * Use this method to map all prospects(cust and true) and products to existing map, and save mappings
	 * 1) load training data and products for map
	 * 2) load map data and derive map node bmus for prospects and products, building jp and jpg segments
	 * 3) load true prospects and map them to map via euclidean dists to map nodes to find their bmus
	 * 4) save all mappings 
	 */
	public abstract void loadAllDataAndBuildMappings();

	protected abstract void loadMapProcAllData_Indiv(Double prodZoneDistThresh);
		
	//this will load the default map training configuration
	public void loadSOMConfig() {	projConfigData.loadDefaultSOMExp_Config();	}//loadSOMConfig
	
	/**
	 * use UI values for map and train map (call only from UI)
	 */
	public boolean loadTrainDataMapConfigAndBuildMap_UI(boolean mapNodesToData, HashMap<String, Integer> mapInts,HashMap<String, Float> mapFloats,HashMap<String, String> mapStrings) {
		msgObj.dispMessage("SOMMapManager","loadTrainDataMapConfigAndBuildMap_UI","Start Loading training data and building map. Mapping examples to SOM Nodes : "+mapNodesToData, MsgCodes.info1);
		//load all training/prospect data and build test and training data partitions
		loadPreprocAndBuildTestTrainPartitions(projConfigData.getTrainTestPartition(), false);
		//build experimental directories, save training, testing and diffs/mins data to directories - only should be called when building a new map
		initNewSOMDirsAndSaveData();	
		
		msgObj.dispMessage("SOMMapManager","loadTrainDataMapConfigAndBuildMap_UI","Finished Loading training data and setting directories.", MsgCodes.info1);
		//update map with current UI values - must be done after initNewSOMDirsAndSaveData to pass appropriately updated SOM values
		updateAllMapArgsFromUI(mapInts, mapFloats, mapStrings);		
		boolean res = _ExecSOM(mapNodesToData);
		msgObj.dispMessage("SOMMapManager","loadTrainDataMapConfigAndBuildMap_UI","Finished Loading training data and building map. Success : " + res+" | Mapped examples to SOM Nodes :"+mapNodesToData, MsgCodes.info1);
		return res;
	}//loadTrainDataMapConfigAndBuildMap_UI
	/**
	 * train map with currently set SOM control values - call from console/cmd line application
	 */
	public boolean loadTrainDataMapConfigAndBuildMap(boolean mapNodesToData) {	
		msgObj.dispMessage("SOMMapManager","loadTrainDataMapConfigAndBuildMap","Start Loading training data and building map. Mapping examples to SOM Nodes : "+mapNodesToData, MsgCodes.info1);
		//load all training/prospect data and build test and training data partitions
		loadPreprocAndBuildTestTrainPartitions(projConfigData.getTrainTestPartition(), false);
		//build experimental directories, save training, testing and diffs/mins data to directories - only should be called when building a new map
		initNewSOMDirsAndSaveData();		
		//reload currently set default config for SOM - IGNORES VALUES SET IN UI
		msgObj.dispMessage("SOMMapManager","loadTrainDataMapConfigAndBuildMap","Finished Loading training data and setting directories.", MsgCodes.info1);
		boolean res = _ExecSOM(mapNodesToData);
		msgObj.dispMessage("SOMMapManager","loadTrainDataMapConfigAndBuildMap","Finished Loading training data and building map. Success : "  + res+ " | Mapped examples to SOM Nodes :"+mapNodesToData, MsgCodes.info1);
		return res;
	}//loadTrainDataMapConfigAndBuildMap
	
	/**
	 * execute some training and map data to to BMUs if specified
	 * @param mapNodesToData
	 * @return
	 */
	private boolean _ExecSOM(boolean mapNodesToData) {
		msgObj.dispMessage("SOMMapManager","_ExecSOM","Start building map.", MsgCodes.info1);
		//execute map training
		SOM_MapDat SOMExeDat = projConfigData.getSOMExeDat();
		//set currently defined directories and values in SOM manager
		SOMExeDat.updateMapDescriptorState();
		msgObj.dispMultiLineMessage("SOMMapManager","runSOMExperiment","SOM map descriptor : \n" + SOMExeDat.toString() + "SOMOutExpSffx str : " +projConfigData.getSOMOutExpSffx(), MsgCodes.info5);		
		//save configuration data
		projConfigData.saveSOM_Exp();
		//save start time
		String startTime = msgObj.getCurrWallTimeAndTimeFromStart();
		//launch in a thread? - needs to finish to be able to proceed, so not necessary
		boolean runSuccess = buildNewMap(SOMExeDat);		
		if(runSuccess) {				
			//build values for human-readable report
			TreeMap<String, String> externalVals = getSOMExecInfo();
			externalVals.put("SOM Training Begin Wall Time and Time elapsed from Start of program execution", startTime);
			externalVals.put("SOM Training Finished Wall Time and Time elapsed from Start of program execution", msgObj.getCurrWallTimeAndTimeFromStart());
			//save report of results of execution in human-readable report format		
			projConfigData.saveSOM_ExecReport(externalVals);
			//set flags before calling loader
			setLoaderRTN(false);
			//map training data to map nodes
			if(mapNodesToData) {			loadMapAndBMUs();		}
		}		
		msgObj.dispMessage("SOMMapManager","_ExecSOM","Finished building map.", MsgCodes.info1);
		return runSuccess;
	}//_ExecSOM	
	
	/**
	 * Build map from data by aggregating all training data, building SOM exec string from UI input, and calling OS cmd to run SOM_MAP
	 * @param mapExeDat SOM descriptor
	 * @return whether training succeeded or not
	 */
	private boolean buildNewMap(SOM_MapDat mapExeDat){
		boolean success = true;
		//try {					//success = _launchTrainNewMapProcess(mapExeDat);			
		msgObj.dispMessage("SOMMapManager","buildNewMap","buildNewMap Starting", MsgCodes.info5);
		msgObj.dispMultiLineMessage("SOMMapManager","buildNewMap","Execution String for running manually : \n"+mapExeDat.getDbgExecStr(), MsgCodes.warning2);
		String[] cmdExecStr = mapExeDat.getExecStrAra();

		msgObj.dispMessage("SOMMapManager","buildNewMap","Execution Arguments passed to SOM, parsed by flags and values: ", MsgCodes.info2);
		msgObj.dispMessageAra(cmdExecStr,"SOMMapManager","buildNewMap",2, MsgCodes.info2);//2 strings per line, display execution command	

		String wkDirStr = mapExeDat.getExeWorkingDir(), 
				cmdStr = mapExeDat.getExename(),
				argsStr = "";
		String[] execStr = new String[cmdExecStr.length +1];
		execStr[0] = wkDirStr + cmdStr;
		//for(int i =2; i<cmdExecStr.length;++i){execStr[i-1] = cmdExecStr[i]; argsStr +=cmdExecStr[i]+" | ";}
		for(int i = 0; i<cmdExecStr.length;++i){execStr[i+1] = cmdExecStr[i]; argsStr +=cmdExecStr[i]+" | ";}
		msgObj.dispMultiLineMessage("SOMMapManager","buildNewMap","\nwkDir : "+ wkDirStr + "\ncmdStr : " + cmdStr + "\nargs : "+argsStr, MsgCodes.info1);
		
		//monitor in multiple threads, either msgs or errors
		List<Future<Boolean>> procMsgMgrsFtrs = new ArrayList<Future<Boolean>>();
		List<ProcConsoleMsgMgr> procMsgMgrs = new ArrayList<ProcConsoleMsgMgr>(); 
		//http://stackoverflow.com/questions/10723346/why-should-avoid-using-runtime-exec-in-java		
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
		} 
		catch (IOException e) {				msgObj.dispMessage("SOMMapManager","buildNewMap","buildNewMap Process failed with IOException : \n" + e.toString() + "\n\t"+ e.getMessage(), MsgCodes.error1);success = false;} 
		catch (InterruptedException e) {	msgObj.dispMessage("SOMMapManager","buildNewMap","buildNewMap Process failed with InterruptedException : \n" + e.toString() + "\n\t"+ e.getMessage(), MsgCodes.error1);success = false;}
		catch (ExecutionException e) {    	msgObj.dispMessage("SOMMapManager","buildNewMap","buildNewMap Process failed with ExecutionException : \n" + e.toString() + "\n\t"+ e.getMessage(), MsgCodes.error1);success = false;}		
		
		msgObj.dispMessage("SOMMapManager","buildNewMap","buildNewMap Finished", MsgCodes.info5);			
		//} catch (IOException e){	msgObj.dispMessage("SOMMapManager","buildNewMap","Error running map defined by : " + mapExeDat.toString() + " :\n " + e.getMessage(), MsgCodes.error1);	return false;}		
		return success;
	}//buildNewMap
	
	
	//////////////////////////////////
	// segments	
	protected abstract int _getNumSecondaryMaps();
	//only appropriate if using UI
	public void setSaveLocClrImg(boolean val) {if (win != null) { win.setPrivFlags(SOM_MapUIWin.saveLocClrImgIDX,val);}}
	//whether or not distances between two datapoints assume that absent features in smaller-length datapoints are 0, or to ignore the values in the larger datapoints
	public abstract void setMapExclZeroFtrs(boolean val);

	//take existing map and use U-Matrix-distances to determine segment membership.Large distances > thresh (around .7) mean nodes are on a boundary
	public final void buildUMatrixSegmentsOnMap() {//need to find closest
		if (nodeInSegUMatrixDistThresh == mapMadeWithUMatrixSegThresh) {return;}
		if ((MapNodes == null) || (MapNodes.size() == 0)) {return;}
		msgObj.dispMessage("SOMMapManager","buildSegmentsOnMap","Started building UMatrix Distance-based cluster map", MsgCodes.info5);	
		//clear existing segments 
		for (SOM_MapNode ex : MapNodes.values()) {ex.clearUMatrixSeg();}
		UMatrix_Segments.clear();
		SOM_UMatrixSegment seg;
		for (SOM_MapNode ex : MapNodes.values()) {
			seg = new SOM_UMatrixSegment(this, nodeInSegUMatrixDistThresh);
			if(seg.doesMapNodeBelongInSeg(ex)) {
				seg.addMapNodeToSegment(ex, MapNodes);		//this does dfs
				UMatrix_Segments.add(seg);				
			}
		}		
		mapMadeWithUMatrixSegThresh = nodeInSegUMatrixDistThresh;
		if(win!=null) {win.setMapSegmentImgClrs_UMatrix();}
		msgObj.dispMessage("SOMMapManager","buildSegmentsOnMap","Finished building UMatrix Distance-based cluster map", MsgCodes.info5);			
	}//buildUMatrixSegmentsOnMap()
	
	//build feature-based segments on map - will overlap
	public final void buildFtrWtSegmentsOnMap() {
		if (nodeInSegFtrWtDistThresh == mapMadeWithFtrWtSegThresh) {return;}		
		if ((MapNodes == null) || (MapNodes.size() == 0)) {return;}
		msgObj.dispMessage("SOMMapManager","buildFtrWtSegmentsOnMap","Started building feature-weight-based cluster map", MsgCodes.info5);	
		//clear existing segments 
		for (SOM_MapNode ex : MapNodes.values()) {ex.clearFtrWtSeg();}
		//for every feature IDX, for every map node
		SOM_FtrWtSegment ftrSeg;
		FtrWt_Segments.clear();
		for(int ftrIdx = 0; ftrIdx<PerFtrHiWtMapNodes.length;++ftrIdx) {
			//build 1 segment per ftr idx
			ftrSeg = new SOM_FtrWtSegment(this, ftrIdx);
			//FtrWtSegments.add(ftrSeg);
			FtrWt_Segments.put(ftrIdx, ftrSeg);
			for(SOM_MapNode ex : MapNodes.values()) {
				if(ftrSeg.doesMapNodeBelongInSeg(ex)) {					ftrSeg.addMapNodeToSegment(ex, MapNodes);		}//this does dfs to find neighbors who share feature value 	
			}			
		}
		mapMadeWithFtrWtSegThresh = nodeInSegFtrWtDistThresh;
		//if(win!=null) {win.setMapSegmentImgClrs_UMatrix();}
		msgObj.dispMessage("SOMMapManager","buildFtrWtSegmentsOnMap","Finished building feature-weight-based cluster map", MsgCodes.info5);			
	}//buildFtrWtSegmentsOnMap
	
	/**
	 * build class-based segments on map
	 */
	protected final void buildClassSegmentsOnMap() {	
		if ((MapNodes == null) || (MapNodes.size() == 0)) {return;}
		String descStr = getClassSegMappingDescrStr();
		getMsgObj().dispMessage("SOMMapManager","buildClassSegmentsOnMap","Started building " + descStr, MsgCodes.info5);	
		//clear existing segments 
		for (SOM_MapNode ex : MapNodes.values()) {ex.clearClassSeg();}
		Class_Segments.clear();
		MapNodesWithMappedClasses.clear();
		MapNodeClassProbs.clear();		
		ConcurrentSkipListMap<Tuple<Integer,Integer>, Float> tmpMapOfNodeProbs = new ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>();
		SOM_ClassSegment classSeg;
		//class labels are determined by instancing application - should be array of label values
		Integer[] allTrainClasses = getAllClassLabels();
		//check every map node for every class to see if it has class membership
		for(int clsIdx = 0; clsIdx<allTrainClasses.length;++clsIdx) {		//build class segment for every class
			Integer cls = allTrainClasses[clsIdx];
			classSeg = new SOM_ClassSegment(this, cls);
			Class_Segments.put(cls,classSeg);
			for(SOM_MapNode ex : MapNodes.values()) {if(classSeg.doesMapNodeBelongInSeg(ex)) {	classSeg.addMapNodeToSegment(ex, MapNodes);		}}//addMapNodeToSegment performs DFS to find neighbors who share segment membership 	
			Collection<SOM_MapNode> mapNodesForClass = classSeg.getAllMapNodes();
			MapNodesWithMappedClasses.put(cls, mapNodesForClass);
			//getMsgObj().dispMessage("Straff_SOMMapManager","buildClassSegmentsOnMap","Class : " + cls + " has " + mapNodesForClass.size()+ " map nodes in its segment.", MsgCodes.info5);			
			tmpMapOfNodeProbs = new ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>();
			for(SOM_MapNode mapNode : mapNodesForClass) {		tmpMapOfNodeProbs.put(mapNode.mapNodeCoord, mapNode.getClassProb(cls));}
			MapNodeClassProbs.put(cls, tmpMapOfNodeProbs);
		}
		
		getMsgObj().dispMessage("SOMMapManager","buildClassSegmentsOnMap","Finished building "+ descStr +" : "+ MapNodesWithMappedClasses.size()+" classes have map nodes mapped to them.", MsgCodes.info5);			
	}//buildClassSegmentsOnMap	

	/**
	 * build category-based segments on map
	 */
	protected final void buildCategorySegmentsOnMap() {		
		if ((MapNodes == null) || (MapNodes.size() == 0)) {return;}
		String descStr = getCategorySegMappingDescrStr();
		getMsgObj().dispMessage("SOMMapManager","buildCategorySegmentsOnMap","Started building " + descStr, MsgCodes.info5);	
		//clear existing segments 
		for (SOM_MapNode ex : MapNodes.values()) {ex.clearCategorySeg();}
		Category_Segments.clear();
		MapNodesWithMappedCategories.clear();
		MapNodeCategoryProbs.clear();		
		ConcurrentSkipListMap<Tuple<Integer,Integer>, Float> tmpMapOfNodeProbs = new ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>();
		SOM_CategorySegment catSeg;
		//category labels are determined by instancing application
		Integer[] allTrainCategories = getAllCategoryLabels();		
		
		for(int catIdx = 0; catIdx<allTrainCategories.length;++catIdx) {		//build category segment for every category label
			Integer cat = allTrainCategories[catIdx];
			catSeg = new SOM_CategorySegment(this, cat);
			Category_Segments.put(cat,catSeg);
			for(SOM_MapNode ex : MapNodes.values()) {if(catSeg.doesMapNodeBelongInSeg(ex)) {		catSeg.addMapNodeToSegment(ex, MapNodes);}}//addMapNodeToSegment performs DFS to find neighbors who share segment membership 	
				
			Collection<SOM_MapNode> mapNodesForCat = catSeg.getAllMapNodes();
			MapNodesWithMappedCategories.put(cat, mapNodesForCat);
			//getMsgObj().dispMessage("SOMMapManager","buildCategorySegmentsOnMap","Category : " + cat + " has " + mapNodesForCat.size()+ " map nodes in its segment.", MsgCodes.info5);
			tmpMapOfNodeProbs = new ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>();
			for(SOM_MapNode mapNode : mapNodesForCat) {	tmpMapOfNodeProbs.put(mapNode.mapNodeCoord, mapNode.getCategoryProb(cat));	}
			MapNodeCategoryProbs.put(cat, tmpMapOfNodeProbs);
		}
		getMsgObj().dispMessage("SOMMapManager","buildCategorySegmentsOnMap","Finished building " + descStr + " : " + MapNodesWithMappedCategories.size() + " categories have map nodes mapped to them.", MsgCodes.info5);			
	}//buildCategorySegmentsOnMap	
	/**
	 * return the class labels used for the classification of training examples to 
	 * their bmus.  bmus then represent a probability distribution of class membership
	 * @return
	 */
	protected abstract Integer[] getAllClassLabels();
	//display message relating to class segments
	protected abstract String getClassSegMappingDescrStr();
	/**
	 * return the category labels used for the classification of training examples to 
	 * their bmus.  bmus then represent a probability distribution of category membership
	 * @return
	 */
	protected abstract Integer[] getAllCategoryLabels();
	//display message relating to category segments
	protected abstract String getCategorySegMappingDescrStr();
	
	////////////////////////////////
	// segments and segment reporting	
	//save passed segments to passed segment report directory	
	/**
	 * Save segment mappings
	 */
	public void saveAllSegment_BMUReports() {
		if(!getSOMMapNodeDataIsLoaded()) {
			msgObj.dispMessage("SOMMapManager","saveAllSegment_BMUReports","SOM not yet loaded/mapped, so cannot build segment report.  Aborting.", MsgCodes.info5);
			return;
		}
		msgObj.dispMessage("SOMMapManager","saveAllSegment_BMUReports","Start saving all segment-to-bmu mapping data.", MsgCodes.info5);
		saveClassSegment_BMUReport();
		saveCategorySegment_BMUReport();
		saveFtrWtSegment_BMUReport();
		saveAllSegment_BMUReports_Indiv();
		msgObj.dispMessage("SOMMapManager","saveAllSegment_BMUReports","Finished saving all segment-to-bmu mapping data.", MsgCodes.info5);
	}
	/**
	 * save all segment reports based on instancing-app-specific segments
	 */
	protected abstract void saveAllSegment_BMUReports_Indiv();
	public void saveClassSegment_BMUReport(){	
		if((null==Class_Segments) || (Class_Segments.size()==0)) {
			msgObj.dispMessage("SOMMapManager","saveClassSegment_BMUReport","Class Segments Not yet built, so cannot save report.  Aborting", MsgCodes.info5);
			return;
		}
		String classFileNamePrefix = projConfigData.getClassSegmentFileNamePrefix();
		msgObj.dispMessage("SOMMapManager","saveClassSegment_BMUReport","Start saving "+Class_Segments.size()+" class segments", MsgCodes.info5);
		_saveSegmentReports(Class_Segments, classFileNamePrefix);
		_saveBMU_SegmentReports("class", classFileNamePrefix);
		
		msgObj.dispMessage("SOMMapManager","saveClassSegment_BMUReport","Finished saving "+Class_Segments.size()+" class segments", MsgCodes.info5);
	}
	public void saveCategorySegment_BMUReport(){	
		if((null==Category_Segments) || (Category_Segments.size()==0)) {
			msgObj.dispMessage("SOMMapManager","saveCategorySegment_BMUReport","Category Segments Not yet built, so cannot save report.  Aborting", MsgCodes.info5);
			return;
		}
		msgObj.dispMessage("SOMMapManager","saveCategorySegment_BMUReport","Start saving "+Category_Segments.size()+" category segments", MsgCodes.info5);
		String catFileNamePrefix = projConfigData.getCategorySegmentFileNamePrefix();
		_saveSegmentReports(Category_Segments, catFileNamePrefix);
		_saveBMU_SegmentReports("category", catFileNamePrefix);
		msgObj.dispMessage("SOMMapManager","saveCategorySegment_BMUReport","Finished saving "+Category_Segments.size()+" category segments", MsgCodes.info5);
	}
	public void saveFtrWtSegment_BMUReport(){		
		buildFtrWtSegmentsOnMap();
		String ftrWtFileNamePrefix = projConfigData.getFtrWtSegmentFileNamePrefix();
		msgObj.dispMessage("SOMMapManager","saveFtrWtSegment_BMUReport","Start saving " + FtrWt_Segments.size() + " Feature weight segments.", MsgCodes.info5);
		_saveSegmentReports(FtrWt_Segments, ftrWtFileNamePrefix);
		_saveBMU_SegmentReports("ftrwt", ftrWtFileNamePrefix);
		msgObj.dispMessage("SOMMapManager","saveFtrWtSegment_BMUReport","Finished saving " + FtrWt_Segments.size() + " Feature weight segments.", MsgCodes.info5);    
	}
	
	
	/**
	 * save specified segment report information - per-segment file of bmu participation and # of participating examples per bmu (if appropriate)
	 * @param _segments map of segments to save report for
	 * @param _fileNamePrefix file name prefix used to name the files
	 */
	protected void _saveSegmentReports(TreeMap<Integer, SOM_MappedSegment> _segments, String _fileNamePrefix) {
		if((null==_segments) || (_segments.size()==0)) {return;}
		int numFrmt = (int) (Math.log10(_segments.size())) + 1;
		String frmtStr = "%0"+numFrmt+"d";
		ArrayList<String> csvDataToMap;
		for(Integer segKey : _segments.keySet()) {
			SOM_MappedSegment seg = _segments.get(segKey);
			String fileName = _fileNamePrefix + "_"+ String.format(frmtStr, segKey) + ".csv";			
			csvDataToMap = seg.buildBMUMembership_CSV();
			fileIO.saveStrings(fileName, csvDataToMap);			
		}		
	}//_saveSegmentReports
	
	/**
	 * save specified segment data for all bmus - holds per-segment-ID probabilities/weights for each bmu, keyed by BMU map loc
	 * @param _segmentType
	 * @param _fileNamePrefix
	 */
	protected void _saveBMU_SegmentReports(String _segmentType, String _fileNamePrefix) {
		String fileName = _fileNamePrefix + "_All_BMUs.csv";	
		ArrayList<String> outStrs = new ArrayList<String>();
		outStrs.add(MapNodes.get(MapNodes.firstKey()).getSegment_Hdr_CSVStr(_segmentType));
		for(SOM_MapNode bmu : MapNodes.values()) {		outStrs.add(bmu.getSegment_CSVStr(_segmentType));	}
		fileIO.saveStrings(fileName, outStrs);	
	}//_saveBMU_SegmentReports
	
	/**
	 * save example to bmu mappings for passed array of example data
	 * @param exData examples to save bmu mappings for
	 * @param dataTypName
	 */
	protected void saveExamplesToBMUMappings(SOM_Example[] exData, String dataTypName, int _rawNumPerParition) {
		msgObj.dispMessage("SOMMapManager","saveExamplesToBMUMappings","Start Saving " +exData.length + " "+dataTypName+" bmu mappings to file.", MsgCodes.info5);
		String _fileNamePrefix = projConfigData.getExampleToBMUFileNamePrefix(dataTypName);
		if(exData.length > 0) {
			SOM_SaveExToBMUs_Runner saveRunner = new SOM_SaveExToBMUs_Runner(this, th_exec, exData, dataTypName, false, _fileNamePrefix,_rawNumPerParition);
			saveRunner.runMe();				
		} else {			msgObj.dispMessage("SOMMapManager","saveExamplesToBMUMappings","No "+dataTypName+" examples so cannot save bmus. Aborting.", MsgCodes.warning5);	return;	}
		msgObj.dispMessage("SOMMapManager","saveExamplesToBMUMappings","Finished Saving " +exData.length + " "+dataTypName+" bmu mappings to file.", MsgCodes.info5);
	}//saveExamplesToBMUMappings
	
	////////////////////////////////
	// end segments and segment reporting	
	
	
 
	/////////////////////////////////////
	// map node management - map nodes are represented by SOMExample objects called SOMMapNodes
	// and are classified as either having examples that map to them (they are bmu's for) or not having any
	//   furthermore, they can have types of examples mapping to them - training, product, validation

	/**
	 * build the array of data to be clustered by the trained map
	 */
	protected abstract void buildValidationDataAra();
	
	//products are zone/segment descriptors corresponding to certain feature configurations
	protected abstract void setProductBMUs();
	
	//once map is built, find bmus on map for each test data example
	protected final void setTestBMUs() {	_setExamplesBMUs(testData, "Testing", SOM_ExDataType.Testing,testDataMappedIDX);	}//setTestBMUs	
	//once map is built, find bmus on map for each validation data example
	protected final void setValidationDataBMUs() {_setExamplesBMUs(validationData, "Validation", SOM_ExDataType.Validation,validateDataMappedIDX);}//setValidationDataBMUs
	
	//set examples - either test data or validation data
	protected void _setExamplesBMUs(SOM_Example[] exData, String dataTypName, SOM_ExDataType dataType, int _rdyToSaveFlagIDX) {
		msgObj.dispMessage("SOMMapManager","_setExamplesBMUs","Start Mapping " +exData.length + " "+dataTypName+" data to best matching units.", MsgCodes.info5);
		if(exData.length > 0) {		
			//launch a MapTestDataToBMUs_Runner to manage multi-threaded calc
			SOM_MapExDataToBMUs_Runner rnr = new SOM_MapExDataToBMUs_Runner(this, th_exec, exData, dataTypName, dataType, _rdyToSaveFlagIDX, false);	
			rnr.runMe();
		} else {			msgObj.dispMessage("SOMMapManager","_setExamplesBMUs","No "+dataTypName+" data to map to BMUs. Aborting.", MsgCodes.warning5);	return;	}
		msgObj.dispMessage("SOMMapManager","_setExamplesBMUs","Finished Mapping " +exData.length + " "+dataTypName+" data to best matching units.", MsgCodes.info5);
	}//_setExamplesBMUs
	
	//call 1 time for any particular type of data - all _exs should have their bmu's set by now
	//this will set the bmu lists for each map node to include the mapped examples
	public synchronized void _completeBMUProcessing(SOM_Example[] _exs, SOM_ExDataType dataType, boolean isMT) {
		msgObj.dispMessage("SOMMapManager","_completeBMUProcessing","Start completion of " +_exs.length + " "+dataType.getName()+" data bmu mappings - assign to BMU's example collection and finalize.", MsgCodes.info5);
		int dataTypeVal = dataType.getVal();
		for(SOM_MapNode mapNode : MapNodes.values()){mapNode.clearBMUExs(dataTypeVal);addExToNodesWithNoExs(mapNode, dataType);}		//must be done synchronously always	
		if(dataType==SOM_ExDataType.Training) {			for (int i=0;i<_exs.length;++i) {			_exs[i].mapTrainingToBMU(dataTypeVal);	}		} 
		else {										for (int i=0;i<_exs.length;++i) {			_exs[i].mapToBMU(dataTypeVal);		}		}
		_finalizeBMUProcessing(dataType);
		msgObj.dispMessage("SOMMapManager","_completeBMUProcessing","Finished completion of " +_exs.length + " "+dataType.getName()+" data bmu mappings - assign to BMU's example collection and finalize.", MsgCodes.info5);
	}//_completeBMUProcessing
	
	/**
	 * this will copy relevant info from map nodes with typeIDX examples that consider them BMU
	 * to the closest "non-bmu"(un-mapped) map node that matches their features.
	 * only should be performed for training data mappings - this is necessary to make sure that 
	 * any validation data that maps to these non-training-data map nodes as bmus have some notion 
	 * of similarity to the training data                             
	 * 
	 * @param withMap set of nodes with natural mappings
	 * @param withOutMap set of nodes with no natural mappings - should be << than with map set
	 * @param typeIDX type of data
	 */
	private void addMappedNodesToEmptyNodes_FtrDist(HashSet<SOM_MapNode> withMap, HashSet<SOM_MapNode> withOutMap, int typeIDX) {
		msgObj.dispMessage("SOMMapManager","addMappedNodesToEmptyNodes_FtrDist","Start assigning " +withOutMap.size() + " map nodes that are not BMUs to any " +SOM_ExDataType.getVal(typeIDX).getName() + " examples to have nearest map node to them as BMU.", MsgCodes.info5);		
		msgObj.dispMessage("SOMMapManager","addMappedNodesToEmptyNodes_FtrDist","Start building map of nodes with examples keyed by ftr idx of non-zero ftrs", MsgCodes.info5);		
		Double minSqDist;
		float minMapSqDist, mapSqDist;
		//build a map keyed by ftrIDX of all nodes that have non-zero ftr idx values for the key ftr idx and also have examples mapped to them
		//MapNodesByFtrIDX map can't be used - it holds -all- map nodes, not just those with examples
		TreeMap<Integer, HashSet<SOM_MapNode>> MapNodesWithExByFtrIDX = new TreeMap<Integer, HashSet<SOM_MapNode>>();
		for(SOM_MapNode nodeWithEx : withMap){
			Integer[] nonZeroIDXs = nodeWithEx.getNonZeroIDXs();
			for(Integer idx : nonZeroIDXs) {
				HashSet<SOM_MapNode> nodeSet = MapNodesWithExByFtrIDX.get(idx);
				if(null==nodeSet) {nodeSet = new HashSet<SOM_MapNode>();}
				nodeSet.add(nodeWithEx);
				MapNodesWithExByFtrIDX.put(idx,nodeSet);
			}	
		}
		ArrayList<SOM_MapNode> closestNodeList = new ArrayList<SOM_MapNode>();
		Entry<Double, ArrayList<SOM_MapNode>> closestList;
		msgObj.dispMessage("SOMMapManager","addMappedNodesToEmptyNodes_FtrDist","Finished building map of nodes with examples keyed by ftr idx of non-zero ftrs | Start finding closest mapped nodes by ftr dist to non-mapped nodes.", MsgCodes.info5);		

		//for each map node without training example bmus...
		for(SOM_MapNode emptyNode : withOutMap){//node has no label mappings, so need to determine label		
			//find list of closest nodes based on ftr similarity
			closestList = emptyNode.findClosestMapNodes(MapNodesWithExByFtrIDX, emptyNode::getSqDistFromFtrType, SOM_MapManager.useUnmoddedDat);			
			minSqDist = closestList.getKey();	
			closestNodeList = closestList.getValue();	
			
			//now find closest actual map node
			SOM_MapNode closestMapNode  = emptyNode;					//will never be added
			//go through list to find closest map dist node from closest ftr dist nodes in 
			if(closestNodeList.size()==1) {
				closestMapNode = closestNodeList.get(0);		//adds single closest -map- node we know has a label, or itself if none found				
			} else {
				//if more than 1 nodes is closest to the umapped node then find the closest of these in map topology
				minMapSqDist = 1000000.0f;
				if (isToroidal()) {//minimize in-loop if checks
					for(SOM_MapNode node2 : closestNodeList){					//this is adding a -map- node
						mapSqDist = getSqMapDist_torr(node2, emptyNode);			//actual map topology dist - need to handle wrapping!
						if (mapSqDist < minMapSqDist) {minMapSqDist = mapSqDist; closestMapNode = node2;}
					}	
				} else {
					for(SOM_MapNode node2 : closestNodeList){					//this is adding a -map- node
						mapSqDist = getSqMapDist_flat(node2, emptyNode);			//actual map topology dist - need to handle wrapping!
						if (mapSqDist < minMapSqDist) {minMapSqDist = mapSqDist; closestMapNode = node2;}
					}					
				}
			}
			
			emptyNode.copyMapNodeExamples(minSqDist, closestMapNode, typeIDX);			//adds single closest -map- node we know has a label, or itself if none found
		}//for each non-mapped node
		msgObj.dispMessage("SOMMapManager","addMappedNodesToEmptyNodes_FtrDist","Finished assigning " +withOutMap.size() + " map nodes that are not BMUs to any "  +SOM_ExDataType.getVal(typeIDX).getName() + " examples to have nearest map node to them as BMU.", MsgCodes.info5);
	}//addMappedNodesToEmptyNodes_FtrDist
	
	/**
	 * finalize the bmu processing - move som nodes that have been mapped to out of the list of nodes that have not been mapped to, copy the closest mapped som node to any som nodes without mappings, finalize all som nodes
	 * @param dataType
	 */
	public synchronized void _finalizeBMUProcessing(SOM_ExDataType dataType) {
		msgObj.dispMessage("SOMMapManager","_finalizeBMUProcessing","Start finalizing BMU processing for data type : "+ dataType.getName()+".", MsgCodes.info5);		
		
		HashSet<SOM_MapNode> withMap = nodesWithEx.get(dataType), withOutMap = nodesWithNoEx.get(dataType);
		//clear out all nodes that have examples from struct holding no-example map nodes
		//remove all examples that have been mapped to
		for (SOM_MapNode tmpMapNode : withMap) {			withOutMap.remove(tmpMapNode);		}
		
		int typeIDX = dataType.getVal();
		
		//copy closest som node with mapped training examples to each som map node that has none
		if(dataType == SOM_ExDataType.Training) {addMappedNodesToEmptyNodes_FtrDist(withMap,withOutMap,typeIDX);}
		
		//finalize all examples - needs to finalize all nodes to manage the SOMMapNodeBMUExamples for the nodes that have not been mapped to 
		for(SOM_MapNode node : MapNodes.values()){		node.finalizeAllBmus(typeIDX);	}
		msgObj.dispMessage("SOMMapManager","_finalizeBMUProcessing","Finished finalizing BMU processing for data type : "+ dataType.getName()+".", MsgCodes.info5);		
	}//sa_finalizeBMUProcessing
		
	protected void _dispMappingNotDoneMsg(String callingClass, String callingMethod, String _datType) {
		msgObj.dispMessage(callingClass,callingMethod, "Mapping "+_datType+" examples to BMUs not yet complete so no mappings are being saved - please try again later", MsgCodes.warning4);		
	}
	
	public void clearBMUNodesWithExs(SOM_ExDataType _type) {							nodesWithEx.get(_type).clear();}
	public void clearBMUNodesWithNoExs(SOM_ExDataType _type) {							nodesWithNoEx.get(_type).clear();}
	public void addExToNodesWithExs(SOM_MapNode node, SOM_ExDataType _type) {			nodesWithEx.get(_type).add(node);}	
	public void addExToNodesWithNoExs(SOM_MapNode node, SOM_ExDataType _type) {			nodesWithNoEx.get(_type).add(node);}	
	public int getNumNodesWithBMUExs(SOM_ExDataType _type) {return nodesWithEx.get(_type).size();}
	public int getNumNodesWithNoBMUExs(SOM_ExDataType _type) {return nodesWithNoEx.get(_type).size();}
	public HashSet<SOM_MapNode> getNodesWithExOfType(SOM_ExDataType _type){return nodesWithEx.get(_type);}
	public HashSet<SOM_MapNode> getNodesWithNoExOfType(SOM_ExDataType _type){return nodesWithNoEx.get(_type);}
	
	//called when som wts are first loaded
	public void initMapNodes() {
		MapNodes = new TreeMap<Tuple<Integer,Integer>, SOM_MapNode>();
		//this will hold all map nodes keyed by the ftr idx where they have non-zero weight
		MapNodesByFtrIDX = new TreeMap<Integer, HashSet<SOM_MapNode>>();
		//reset segement holders
		//array of map segments based on UMatrix dist
		UMatrix_Segments = new ArrayList<SOM_MappedSegment>();
		//array of map clusters based on ftr distance
		//FtrWtSegments = new ArrayList<SOM_MapSegment>();
		FtrWt_Segments = new TreeMap<Integer, SOM_MappedSegment>();
		//Map of classes to segment
		Class_Segments = new TreeMap<Integer, SOM_MappedSegment>();
		//map of categories to segment
		Category_Segments = new TreeMap<Integer, SOM_MappedSegment>();
		//map with key being class and with value being collection of map nodes with that class present in mapped examples
		MapNodesWithMappedClasses = new TreeMap<Integer,Collection<SOM_MapNode>>();
		//map with key being category and with value being collection of map nodes with that category present in mapped examples
		MapNodesWithMappedCategories = new TreeMap<Integer,Collection<SOM_MapNode>>();
		//probabilities for each class for each map node
		MapNodeClassProbs = new ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>>();
		//probabilities for each category for each map node
		MapNodeCategoryProbs = new ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>>();
		//any instance-class specific code to execute when new map nodes are being loaded
		initMapNodesPriv();
	}//initMapNodes()
	protected abstract void initMapNodesPriv();
	
	//only appropriate if using UI
	public void initMapFtrVisAras(int numTrainFtrs) {
		if (win != null) {
			int num2ndTrainFtrs = _getNumSecondaryMaps();
			msgObj.dispMessage("SOMMapManager","initMapAras","Initializing per-feature map display to hold : "+ numTrainFtrs +" primary feature and " +num2ndTrainFtrs + " secondary feature map images.", MsgCodes.info1);
			win.initMapAras(numTrainFtrs, num2ndTrainFtrs);
		} else {msgObj.dispMessage("SOMMapManager","initMapAras","Display window doesn't exist, can't build map visualization image arrays; ignoring.", MsgCodes.warning2);}
	}//initMapAras
	
	//process map node's ftr vals, add node to map, and add node to struct without any training examples (initial state for all map nodes)
	public void addToMapNodes(Tuple<Integer,Integer> key, SOM_MapNode mapnode, float[] tmpMapMaxs, int numTrainFtrs) {
		float[] ftrData = mapnode.getFtrs();
		for(int d = 0; d<numTrainFtrs; ++d){
			map_ftrsMean[d] += ftrData[d];
			tmpMapMaxs[d] = (tmpMapMaxs[d] < ftrData[d] ? ftrData[d]  : tmpMapMaxs[d]);
			map_ftrsMin[d] = (map_ftrsMin[d] > ftrData[d] ? ftrData[d]  : map_ftrsMin[d]);
		}
		MapNodes.put(key, mapnode);	
		//set map nodes by ftr idx
		Integer[] nonZeroIDXs = mapnode.getNonZeroIDXs();
		for(Integer idx : nonZeroIDXs) {
			HashSet<SOM_MapNode> nodeSet = MapNodesByFtrIDX.get(idx);
			if(null==nodeSet) {nodeSet = new HashSet<SOM_MapNode>();}
			nodeSet.add(mapnode);
			MapNodesByFtrIDX.put(idx,nodeSet);
		}	
		//initialize : add all nodes to set, will remove nodes when they get mappings
		addExToNodesWithNoExs(mapnode, SOM_ExDataType.Training);//nodesWithNoTrainEx.add(dpt);				//initialize : add all nodes to set, will remove nodes when they get mappings
	}//addToMapNodes
	
//	private TreeMap<Integer, HashSet<SOMMapNode>>  _buildMapNodesByFtrMap(Collection<SOMMapNode> mapNodes, TreeMap<Integer, HashSet<SOMMapNode>>  MapByFtr){
//		MapByFtr.clear();
//		for(SOMMapNode mapnode : mapNodes) {
//			Integer[] nonZeroIDXs = mapnode.getNonZeroIDXs();
//			for(Integer idx : nonZeroIDXs) {
//				HashSet<SOMMapNode> nodeSet = MapByFtr.get(idx);
//				if(null==nodeSet) {nodeSet = new HashSet<SOMMapNode>();}
//				nodeSet.add(mapnode);
//				MapByFtr.put(idx,nodeSet);
//			}			
//		}
//		return MapByFtr;
//	}//_buildMapNodesByFtrMap
	
	//returns sq distance between two map locations (using actual map distance, not feature similarity) - needs to handle wrapping if map built torroidally
	private float getSqMapDist_flat(SOM_MapNode a, SOM_MapNode b){		return (a.mapLoc._SqrDist(b.mapLoc));	}//	
	//returns sq distance between two map locations - needs to handle wrapping if map built torroidally
	private float getSqMapDist_torr(SOM_MapNode a, SOM_MapNode b){
		float 
			oldXa = a.mapLoc.x - b.mapLoc.x, oldXaSq = oldXa*oldXa,			//a is to right of b
			newXa = oldXa + getMapWidth(), newXaSq = newXa*newXa,	//a is to left of b
			oldYa = a.mapLoc.y - b.mapLoc.y, oldYaSq = oldYa*oldYa,			//a is below b
			newYa = oldYa + getMapHeight(), newYaSq = newYa*newYa;	//a is above b
		return (oldXaSq < newXaSq ? oldXaSq : newXaSq ) + (oldYaSq < newYaSq ? oldYaSq : newYaSq);
	}//

	
	//build all neighborhood values for UMatrix and distance
	public void buildAllMapNodeNeighborhood_Dists() {for(SOM_MapNode ex : MapNodes.values()) {	ex.buildMapNodeNeighborUMatrixVals(MapNodes); ex.buildMapNodeNeighborSqDistVals(MapNodes);	}}

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
		for (int i=0;i<PerFtrHiWtMapNodes.length; ++i) {PerFtrHiWtMapNodes[i] = new TreeMap<Float,ArrayList<SOM_MapNode>>(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}});}
	}//initPerFtrMapOfNodes	
	
	//put a map node in PerFtrHiWtMapNodes per-ftr array
	public void setMapNodeFtrStr(SOM_MapNode mapNode) {
		TreeMap<Integer, Float> stdFtrMap = mapNode.getCurrentFtrMap(SOM_MapManager.useScaledDat);
		for (Integer ftrIDX : stdFtrMap.keySet()) {
			Float ftrVal = stdFtrMap.get(ftrIDX);
			ArrayList<SOM_MapNode> nodeList = PerFtrHiWtMapNodes[ftrIDX].get(ftrVal);
			if (nodeList== null) {			nodeList = new ArrayList<SOM_MapNode>();		}
			nodeList.add(mapNode);
			PerFtrHiWtMapNodes[ftrIDX].put(ftrVal, nodeList);
		}		
	}//setMapNodeFtrStr
		
	//after all map nodes are loaded
	public void finalizeMapNodes(float[] tmpMapMaxs, int _numTrainFtrs, int _numEx) {
		//build map nodes by ftr index structure 
		//_buildMapNodesByFtrMap(MapNodes.values(), MapNodesByFtrIDX);
		
		//make sure both unmoddified features and std'ized features are built before determining map mean/var
		//need to have all features built to scale features		
		map_ftrsDiffs = new float[_numTrainFtrs];
		//initialize array of images to display map of particular feature with
		initMapFtrVisAras(_numTrainFtrs);
		
		for(int d = 0; d<map_ftrsMean.length; ++d){map_ftrsMean[d] /= 1.0f*_numEx;map_ftrsDiffs[d]=tmpMapMaxs[d]-map_ftrsMin[d];}
		//reset this to manage all map nodes
		initPerFtrMapOfNodes(_numTrainFtrs);
//		PerFtrHiWtMapNodes = new TreeMap[_numTrainFtrs];
//		for (int i=0;i<PerFtrHiWtMapNodes.length; ++i) {PerFtrHiWtMapNodes[i] = new TreeMap<Float,ArrayList<SOMMapNode>>(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}});}
		//build stats for map nodes
		float diff;
		float[] ftrData ;
		//for every node, now build standardized features 
		for(Tuple<Integer, Integer> key : MapNodes.keySet()){
			SOM_MapNode tmp = MapNodes.get(key);
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

	//build a map node that is formatted specifically for this project
	public abstract SOM_MapNode buildMapNode(Tuple<Integer,Integer>mapLoc, String[] tkns);

	///////////////////////////
	// end build and manage mapNodes 
	
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
	
	
	///////////////////////////
	// manage mins/diffs	
	
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
	
	//call after training data feature vectors have been constructed, and get the resultant mins and diffs of the training data
	protected void setMinsAndDiffs(Float[][] _mins, Float[][] _diffs) {
		String dispStr = "MinsVals and DiffsVall being set : Mins is 2d ara of len : " + _mins.length + " with each array of len : [";		
		for(int i=0;i<_mins.length-1;++i) {dispStr+= " "+i+":"+_mins[i].length+",";}							
		dispStr+= " "+(_mins.length-1)+":"+_mins[_mins.length-1].length+"] | Diffs is 2D ara of len : "+_diffs.length + " with each array of len : [";
		for(int i=0;i<_diffs.length-1;++i) {dispStr+= " "+i+":"+_diffs[i].length+",";}
		dispStr+=" "+(_diffs.length-1)+":"+_diffs[_diffs.length-1].length+"]";
		msgObj.dispMessage("SOMMapManager","setMinsAndDiffs",dispStr, MsgCodes.info2);
		minsVals = _mins;
		diffsVals = _diffs;
	}//setMinsAndDiffs

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
	///////////////////////////
	// end manage mins/diffs	
	
	
	///////////////////////////////////////
	// ftr interp routines	
	//return interpolated feature vector on map at location given by x,y, where x,y is float location of map using mapnodes as integral locations
	//only uses training features here
	public TreeMap<Integer, Float> getInterpFtrs(float[] c){
		float xColShift = (c[0]+mapNodeCols), 
				yRowShift = (c[1]+mapNodeRows), 
				xInterp = (xColShift) %1, 
				yInterp = (yRowShift) %1;
		int xInt = (int) Math.floor(xColShift)%mapNodeCols, yInt = (int) Math.floor(yRowShift)%mapNodeRows, xIntp1 = (xInt+1)%mapNodeCols, yIntp1 = (yInt+1)%mapNodeRows;		//assume torroidal map		
		//always compare standardized feature data in test/train data to standardized feature data in map
		TreeMap<Integer, Float> LowXLowYFtrs = MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt)).getCurrentFtrMap(curMapTrainFtrType), LowXHiYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xInt,yIntp1)).getCurrentFtrMap(curMapTrainFtrType),
				 HiXLowYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yInt)).getCurrentFtrMap(curMapTrainFtrType),  HiXHiYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yIntp1)).getCurrentFtrMap(curMapTrainFtrType);
		try{
			TreeMap<Integer, Float> ftrs = interpTreeMap(interpTreeMap(LowXLowYFtrs, LowXHiYFtrs,yInterp,1.0f),interpTreeMap(HiXLowYFtrs, HiXHiYFtrs,yInterp,1.0f),xInterp,255.0f);	
			return ftrs;
		} catch (Exception e){
			msgObj.dispMessage("SOMMapManager","getInterpFtrs","Exception triggered in SOMMapManager::getInterpFtrs : \n"+e.toString() + "\n\tMessage : "+e.getMessage(), MsgCodes.error1);
			return null;
		}		
	}//getInterpFtrs
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
	
	//return interpolated UMatrix value on map at location given by x,y, where x,y  is float location of map using mapnodes as integral locations
	public Float getBiLinInterpUMatVal(float[] c){
		float xColShift = (c[0]+mapNodeCols), 
				yRowShift = (c[1]+mapNodeRows), 
				xInterp = (xColShift) %1, 
				yInterp = (yRowShift) %1;
		int xInt = (int) Math.floor(xColShift)%mapNodeCols, yInt = (int) Math.floor(yRowShift)%mapNodeRows, xIntp1 = (xInt+1)%mapNodeCols, yIntp1 = (yInt+1)%mapNodeRows;		//assume torroidal map		
		//always compare standardized feature data in test/train data to standardized feature data in map
		Float LowXLowYUMat = MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt)).getUMatDist(), LowXHiYUMat= MapNodes.get(new Tuple<Integer, Integer>(xInt,yIntp1)).getUMatDist(),
				HiXLowYUMat= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yInt)).getUMatDist(),  HiXHiYUMat= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yIntp1)).getUMatDist();
		try{
			Float uMatVal = linInterpVal(linInterpVal(LowXLowYUMat, LowXHiYUMat,yInterp,1.0f),linInterpVal(HiXLowYUMat, HiXHiYUMat,yInterp,1.0f),xInterp,255.0f);	
			return uMatVal;
		} catch (Exception e){
			msgObj.dispMessage("SOMMapManager","getBiLinInterpUMatVal","Exception triggered in SOMMapManager::getInterpFtrs : \n"+e.toString() + "\n\tMessage : "+e.getMessage(), MsgCodes.error1 );
			return 0.0f;
		}
	}//getInterpUMatVal	
	private float linInterpVal(float a, float b, float t, float mult) {		return mult*((a*(1.0f-t)) + (b*t));		}//interpVal
	
	//return interpolated UMatrix value on map at location given by x,y, where x,y  is float location of map using mapnodes as integral locations
	//public Float getBiCubicInterpUMatVal(float x, float y){
	public Float getBiCubicInterpUMatVal(float[] c){
		float xColShift = (c[0]+mapNodeCols), 	//shifted for modulo
				yRowShift = (c[1]+mapNodeRows), //shifted for modulo
				xInterp = (xColShift) %1, 
				yInterp = (yRowShift) %1;
		int xInt = (int) Math.floor(xColShift)%mapNodeCols, yInt = (int) Math.floor(yRowShift)%mapNodeRows;		//assume torroidal map		
		SOM_MapNode ex = MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt));
		try{
			Float uMatVal = 255.0f*(ex.biCubicInterp_UMatrix(xInterp, yInterp));
			return uMatVal;
		} catch (Exception e){
			msgObj.dispMessage("SOMMapManager","getBiCubicInterpUMatVal","Exception triggered in SOMMapManager::getInterpFtrs : \n"+e.toString() + "\n\tMessage : "+e.getMessage(), MsgCodes.error1 );
			return 0.0f;
		}
	}//getInterpUMatVal
	
	//synthesize umat value
	public int getUMatrixSegementColorAtPxl(float[] c) {
		float xColShift = (c[0]+mapNodeCols), 
				yRowShift = (c[1]+mapNodeRows), 
				xInterp = (xColShift) %1, 
				yInterp = (yRowShift) %1;
		int xInt = (int) Math.floor(xColShift)%mapNodeCols, yInt = (int) Math.floor(yRowShift)%mapNodeRows;		//assume torroidal map		
		SOM_MapNode ex = MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt));
		try{
			Float uMatVal = (ex.biCubicInterp_UMatrix(xInterp, yInterp));
			return (uMatVal > nodeInSegUMatrixDistThresh ? 0 : ex.getUMatrixSegClrAsInt());
		} catch (Exception e){
			msgObj.dispMessage("SOMMapManager","getUMatrixSegementColorAtPxl","Exception triggered in SOMMapManager::getInterpFtrs : \n"+e.toString() + "\n\tMessage : "+e.getMessage() , MsgCodes.error1);
			return 0;
		}
	}//getUMatrixSegementColorAtPxl	
	
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
	//shuffle all training example data passed
	public SOM_Example[] shuffleTrainingData(SOM_Example[] _list, long seed) {
		SOM_Example tmp;
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
	// mouse and draw routines	
	
	//set specific mouse-over display data/values
	public SOM_MseOvrDisplay setMseDataExampleFtrs(myPointf ptrLoc, TreeMap<Integer, Float> ftrs, float sens) {mseOverExample.initMseDatFtrs(ptrLoc, ftrs, sens); return mseOverExample;}
	public SOM_MseOvrDisplay setMseDataExampleDists(myPointf ptrLoc, float dist, float sens) {mseOverExample.initMseDatUMat( ptrLoc, dist, sens);return mseOverExample;}
	public SOM_MseOvrDisplay setMseDataExampleClassProb(myPointf ptrLoc, SOM_MapNode nearestNode, float sens) {mseOverExample.initMseDatProb( ptrLoc, nearestNode, sens, true);return mseOverExample;}
	public SOM_MseOvrDisplay setMseDataExampleCategoryProb(myPointf ptrLoc, SOM_MapNode nearestNode, float sens) {mseOverExample.initMseDatProb( ptrLoc, nearestNode, sens, false);return mseOverExample;}
	public SOM_MseOvrDisplay setMseDataExampleNodePop(myPointf ptrLoc, SOM_MapNode nearestNode, float sens) {mseOverExample.initMseDatProb( ptrLoc, nearestNode, sens, SOM_ExDataType.Training);return mseOverExample;}
	public SOM_MseOvrDisplay setMseDataExampleNone() { mseOverExample.clearMseDat(); return mseOverExample;}
		
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
	public final void drawUMatrixVals(my_procApplet pa) {
		pa.pushMatrix();pa.pushStyle();
		for(SOM_MapNode node : MapNodes.values()){	node.drawMeUMatDist(pa);	}		
		pa.popStyle();pa.popMatrix();
	}//drawUMatrix
	//draw boxes around each node representing UMatrix-distance-based segments these nodes belong to
	public final void drawUMatrixSegments(my_procApplet pa) {
		pa.pushMatrix();pa.pushStyle();
		for(SOM_MapNode node : MapNodes.values()){	node.drawMeUMatSegClr(pa);	}		
		pa.popStyle();pa.popMatrix();
	}//drawUMatrix
	
	//draw boxes around each node representing ftrwt-based segments that nodes belong to, so long as their ftr values are higher than threshold amount
	public final void drawFtrWtSegments(my_procApplet pa, float valThresh, int curFtrIdx) {
		pa.pushMatrix();pa.pushStyle();
		TreeMap<Float,ArrayList<SOM_MapNode>> map = PerFtrHiWtMapNodes[curFtrIdx];
		//map holds map nodes keyed by wt of nodes that actually have curFtrIdx presence
		SortedMap<Float,ArrayList<SOM_MapNode>> headMap = map.headMap(valThresh);
		for(Float key : headMap.keySet()) {
			ArrayList<SOM_MapNode> ara = headMap.get(key);
			for (SOM_MapNode node : ara) {		node.drawMeFtrWtSegClr(pa, curFtrIdx, key);}
		}		
		pa.popStyle();pa.popMatrix();
	}//drawFtrWtSegments
	
	//draw boxes around every node representing ftrwt-based segments that node belongs to, with color strength proportional to ftr val and different colors for each segment
	public final void drawAllFtrWtSegments(my_procApplet pa, float valThresh) {		
		for(int curFtrIdx=0;curFtrIdx<PerFtrHiWtMapNodes.length;++curFtrIdx) {		drawFtrWtSegments(pa, valThresh, curFtrIdx);	}		
	}//drawFtrWtSegments

	
	/**
	 * draw boxes around each node representing class-based segments that node 
	 * belongs to, with color strength proportional to probablity and 
	 * different colors for each segment
	 * pass class -label- not class index
	 * @param pa
	 * @param classLabel - label corresponding to class to be displayed
	 */
	public abstract void drawClassSegments(my_procApplet pa, int classLabel);	
	public final void drawAllClassSegments(my_procApplet pa) {	for(Integer key : Class_Segments.keySet()) {	drawClassSegments(pa,key);}	}

	/**
	 * draw filled boxes around each node representing category-based segments 
	 * that node belongs to, with color strength proportional to probablity 
	 * and different colors for each segment
	 * pass class -label- not class index
	 * @param pa
	 * @param classLabel - label corresponding to class to be displayed
	 */
	public abstract void drawCategorySegments(my_procApplet pa, int categoryLabel);
	public final void drawAllCategorySegments(my_procApplet pa) {	for(Integer key : Category_Segments.keySet()) {	drawCategorySegments(pa,key);}	}
		
	public void drawAllNodes(my_procApplet pa) {//, int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		//pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		for(SOM_MapNode node : MapNodes.values()){	node.drawMeSmall(pa);	}
		pa.popStyle();pa.popMatrix();
	} 
	
	public void drawAllNodesNoLbl(my_procApplet pa) {//, int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		//pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		for(SOM_MapNode node : MapNodes.values()){	node.drawMeSmallNoLbl(pa);	}
		pa.popStyle();pa.popMatrix();
	} 
	
	public void drawNodesWithWt(my_procApplet pa, float valThresh, int curFtrIdx) {//, int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		//pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		TreeMap<Float,ArrayList<SOM_MapNode>> map = PerFtrHiWtMapNodes[curFtrIdx];
		SortedMap<Float,ArrayList<SOM_MapNode>> headMap = map.headMap(valThresh);
		for(Float key : headMap.keySet()) {
			ArrayList<SOM_MapNode> ara = headMap.get(key);
			for (SOM_MapNode node : ara) {		node.drawMeWithWt(pa, 10.0f*key, new String[] {""+node.OID+" : ",String.format("%.4f",key)});}
		}
		pa.popStyle();pa.popMatrix();
	}//drawNodesWithWt
	
	public void drawPopMapNodes(my_procApplet pa, SOM_ExDataType _type) {
		pa.pushMatrix();pa.pushStyle();
		int _typeIDX = _type.getVal();
		for(SOM_MapNode node : MapNodes.values()){	node.drawMePopLbl(pa, _typeIDX);}
		pa.popStyle();pa.popMatrix();		
	}	
	public void drawPopMapNodesNoLbl(my_procApplet pa, SOM_ExDataType _type) {
		pa.pushMatrix();pa.pushStyle();
		int _typeIDX = _type.getVal();
		for(SOM_MapNode node : MapNodes.values()){				node.drawMePopNoLbl(pa, _typeIDX);}
		pa.popStyle();pa.popMatrix();		
	}
	
	//get ftr name/idx/instance-specific value based to save an image of current map
	public abstract String getSOMLocClrImgForFtrFName(int ftrIDX);
	
	protected int sideBarMseOvrDispOffset = 100;
	protected float sideBarYDisp = 10.0f;

	//draw right sidebar data
	public void drawResultBar(my_procApplet pa, float yOff) {
		yOff-=4;
		float sbrMult = 1.2f, lbrMult = 1.5f;//offsets multiplier for barriers between contextual ui elements
		pa.pushMatrix();pa.pushStyle();
		//display preloaded maps
		yOff=drawLoadedPreBuiltMaps(yOff);
		//display mouse-over results in side bar
		yOff= drawMseRes(yOff);
		pa.sphere(3.0f);
		yOff = drawResultBarPriv1(pa, yOff);
		
		yOff = drawResultBarPriv2(pa, yOff);

		yOff = drawResultBarPriv3(pa, yOff);

		pa.popStyle();pa.popMatrix();	
	}//drawResultBar
	
	/**
	 * draw mouse-over results
	 * @param yOff
	 * @return
	 */
	private final float drawMseRes(float yOff) {
		if((getFlag(dispMseDataSideBarIDX)) && mseOverExample.canDisplayMseLabel()) {
			pa.translate(10.0f, 0.0f, 0.0f);
			pa.showOffsetText(0,pa.gui_White,"Mouse Values : ");
			yOff += sideBarYDisp;
			pa.translate(0.0f,sideBarYDisp, 0.0f);
			mseOverExample.drawMseLbl_Info(pa, new myPointf(0,0,0));
			float tmpOff = ((int)(mseOverExample.getMseLabelYOffset() / (1.0f*sideBarMseOvrDispOffset)) + 1 )*sideBarMseOvrDispOffset;
			yOff += tmpOff;
			pa.translate(-10.0f, tmpOff, 0.0f);
		}
		return yOff;
	}//drawMseRes
	
	protected final float drawLoadedPreBuiltMaps(float yOff) {
		if(getFlag(dispLdPreBuitMapsIDX)) {	
			String[][] loadedPreBuiltMapData = projConfigData.getPreBuiltMapInfoAra();		
			pa.translate(0.0f, 0.0f, 0.0f);
			float stYOff = yOff, tmpOff = sideBarMseOvrDispOffset;	
			if(loadedPreBuiltMapData.length==0) {				
				pa.showOffsetText(0,pa.gui_White,"No Pre-build Map Directories specified.");
			} else {				
				pa.showOffsetText(0,pa.gui_White,"Pre-build Map Directories specified in config : ");
				yOff += sideBarYDisp;
				pa.translate(10.0f, sideBarYDisp, 0.0f);
				for(int i=0;i<loadedPreBuiltMapData.length;++i) {yOff = getPreBuiltMapInfoDetail(pa,loadedPreBuiltMapData[i], i, yOff);}
			}		
			pa.translate(-10.0f, 0.0f, 0.0f);
		}
		return yOff;
	}//drawLoadedPreBuiltMaps
	protected abstract float getPreBuiltMapInfoDetail(my_procApplet pa, String[] str, int i, float yOff);
	
	//draw app-specific sidebar data
	protected abstract float drawResultBarPriv1(my_procApplet pa, float yOff);
	protected abstract float drawResultBarPriv2(my_procApplet pa, float yOff);
	protected abstract float drawResultBarPriv3(my_procApplet pa, float yOff);
	
	//invoke multi-threading call to build map imgs - called from UI window
	public void invokeSOMFtrDispBuild(List<SOM_FtrMapVisImgBldr> mapImgBuilders) {		
		try {
			List<Future<Boolean>> mapImgFtrs = th_exec.invokeAll(mapImgBuilders);
			for(Future<Boolean> f: mapImgFtrs) { f.get(); }
		} catch (Exception e) { e.printStackTrace(); }	
	}//
	
	public int[] getRndClr() { 				if (win==null) {return new int[] {255,255,255,255};}return win.pa.getRndClr2();}
	public int[] getRndClr(int alpha) {		if (win==null) {return new int[] {255,255,255,alpha};}return win.pa.getRndClr2(alpha);}

	//////////////////////////////
	// getters/setters
	
	//return a map of descriptive quantities and their values, for the SOM Execution human-readable report
	public abstract TreeMap<String, String> getSOMExecInfo();
	
	//return a copy of the message object - making a copy so that multiple threads can consume without concurrency issues
	public MessageObject buildMsgObj() {return MessageObject.buildMe();}
	
	/**
	 * called from map as bmus after loaded and training data bmus are set from bmu file - application-specific functionality 
	 */
	public abstract void setAllBMUsFromMap();
	
	public void setMapImgClrs(){if (win != null) {win.setMapImgClrs();} else {msgObj.dispMessage("SOMMapManager","setMapImgClrs","Display window doesn't exist, can't build visualization images; ignoring.", MsgCodes.warning2);}}

	public boolean getUseChiSqDist() {return useChiSqDist;}
	public void setUseChiSqDist(boolean _useChiSq) {useChiSqDist=_useChiSq;}
	
	//set current map ftr type, and update ui if necessary
	public void setCurrentTrainDataFormat(int _frmt) {	curMapTrainFtrType = _frmt; msgObj.dispMessage("SOMMapManager","setCurrentTrainDataFormat","curMapTrainFtrType set to : " +curMapTrainFtrType + ".", MsgCodes.warning2); }//setCurrentDataFormat
	public int getCurrentTrainDataFormat() {	return curMapTrainFtrType;}
	
	public void setCurrentTestDataFormat(int _frmt) {	curMapTestFtrType = _frmt; }//setCurrentDataFormat
	public int getCurrentTestDataFormat() {	return curMapTestFtrType;}
	public MessageObject getMsgObj(){	return msgObj;}
	public void setMsgObj(MessageObject msgObj) {	this.msgObj = msgObj;}
	public float getMapWidth(){return mapDims[0];}
	public float getMapHeight(){return mapDims[1];}
	public int getMapNodeCols(){return mapNodeCols;}
	public int getMapNodeRows(){return mapNodeRows;}	
	
	public SOM_MapNode getMapNodeByCoords(Tuple<Integer,Integer> key) {return MapNodes.get(key);}
	public TreeMap<Tuple<Integer,Integer>, SOM_MapNode> getMapNodes(){return MapNodes;}
	public TreeMap<Integer, HashSet<SOM_MapNode>> getMapNodesByFtr(){return MapNodesByFtrIDX;}
	
	public float getNodePerPxlCol() {return nodeXPerPxl;}
	public float getNodePerPxlRow() {return nodeYPerPxl;}	
	//mean/var,mins/diffs of features of map nodes
	public float[] getMap_ftrsMean() {return map_ftrsMean;}			
	public float[] getMap_ftrsVar() {return map_ftrsVar;}			
	public float[] getMap_ftrsDiffs() {return map_ftrsDiffs;}			
	public float[] getMap_ftrsMin() {return map_ftrsMin;}
	
	public Float[] getMinVals(int idx){return minsVals[idx];}
	public Float[] getDiffVals(int idx){return diffsVals[idx];}
	public abstract Float[] getTrainFtrMins();
	public abstract Float[] getTrainFtrDiffs();

	public SOM_Example[] getTrainingData() {return trainData;}
	
	//# of features used to train SOM
	public int getNumTrainFtrs() {return numTrnFtrs;}
	public void setNumTrainFtrs(int _numTrnFtrs) {numTrnFtrs = _numTrnFtrs;}
	
	//project config manages this information now
	public Calendar getInstancedNow() { return projConfigData.getInstancedNow();}
	
	//getter/setter/convenience funcs to check for whether mt capable, and to return # of usable threads (total host threads minus some reserved for processing)
	public int getNumUsableThreads() {return numUsableThreads;}
	public ExecutorService getTh_Exec() {return th_exec;}
	

	////////////////////////////////
	// segments and segment-related values
	
	//umatrix segment thresholds
	public static float getNodeInUMatrixSegThresh() {return nodeInSegUMatrixDistThresh;}
	public static void setNodeInUMatrixSegThresh(float _val) {nodeInSegUMatrixDistThresh=_val;}	
	public String getUMatrixSegmentTitleString() {return "UMatrix Dist calc from SOM Training.";}
	//ftr-wt segment thresholds
	public static float getNodeInFtrWtSegThresh() {return nodeInSegFtrWtDistThresh;}
	public static void setNodeInFtrWtSegThresh(float _val) {nodeInSegFtrWtDistThresh=_val;}		
	public abstract String getFtrWtSegmentTitleString(int ftrCalcType, int ftrIDX);
	//class and category segments
	public TreeMap<Integer, SOM_MappedSegment> getClass_Segments(){ return Class_Segments; }
	public abstract String getClassSegmentTitleString(int classID);
	public TreeMap<Integer, SOM_MappedSegment> getCategory_Segments(){ return Category_Segments; }
	public abstract String getCategorySegmentTitleString(int catID);

	
	//////////////
	// private state flags
	
	public boolean isMTCapable() {return getFlag(isMTCapableIDX);}		
	//set flag that SOM file loader is finished to false
	//public void setLoaderRtnFalse() {setFlag(loaderFinishedRtnIDX, false);}	
	// use functions to easily access states
	public void setIsDebug(boolean val) {setFlag(debugIDX, val);}
	public boolean getIsDebug() {return getFlag(debugIDX);}	
	public void setSOMMapNodeDataIsLoaded(boolean val) {setFlag(SOMmapNodeDataLoadedIDX, val);}
	public boolean getSOMMapNodeDataIsLoaded() {return getFlag(SOMmapNodeDataLoadedIDX);}	
	public void setLoaderRTN(boolean val) {setFlag(loaderFinishedRtnIDX, val);}
	public boolean getLoaderRTN() {return getFlag(loaderFinishedRtnIDX);}
	public void setDenseTrainDataSaved(boolean val) {setFlag(denseTrainDataSavedIDX, val);}
	public void setSparseTrainDataSaved(boolean val) {setFlag(sparseTrainDataSavedIDX, val);}
	public void setCSVTestDataSaved(boolean val) {setFlag(testDataSavedIDX, val);}	
	//set all training/testing data save flags to val
	public void setAllTrainDatSaveFlags(boolean val) {
		setFlag(denseTrainDataSavedIDX, val);
		setFlag(sparseTrainDataSavedIDX, val);
		setFlag(testDataSavedIDX, val);		
	}			
	public boolean getTrainDataBMUsRdyToSave() {return getFlag(trainDataMappedIDX);}
	public boolean getProdDataBMUsRdyToSave() {return getFlag(prodDataMappedIDX);}
	public boolean getTestDataBMUsRdyToSave() {return (getFlag(testDataMappedIDX) || testData.length==0);}
	public boolean getValidationDataBMUsRdyToSave() {return getFlag(validateDataMappedIDX);}
	//call on load of bmus
	public void setTrainDataBMUsRdyToSave(boolean val) {setFlag(trainDataMappedIDX,val);}
	public void setProdDataBMUsRdyToSave(boolean val) {setFlag(prodDataMappedIDX, val);}
	
	protected abstract int getNumFlags();
	private void initFlags(){int _numFlags = getNumFlags(); stFlags = new int[1 + _numFlags/32]; for(int i = 0; i<_numFlags; ++i){setFlag(i,false);}}
	private void setAllFlags(int[] idxs, boolean val) {for (int idx : idxs) {setFlag(idx, val);}}
	public void setFlag(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case debugIDX 					: {break;}	
			case isMTCapableIDX 			: {break;}					//whether or not the host architecture can support multiple execution threads
			case SOMmapNodeDataLoadedIDX	: {break;}		
			case loaderFinishedRtnIDX 		: {break;}
			case denseTrainDataSavedIDX : {
				if (val) {msgObj.dispMessage("SOMMapManager","setFlag","All "+ this.numTrainData +" Dense Training data saved to .lrn file", MsgCodes.info5);}
				break;}				//all prospect examples saved as training data
			case sparseTrainDataSavedIDX : {
				if (val) {msgObj.dispMessage("SOMMapManager","setFlag","All "+ this.numTrainData +" Sparse Training data saved to .svm file", MsgCodes.info5);}
				break;}				//all prospect examples saved as training data
			case testDataSavedIDX : {
				if (val) {msgObj.dispMessage("SOMMapManager","setFlag","All "+ this.numTestData + " saved to " + projConfigData.getSOMMapTestFileName() + " using "+(projConfigData.isUseSparseTestingData() ? "Sparse ": "Dense ") + "data format", MsgCodes.info5);}
				break;}	
			case trainDataMappedIDX			: {break;}
			case prodDataMappedIDX			: {break;}
			case testDataMappedIDX			: {break;}
			case validateDataMappedIDX		: {break;}
			case dispMseDataSideBarIDX		: {break;}
			case dispLdPreBuitMapsIDX		: {break;}
			
			default : { setFlag_Indiv(idx, val);}	//any flags not covered get set here in instancing class			
		}
	}//setFlag		
	public boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		
	protected abstract void setFlag_Indiv(int idx, boolean val);	
	
	//getter/setter/convenience funcs
	public boolean mapCanBeTrained(int kVal) {
		msgObj.dispMessage("SOMMapManager","mapCanBeTrained","denseTrainDataSavedIDX : " + getFlag(denseTrainDataSavedIDX), MsgCodes.info5);
		msgObj.dispMessage("SOMMapManager","mapCanBeTrained","sparseTrainDataSavedIDX : " + getFlag(sparseTrainDataSavedIDX), MsgCodes.info5);
		boolean val = ((kVal <= 1) && (getFlag(denseTrainDataSavedIDX)) || ((kVal == 2) && getFlag(sparseTrainDataSavedIDX)));
		msgObj.dispMessage("SOMMapManager","mapCanBeTrained","kVal : " + kVal + " | bool val : " + val, MsgCodes.info5);
		
		//eventually enable training map on existing files - save all file names, enable file names to be loaded and map built directly
		return ((kVal <= 1) && (getFlag(denseTrainDataSavedIDX)) || ((kVal == 2) && getFlag(sparseTrainDataSavedIDX)));
	}	
	//return true if loader is done and if data is successfully loaded
	public boolean isMapDrawable(){return getFlag(loaderFinishedRtnIDX) && getFlag(SOMmapNodeDataLoadedIDX);}
	public boolean isToroidal(){return projConfigData.isToroidal();}	
	//get fill, stroke and text color ID if win exists (to reference papplet) otw returns 0,0,0
	public int[] getClrFillStrkTxtAra(SOM_ExDataType _type) {
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
	public int[] getAltClrFillStrkTxtAra() {
		if (win==null) {return new int[] {0,0,0};}		
		return new int[] {my_procApplet.gui_Red,my_procApplet.gui_Red,my_procApplet.gui_White};
	}
	
	
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
	}//setMapX
	public void setMapNumRows(int _y){
		//need to update UI value in win
		mapNodeRows = _y;
		nodeYPerPxl = mapNodeRows/this.mapDims[1];
		projConfigData.updateMapDat_Integer_MapRows(_y, true,true);
	}//setMapY

	public String toString(){
		String res = "Map Manager : ";
		res += "PApplet is :"+(pa==null ? "null " : "present and non-null " ) +  " | UI Window class is : "+(win==null ? "null " : "present and non-null " );
		
		return res;	
	}	
	
}//abstract class SOMMapManager



//manage a message stream from a launched external process - used to manage output from som training process
class ProcConsoleMsgMgr implements Callable<Boolean> {
	MessageObject msgObj;
	final Process process;
	BufferedReader rdr;
	StringBuilder strbld;
	String type;
	MsgCodes msgType;//for display of output
	int iter = 0;
	public ProcConsoleMsgMgr(SOM_MapManager _mapMgr, final Process _process, Reader _in, String _type) {
		msgObj = _mapMgr.getMsgObj();
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
		if(msgObj != null) {
			String typStr = getStreamType(str);			
			msgObj.dispMessage("messageMgr","call ("+typStr+" Stream Handler)", str, useCode);}
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

