package SOM_Strafford_PKG;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;


//this class holds the data describing a SOM and the data used to both build and query the som

public class SOMMapData {
	//source directory for reading all source csv files, writing intermediate outputs, executing SOM_MAP and writing results
	//TODO : move this to main java stub, pass it around via ctor
	//public static final String straffBasefDir = "F:" + File.separator + "StraffordProject" + File.separator;
	public final String straffBasefDir = "StraffordProject" + File.separator;
	//calcFileWeights is file holding config for calc object
	public static final String calcWtFileName = "WeightEqConfig.csv";

	////////////////////////////////////////////////////////////////////////////////////////////////
	
	//description of SOM_MAP exe params
	private SOM_MAPDat SOMExeDat;	
	//string to invoke som executable - platform dependent
	private final String SOM_Map_EXECSTR;
	//public TreeMap<Float,ArrayList<Tuple<Integer,Integer>>> MapSqMagToFtrs;		//precalculate the squared sum of all ftrs to facilitate lookup for locations of points on map
	public final Calendar now;
	//all nodes of som map, keyed by node location
	public TreeMap<Tuple<Integer,Integer>, SOMMapNodeExample> MapNodes;
	//keyed by field used in lrn file (float rep of individual record, 
	public TreeMap<String, dataClass> TrainDataLabels;	
	//data set to be training data, etc
	public StraffSOMExample[] trainData, inputData, testData;
	
	public float[] map_ftrsMean, map_ftrsVar, map_ftrsDiffs, map_ftrsMin,
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
	
	//public String somResBaseFName, trainDataName, diffsFileName, minsFileName;//files holding diffs and mins (.csv extension)
	private int[] stFlags;						//state flags - bits in array holding relevant process info
	public static final int
			debugIDX 				= 0,
			dataLoadedIDX			= 1,			//data is cleanly loaded
			loaderRtnIDX			= 2,			//dataloader has finished - wait on this to draw map
			mapSetSmFtrZeroIDX  	= 3,			//map small vector's features to 0, otherwise only compare non-zero features
			//relating to raw data
			prospectDataLoadedIDX	= 4,			//raw prospect data has been loaded but not yet processed
			optDataLoadedIDX		= 5,			//raw opt data has been loaded but not processed
			orderDataLoadedIDX		= 6,			//raw order data loaded not proced
			linkDataLoadedIDX		= 7,			//raw link data loaded not proced
			rawDataProcedIDX		= 8,			//all raw data has been loaded and processed into StraffSOMExamples
			lrnDataSavedIDX 		= 9,			//all current prospect data has been saved as a training data file for SOM (.lrn format)
			svmDataSavedIDX 		= 10,			//sparse data format using .svm file descriptions (basically a map with a key:value pair of ftr index : ftr value
			testDataSavedIDX		= 11;

	public static final int numFlags = 12;	
	
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
	
	//idxs for each type of data in arrays holding relevant data info
	public static final int prspctIDX = 0, orderEvntIDX = 1, optEvntIDX = 2, linkEvntIDX = 3;
	//file names of each type of file
	public final String[] straffDataFileNames = new String[] {"prospect_objects", "order_event_objects", "opt_event_objects", "link_event_objects"};
	//list of idxs related to each table for data
	public static final int[] straffObjFlagIDXs = new int[] {prospectDataLoadedIDX, orderDataLoadedIDX, optDataLoadedIDX, linkDataLoadedIDX};
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
	//struct maintaining file names for all files in som, along with 
	public SOMDatFileConfig fnames;			
	
	////////////////////////////////////////
	//TODO save these to file, to faciliate reloading without re-reading db
	//this information comes from BaseRawData
	//reference to ids and counts of all jps seen in all data 
	private TreeMap<Integer, Integer> jpSeenCount;// = new TreeMap<Integer, Integer>();
	//reference to ids and counts of all jps seen in -event- data
	private TreeMap<Integer, Integer> jpEvSeenCount;// = new TreeMap<Integer, Integer>();//jpEvSeen
	//reference to ids and counts of all jps seen only in prospect record data
	private TreeMap<Integer, Integer> jpPrspctSeenCount;// = new TreeMap<Integer, Integer>();//jpEvSeen
	//map from jp to idx in resultant feature vector
	public TreeMap<Integer, Integer> jpToFtrIDX;// = new TreeMap<Integer, Integer>();
	//map from jpgroup to integer
	public TreeMap<Integer, Integer> jpgToIDX;// = new TreeMap<Integer, Integer>();
	//map from jpgroup to jps corresponding to this group.
	private TreeMap<Integer, TreeSet <Integer>> jpgsToJps;// = new TreeMap<Integer, TreeSet <Integer>>();
	//map from jps to owning jpgs
	private TreeMap<Integer, Integer> jpsToJpgs;// = new TreeMap<Integer, Integer>();
	//used by UI and also to map specific indexes to actual jps/jpgs
	public Integer[] jpByIdx = new Integer[] {1}, jpgrpsByIdx = new Integer[] {1};
	
	//file names
	//public static final String jpseenFName = "jpSeen.txt", jpgroupsAndJpsFName = "jpgroupsAndJps.txt";
	//calc object to be used to derive feature vector for each prospect
	public StraffWeightCalc ftrCalcObj;
	
	//extensions of files for SOM data
	public static String[] SOMResExtAra = new String[]{".wts",".fwts",".bm",".umx"};
	public static final int
		wtsIDX = 0,
		fwtsIDX = 1,
		bmuIDX = 2,
		umtxIDX = 3;	
	
	//distance to use : 2 : scaled features, 1: chisq features or 0 : regular feature dists
	public int distType = 0;
	//map dims is used to calculate distances for BMUs - based on screen dimensions - need to change this?
	private float[] mapDims;
	
	//used by UI, ignored if NULL (passed by command line program)
	public SOM_StraffordMain p;
	public mySOMMapUIWin win;				//owning window
	
	private ExecutorService th_exec;	//to access multithreading
	private final int numUsableThreads;		//# of threads usable by the application
	
