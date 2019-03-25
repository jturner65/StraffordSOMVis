package SOM_Strafford_PKG;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

//this class holds the data describing a SOM and the data used to both build and query the som
public class SOMMapManager {
	//struct maintaining complete project configuration and information from config files - all file name data and building needs to be done by this object
	public SOMProjConfigData projConfigData;			
	//manage IO in this object
	private FileIOManager fileIO; 
			
	////////////////////////////////////////////////////////////////////////////////////////////////
	//map descriptions
	//full input data, data set to be training data and testing data (all of these examples are potential -training- data, in that they have all features required of training data)
	public ProspectExample[] inputData, trainData, testData;	
	//all nodes of som map, keyed by node location as tuple of row/col coordinates
	public TreeMap<Tuple<Integer,Integer>, SOMMapNode> MapNodes;	
	
	//map keyed by type of maps of prospectExamples built from database data, each keyed by prospect OID.  type :  
	//"customer" : customer prospectExamples with order events in their history
	//"prospect" : true prospects, with no order event history 
	private ConcurrentSkipListMap<String, ConcurrentSkipListMap<String, ProspectExample>> prospectExamples;
	private String custExKey = "custProspect", prspctExKey = "trueProspect";
	
	//private ConcurrentSkipListMap<String, ProspectExample> customerPrspctMap;	
	//map of prospectExamples built from database data, keyed by prospect OID - these are true prospects, with no order event history 
	//TODO these examples are loaded and then applied against an existing map to find products for them
	//private ConcurrentSkipListMap<String, ProspectExample> truePrspctMap;	
	//map of names of 
	//map of products build from TC_Taggings entries, keyed by tag ID (synthesized upon creation)
	private ConcurrentSkipListMap<String, ProductExample> productMap;	
	
	//structure to map specified products to the SOM and find prospects with varying levels of confidence
	public StraffProdMapOutputBuilder prodMapper;
	
	//manage all jps and jpgs seen in project
	public MonitorJpJpgrp jpJpgrpMon;	
	//calc object to be used to derive feature vector for each prospect
	public StraffWeightCalc ftrCalcObj;
	//data for products to be measured on map
	private ProductExample[] productData;
	//maps of product arrays, with key for each map being either jpg or jp
	private TreeMap<Integer, ArrayList<ProductExample>> productsByJpg, productsByJp;
	//array of per jp treemaps of nodes keyed by jp weight
	public TreeMap<Float,ArrayList<SOMMapNode>>[] PerJPHiWtMapNodes;
	//array of map clusters
	public ArrayList<SOMMapSegment> segments;
	
	//data values directly from the trained map
	public float[] 
			map_ftrsMean, 				
			map_ftrsVar, 
			map_ftrsDiffs, 
			map_ftrsMin;				//per feature mean, variance, difference, mins, in -map features- data
//	public float[] 		
//			td_ftrsMean, td_ftrsVar, 	//TODO perhaps remove these - we have this info already
//			in_ftrsMean, in_ftrsVar; 	//TODO perhaps remove these - we have this info already : per feature training and input data means and variances
	
	public TreeMap<ExDataType, HashSet<SOMMapNode>> nodesWithEx, nodesWithNoEx;	//map nodes that have/don't have training examples - for display only
	
	//types of possible mappings to particular map node as bmu
	//corresponds to these values : ProspectTraining(0),ProspectTesting(1),Product(2)
	public static final String[] nodeBMUMapTypes = new String[] {"Training", "Testing", "Products"};
	
	public int numFtrs;
	public int numInputData, numTrainData, numTestData;
	
	public Float[] diffsVals, minsVals;	//values to return scaled values to actual data points - multiply wts by diffsVals, add minsVals
	
	private int[] stFlags;						//state flags - bits in array holding relevant process info
	public static final int
			debugIDX 					= 0,
			isMTCapableIDX				= 1,
			mapDataLoadedIDX			= 2,			//som map data is cleanly loaded
			loaderRtnIDX				= 3,			//dataloader has finished - wait on this to draw map
			mapExclProdZeroFtrIDX  		= 4,			//if true, exclude u
			
			//raw data loading/processing state : 
			prospectDataLoadedIDX		= 5,			//raw prospect data has been loaded but not yet processed
			optDataLoadedIDX			= 6,			//raw opt data has been loaded but not processed
			orderDataLoadedIDX			= 7,			//raw order data loaded not proced
			linkDataLoadedIDX			= 8,			//raw link data loaded not proced
			sourceDataLoadedIDX			= 9,			//raw source event data loaded not proced
			tcTagsDataLoadedIDX			= 10,			//raw tc taggings data loaded not proced
			jpDataLoadedIDX				= 11,			//raw jp data loaded not proced
			jpgDataLoadedIDX			= 12,			//raw jpg data loaded not proced
			rawPrspctEvDataProcedIDX	= 13,			//all raw prospect/event data has been loaded and processed into StraffSOMExamples (prospect)
			rawProducDataProcedIDX		= 14,			//all raw product data (from tc_taggings) has been loaded and processed into StraffSOMExamples (product)
			//training data saved state : 
			testTrainProdDataBuiltIDX	= 15,			//product, input, testing and training data arrays have all been built
			denseTrainDataSavedIDX 		= 16,			//all current prospect data has been saved as a training data file for SOM (.lrn format) - strafford doesn't use dense training data
			sparseTrainDataSavedIDX		= 17,			//sparse data format using .svm file descriptions (basically a map with a key:value pair of ftr index : ftr value
			testDataSavedIDX			= 18;			//save test data in sparse format csv
		
	public static final int numFlags = 19;	
	
	//////////////////////////////
	//data in files created by SOM_MAP separated by spaces
	public static final String SOM_FileToken = " ", csvFileToken = "\\s*,\\s*";	
	//size of intermediate per-OID record csv files : 
	public static final int preProcDatPartSz = 50000;	

	//////////////////
	//source data constructs
	//all of these constructs must follow the same order - 1st value must be prospect, 2nd must be order events, etc.
	//idxs for each type of data in arrays holding relevant data info
	public static final int prspctIDX = 0, orderEvntIDX = 1, optEvntIDX = 2, linkEvntIDX = 3, srcEvntIDX = 4, tcTagsIDX = 5, jpDataIDX = 6, jpgDataIDX = 7;
	//directory names where raw data files can be found - also use as key in rawDataArrays to access arrays of raw objects
	private static final String[] straffDataDirNames = new String[] {"prospect_objects", "order_event_objects", "opt_event_objects", "link_event_objects", "source_event_objects", "tc_taggings", "jp_data", "jpg_data"};
	//idxs of string keys in rawDataArrays corresponding to EVENTS (from xxx_event_objects tables)
	private static final int[] straffEventDataIDXs = new int[] {orderEvntIDX, optEvntIDX, linkEvntIDX, srcEvntIDX};
	
	//file names for specific file dirs/types, keyed by dir - looks up in directory and gets all csv files to use as sources
	private TreeMap<String, String[]> straffDataFileNames;
	//whether each table uses json as final field to hold important info or not
	private static final boolean[] straffRawDatUsesJSON = new boolean[] {true, true, true, true, true, false, true, true};
	//whether we want to debug the loading of a particular type of raw data
	private static final boolean[] straffRawDatDebugLoad = new boolean[] {false, false, false, false,false, true, true, true};
	//list of idxs related to each table for data
	private static final int[] straffObjFlagIDXs = new int[] {prospectDataLoadedIDX, orderDataLoadedIDX, optDataLoadedIDX, linkDataLoadedIDX, sourceDataLoadedIDX, tcTagsDataLoadedIDX, jpDataLoadedIDX, jpgDataLoadedIDX};
	//list of class names used to build array of object loaders
	private static final String[] straffClassLdrNames = new String[] {
			"SOM_Strafford_PKG.ProspectDataLoader","SOM_Strafford_PKG.OrderEventDataLoader","SOM_Strafford_PKG.OptEventDataLoader","SOM_Strafford_PKG.LinkEventDataLoader",
			"SOM_Strafford_PKG.SourceEventDataLoader","SOM_Strafford_PKG.TcTagDataLoader", "SOM_Strafford_PKG.JpDataLoader", "SOM_Strafford_PKG.JpgrpDataLoader"
		};	
	
	//classes of data loader objects
	public Class[] straffObjLoaders;
	//destination object to manage arrays of each type of raw data from db
	public ConcurrentSkipListMap<String, ArrayList<BaseRawData>> rawDataArrays;
	//for multi-threaded calls to base loader
	public List<Future<Boolean>> straffDataLdrFtrs;
	public TreeMap<String, StraffordDataLoader>straffDataLoaders;
	//public List<StraffordDataLoader> straffDataLoaders;
	////////////////////
		
	//data type to use to train map
	public static final int useUnmoddedDat = 0, useScaledDat = 1, useNormedDat = 2;
	public static final String[] uiMapTrainFtrTypeList = new String[] {"Unmodified","Standardized (0->1 per ftr)","Normalized (vector mag==1)"};
	//feature type used for currently trained/loaded map
	private int curMapFtrType;
	
	//distance to use :  1: chisq features or 0 : regular feature dists
	private boolean useChiSqDist;
	
	//map dims is used to calculate distances for BMUs - based on screen dimensions - need to change this?
	private float[] mapDims;
	//# of nodes in x/y
	private int mapNodeCols =0, mapNodeRows =0;
	//# of nodes / map dim  in x/y
	private float nodeXPerPxl, nodeYPerPxl;
	//threshold of u-dist for nodes to belong to same segment
	private static float nodeInSegDistThresh = .3f;
	private float mapMadeWithSegThresh = 0.0f;
	
	//////////////////////
	// misc.
	//used by UI for visualization, ignored if NULL (passed by command line program)
	public SOM_StraffordMain pa;				//applet, if used in graphical context
	public mySOMMapUIWin win;					//owning window
	
	//time of current process start, from initial construction of mapmgr - TODO use this to monitor specific process time elapsed.  set to 0 at beginning of a particular process, then measure time elapsed in process
	private long curProcStartTime;
	//time mapMgr built, in millis - used as offset for instant to provide smaller values for timestamp
	private final long mapMgrBuiltTime;
	
	//threading constructions
	private ExecutorService th_exec;	//to access multithreading - instance from calling program
	private final int numUsableThreads;		//# of threads usable by the application
	///////////////////////////////////	
	
	private SOMMapManager(mySOMMapUIWin _win, ExecutorService _th_exec, float[] _dims) {
		pa=null; win=_win;th_exec=_th_exec;		
		fileIO = new FileIOManager(this,"SOMMapManager");
		//want # of usable background threads.  Leave 2 for primary process (and potential draw loop)
		numUsableThreads = Runtime.getRuntime().availableProcessors() - 2;
		//for display of time since processes occur
		Instant now = Instant.now();
		mapMgrBuiltTime = now.toEpochMilli();//milliseconds since 1/1/1970 when this exec was built.
		initFlags();
		//set if this is multi-threaded capable - need more than 1 outside of 2 primary threads (i.e. only perform multithreaded calculations if 4 or more threads are available on host)
		setFlag(isMTCapableIDX, numUsableThreads>1);
		
		//build project configuration data object - this manages all file locations and other configuration options
		projConfigData = new SOMProjConfigData(this);
		//load all raw data file names based on exploring directory structure for all csv files
		straffDataFileNames = projConfigData.buildFileNameMap(straffDataDirNames);
		prospectExamples = new ConcurrentSkipListMap<String, ConcurrentSkipListMap<String, ProspectExample>>();
		

		mapDims = _dims;
		//raw data from csv's/db
		rawDataArrays = new ConcurrentSkipListMap<String, ArrayList<BaseRawData>>();
		//instantiate maps of ProspectExamples - customers and true prospects (no order event history)
		prospectExamples.put(custExKey,  new ConcurrentSkipListMap<String, ProspectExample>());		
		prospectExamples.put(prspctExKey,  new ConcurrentSkipListMap<String, ProspectExample>());		
		productMap = new ConcurrentSkipListMap<String, ProductExample>();
		
		productsByJpg = new TreeMap<Integer, ArrayList<ProductExample>>();
		productsByJp = new TreeMap<Integer, ArrayList<ProductExample>>();
		//object to manage all jps and jpgroups seen in project
		jpJpgrpMon = new MonitorJpJpgrp(this);
		try {
			straffObjLoaders = new Class[straffClassLdrNames.length];//{Class.forName("SOM_Strafford_PKG.ProspectDataLoader"),  Class.forName("SOM_Strafford_PKG.OrderEventDataLoader"),Class.forName("SOM_Strafford_PKG.OptEventDataLoader"),Class.forName("SOM_Strafford_PKG.LinkEventDataLoader")};
			for (int i=0;i<straffClassLdrNames.length;++i) {straffObjLoaders[i]=Class.forName(straffClassLdrNames[i]);			}
		} catch (Exception e) {dispMessage("SOMMapManager","Constructor","Failed to instance straffObjLoader classes : " + e, MsgCodes.error1);	}
		//to launch loader callable instances
		buildStraffDataLoaders();		
		initData();
	}//ctor	
	//ctor from non-UI stub main
	public SOMMapManager(ExecutorService _th_exec,float[] _dims) {this(null, _th_exec, _dims);}
	
