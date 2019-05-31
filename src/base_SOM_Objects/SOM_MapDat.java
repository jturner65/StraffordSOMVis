package base_SOM_Objects;

/**
 * This object holds the configuration information for a SOMOCLU-based map to be trained and consumed.  
 * An instance of this class object must be the prime source for all actual map-based configuration and 
 * execution commands - all consumption/access should be subordinate to this object
 * 
 * @author john
 */

import java.io.File;
import java.util.*;

import base_SOM_Objects.som_utils.SOMProjConfigData;
import base_Utils_Objects.*;

public class SOM_MapDat{
	//project config object
	private SOMProjConfigData config;
	//object to faciliate printing to screen or log file
	private MessageObject msgObj = null;
	//os currently running - use to modify exec string for mac/linux
	private final String curOS;		
	//SOM_MAP execution directory
	private String execDir;		
	//actual string used to execute som program
	private String execSOMStr;
	//these are name-value pairs of command line arguments for SOM exe
	private HashMap<String, Integer> mapInts;			// mapCols (x), mapRows (y), mapEpochs, mapKType, mapStRad, mapEndRad;
	private HashMap<String, Float> mapFloats;			// mapStLrnRate, mapEndLrnRate;
	private HashMap<String, String> mapStrings;			// mapGridShape, mapBounds, mapRadCool, mapNHood, mapLearnCool;	
	
	//instance directory within execDir for som data - does not have separator
	private String SOM_instanceDirName;
	//file names used for SOM
	private String trainDataDenseFN,		//training data file name for dense data, including -relative- path
		trainDataSparseFN,				//for sparse data, including -relative- path
		outFilesPrefix;					//output from map prefix, including -relative- path
	private String trainDataDenseFN_fullPath,		//training data file name for dense data, including -relative- path
		trainDataSparseFN_fullPath,				//for sparse data, including -relative- path
		outFilesPrefix_fullPath;					//output from map prefix, including -relative- path
	private boolean isSparse;
	private String[] execStrAra;			//holds arg list sent to som executable
	private String dbgExecStr;				//string to be executed on command line, built for ease in debugging		
	//types of data kernel (config) supported by somoclu-based som executable - describes data format and whether computed via strictly cpu computation or using cuda
	private static final String[] kernelTypes = new String[] {"Dense CPU","Dense GPU","Sparse CPU"};

	//not ready to be used until given data
	public SOM_MapDat(SOMProjConfigData _config, String _curOS) {
		curOS = _curOS;
		config = _config;
		mapInts = new HashMap<String, Integer>();			// mapCols (x), mapRows (y), mapEpochs, mapKType, mapStRad, mapEndRad;
		mapFloats = new HashMap<String, Float>();			// mapStLrnRate, mapEndLrnRate;
		mapStrings	= new HashMap<String, String>();		// mapGridShape, mapBounds, mapRadCool, mapNHood, mapLearnCool;	
		msgObj = config.buildMsgObj();
	}//ctor
		
	//set current configuration of map
	public void setArgsMapData(HashMap<String, Integer> _mapInts, HashMap<String, Float> _mapFloats, HashMap<String, String> _mapStrings){
		mapInts.clear();
		mapFloats.clear();
		mapStrings.clear();
		for(String key : _mapInts.keySet()) {mapInts.put(key, _mapInts.get(key));}
		for(String key : _mapFloats.keySet()) {mapFloats.put(key, _mapFloats.get(key));}
		for(String key : _mapStrings.keySet()) {mapStrings.put(key, _mapStrings.get(key));}
		//mapInts = _mapInts;
		//mapFloats = _mapFloats;
		//mapStrings = _mapStrings;
		
		isSparse = (mapInts.get("mapKType") > 1);//0 and 1 are dense cpu/gpu, 2 is sparse cpu
		//setAllDirs(_config);
		updateMapDescriptorState();
	}//SOM_MapDat ctor from data	
	