	public SOMMapData(SOM_StraffordMain _pa, mySOMMapUIWin _win, ExecutorService _th_exec, float[] _dims) {
		p=_pa; win=_win;th_exec=_th_exec;
		//find platform this is executing on
		String osName = System.getProperty("os.name");
		dispMessage("SOMMapData::Constructor : OS this application is running on : "  + osName);
		//set invoking string for map executable - is platform dependent
		String execStr = "straff_SOM";
		if (osName.toLowerCase().contains("windows")) {execStr += ".exe";	}
		SOM_Map_EXECSTR = execStr;
		//want # of usable background threads.  Leave 2 for primary process (and potential draw loop)
		numUsableThreads = Runtime.getRuntime().availableProcessors() - 2;
		initFlags();
		mapDims = _dims;
		//raw data from csv's/db
		rawDataArrays = new ConcurrentSkipListMap<String, ArrayList<BaseRawData>>();
		//instantiate map of ProspectExamples
		prospectMap = new ConcurrentSkipListMap<String, ProspectExample>();		
		now = Calendar.getInstance();
		try {
			straffObjLoaders = new Class[] {Class.forName("SOM_Strafford_PKG.ProspectDataLoader"),  Class.forName("SOM_Strafford_PKG.OrderEventDataLoader"),Class.forName("SOM_Strafford_PKG.OptEventDataLoader"),Class.forName("SOM_Strafford_PKG.LinkEventDataLoader")};
		} catch (Exception e) {
			dispMessage("Failed to instance straffObjLoaders : " + e);			
		}
		//to launch loader callable instances
		buildStraffDataLoaders();		
		initData();
	}//ctor
	
	public SOMMapData(ExecutorService _th_exec,float[] _dims) {this(null, null, _th_exec, _dims);}

	//build a date with each component separated by token
	public String getDateTimeString(Calendar now){return getDateTimeString(true, false,".", now);}
	public String getDateTimeString(boolean useYear, boolean toSecond, String token,Calendar now){
		String result = "";
		int val;
		if(useYear){val = now.get(Calendar.YEAR);		result += ""+val+token;}
		val = now.get(Calendar.MONTH)+1;				result += (val < 10 ? "0"+val : ""+val)+ token;
		val = now.get(Calendar.DAY_OF_MONTH);			result += (val < 10 ? "0"+val : ""+val)+ token;
		val = now.get(Calendar.HOUR_OF_DAY);					result += (val < 10 ? "0"+val : ""+val)+ token;
		val = now.get(Calendar.MINUTE);					result += (val < 10 ? "0"+val : ""+val);
		if(toSecond){val = now.get(Calendar.SECOND);	result += token + (val < 10 ? "0"+val : ""+val);}
		return result;
	}
	
	private String[] StraffordSOMLrnFN;		//idx 0 = file name for SOM training data -> .lrn file, 1 = file name for sphere sample data -> .csv file
	//call when src data are first initialized - sets file names for .lrn file  and testing file output database query
	public void setStraffordLRNFileNames(int _numSmpls, int _numTrain, int _numTest){
		Calendar now = Calendar.getInstance();
		String nowDirTmp = straffSOMProcSubDir+ "StraffSOM_"+getDateTimeString(true,false,"_", now)+File.separator, fileNow = getDateTimeString(false,false,"_", now);
		String nowDir = getDirNameAndBuild(nowDirTmp);
		String[] tmp = {"Out_Straff_Smp_"+_numSmpls,
						"Train_Straff_Smp_"+_numTrain+"_of_"+_numSmpls+"_Dt_"+fileNow+".lrn",
						"Train_Straff_Smp_"+_numTrain+"_of_"+_numSmpls+"_Dt_"+fileNow+".svm",				
						"Test_Straff_Smp_"+_numTest+"_of_"+_numSmpls+"_Dt_"+fileNow+".csv",
						"Mins_Straff_Smp_"+_numSmpls+"_Dt_"+fileNow+".csv",
						"Diffs_Straff_Smp_"+_numSmpls+"_Dt_"+fileNow+".csv",
						"SOMImg_Straff_Smp_"+_numSmpls+"_Dt_"+fileNow+".png"
						};
		StraffordSOMLrnFN = new String[tmp.length];
		
		for(int i =0; i<StraffordSOMLrnFN.length;++i){	
			//build dir as well
			StraffordSOMLrnFN[i] = nowDir + tmp[i];
			dispMessage("Built Dir : " + StraffordSOMLrnFN[i]);
		}				
	}//setStraffordLRNFileNames

	
	public String getStraffMapOutFileBase(){	return StraffordSOMLrnFN[0];	}	
	public String getStraffMapLRNFileName(){	return StraffordSOMLrnFN[1];	}
	public String getStraffMapSVMFileName(){	return StraffordSOMLrnFN[2];	}
	public String getStraffMapTestFileName(){	return StraffordSOMLrnFN[3];	}
	public String getStraffMapMinsFileName(){	return StraffordSOMLrnFN[4];	}
	public String getStraffMapDiffsFileName(){	return StraffordSOMLrnFN[5];	}	
	//private String getSOMLocClrImgFName(){	return StraffordSOMLrnFN[6];}	
	
	public String getSOMLocClrImgForJPFName(int jpIDX) {return "jp_"+jpByIdx[jpIDX]+"_"+StraffordSOMLrnFN[6];}
	

	//build new SOM_MAP map using UI-entered values, then load resultant data
	//with maps of required SOM exe params
	protected void buildNewSOMMap(String SOM_Dir, boolean mapLoadFtrBMUsIDX, HashMap<String, Integer> mapInts, HashMap<String, Float> mapFloats, HashMap<String, String> mapStrings){
		String lrnFileName = getStraffMapLRNFileName(), 		//dense training data .lrn file
				svmFileName = getStraffMapSVMFileName(),		//sparse training data svm file name
				testFileName = getStraffMapTestFileName(),		//testing data .csv file
				minsFileName = getStraffMapMinsFileName(),		//mins data percol .csv file
				diffsFileName = getStraffMapDiffsFileName(),	//diffs data percol .csv file
				
				outFilePrfx = getStraffMapOutFileBase() + "_x"+mapInts.get("mapCols")+"_y"+mapInts.get("mapRows")+"_k"+mapInts.get("mapKType");
		//build new map descriptor and execute
		//structure holding SOM_MAP specific cmd line args and file names and such
		SOM_MAPDat SOMExecDat = new SOM_MAPDat(SOM_Dir, SOM_Map_EXECSTR, mapInts,mapFloats, mapStrings, lrnFileName, svmFileName,  outFilePrfx);
		dispMessage("SOM map descriptor : " + SOMExecDat + " exec str : ");
		dispMessageAra(SOMExecDat.execStrAra(),2);//2 strings per line
		//launch in a thread?
		buildNewMap(SOMExecDat);
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
		String cFN= "Not Used";
		String csvOutBaseFName = outFilePrfx + "_outCSV";
		initData();			
		setFlag(loaderRtnIDX, false);
		fnames = new SOMDatFileConfig(this);
		fnames.setAllFileNames(cFN,diffsFileName,minsFileName, lrnFileName, outFilePrfx, csvOutBaseFName);
		dispMessage("Current fnames before dataLoader Call : " + fnames.toString());
		th_exec.execute(new dataLoader(this,mapLoadFtrBMUsIDX,fnames));//fire and forget load task to load	

	}//buildNewSOMMap	
	
