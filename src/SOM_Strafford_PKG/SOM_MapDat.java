package SOM_Strafford_PKG;
/**
 * This object holds the configuration information for a SOMOCLU-based map to be trained and consumed.  
 * An instance of this class object must be the prime source for all actual map-based configuration and 
 * execution commands - all consumption/access should be subordinate to this object
 * 
 * @author john
 */

import java.util.*;

public class SOM_MapDat{
	private final String curOS;							//os currently running - use to modify exec string for mac?
	private String execDir;								//SOM_MAP execution directory
	private String execSOMStr;
	private HashMap<String, Integer> mapInts;			// mapCols (x), mapRows (y), mapEpochs, mapKType, mapStRad, mapEndRad;
	private HashMap<String, Float> mapFloats;			// mapStLrnRate, mapEndLrnRate;
	private HashMap<String, String> mapStrings;			// mapGridShape, mapBounds, mapRadCool, mapNHood, mapLearnCool;	
	
	private String trainDataDenseFN,		//training data file name for dense data
			trainDataSparseFN,			//for sparse data
			outFilesPrefix;			//output from map prefix
	private boolean isSparse;
	private String[] execStrAra;	//holds arg list sent to som executable
	private String dbgExecStr;		//string to be executed on command line, for ease in debugging	
	
	//boolean state flags
	private int[] stFlags;						//state flags - bits in array holding relevant process info
	private static int 
		debugIDX 		= 0,
		rdyToTrainIDX	= 1,
		trainedIDX		= 2;
	
	private static int numFlags = 3;
		
	//
	public SOM_MapDat(String _curOS) {
		curOS = _curOS;
		mapInts = new HashMap<String, Integer>();			// mapCols (x), mapRows (y), mapEpochs, mapKType, mapStRad, mapEndRad;
		mapFloats = new HashMap<String, Float>();			// mapStLrnRate, mapEndLrnRate;
		mapStrings	= new HashMap<String, String>();		// mapGridShape, mapBounds, mapRadCool, mapNHood, mapLearnCool;	
		
	}//ctor
	
