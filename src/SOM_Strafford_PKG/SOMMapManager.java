package SOM_Strafford_PKG;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;


//this class holds the data describing a SOM and the data used to both build and query the som
public class SOMMapManager {
	//source directory for reading all source csv files, writing intermediate outputs, executing SOM_MAP and writing results
	//TODO : move this to main java stub, pass it around via ctor
	//public static final String straffBasefDir = "F:" + File.separator + "StraffordProject" + File.separator;
	public String straffBasefDir;
	//calcFileWeights is file holding config for calc object
	public static final String calcWtFileName = "WeightEqConfig.csv";
	//short name to be used in file names to denote this project
	private final String SOMProjName = "straff";
	
	//struct maintaining complete project configuration and information from config files
	public SOMProjConfigData projConfigData;			

	//lowest rank to display of bmus/mapnodes
	//private static final int dispRankThresh = 10;
	
	////////////////////////////////////////////////////////////////////////////////////////////////
	//OS data
	//OS this application is currently running on
	private final String OSUsed;
		
	////////////////////////////////////////////////////////////////////////////////////////////////
	//map descriptions
	//description of SOM_MAP exe params
	private SOM_MAPDat SOMExeDat;	
	//string to invoke som executable - platform dependent
	private final String SOM_Map_EXECSTR;
	//calandar object to be used to query instancing time
	public final Calendar instancedNow;
	//all nodes of som map, keyed by node location
	public TreeMap<Tuple<Integer,Integer>, SOMMapNodeExample> MapNodes;
	//keyed by field used in lrn file (float rep of individual record, 
	public TreeMap<String, dataClass> TrainDataLabels;	
	//data set to be training data, etc
	public StraffSOMExample[] trainData, inputData, testData;
	//array of per jp treemaps of nodes keyed by jp weight
	public TreeMap<Float,ArrayList<SOMMapNodeExample>>[] PerJPHiWtMapNodes;
	
	public float[] 
			map_ftrsMean, 
			map_ftrsVar, 
			map_ftrsDiffs, 
			map_ftrsMin,			//per feature mean, variance, difference, mins, in -map features- data
			td_ftrsMean, td_ftrsVar, 
			in_ftrsMean, in_ftrsVar; //per feature training and input data means and variances
	
	public HashSet<SOMMapNodeExample> nodesWithEx, nodesWithNoEx;	//map nodes that have examples - for display only
	
	//features used to train map - these constructs are intended to hold the sorted list of weight ratios for each feature on all map nodes.  this can be very big (as big as the weights structure) so only load if necessary
	public SOMFeature[] featuresBMUs;
	public dataDesc dataHdr;			//describes data, set in weights read used in csv save file
	public int numFtrs, numTrainData, numInputData, numTestData;
	
	public Float[] diffsVals, minsVals;	//values to return scaled values to actual data points - multiply wts by diffsVals, add minsVals
	//# of nodes in x/y
	public int mapX =0, mapY =0;
	//# of nodes / map dim  in x/y
	public float nodeXPerPxl, nodeYPerPxl;
	
	//public String somResBaseFName, trainDataName, diffsFileName, minsFileName;//files holding diffs and mins (.csv extension)
	private int[] stFlags;						//state flags - bits in array holding relevant process info
	public static final int
			debugIDX 					= 0,
			mapDataLoadedIDX			= 1,			//som map data is cleanly loaded
			loaderRtnIDX				= 2,			//dataloader has finished - wait on this to draw map
			mapSetSmFtrZeroIDX  		= 3,			//map small vector's features to 0, otherwise only compare non-zero features
			
			//raw data loading/processing state : 
			prospectDataLoadedIDX		= 4,			//raw prospect data has been loaded but not yet processed
			optDataLoadedIDX			= 5,			//raw opt data has been loaded but not processed
			orderDataLoadedIDX			= 6,			//raw order data loaded not proced
			linkDataLoadedIDX			= 7,			//raw link data loaded not proced
			tcTagsDataLoadedIDX			= 8,			//raw tc taggings data loaded not proced
			jpDataLoadedIDX				= 9,			//raw jp data loaded not proced
			jpgDataLoadedIDX			= 10,			//raw jpg data loaded not proced
			
			rawPrspctEvDataProcedIDX	= 11,			//all raw prospect/event data has been loaded and processed into StraffSOMExamples (prospect)
			rawProducDataProcedIDX		= 12,			//all raw product data (from tc_taggings) has been loaded and processed into StraffSOMExamples (product)
			//training data saved state : 
			denseTrainDataSavedIDX 		= 13,			//all current prospect data has been saved as a training data file for SOM (.lrn format) - strafford doesn't use dense training data
			sparseTrainDataSavedIDX		= 14,			//sparse data format using .svm file descriptions (basically a map with a key:value pair of ftr index : ftr value
			testDataSavedIDX			= 15;			//save test data in sparse format csv

	public static final int numFlags = 16;	
	
	//size of intermediate per-OID record csv files : 
	public static final int preProcDatPartSz = 50000;
	//data in files created by SOM_MAP separated by spaces
	public static final String SOM_FileToken = " ", csvFileToken = "\\s*,\\s*";	
	//subdir to put preproc data files
	public static final String straffPreProcSubDir = "PreprocData" + File.separator;
	//subdir to hold source csv files
	public static final String straffSourceCSVSubDir = "source_csvs" + File.separator;
	//subdir for all sql info - connect config file
	public static final String straffSQLProcSubDir = "Sql"+ File.separator;
	//subdir for all SOM functionality
	public static final String straffSOMProcSubDir = "SOM"+ File.separator;
	//subdir holding calc object information
	public static final String straffCalcInfoSubDir = "Calc"+ File.separator;
	
	//actual directory where SOM data is located
	public String SOMDataDir;
	
	//////////////////
	//source data constructs
	//all of these constructs must follow the same order - 1st value must be prospect, 2nd must be order events, etc.
	//idxs for each type of data in arrays holding relevant data info
	public static final int prspctIDX = 0, orderEvntIDX = 1, optEvntIDX = 2, linkEvntIDX = 3, tcTagsIDX = 4, jpDataIDX = 5, jpgDataIDX = 6;
	//file names of each type of file
	private static final String[] straffDataFileNames = new String[] {"prospect_objects", "order_event_objects", "opt_event_objects", "link_event_objects", "tc_taggings", "jp_data", "jpg_data"};
	//whether each table uses json as final field to hold important info or not
	private static final boolean[] straffRawDatUsesJSON = new boolean[] {true, true, true, true, false, true, true};
	//whether we want to debug the loading of a particular type of raw data
	private static final boolean[] straffRawDatDebugLoad = new boolean[] {false, false, false, false, true, true, true};
	//list of idxs related to each table for data
	private static final int[] straffObjFlagIDXs = new int[] {prospectDataLoadedIDX, orderDataLoadedIDX, optDataLoadedIDX, linkDataLoadedIDX, tcTagsDataLoadedIDX, jpDataLoadedIDX, jpgDataLoadedIDX};
	//list of class names used to build array of object loaders
	private static final String[] straffClassLdrNames = new String[] {
			"SOM_Strafford_PKG.ProspectDataLoader","SOM_Strafford_PKG.OrderEventDataLoader","SOM_Strafford_PKG.OptEventDataLoader",
			"SOM_Strafford_PKG.LinkEventDataLoader","SOM_Strafford_PKG.TcTagDataLoader", "SOM_Strafford_PKG.JpDataLoader", "SOM_Strafford_PKG.JpgrpDataLoader"
		};	
	//total # of source data types
	public static final int numStraffDataTypes = straffObjFlagIDXs.length;	
	//classes of data loader objects
	public Class[] straffObjLoaders;
	//destination object to manage arrays of each type of raw data from db
	public ConcurrentSkipListMap<String, ArrayList<BaseRawData>> rawDataArrays;
	//for multi-threaded calls to base loader
	public List<Future<Boolean>> straffDataLdrFtrs;
	public List<StraffordDataLoader> straffDataLoaders;
	
	//map of prospectExamples built from database data, keyed by prospect OID
	public ConcurrentSkipListMap<String, ProspectExample> prospectMap;	
	//map of products build from TC_Taggings entries, keyed by tag ID (synthesized upon creation)
	public ConcurrentSkipListMap<String, ProductExample> productMap;
	
	//manage all jps and jpgs seen in project
	public MonitorJpJpgrp jpJpgrpMon;
	
	//file names
	//public static final String jpseenFName = "jpSeen.txt", jpgroupsAndJpsFName = "jpgroupsAndJps.txt";
	//calc object to be used to derive feature vector for each prospect
	public StraffWeightCalc ftrCalcObj;

	//data type to use to train map
	public static final int useUnmoddedDat = 0, useScaledDat = 1, useNormedDat = 2;
	public static final String[] uiMapTrainFtrTypeList = new String[] {"Unmodified","Standardized (0->1 per ftr)","Normalized (vector mag==1)"};
	//distance to use : 2 : scaled features, 1: chisq features or 0 : regular feature dists
	public int distType = 0;
	//map dims is used to calculate distances for BMUs - based on screen dimensions - need to change this?
	private float[] mapDims;
	
	//used by UI, ignored if NULL (passed by command line program)
	public SOM_StraffordMain p;
	public mySOMMapUIWin win;				//owning window
	
	private ExecutorService th_exec;	//to access multithreading - instance from calling program
	public final int numUsableThreads;		//# of threads usable by the application
	
	public SOMMapManager(SOM_StraffordMain _pa, mySOMMapUIWin _win, ExecutorService _th_exec, float[] _dims) {
		p=_pa; win=_win;th_exec=_th_exec;
		
		//----accumulate and manage OS info ----//
		//find platform this is executing on
		OSUsed = System.getProperty("os.name");
		dispMessage("SOMMapData","Constructor","OS this application is running on : "  + OSUsed);
		//set invoking string for map executable - is platform dependent
		String execStr = "straff_SOM";
		if (OSUsed.toLowerCase().contains("windows")) {execStr += ".exe";	}

		SOM_Map_EXECSTR = execStr;		
		
		//----end accumulate and manage OS info ----//
		
		
		String baseDir = "StraffordProject" + File.separator;
		try {
			straffBasefDir = new File(baseDir).getCanonicalPath() + File.separator ;
		} catch (Exception e) {
			straffBasefDir =  baseDir;
			dispMessage("SOMMapData","Constructor","Failed to find base application directory "+ baseDir + " due to : " + e);
		}
		dispMessage("SOMMapData","Constructor","Canonical Path to application directory : " + straffBasefDir);
		dispMessage("SOMMapData","Constructor","Loading Project configuration file. (Not yet implemented : TODO : build project config to facilitate turnkey operation.)");	
		
		//want # of usable background threads.  Leave 2 for primary process (and potential draw loop)
		numUsableThreads = Runtime.getRuntime().availableProcessors() - 2;
		initFlags();
		mapDims = _dims;
		//raw data from csv's/db
		rawDataArrays = new ConcurrentSkipListMap<String, ArrayList<BaseRawData>>();
		//instantiate map of ProspectExamples
		prospectMap = new ConcurrentSkipListMap<String, ProspectExample>();		
		productMap = new ConcurrentSkipListMap<String, ProductExample>();
		//object to manage all jps and jpgroups seen in project
		jpJpgrpMon = new MonitorJpJpgrp(this);
		
		instancedNow = Calendar.getInstance();
		try {
			straffObjLoaders = new Class[straffClassLdrNames.length];//{Class.forName("SOM_Strafford_PKG.ProspectDataLoader"),  Class.forName("SOM_Strafford_PKG.OrderEventDataLoader"),Class.forName("SOM_Strafford_PKG.OptEventDataLoader"),Class.forName("SOM_Strafford_PKG.LinkEventDataLoader")};
			for (int i=0;i<straffClassLdrNames.length;++i) {straffObjLoaders[i]=Class.forName(straffClassLdrNames[i]);			}
		} catch (Exception e) {dispMessage("SOMMapData","Constructor","Failed to instance straffObjLoader classes : " + e);	}
		//to launch loader callable instances
		buildStraffDataLoaders();		
		initData();
	}//ctor
	//ctor from non-UI stub
	public SOMMapManager(ExecutorService _th_exec,float[] _dims) {this(null, null, _th_exec, _dims);}