	public boolean mapCanBeTrained(int kVal) {
		//eventually enable training map on existing files - save all file names, enable file names to be loaded and map built directly
		return ((kVal < 1) && (getFlag(lrnDataSavedIDX)) || ((kVal == 2) && getFlag(svmDataSavedIDX)));
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
	public void setMapImgClrs(){if (win != null) {win.setMapImgClrs();} else {dispMessage("Display window doesn't exist, can't build images; ignoring.");}}
	//only appropriate if using UI
	public void initMapAras(int numFtrs) {if (win != null) {win.initMapAras(numFtrs);} else {dispMessage("Display window doesn't exist, can't build image arrays; ignoring.");}}
	
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
		    System.out.println(dispYesStr+"\tLength : " +  lines.size());
		    res = lines.toArray(new String[0]);		    
		} catch (Exception e) {	
			e.printStackTrace();
			System.out.println("!!"+dispNoStr);
			res= null;
		} 
		finally {
		    if (inputStream != null) {inputStream.close();		    }
		    if (sc != null) { sc.close();		    }
		}
		return res;
	}//loadFileContents
	
	//launch process to exec SOM_MAP
	public void runMap(String[] cmdExecStr) throws IOException{
		boolean showDebug = getFlag(debugIDX);
		//http://stackoverflow.com/questions/10723346/why-should-avoid-using-runtime-exec-in-java		
		String wkDirStr = cmdExecStr[0], cmdStr = cmdExecStr[1], argsStr = "";
		String[] execStr = new String[cmdExecStr.length - 1];
		execStr[0] = wkDirStr + cmdStr;
		for(int i =2; i<cmdExecStr.length;++i){execStr[i-1] = cmdExecStr[i]; argsStr +=cmdExecStr[i]+" | ";}
		if(showDebug){dispMessage("wkDir : "+ wkDirStr + "\ncmdStr : " + cmdStr + "\nargs : "+argsStr);}
		ProcessBuilder pb = new ProcessBuilder(execStr);//.inheritIO();
		
		File wkDir = new File(wkDirStr); 
		pb.directory(wkDir);
		Process process=pb.start();
		if(showDebug){for(String s : pb.command()){dispMessage("cmd : " + s);}dispMessage(pb.directory().toString());}		
		BufferedReader rdr = new BufferedReader(new InputStreamReader(process.getInputStream())); 
		//put output into a string
		dispMessage("begin getting output");
		StringBuilder strbld = new StringBuilder();
		String s = null;
		while ((s = rdr.readLine()) != null){
			dispMessage(s);
			strbld.append(s);			
			strbld.append(System.getProperty("line.separator"));
		}
		String result = strbld.toString();//result of running map TODO save to log?
		dispMessage("runMap Finished");
	}
	//Build map from data by aggregating all training data, building SOM exec string from UI input, and calling OS cmd to run SOM_MAP
	private boolean buildNewMap(SOM_MAPDat _dat){
		SOMExeDat = _dat;				
		try {	runMap(SOMExeDat.execStrAra());	} 
		catch (IOException e){	dispMessage("Error running map : " + e.getMessage());	return false;}		
		return true;
	}//buildNewMap

	//load training and testing data from map results, if needing to be reloaded
	//when this method is done, trainData, testData and numTrainData and numTestData must be populated correctly
	public void assignTrainTestData() {
		//TODO need to build trainData and testData from data aggregation/loading structure - this method needs to be rewritten
		dispMessage("SOMMapData::assignTrainTestData :TODO :Need to set train and test data from saved file if not saved already");
		
		
//		trainData = new dataPoint[tmpTrainAra.length];
//		testData = new dataPoint[tmpTestAra.length];
//		numTrainData = trainData.length;
//		numTestData = testData.length;
//		System.arraycopy(tmpTrainAra, 0, trainData, 0, numTrainData);
//		System.arraycopy(tmpTestAra, 0, testData, 0, numTestData);
//		dispMessage("DataLoader : Finished assigning Training and Testing data from raw data -> added " + numTrainData + " training examples and " +numTestData + " testing examples");
		int numDataVals = 0;//TODO get the # of features from training data objects
		//dataBounds = new Float[2][numDataVals];
		//todo build data bounds
	}//assignTrainTestData
	
	
	
	public void initData(){
		dispMessage("Init Called");
		trainData = new StraffSOMExample[0];
		testData = new StraffSOMExample[0];
		inputData = new StraffSOMExample[0];
		nodesWithEx = new HashSet<SOMMapNodeExample>();
		nodesWithNoEx = new HashSet<SOMMapNodeExample>();
		numTrainData = 0;
		numFtrs = 0;
		numInputData = 0;
		SOMDataDir = this.getDirNameAndBuild(straffSOMProcSubDir);
	}//initdata
	//return true if loader is done and if data is successfully loaded
	public boolean isMapDrawable(){return getFlag(loaderRtnIDX) && getFlag(dataLoadedIDX);}


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
	//return how many times this JP has been seen
	public int getCountJPSeen(Integer jp) {
		Integer res = jpSeenCount.get(jp);
		if (res == null) {res = 0;	}
		return res;
	}

	//set all training/testing data save flags to val
	private void setAllTrainDatSaveFlags(boolean val) {
		setFlag(lrnDataSavedIDX, val);
		setFlag(svmDataSavedIDX, val);
		setFlag(testDataSavedIDX, val);		
	}
	
	//clear out existing prospect map
	public void resetProspectMap() {
		prospectMap = new ConcurrentSkipListMap<String, ProspectExample>();
		setFlag(rawDataProcedIDX, false);
		setAllTrainDatSaveFlags(false);
	}

	
	//fromCSVFiles : whether loading data from csv files or from SQL calls
	//eventsOnly : only use examples with event data to train
	//append : whether to append to existing data values or to load new data
	public void loadAllRawData(boolean fromCSVFiles, boolean eventsOnly, boolean appendToExisting) {
		boolean singleThread=numUsableThreads<1;//this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		if(singleThread) {
			for (int idx=0;idx<numStraffDataTypes;++idx) {
				String[] loadRawDatStrs = getLoadRawDataStrs(fromCSVFiles,idx);
				loadRawDataVals(loadRawDatStrs, fromCSVFiles,idx);
			}
		} else {
			for (int idx=0;idx<numStraffDataTypes;++idx) {
				String[] loadRawDatStrs = getLoadRawDataStrs(fromCSVFiles,idx);
				straffDataLoaders.get(idx).setLoadData(this, loadRawDatStrs[0], fromCSVFiles, loadRawDatStrs[1], straffObjFlagIDXs[idx]);
			}
			//blocking on callables for multithreaded
			try {straffDataLdrFtrs = th_exec.invokeAll(straffDataLoaders);for(Future<Boolean> f: straffDataLdrFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		
		}
		dispMessage("# of features seen : " + numFtrs);

		//process loaded data
		procRawLoadedData(eventsOnly, appendToExisting);
		
		//for debugging purposes 
		//dbgDispKnownJPsJPGs();
	}//loadAllRawData
	
	//any subdirectories need to already exist for this to not cause an error
	private String[] getLoadRawDataStrs(boolean fromFiles, int idx) {
		String baseFName = straffDataFileNames[idx];
		dispLoadMessage(baseFName, fromFiles);
		String dataLocStrData = "";//fully qualified path to either csv of data or csv of connection string info for sql to data
		if (fromFiles) {
			dataLocStrData = straffBasefDir + straffSourceCSVSubDir + baseFName + File.separator + baseFName+".csv";
		} else {
			dataLocStrData = straffBasefDir + straffSQLProcSubDir + "sqlConnData_"+baseFName+".csv";
			dispMessage("SOMMapData::getLoadRawDataStrs : Need to construct appropriate sql connection info and put in text config file : " + dataLocStrData);
		}
		return new String[] {baseFName,dataLocStrData};
	}//getLoadRawDataStrs

	private void dispLoadMessage(String orig, boolean fromFiles) {dispMessage("Load Raw " +orig + " data " + (fromFiles ? "from CSV files" : "from SQL Calls"));}
	//will instantiate specific loader class object and load the data specified by idx, either from csv file or from an sql call described by csvFile
	private void loadRawDataVals(String[] loadRawDatStrs, boolean fromFiles, int idx){//boolean _isFileLoader, String _fileNameAndPath
		//single threaded implementation
		@SuppressWarnings("rawtypes")
		Class[] args = new Class[] {boolean.class, String.class};//classes of arguments for loader ctor		
		try {
			@SuppressWarnings("unchecked")
			StraffordDataLoader loaderObj = (StraffordDataLoader) straffObjLoaders[idx].getDeclaredConstructor(args).newInstance(fromFiles, loadRawDatStrs[1]);
			ArrayList<BaseRawData> datAra = loaderObj.execLoad();
			rawDataArrays.put(loadRawDatStrs[0], datAra);
			setFlag(straffObjFlagIDXs[idx], true);			//set flag corresponding to this type of data to be loaded
		} catch (Exception e) {			e.printStackTrace();}
		
		
		setFlag(straffObjFlagIDXs[idx], true);			//set flag corresponding to this type of data to be loaded
	}//loadRawDataVals	
	
	//this will retrieve a subdirectory name under the main directory of this project and build the subdir if it doesn't exist
	private String getDirNameAndBuild(String subdir) {
		String dirName = straffBasefDir +subdir;
		File directory = new File(dirName);
	    if (! directory.exists()){ directory.mkdir(); }
		return dirName;
	}//getDirNameAndBuild
	
	//build the file names for the csv files used to save intermediate data from db that has been partially preprocessed
	private String[] buildPrspctDataCSVFNames(boolean eventsOnly) {
		String destDir = getDirNameAndBuild(straffPreProcSubDir); 
		String suffix;
		if (eventsOnly) {	suffix = "prospectMapSrcDataEvnts";}
		else {				suffix = "prospectMapSrcData";}		
		String destForPrspctFName = destDir + suffix;				
		return new String[] {destForPrspctFName, suffix};
	}//buildPrspctDataCSVFNames	
	
	//load prospect mapped training data into StraffSOMExamples from disk
	public void loadAllPropsectMapData(boolean eventsOnly) {
		dispMessage("Loading all prospect map data " + (eventsOnly ? "that only have event-based training info" : "that have any training info (including only prospect jpg/jp specification)"));
		//clear out current prospect data
		resetProspectMap();
		//load in variables describing jpgroup/jp configurations in current data - not used, rebuild this info with prospect map
		//loadAllJPGData();
		String[] loadSrcFNamePrefixAra = buildPrspctDataCSVFNames(eventsOnly);
		
		String fmtFile = loadSrcFNamePrefixAra[0]+"_format.csv";
		String[] loadRes = loadFileIntoStringAra(fmtFile, "Format file loaded", "Format File Failed to load");
		int numPartitions = 0;
		try {
			numPartitions = Integer.parseInt(loadRes[0].split(" : ")[1].trim());
		} catch (Exception e) {e.printStackTrace(); } 
		for (int i=0; i<numPartitions;++i) {
			String dataFile =  loadSrcFNamePrefixAra[0]+"_"+i+".csv";
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
		finishProspectBuild(prospectMap);		
		dispMessage("SOMMapData::loadAllPropsectMapData : Finished loading and preprocessing all local prospect map data and calculating features.  Number of entries in prospectMap : " + prospectMap.size());
	}//loadAllPropsectMapData

	//write all prospect map data to a csv to be able to be reloaded to build training data from, so we don't have to re-read database every time
	public void saveAllProspectMapData(boolean eventsOnly) {
		dispMessage("SOMMapData::saveAllProspectMapData : Saving all prospect map data : " + prospectMap.size() + " examples to save.");
		String[] saveDestFNamePrefixAra = buildPrspctDataCSVFNames(eventsOnly);
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
				dispMessage("Done Building String Array : " +(nameCounter++));
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
			dispMessage("Saving String array : " +nameCounter);
			//saveStrings(saveDestFNamePrefixAra[0]+"_"+nameCounter+".csv", csvResSubAra.toArray(new String[0]));
			saveStrings(saveDestFNamePrefixAra[0]+"_"+nameCounter+".csv", csvResSubAra);
			++nameCounter;
		}
		//save the data in a format file
		String[] data = new String[] {"Number of file partitions for " + saveDestFNamePrefixAra[1] +" data : "+ nameCounter + "\n"};
		saveStrings(saveDestFNamePrefixAra[0]+"_format.csv", data);		
		dispMessage("SOMMapData::saveAllProspectMapData : Finished saving all prospect map data");
	}//saveAllProspectMapData	
	
	private void incrJPCounts(Integer jp, TreeMap<Integer, Integer> map) {
		Integer count = map.get(jp);
		if(count==null) {count=0;}
		map.put(jp, count+1);
	}//incrJPCounts

	//finish building the prospect map - finalize each prospect example and then perform calculation to derive weight vector
	private void finishProspectBuild(ConcurrentSkipListMap<String, ProspectExample> map) {
		//finalize builds each example's occurence structures, which describe the jp-jpg relationships found in the example
		for (ProspectExample ex : map.values()) {
			//if (ex.OID.toUpperCase().equals("PR_000000019")) {dispMessage("Prospect Example finalizeBuild for prospect with key pr_000000019");}//pr_000000019 is a record that only has an opt out all event
			ex.finalizeBuild();}
		//dispMessage("Total # of rebuilds : " + ProspectExample.countofRebuild);
		dispMessage("Begin setJPDateFromProspectData");
		//we need the jp-jpg counts and relationships dictated by the data by here.
		setJPDataFromProspectData(map);
		dispMessage("End setJPDateFromProspectData");// | Start processHoldOutOptRecs");		
//		//NOT DONE (changed calc to handle this instead) once all jp-jpg data has been set, go through all opt records that require building occurences for all jp/jpgs (positive opts with empty lists of requests)
//		for (ProspectExample ex : map.values()) {ex.processHoldOutOptRecs();}
//		dispMessage("End processHoldOutOptRecs");
		for (ProspectExample ex : map.values()) {
			//if (ex.OID.toUpperCase().equals("PR_000000019")) {dispMessage("Prospect Example buildFeatureVector for prospect with key pr_000000019");}//pr_000000019 is a record that only has an opt out all event
			ex.buildFeatureVector();}
		//now get mins and diffs from calc object
		diffsVals = ftrCalcObj.getDiffsBndsAra();
		minsVals = ftrCalcObj.getMinBndsAra();
		for (ProspectExample ex : map.values()) {ex.buildPostFeatureVectorStructs();}
		setFlag(rawDataProcedIDX, true);
		dispMessage("SOMMapData::finishProspectBuild : Finished calculating prospect feature vectors");
	}//finishProspectBuild
	
	public TreeMap<Integer, Float> calcStdFtrVector(StraffSOMExample ex, HashSet<Integer> jps){
		return calcStdFtrVector(ex, jps, minsVals, diffsVals);
	}
	//scale each feature value to be between 0->1 based on min/max values seen for this feature
	//all examples features will be scaled with respect to seen calc results 0- do not use this for
	//exemplar objects (those that represent a particular product, for example)
	//MUST BE SET WITH APPROPRIATE MINS AND DIFFS
	public TreeMap<Integer, Float> calcStdFtrVector(StraffSOMExample ex, HashSet<Integer> jps, Float[] mins, Float[] diffs) {
		TreeMap<Integer, Float> ftrs = ex.ftrMap;
		TreeMap<Integer, Float> sclFtrs = new TreeMap<Integer, Float>();
		Integer destIDX;
		for (Integer jp : jps) {
			destIDX = jpToFtrIDX.get(jp);
			Float lb = mins[destIDX], 
					diff = diffs[destIDX];
			if (diff==0) {//same min and max
				if (lb > 0) {	sclFtrs.put(destIDX,1.0f);}//only a single value same min and max-> set feature value to 1.0
				else {			sclFtrs.put(destIDX,0.0f);}
			} else {
				float val = (ftrs.get(destIDX)-lb)/diff;
				sclFtrs.put(destIDX,val);
			}			
		}//for each jp
		return sclFtrs;
	}//standardizeFeatureVector
	
	//build training data from current global prospect data map
	//and save them to .lrn format 
	//typeOfTrainData : 
	//		0 : regular features, 
	//		1 : standardized features (across each ftr component transformed to 0->1), 
	//		2 : normalized features (each feature vector has magnitude 1)
	public void buildAndSaveTrainingData(int dataFrmt, float testTrainPartition) {
		if (prospectMap.size() == 0) {
			dispMessage("SOMMapData::buildAndSaveTrainingData : prospectMap data not loaded/built, unable to build training data for SOM");
			return;
		}

		//build SOM data
		dispMessage("Saving data to training file.");
		//set inputdata array to be all prospect map examples
		inputData = prospectMap.values().toArray(new StraffSOMExample[0]);			
		numTrainData = (int) (inputData.length * testTrainPartition);		
		
		numTestData = inputData.length - numTrainData;
		//trainData, inputData, testData;
		trainData = new StraffSOMExample[numTrainData];	
		dispMessage("# of training examples : " + numTrainData + " inputData size : " + inputData.length);
		//numTrainData and numTestData 
		for (int i=0;i<trainData.length;++i) {trainData[i]=inputData[i];}
		testData = new StraffSOMExample[numTestData];
		for (int i=0;i<testData.length;++i) {testData[i]=inputData[i+numTrainData];}		
		//build file names
		setStraffordLRNFileNames(inputData.length, numTrainData, numTestData);
		dispMessage("Starting to save training/testing data partitions ");

		//call threads to instance and save different file formats
		if (numTrainData > 0) {
			//th_exec.execute(new straffDataWriter(this, dataFrmt, 0,trainData));	//lrn dense format
			th_exec.execute(new straffDataWriter(this, dataFrmt, 1,trainData));	
		}
		if (numTestData > 0) {
			th_exec.execute(new straffDataWriter(this, dataFrmt,2 ,testData));	
		}
		//save mins/maxes so this file data be reconstructed
		//save diffs and mins - csv files with each field value sep'ed by a comma
		//boundary region for training data
		String diffStr = "", minStr = "";
		for(int i =0; i<minsVals.length; ++i){
			minStr += String.format("%1.7g", minsVals[i]) + ",";
			diffStr += String.format("%1.7g", diffsVals[i]) + ",";
		}
		String minsFileName = getStraffMapMinsFileName();
		String diffsFileName = getStraffMapDiffsFileName();				
		saveStrings(minsFileName,new String[]{minStr});		
		saveStrings(diffsFileName,new String[]{diffStr});		
		dispMessage("Strafford Prospects Mins and Diffs Files Saved");	
	}//buildAndSaveTrainingData	
	
	//set names to be pre-calced results to test map
	private void setStraffNamesDBG(){
		int _numSmpls = 205406, _numTrain = 184865, _numTest = 20541;
		Calendar now = Calendar.getInstance();//F:\Strafford Project\SOM\StraffSOM_2018_10_24_12_37
		//String nowDirTmp = straffSOMProcSubDir+ "StraffSOM_"+getDateTimeString(true,false,"_", now)+File.separator;
		String fileNow = "10_24_12_37";
		String nowDir = getDirNameAndBuild(straffSOMProcSubDir+"StraffSOM_2018_"+fileNow+"_DebugRun"+File.separator);
		String[] tmp = {"Out_Straff_Smp_"+_numSmpls,
						"Train_Straff_Smp_"+_numTrain+"_of_"+_numSmpls+"_Dt_"+fileNow+".lrn",
						"Train_Straff_Smp_"+_numTrain+"_of_"+_numSmpls+"_Dt_"+fileNow+".svm",				
						"Test_Straff_Smp_"+_numTest+"_of_"+_numSmpls+"_Dt_"+fileNow+".csv",
						"Mins_Straff_Smp_"+_numSmpls+"_Dt_"+fileNow+".csv",
						"Diffs_Straff_Smp_"+_numSmpls+"_Dt_"+fileNow+".csv",
						"SOMImg_Straff_Smp_"+_numSmpls+"_Dt_"+fileNow+".png"
						};
		StraffordSOMLrnFN = new String[tmp.length];
		for(int i =0; i<StraffordSOMLrnFN.length;++i){	
			//build dir as well
			StraffordSOMLrnFN[i] = nowDir + tmp[i];
		}				
	}//setStraffordLRNFileNames
	
	//this will load an existing map - needs to have been built with the current data - data must be loaded!
	public void dbgBuildExistingMap() {
		//TODO this hard codes the source of the map data
		setStraffNamesDBG();		
		String lrnFileName = getStraffMapLRNFileName(), 		//dense training data .lrn file
				svmFileName = getStraffMapSVMFileName(),		//sparse training data svm file name
				testFileName = getStraffMapTestFileName(),		//testing data .csv file
				minsFileName = getStraffMapMinsFileName(),		//mins data percol .csv file
				diffsFileName = getStraffMapDiffsFileName(),	//diffs data percol .csv file
				
				outFilePrfx = getStraffMapOutFileBase() + "_x10_y10_k2";
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
		String cFN= "Not Used";
		String csvOutBaseFName = outFilePrfx + "_outCSV";
		initData();			
		setFlag(loaderRtnIDX, false);
		fnames = new SOMDatFileConfig(this);
		fnames.setAllFileNames(cFN,diffsFileName,minsFileName, lrnFileName, outFilePrfx, csvOutBaseFName);
		dispMessage("Current fnames before dataLoader Call : " + fnames.toString());
		th_exec.execute(new dataLoader(this,true,fnames));//fire and forget load task to load	
		
	}//dbgBuildExistingMap
	
	//called from StraffSOMExample after all occurences are built
	//this will add entries into an occ map with the passed date and opt value - opts are possible with no jps specified, if so this means all
	//not making occurences for negative opts, but for non-negative opts we can build the occurence information for all jps/jpgs.  This will be used for training data
	//only
	public void buildAllJpgJpOccMap(TreeMap<Integer, jpOccurrenceData> jpOccMap, Date eventDate, int opt) {
		if(jpgsToJps.size()==0) {		dispMessage("SOMMapData::buildAllJpgJpOccMap : Error - attempting to add all jpg's/jp's  to occurence data but haven't been aggregated yet."); return;}
		for (Integer jpg : jpgsToJps.keySet()) {
			TreeSet<Integer> jps = jpgsToJps.get(jpg);
			for (Integer jp : jps) {
				jpOccurrenceData jpOcc = jpOccMap.get(jp);
				if (jpOcc==null) {jpOcc = new jpOccurrenceData(jp, jpg);}
				jpOcc.addOccurence(eventDate, opt);		
				//add this occurence object to map at idx jp
				jpOccMap.put(jp, jpOcc);
			}
		}
	}//buildAllJpgJpOccMap
	
	//this will set the current jp->jpg data maps based on passed prospect data map
	//When acquiring new data, this must be performed after all data is loaded, but before
	//the prospect data is finalized and actual map is built due to the data finalization 
	//requiring a knowledge of the entire dataset to build weights appropriately
	public void setJPDataFromProspectData(ConcurrentSkipListMap<String, ProspectExample> map) {
		jpSeenCount = new TreeMap<Integer, Integer>(); 	//count of prospect training data records having jp
		jpEvSeenCount = new TreeMap<Integer, Integer>(); //count of prospect train records having jp only counting events
		jpPrspctSeenCount = new TreeMap<Integer, Integer>(); //count of prospect train records having jp only in base prospect record
		jpsToJpgs = new TreeMap<Integer, Integer>();	//map from jpgs to jps
		jpgsToJps = new TreeMap<Integer, TreeSet <Integer>>();
		
		jpToFtrIDX = new TreeMap<Integer, Integer>();	
		jpgToIDX = new TreeMap<Integer, Integer>();	
		//rebuild all jp->jpg mappings based on prospect data
		HashSet<Tuple<Integer,Integer>> tmpSetAllJpsJpgs = new HashSet<Tuple<Integer,Integer>>();
		for (ProspectExample ex : map.values()) {//for every prospect, look at every jp
			HashSet<Tuple<Integer,Integer>> tmpExSet = ex.getSetOfAllJpgJpData(); //tmpExSet is set of all jps/jpgs in ex
			for (Tuple<Integer,Integer> jpgJp : tmpExSet) {
				Integer jpg = jpgJp.x, jp=jpgJp.y;
				//if ((jp==-1) && (jpg==-2)){continue;}//don't add sentinel value//need to verify that sentinel values are not being made!
				boolean[] recMmbrship = ex.hasJP(jp);
				incrJPCounts(jp, jpSeenCount);
				if (recMmbrship[1]){incrJPCounts(jp,jpPrspctSeenCount);}
				if (recMmbrship[2]){incrJPCounts(jp,jpEvSeenCount);}
				tmpSetAllJpsJpgs.add(jpgJp);
			}											//add all tuples to set already seen
		}//for each prospect
		//get rid of sentinel value for opt out
		
		//build jpsToJpgs and JpgsToJps structs
		for (Tuple<Integer,Integer> jpgJp : tmpSetAllJpsJpgs) {
			Integer jpg = jpgJp.x, jp=jpgJp.y;
			jpsToJpgs.put(jp, jpg);
			TreeSet <Integer> jpList = jpgsToJps.get(jpg);
			if (jpList==null) {jpList = new TreeSet <Integer>();}
			jpList.add(jp);
			jpgsToJps.put(jpg, jpList);			
		}

		jpByIdx = jpSeenCount.keySet().toArray(new Integer[0]);
		jpgrpsByIdx = jpgsToJps.keySet().toArray(new Integer[0]);
		for(int i=0;i<jpByIdx.length;++i) {jpToFtrIDX.put(jpByIdx[i], i);}
		for(int i=0;i<jpgrpsByIdx.length;++i) {jpgToIDX.put(jpgrpsByIdx[i], i);}
		
		numFtrs = jpSeenCount.size();
		//rebuild calc object since feature terrain might have changed
		String calcDirName = this.getDirNameAndBuild(straffCalcInfoSubDir);
		
		ftrCalcObj = new StraffWeightCalc(this, calcDirName + calcWtFileName);
	}//setJPDataFromProspectData
	
	//process all events into training examples
	private void procRawEventData(ConcurrentSkipListMap<String, ProspectExample> tmpProspectMap, boolean saveBadRecs) {			
		
		for (int idx = 1; idx <straffDataFileNames.length;++idx) {
			ArrayList<BaseRawData> events = rawDataArrays.get(straffDataFileNames[idx]);
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
				dispMessage("SOMMapData::procRawEventData : # of "+eventType+" events without corresponding prospect records : "+badEventOIDs.size() + " | # Unique bad "+eventType+" event prospect OID refs (missing OIDs in prospect) : "+uniqueBadEventOIDs.size());
			}
		}
	}//procRawEventData
	
	
	//this will go through all the prospects, opts, and events and build a map of prospectExample keyed by prospect OID and holding all the known data
	private void procRawLoadedData(boolean eventsOnly, boolean appendToExistingData){
		if(appendToExistingData) {
			//TODO if we want to append prospect data to existing data map			
			setFlag(lrnDataSavedIDX, false);
			setAllTrainDatSaveFlags(false);
		} else {
			resetProspectMap();
		}
		dispMessage("Process all loaded data");
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
			 else {dispMessage("SOMMapData::procRawLoadedData : Prospect with OID : "+  prs.OID + " existed in map already and was replaced.");}
			 tmpProspectMap.put(ex.OID, ex);
		}
		procRawEventData(tmpProspectMap, true);