	//must be called -every time- any internal map data is changed
	public void updateMapDescriptorState() {
		setAllDirs();
		init();
	}//setAllDirs
	//set local refs to directories
	private void setAllDirs() {
		execDir = config.getSOMExec_FullPath();
		execSOMStr = config.getSOM_Map_EXECSTR();
		SOM_instanceDirName = config.getSOMMap_CurrSubDirNoSep();
		
		trainDataDenseFN = config.getSOMMap_lclLRNFileName();
		trainDataSparseFN = config.getSOMMap_lclSVMFileName();
		outFilesPrefix = config.getSOMMap_lclOutFileBase(getOutNameSuffix());	
		setAllFullPathNames();
	}//setAllDirs	
	
	private void setAllFullPathNames() {
		trainDataDenseFN_fullPath = execDir +SOM_instanceDirName + File.separator + trainDataDenseFN;
		trainDataSparseFN_fullPath = execDir +SOM_instanceDirName + File.separator + trainDataSparseFN;
		outFilesPrefix_fullPath = execDir +SOM_instanceDirName + File.separator + outFilesPrefix;		
	}
	
	//after-construction initialization code whenever map values are changed
	private void init() {
		execStrAra = buildExecStrAra();					//build execution string array used by processbuilder
		dbgExecStr = execDir + execSOMStr;
		for(int i=0;i<execStrAra.length;++i) {	dbgExecStr += " "+execStrAra[i];}
	}//init		

	/**
	 * update map descriptor int values
	 * @param key 
	 * @param val
	 */
	public void updateMapDat_Integer(String key, Integer val) {
		Integer oldVal = mapInts.get(key);
		if(oldVal==val) {return;}
		mapInts.put(key, val);
		isSparse = (mapInts.get("mapKType") > 1);//0 and 1 are dense cpu/gpu, 2 is sparse cpu
		init();
	}//updateMapDat_Int
	
	/**
	 * update map descriptor Float values
	 * @param key 
	 * @param val
	 */
	public void updateMapDat_Float(String key, Float val) {
		Float oldVal = mapFloats.get(key);
		if(oldVal==val) {return;}
		mapFloats.put(key, val);
		init();
	}//updateMapDat_Int
	
	/**
	 * update map descriptor String values
	 * @param key 
	 * @param val
	 */
	public void updateMapDat_String(String key, String val) {
		String oldVal = mapStrings.get(key);
		if(oldVal==val) {return;}
		mapStrings.put(key, val);
		init();
	}//updateMapDat_Int
	
	
	//return output name suffix used for this map's data files
	public String getOutNameSuffix() {	return "_x"+mapInts.get("mapCols")+"_y"+mapInts.get("mapRows")+"_k"+mapInts.get("mapKType");}	
	