	//load the data from csv files for jpg and jp data - names
	public void loadCSVJpgJpData() {
		
	}//loadCSVJpgJpData
	
	public void loadSQLJpgJpData() {
		
	}//loadSQLJpgJpData
	
	//build a date with each component separated by token
	public String[] getDateTimeString(Calendar now){return getDateTimeString(false,".", now);}
	public String[] getDateTimeString(boolean toSecond, String token,Calendar now){
		String res, resWithYear="", resWithoutYear="";
		int val;
		val = now.get(Calendar.YEAR);	
		resWithYear += ""+val+token;
		val = now.get(Calendar.MONTH)+1;		res = (val < 10 ? "0"+val : ""+val)+ token;	 resWithYear += res;		resWithoutYear += res;		
		val = now.get(Calendar.DAY_OF_MONTH);	res = (val < 10 ? "0"+val : ""+val)+ token;	 resWithYear += res;		resWithoutYear += res;
		val = now.get(Calendar.HOUR_OF_DAY);	res = (val < 10 ? "0"+val : ""+val)+ token;	 resWithYear += res;		resWithoutYear += res;
		val = now.get(Calendar.MINUTE);			res = (val < 10 ? "0"+val : ""+val);	 resWithYear += res;		resWithoutYear += res;		
		if(toSecond){val = now.get(Calendar.SECOND);res = token + (val < 10 ? "0"+val : ""+val)+ token;	 resWithYear += res;		resWithoutYear += res;}
		
		return new String[] {resWithYear,resWithoutYear};
	}
	
	private String[] SOMFileNamesAra;		//idx 0 = file name for SOM training data -> .lrn file, 1 = file name for sphere sample data -> .csv file	
	//call when src data are first initialized - sets file names for .lrn file  and testing file output database query
	//dataFrmt : format used to train SOM == 0:unmodded; 1:std'ized; 2:normed
	public void setSOM_ExpFileNames(int _numSmpls, int _numTrain, int _numTest, int dataFrmt){
		//enable these to be set manually based on passed "now"
		String[] dateTimeStrAra = getDateTimeString(false, "_", instancedNow);//idx 0 has year, idx 1 does not
		String nowDirTmp = straffSOMProcSubDir+ "StraffSOM_"+dateTimeStrAra[0]+File.separator;
		String fileNow = dateTimeStrAra[1];
		setSOM_ExpFileNames( _numSmpls, _numTrain, _numTest, dataFrmt, fileNow, nowDirTmp);		
	}//setSOM_ExpFileNames
	
	public void setSOM_ExpFileNames(int _numSmpls, int _numTrain, int _numTest, int dataFrmt, String fileNow, String nowDirTmp){
		String nowDir = getDirNameAndBuild(nowDirTmp);
		String dType = getDataTypeNameFromInt(dataFrmt);
		String[] tmp = {"Out_"+SOMProjName+"_Smp_"+_numSmpls,
						"Train_"+SOMProjName+"_Smp_"+_numTrain+"_of_"+_numSmpls+"_typ_" +dType + "_Dt_"+fileNow+".lrn",
						"Train_"+SOMProjName+"_Smp_"+_numTrain+"_of_"+_numSmpls+"_typ_" + dType+"_Dt_"+fileNow+".svm",				
						"Test_"+SOMProjName+"_Smp_"+_numTest+"_of_"+_numSmpls+"_typ_" +dType +"_Dt_"+fileNow+".csv",
						"Mins_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+"_typ_" +dType +".csv",
						"Diffs_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+"_typ_" +dType +".csv",
						"SOMImg_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+"_typ_" +dType +".png",
						"SOM_EXP_Format_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+".txt"
						};
		SOMFileNamesAra = new String[tmp.length];
		
		for(int i =0; i<SOMFileNamesAra.length;++i){	
			//build dir as well
			SOMFileNamesAra[i] = nowDir + tmp[i];
			dispMessage("SOMMapData","setSOM_ExpFileNames","Built Dir : " + SOMFileNamesAra[i]);
		}				
	}//setSOM_ExpFileNames
	
	private String getSOMMapOutFileBase(){	return SOMFileNamesAra[0];	}	
	private String getSOMMapLRNFileName(){	return SOMFileNamesAra[1];	}
	private String getSOMMapSVMFileName(){	return SOMFileNamesAra[2];	}
	private String getSOMMapTestFileName(){	return SOMFileNamesAra[3];	}
	private String getSOMMapMinsFileName(){	return SOMFileNamesAra[4];	}
	private String getSOMMapDiffsFileName(){	return SOMFileNamesAra[5];	}	
	//private String getSOMLocClrImgFName(){	return SOMFileNamesAra[6];}		
	public String getSOMLocClrImgForJPFName(int jpIDX) {return "jp_"+jpJpgrpMon.getJpByIdx(jpIDX)+"_"+SOMFileNamesAra[6];}	
	//ref to file name for experimental setup
	private String getSOMMapExpFileName() {return SOMFileNamesAra[7];	}	

	//this will save all essential information for a SOM-based experimental run, to make duplication easier
	public void saveSOM_Exp() {
		//build file describing experiment and put here : 
		String expFileName = getSOMMapExpFileName();
		//save file holding names of source csv data from which training and testing data are built
		//
		//TODO
		
	}//saveSOM_Exp
	
	//this will load all essential information for a SOM-based experimental run, from loading preprocced data, configuring map
	public void loadSOM_Exp() {
		//TODO
	}//loadSOM_Exp