//		//to monitor bad event formats - events with OIDs that are not found in prospect DB data
//		TreeMap<String, Integer> badOptOIDsOps = new TreeMap<String, Integer>();		
//		//now add order events
//		String ordrBadFName = straffBasefDir + straffDataFileNames[orderEvntIDX] +"_bad_OIDs.csv";		
//		ArrayList<String> badOrdrOIDs = new ArrayList<String>();
//		ArrayList<BaseRawData> orders = rawDataArrays.get(straffDataFileNames[orderEvntIDX]);
//		HashSet<String> uniqueBadOrderOIDs = new HashSet<String>();
//		for (BaseRawData obj : orders) {
//			ProspectExample ex = tmpProspectMap.get(obj.OID);
//			if (ex == null) {//means no prospect object corresponding to the OID in this event
//				badOrdrOIDs.add(obj.OID);
//				uniqueBadOrderOIDs.add(obj.OID);
//				continue;}
//			ex.addOrderObj((OrderEvent) obj);
//		}
//		//now add opt events
//		String optBadFName = straffBasefDir + straffDataFileNames[optEvntIDX] +"_bad_OIDs.csv";
//		ArrayList<String> badOptOIDs = new ArrayList<String>();
//		ArrayList<BaseRawData> opts = rawDataArrays.get(straffDataFileNames[optEvntIDX]);
//		HashSet<String> uniqueBadOptOIDs = new HashSet<String>();		
//		for (BaseRawData obj : opts) {
//			ProspectExample ex = tmpProspectMap.get(obj.OID);
//			if (ex == null) {//means no prospect object corresponding to the OID in this event
//				badOptOIDs.add(obj.OID);
//				uniqueBadOptOIDs.add(obj.OID);		
//				badOptOIDsOps.put(obj.OID, ((OptEvent)obj).getOptType());
//				continue;}			
//			//if (ex.OID.toUpperCase().equals("US_000060959")) {dispMessage("Prospect Example opts proced for prospect with key US_000060959 : "+obj.toString());	 }//US_000060959 is a record with opt out of specific jps
//			ex.addOptObj((OptEvent) obj);					
//		}		
//		if (badOrdrOIDs.size() > 0) {saveStrings(ordrBadFName, uniqueBadOrderOIDs.toArray(new String[0]));		}
//		if (badOptOIDs.size() > 0) {saveStrings(optBadFName, uniqueBadOptOIDs.toArray(new String[0]));		}
//		int numMissingGoodOpts = 0;
//		for(String OID : badOptOIDsOps.keySet()) {
//			Integer opt = badOptOIDsOps.get(OID);
//			if (opt > -2) {++numMissingGoodOpts;}
//		}
//		
//		dispMessage("# bad orders : "+badOrdrOIDs.size()+"| # bad opts : " + badOptOIDs.size());
//		dispMessage("# unique bad orders : "+uniqueBadOrderOIDs.size()+"| # unique bad opts : " + uniqueBadOptOIDs.size()+" | # of opt records with opt > -2: "+numMissingGoodOpts);
		
		//finalize each prospect record, aggregate data-driven static vals, rebuild ftr vectors
		finishProspectBuild(tmpProspectMap);

		
		
		
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
		System.out.println("Raw Records Unique OIDs presented : " + tmpProspectMap.size()+" | Records found with trainable " + (eventsOnly ? " events " : "") + " info : " + prospectMap.size());
		//rawDataArrays = new ConcurrentSkipListMap<String, ArrayList<BaseRawData>>();
		//setAllFlags(new int[] {prospectDataLoadedIDX, optDataLoadedIDX, orderDataLoadedIDX}, false);
		setFlag(rawDataProcedIDX, true);
		saveAllProspectMapData(eventsOnly);
	}//procRawLoadedData
	
	//show first numToShow elemens of array of BaseRawData, either just to console or to applet window
	private void dispRawDataAra(ArrayList<BaseRawData> sAra, int numToShow) {
		if (sAra.size() < numToShow) {numToShow = sAra.size();}
		for(int i=0;i<numToShow; ++i){dispMessage(sAra.get(i).toString());}
	}	
	
	public void dbgShowAllRawData() {
		int numToShow = 10;
		for (String key : rawDataArrays.keySet()) {
			dispMessage("Showing first "+ numToShow + " records of data at key " + key);
			dispRawDataAra(rawDataArrays.get(key), numToShow);
		}
	}//showAllRawData
	//debugging function to display all unique jps seen in data
	public void dbgShowUniqueJPsSeen() {
		dispMessage("All Jp's seen : ");
		for (Integer key : jpSeenCount.keySet()) {dispMessage("JP : "+ String.format("%3d",key) + "   |Count : " + String.format("%6d",jpSeenCount.get(key)) + "\t|Ftr IDX : " + String.format("%3d",jpToFtrIDX.get(key)) + "\t|Owning JPG : " + String.format("%2d",jpsToJpgs.get(key)));}
		dispMessage("Number of unique JP's seen : " + jpSeenCount.size());
		dbgDispKnownJPsJPGs();
	}//dbgShowUniqueJPsSeen
	
	public void dbgDispKnownJPsJPGs() {
		dispMessage("\nJPGs seen : (jp : count : ftridx) :");
		for (Integer jpgrp : jpgsToJps.keySet()) {
			String res = "JPG : " + String.format("%3d", jpgrp);
			TreeSet <Integer> jps = jpgsToJps.get(jpgrp);			
			for (Integer jp : jps) {res += " ("+String.format("%3d", jp)+" :"+String.format("%6d", jpSeenCount.get(jp))+" : "+ String.format("%3d", jpToFtrIDX.get(jp))+"),";}
			dispMessage(res);		
		}
	}//dbgDispKnownJPsJPGs
	
	//display current calc function's equation coefficients for each JP
	public void dbgShowCalcEqs() {
		if (null == ftrCalcObj) {	dispMessage("SOMMapData::dbgShowCalcEqs : No calc object made to display.");return;	}
		dispMessage("Weight Calculation Equations : \n"+ftrCalcObj.toString());		
	}
	
	//display all currently calculated feature vectors
	public void dbgShowAllFtrVecs() {
		dispMessage("Feature vectors of all examples : ");	
		int numBadEx = 0;
		for (ProspectExample ex : prospectMap.values()) {	
			float[] ftrData = ex.getFtrs();
			if(ex.isBadExample()) {		++numBadEx;	} else {			dispMessage(ex.OID + " : " + getFloatAraStr(ftrData, "%.6f", 80));}
		}
		dispMessage("# of bad examples (which have empty feature vectors and are useless for training) : " + numBadEx);
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
	public void drawTrainData(SOM_StraffordMain pa, boolean drawLbls) {
		if(drawLbls){
			for(int i=0;i<trainData.length;++i){trainData[i].drawMeLblMap(pa,trainData[i].label,false);}} 
		else {for(int i=0;i<trainData.length;++i){trainData[i].drawMeMap(pa,trainData[i].label);}}	
	}//drawTrainData
	
	public void drawAllNodes(SOM_StraffordMain pa, int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		for(SOMMapNodeExample node : MapNodes.values()){	node.drawMeSmallBk(pa);	}
		pa.popStyle();pa.popMatrix();
	} 
		
	public void drawExMapNodes(SOM_StraffordMain pa,int[] dpFillClr, int[] dpStkClr) {
		pa.pushMatrix();pa.pushStyle();
		pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		for(SOMMapNodeExample node : nodesWithEx){			node.drawMeMap(pa, node.label);}
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
			dispMessage(s);
		}
	}
	public void dispMessage(String msg) {
		if (p == null) {System.out.println(msg);} else {p.outStr2Scr(msg);}
	}//dispMessage
	

	public void setMapX(int _x){
		//need to update UI value in win
		mapX = _x;
		if (win != null) {			
			boolean didSet = win.setWinToUIVals(win.uiMapColsIDX, mapX);
			if(!didSet){dispMessage("Setting ui map x value failed for x = " + _x);}
		}
	}
	public void setMapY(int _y){
		//need to update UI value in win
		mapY = _y;
		if (win != null) {			
			boolean didSet = win.setWinToUIVals(win.uiMapRowsIDX, mapY);
			if(!didSet){dispMessage("Setting ui map y value failed for y = " + _y);}
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
			case dataLoadedIDX	: {break;}		
			case loaderRtnIDX : {break;}
			case mapSetSmFtrZeroIDX : {break;}
			case prospectDataLoadedIDX: {break;}		//raw prospect data has been loaded but not yet processed
			case optDataLoadedIDX: {break;}				//raw opt data has been loaded but not processed
			case orderDataLoadedIDX: {break;}			//raw order data loaded not proced
			case rawDataProcedIDX: {break;}				//all raw data has been processed into StraffSOMExamples and subsequently erased
			case lrnDataSavedIDX : {
				if (val) {dispMessage("All "+ this.numTrainData +" Dense Training data saved to .lrn file");}
				break;}				//all prospect examples saved as training data
			case svmDataSavedIDX : {
				if (val) {dispMessage("All "+ this.numTrainData +" Sparse Training data saved to .svm file");}
				break;}				//all prospect examples saved as training data
			case testDataSavedIDX : {
				if (val) {dispMessage("All "+ this.numTestData + " saved to .csv file");}
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
	
	public SOM_MAPDat(String _SOM_Dir, String _execStr, HashMap<String, Integer> _mapInts, HashMap<String, Float> _mapFloats, HashMap<String, String> _mapStrings, String _trndDenseFN, String _trndSparseFN, String _outPfx){
		execDir = _SOM_Dir;
		execStr = _execStr;
		mapInts = _mapInts;
		isSparse = (mapInts.get("mapKType") > 1);//0 and 1 are dense cpu/gpu, 2 is sparse cpu
		mapFloats = _mapFloats;
		mapStrings = _mapStrings;
		trainDataDenseFN = _trndDenseFN;
		trainDataSparseFN = _trndSparseFN;
		outFilesPrefix = _outPfx;
	}
	
	//build execution string for SOM_MAP
	public String[] execStrAra(){
		String[] res = new String[]{execDir, execStr,
				"-k",""+mapInts.get("mapKType"),"-x",""+mapInts.get("mapCols"),"-y",""+mapInts.get("mapRows"), "-e",""+mapInts.get("mapEpochs"),"-r",""+mapInts.get("mapStRad"),"-R",""+mapInts.get("mapEndRad"),
				"-l",""+String.format("%.4f",mapFloats.get("mapStLrnRate")),"-L",""+String.format("%.4f",mapFloats.get("mapEndLrnRate")), 
				"-m",""+mapStrings.get("mapBounds"),"-g",""+mapStrings.get("mapGridShape"),"-n",""+mapStrings.get("mapNHood"), "-T",""+mapStrings.get("mapLearnCool"), 
				"-v 2",
				"-t",""+mapStrings.get("mapRadCool"), (isSparse ? trainDataSparseFN : trainDataDenseFN) , outFilesPrefix};
		return res;		
	}//execString
	
	public boolean isToroidal(){return (mapStrings.get("mapBounds").equals("toroid"));}
	
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
	public SOMMapData map;			//owning map
	public int fIdx;
	public String name;
	public TreeMap<Float,SOMMapNodeExample> sortedBMUs;		//best units for this particular feature, based on weight ratio (this is not actual weight of feature in node)

	public SOMFeature(SOMMapData _map, String _name, int _fIdx, String[] _tkns){//_tkns is in order idx%3==0 : wt ration, idx%3==1 : y coord, idx%3==2 : x coord
		map=_map;fIdx=_fIdx;name=_name;
		setBMUWts(_tkns);
	}
	
	public void setBMUWts(String[] _tkns){
		if(_tkns == null){map.dispMessage("Feature wts not found for feature : " + name + " idx : "+ fIdx);return;}
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
	public SOMMapData map;
	public String[] ftrNames;
	public int numFtrs;		
	
	public dataDesc(SOMMapData _map,int _numFtrs){
		map=_map;
		numFtrs = _numFtrs;
		ftrNames = new String[numFtrs];		
	}
	
	public dataDesc(SOMMapData _map,String [] tkns){
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