	//build an object based on an array of strings read from a file
	//array is array of strings holding comma sep key-value pairs, grouped by construct, with tags denoting which construct
	public void buildFromStringArray(String[] _descrAra) {		
		mapInts = new HashMap<String, Integer>();			// mapCols (x), mapRows (y), mapEpochs, mapKType, mapStRad, mapEndRad;
		mapFloats = new HashMap<String, Float>();			// mapStLrnRate, mapEndLrnRate;
		mapStrings	= new HashMap<String, String>();			// mapGridShape, mapBounds, mapRadCool, mapNHood, mapLearnCool;	
		HashMap<String, String> tmpVars = new HashMap<String, String>();
		int idx = 0;
		boolean foundDataPartition = false;
		int numLines = _descrAra.length;
		while ((!foundDataPartition) && (idx < numLines)) {
			if(_descrAra[idx].contains(SOMProjConfigData.fileComment + " Base Vars")) {foundDataPartition=true;}
			++idx;		
		}
		if(idx == numLines) {msgObj.dispMessage("SOM_MapDat","buildFromStringArray","Array of description information not correct format to build SOM_MapDat object.  Aborting.",  MsgCodes.error2);	return;	}
		//use ara to pass index via ptr
		int[] idxAra = new int[] {idx};
		//read in method vars
		tmpVars = _readArrayIntoStringMap(idxAra, numLines, SOMProjConfigData.fileComment, _descrAra);// mapInts descriptors", _descrAra);
		if (tmpVars == null) {return;}
		//need to set these from application call/config?
		//execDir = tmpVars.get("execDir").trim();	//should always be retrieved from current execution environment
		execDir = config.getSOMExec_FullPath();
		execSOMStr = config.getSOM_Map_EXECSTR();
		SOM_instanceDirName = tmpVars.get("SOM_instanceDirName").trim();
		trainDataDenseFN = tmpVars.get("trainDataDenseFN").trim();
		trainDataSparseFN = tmpVars.get("trainDataSparseFN").trim();
		outFilesPrefix = tmpVars.get("outFilesPrefix").trim();
		setAllFullPathNames();
		
		isSparse = (tmpVars.get("isSparse").trim().toLowerCase().contains("true") ? true : false);
		//integer SOM cmnd line args
		tmpVars = _readArrayIntoStringMap(idxAra, numLines, SOMProjConfigData.fileComment, _descrAra);// mapFloats descriptors", _descrAra);
		if (tmpVars == null) {return;}
		for(String key : tmpVars.keySet()) {mapInts.put(key, Integer.parseInt(tmpVars.get(key).trim()));}
		//float SOM Cmnd Line Args
		tmpVars = _readArrayIntoStringMap(idxAra, numLines, SOMProjConfigData.fileComment, _descrAra);// mapStrings descriptors", _descrAra);
		if (tmpVars == null) {return;}
		for(String key : tmpVars.keySet()) {mapFloats.put(key, Float.parseFloat(tmpVars.get(key).trim()));}		
		//String SOM Cmnd Line Args
		tmpVars = _readArrayIntoStringMap(idxAra, numLines, SOMProjConfigData.fileComment, _descrAra);// End Descriptor Data", _descrAra);
		if (tmpVars == null) {return;}
		for(String key : tmpVars.keySet()) {mapStrings.put(key, tmpVars.get(key).trim());}
		init();
	}//SOM_MapDat ctor from string ara 	
	
	//read string array into map of string-string key-value pairs.  idx passed as reference (in array)
	private HashMap<String, String> _readArrayIntoStringMap(int[] idx, int numLines, String _partitionStr, String[] _descrAra){
		HashMap<String, String> tmpVars = new HashMap<String, String>();
		boolean foundDataPartition = false;
		//load base vars here
		while ((!foundDataPartition) && (idx[0] < numLines)) {
			String desc = _descrAra[idx[0]];
			if(desc.contains(_partitionStr)) {foundDataPartition=true; ++idx[0]; continue;}
			String[] dat = desc.trim().split(",");
			//System.out.println("IDX : " + idx[0] + " == "+  desc +"  | Split : "+ dat[0] +" | " + dat[1]);
			tmpVars.put(dat[0], dat[1]);
			++idx[0];	
		}	
		if(!foundDataPartition) {msgObj.dispMessage("SOM_MapDat","buildFromStringArray","Array of description information not correct format to build SOM_MapDat object - failed finding partition bound : " +_partitionStr + ".  Aborting.", MsgCodes.error2);	return tmpVars;}
		return tmpVars;
	}//read array into map of strings, to be processed into object variables

	//return string array describing this SOM map execution in csv format so can be saved to a file - group each construct by string title
	public ArrayList<String> buildStringDescAra() {
		ArrayList<String> res = new ArrayList<String>();
		res.add(SOMProjConfigData.fileComment + " This file holds description of SOM map experiment execution settings");
		res.add(SOMProjConfigData.fileComment + " It should be used to build a SOM_MapDat object which then is consumed to control the execution of the SOM.");
		res.add(SOMProjConfigData.fileComment + " Base Vars");
		//res.add("execDir,"+execDir); //should always be retrieved from current execution environment
		//res.add("execSOMStr,"+execSOMStr); //should always be retrieved from current execution environment
		res.add("isSparse,"+isSparse);
		res.add("SOM_instanceDirName,"+SOM_instanceDirName);
		res.add("trainDataDenseFN,"+trainDataDenseFN);
		res.add("trainDataSparseFN,"+trainDataSparseFN);
		res.add("outFilesPrefix,"+outFilesPrefix);
		res.add(SOMProjConfigData.fileComment + " mapInts descriptors");
		for (String key : mapInts.keySet()) {res.add(""+key+","+mapInts.get(key));}
		res.add(SOMProjConfigData.fileComment + " mapFloats descriptors");
		for (String key : mapFloats.keySet()) {res.add(""+key+","+String.format("%.6f", mapFloats.get(key)));}
		res.add(SOMProjConfigData.fileComment + " mapStrings descriptors");
		for (String key : mapStrings.keySet()) {res.add(""+key+","+mapStrings.get(key));}
		res.add(SOMProjConfigData.fileComment + " End Descriptor Data");		
		return res;		
	}//buildStringDescAra	
	