	//build new SOM_MAP map using UI-entered values, then load resultant data
	//with maps of required SOM exe params
	protected boolean buildNewSOMMap(boolean mapLoadFtrBMUsIDX, int dataFrmt, HashMap<String, Integer> mapInts, HashMap<String, Float> mapFloats, HashMap<String, String> mapStrings){
		String lrnFileName = getSOMMapLRNFileName(), 		//dense training data .lrn file
				svmFileName = getSOMMapSVMFileName(),		//sparse training data svm file name
				testFileName = getSOMMapTestFileName(),		//testing data .csv file
				minsFileName = getSOMMapMinsFileName(),		//mins data percol .csv file
				diffsFileName = getSOMMapDiffsFileName(),	//diffs data percol .csv file
				outFilePrfx = getSOMMapOutFileBase() + "_x"+mapInts.get("mapCols")+"_y"+mapInts.get("mapRows")+"_k"+mapInts.get("mapKType");
		
		//determine directory where map exe resides, build map data object to point there
		String SOM_Dir = getDirNameAndBuild(straffSOMProcSubDir);
		//structure holding SOM_MAP specific cmd line args and file names and such
		SOMExeDat = new SOM_MAPDat(OSUsed, SOM_Dir, SOM_Map_EXECSTR, mapInts,mapFloats, mapStrings, lrnFileName, svmFileName,  outFilePrfx);
		dispMessage("SOMMapData","buildNewSOMMap","SOM map descriptor : \n" + SOMExeDat.toString() + "Exec str : ");
		//launch in a thread? - needs to finish to be able to proceed, so not necessary
		boolean runSuccess = buildNewMap();
		if(!runSuccess) {
			return false;
		}
		//now load new map data and configure SOMMapData obj to hold all appropriate data
		/**
		 * load data to represent map results
		 * @param cFN class file name
		 * @param diffsFileName per-feature differences file name
		 * @param minsFileName per-feature mins file name
		 * @param lrnFileName som training data file name 
		 * @param outFilePrfx
		 * @param csvOutBaseFName file name prefix used to save class data in multiple formats to csv files
		 */
		String csvOutBaseFName = outFilePrfx + "_outCSV";
		//initData();			
		setFlag(loaderRtnIDX, false);
		projConfigData = new SOMProjConfigData(this);
		projConfigData.setAllFileNames(diffsFileName,minsFileName, lrnFileName, outFilePrfx, csvOutBaseFName);
		dispMessage("SOMMapData","buildNewSOMMap","Current projConfigData before dataLoader Call : " + projConfigData.toString());
		th_exec.execute(new SOMDataLoader(this,mapLoadFtrBMUsIDX,projConfigData, dataFrmt));//fire and forget load task to load	
		return true;
	}//buildNewSOMMap	
	@SuppressWarnings("unchecked")
	public void initPerJPMapOfNodes() {
		PerJPHiWtMapNodes = new TreeMap[numFtrs];
		for (int i=0;i<PerJPHiWtMapNodes.length; ++i) {PerJPHiWtMapNodes[i] = new TreeMap<Float,ArrayList<SOMMapNodeExample>>(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}});}
	}//

	//put a map node in PerJPHiWtMapNodes per-jp array
	public void setMapNodeFtrStr(SOMMapNodeExample mapNode) {
		TreeMap<Integer, Float> stdFtrMap = mapNode.getCurrentFtrMap(SOMMapManager.useScaledDat);
		for (Integer jpIDX : stdFtrMap.keySet()) {
			Float ftrVal = stdFtrMap.get(jpIDX);
			ArrayList<SOMMapNodeExample> nodeList = PerJPHiWtMapNodes[jpIDX].get(ftrVal);
			if (nodeList== null) {
				nodeList = new ArrayList<SOMMapNodeExample>();
			}
			nodeList.add(mapNode);
			PerJPHiWtMapNodes[jpIDX].put(ftrVal, nodeList);
		}		
	}//setMapNodeFtrStr
	
	public boolean mapCanBeTrained(int kVal) {
		//eventually enable training map on existing files - save all file names, enable file names to be loaded and map built directly
		return ((kVal <= 1) && (getFlag(denseTrainDataSavedIDX)) || ((kVal == 2) && getFlag(sparseTrainDataSavedIDX)));
	}
	
	//build the callable strafford data loaders list
	private void buildStraffDataLoaders() {
		//to launch loader callable instances
		straffDataLdrFtrs = new ArrayList<Future<Boolean>>();
		straffDataLoaders = new ArrayList<StraffordDataLoader>();
		//build constructors
		@SuppressWarnings("rawtypes")
		Class[] args = new Class[] {boolean.class, String.class};//classes of arguments for loader ctor	
		//numStraffDataTypes
		try {
			for (int idx=0;idx<numStraffDataTypes;++idx) {straffDataLoaders.add((StraffordDataLoader) straffObjLoaders[idx].getDeclaredConstructor(args).newInstance(true, "to be set"));}
		} catch (Exception e) {			e.printStackTrace();}
		
	}//buildStraffDataLoaders
		
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
	
	//only appropriate if using UI
	public void setMapImgClrs(){if (win != null) {win.setMapImgClrs();} else {dispMessage("SOMMapData","setMapImgClrs","Display window doesn't exist, can't build visualization images; ignoring.");}}
	//only appropriate if using UI
	public void initMapAras(int numFtrs) {
		if (win != null) {
			dispMessage("SOMMapData","initMapAras","Initializing per-feature map display to hold : "+ numFtrs+" map images.");
			win.initMapAras(numFtrs);} 
		else {dispMessage("SOMMapData","initMapAras","Display window doesn't exist, can't build map visualization image arrays; ignoring.");}}
	
	//only appropriate if using UI
	public void setSaveLocClrImg(boolean val) {if (win != null) { win.setPrivFlags(win.saveLocClrImgIDX,val);}}
	
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
		    dispMessage("SOMMapManager", "loadFileIntoStringAra",dispYesStr+"\tLength : " +  lines.size());
		    res = lines.toArray(new String[0]);		    
		} catch (Exception e) {	
			e.printStackTrace();
			 dispMessage("SOMMapManager", "loadFileIntoStringAra","!!"+dispNoStr);
			res= new String[0];
		} 
		finally {
		    if (inputStream != null) {inputStream.close();		    }
		    if (sc != null) { sc.close();		    }
		}
		return res;
	}//loadFileContents
	
	//launch process to exec SOM_MAP
	public void runMap(String[] cmdExecStr) throws IOException{
		dispMessage("SOMMapData","runMap","runMap Starting");
		dispMessageAra(cmdExecStr,2);//2 strings per line, display execution command
		boolean showDebug = getFlag(debugIDX);
		//http://stackoverflow.com/questions/10723346/why-should-avoid-using-runtime-exec-in-java		
		String wkDirStr = cmdExecStr[0], cmdStr = cmdExecStr[1], argsStr = "";
		String[] execStr = new String[cmdExecStr.length - 1];
		execStr[0] = wkDirStr + cmdStr;
		for(int i =2; i<cmdExecStr.length;++i){execStr[i-1] = cmdExecStr[i]; argsStr +=cmdExecStr[i]+" | ";}
		if(showDebug){dispMessage("SOMMapData","runMap","wkDir : "+ wkDirStr + "\ncmdStr : " + cmdStr + "\nargs : "+argsStr);}
		
		//monitor in multiple threads, either msgs or errors
		List<Future<Boolean>> procMsgMgrsFtrs = new ArrayList<Future<Boolean>>();
		List<messageMgr> procMsgMgrs = new ArrayList<messageMgr>(); 
		
		ProcessBuilder pb = new ProcessBuilder(execStr);		
		File wkDir = new File(wkDirStr); 
		pb.directory(wkDir);
		try {
			final Process process=pb.start();
			
			messageMgr inMsgs = new messageMgr(this,process,new InputStreamReader(process.getInputStream()), "Input" );
			messageMgr errMsgs = new messageMgr(this,process,new InputStreamReader(process.getErrorStream()), "Error" );
			procMsgMgrs.add(inMsgs);
			procMsgMgrs.add(errMsgs);
			
			procMsgMgrsFtrs = th_exec.invokeAll(procMsgMgrs);for(Future<Boolean> f: procMsgMgrsFtrs) { f.get(); }

			String resultIn = inMsgs.getResults(), 
					resultErr = errMsgs.getResults() ;//result of running map TODO save to log?			
			
			
		} catch (IOException e) {
			dispMessage("SOMMapManager","runMap","Process failed with IOException : " + e.toString() + "\n\t"+ e.getMessage());
	    } catch (InterruptedException e) {
	    	dispMessage("SOMMapManager","runMap","Process failed with InterruptedException : " + e.toString() + "\n\t"+ e.getMessage());	    	
	    } catch (ExecutionException e) {
	    	dispMessage("SOMMapManager","runMap","Process failed with ExecutionException : " + e.toString() + "\n\t"+ e.getMessage());
		}		
		
		dispMessage("SOMMapData","runMap","runMap Finished");
	}
	//Build map from data by aggregating all training data, building SOM exec string from UI input, and calling OS cmd to run SOM_MAP
	private boolean buildNewMap(){
		try {	runMap(SOMExeDat.getExecStrAra());	} 
		catch (IOException e){	dispMessage("SOMMapData","buildNewMap","Error running map : " + e.getMessage());	return false;}		
		return true;
	}//buildNewMap

	//load training and testing data from map results, if needing to be reloaded
	//when this method is done, trainData, testData and numTrainData and numTestData must be populated correctly
	public void assignTrainTestData() {
		//TODO need to build trainData and testData from data aggregation/loading structure - this method needs to be rewritten
		//this is only important if map is built without using built in structure to convert saved preprocced data to svn/lrn data
		dispMessage("SOMMapData","assignTrainTestData","assignTrainTestData :TODO :Need to set train and test data from saved file if not saved already");
		
		
//		trainData = new dataPoint[tmpTrainAra.length];
//		testData = new dataPoint[tmpTestAra.length];
//		numTrainData = trainData.length;
//		numTestData = testData.length;
//		System.arraycopy(tmpTrainAra, 0, trainData, 0, numTrainData);
//		System.arraycopy(tmpTestAra, 0, testData, 0, numTestData);
//		dispMessage("SOMMapData","DataLoader : Finished assigning Training and Testing data from raw data -> added " + numTrainData + " training examples and " +numTestData + " testing examples");
		int numDataVals = 0;//TODO get the # of features from training data objects
		//dataBounds = new Float[2][numDataVals];
		//todo build data bounds
	}//assignTrainTestData
	
	//initialize the SOM-facing data structures - these are used to train/consume a map.
	public void initData(){
		dispMessage("SOMMapData","initData","Init Called");
		trainData = new StraffSOMExample[0];
		testData = new StraffSOMExample[0];
		inputData = new StraffSOMExample[0];
		nodesWithEx = new HashSet<SOMMapNodeExample>();
		nodesWithNoEx = new HashSet<SOMMapNodeExample>();
		numTrainData = 0;
		numFtrs = 0;
		numInputData = 0;
		SOMDataDir = getDirNameAndBuild(straffSOMProcSubDir);
		dispMessage("SOMMapData","initData","Init Finished");
	}//initdata
	//return true if loader is done and if data is successfully loaded
	public boolean isMapDrawable(){return getFlag(loaderRtnIDX) && getFlag(mapDataLoadedIDX);}


	/**
	 * calc distance between two data points, using L2 calculation or standardized (divided by variance) euc
	 * @param a,b datapoints
	 * @param assumeZero if this is true then assume the smaller of two datapoints has a value of 0 for the missing features, otherwise only calculate distance between shared features
	 * 		(which assumes features share positions in arrays, and extra features are at tail of arrays)
	 * //passing variance (dataVar) for chi sq dist
	 * @return dist
	 */
	public double dpDistFunc(StraffSOMExample a, StraffSOMExample b, float[] dataVar){
		float[] bigFtrs, smlFtrs, bigSclFtrs, smlSclFtrs; 
		float[] aftrData = a.getFtrs(), bftrData = b.getFtrs();
		float[] astdFtrData = a.getStdFtrs(), bstdFtrData = b.getStdFtrs();
		
		if (aftrData.length >= bftrData.length) {
			bigFtrs = aftrData;
			smlFtrs = bftrData;
			bigSclFtrs = astdFtrData;
			smlSclFtrs = bstdFtrData;			
		} else {
			bigFtrs = bftrData;
			smlFtrs = aftrData;
			bigSclFtrs = bstdFtrData;
			smlSclFtrs = astdFtrData;		
		}
		
		switch (distType) {
			case 0 : return calcPtDist(bigFtrs, smlFtrs); 					//dflt dist measure - l2 norm
			case 1 : return calcPtChiDist(bigFtrs, smlFtrs, dataVar);		//chi dist measure
			case 2 : return calcPtDist(bigSclFtrs, smlSclFtrs);				//scaled dist measure
			default : return calcPtDist(bigFtrs, smlFtrs);					//dflt dist measure - l2 norm
		}
	}//distFunc	
	//passing variance for chi sq dist
	private double calcPtChiDist(float[] bigFtrs, float[] smFtrs, float[] dataVar){
		return Math.sqrt(calcSqPtChiDist(bigFtrs, smFtrs,dataVar));
	}//calcPtDist
	
	private double calcPtDist(float[] bigFtrs, float[] smFtrs){
		return Math.sqrt(calcSqPtDist(bigFtrs, smFtrs));
	}//calcPtDist
	//passing variance for chi sq dist
	private float calcSqPtChiDist(float[] bigFtrs, float[] smFtrs, float[] dataVar){
		float res = 0, diff;
		if(getFlag(mapSetSmFtrZeroIDX)){	for(int i=(bigFtrs.length-1); i>= smFtrs.length;--i){res += bigFtrs[i] * bigFtrs[i];}}		
		for(int i =0; i<smFtrs.length; ++i){			diff = bigFtrs[i] - smFtrs[i];res +=  (diff * diff)/dataVar[i];}			
		return res;	
	}//calcPtDist
	
	private float calcSqPtDist(float[] bigFtrs, float[] smFtrs){
		float res = 0, diff;
		if(getFlag(mapSetSmFtrZeroIDX)){	for(int i=(bigFtrs.length-1); i>= smFtrs.length;--i){res += bigFtrs[i] * bigFtrs[i];}}		
		for(int i =0; i<smFtrs.length; ++i){			diff = bigFtrs[i] - smFtrs[i];res += diff * diff;}			
		return res;	
	}//calcPtDist

	public boolean isToroidal(){
		if(null==SOMExeDat){return false;}
		return SOMExeDat.isToroidal();
	}

	//set all training/testing data save flags to val
	private void setAllTrainDatSaveFlags(boolean val) {
		setFlag(denseTrainDataSavedIDX, val);
		setFlag(sparseTrainDataSavedIDX, val);
		setFlag(testDataSavedIDX, val);		
	}
	
	//clear out existing prospect map
	public void resetProspectMap() {
		prospectMap = new ConcurrentSkipListMap<String, ProspectExample>();
		setFlag(rawPrspctEvDataProcedIDX, false);
		setAllTrainDatSaveFlags(false);
	}//resetProspectMap
	//clear out existing prospect map
	public void resetProductMap() {
		productMap = new ConcurrentSkipListMap<String, ProductExample>();
		setFlag(rawProducDataProcedIDX, false);
		setAllTrainDatSaveFlags(false);
	}//resetProspectMap
	
	//fromCSVFiles : whether loading data from csv files or from SQL calls
	//eventsOnly : only use examples with event data to train
	//append : whether to append to existing data values or to load new data
	public void loadAllRawData(boolean fromCSVFiles, boolean eventsOnly, boolean appendToExisting) {
		dispMessage("SOMMapData","loadAllRawData","Start loading and processing raw data");
		//TODO remove this when SQL support is implemented
		if(!fromCSVFiles) {
			dispMessage("SOMMapData","loadAllRawData","WARNING : SQL-based raw data queries not yet implemented.  Use CSV-based raw data to build training data set instead");
			return;
		}
		boolean singleThread=numUsableThreads<=1;//this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		if(singleThread) {
			for (int idx=0;idx<numStraffDataTypes;++idx) {
				String[] loadRawDatStrs = getLoadRawDataStrs(fromCSVFiles,idx);
				boolean[] flags = new boolean[] {fromCSVFiles,straffRawDatUsesJSON[idx], straffRawDatDebugLoad[idx]};
				loadRawDataVals(loadRawDatStrs, flags,idx);
			}
		} else {
			buildStraffDataLoaders();
			for (int idx=0;idx<numStraffDataTypes;++idx) {//build a thread per data type //straffRawDatDebugLoad
				String[] loadRawDatStrs = getLoadRawDataStrs(fromCSVFiles,idx);
				boolean[] flags = new boolean[] {fromCSVFiles,straffRawDatUsesJSON[idx], straffRawDatDebugLoad[idx]};
				straffDataLoaders.get(idx).setLoadData(this, loadRawDatStrs,  flags, straffObjFlagIDXs[idx]);
			}
			//blocking on callables for multithreaded
			try {straffDataLdrFtrs = th_exec.invokeAll(straffDataLoaders);for(Future<Boolean> f: straffDataLdrFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		
		}
		//process loaded data
		//dbgLoadedData(tcTagsIDX);
		procRawLoadedData(eventsOnly, appendToExisting);		
		dispMessage("SOMMapData","loadAllRawData","Finished loading and processing raw data");
	}//loadAllRawData
	
	private void dbgLoadedData(int idx) {
		ArrayList<BaseRawData> recs = rawDataArrays.get(straffDataFileNames[idx]);
		for  (BaseRawData rec : recs) {if(rec.rawJpMapOfArrays.size() > 1) {dispMessage("SOMMapData","dbgLoadedData",straffDataFileNames[idx] + " : " + rec.toString());}}			
	}
	
	//any subdirectories need to already exist for this to not cause an error
	//this returns the file type in location 0 and the fully qualified file path to the csv data/sql confirguration csv in idx1
	private String[] getLoadRawDataStrs(boolean fromFiles, int idx) {
		String baseFName = straffDataFileNames[idx];
		dispLoadMessage(baseFName, fromFiles);
		String dataLocStrData = "";//fully qualified path to either csv of data or csv of connection string info for sql to data
		if (fromFiles) {
			dataLocStrData = straffBasefDir + straffSourceCSVSubDir + baseFName + File.separator + baseFName+".csv";
		} else {//SQL connection configuration needs to be determined/designed
			dataLocStrData = straffBasefDir + straffSQLProcSubDir + "sqlConnData_"+baseFName+".csv";
			dispMessage("SOMMapData","getLoadRawDataStrs","Need to construct appropriate sql connection info and put in text config file : " + dataLocStrData);
		}
		return new String[] {baseFName,dataLocStrData};
	}//getLoadRawDataStrs

	private void dispLoadMessage(String orig, boolean fromFiles) {dispMessage("SOMMapData","dispLoadMessage","Load Raw " +orig + " data " + (fromFiles ? "from CSV files" : "from SQL Calls"));}
	//will instantiate specific loader class object and load the data specified by idx, either from csv file or from an sql call described by csvFile
	private void loadRawDataVals(String[] loadRawDatStrs, boolean[] flags, int idx){//boolean _isFileLoader, String _fileNameAndPath
		//single threaded implementation
		@SuppressWarnings("rawtypes")
		Class[] args = new Class[] {boolean.class, String.class};//classes of arguments for loader ctor		
		try {
			@SuppressWarnings("unchecked")
			StraffordDataLoader loaderObj = (StraffordDataLoader) straffObjLoaders[idx].getDeclaredConstructor(args).newInstance(flags[0], loadRawDatStrs[1]);
			loaderObj.setLoadData(this, loadRawDatStrs,  flags, straffObjFlagIDXs[idx]);
			ArrayList<BaseRawData> datAra = loaderObj.execLoad();
			rawDataArrays.put(loadRawDatStrs[0], datAra);
			setFlag(straffObjFlagIDXs[idx], true);			//set flag corresponding to this type of data to be loaded
		} catch (Exception e) {			e.printStackTrace();}		
		
		setFlag(straffObjFlagIDXs[idx], true);			//set flag corresponding to this type of data to be loaded
	}//loadRawDataVals	
	
	//this will retrieve a subdirectory name under the main directory of this project and build the subdir if it doesn't exist
	//subdir assumed to have file.separator already appended (might not be necessary)
	private String getDirNameAndBuild(String subdir) {return getDirNameAndBuild(straffBasefDir,subdir);} 
	//baseDir must exist already
	private String getDirNameAndBuild(String baseDir, String subdir) {
		String dirName = baseDir +subdir;
		File directory = new File(dirName);
	    if (! directory.exists()){ directory.mkdir(); }
		return dirName;
	}//getDirNameAndBuild
	
	//build prospect data directory structures based on current date
	private String[] buildPrspctDataCSVFNames(boolean eventsOnly, String _desSuffix) {
		String[] dateTimeStrAra = getDateTimeString(false, "_", instancedNow);
		String subDir = "preprocData_" + dateTimeStrAra[0] + File.separator;
		return buildProccedDataCSVFNames(subDir, eventsOnly,_desSuffix);
	}//buildPrspctDataCSVFNames
	
	//build the file names for the csv files used to save intermediate data from db that has been partially preprocessed
	private String[] buildProccedDataCSVFNames(String subDir, boolean eventsOnly, String _desSuffix) {
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
	
	//return the fully qualified directory to the most recent prospect data as specified in config file
	//TODO replace this with info from global project config data 
	private String getRawDataDesiredDirName() {
		return "default" + File.separator;
	}
	public void loadAllPreProccedData(boolean eventsOnly) {
		//		boolean singleThread=numUsableThreads<=1;//this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		dispMessage("SOMMapData","loadAllPreProccedData","Begin loading preprocced data");
		//perform the f
		loadAllPropsectMapData(eventsOnly);
		loadAllProductMapData();
		finishSOMExampleBuild(prospectMap);		
		dispMessage("SOMMapData","loadAllPreProccedData","Finished loading preprocced data");
	}
	
	//load prospect mapped training data into StraffSOMExamples from disk
	private void loadAllPropsectMapData(boolean eventsOnly) {
		//perform in multiple threads if possible
		dispMessage("SOMMapData","loadAllPropsectMapData","Loading all prospect map data " + (eventsOnly ? "that only have event-based training info" : "that have any training info (including only prospect jpg/jp specification)"));
		//clear out current prospect data
		resetProspectMap();
		String desSubDir = getRawDataDesiredDirName();
		String[] loadSrcFNamePrefixAra = buildProccedDataCSVFNames(desSubDir, eventsOnly, "prospectMapSrcData");
		
		String fmtFile = loadSrcFNamePrefixAra[0]+"_format.csv";
		String[] loadRes = loadFileIntoStringAra(fmtFile, "Format file loaded", "Format File Failed to load");
		int numPartitions = 0;
		try {
			numPartitions = Integer.parseInt(loadRes[0].split(" : ")[1].trim());
		} catch (Exception e) {e.printStackTrace(); dispMessage("SOMMapData","loadAllPropsectMapData","Due to error with not finding format file : " + fmtFile+ " no data will be loaded."); return;} 
		
		boolean singleThread=numUsableThreads<=1;//this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		if(singleThread) {
			for (int i=0; i<numPartitions;++i) {
				String dataFile = loadSrcFNamePrefixAra[0]+"_"+i+".csv";
				String[] csvLoadRes = loadFileIntoStringAra(dataFile, "Data file " + i +" loaded", "Data File " + i +" Failed to load");
				//ignore first entry - header
				for (int j=1;j<csvLoadRes.length; ++j) {
					String str = csvLoadRes[j];
					int pos = str.indexOf(',');
					String oid = str.substring(0, pos);
					ProspectExample ex = new ProspectExample(this, oid, str);
					prospectMap.put(oid, ex);			
				}
			}			
		} else {//load each file in its own csv
			List<Future<Boolean>> preProcLoadFtrs = new ArrayList<Future<Boolean>>();
			List<straffCSVDataLoader> preProcLoaders = new ArrayList<straffCSVDataLoader>();
			for (int i=0; i<numPartitions;++i) {				
				preProcLoaders.add(new straffCSVDataLoader(this, i, loadSrcFNamePrefixAra[0]+"_"+i+".csv", "Data file " + i +" loaded", "Data File " + i +" Failed to load"));
			}
			try {preProcLoadFtrs = th_exec.invokeAll(preProcLoaders);for(Future<Boolean> f: preProcLoadFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }					
		}
	
		dispMessage("SOMMapData","loadAllPropsectMapData","Finished loading and preprocessing all local prospect map data and calculating features.  Number of entries in prospectMap : " + prospectMap.size());
	}//loadAllPropsectMapData
	
	//load product pre-procced data from tc_taggings source
	private void loadAllProductMapData() {
		dispMessage("SOMMapData","loadAllProductMapData","Loading all product map data");
		//clear out current product data
		resetProductMap();
		String desSubDir = getRawDataDesiredDirName();
		String[] loadSrcFNamePrefixAra = buildProccedDataCSVFNames(desSubDir, false, "productMapSrcData");
		String dataFile =  loadSrcFNamePrefixAra[0]+".csv";
		String[] csvLoadRes = loadFileIntoStringAra(dataFile, "Product Data file loaded", "Product Data File Failed to load");
		//ignore first entry - header
		for (int j=1;j<csvLoadRes.length; ++j) {
			String str = csvLoadRes[j];
			int pos = str.indexOf(',');
			String oid = str.substring(0, pos);
			ProductExample ex = new ProductExample(this, oid, str);
			productMap.put(oid, ex);			
		}		
		dispMessage("SOMMapData","loadAllProductMapData","Finished loading and preprocessing all local prospect map data and calculating features.  Number of entries in productMap : " + productMap.size());
	}//loadAllProductMapData
	
	//write all prospect map data to a csv to be able to be reloaded to build training data from, so we don't have to re-read database every time
	public void saveAllProspectMapData(boolean eventsOnly) {
		dispMessage("SOMMapData","saveAllProspectMapData","Saving all prospect map data : " + prospectMap.size() + " examples to save.");
		String[] saveDestFNamePrefixAra = buildPrspctDataCSVFNames(eventsOnly, "prospectMapSrcData");
		ArrayList<ArrayList<String>> csvRes = new ArrayList<ArrayList<String>>();
		ArrayList<String> csvResTmp = new ArrayList<String>();		
		int counter = 0;
		ProspectExample ex1 = prospectMap.get(prospectMap.firstKey());
		String hdrStr = ex1.getRawDescColNamesForCSV();
		csvResTmp.add( hdrStr);
		int nameCounter = 0;
		for (ProspectExample ex : prospectMap.values()) {			
			csvResTmp.add(ex.getRawDescrForCSV());
			++counter;
			if(counter % preProcDatPartSz ==0) {
				dispMessage("SOMMapData","saveAllProspectMapData","Done Building String Array : " +(nameCounter++));
				counter = 0;
				csvRes.add(csvResTmp); 
				csvResTmp = new ArrayList<String>();
				csvResTmp.add( hdrStr);
			}
		}
		csvRes.add(csvResTmp);
		//save array of arrays of strings, partitioned and named so that no file is too large
		nameCounter = 0;
		for (ArrayList<String> csvResSubAra : csvRes) {		
			dispMessage("SOMMapData","saveAllProspectMapData","Saving Pre-procced Prospect data String array : " +nameCounter);
			saveStrings(saveDestFNamePrefixAra[0]+"_"+nameCounter+".csv", csvResSubAra);
			++nameCounter;
		}
		//save the data in a format file
		String[] data = new String[] {"Number of file partitions for " + saveDestFNamePrefixAra[1] +" data : "+ nameCounter + "\n"};
		saveStrings(saveDestFNamePrefixAra[0]+"_format.csv", data);		
		dispMessage("SOMMapData","saveAllProspectMapData","Finished saving all prospect map data");
	}//saveAllProspectMapData	
	
	public void saveAllProductMapData() {
		dispMessage("SOMMapData","saveAllProductMapData","Saving all product map data : " + productMap.size() + " examples to save.");
		String[] saveDestFNamePrefixAra = buildPrspctDataCSVFNames(false, "productMapSrcData");
		ArrayList<String> csvResTmp = new ArrayList<String>();		
		ProductExample ex1 = productMap.get(productMap.firstKey());
		String hdrStr = ex1.getRawDescColNamesForCSV();
		csvResTmp.add( hdrStr);	
		for (ProductExample ex : productMap.values()) {			
			csvResTmp.add(ex.getRawDescrForCSV());
		}
		saveStrings(saveDestFNamePrefixAra[0]+".csv", csvResTmp);		
		dispMessage("SOMMapData","saveAllProductMapData","Finished saving all product map data");
	}//saveAllProductMapData

	//finish building the prospect map - finalize each prospect example and then perform calculation to derive weight vector
	private void finishSOMExampleBuild(ConcurrentSkipListMap<String, ProspectExample> map) {
		//finalize builds each example's occurence structures, which describe the jp-jpg relationships found in the example
		for (ProspectExample ex : map.values()) {			ex.finalizeBuild();		}		
		//finalize build for all products - aggregates all jps seen in product
		for (ProductExample ex : productMap.values()){		ex.finalizeBuild();		}		
		
		dispMessage("SOMMapData","finishSOMExampleBuild","Begin setJPDataFromExampleData");
		//we need the jp-jpg counts and relationships dictated by the data by here.
		setJPDataFromExampleData(map);
		dispMessage("SOMMapData","finishSOMExampleBuild","End setJPDataFromExampleData");// | Start processHoldOutOptRecs");		

		dispMessage("SOMMapData","finishSOMExampleBuild","Begin buildFeatureVector prospects");
		for (ProspectExample ex : map.values()) {			ex.buildFeatureVector();	}
		dispMessage("SOMMapData","finishSOMExampleBuild","End buildFeatureVector prospects");
		
		dispMessage("SOMMapData","finishSOMExampleBuild","Begin buildFeatureVector products");
		for (ProductExample ex : productMap.values()) {		ex.buildFeatureVector();	}
		dispMessage("SOMMapData","finishSOMExampleBuild","End buildFeatureVector products");
		
		
		//now get mins and diffs from calc object
		diffsVals = ftrCalcObj.getDiffsBndsAra();
		minsVals = ftrCalcObj.getMinBndsAra();
		for (ProspectExample ex : map.values()) {			ex.buildPostFeatureVectorStructs();		}//this builds std ftr vector for prospects, once diffs and mins are set - not necessary for products, buildFeatureVector for products builds std ftr vec
		setFlag(rawPrspctEvDataProcedIDX, true);
		dispMessage("SOMMapData","finishSOMExampleBuild","Finished calculating prospect feature vectors");
	}//finishSOMExampleBuild
	
	
	//useUnmoddedDat = 0, useScaledDat = 1, useNormedDat
	private String getDataTypeNameFromInt(int dataFrmt) {
		switch(dataFrmt) {
		case useUnmoddedDat : {return "unModFtrs";}
		case useScaledDat : {return "stdFtrs";}
		case useNormedDat : {return "normFtrs";}
		default : {return null;}		//unknown data frmt type
		}
	}
	
	//using the passed map, build the testing and training data partitions
	protected void buildTestTrainFromInput(ConcurrentSkipListMap<String, ProspectExample> dataMap, float testTrainPartition) {
		dispMessage("SOMMapData","buildTestTrainFromInput","Building Training and Testing Partitions.");
		//set inputdata array to be all prospect map examples
		inputData = dataMap.values().toArray(new StraffSOMExample[0]);			
		numTrainData = (int) (inputData.length * testTrainPartition);		
		
		numTestData = inputData.length - numTrainData;
		//trainData, inputData, testData;
		trainData = new StraffSOMExample[numTrainData];	
		dispMessage("SOMMapData","buildTestTrainFromInput","# of training examples : " + numTrainData + " inputData size : " + inputData.length);
		//numTrainData and numTestData 
		for (int i=0;i<trainData.length;++i) {trainData[i]=inputData[i];}
		testData = new StraffSOMExample[numTestData];
		for (int i=0;i<testData.length;++i) {testData[i]=inputData[i+numTrainData];}	
		dispMessage("SOMMapData","buildTestTrainFromInput","Finished Building Training and Testing Partitions.  Train size : " + numTrainData+ " Testing size : " + numTestData + ".");
	}//buildTestTrainFromInput
	
	//build training data from current global prospect data map
	//and save them to .lrn format 
	//typeOfTrainData : 
	//		0 : regular features, 
	//		1 : standardized features (across each ftr component transformed to 0->1), 
	//		2 : normalized features (each feature vector has magnitude 1)
	public void buildAndSaveTrainingData(int dataFrmt, float testTrainPartition) {
		if (prospectMap.size() == 0) {
			dispMessage("SOMMapData","buildAndSaveTrainingData","ProspectMap data not loaded/built, unable to build training data for SOM");
			return;
		}
		//build SOM data
		buildTestTrainFromInput(prospectMap, testTrainPartition);	
		//build file names, including info for data type used to train map
		setSOM_ExpFileNames(inputData.length, numTrainData, numTestData, dataFrmt);
		dispMessage("SOMMapData","buildAndSaveTrainingData","Saving data to training file : Starting to save training/testing data partitions ");
		launchTestTrainSaveThrds(dataFrmt);

//		String saveFileName = "";
		//call threads to instance and save different file formats
//		if (numTrainData > 0) {
//			//saveFileName = this.getSOMMapLRNFileName();
//			//th_exec.execute(new straffDataWriter(this, dataFrmt,denseTrainDataSavedIDX, saveFileName,"denseLRNData",trainData));	//lrn dense format - strafford project doesn't use dense format
//			saveFileName = this.getSOMMapSVMFileName();
//			th_exec.execute(new straffDataWriter(this, dataFrmt,sparseTrainDataSavedIDX, saveFileName, "sparseSVMData",trainData));	
//		}
//		if (numTestData > 0) {
//			//th_exec.execute(new straffDataWriter(this, dataFrmt, "denseTest" ,testData));	
//			saveFileName = this.getSOMMapTestFileName();
//			th_exec.execute(new straffDataWriter(this, dataFrmt, testDataSavedIDX, saveFileName, "denseCSVData" ,testData));				
//		}
		//save mins/maxes so this file data be reconstructed
		//save diffs and mins - csv files with each field value sep'ed by a comma
		//boundary region for training data
		String diffStr = "", minStr = "";
		for(int i =0; i<minsVals.length; ++i){
			minStr += String.format("%1.7g", minsVals[i]) + ",";
			diffStr += String.format("%1.7g", diffsVals[i]) + ",";
		}
		String minsFileName = getSOMMapMinsFileName();
		String diffsFileName = getSOMMapDiffsFileName();				
		saveStrings(minsFileName,new String[]{minStr});		
		saveStrings(diffsFileName,new String[]{diffStr});		
		dispMessage("SOMMapData","buildAndSaveTrainingData","Strafford Prospects Mins and Diffs Files Saved");	
	}//buildAndSaveTrainingData	
		
	//set names to be pre-calced results to test map
	private void setStraffNamesDBG(int _numSmpls, int _numTrain, int _numTest, int _dataFrmt){ //StraffSOM_2018_11_09_14_45_DebugRun
		//int _numSmpls = 458654, _numTrain = 412788, _numTest = 45866; 		//Calendar now = Calendar.getInstance();//F:\Strafford Project\SOM\StraffSOM_2018_10_24_12_37
		//String nowDirTmp = straffSOMProcSubDir+ "StraffSOM_"+getDateTimeString(true,false,"_", now)+File.separator;
		//String fileNow = "10_24_12_37";
		//Train_straff_Smp_412788_of_458654_typ_stdFtrs_Dt_11_26_09_50
		//Test_straff_Smp_45866_of_458654_typ_stdFtrs_Dt_11_26_09_50
		String fileNow = "11_27_11_42";
		String nowDir = getDirNameAndBuild(straffSOMProcSubDir+"StraffSOM_2018_"+fileNow+"_DebugRun"+File.separator);
		String dType = getDataTypeNameFromInt(_dataFrmt);
		String[] tmp = {"Out_"+SOMProjName+"_Smp_"+_numSmpls,
				"Train_"+SOMProjName+"_Smp_"+_numTrain+"_of_"+_numSmpls+"_typ_" +dType + "_Dt_"+fileNow+".lrn",
				"Train_"+SOMProjName+"_Smp_"+_numTrain+"_of_"+_numSmpls+"_typ_" + dType+"_Dt_"+fileNow+".svm",				
				"Test_"+SOMProjName+"_Smp_"+_numTest+"_of_"+_numSmpls+"_typ_" +dType +"_Dt_"+fileNow+".csv",
				"Mins_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+"_typ_" +dType +".csv",
				"Diffs_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+"_typ_" +dType +".csv",
				"SOMImg_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+"_typ_" +dType +".png",
				"SOM_EXP_Format_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+".txt"
				};

		SOMFileNamesAra = new String[tmp.length];
		for(int i =0; i<SOMFileNamesAra.length;++i){	
			//build dir as well
			SOMFileNamesAra[i] = nowDir + tmp[i];
		}				
	}//setSOM_ExpFileNames
	
	private void launchTestTrainSaveThrds(int dataFrmt) {
		String saveFileName = "";
		List<Future<Boolean>> straffSOMDataWriteFutures = new ArrayList<Future<Boolean>>();
		List<straffDataWriter> straffSOMDataWrite = new ArrayList<straffDataWriter>();
		//call threads to instance and save different file formats
		if (numTrainData > 0) {
			//saveFileName = this.getSOMMapLRNFileName();
			//th_exec.execute(new straffDataWriter(this, dataFrmt,denseTrainDataSavedIDX, saveFileName,"denseLRNData",trainData));	//lrn dense format - strafford project doesn't use dense format
			saveFileName = this.getSOMMapSVMFileName();
			straffSOMDataWrite.add(new straffDataWriter(this, dataFrmt,sparseTrainDataSavedIDX, saveFileName, "sparseSVMData",trainData));	
		}
		if (numTestData > 0) {
			//th_exec.execute(new straffDataWriter(this, dataFrmt, "denseTest" ,testData));	
			saveFileName = this.getSOMMapTestFileName();
			straffSOMDataWrite.add(new straffDataWriter(this, dataFrmt, testDataSavedIDX, saveFileName, "denseCSVData" ,testData));				
		}
		try {straffSOMDataWriteFutures = th_exec.invokeAll(straffSOMDataWrite);for(Future<Boolean> f: straffSOMDataWriteFutures) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		

		
	}
	
	//load preproc csv and build training and testing partitions
	public void dbgLoadCSVBuildDataTrainMap(boolean useOnlyEvents, int dataFrmt, float testTrainPartition) {
		loadAllPreProccedData(useOnlyEvents);
		//build SOM data
		buildTestTrainFromInput(prospectMap, testTrainPartition);	
		//build file names, including info for data type used to train map
		setSOM_ExpFileNames(inputData.length, numTrainData, numTestData, dataFrmt);
		dispMessage("SOMMapData","dbgLoadCSVBuildDataTrainMap","Saving data to training file : Starting to save training/testing data partitions ");
		launchTestTrainSaveThrds(dataFrmt);
		//save mins/maxes so this file data be reconstructed
		//save diffs and mins - csv files with each field value sep'ed by a comma
		//boundary region for training data
		String diffStr = "", minStr = "";
		for(int i =0; i<minsVals.length; ++i){
			minStr += String.format("%1.7g", minsVals[i]) + ",";
			diffStr += String.format("%1.7g", diffsVals[i]) + ",";
		}
		String minsFileName = getSOMMapMinsFileName();
		String diffsFileName = getSOMMapDiffsFileName();				
		saveStrings(minsFileName,new String[]{minStr});		
		saveStrings(diffsFileName,new String[]{diffStr});		
		dispMessage("SOMMapData","buildAndSaveTrainingData","Strafford Prospects Mins and Diffs Files Saved");	
	}//
	
	//load the data used to build a map - NOTE this may break if different data is used to build the map than the current data
	public void dbgBuildExistingMap() {
		//load data into preproc
		loadAllPreProccedData(true);
		int _numSmpls = 458654, _numTrain = 412788, _numTest = 45866; 
		//TOOD replace this when saved file with appropriate format
		int _dataFrmt = 1;//1 means trained with std'ized data
		//build SOM data partitions from loaded data used to train map
		float testTrainPartition = _numTrain/(1.0f*_numSmpls);
		buildTestTrainFromInput(prospectMap, testTrainPartition);	
		
		//now load map values from pre-trained map using this data - IGNORES VALUES SET IN UI		
		setStraffNamesDBG(_numSmpls, _numTrain, _numTest, _dataFrmt);
		String lrnFileName = getSOMMapLRNFileName(), 		//dense training data .lrn file
		svmFileName = getSOMMapSVMFileName(),		//sparse training data svm file name
		testFileName = getSOMMapTestFileName(),		//testing data .csv file
		minsFileName = getSOMMapMinsFileName(),		//mins data percol .csv file
		diffsFileName = getSOMMapDiffsFileName(),	//diffs data percol .csv file
		
		outFilePrfx = getSOMMapOutFileBase() + "_x10_y10_k2";
		//build new map descriptor and execute
		//structure holding SOM_MAP specific cmd line args and file names and such
		//now load new map data and configure SOMMapData obj to hold all appropriate data
		/**
		 * load data to represent map results
		 * @param cFN class file name
		 * @param diffsFileName per-feature differences file name
		 * @param minsFileName per-feature mins file name
		 * @param lrnFileName som training data file name 
		 * @param outFilePrfx
		 * @param csvOutBaseFName file name prefix used to save class data in multiple formats to csv files
		 */
		String csvOutBaseFName = outFilePrfx + "_outCSV";
		//initData();			
		setFlag(loaderRtnIDX, false);
		projConfigData = new SOMProjConfigData(this);
		projConfigData.setAllFileNames(diffsFileName,minsFileName, lrnFileName, outFilePrfx, csvOutBaseFName);
		dispMessage("SOMMapData","dbgBuildExistingMap","Current projConfigData before dataLoader Call : " + projConfigData.toString());
		th_exec.execute(new SOMDataLoader(this,true,projConfigData,_dataFrmt));//fire and forget load task to load		
		
	}//dbgBuildExistingMap
		
//	//called from StraffSOMExample after all occurences are built
//	//this will add entries into an occ map with the passed date and opt value - opts are possible with no jps specified, if so this means all
//	//not making occurences for negative opts, but for non-negative opts we can build the occurence information for all jps/jpgs.  This will be used for training data
//	//only
//	private void buildAllJpgJpOccMap(TreeMap<Integer, jpOccurrenceData> jpOccMap, Date eventDate, int opt) {
//		if(jpgsToJps.size()==0) {		dispMessage("SOMMapData","buildAllJpgJpOccMap : Error - attempting to add all jpg's/jp's  to occurence data but haven't been aggregated yet."); return;}
//		for (Integer jpg : jpgsToJps.keySet()) {
//			TreeSet<Integer> jps = jpgsToJps.get(jpg);
//			for (Integer jp : jps) {
//				jpOccurrenceData jpOcc = jpOccMap.get(jp);
//				if (jpOcc==null) {jpOcc = new jpOccurrenceData(jp, jpg);}
//				jpOcc.addOccurence(eventDate, opt);		
//				//add this occurence object to map at idx jp
//				jpOccMap.put(jp, jpOcc);
//			}
//		}
//	}//buildAllJpgJpOccMap
	
	public int getLenJpByIdxStr() {		return jpJpgrpMon.getLenJpByIdxStr();	}	
	public int getLenJpGrpByIdxStr(){	return jpJpgrpMon.getLenJpGrpByIdxStr(); }
	
	public String getJpByIdxStr(int idx) {return jpJpgrpMon.getJpByIdxStr(idx);}	
	public String getJpGrpByIdxStr(int idx) {return jpJpgrpMon.getJpGrpByIdxStr(idx);}
	
	
	//this will set the current jp->jpg data maps based on passed prospect data map
	//When acquiring new data, this must be performed after all data is loaded, but before
	//the prospect data is finalized and actual map is built due to the data finalization 
	//requiring a knowledge of the entire dataset to build weights appropriately
	private void setJPDataFromExampleData(ConcurrentSkipListMap<String, ProspectExample> map) {
		//object to manage all jps and jpgroups seen in project
		jpJpgrpMon.setJPDataFromExampleData(map, productMap);
		
		numFtrs = jpJpgrpMon.getNumFtrs();
		//rebuild calc object since feature terrain might have changed
		String calcDirName = this.getDirNameAndBuild(straffCalcInfoSubDir);
		
		ftrCalcObj = new StraffWeightCalc(this, calcDirName + calcWtFileName, jpJpgrpMon);
	}//setJPDataFromProspectData	

	//process all events into training examples
	private void procRawEventData(ConcurrentSkipListMap<String, ProspectExample> tmpProspectMap,ConcurrentSkipListMap<String, ArrayList<BaseRawData>> dataArrays, boolean saveBadRecs) {			
		dispMessage("SOMMapData","procRawEventData","Starting.");
		for (int idx = 1; idx <straffDataFileNames.length;++idx) {
			if (idx == tcTagsIDX) {continue;}//dont' handle tcTags here
			ArrayList<BaseRawData> events = dataArrays.get(straffDataFileNames[idx]);
			String eventType = straffDataFileNames[idx].split("_")[0];		
			String eventBadFName = straffBasefDir + straffDataFileNames[idx] +"_bad_OIDs.csv";		
			ArrayList<String> badEventOIDs = new ArrayList<String>();
			HashSet<String> uniqueBadEventOIDs = new HashSet<String>();
			
			if (idx == optEvntIDX){
				//to monitor bad event formats - events with OIDs that are not found in prospect DB data
				TreeMap<String, Integer> badOptOIDsOps = new TreeMap<String, Integer>();		
				for (BaseRawData obj : events) {
					ProspectExample ex = tmpProspectMap.get(obj.OID);
					if (ex == null) {
						if (saveBadRecs) {//means no prospect object corresponding to the OID in this event
							badEventOIDs.add(obj.OID);
							uniqueBadEventOIDs.add(obj.OID);
							badOptOIDsOps.put(obj.OID, ((OptEvent)obj).getOptType());
						}
						continue;}
					ex.addObj(obj, idx);
				}//for every event
				
			} else {
				for (BaseRawData obj : events) {
					ProspectExample ex = tmpProspectMap.get(obj.OID);
					if (ex == null) {
						if (saveBadRecs) {//means no prospect object corresponding to the OID in this event
							badEventOIDs.add(obj.OID);
							uniqueBadEventOIDs.add(obj.OID);
						}
						continue;}
					ex.addObj(obj, idx);
				}//for every event
			}			
			if (saveBadRecs && (badEventOIDs.size() > 0)) {
				saveStrings(eventBadFName, uniqueBadEventOIDs.toArray(new String[0]));		
				dispMessage("SOMMapData","procRawEventData","# of "+eventType+" events without corresponding prospect records : "+badEventOIDs.size() + " | # Unique bad "+eventType+" event prospect OID refs (missing OIDs in prospect) : "+uniqueBadEventOIDs.size());
			}
		}
		dispMessage("SOMMapData","procRawEventData","Finished.");
	}//procRawEventData
	
	//convert raw tc taggings table data to product examples
	private void procRawProductData(ArrayList<BaseRawData> tcTagRawData) {
		dispMessage("SOMMapData","procRawProductData","Starting.");
		productMap = new ConcurrentSkipListMap<String, ProductExample>();
		for (BaseRawData tcDat : tcTagRawData) {
			ProductExample ex = new ProductExample(this, (TcTagData)tcDat);
			productMap.put(ex.OID, ex);
		}
		dispMessage("SOMMapData","procRawProductData","Finished.");		
	}//procRawProductData
	
	//this will scale unmodified ftr data - scaled or normalized data does not need this
	public int scaleUnfrmttedFtrData(Float ftrVal, Integer jpIDX) {
		//what to use to scale features - if dataFmt == 0 then need to use mins/maxs, otherwise ftrs can be just treated as if they are scaled already - either normalized or standardized will be between 0-1 for all ftr values
		Float min = this.minsVals[jpIDX], 
				diffs = this.diffsVals[jpIDX],
				calcVal = (ftrVal - min)/diffs;
		return Math.round(255 * calcVal);
	}//scaleUnfrmttedFtrData
		
	//this will go through all the prospects, opts, and events and build a map of prospectExample keyed by prospect OID and holding all the known data
	private void procRawLoadedData(boolean eventsOnly, boolean appendToExistingData){
		if(appendToExistingData) {
			//TODO if we want to append prospect data to existing data map			
			setAllTrainDatSaveFlags(false);
		} else {
			resetProspectMap();
			resetProductMap();
		}
		dispMessage("SOMMapData","procRawLoadedData","Start Processing all loaded data");
		if (!(getFlag(prospectDataLoadedIDX) && getFlag(optDataLoadedIDX) && getFlag(orderDataLoadedIDX))){//not all data loaded, don't process 
			System.out.println("Can't build data examples until prospect, opt event and order event data is all loaded");
			return;
		}
		//build prospectMap - first get prospect data and add to map
		ArrayList<BaseRawData> prospects = rawDataArrays.get(straffDataFileNames[prspctIDX]);
		ConcurrentSkipListMap<String, ProspectExample> tmpProspectMap = new ConcurrentSkipListMap<String, ProspectExample>();
		for (BaseRawData prs : prospects) {
			 ProspectExample ex = prospectMap.get(prs.OID);
			 if (ex == null) {ex = new ProspectExample(this, (prospectData) prs);}
			 else {dispMessage("SOMMapData","procRawLoadedData","Prospect with OID : "+  prs.OID + " existed in map already and was replaced.");}
			 tmpProspectMap.put(ex.OID, ex);
		}
		//add all events to prospects
		procRawEventData(tmpProspectMap, rawDataArrays, true);
		//now handle products
		procRawProductData(rawDataArrays.get(straffDataFileNames[tcTagsIDX]));
		//to clear up memory
		rawDataArrays = new ConcurrentSkipListMap<String, ArrayList<BaseRawData>>();
		//finalize each prospect record, aggregate data-driven static vals, rebuild ftr vectors
		finishSOMExampleBuild(tmpProspectMap);
	
		//save all data here,clear rawDataArrays, reset raw data array flags
		//build actual prospect map only from prospectExamples that hold trainable information
		//need to have every entered prospect example finalized before this - the finalization process is necessary to determine if a good example or not
		if(eventsOnly) {//only records with events will be used to train
			for (String OID : tmpProspectMap.keySet()) {
				ProspectExample ex = tmpProspectMap.get(OID);
				if (ex.isTrainableRecordEvent()) {			prospectMap.put(OID, ex);		} 
			}
		} else {//any jpgs/jps present will be used to train
			for (String OID : tmpProspectMap.keySet()) {
				ProspectExample ex = tmpProspectMap.get(OID);
				if (ex.isTrainableRecord()) {				prospectMap.put(OID, ex);			}
			}			
		}
		dispMessage("SOMMapData","procRawLoadedData","Raw Records Unique OIDs presented : " + tmpProspectMap.size()+" | Records found with trainable " + (eventsOnly ? " events " : "") + " info : " + prospectMap.size());
		//setAllFlags(new int[] {prospectDataLoadedIDX, optDataLoadedIDX, orderDataLoadedIDX}, false);
		setFlag(rawPrspctEvDataProcedIDX, true);
		setFlag(rawProducDataProcedIDX, true);
		saveAllProspectMapData(eventsOnly);
		saveAllProductMapData();
		dispMessage("SOMMapData","procRawLoadedData","Finished processing all loaded data");
	}//procRawLoadedData
	
	//show first numToShow elemens of array of BaseRawData, either just to console or to applet window
	private void dispRawDataAra(ArrayList<BaseRawData> sAra, int numToShow) {
		if (sAra.size() < numToShow) {numToShow = sAra.size();}
		for(int i=0;i<numToShow; ++i){dispMessage("SOMMapData","dispRawDataAra",sAra.get(i).toString());}
	}	
	
	public void dbgShowAllRawData() {
		int numToShow = 10;
		for (String key : rawDataArrays.keySet()) {
			dispMessage("SOMMapData","dbgShowAllRawData","Showing first "+ numToShow + " records of data at key " + key);
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
		if (null == ftrCalcObj) {	dispMessage("SOMMapData","dbgShowCalcEqs","No calc object made to display.");return;	}
		dispMessage("SOMMapData","dbgShowCalcEqs","Weight Calculation Equations : \n"+ftrCalcObj.toString());		
	}
	
	//display all currently calculated feature vectors
	public void dbgShowAllFtrVecs() {
		dispMessage("SOMMapData","dbgShowAllFtrVecs","Feature vectors of all examples : ");	
		int numBadEx = 0;
		for (ProspectExample ex : prospectMap.values()) {	
			float[] ftrData = ex.getFtrs();
			if(ex.isBadExample()) {		++numBadEx;	} else {			dispMessage("SOMMapData","dbgShowAllFtrVecs",ex.OID + " : " + getFloatAraStr(ftrData, "%.6f", 80));}
		}
		dispMessage("SOMMapData","dbgShowAllFtrVecs","# of bad examples (which have empty feature vectors and are useless for training) : " + numBadEx);
	}//loadAllPropsectMapData

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
	
	//if connected to UI, draw data - only called from window
	public void drawTrainData(SOM_StraffordMain pa, int curMapImgIDX, boolean drawLbls) {
		if(drawLbls){
			for(int i=0;i<trainData.length;++i){trainData[i].drawMeLblMap(pa,trainData[i].label,false);}} 
		else {for(int i=0;i<trainData.length;++i){trainData[i].drawMeMap(pa, 2,trainData[i].label);}}	
	}//drawTrainData
	
	public void drawAllNodes(SOM_StraffordMain pa, int curJPIdx, int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		for(SOMMapNodeExample node : MapNodes.values()){	node.drawMeSmall(pa,curJPIdx);	}
		pa.popStyle();pa.popMatrix();
	} 
		
	public void drawNodesWithWt(SOM_StraffordMain pa, float valThresh, int curJPIdx, int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		TreeMap<Float,ArrayList<SOMMapNodeExample>> map = PerJPHiWtMapNodes[curJPIdx];
		SortedMap<Float,ArrayList<SOMMapNodeExample>> headMap = map.headMap(valThresh);
		for(Float key : headMap.keySet()) {
			ArrayList<SOMMapNodeExample> ara = headMap.get(key);
			for (SOMMapNodeExample node : ara) {		node.drawMeWithWt(pa, key, 2,node.label);}
		}
		pa.popStyle();pa.popMatrix();
	} 
		
	public void drawExMapNodes(SOM_StraffordMain pa, int curMapImgIDX, int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		//PerJPHiWtMapNodes
		for(SOMMapNodeExample node : nodesWithEx){			node.drawMeMap(pa, 2,node.label);}
		pa.popStyle();pa.popMatrix();		
	}
	
	
	public DispSOMMapExample buildTmpDataExample(myPointf ptrLoc, TreeMap<Integer, Float> ftrs, float sens) {return new DispSOMMapExample(this, ptrLoc, ftrs, sens);}
	
	//find distance on map
	public myPoint buildScaledLoc(float x, float y){		
		float xLoc = (x + .5f) * (mapDims[2]/mapX), yLoc = (y + .5f) * (mapDims[3]/mapY);
		myPoint pt = new myPoint(xLoc, yLoc, 0);
		return pt;
	}
	
	//distance on map	
	public myPointf buildScaledLoc(Tuple<Integer,Integer> mapNodeLoc){		
		float xLoc = (mapNodeLoc.x + .5f) * (mapDims[2]/mapX), yLoc = (mapNodeLoc.y + .5f) * (mapDims[3]/mapY);
		myPointf pt = new myPointf(xLoc, yLoc, 0);
		return pt;
	}
	
	public int getMapX(){return mapX;}
	public int getMapY(){return mapY;}	
	
	//show array of strings, either just to console or to applet window
	public void dispMessageAra(String[] sAra, int perLine) {
		for(int i=0;i<sAra.length; i+=perLine){
			String s = "";
			for(int j=0; j<perLine; ++j){	s+= sAra[i+j]+ "\t";}
			dispMessage("SOMMapData","dispMessageAra",s);
		}
	}
	public void dispMessage(String srcClass, String srcMethod, String msgText) {
		String msg = srcClass + "::" + srcMethod + " : " + msgText;
		if (p == null) {System.out.println(msg);} else {p.outStr2Scr(msg);}
	}//dispMessage
	

	public void setMapX(int _x){
		//need to update UI value in win
		mapX = _x;
		nodeXPerPxl = mapX/this.mapDims[2];
		if (win != null) {			
			boolean didSet = win.setWinToUIVals(win.uiMapColsIDX, mapX);
			if(!didSet){dispMessage("SOMMapData","setMapX","Setting ui map x value failed for x = " + _x);}
		}
	}
	public void setMapY(int _y){
		//need to update UI value in win
		mapY = _y;
		nodeYPerPxl = mapY/this.mapDims[3];
		if (win != null) {			
			boolean didSet = win.setWinToUIVals(win.uiMapRowsIDX, mapY);
			if(!didSet){dispMessage("SOMMapData","setMapY","Setting ui map y value failed for y = " + _y);}
		}
	}
	
	public float getMapWidth(){return mapDims[2];}
	public float getMapHeight(){return mapDims[3];}
	
	private void initFlags(){stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlag(i,false);}}
	public void setAllFlags(int[] idxs, boolean val) {for (int idx : idxs) {setFlag(idx, val);}}
	public void setFlag(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case debugIDX : {break;}			
			case mapDataLoadedIDX	: {break;}		
			case loaderRtnIDX : {break;}
			case mapSetSmFtrZeroIDX : {break;}
			case prospectDataLoadedIDX: {break;}		//raw prospect data has been loaded but not yet processed
			case optDataLoadedIDX: {break;}				//raw opt data has been loaded but not processed
			case orderDataLoadedIDX: {break;}			//raw order data loaded not proced
			case rawPrspctEvDataProcedIDX: {break;}				//all raw prospect/event data has been processed into StraffSOMExamples and subsequently erased
			case rawProducDataProcedIDX : {break;}			//all raw product data has been processed into StraffSOMExamples and subsequently erased 
			case denseTrainDataSavedIDX : {
				if (val) {dispMessage("SOMMapData","setFlag","All "+ this.numTrainData +" Dense Training data saved to .lrn file");}
				break;}				//all prospect examples saved as training data
			case sparseTrainDataSavedIDX : {
				if (val) {dispMessage("SOMMapData","setFlag","All "+ this.numTrainData +" Sparse Training data saved to .svm file");}
				break;}				//all prospect examples saved as training data
			case testDataSavedIDX : {
				if (val) {dispMessage("SOMMapData","setFlag","All "+ this.numTestData + " saved to .csv file");}
				break;		}
		}
	}//setFlag		
	public boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		
	
	public String toString(){
		String res = "Weights Data : \n";
		for(Tuple<Integer,Integer> key : MapNodes.keySet()){
			SOMMapNodeExample n = MapNodes.get(key);
			res+="Key:"+key.toString()+" : "+n.toCSVString(0)+"\n";}
		res += "Training Data : \n";
		for(int i =0; i<trainData.length;++i){ res += trainData[i].toString();}
		//TODO a lot of data is missing
		return res;	
	}	
}//SOMMapData

//class to hold the data that defines a SOM_MAP map execution
class SOM_MAPDat{
	private String execDir;			//SOM_MAP execution directory
	private String execStr;
	private HashMap<String, Integer> mapInts;			// mapCols (x), mapRows (y), mapEpochs, mapKType, mapStRad, mapEndRad;
	private HashMap<String, Float> mapFloats;			// mapStLrnRate, mapEndLrnRate;
	private HashMap<String, String> mapStrings;			// mapGridShape, mapBounds, mapRadCool, mapNHood, mapLearnCool;	
	private String trainDataDenseFN,		//training data file name for dense data
			trainDataSparseFN,			//for sparse data
			outFilesPrefix;			//output from map prefix
	private boolean isSparse;
	private String curOS;			//os currently running - use to modify exec string for mac?
	private String[] execStrAra;
	
	public SOM_MAPDat(String _curOS, String _SOM_Dir, String _execStr, HashMap<String, Integer> _mapInts, HashMap<String, Float> _mapFloats, HashMap<String, String> _mapStrings, String _trndDenseFN, String _trndSparseFN, String _outPfx){
		execDir = _SOM_Dir;
		execStr = _execStr;
		mapInts = _mapInts;
		curOS = _curOS;
		isSparse = (mapInts.get("mapKType") > 1);//0 and 1 are dense cpu/gpu, 2 is sparse cpu
		mapFloats = _mapFloats;
		mapStrings = _mapStrings;
		trainDataDenseFN = _trndDenseFN;
		trainDataSparseFN = _trndSparseFN;
		outFilesPrefix = _outPfx;
		init();
	}//SOM_MAPDat ctor from data
	
	//build an object based on an array of strings read from a file TODO
	//array is array of strings holding comma sep key-value pairs, grouped by construct, with tags denoting which construct
	public SOM_MAPDat(String _curOS, String[] _descrAra) {		
		curOS = _curOS;										//current OS running on
		mapInts = new HashMap<String, Integer>();			// mapCols (x), mapRows (y), mapEpochs, mapKType, mapStRad, mapEndRad;
		mapFloats = new HashMap<String, Float>();			// mapStLrnRate, mapEndLrnRate;
		mapStrings	= new HashMap<String, String>();			// mapGridShape, mapBounds, mapRadCool, mapNHood, mapLearnCool;	
		HashMap<String, String> tmpVars = new HashMap<String, String>();
		int idx = 0;
		boolean foundDataPartition = false;
		int numLines = _descrAra.length;
		while ((!foundDataPartition) && (idx < numLines)) {
			if(_descrAra[idx].contains("### Base Vars")) {foundDataPartition=true;}
			++idx;		
		}
		if(idx == numLines) {System.out.println("SOM_MAPDat::buildSOMMapDatFromAra : ERROR :  Array of description information not correct format to build SOM_MAPDat object.  Aborting.");	return;	}
		//read in method vars
		tmpVars = _readArrayIntoStringMap(new int[] {idx}, numLines, "### mapInts descriptors", _descrAra);
		if (tmpVars == null) {return;}
		execDir = tmpVars.get("execDir").trim();
		execStr = tmpVars.get("execStr").trim();
		trainDataDenseFN = tmpVars.get("trainDataDenseFN").trim();
		trainDataSparseFN = tmpVars.get("trainDataSparseFN").trim();
		outFilesPrefix = tmpVars.get("outFilesPrefix").trim();
		isSparse = (tmpVars.get("isSparse").trim().toLowerCase().contains("true") ? true : false);
		//integer SOM cmnd line args
		tmpVars = _readArrayIntoStringMap(new int[] {idx}, numLines, "### mapFloats descriptors", _descrAra);
		if (tmpVars == null) {return;}
		for(String key : tmpVars.keySet()) {mapInts.put(key, Integer.parseInt(tmpVars.get(key).trim()));}
		//float SOM Cmnd Line Args
		tmpVars = _readArrayIntoStringMap(new int[] {idx}, numLines, "### mapStrings descriptors", _descrAra);
		if (tmpVars == null) {return;}
		for(String key : tmpVars.keySet()) {mapFloats.put(key, Float.parseFloat(tmpVars.get(key).trim()));}		
		//String SOM Cmnd Line Args
		tmpVars = _readArrayIntoStringMap(new int[] {idx}, numLines, "### End Descriptor Data", _descrAra);
		if (tmpVars == null) {return;}
		for(String key : tmpVars.keySet()) {mapStrings.put(key, tmpVars.get(key).trim());}
		init();
	}//SOM_MAPDat ctor from string ara - 
	
	//after-construction initialization code for both ctors
	private void init() {
		execStrAra = buildExecStrAra();					//build execution string array used by processbuilder
	}//init
	//return execution string array used by processbuilder
	public String[] getExecStrAra(){		return execStrAra;	}
	
	//public void write
	
	
	
	//read string array into map of string-string key-value pairs.  idx passed as reference (in array)
	private HashMap<String, String> _readArrayIntoStringMap(int[] idx, int numLines, String _partitionStr, String[] _descrAra){
		HashMap<String, String> tmpVars = new HashMap<String, String>();
		boolean foundDataPartition = false;
		//load base vars here
		while ((!foundDataPartition) && (idx[0] < numLines)) {
			if(_descrAra[idx[0]].contains(_partitionStr)) {foundDataPartition=true;}
			String[] dat = _descrAra[idx[0]].trim().split(",");
			tmpVars.put(dat[0], dat[1]);
			++idx[0];	
		}	
		if(idx[0] == numLines) {System.out.println("SOM_MAPDat::buildSOMMapDatFromAra : ERROR :  Array of description information not correct format to build SOM_MAPDat object - failed finding partition bound : " +_partitionStr + ".  Aborting.");	return null;}
		return tmpVars;
	}//read array into map of strings, to be processed into object variables
	
	
	//build execution string for SOM_MAP
	private String[] buildExecStrAra(){
		String[] res;
		if (curOS.toLowerCase().contains("mac os x")) {
			res = new String[]{execDir + execStr,
			"-k",""+mapInts.get("mapKType"),"-x",""+mapInts.get("mapCols"),"-y",""+mapInts.get("mapRows"), "-e",""+mapInts.get("mapEpochs"),"-r",""+mapInts.get("mapStRad"),"-R",""+mapInts.get("mapEndRad"),
			"-l",""+String.format("%.4f",mapFloats.get("mapStLrnRate")),"-L",""+String.format("%.4f",mapFloats.get("mapEndLrnRate")), 
			"-m",""+mapStrings.get("mapBounds"),"-g",""+mapStrings.get("mapGridShape"),"-n",""+mapStrings.get("mapNHood"), "-T",""+mapStrings.get("mapLearnCool"), 
			"-v", "2",
			"-t",""+mapStrings.get("mapRadCool"), "\"" +(isSparse ? trainDataSparseFN : trainDataDenseFN) + "\"" , "\"" + outFilesPrefix +  "\""};
			
		} else {
			res = new String[]{execDir, execStr,
			"-k",""+mapInts.get("mapKType"),"-x",""+mapInts.get("mapCols"),"-y",""+mapInts.get("mapRows"), "-e",""+mapInts.get("mapEpochs"),"-r",""+mapInts.get("mapStRad"),"-R",""+mapInts.get("mapEndRad"),
			"-l",""+String.format("%.4f",mapFloats.get("mapStLrnRate")),"-L",""+String.format("%.4f",mapFloats.get("mapEndLrnRate")), 
			"-m",""+mapStrings.get("mapBounds"),"-g",""+mapStrings.get("mapGridShape"),"-n",""+mapStrings.get("mapNHood"), "-T",""+mapStrings.get("mapLearnCool"), 
			"-v", "2",
			"-t",""+mapStrings.get("mapRadCool"), "\"" +(isSparse ? trainDataSparseFN : trainDataDenseFN) + "\"" , "\"" + outFilesPrefix +  "\""};
		}
		return res;		
	}//execString
	
	public boolean isToroidal(){return (mapStrings.get("mapBounds").equals("toroid"));}
	
	//return string array describing this SOM map execution in csv format so can be saved to a file - group each construct by string title
	public ArrayList<String> buildStringDescAra() {
		ArrayList<String> res = new ArrayList<String>();
		res.add("### This file holds description of SOM map experiment execution settings");
		res.add("### It should be used to build a SOM_MAPDat object which then is consumed to control the execution of the SOM.");
		res.add("### Base Vars");
		res.add("execDir,"+execDir);
		res.add("execStr,"+execStr);
		res.add("isSparse,"+isSparse);
		res.add("trainDataDenseFN,"+trainDataDenseFN);
		res.add("trainDataSparseFN,"+trainDataSparseFN);
		res.add("outFilesPrefix,"+outFilesPrefix);
		res.add("### mapInts descriptors");
		for (String key : mapInts.keySet()) {res.add(""+key+","+mapInts.get(key));}
		res.add("### mapFloats descriptors");
		for (String key : mapFloats.keySet()) {res.add(""+key+","+String.format("%.6f", mapFloats.get(key)));}
		res.add("### mapStrings descriptors");
		for (String key : mapStrings.keySet()) {res.add(""+key+","+mapStrings.get(key));}
		res.add("### End Descriptor Data");		
		return res;		
	}//buildStringDescAra
		
	@Override
	public String toString(){
		String res = "Map config : SOM_MAP Dir : " + execDir +"\n";
		res += "Kernel(k) : "+mapInts.get("mapKType") + "\t#Cols : " + mapInts.get("mapCols") + "\t#Rows : " + mapInts.get("mapRows") + "\t#Epochs : " + mapInts.get("mapEpochs") + "\tStart Radius : " +mapInts.get("mapStRad") + "\tEnd Radius : " + +mapInts.get("mapEndRad")+"\n";
		res += "Start Learning Rate : " + String.format("%.4f",mapFloats.get("mapStLrnRate"))+"\tEnd Learning Rate : " + String.format("%.4f",mapFloats.get("mapEndLrnRate"))+"\n";
		res += "Boundaries : "+mapStrings.get("mapBounds") + "\tGrid Shape : "+mapStrings.get("mapGridShape")+"\tNeighborhood Function : " + mapStrings.get("mapNHood") + "\nLearning Cooling: " + mapStrings.get("mapLearnCool") + "\tRadius Cooling : "+ mapStrings.get("mapRadCool")+"\n";		
		res += "Training data : "+(isSparse ? ".svm (Sparse) file name : " + trainDataSparseFN :  ".lrn (dense) file name : " + trainDataDenseFN) + "\nOutput files prefix : " + outFilesPrefix +"\n";
		return res;
	}
	
}//SOM_MAPDat

/////////////////////////////////////////////////////////////////////
//class holding data about a som feature
class SOMFeature{
	public SOMMapManager map;			//owning map
	public int fIdx;
	public String name;
	public TreeMap<Float,SOMMapNodeExample> sortedBMUs;		//best units for this particular feature, based on weight ratio (this is not actual weight of feature in node)

	public SOMFeature(SOMMapManager _map, String _name, int _fIdx, String[] _tkns){//_tkns is in order idx%3==0 : wt ration, idx%3==1 : y coord, idx%3==2 : x coord
		map=_map;fIdx=_fIdx;name=_name;
		setBMUWts(_tkns);
	}
	
	public void setBMUWts(String[] _tkns){
		if(_tkns == null){map.dispMessage("SOMFeature","setBMUWts","Feature wts not found for feature : " + name + " idx : "+ fIdx);return;}
		sortedBMUs = new TreeMap<Float,SOMMapNodeExample>();
	}
	
	public String toString(){
		String res = "Feature Name : "+name;
		return res;
	}

}//SOMFeature


//class description for a data point - used to distinguish different jp-jpg members - class membership is determined by comparing the label
//TODO change this to some other structure, or other comparison mechanism?  allow for subset membership check?
class dataClass implements Comparable<dataClass> {
	
	public String label;
	public String lrnKey;
	public String cls;
	
	public int jpGrp, jp;
	//color of this class, for vis rep
	public int[] clrVal;
	
	public dataClass(String _lrnKey, String _lbl, String _cls, int[] _clrVal){
		lrnKey=_lrnKey;
		label = _lbl;	
		cls = _cls;
		clrVal = _clrVal;
	}	
	public dataClass(dataClass _o){this(_o.lrnKey, _o.label,_o.cls, _o.clrVal);}//copy ctor
	//set the defini
	public void setJpJPG(int _jpGrp, int _jp) {jpGrp=_jpGrp;jp=_jp;}
	
	//this will guarantee that, so long as a string has only one period, the value returned will be in the appropriate format for this mocapClass to match it
	//reparses and recalcs subject and clip from passed val
	public static String getPrfxFromData(String val){
		String[] valTkns = val.trim().split("\\.");
		return String.format("%03d",(Integer.parseInt(valTkns[0]))) + "."+ String.format("%03d",(Integer.parseInt(valTkns[1])));		
	}	
	@Override
	public int compareTo(dataClass o) {	return label.compareTo(o.label);}
	public String toCSVString(){String res = "" + lrnKey +","+label+","+cls;	return res;}
	public String getFullLabel(){return label +"|"+cls;}
	//public static String buildPrfx(int val){return (val < 100 ? (val < 10 ? "00" : "0") : "") + val;}//handles up to 999 val to be prefixed with 0's	
	public String toString(){
		String res = "Label :  " +label + "\tLrnKey : " + lrnKey  + "\tJPGroup # : " + String.format("%03d",jpGrp)+ "\tJP # : "+String.format("%03d",jp)+"\tDesc : "+cls;
		return res;		
	}	
}//dataClass

class dataDesc{
	public SOMMapManager map;
	public String[] ftrNames;
	public int numFtrs;		
	
	public dataDesc(SOMMapManager _map,int _numFtrs){
		map=_map;
		numFtrs = _numFtrs;
		ftrNames = new String[numFtrs];		
	}
	
	public dataDesc(SOMMapManager _map,String [] tkns){
		this(_map, tkns.length);
		System.arraycopy(tkns, 0, ftrNames, 0, tkns.length);
	}
	
	//build the default header for this data descriptor
	public void buildDefHdr(){for(int i =0; i<numFtrs; ++i){ftrNames[i] = "ftr_"+i;}}//buildDefHdr
	
	public String toString(){
		String res = "";
		for(int i=0;i<ftrNames.length;++i){res += ftrNames[i] + ",";}
		return res;
	}
	
}//class dataDesc


//manage a message stream from a process
class messageMgr implements Callable<Boolean> {
	SOMMapManager mapMgr;
	final Process process;
	BufferedReader rdr;
	StringBuilder strbld;
	String type;
	int iter = 0;
	public messageMgr(SOMMapManager _mapMgr, final Process _process, Reader _in, String _type) {
		mapMgr = _mapMgr;
		process=_process;
		rdr = new BufferedReader(_in); 
		strbld = new StringBuilder();
		type=_type;
	}
	
	private void dispMessage(String str) {
		if(mapMgr != null) {mapMgr.dispMessage("messageMgr","call ("+type+")", str);}
		else {				System.out.println(str);	}
	}
	
	
	public String getResults() {	return strbld.toString();	}
	@Override
	public Boolean call() throws Exception {
		String sIn = null;
		try {
			while ((sIn = rdr.readLine()) != null) {
				dispMessage("Stream " + type+" Line : " + (iter++) + " | Msg : " + sIn);
				strbld.append(sIn);			
				strbld.append(System.getProperty("line.separator"));				
			}
		} catch (IOException e) {
	        // this code will be executed if a IOException happens "e.getMessage()" will have an error
			e.printStackTrace();
			dispMessage("Process IO failed with exception : " + e.toString() + "\n\t"+ e.getMessage());
		}
		return true;
	}
	
	
}//messageMgr