	//build new SOM_MAP map using UI-entered values, then load resultant data
	//with maps of required SOM exe params
	//TODO this will be changed to not pass values from UI, but rather to finalize and save values already set in SOM_MapDat object from UI or other user input
	protected boolean buildNewSOMMap(HashMap<String, Integer> mapInts, HashMap<String, Float> mapFloats, HashMap<String, String> mapStrings){
		//set and save configurations
		boolean runSuccess = projConfigData.setSOM_ExperimentRun(mapInts, mapFloats, mapStrings);
		if(!runSuccess) {
			return false;
		}
		dispMessage("SOMMapManager","buildNewSOMMap","Current projConfigData before dataLoader Call : " + projConfigData.toString(), MsgCodes.info1);
		th_exec.execute(new SOMDataLoader(this, projConfigData));//fire and forget load task to load results from map building
		return true;
	}//buildNewSOMMap	
	
	@SuppressWarnings("unchecked")
	public void initPerJPMapOfNodes() {
		PerJPHiWtMapNodes = new TreeMap[numFtrs];
		for (int i=0;i<PerJPHiWtMapNodes.length; ++i) {PerJPHiWtMapNodes[i] = new TreeMap<Float,ArrayList<SOMMapNode>>(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}});}
	}//

	//put a map node in PerJPHiWtMapNodes per-jp array
	public void setMapNodeFtrStr(SOMMapNode mapNode) {
		TreeMap<Integer, Float> stdFtrMap = mapNode.getCurrentFtrMap(SOMMapManager.useScaledDat);
		for (Integer jpIDX : stdFtrMap.keySet()) {
			Float ftrVal = stdFtrMap.get(jpIDX);
			ArrayList<SOMMapNode> nodeList = PerJPHiWtMapNodes[jpIDX].get(ftrVal);
			if (nodeList== null) {
				nodeList = new ArrayList<SOMMapNode>();
			}
			nodeList.add(mapNode);
			PerJPHiWtMapNodes[jpIDX].put(ftrVal, nodeList);
		}		
	}//setMapNodeFtrStr
	
	//build the callable strafford data loaders list
	private void buildStraffDataLoaders() {
		//to launch loader callable instances
		straffDataLdrFtrs = new ArrayList<Future<Boolean>>();
		//straffDataLoaders = new ArrayList<StraffordDataLoader>();
		straffDataLoaders = new TreeMap<String, StraffordDataLoader>();
		//build constructors
		@SuppressWarnings("rawtypes")
		Class[] args = new Class[] {boolean.class, String.class};//classes of arguments for loader ctor	
		//numStraffDataTypes
		try {
			for (int idx=0;idx<straffDataDirNames.length;++idx) {
				String[] fileNameAra = straffDataFileNames.get(straffDataDirNames[idx]);
				for (int fidx = 0;fidx < fileNameAra.length;++fidx) {
					String dataLoaderKey = fileNameAra[fidx];				
					straffDataLoaders.put(dataLoaderKey,(StraffordDataLoader) straffObjLoaders[idx].getDeclaredConstructor(args).newInstance(true, dataLoaderKey));
				}
			}
		} catch (Exception e) {			e.printStackTrace();}
	
	}//buildStraffDataLoaders

	//set max display list values
	public void setUI_JPMaxVals(int jpGrpLen, int jpLen) {if (win != null) {win.setUI_JPListMaxVals(jpGrpLen, jpLen);}}	
	//only appropriate if using UI
	public void setMapImgClrs(){if (win != null) {win.setMapImgClrs();} else {dispMessage("SOMMapManager","setMapImgClrs","Display window doesn't exist, can't build visualization images; ignoring.", MsgCodes.warning2);}}
	//only appropriate if using UI
	public void initMapAras() {
		if (win != null) {
			dispMessage("SOMMapManager","initMapAras","Initializing per-feature map display to hold : "+ numFtrs+" map images.", MsgCodes.info1);
			win.initMapAras(numFtrs, jpJpgrpMon.getLenJpGrpByIdx());
		} else {dispMessage("SOMMapManager","initMapAras","Display window doesn't exist, can't build map visualization image arrays; ignoring.", MsgCodes.warning2);}}
	
	//only appropriate if using UI
	public void setSaveLocClrImg(boolean val) {if (win != null) { win.setPrivFlags(win.saveLocClrImgIDX,val);}}

	
	//Build map from data by aggregating all training data, building SOM exec string from UI input, and calling OS cmd to run SOM_MAP
	public boolean buildNewMap(SOM_MapDat mapExeDat){
		boolean success = false;
		try {					success = _buildNewMap(mapExeDat);			} 
		catch (IOException e){	dispMessage("SOMMapManager","buildNewMap","Error running map defined by : " + mapExeDat.toString() + " :\n " + e.getMessage(), MsgCodes.error1);	return false;}		
		return success;
	}//buildNewMap
	
	//launch process to exec SOM_MAP
	private boolean _buildNewMap(SOM_MapDat mapExeDat) throws IOException{
		boolean showDebug = getFlag(debugIDX), 
				success = true;		
		dispMessage("SOMMapManager","_buildNewMap","buildNewMap Starting", MsgCodes.info5);
		dispMessage("SOMMapManager","_buildNewMap","Execution String for running manually : \n"+mapExeDat.getDbgExecStr(), MsgCodes.warning2);
		String[] cmdExecStr = mapExeDat.getExecStrAra();
		//if(showDebug){
		dispMessage("SOMMapManager","_buildNewMap","Execution Arguments passed to SOM, parsed by flags and values: ", MsgCodes.info2);
		dispMessageAra(cmdExecStr,"SOMMapManager","_buildNewMap",2, MsgCodes.info2);//2 strings per line, display execution command	
		//}
		//http://stackoverflow.com/questions/10723346/why-should-avoid-using-runtime-exec-in-java		
		String wkDirStr = mapExeDat.getExeWorkingDir(), 
				cmdStr = mapExeDat.getExename(),
				argsStr = "";
		String[] execStr = new String[cmdExecStr.length +1];
		execStr[0] = wkDirStr + cmdStr;
		//for(int i =2; i<cmdExecStr.length;++i){execStr[i-1] = cmdExecStr[i]; argsStr +=cmdExecStr[i]+" | ";}
		for(int i = 0; i<cmdExecStr.length;++i){execStr[i+1] = cmdExecStr[i]; argsStr +=cmdExecStr[i]+" | ";}
		dispMessage("SOMMapManager","_buildNewMap","\nwkDir : "+ wkDirStr + "\ncmdStr : " + cmdStr + "\nargs : "+argsStr, MsgCodes.info1);
		
		//monitor in multiple threads, either msgs or errors
		List<Future<Boolean>> procMsgMgrsFtrs = new ArrayList<Future<Boolean>>();
		List<messageMgr> procMsgMgrs = new ArrayList<messageMgr>(); 
		
		ProcessBuilder pb = new ProcessBuilder(execStr);		
		File wkDir = new File(wkDirStr); 
		pb.directory(wkDir);
		
		String resultIn = "",resultErr = "";
		try {
			final Process process=pb.start();			
			messageMgr inMsgs = new messageMgr(this,process,new InputStreamReader(process.getInputStream()), "Input" );
			messageMgr errMsgs = new messageMgr(this,process,new InputStreamReader(process.getErrorStream()), "Error" );
			procMsgMgrs.add(inMsgs);
			procMsgMgrs.add(errMsgs);			
			procMsgMgrsFtrs = th_exec.invokeAll(procMsgMgrs);for(Future<Boolean> f: procMsgMgrsFtrs) { f.get(); }

			resultIn = inMsgs.getResults(); 
			resultErr = errMsgs.getResults() ;//results of running map TODO save to log?	
			if(resultErr.toLowerCase().contains("error:")) {throw new InterruptedException("SOM Executable aborted");}
		} catch (IOException e) {
			dispMessage("SOMMapManager","_buildNewMap","buildNewMap Process failed with IOException : \n" + e.toString() + "\n\t"+ e.getMessage(), MsgCodes.error1);success = false;
	    } catch (InterruptedException e) {
	    	dispMessage("SOMMapManager","_buildNewMap","buildNewMap Process failed with InterruptedException : \n" + e.toString() + "\n\t"+ e.getMessage(), MsgCodes.error1);success = false;	    	
	    } catch (ExecutionException e) {
	    	dispMessage("SOMMapManager","_buildNewMap","buildNewMap Process failed with ExecutionException : \n" + e.toString() + "\n\t"+ e.getMessage(), MsgCodes.error1);success = false;
		}		
		
		dispMessage("SOMMapManager","_buildNewMap","buildNewMap Finished", MsgCodes.info5);	
		return success;
	}//_buildNewMap
	
	//initialize the SOM-facing data structures - these are used to train/consume a map.
	public void initData(){
		dispMessage("SOMMapManager","initData","Init Called", MsgCodes.info5);
		trainData = new ProspectExample[0];
		testData = new ProspectExample[0];
		inputData = new ProspectExample[0];
		
		nodesWithEx = new TreeMap<ExDataType, HashSet<SOMMapNode>>();
		nodesWithNoEx = new TreeMap<ExDataType, HashSet<SOMMapNode>>();
		for (ExDataType _type : ExDataType.values()) {
			nodesWithEx.put(_type, new HashSet<SOMMapNode>());
			nodesWithNoEx.put(_type, new HashSet<SOMMapNode>());		
		}
		numTrainData = 0;
		numFtrs = 0;
		numInputData = 0;
		dispMessage("SOMMapManager","initData","Init Finished", MsgCodes.info5);
	}//initdata
	
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
	}
	
	public void finalizeExMapNodes(ExDataType _type) {
		HashSet<SOMMapNode> withMap = nodesWithEx.get(_type);
		int typeIDX = _type.getVal();
		for(SOMMapNode node : withMap){		node.finalizeAllBmus(typeIDX);	}
	}
	
	//set all training/testing data save flags to val
	private void setAllTrainDatSaveFlags(boolean val) {
		setFlag(denseTrainDataSavedIDX, val);
		setFlag(sparseTrainDataSavedIDX, val);
		setFlag(testDataSavedIDX, val);		
	}
	
	private void resetValidationMap() {
		prospectExamples.put(prspctExKey,  new ConcurrentSkipListMap<String, ProspectExample>());		
	}
	
	//clear out existing prospect map to be rebuilt
	private void resetProspectMap() {
		prospectExamples.put(custExKey,  new ConcurrentSkipListMap<String, ProspectExample>());		
		//data used by actual SOM for testing/training
		inputData = null;
		testData = null;
		trainData = null;
		setFlag(rawPrspctEvDataProcedIDX, false);
		setFlag(testTrainProdDataBuiltIDX, false);		
		setAllTrainDatSaveFlags(false);
	}//resetProspectMap
	
	//clear out existing product map to be rebuilt
	private void resetProductMap() {
		productMap = new ConcurrentSkipListMap<String, ProductExample>();
		productsByJpg = new TreeMap<Integer, ArrayList<ProductExample>>();
		productsByJp = new TreeMap<Integer, ArrayList<ProductExample>>();
		productData = null;
		setFlag(testTrainProdDataBuiltIDX, false);
		//initialize product-wide aggregations
		ProductExample.initAllStaticProdData();
		setFlag(rawProducDataProcedIDX, false);
		setAllTrainDatSaveFlags(false);
	}//resetProspectMap
	
	//add constructed product example to maps holding products keyed by their constituent jps and jpgs
	public void addProductToJPProductMaps(ProductExample ex) {
		//add to jp and jpg trees
		HashSet<Integer> jpgs = new HashSet<Integer>();
		for (Integer jp : ex.allJPs) {
			ArrayList<ProductExample> exList = productsByJp.get(jp);
			if(exList==null) {exList = new ArrayList<ProductExample>();}
			exList.add(ex);
			productsByJp.put(jp, exList);	
			jpgs.add( jpJpgrpMon.getJpgFromJp(jp));	//record jp groups this product covers
		}
		for (Integer jpg : jpgs) {
			ArrayList<ProductExample> exList = productsByJpg.get(jpg);
			if(exList==null) {exList = new ArrayList<ProductExample>();}
			exList.add(ex);
			productsByJpg.put(jpg, exList);	
		}
	}//addProductToProductMaps	
	
	//use this to rebuild product data only
	public void loadRawProductData(boolean fromCSVFiles) {
		dispMessage("SOMMapManager","loadRawProductData","Start loading and processing raw product only data", MsgCodes.info5);
		if(!fromCSVFiles) {
			dispMessage("SOMMapManager","loadRawProductData","WARNING : SQL-based raw data queries not yet implemented.  Use CSV-based raw data to build training data set instead", MsgCodes.warning1);
			return;
		}
		//no need to make multi-threaded - this will only ever process in a single thread anyway
		//first load all existing data, then overwrite existing data with new product data
		loadAllPreProccedData();	
		if((prospectExamples.get(custExKey).size() == 0) || (productMap.size()==0)){
			dispMessage("SOMMapManager","loadRawProductData","Unable to process only raw product data into preprocessed format - prospect data must exist before hand.", MsgCodes.warning1);
			return;		
		}
		//now load raw product/tcTag data either via csv or sql
		String tcTaggingsFileName = projConfigData.getRawDataLoadInfo(fromCSVFiles,straffDataDirNames[tcTagsIDX],straffDataFileNames.get(straffDataDirNames[tcTagsIDX])[0]);
		boolean[] flags = new boolean[] {fromCSVFiles,straffRawDatUsesJSON[tcTagsIDX], straffRawDatDebugLoad[tcTagsIDX]};
		loadRawDataVals( straffDataDirNames[tcTagsIDX] ,tcTaggingsFileName, flags,tcTagsIDX);
		//now process raw products
		resetProductMap();
		procRawProductData(rawDataArrays.get(straffDataDirNames[tcTagsIDX]));
		//finalize all products and customers
		_finalizeProsProdJpJPGMon("loadRawProductData", "main customer", prospectExamples.get(custExKey));
		//finalize - recalc all processed data in case new products have different JP's present, set flags and save to file
		calcFtrsDiffsMinsAndSave();
		dispMessage("SOMMapManager","loadRawProductData","Finished loading raw product data, processing and saving preprocessed for products data only", MsgCodes.info5);
	}//only load the product/TC-tagging data, process it and then save it to files
		
	//fromCSVFiles : whether loading data from csv files or from SQL calls
	//eventsOnly : only use examples with event data to train
	//append : whether to append to existing data values or to load new data
	public void loadAllRawData(boolean fromCSVFiles) {
		dispMessage("SOMMapManager","loadAllRawData","Start loading and processing raw data", MsgCodes.info5);
		//TODO remove this when SQL support is implemented
		if(!fromCSVFiles) {
			dispMessage("SOMMapManager","loadAllRawData","WARNING : SQL-based raw data queries not yet implemented.  Use CSV-based raw data to build training data set instead", MsgCodes.warning2);
			return;
		}
		boolean canMultiThread=isMTCapable();//this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		if(canMultiThread) {
			buildStraffDataLoaders();
			ArrayList<String> _fileNames;
			String destAraDataKey;
			for (int idx=0;idx<straffDataDirNames.length;++idx) {//build a thread per data type //straffRawDatDebugLoad
				 _fileNames = new ArrayList<String>();
				boolean[] flags = new boolean[] {fromCSVFiles,straffRawDatUsesJSON[idx], straffRawDatDebugLoad[idx]};
				String[] fileNameAra = straffDataFileNames.get(straffDataDirNames[idx]);
				for (int fidx =0;fidx <fileNameAra.length;++fidx) {
					String fullFileName = projConfigData.getRawDataLoadInfo(fromCSVFiles,straffDataDirNames[idx],fileNameAra[fidx]);
					straffDataLoaders.get(fileNameAra[fidx]).setLoadData(this, straffDataDirNames[idx],  fullFileName, flags, straffObjFlagIDXs[idx]);
				}
			}
			//blocking on callables for multithreaded
			try {straffDataLdrFtrs = th_exec.invokeAll(straffDataLoaders.values());for(Future<Boolean> f: straffDataLdrFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		
		} else {
			for (int idx=0;idx<straffDataDirNames.length;++idx) {
				boolean[] flags = new boolean[] {fromCSVFiles,straffRawDatUsesJSON[idx], straffRawDatDebugLoad[idx]};
				String[] fileNameAra = straffDataFileNames.get(straffDataDirNames[idx]);
				for (int fidx =0;fidx <fileNameAra.length;++fidx) {
					String fullFileName = projConfigData.getRawDataLoadInfo(fromCSVFiles,straffDataDirNames[idx],fileNameAra[fidx]);
					loadRawDataVals(straffDataDirNames[idx], fullFileName, flags,idx);
				}
			}
		}
		//process loaded data
		//dbgLoadedData(tcTagsIDX);
		procRawLoadedData();	
		dispMessage("SOMMapManager","loadAllRawData","Finished loading raw data, processing and saving preprocessed data", MsgCodes.info5);
	}//loadAllRawData
	
	//will instantiate specific loader class object and load the data specified by idx, either from csv file or from an sql call described by csvFile
	private void loadRawDataVals(String dataDirTypeName, String fullFileName, boolean[] flags, int idx){//boolean _isFileLoader, String _fileNameAndPath
		//single threaded implementation
		@SuppressWarnings("rawtypes")
		Class[] args = new Class[] {boolean.class, String.class};//classes of arguments for loader ctor		
		try {
			@SuppressWarnings("unchecked")
			StraffordDataLoader loaderObj = (StraffordDataLoader) straffObjLoaders[idx].getDeclaredConstructor(args).newInstance(flags[0], fullFileName);
			loaderObj.setLoadData(this, dataDirTypeName, fullFileName, flags, straffObjFlagIDXs[idx]);
			ArrayList<BaseRawData> datAra = loaderObj.execLoad();
			if(datAra.size() > 0) {
				ArrayList<BaseRawData> existAra = rawDataArrays.get(dataDirTypeName);
				if(existAra != null) {			datAra.addAll(existAra);			} //merge with existing array
				rawDataArrays.put(dataDirTypeName, datAra);
				setFlag(straffObjFlagIDXs[idx], true);			//set flag corresponding to this type of data to be loaded
			}
		} catch (Exception e) {						e.printStackTrace();				}		
		
		//setFlag(straffObjFlagIDXs[idx], true);			//set flag corresponding to this type of data to be loaded
	}//loadRawDataVals	
	
	//load all preprocessed data from default data location
	public void loadAllPreProccedData() { loadAllPreProccedData(projConfigData.getRawDataDesiredDirName());}
	//pass subdir within data directory, or use default
	public void loadAllPreProccedData(String subDir) {
		dispMessage("SOMMapManager","loadAllPreProccedData","Begin loading preprocced data from " + subDir +  "directory.", MsgCodes.info5);
		//load monitor first;save it last
		dispMessage("SOMMapManager","loadMonitorJpJpgrp","Loading MonitorJpJpgrp data", MsgCodes.info1);
		String[] loadSrcFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(subDir, false, "MonitorJpJpgrpData");
		jpJpgrpMon.loadAllData(loadSrcFNamePrefixAra[0]+".csv");
		dispMessage("SOMMapManager","loadMonitorJpJpgrp","Finished loading MonitorJpJpgrp data", MsgCodes.info1);
		dispMessage("SOMMapManager","loadMonitorJpJpgrp",jpJpgrpMon.toString(), MsgCodes.info1);
		//load prospect data
		loadAllProspectData(subDir);		
		
		//load product data
		loadAllProductMapData(subDir);
		finishSOMExampleBuild();
		setFlag(rawPrspctEvDataProcedIDX, true);
		setFlag(rawProducDataProcedIDX, true);
		dispMessage("SOMMapManager","loadAllPreProccedData","Finished loading preprocced data from " + subDir +  "directory.", MsgCodes.info5);
	}//loadAllPreProccedData

	//load "prospect" (customers/prospects with past events) data found in subDir
	private void loadAllProspectData(String subDir) {
		//load prospect data - prospect records with orders
		//clear out current prospect data
		resetProspectMap();		
		loadAllExampleMapData(subDir, custExKey, prospectExamples.get(custExKey));		
	}//loadAllProspectData
	
	//load validation (true prospects) data found in subDir
	private void loadAllValidationData(String subDir) {
		//load validation data - prospect records with no order events
		//clear out current validation data
		resetValidationMap();	
		loadAllExampleMapData(subDir, prspctExKey, prospectExamples.get(prspctExKey));		
	}//loadAllProspectData	
	
	
	//load prospect mapped training data into StraffSOMExamples from disk
	//must reset prospect/validation maps before this is called
	private void loadAllExampleMapData(String subDir, String mapType, ConcurrentSkipListMap<String, ProspectExample> mapToBuild) {
		//perform in multiple threads if possible
		dispMessage("SOMMapManager","loadAllExampleMapData","Loading all " + mapType+ " map data that only have event-based training info", MsgCodes.info5);//" + (eventsOnly ? "that only have event-based training info" : "that have any training info (including only prospect jpg/jp specification)"));
		String[] loadSrcFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(subDir, true, mapType+ "MapSrcData");
		String fmtFile = loadSrcFNamePrefixAra[0]+"_format.csv";
		
		String[] loadRes = fileIO.loadFileIntoStringAra(fmtFile, "Format file loaded", "Format File Failed to load");
		int numPartitions = 0;
		try {
			numPartitions = Integer.parseInt(loadRes[0].split(" : ")[1].trim());
		} catch (Exception e) {e.printStackTrace(); dispMessage("SOMMapManager","loadAllExampleMapData","Due to error with not finding format file : " + fmtFile+ " no data will be loaded.", MsgCodes.error1); return;} 
		
		boolean canMultiThread=isMTCapable();//this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		if(canMultiThread) {			
			List<Future<Boolean>> preProcLoadFtrs = new ArrayList<Future<Boolean>>();
			List<straffCSVDataLoader> preProcLoaders = new ArrayList<straffCSVDataLoader>();
			for (int i=0; i<numPartitions;++i) {				
				preProcLoaders.add(new straffCSVDataLoader(this, i, loadSrcFNamePrefixAra[0]+"_"+i+".csv", "Data file " + i +" loaded", "Data File " + i +" Failed to load", mapToBuild));
			}
			try {preProcLoadFtrs = th_exec.invokeAll(preProcLoaders);for(Future<Boolean> f: preProcLoadFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }					
		} else {//load each file in its own csv
			for (int i=numPartitions-1; i>=0;--i) {
				String dataFile = loadSrcFNamePrefixAra[0]+"_"+i+".csv";
				String[] csvLoadRes = fileIO.loadFileIntoStringAra(dataFile, "Data file " + i +" loaded", "Data File " + i +" Failed to load");
				//ignore first entry - header
				for (int j=1;j<csvLoadRes.length; ++j) {
					String str = csvLoadRes[j];
					int pos = str.indexOf(',');
					String oid = str.substring(0, pos);
					ProspectExample ex = new ProspectExample(this, oid, str);
					mapToBuild.put(oid, ex);			
				}
			}		
		}	
		dispMessage("SOMMapManager","loadAllExampleMapData","Finished loading and preprocessing all local prospect map data and calculating features.  Number of entries in " + mapType + " prospectMap : " + mapToBuild.size(), MsgCodes.info5);
	}//loadAllPropsectMapData	
	
	//load product pre-procced data from tc_taggings source
	private void loadAllProductMapData(String subDir) {
		dispMessage("SOMMapManager","loadAllProductMapData","Loading all product map data", MsgCodes.info5);
		//clear out current product data
		resetProductMap();
		String[] loadSrcFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(subDir, false, "productMapSrcData");
		String dataFile =  loadSrcFNamePrefixAra[0]+".csv";
		String[] csvLoadRes = fileIO.loadFileIntoStringAra(dataFile, "Product Data file loaded", "Product Data File Failed to load");
		//ignore first entry - header
		for (int j=1;j<csvLoadRes.length; ++j) {
			String str = csvLoadRes[j];
			int pos = str.indexOf(',');
			String oid = str.substring(0, pos);
			ProductExample ex = new ProductExample(this, oid, str);
			productMap.put(oid, ex);			
		}
		dispMessage("SOMMapManager","loadAllProductMapData","Finished loading and preprocessing all local prospect map data and calculating features.  Number of entries in productMap : " + productMap.size(), MsgCodes.info5);
	}//loadAllProductMapData
	
	//this will load the product IDs to query on map for prospects from the location specified in the config
	//map these ids to loaded products and then 
	//prodZoneDistThresh is distance threshold to determine outermost map region to be mapped to a specific product
	public void saveProductToProspectsMappings(double prodZoneDistThresh) {
		if (!getFlag(mapDataLoadedIDX)) {	dispMessage("SOMMapManager","mapProductsToProspects","No Mapped data has been loaded or processed; aborting", MsgCodes.error2);		return;}
		if ((productMap == null) || (productMap.size() == 0)) {dispMessage("SOMMapManager","mapProductsToProspects","No products have been loaded or processed; aborting", MsgCodes.error2);		return;}
		dispMessage("SOMMapManager","mapProductsToProspects","Starting load of product to prospecting mapping configuration and building product output mapper", MsgCodes.info5);	
		//get file name of product mapper configuration file
		String prodMapFileName = projConfigData.getFullProdOutMapperInfoFileName();
		int prodDistType = getDistType();
		//builds the output mapper and loads the product IDs to map from config file
		prodMapper = new StraffProdMapOutputBuilder(this, prodMapFileName,th_exec, prodDistType, prodZoneDistThresh);
		dispMessage("SOMMapManager","mapProductsToProspects","Finished load of product to prospecting mapping configuration and building product output mapper | Begin Saving prod-to-prospect mappings to files", MsgCodes.info1);	
		//by here all prods to map have been specified. prodMapBuilder will determine whether multithreaded or single threaded; 
		prodMapper.saveAllSpecifiedProdMappings();		
		dispMessage("SOMMapManager","mapProductsToProspects","Finished Saving prod-to-prospect mappings to files | Begin Saving prospect-to-product mappings to files", MsgCodes.info1);	
		//now save prospect-to-product mappings
		prodMapper.saveAllProspectToProdMappings(inputData);
		dispMessage("SOMMapManager","mapProductsToProspects","Finished Saving prospect-to-product mappings to files", MsgCodes.info5);	
	}//mapProductsToProspects
		
	//write all prospect map data to a csv to be able to be reloaded to build training data from, so we don't have to re-read database every time
	private boolean saveAllExampleMapData(String mapType, ConcurrentSkipListMap<String, ProspectExample> exMap) {
		if ((null != exMap) && (exMap.size() > 0)) {
			dispMessage("SOMMapManager","saveAllExampleMapData","Saving all "+mapType+" map data : " + exMap.size() + " examples to save.", MsgCodes.info5);
			String[] saveDestFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(true, mapType+"MapSrcData");
			ArrayList<ArrayList<String>> csvRes = new ArrayList<ArrayList<String>>();
			ArrayList<String> csvResTmp = new ArrayList<String>();		
			int counter = 0;
			ProspectExample ex1 = exMap.get(exMap.firstKey());
			String hdrStr = ex1.getRawDescColNamesForCSV();
			csvResTmp.add( hdrStr);
			int nameCounter = 0;
			for (ProspectExample ex : exMap.values()) {			
				csvResTmp.add(ex.getRawDescrForCSV());
				++counter;
				if(counter % preProcDatPartSz ==0) {
					dispMessage("SOMMapManager","saveAllExampleMapData","Done Building String Array : " +(nameCounter++), MsgCodes.info1);
					counter = 0;
					csvRes.add(csvResTmp); 
					csvResTmp = new ArrayList<String>();
					csvResTmp.add( hdrStr);
				}
			}
			if(csvResTmp.size() > 1) {	csvRes.add(csvResTmp);}//add last result
			dispMessage("SOMMapManager","saveAllExampleMapData","Finished partitioning " + exMap.size()+ " "+mapType+" records into " + csvRes.size() + " "+mapType+" record files, each holding up to " + preProcDatPartSz + " records.  Start saving files.", MsgCodes.info1);
			//save array of arrays of strings, partitioned and named so that no file is too large
			nameCounter = 0;
			for (ArrayList<String> csvResSubAra : csvRes) {		
				dispMessage("SOMMapManager","saveAllExampleMapData","Saving Pre-procced "+mapType+" data String array : " +nameCounter, MsgCodes.info1);
				fileIO.saveStrings(saveDestFNamePrefixAra[0]+"_"+nameCounter+".csv", csvResSubAra);
				++nameCounter;
			}
			//save the data in a format file
			String[] data = new String[] {"Number of file partitions for " + saveDestFNamePrefixAra[1] +" data : "+ nameCounter + "\n"};
			fileIO.saveStrings(saveDestFNamePrefixAra[0]+"_format.csv", data);		
			dispMessage("SOMMapManager","saveAllExampleMapData","Finished saving all "+mapType+" map data", MsgCodes.info5);
			return true;
		} else {dispMessage("SOMMapManager","saveAllExampleMapData","No "+mapType+" example data to save. Aborting", MsgCodes.error2); return false;}
	}//saveAllExampleMapData	

	private boolean saveAllProductMapData() {
		if ((null != productMap) && (productMap.size() > 0)) {
			dispMessage("SOMMapManager","saveAllProductMapData","Saving all product map data : " + productMap.size() + " examples to save.", MsgCodes.info5);
			String[] saveDestFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(false, "productMapSrcData");
			ArrayList<String> csvResTmp = new ArrayList<String>();		
			ProductExample ex1 = productMap.get(productMap.firstKey());
			String hdrStr = ex1.getRawDescColNamesForCSV();
			csvResTmp.add( hdrStr);	
			for (ProductExample ex : productMap.values()) {			
				csvResTmp.add(ex.getRawDescrForCSV());
			}
			fileIO.saveStrings(saveDestFNamePrefixAra[0]+".csv", csvResTmp);		
			dispMessage("SOMMapManager","saveAllProductMapData","Finished saving all product map data", MsgCodes.info5);
			return true;
		} else {dispMessage("SOMMapManager","saveAllProductMapData","No product example data to save. Aborting", MsgCodes.error2); return false;}
	}//saveAllProductMapData
	
	//save MonitorJpJpgrp
	private void saveMonitorJpJpgrp() {
		dispMessage("SOMMapManager","saveMonitorJpJpgrp","Saving MonitorJpJpgrp data", MsgCodes.info5);
		String[] saveDestFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(false, "MonitorJpJpgrpData");
		jpJpgrpMon.saveAllData(saveDestFNamePrefixAra[0]+".csv");
		dispMessage("SOMMapManager","saveMonitorJpJpgrp","Finished saving MonitorJpJpgrp data", MsgCodes.info5);
	}//saveMonitorJpJpgrp
			
	//build the calculation object, recalculate the features and calc and save the mins, diffs, and all prospect, validation and product map data
	public void calcFtrsDiffsMinsAndSave() {
		dispMessage("SOMMapManager","rebuildCalcObj","Start loading calc object, calculating all feature vectors for prospects and products, calculating mins and diffs, and saving all results.", MsgCodes.info5);
		finishSOMExampleBuild();
		dispMessage("SOMMapManager","rebuildCalcObj","Finished loading calc object, calculating all feature vectors for prospects and products & calculating mins and diffs | Start saving all results.", MsgCodes.info1);
		setFlag(rawPrspctEvDataProcedIDX, true);
		setFlag(rawProducDataProcedIDX, true);
		boolean prspctSuccess = saveAllExampleMapData(custExKey, prospectExamples.get(custExKey));
		boolean validationSuccess = saveAllExampleMapData(prspctExKey, prospectExamples.get(prspctExKey));
		boolean prodSuccess = saveAllProductMapData();		
		if (prspctSuccess || validationSuccess || prodSuccess) { saveMonitorJpJpgrp();}
		dispMessage("SOMMapManager","rebuildCalcObj","Finished loading calc object, calculating all feature vectors for prospects and products, calculating mins and diffs, and saving all results.", MsgCodes.info5);
	}//calcFtrsDiffsMinsAndSave()

	//finish building the prospect map - finalize each prospect example and then perform calculation to derive weight vector
	private void finishSOMExampleBuild() {
		if((prospectExamples.get(custExKey).size() != 0) || (productMap.size() != 0)) {
			setFlag(mapDataLoadedIDX,false);//current map, if there is one, is now out of date, do not use
			//finalize prospects and products - customers are defined by 
			ConcurrentSkipListMap<String, ProspectExample> customerMap = prospectExamples.get(custExKey);
			_finalizeProsProdJpJPGMon("finishSOMExampleBuild", "main customer", customerMap);
			//reset calc analysis objects before building feature vectors to enable new analytic info to be aggregated - only build features on customers
			ftrCalcObj.resetAllCalcObjs();
			Collection<ProspectExample> exs = customerMap.values();
			for (ProspectExample ex : exs) {			ex.buildFeatureVector();	}
			//set state as finished
			ftrCalcObj.finishFtrCalcs();
			dispMessage("SOMMapManager","finishSOMExampleBuild","End buildFeatureVector prospects | Begin buildFeatureVector products", MsgCodes.info1);
			productsByJpg = new TreeMap<Integer, ArrayList<ProductExample>>();
			productsByJp = new TreeMap<Integer, ArrayList<ProductExample>>();
			for (ProductExample ex : productMap.values()) {		ex.buildFeatureVector();  addProductToJPProductMaps(ex);	}
			dispMessage("SOMMapManager","finishSOMExampleBuild","End buildFeatureVector products | Begin calculating diffs and mins", MsgCodes.info1);			
			//dbgDispProductWtSpans()	
			//now get mins and diffs from calc object
			diffsVals = ftrCalcObj.getDiffsBndsAra();
			minsVals = ftrCalcObj.getMinBndsAra();
			dispMessage("SOMMapManager","finishSOMExampleBuild","End calculating diffs and mins | Begin building post-feature calc structs in prospects (i.e. std ftrs) dependent on diffs and mins", MsgCodes.info1);		
			
			exs = customerMap.values();
			for (ProspectExample ex : exs) {			ex.buildPostFeatureVectorStructs();		}//this builds std ftr vector for prospects, once diffs and mins are set - not necessary for products, buildFeatureVector for products builds std ftr vec
			dispMessage("SOMMapManager","finishSOMExampleBuild","End building post-feature calc structs in prospects (i.e. std ftrs)", MsgCodes.info5);			
				
		} else {		dispMessage("SOMMapManager","finishSOMExampleBuild","No prospects or products loaded to calculate.", MsgCodes.warning2);	}
	}//finishSOMExampleBuild
	
	//called to process analysis data
	public void processCalcAnalysis() {	if (ftrCalcObj != null) {ftrCalcObj.finalizeCalcAnalysis();}}
	
	//once map is built, find bmus on map for each product
	public void setProductBMUs() {
		dispMessage("SOMMapManager","setProductBMUs","Start Mapping products to best matching units.", MsgCodes.info5);
		boolean canMultiThread=isMTCapable();//this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		if(canMultiThread) {
			List<Future<Boolean>> prdcttMapperFtrs = new ArrayList<Future<Boolean>>();
			List<mapProductDataToBMUs> prdcttMappers = new ArrayList<mapProductDataToBMUs>();
			int numForEachThrd = ((int)((productData.length-1)/(1.0f*numUsableThreads))) + 1;
			//use this many for every thread but last one
			int stIDX = 0;
			int endIDX = numForEachThrd;				
			for (int i=0; i<(numUsableThreads-1);++i) {				
				prdcttMappers.add(new mapProductDataToBMUs(this,stIDX, endIDX, productData, i, useChiSqDist));
				stIDX = endIDX;
				endIDX += numForEachThrd;
			}
			//last one probably won't end at endIDX, so use length
			prdcttMappers.add(new mapProductDataToBMUs(this,stIDX, productData.length, productData, numUsableThreads-1, useChiSqDist));
			try {prdcttMapperFtrs = th_exec.invokeAll(prdcttMappers);for(Future<Boolean> f: prdcttMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		
		} else {//for every product find closest map node
			TreeMap<Double, ArrayList<SOMMapNode>> mapNodes;
			if (useChiSqDist) {	
				for (int i=0;i<productData.length;++i) {
					mapNodes = productData[i].findBMUFromNodes_ChiSq_Excl(MapNodes, curMapFtrType); 
					productData[i].setMapNodesStruct(ProductExample.SharedFtrsIDX, mapNodes);
					mapNodes = productData[i].findBMUFromNodes_ChiSq(MapNodes, curMapFtrType); 
					productData[i].setMapNodesStruct(ProductExample.AllFtrsIDX, mapNodes);
				}
			} else {				
				for (int i=0;i<productData.length;++i) {
					mapNodes = productData[i].findBMUFromNodes_Excl(MapNodes,  curMapFtrType); 
					productData[i].setMapNodesStruct(ProductExample.SharedFtrsIDX, mapNodes);
					mapNodes = productData[i].findBMUFromNodes(MapNodes,  curMapFtrType); 
					productData[i].setMapNodesStruct(ProductExample.AllFtrsIDX, mapNodes);
				}
			}	
		}
		//go through every product and attach prod to bmu - needs to be done synchronously because don't want to concurrently modify bmus from 2 different prods
		dispMessage("SOMMapManager","setProductBMUs","Finished finding bmus for all product data. Start adding product data to appropriate bmu's list.", MsgCodes.info1);
		_finalizeBMUProcessing(productData, ExDataType.Product);
		
		dispMessage("SOMMapManager","setProductBMUs","Finished Mapping products to best matching units.", MsgCodes.info5);
	}//setProductBMUs

	//build and save feature-based reports for all examples, products and map nodes
	public void buildFtrBasedRpt() {
		if(!getFlag(testTrainProdDataBuiltIDX)) {return;}
		dispMessage("SOMMapManager","buildFtrBasedRpt","Start Building feature weight reports for all examples --NOT YET IMPLEMENTED-- .", MsgCodes.info5);
		//all underlying code in SOMExample has been completed
		boolean canMultiThread=isMTCapable();//this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		if(canMultiThread) {
		
		} else {

			for (Tuple<Integer, Integer> nodeLoc : MapNodes.keySet()) {
				SOMExample ex = MapNodes.get(nodeLoc);
				
			}
			for (int idx=0; idx<inputData.length;++idx) {
				SOMExample ex = inputData[idx];
				
			}
			
			for (int idx=0; idx<productData.length;++idx) {
				SOMExample ex = productData[idx];
				
			}
			
		}			
	
		dispMessage("SOMMapManager","buildFtrBasedRpt","Finished Building feature weight reports for all examples.", MsgCodes.info5);

	}//buildFtrBasedRpt
	
	
	//once map is built, find bmus on map for each test data example
	public void setTestBMUs() {
		dispMessage("SOMMapManager","setTestBMUs","Start Mapping test (held out) data to best matching units.", MsgCodes.info5);
		boolean canMultiThread=isMTCapable();//this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		if(canMultiThread) {
			List<Future<Boolean>> testMapperFtrs = new ArrayList<Future<Boolean>>();
			List<mapTestDataToBMUs> testMappers = new ArrayList<mapTestDataToBMUs>();
			int numForEachThrd = ((int)((testData.length-1)/(1.0f*numUsableThreads))) + 1;
			//use this many for every thread but last one
			int stIDX = 0;
			int endIDX = numForEachThrd;		
			for (int i=0; i<(numUsableThreads-1);++i) {				
				testMappers.add(new mapTestDataToBMUs(this,stIDX, endIDX,  testData, i, useChiSqDist));
				stIDX = endIDX;
				endIDX += numForEachThrd;
			}
			//last one probably won't end at endIDX, so use length
			testMappers.add(new mapTestDataToBMUs(this,stIDX, testData.length, testData, numUsableThreads-1, useChiSqDist));
			try {testMapperFtrs = th_exec.invokeAll(testMappers);for(Future<Boolean> f: testMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		
		} else {//for every product find closest map node
			if (useChiSqDist) {			for (int i=0;i<testData.length;++i) {	testData[i].findBMUFromNodes_ChiSq(MapNodes, curMapFtrType);		}}			
			else {						for (int i=0;i<testData.length;++i) {	testData[i].findBMUFromNodes(MapNodes, curMapFtrType);		}	}			
		}
		//go through every test example and attach prod to bmu - needs to be done synchronously because don't want to concurrently modify bmus from 2 different test examples
		dispMessage("SOMMapManager","setTestBMUs","Finished finding bmus for all test data. Start adding test data to appropriate bmu's list.", MsgCodes.info1);
		_finalizeBMUProcessing(testData, ExDataType.ProspectTesting);
		
		dispMessage("SOMMapManager","setTestBMUs","Finished Mapping test data to best matching units.", MsgCodes.info5);
	}//setProductBMUs
	
	private void _finalizeBMUProcessing(SOMExample[] _exs, ExDataType _type) {
		int val = _type.getVal();
		for(SOMMapNode mapNode : MapNodes.values()){mapNode.clearBMUExs(val);addExToNodesWithNoExs(mapNode, _type);}	
		for (int i=0;i<_exs.length;++i) {	
			SOMExample ex = _exs[i];
			ex.bmu.addExToBMUs(ex);	
			addExToNodesWithExs(ex.bmu, _type);
		}
		filterExFromNoEx(_type);		//clear out all nodes that have examples from struct holding no-example map nodes
		finalizeExMapNodes(_type);		
	}//_finalizeBMUProcessing
	
	//debug - display spans of weights of all features in products after products are built
	public void dbgDispProductWtSpans() {
		//debug - display spans of weights of all features in products
		String[] prodExVals = ProductExample.getMinMaxDists();
		dispMessageAra(prodExVals,"SOMMapManager", "SOMMapManager::finishSOMExampleBuild : spans of all product ftrs seen", 1, MsgCodes.info1);		
	}//dbgDispProductWtSpans()
	
	public String getDataTypeNameFromCurFtrType() {return getDataTypeNameFromInt(curMapFtrType);}	
	//useUnmoddedDat = 0, useScaledDat = 1, useNormedDat
	public String getDataTypeNameFromInt(int dataFrmt) {
		switch(dataFrmt) {
		case useUnmoddedDat : {return "unModFtrs";}
		case useScaledDat : {return "stdFtrs";}
		case useNormedDat : {return "normFtrs";}
		default : {return null;}		//unknown data frmt type
		}
	}//getDataTypeNameFromInt
	
	public String getDataDescFromCurFtrType()  {return getDataDescFromInt(curMapFtrType);}
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

	//using the passed map, build the testing and training data partitions and save them to files
	private void buildTestTrainFromProspectMap(float trainTestPartition, boolean isBuildingNewMap) {
		dispMessage("SOMMapManager","buildTestTrainFromInput","Building Training and Testing Partitions.", MsgCodes.info5);
		//set partition size in project config
		projConfigData.setTrainTestPartition(trainTestPartition);
		//set inputdata array to be all prospect map examples
		inputData = prospectExamples.get(custExKey).values().toArray(new ProspectExample[0]);		
		//shuffleProspects(ProspectExample[] _list, long seed) -- performed in place - use same key so is reproducible training, always has same shuffled order
		inputData = shuffleProspects(inputData, 12345L);
		
		numTrainData = (int) (inputData.length * trainTestPartition);			
		numTestData = inputData.length - numTrainData;
		
		//build train and test partitions
		trainData = new ProspectExample[numTrainData];	
		dispMessage("SOMMapManager","buildTestTrainFromInput","# of training examples : " + numTrainData + " inputData size : " + inputData.length, MsgCodes.info3);
		for (int i=0;i<trainData.length;++i) {trainData[i]=inputData[i];trainData[i].setIsTrainingDataIDX(true, i);}
		testData = new ProspectExample[numTestData];
		for (int i=0;i<testData.length;++i) {testData[i]=inputData[i+numTrainData];testData[i].setIsTrainingDataIDX(false, i);}
		//build array of produt examples based on product map
		productData = productMap.values().toArray(new ProductExample[0]);
		setFlag(testTrainProdDataBuiltIDX,true);
		//dbg disp
		//for(ProductExample prdEx : productData) {dispMessage("SOMMapManager","buildTestTrainFromInput",prdEx.toString());}
		//build file names, including info for data type used to train map
		if (isBuildingNewMap) {//will save results to new directory
			projConfigData.buildDateTimeStrAraAndDType(getDataTypeNameFromCurFtrType());
			projConfigData.setSOM_ExpFileNames(inputData.length, numTrainData, numTestData);
			projConfigData.launchTestTrainSaveThrds(th_exec, curMapFtrType);				//save testing and training data
		} //else {		//will load results from previously run experiment
			//projConfigData.setSOM_UsePreBuilt();		
		//}		
		dispMessage("SOMMapManager","buildTestTrainFromInput","Finished Building Training and Testing Partitions.  Train size : " + numTrainData+ " Testing size : " + numTestData + " Product data size : " +productData.length +".", MsgCodes.info5);
	}//buildTestTrainFromInput
	
	//this will load the default map training configuration
	public void loadSOMConfig() {		
		projConfigData.loadDefaultSOMExp_Config();
		
	}//loadSOMConfig
	
	//load preproc csv and build training and testing partitions
	public void loadPreprocAndBuildTestTrainPartitions(float trainTestPartition) {
		dispMessage("SOMMapManager","loadPreprocAndBuildTestTrainPartitions","Start Loading all CSV Build Data to train map.", MsgCodes.info5);
		loadAllPreProccedData();
		//build SOM data
		buildTestTrainFromProspectMap(trainTestPartition, true);	
		dispMessage("SOMMapManager","loadPreprocAndBuildTestTrainPartitions","Saving data to training file : Starting to save training/testing data partitions ", MsgCodes.info1);
		//save mins/maxes so this file data be reconstructed
		//save diffs and mins - csv files with each field value sep'ed by a comma
		//boundary region for training data
		String diffStr = "", minStr = "";
		for(int i =0; i<minsVals.length; ++i){
			minStr += String.format("%1.7g", minsVals[i]) + ",";
			diffStr += String.format("%1.7g", diffsVals[i]) + ",";
		}
		String minsFileName = projConfigData.getSOMMapMinsFileName();
		String diffsFileName = projConfigData.getSOMMapDiffsFileName();				
		fileIO.saveStrings(minsFileName,new String[]{minStr});		
		fileIO.saveStrings(diffsFileName,new String[]{diffStr});		
		dispMessage("SOMMapManager","loadPreprocAndBuildTestTrainPartitions","Strafford Prospects Mins and Diffs Files Saved", MsgCodes.info1);	
		dispMessage("SOMMapManager","loadPreprocAndBuildTestTrainPartitions","Finished Loading all CSV Build Data to train map.", MsgCodes.info5);
	}//loadPreprocAndBuildTestTrainPartitions
	
	//load the data used to build a map as well as existing map results
	//NOTE this may break if different data is used to build the map than the current data being loaded
	public void loadPretrainedExistingMap() {
		//load data into preproc  -this must be data used to build map
		loadAllPreProccedData();
		
		projConfigData.setSOM_UsePreBuilt();	
		//set project test/train partitions based on preset data values
		//projConfigData.setTrainTestPartitionDBG();
		//build data partitions - use partition size set via constants in debug
		buildTestTrainFromProspectMap(projConfigData.getTrainTestPartition(), false);	
		dispMessage("SOMMapManager","loadPretrainedExistingMap","Current projConfigData before dataLoader Call : " + projConfigData.toString(), MsgCodes.info3);
		th_exec.execute(new SOMDataLoader(this,projConfigData));//fire and forget load task to load		
	}//dbgBuildExistingMap
		
	//this will set the current jp->jpg data maps based on passed prospect data map
	//When acquiring new data, this must be performed after all data is loaded, but before
	//the prospect data is finalized and actual map is built due to the data finalization 
	//requiring a knowledge of the entire dataset to build weights appropriately
	private void setJPDataFromExampleData(ConcurrentSkipListMap<String, ProspectExample> map) {
		//object to manage all jps and jpgroups seen in project
		jpJpgrpMon.setJPDataFromExampleData(map, prospectExamples.get(prspctExKey), productMap);		
		numFtrs = jpJpgrpMon.getNumFtrs();
		//rebuild calc object since feature terrain might have changed 
		String calcFullFileName = projConfigData.getFullCalcInfoFileName(); 
		//make/remake calc object - reads from calcFullFileName data file
		ftrCalcObj = new StraffWeightCalc(this, calcFullFileName, jpJpgrpMon);
	}//setJPDataFromProspectData	

	//process all events into training examples
	private void procRawEventData(ConcurrentSkipListMap<String, ProspectExample> tmpProspectMap,ConcurrentSkipListMap<String, ArrayList<BaseRawData>> dataArrays, boolean saveBadRecs) {			
		dispMessage("SOMMapManager","procRawEventData","Start processing raw event data.", MsgCodes.info5);
		String dataName;
		int dataTypeIDX;
		//only iterate through event records in dataArraysMap
		for (int i = 0; i <straffEventDataIDXs.length;++i) {//for each event
			dataTypeIDX = straffEventDataIDXs[i];
			dataName = straffDataDirNames[dataTypeIDX];
			ArrayList<BaseRawData> events = dataArrays.get(dataName);
			//derive event type from file name?
			String eventType = dataName.split("_")[0];		
			String eventBadFName = projConfigData.getBadEventFName(dataName);//	
			ArrayList<String> badEventOIDs = new ArrayList<String>();
			HashSet<String> uniqueBadEventOIDs = new HashSet<String>();
			
			for (BaseRawData obj : events) {
				ProspectExample ex = tmpProspectMap.get(obj.OID);			//event has OID referencing prospect/customer record in prospect table
				if (ex == null) {
					if (saveBadRecs) {//means no prospect object corresponding to the OID in this event
						badEventOIDs.add(obj.OID);
						uniqueBadEventOIDs.add(obj.OID);
					}
					continue;}
				ex.addEventObj(obj, dataTypeIDX);//can't multi-thread this based on event type because prospect objects might collide and they aren't thread safe
			}//for every actual event - verify every event references an actual object using its OID field
	
			if (saveBadRecs && (badEventOIDs.size() > 0)) {
				fileIO.saveStrings(eventBadFName, uniqueBadEventOIDs.toArray(new String[0]));		
				dispMessage("SOMMapManager","procRawEventData","# of "+eventType+" events without corresponding prospect records : "+badEventOIDs.size() + " out of " +events.size() + " total "+eventType+" events | # Unique bad "+eventType+" event prospect OID refs (missing OIDs in prospect) : "+uniqueBadEventOIDs.size(), MsgCodes.info3);
			} else {
				dispMessage("SOMMapManager","procRawEventData","No "+eventType+" events without corresponding prospect records found after processing "+ events.size() +" events.", MsgCodes.info3);				
			}
		}//for each event type
		dispMessage("SOMMapManager","procRawEventData","Finished processing raw event data.", MsgCodes.info5);
	}//procRawEventData
	
	//take existing map and use U-Matrix-distances to determine segment membership.Large distances > thresh (around .7) mean nodes are on a boundary
	public void buildSegmentsOnMap() {
		if (nodeInSegDistThresh == mapMadeWithSegThresh) {return;}
		if ((MapNodes == null) || (MapNodes.size() == 0)) {return;}
		dispMessage("SOMMapManager","buildSegmentsOnMap","Started building cluster map", MsgCodes.info5);	
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
		dispMessage("SOMMapManager","buildSegmentsOnMap","Finished building cluster map", MsgCodes.info5);			
	}//buildSegmentsOnMap()

	
	//convert raw tc taggings table data to product examples
	private void procRawProductData(ArrayList<BaseRawData> tcTagRawData) {
		dispMessage("SOMMapManager","procRawProductData","Starting to process Raw Product Data.", MsgCodes.info5);
		for (BaseRawData tcDat : tcTagRawData) {
			ProductExample ex = new ProductExample(this, (TcTagData)tcDat);
			productMap.put(ex.OID, ex);
		}
		dispMessage("SOMMapManager","procRawProductData","Finished processing  : " + tcTagRawData.size()+ " raw records.", MsgCodes.info5);		
	}//procRawProductData
	
	//this will scale unmodified ftr data - scaled or normalized data does not need this
	public int scaleUnfrmttedFtrData(Float ftrVal, Integer jpIDX) {
		//what to use to scale features - if dataFmt == 0 then need to use mins/maxs, otherwise ftrs can be just treated as if they are scaled already - either normalized or standardized will be between 0-1 for all ftr values
		Float min = this.minsVals[jpIDX], 
				diffs = this.diffsVals[jpIDX],
				calcVal = (ftrVal - min)/diffs;
		return Math.round(255 * calcVal);
	}//scaleUnfrmttedFtrData
	
	//manage the finalizing of the prospects in tmpProspectMap and the loaded products
	private void _finalizeProsProdJpJPGMon(String calledFromMethod, String prospectMapName, ConcurrentSkipListMap<String, ProspectExample> tmpProspectMap) {
		//code pulled from finalize; finalize builds each example's occurence structures, which describe the jp-jpg relationships found in the example
		dispMessage("SOMMapManager",calledFromMethod+"->_finalizeProsProdJpJPGMon","Begin initial finalize of "+ prospectMapName +" customer map to aggregate all JPs and (potentially) determine which records are valid training examples", MsgCodes.info1);		
		//finalize each customer - this will aggregate all the jp's that are seen, as well as finding all records that are bad due to having a 0 ftr vector
		for (ProspectExample ex : tmpProspectMap.values()) {			ex.finalizeBuild();		}		
		ConcurrentSkipListMap<String, ProspectExample> trueProspects = prospectExamples.get(prspctExKey);
		if(trueProspects.size() != 0) {
			dispMessage("SOMMapManager",calledFromMethod+"->_finalizeProsProdJpJPGMon","End initial finalize of "+ prospectMapName +" customer map | Begin initial finalize of true prospects map to aggregate all JPs", MsgCodes.info1);			
			Collection<ProspectExample> truPspctExs = trueProspects.values();
			for (ProspectExample ex : truPspctExs) {			ex.finalizeBuild();		}		
			dispMessage("SOMMapManager",calledFromMethod+"->_finalizeProsProdJpJPGMon","End initial finalize of true prospects map | Begin initial finalize of product map to aggregate all JPs", MsgCodes.info1);	
		} else {
			dispMessage("SOMMapManager",calledFromMethod+"->_finalizeProsProdJpJPGMon","End initial finalize of "+ prospectMapName +" customer map | Begin initial finalize of product map to aggregate all JPs", MsgCodes.info1);	
		}
		//finalize build for all products - aggregates all jps seen in product
		for (ProductExample ex : productMap.values()){		ex.finalizeBuild();		}		
		//must rebuild this because we might not have same jp's
		dispMessage("SOMMapManager",calledFromMethod+"->_finalizeProsProdJpJPGMon","End initial finalize of product map | Begin setJPDataFromExampleData from prospect map", MsgCodes.info1);
		//we need the jp-jpg counts and relationships dictated by the data by here.
		setJPDataFromExampleData(tmpProspectMap);
		dispMessage("SOMMapManager",calledFromMethod+"->_finalizeProsProdJpJPGMon","End setJPDataFromExampleData from "+ prospectMapName +" prospect map", MsgCodes.info1);
	}//_finalizeProsProdJpJPGMon
	
	//this will display debug-related 
	private void dispDebugEventPresenceData(int[] countsOfBoolResOcc, int[] countsOfBoolResEvt) {
		for(int i=0;i<ProspectExample.eventMapTypeKeys.length;++i) {
			dispMessage("SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect OCC records with "+ ProspectExample.eventMapTypeKeys[i]+" events : " + countsOfBoolResOcc[i] , MsgCodes.info1);				
			dispMessage("SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect Event records with "+ ProspectExample.eventMapTypeKeys[i]+" events : " + countsOfBoolResEvt[i] , MsgCodes.info1);	
			if(i==1) {
				dispMessage("SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect records with global opt : " + StraffEvntTrainData.numOptAllIncidences , MsgCodes.info1);	
				dispMessage("SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect records with positive global opt : " + StraffEvntTrainData.numPosOptAllIncidences , MsgCodes.info1);	
				dispMessage("SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect records with non-positive global opt : " + StraffEvntTrainData.numNegOptAllIncidences , MsgCodes.info1);	
			}
			dispMessage("SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData"," " , MsgCodes.info1);				
		}		
		dispMessage("SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect records with OCC non-source events : " + countsOfBoolResOcc[countsOfBoolResOcc.length-1] , MsgCodes.info1);			
		dispMessage("SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect records with Evt non-source events : " + countsOfBoolResEvt[countsOfBoolResEvt.length-1] , MsgCodes.info1);			
		
	}//dispDebugEventPresenceData
	
	//check and increment relevant counters if specific events are found in a particular example
	private void dbgEventInExample(ProspectExample ex, int[] countsOfBoolResOcc, int[] countsOfBoolResEvt) {
		boolean[] exOCCStatus, exEvtStatus;
		exOCCStatus = ex.getExampleStatusOcc();
		exEvtStatus = ex.getExampleStatusEvt();
		for(int i=0;i<exOCCStatus.length;++i) {
			if(exOCCStatus[i]) {++countsOfBoolResOcc[i];}
			if(exEvtStatus[i]) {++countsOfBoolResEvt[i];}
		}
	}//dbgEventInExample
		
	//this function will take all raw loaded prospects and partition them into customers and true prospects
	//it determines what the partition/definition is for a "customer" which is used to train the map, and a "true prospect" which is polled against the map to find product membership.
	//typeOfEventsForCustomer : int corresponding to what kind of events define a customer and what defines a prospect.  
	//    0 : cust has order event, prospect does not but has source and possibly other events
	//    1 : cust has some non-source event, prospect does not have customer event but does have source event
	private void _buildCustomerAndProspectMaps(ConcurrentSkipListMap<String, ProspectExample> tmpProspectMap) {
		//whether or not to display total event membership counts across all examples to console
		boolean dispDebugEventMembership = true;
		
		int typeOfEventsForCustomer = projConfigData.getTypeOfEventsForCustAndProspect();

		int[] countsOfBoolResOcc = new int[ProspectExample.eventMapTypeKeys.length+1],
			countsOfBoolResEvt = new int[ProspectExample.eventMapTypeKeys.length+1];		//all types of events supported + 1
		ConcurrentSkipListMap<String, ProspectExample> 
			customerPrspctMap = prospectExamples.get(custExKey),			//map of customers to build
			truePrspctMap = prospectExamples.get(prspctExKey);				//map of true prospects to build
		
		switch(typeOfEventsForCustomer) {
		case 0 : {		// cust has order event, prospect does not but has source and possibly other events
			for (String OID : tmpProspectMap.keySet()) {		
				ProspectExample ex = tmpProspectMap.get(OID);
				if(dispDebugEventMembership) {dbgEventInExample(ex, countsOfBoolResOcc, countsOfBoolResEvt);}
				if (ex.isTrainablePastCustomer()) {			customerPrspctMap.put(OID, ex);		} 			//training data - has valid feature vector and past order events
				else if (ex.isTrueProspect()) {				truePrspctMap.put(OID, ex);		} 				//no past order events but has valid source event data
			}
			break;}
		case 1 : {		//cust has some non-source event, prospect does not have customer event but does have source event
			for (String OID : tmpProspectMap.keySet()) {		
				ProspectExample ex = tmpProspectMap.get(OID);
				if(dispDebugEventMembership) {dbgEventInExample(ex, countsOfBoolResOcc, countsOfBoolResEvt);}
				if (ex.hasNonSourceEvents()) {					customerPrspctMap.put(OID, ex);		} 			//training data - has valid feature vector and any non-source event data
				else if (ex.hasOnlySourceEvents()) {			truePrspctMap.put(OID, ex);		} 				//only has source data
			}
			break;}
			default : { 
				return;}
		}//switch
		//eventMapTypeKeys 
		//display debug info relating to counts of different types of events present in given examples
		if(dispDebugEventMembership) {dispDebugEventPresenceData(countsOfBoolResOcc, countsOfBoolResEvt);}
		//display customers and true prospects info
		dispMessage("SOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","Raw Records Unique OIDs presented : " + tmpProspectMap.size(), MsgCodes.info3);
		dispMessage("SOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","# prospect records that should be used as valiation records (have jp/jpg set in prospect data but no events) : " + truePrspctMap.size(), MsgCodes.info1);	
		dispMessage("SOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","# prospect records found with trainable event-based info : " + customerPrspctMap.size(), MsgCodes.info3);

	}//_buildCustomerAndProspectMaps
	
	
	//this will go through all the prospects and events and build a map of prospectExample keyed by prospect OID and holding all the known data
	private void procRawLoadedData() {
		resetProspectMap();		
		resetValidationMap();		
		dispMessage("SOMMapManager","procRawLoadedData","Start Processing all loaded raw data", MsgCodes.info5);
		if (!(getFlag(prospectDataLoadedIDX) && getFlag(optDataLoadedIDX) && getFlag(orderDataLoadedIDX))){//not all data loaded, don't process 
			dispMessage("SOMMapManager","procRawLoadedData","Can't build data examples until prospect, opt event and order event data is all loaded", MsgCodes.warning2);
			return;
		}
		//build prospectMap - first get prospect data and add to map
		ArrayList<BaseRawData> prospects = rawDataArrays.get(straffDataDirNames[prspctIDX]);
		ConcurrentSkipListMap<String, ProspectExample> tmpProspectMap = new ConcurrentSkipListMap<String, ProspectExample>(), 
				customerPrspctMap = prospectExamples.get(custExKey);
		for (BaseRawData prs : prospects) {
			//prospectMap is empty here
			ProspectExample ex = customerPrspctMap.get(prs.OID);
			if (ex == null) {ex = new ProspectExample(this, (prospectData) prs);}
			else {dispMessage("SOMMapManager","procRawLoadedData","Prospect with OID : "+  prs.OID + " existed in map already and was replaced.", MsgCodes.warning2);}
			tmpProspectMap.put(ex.OID, ex);
		}		
		//add all events to prospects
		procRawEventData(tmpProspectMap, rawDataArrays, true);		
		//now handle products - found in tc_taggings table
		resetProductMap();
		procRawProductData(rawDataArrays.get(straffDataDirNames[tcTagsIDX]));		
		//now handle loaded jp and jpgroup data
		jpJpgrpMon.setJpJpgrpNames(rawDataArrays.get(straffDataDirNames[jpDataIDX]),rawDataArrays.get(straffDataDirNames[jpgDataIDX]));		
		//to free up memory before we build feature weight vectors; get rid of rawDataArrays used to hold original data read from files		
		rawDataArrays = null;			
		//finalize around temp map - finalize builds each example's occurrence structures, which describe the jp-jpg relationships found in the example
		_finalizeProsProdJpJPGMon("procRawLoadedData", "temp", tmpProspectMap);
	
		//build actual customer and validation maps using rules defining what is a customer (probably means having an order event) and what is a "prospect" (probably not having an order event)
		_buildCustomerAndProspectMaps(tmpProspectMap);

		
		
		//finalize - recalc all processed data in case new products have different JP's present, set flags and save to file
		calcFtrsDiffsMinsAndSave();
		dispMessage("SOMMapManager","procRawLoadedData","Finished processing all loaded data", MsgCodes.info5);
	}//procRawLoadedData

	//return interpolated feature vector on map at location given by x,y, where x,y is float location of map using mapnodes as integral locations
	public TreeMap<Integer, Float> getInterpFtrs(float x, float y){
		float xInterp = (x+mapNodeCols) %1, yInterp = (y+mapNodeRows) %1;
		int xInt = (int) Math.floor(x+mapNodeCols)%mapNodeCols, yInt = (int) Math.floor(y+mapNodeRows)%mapNodeRows, xIntp1 = (xInt+1)%mapNodeCols, yIntp1 = (yInt+1)%mapNodeRows;		//assume torroidal map		
		//always compare standardized feature data in test/train data to standardized feature data in map
		TreeMap<Integer, Float> LowXLowYFtrs = MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt)).getCurrentFtrMap(curMapFtrType), LowXHiYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xInt,yIntp1)).getCurrentFtrMap(curMapFtrType),
				 HiXLowYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yInt)).getCurrentFtrMap(curMapFtrType),  HiXHiYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yIntp1)).getCurrentFtrMap(curMapFtrType);
		try{
			TreeMap<Integer, Float> ftrs = interpTreeMap(interpTreeMap(LowXLowYFtrs, LowXHiYFtrs,yInterp,1.0f),interpTreeMap(HiXLowYFtrs, HiXHiYFtrs,yInterp,1.0f),xInterp,255.0f);	
			return ftrs;
		} catch (Exception e){
			dispMessage("mySOMMapUIWin","getInterpFtrs","Exception triggered in mySOMMapUIWin::getInterpFtrs : \n"+e.toString() + "\n\tMessage : "+e.getMessage(), MsgCodes.error1);
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
			dispMessage("mySOMMapUIWin","getInterpFtrs","Exception triggered in mySOMMapUIWin::getInterpFtrs : \n"+e.toString() + "\n\tMessage : "+e.getMessage(), MsgCodes.error1 );
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
			dispMessage("mySOMMapUIWin","getInterpFtrs","Exception triggered in mySOMMapUIWin::getInterpFtrs : \n"+e.toString() + "\n\tMessage : "+e.getMessage(), MsgCodes.error1 );
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
			dispMessage("mySOMMapUIWin","getInterpFtrs","Exception triggered in mySOMMapUIWin::getInterpFtrs : \n"+e.toString() + "\n\tMessage : "+e.getMessage() , MsgCodes.error1);
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
		
	//show first numToShow elemens of array of BaseRawData, either just to console or to applet window
	private void dispRawDataAra(ArrayList<BaseRawData> sAra, int numToShow) {
		if (sAra.size() < numToShow) {numToShow = sAra.size();}
		for(int i=0;i<numToShow; ++i){dispMessage("SOMMapManager","dispRawDataAra",sAra.get(i).toString(), MsgCodes.info4);}
	}	
	
	public void dbgShowAllRawData() {
		int numToShow = 10;
		for (String key : rawDataArrays.keySet()) {
			dispMessage("SOMMapManager","dbgShowAllRawData","Showing first "+ numToShow + " records of data at key " + key, MsgCodes.info4);
			dispRawDataAra(rawDataArrays.get(key), numToShow);
		}
	}//showAllRawData
	//debugging function to display all unique jps seen in data
	public void dbgShowUniqueJPsSeen() {
		jpJpgrpMon.dbgShowUniqueJPsSeen();
	}//dbgShowUniqueJPsSeen
	
	public void dbgDispKnownJPsJPGs() {
		jpJpgrpMon.dbgDispKnownJPsJPGs();
	}//dbgDispKnownJPsJPGs
	
	//display current calc function's equation coefficients for each JP
	public void dbgShowCalcEqs() {
		if (null == ftrCalcObj) {	dispMessage("SOMMapManager","dbgShowCalcEqs","No calc object made to display.", MsgCodes.warning1);return;	}
		dispMessage("SOMMapManager","dbgShowCalcEqs","Weight Calculation Equations : \n"+ftrCalcObj.toString(), MsgCodes.info1);		
	}
	
	private void dbgLoadedData(int idx) {
		ArrayList<BaseRawData> recs = rawDataArrays.get(straffDataDirNames[idx]);
		for  (BaseRawData rec : recs) {if(rec.rawJpMapOfArrays.size() > 1) {dispMessage("SOMMapManager","dbgLoadedData",straffDataDirNames[idx] + " : " + rec.toString(), MsgCodes.info1);}}			
	}
	
	public void dbgShowJpJpgrpData() {
		dispMessage("SOMMapManager","dbgShowJpJpgrpData","Showing current jpJpg Data : \n"+jpJpgrpMon.toString(), MsgCodes.info1);
	}
	

	//build a string to display an array of floats
	private String getFloatAraStr(float[] datAra, String fmtStr, int brk) {
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
	private int[] shuffleAraIDXs(int len) {
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
	
	//shuffle all data passed
	public ProspectExample[] shuffleProspects(ProspectExample[] _list, long seed) {
		ProspectExample tmp;
		Random tr = new Random(seed);
		for(int i=(_list.length-1);i>0;--i){
			int j = tr.nextInt(i + 1);//find random lower idx somewhere below current position but greater than stIdx, and swap current with this idx
			tmp = _list[i];
			_list[i] = _list[j];
			_list[j] = tmp;
		}
		return _list;
	}
	
	//performs Durstenfeld  shuffle, leaves 0->stIdx alone - for testing/training data
	public String[] shuffleStrList(String[] _list, String type, int stIdx){
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
	
	/////////////////////////////////////////
	//drawing and graphics methods - these must check if win and/or pa exist, or else except win or pa as passed arguments, to manage when this code is executed without UI
	public int[] getRndClr() {
		if (win==null) {return new int[] {255,255,255,255};}
		return win.pa.getRndClr2();
	}
	public int[] getRndClr(int alpha) {
		if (win==null) {return new int[] {255,255,255,alpha};}
		return win.pa.getRndClr2(alpha);
	}
	
	private static int dispTrainDataFrame = 0, numDispTrainDataFrames = 20;
	//if connected to UI, draw data - only called from window
	public void drawTrainData(SOM_StraffordMain pa) {
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
	public void drawTestData(SOM_StraffordMain pa) {
		pa.pushMatrix();pa.pushStyle();
		if (testData.length < numDispTestDataFrames) {	for(int i=0;i<testData.length;++i){		testData[i].drawMeMap(pa);	}	} 
		else {
			for(int i=dispTestDataFrame;i<testData.length-numDispTestDataFrames;i+=numDispTestDataFrames){		testData[i].drawMeMap(pa);	}
			for(int i=(testData.length-numDispTestDataFrames);i<testData.length;++i){		testData[i].drawMeMap(pa);	}				//always draw these (small count < numDispDataFrames
			dispTestDataFrame = (dispTestDataFrame + 1) % numDispTestDataFrames;
		}
		pa.popStyle();pa.popMatrix();
	}//drawTrainData
	
	//draw boxes around each node representing umtrx values derived in SOM code - deprecated, now drawing image
	public void drawUMatrixVals(SOM_StraffordMain pa) {
		pa.pushMatrix();pa.pushStyle();
		for(SOMMapNode node : MapNodes.values()){	node.drawMeUMatDist(pa);	}		
		pa.popStyle();pa.popMatrix();
	}//drawUMatrix
	//draw boxes around each node representing segments these nodes belong to
	public void drawSegments(SOM_StraffordMain pa) {
		pa.pushMatrix();pa.pushStyle();
		for(SOMMapNode node : MapNodes.values()){	node.drawMeSegClr(pa);	}		
		pa.popStyle();pa.popMatrix();
	}//drawUMatrix
	
	//draw all product nodes with max vals corresponding to current JPIDX
	public void drawProductNodes(SOM_StraffordMain pa, int prodJpIDX, boolean showJPorJPG) {
		pa.pushMatrix();pa.pushStyle();
		ArrayList<ProductExample> prodsToShow = (showJPorJPG ? productsByJp.get(jpJpgrpMon.getProdJpByIdx(prodJpIDX)) :  productsByJpg.get(jpJpgrpMon.getProdJpGrpByIdx(prodJpIDX)));
		for(ProductExample ex : prodsToShow) {			ex.drawMeLinkedToBMU(pa, 5.0f,ex.OID);		}		
		pa.popStyle();pa.popMatrix();
	}//drawProductNodes	
	
	private static int dispProdDataFrame = 0, numDispProdDataFrames = 20, framesPerDisp = 0, maxFramesPerDisp = 10;
	//show all products
	public void drawAllProductNodes(SOM_StraffordMain pa) {
		pa.pushMatrix();pa.pushStyle();
		if (productData.length-numDispProdDataFrames <=  0 ) {	for(int i=0;i<productData.length;++i){		productData[i].drawMeMap(pa);	}} 
		else {
			for(int i=dispProdDataFrame;i<productData.length-numDispProdDataFrames;i+=numDispProdDataFrames){		productData[i].drawMeMap(pa);	}
			for(int i=(productData.length-numDispProdDataFrames);i<productData.length;++i){		productData[i].drawMeMap(pa);	}				//always draw these (small count < numDispDataFrames
			++framesPerDisp;
			if(framesPerDisp >= maxFramesPerDisp) {
				framesPerDisp = 0;
				dispProdDataFrame = (dispProdDataFrame + 1) % numDispProdDataFrames;
			}
		}
		//for(ProductExample ex : productData) {ex.drawMeLinkedToBMU(pa, 5.0f,ex.OID);}		
		pa.popStyle();pa.popMatrix();
	}//drawProductNodes
	
	public void drawAnalysisAllJps(SOM_StraffordMain pa, float ht, float barWidth, int curJPIdx) {
		pa.pushMatrix();pa.pushStyle();
		ftrCalcObj.drawAllCalcRes(pa, ht, barWidth, curJPIdx);
		pa.popStyle();pa.popMatrix();
	}//drawAnalysisAllJps
	
	public void drawAnalysisOneJp(SOM_StraffordMain pa,  float ht, float width, int curJPIdx) {
		pa.pushMatrix();pa.pushStyle();
		ftrCalcObj.drawSingleFtr(pa, ht, width,jpJpgrpMon.getJpByIdx(curJPIdx));
		pa.popStyle();pa.popMatrix();
	}//drawAnalysisOneJp	
	
	public void drawAllNodesWted(SOM_StraffordMain pa, int curJPIdx) {//, int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		//pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		for(SOMMapNode node : MapNodes.values()){	node.drawMeSmallWt(pa,curJPIdx);	}
		pa.popStyle();pa.popMatrix();
	} 
		
	public void drawAllNodes(SOM_StraffordMain pa) {//, int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		//pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		for(SOMMapNode node : MapNodes.values()){	node.drawMeSmall(pa);	}
		pa.popStyle();pa.popMatrix();
	} 
	
	public void drawAllNodesNoLbl(SOM_StraffordMain pa) {//, int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		//pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		for(SOMMapNode node : MapNodes.values()){	node.drawMeSmallNoLbl(pa);	}
		pa.popStyle();pa.popMatrix();
	} 
		
	public void drawNodesWithWt(SOM_StraffordMain pa, float valThresh, int curJPIdx) {//, int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		//pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		TreeMap<Float,ArrayList<SOMMapNode>> map = PerJPHiWtMapNodes[curJPIdx];
		SortedMap<Float,ArrayList<SOMMapNode>> headMap = map.headMap(valThresh);
		for(Float key : headMap.keySet()) {
			ArrayList<SOMMapNode> ara = headMap.get(key);
			for (SOMMapNode node : ara) {		node.drawMeWithWt(pa, 10.0f*key, new String[] {""+node.OID+" : ",String.format("%.4f",key)});}
		}
		pa.popStyle();pa.popMatrix();
	}//drawNodesWithWt
	
	public void drawExMapNodes(SOM_StraffordMain pa, ExDataType _type) {
		HashSet<SOMMapNode> nodes = nodesWithEx.get(_type);
		pa.pushMatrix();pa.pushStyle();
		int _typeIDX = _type.getVal();
		for(SOMMapNode node : nodes){	node.drawMePopLbl(pa, _typeIDX);}
		pa.popStyle();pa.popMatrix();		
	}	
	public void drawExMapNodesNoLbl(SOM_StraffordMain pa, ExDataType _type) {
		HashSet<SOMMapNode> nodes = nodesWithEx.get(_type);
		pa.pushMatrix();pa.pushStyle();
		int _typeIDX = _type.getVal();
		for(SOMMapNode node : nodes){				node.drawMePopNoLbl(pa, _typeIDX);}
		pa.popStyle();pa.popMatrix();		
	}	
	private int getDistType() {return (getFlag(mapExclProdZeroFtrIDX) ? ProductExample.SharedFtrsIDX : ProductExample.AllFtrsIDX);}
	private static int dispProdJPDataFrame = 0, curProdJPIdx = -1, curProdTimer = 0;
	//display the region of the map expected to be impacted by the products serving the passed jp 
	public void drawProductRegion(SOM_StraffordMain pa, int prodJpIDX, double maxDist) {
		pa.pushMatrix();pa.pushStyle();
		ArrayList<ProductExample> prodsToShow = productsByJp.get(jpJpgrpMon.getProdJpByIdx(prodJpIDX));
		if(curProdJPIdx != prodJpIDX) {
			curProdJPIdx = prodJpIDX;
			dispProdJPDataFrame = 0;
			curProdTimer = 0;
		}
		int distType = getDistType();
		ProductExample ex = prodsToShow.get(dispProdJPDataFrame);
		ex.drawMeLinkedToBMU(pa, 5.0f,ex.OID);		
		ex.drawProdMapExtent(pa, distType, prodsToShow.size(), maxDist);
		++curProdTimer;
		if(curProdTimer > 10) {
			curProdTimer = 0;
			dispProdJPDataFrame = (dispProdJPDataFrame + 1) % prodsToShow.size();
		}
		pa.popStyle();pa.popMatrix();	
	}
	//draw right sidebar data
	public void drawResultBar(SOM_StraffordMain pa, float yOff) {
		yOff-=4;
		float sbrMult = 1.2f, lbrMult = 1.5f;//offsets multiplier for barriers between contextual ui elements
		pa.pushMatrix();pa.pushStyle();

		pa.popStyle();pa.popMatrix();	
	}//drawResultBar	
		
	// end drawing routines
	//////////////////////////////////////////////////////
	
	
	public DispSOMMapExample buildTmpDataExampleFtrs(myPointf ptrLoc, TreeMap<Integer, Float> ftrs, float sens) {return new DispSOMMapExample(this, ptrLoc, ftrs, sens);}
	public DispSOMMapExample buildTmpDataExampleDists(myPointf ptrLoc, float dist, float sens) {return new DispSOMMapExample(this, ptrLoc, dist, sens);}
	
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
	
	//get time from "start time" (ctor run for map manager)
	protected long getCurTime() {			
		Instant instant = Instant.now();
		return instant.toEpochMilli() - mapMgrBuiltTime;//milliseconds since 1/1/1970, subtracting when mapmgr was built to keep millis low		
	}//getCurTime() 
	
	//returns a positive int value in millis of current world time since sim start
	protected long getCurRunTimeForProc() {	return getCurTime() - curProcStartTime;}
	
	protected String getTimeStrFromProcStart() {return  getTimeStrFromPassedMillis(getCurRunTimeForProc());}
	//get a decent display of passed milliseconds elapsed
	//	long msElapsed = getCurRunTimeForProc();
	protected String getTimeStrFromPassedMillis(long msElapsed) {
		long ms = msElapsed % 1000, sec = (msElapsed / 1000) % 60, min = (msElapsed / 60000) % 60, hr = (msElapsed / 3600000) % 24;	
		String res = String.format("%02d:%02d:%02d.%03d", hr, min, sec, ms);
		return res;
	}//getTimeStrFromPassedMillis	
	
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
	
	//show array of strings, either just to console or to applet window
	public void dispMessageAra(String[] _sAra, String _callingClass, String _callingMethod, int _perLine, MsgCodes useCode) {
		String callingClassPrfx = getTimeStrFromProcStart() +"|" + _callingClass;		 
		for(int i=0;i<_sAra.length; i+=_perLine){
			String s = "";
			for(int j=0; j<_perLine; ++j){	
				if((i+j >= _sAra.length)) {continue;}
				s+= _sAra[i+j]+ "\t";}
			_dispMessage_base(callingClassPrfx,_callingMethod,s, useCode);
		}
	}//dispMessageAra
	
	public void dispMessage(String srcClass, String srcMethod, String msgText, MsgCodes useCode) {_dispMessage_base(getTimeStrFromProcStart() +"|" + srcClass,srcMethod,msgText, useCode);	}	
	private void _dispMessage_base(String srcClass, String srcMethod, String msgText, MsgCodes useCode) {		
		String msg = _processMsgCode(srcClass + "::" + srcMethod + " : " + msgText, useCode);
		if (pa == null) {System.out.println(msg);} else {pa.outStr2Scr(msg);}
	}//dispMessage

	//get fill, stroke and text color ID if win exists (to reference papplet) otw returns 0,0,0
	public int[] getClrVal(ExDataType _type) {
		if (win==null) {return new int[] {0,0,0};}															//if null then not going to be displaying anything
		switch(_type) {
			case ProspectTraining : {		return new int[] {win.pa.gui_Cyan,win.pa.gui_Cyan,win.pa.gui_Blue};}			//corresponds to prospect training example
			case ProspectTesting : {		return new int[] {win.pa.gui_Magenta,win.pa.gui_Magenta,win.pa.gui_Red};}		//corresponds to prospect testing/held-out example
			case Product : {		return new int[] {win.pa.gui_Yellow,win.pa.gui_Yellow,win.pa.gui_White};}		//corresponds to product example
			case MapNode : {		return new int[] {win.pa.gui_Green,win.pa.gui_Green,win.pa.gui_Cyan};}			//corresponds to map node example
			case MouseOver : {		return new int[] {win.pa.gui_White,win.pa.gui_White,win.pa.gui_White};}			//corresponds to mouse example
		}
		return new int[] {win.pa.gui_White,win.pa.gui_White,win.pa.gui_White};
	}//getClrVal
	
	//set UI values from loaded map data, if UI is in use
	public void setUIValsFromLoad(SOM_MapDat mapDat) {if (win != null) {		win.setUIValues(mapDat);	}}//setUIValsFromLoad
	
	public void resetButtonState() {if (win != null) {	win.resetButtonState();}}

	public void setMapNumCols(int _x){
		//need to update UI value in win
		mapNodeCols = _x;
		nodeXPerPxl = mapNodeCols/this.mapDims[0];
		if (win != null) {			
			boolean didSet = win.setWinToUIVals(win.uiMapColsIDX, mapNodeCols);
			if(!didSet){dispMessage("SOMMapManager","setMapX","Setting ui map x value failed for x = " + _x, MsgCodes.error2);}
		}
	}//setMapX
	public void setMapNumRows(int _y){
		//need to update UI value in win
		mapNodeRows = _y;
		nodeYPerPxl = mapNodeRows/this.mapDims[1];
		if (win != null) {			
			boolean didSet = win.setWinToUIVals(win.uiMapRowsIDX, mapNodeRows);
			if(!didSet){dispMessage("SOMMapManager","setMapY","Setting ui map y value failed for y = " + _y, MsgCodes.error2);}
		}
	}//setMapY
	
	public float getMapWidth(){return mapDims[0];}
	public float getMapHeight(){return mapDims[1];}
	public int getMapNodeCols(){return mapNodeCols;}
	public int getMapNodeRows(){return mapNodeRows;}	
	
	public float getNodePerPxlCol() {return nodeXPerPxl;}
	public float getNodePerPxlRow() {return nodeYPerPxl;}	
	
	private void initFlags(){stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlag(i,false);}}
	public void setAllFlags(int[] idxs, boolean val) {for (int idx : idxs) {setFlag(idx, val);}}
	public void setFlag(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case debugIDX : 				{break;}	
			case isMTCapableIDX : {						//whether or not the host architecture can support multiple execution threads
				break;}
			case mapDataLoadedIDX	: 		{break;}		
			case loaderRtnIDX : {break;}
			case mapExclProdZeroFtrIDX : 	{break;}
			case prospectDataLoadedIDX: 	{break;}		//raw prospect data has been loaded but not yet processed
			case optDataLoadedIDX: 			{break;}				//raw opt data has been loaded but not processed
			case orderDataLoadedIDX: 		{break;}			//raw order data loaded not proced
			case rawPrspctEvDataProcedIDX: 	{break;}				//all raw prospect/event data has been processed into StraffSOMExamples and subsequently erased
			case rawProducDataProcedIDX : 	{break;}			//all raw product data has been processed into StraffSOMExamples and subsequently erased 
			case testTrainProdDataBuiltIDX : 	{break;}			//arrays of input, training and testing data built
			case denseTrainDataSavedIDX : {
				if (val) {dispMessage("SOMMapManager","setFlag","All "+ this.numTrainData +" Dense Training data saved to .lrn file", MsgCodes.info5);}
				break;}				//all prospect examples saved as training data
			case sparseTrainDataSavedIDX : {
				if (val) {dispMessage("SOMMapManager","setFlag","All "+ this.numTrainData +" Sparse Training data saved to .svm file", MsgCodes.info5);}
				break;}				//all prospect examples saved as training data
			case testDataSavedIDX : {
				if (val) {dispMessage("SOMMapManager","setFlag","All "+ this.numTestData + " saved to " + projConfigData.getSOMMapTestFileName() + " using "+(projConfigData.useSparseTestingData ? "Sparse ": "Dense ") + "data format", MsgCodes.info5);}
				break;		}			
		}
	}//setFlag		
	public boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		
	//getter/setter/convenience funcs
	public boolean mapCanBeTrained(int kVal) {
		//eventually enable training map on existing files - save all file names, enable file names to be loaded and map built directly
		return ((kVal <= 1) && (getFlag(denseTrainDataSavedIDX)) || ((kVal == 2) && getFlag(sparseTrainDataSavedIDX)));
	}	
	//return true if loader is done and if data is successfully loaded
	public boolean isMapDrawable(){return getFlag(loaderRtnIDX) && getFlag(mapDataLoadedIDX);}
	public boolean isFtrCalcDone() {return (ftrCalcObj != null) && ftrCalcObj.calcAnalysisIsReady();}	
	public boolean isToroidal(){return projConfigData.isToroidal();}
	//add prospect to prospect map
	//public ProspectExample putInProspectMap(ProspectExample ex) {	return prospectMap.put(ex.OID, ex);}	

	public ProductExample getProductByID(String prodOID) {	return productMap.get(prodOID);		}
	public boolean getUseChiSqDist() {return useChiSqDist;}
	public void setUseChiSqDist(boolean _useChiSq) {useChiSqDist=_useChiSq;}
	
	public String getJpByIdxStr(int idx) {return jpJpgrpMon.getJpByIdxStr(idx);}	
	public String getJpGrpByIdxStr(int idx) {return jpJpgrpMon.getJpGrpByIdxStr(idx);}
		
	public String getProdJpByIdxStr(int idx) {return jpJpgrpMon.getProdJpByIdxStr(idx);}	
	public String getProdJpGrpByIdxStr(int idx) {return jpJpgrpMon.getProdJpGrpByIdxStr(idx);}
		
	//this will return the appropriate jpgrp for the given jpIDX (ftr idx)
	public int getUI_JPGrpFromJP(int jpIdx, int curVal) {		return jpJpgrpMon.getUI_JPGrpFromJP(jpIdx, curVal);}
	//this will return the first(lowest) jp for a particular jpgrp
	public int getUI_FirstJPFromJPG(int jpgIdx, int curVal) {	return jpJpgrpMon.getUI_FirstJPFromJPG(jpgIdx, curVal);}	
	//return appropriately pathed file name for map image of specified JP idx
	public String getSOMLocClrImgForJPFName(int jpIDX) {return projConfigData.getSOMLocClrImgForJPFName(jpIDX);	}
	
	//project config manages this information now
	public Calendar getInstancedNow() { return projConfigData.getInstancedNow();}

	//set current map ftr type, and update ui if necessary
	public void setCurrentDataFormat(int _frmt) {	curMapFtrType = _frmt; }//setCurrentDataFormat
	public int getCurrMapDataFrmt() {	return curMapFtrType;}
	//set flag that SOM file loader is finished to false
	public void setLoaderRtnFalse() {setFlag(loaderRtnIDX, false);}
	
	public static float getNodeInSegThresh() {return nodeInSegDistThresh;}
	public static void setNodeInSegThresh(float _val) {nodeInSegDistThresh=_val;}	
	//getter/setter/convenience funcs to check for whether mt capable, and to return # of usable threads (total host threads minus some reserved for processing)
	public boolean isMTCapable() {return getFlag(isMTCapableIDX);}
	public int getNumUsableThreads() {return numUsableThreads;}
	public ExecutorService getTh_Exec() {return th_exec;}
	
	public String toString(){
		String res = "Weights Data : \n";
		for(Tuple<Integer,Integer> key : MapNodes.keySet()){
			SOMMapNode n = MapNodes.get(key);
			res+="Key:"+key.toString()+" : "+n.toCSVString(0)+"\n";}
		res += "Training Data : \n";
		for(int i =0; i<trainData.length;++i){ res += trainData[i].toString();}
		//TODO a lot of data is missing
		return res;	
	}	
	
}//SOMMapManager

//manage a message stream from a launched external process - used to manage output from som training process
class messageMgr implements Callable<Boolean> {
	SOMMapManager mapMgr;
	final Process process;
	BufferedReader rdr;
	StringBuilder strbld;
	String type;
	MsgCodes msgType;//for display of output
	int iter = 0;
	public messageMgr(SOMMapManager _mapMgr, final Process _process, Reader _in, String _type) {
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
	private void dispMessage(String str, MsgCodes useCode) {
		if(mapMgr != null) {
			String typStr = getStreamType(str);			
			mapMgr.dispMessage("messageMgr","call ("+type+" Stream Handler)", str, useCode);}
		else {				System.out.println(str);	}
	}//dispMessage
	
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