	//build execution string for SOM_MAP - should always be even in length
	private String[] buildExecStrAra(){
		String useQts = (curOS.toLowerCase().contains("win") ? "\"" : "");
		String[] res;
		res = new String[]{//execDir, execSOMStr,
		"-k",""+mapInts.get("mapKType"),"-x",""+mapInts.get("mapCols"),"-y",""+mapInts.get("mapRows"), "-e",""+mapInts.get("mapEpochs"),"-r",""+mapInts.get("mapStRad"),"-R",""+mapInts.get("mapEndRad"),
		"-l",""+String.format("%.4f",mapFloats.get("mapStLrnRate")),"-L",""+String.format("%.4f",mapFloats.get("mapEndLrnRate")), 
		"-m",""+mapStrings.get("mapBounds"),"-g",""+mapStrings.get("mapGridShape"),"-n",""+mapStrings.get("mapNHood"), "-T",""+mapStrings.get("mapLearnCool"), 
		"-v", "2",
		"-t",""+mapStrings.get("mapRadCool"), useQts +(isSparse ? trainDataSparseFN_fullPath : trainDataDenseFN_fullPath) + useQts , useQts + outFilesPrefix_fullPath +  useQts};
		return res;		
	}//execString
	
	public HashMap<String, Integer> getMapInts(){return mapInts;}
	public HashMap<String, Float> getMapFloats(){return mapFloats;}
	public HashMap<String, String> getMapStrings(){return mapStrings;}
	//return execution string array used by processbuilder
	public String[] getExecStrAra(){		return execStrAra;	}
	public String getDbgExecStr() {			return dbgExecStr;}
	//get working directory
	public String getExeWorkingDir() {		return execDir;}
	//executable invocation
	public String getExename() {		return execSOMStr;}
		
	public boolean isToroidal(){return (mapStrings.get("mapBounds").equals("toroid"));}
	
	//purely descriptive
	@Override
	public String toString(){
		String res = "Map config : SOM_MAP Dir : " + execDir +"\n";
		res += "Data Format and Computation Method(k) : "+kernelTypes[mapInts.get("mapKType")] + "\t#Cols : " + mapInts.get("mapCols") + "\t#Rows : " + mapInts.get("mapRows") + "\t#Epochs : " + mapInts.get("mapEpochs") + "\tStart Radius : " +mapInts.get("mapStRad") + "\tEnd Radius : " + +mapInts.get("mapEndRad")+"\n";
		res += "Start Learning Rate : " + String.format("%.4f",mapFloats.get("mapStLrnRate"))+"\tEnd Learning Rate : " + String.format("%.4f",mapFloats.get("mapEndLrnRate"))+"\n";
		res += "Boundaries : "+mapStrings.get("mapBounds") + "\tGrid Shape : "+mapStrings.get("mapGridShape")+"\tNeighborhood Function : " + mapStrings.get("mapNHood") + "\nLearning Cooling: " + mapStrings.get("mapLearnCool") + "\tRadius Cooling : "+ mapStrings.get("mapRadCool")+"\n";		
		res += "Training data (full path) : "+(isSparse ? ".svm (Sparse) file name : " + trainDataSparseFN_fullPath :  ".lrn (dense) file name : " + trainDataDenseFN_fullPath) + "\nOutput files prefix (full path) : " + outFilesPrefix_fullPath +"\n";
		res += "Training data : "+(isSparse ? ".svm (Sparse) file name : " + trainDataSparseFN :  ".lrn (dense) file name : " + trainDataDenseFN) + "\nOutput files prefix : " + outFilesPrefix +"\n";
		res +="\n dbgString of exec string : "+dbgExecStr+"\n";
		return res;
	}
	
}//SOM_MapDat