	//set all SOM data from UI values passed from map manager TODO this will be deprecated - these values should be set as UI input changes
	//public void setUIMapData(String _SOM_Dir, String _execStr, HashMap<String, Integer> _mapInts, HashMap<String, Float> _mapFloats, HashMap<String, String> _mapStrings, String _trndDenseFN, String _trndSparseFN, String _outPfx){
	public void setUIMapData(SOMProjConfigData _config, HashMap<String, Integer> _mapInts, HashMap<String, Float> _mapFloats, HashMap<String, String> _mapStrings, String _outFilesPrefix){
		execDir = _config.getSOM_FullExecPath();
		execSOMStr = _config.getSOM_Map_EXECSTR();
		trainDataDenseFN = _config.getSOMMapLRNFileName();
		trainDataSparseFN = _config.getSOMMapSVMFileName();
		outFilesPrefix = _outFilesPrefix;

		mapInts = _mapInts;
		isSparse = (mapInts.get("mapKType") > 1);//0 and 1 are dense cpu/gpu, 2 is sparse cpu
		mapFloats = _mapFloats;
		mapStrings = _mapStrings;
		init();
	}//SOM_MapDat ctor from data	
	
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
			if(_descrAra[idx].contains("### Base Vars")) {foundDataPartition=true;}
			++idx;		
		}
		if(idx == numLines) {System.out.println("SOM_MapDat::buildSOMMapDatFromAra : ERROR :  Array of description information not correct format to build SOM_MapDat object.  Aborting.");	return;	}
		//read in method vars
		tmpVars = _readArrayIntoStringMap(new int[] {idx}, numLines, "### mapInts descriptors", _descrAra);
		if (tmpVars == null) {return;}
		execDir = tmpVars.get("execDir").trim();
		execSOMStr = tmpVars.get("execSOMStr").trim();
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
	}//SOM_MapDat ctor from string ara 	
	
	//after-construction initialization code for both ctors
	private void init() {
		execStrAra = buildExecStrAra();					//build execution string array used by processbuilder
		dbgExecStr = execDir + execSOMStr;
		for(int i=0;i<execStrAra.length;++i) {	dbgExecStr += execStrAra[i] + " ";}
	}//init
		
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
		if(idx[0] == numLines) {System.out.println("SOM_MapDat::buildSOMMapDatFromAra : ERROR :  Array of description information not correct format to build SOM_MapDat object - failed finding partition bound : " +_partitionStr + ".  Aborting.");	return null;}
		return tmpVars;
	}//read array into map of strings, to be processed into object variables

	//return string array describing this SOM map execution in csv format so can be saved to a file - group each construct by string title
	public ArrayList<String> buildStringDescAra() {
		ArrayList<String> res = new ArrayList<String>();
		res.add("### This file holds description of SOM map experiment execution settings");
		res.add("### It should be used to build a SOM_MapDat object which then is consumed to control the execution of the SOM.");
		res.add("### Base Vars");
		res.add("execDir,"+execDir);
		res.add("execStr,"+execSOMStr);
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
	
	//build execution string for SOM_MAP - should always be even in length
	private String[] buildExecStrAra(){
		String useQts = (curOS.toLowerCase().contains("win") ? "\"" : "");
		String[] res;
		res = new String[]{//execDir, execSOMStr,
		"-k",""+mapInts.get("mapKType"),"-x",""+mapInts.get("mapCols"),"-y",""+mapInts.get("mapRows"), "-e",""+mapInts.get("mapEpochs"),"-r",""+mapInts.get("mapStRad"),"-R",""+mapInts.get("mapEndRad"),
		"-l",""+String.format("%.4f",mapFloats.get("mapStLrnRate")),"-L",""+String.format("%.4f",mapFloats.get("mapEndLrnRate")), 
		"-m",""+mapStrings.get("mapBounds"),"-g",""+mapStrings.get("mapGridShape"),"-n",""+mapStrings.get("mapNHood"), "-T",""+mapStrings.get("mapLearnCool"), 
		"-v", "2",
		"-t",""+mapStrings.get("mapRadCool"), useQts +(isSparse ? trainDataSparseFN : trainDataDenseFN) + useQts , useQts + outFilesPrefix +  useQts};
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
	
		
	@Override
	public String toString(){
		String res = "Map config : SOM_MAP Dir : " + execDir +"\n";
		res += "Kernel(k) : "+mapInts.get("mapKType") + "\t#Cols : " + mapInts.get("mapCols") + "\t#Rows : " + mapInts.get("mapRows") + "\t#Epochs : " + mapInts.get("mapEpochs") + "\tStart Radius : " +mapInts.get("mapStRad") + "\tEnd Radius : " + +mapInts.get("mapEndRad")+"\n";
		res += "Start Learning Rate : " + String.format("%.4f",mapFloats.get("mapStLrnRate"))+"\tEnd Learning Rate : " + String.format("%.4f",mapFloats.get("mapEndLrnRate"))+"\n";
		res += "Boundaries : "+mapStrings.get("mapBounds") + "\tGrid Shape : "+mapStrings.get("mapGridShape")+"\tNeighborhood Function : " + mapStrings.get("mapNHood") + "\nLearning Cooling: " + mapStrings.get("mapLearnCool") + "\tRadius Cooling : "+ mapStrings.get("mapRadCool")+"\n";		
		res += "Training data : "+(isSparse ? ".svm (Sparse) file name : " + trainDataSparseFN :  ".lrn (dense) file name : " + trainDataDenseFN) + "\nOutput files prefix : " + outFilesPrefix +"\n";
		return res;
	}
	
}//SOM_MapDat

////describe data in SOM map, per feature. TODO not sure if this is necessary for this project.  Current only was built in data loading
//class dataDesc{
//	public SOMMapManager map;
//	public String[] ftrNames;
//	public int numFtrs;		
//	
//	public dataDesc(SOMMapManager _map,int _numFtrs){
//		map=_map;
//		numFtrs = _numFtrs;
//		ftrNames = new String[numFtrs];		
//	}
//	
//	public dataDesc(SOMMapManager _map,String [] tkns){
//		this(_map, tkns.length);
//		System.arraycopy(tkns, 0, ftrNames, 0, tkns.length);
//	}
//	
//	//build the default header for this data descriptor
//	public void buildDefHdr(){for(int i =0; i<numFtrs; ++i){ftrNames[i] = "ftr_"+i;}}//buildDefHdr
//	
//	public String toString(){
//		String res = "";
//		for(int i=0;i<ftrNames.length;++i){res += ftrNames[i] + ",";}
//		return res;
//	}
//	
//}//class dataDesc