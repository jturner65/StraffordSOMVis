package strafford_SOM_PKG;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import base_Utils_Objects.appManager.Console_AppManager;
import base_Utils_Objects.appManager.argParse.cmdLineArgs.base.Base_CmdLineArg;
import base_Utils_Objects.io.messaging.MessageObject;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

/**
 * this class will launch the SOM project without any dependencies on any processing applets or code
 * @author john
 */
public class Strafford_SOM_Mapper_Main extends Console_AppManager {
	private final String prjNmShrt = "SOM_Strafford_Main";
	private final String prjNmLong = "Strafford Customer, Prospect and Product reporting and prediction.";	
	private final String projDesc = "Preprocess Raw customer, prospect and product data, train a self organizing map on the customers and use the map to draw suggest likely products for customers and prospects.";
	Straff_SOMMapManager mapMgr;
	private MessageObject msgObj;
	/**
	 * 
	 */
	public Strafford_SOM_Mapper_Main() {
		super();
		//dims should be large enough to make sure map nodes can be mapped to spaces - only used for displaying results and for placing map nodes in "locations" so that distance can be quickly computed
		//float[] _dims = new float[] {834.8f,834.8f};
		//set default directories
	}//main
	
	public void initExec() {
		TreeMap<String, Object> argsMap = getArgsMap();
		mapMgr = new Straff_SOMMapManager(argsMap);
		msgObj = mapMgr.buildMsgObj();
		msgObj.dispInfoMessage("SOM_Strafford_Main", "constructor", "Begin SOM Process Execution");
		execSOMProc(argsMap);
		msgObj.dispInfoMessage("SOM_Strafford_Main", "constructor", "Finished SOM Process Execution");			
	}
	
	private void execSOMProc(TreeMap<String, Object> resMap) {
		Integer exec = (Integer) resMap.get("exec");
		switch(exec) {
		case 0 :{		//preprocess raw data and save
			Integer rawDataSource = (Integer) resMap.get("rawDataSource");
			mapMgr.loadAndPreProcAllRawData((rawDataSource==0));
			return;}
		case 1 :{		//build map using values specified in map config
			//this will load training data and build map
			boolean success = mapMgr.loadTrainDataMapConfigAndBuildMap(false);		
			if(!success) {
				msgObj.dispInfoMessage("SOM_Strafford_Main", "execSOMProc","Failure in attempting to load training data and specified map configuration");
			}
			return;}
		case 2 :{
			/**
			 * Use this method to map all prospects(cust and true) and products to existing map specified in project config, and save mappings
			 * 1) load training data and products for map
			 * 2) load map data and derive map node bmus for prospects and products, building jp and jpg segments
			 * 3) load true prospects and map them to map via euclidean dists to map nodes to find their bmus
			 * 4) save all mappings 
			 */
			mapMgr.loadAllDataAndBuildMappings();
			
			return;}
		}
		
	}//execSOMProc
	
	@Override
	public String getPrjNmShrt() {return prjNmShrt;}
	@Override
	public String getPrjNmLong() {return prjNmLong;}
	@Override
	public String getPrjDescr() {return projDesc;}
	
	@Override
	protected ArrayList<Base_CmdLineArg> getCommandLineParserAttributes() {
		ArrayList<Base_CmdLineArg> cmdArgs = new ArrayList<Base_CmdLineArg>();
		//config dir	
		cmdArgs.add(buildStringCommandLineArgDesc('c', "configdir", 
				"configDir", "Specify the directory where project configuration files can be found (relative to current execution directory).",
				"StraffordProject" + File.separator+"config"+File.separator, null));

		//data dir
		cmdArgs.add(buildStringCommandLineArgDesc('d', "datadir", "dataDir", 
				"Specify the directory where all data files can be found (in their respective subdirectories)(relative to current execution directory).",
				"StraffordProject" + File.separator, null));

		//log settings
		cmdArgs.add(buildIntCommandLineArgDesc('l', "log", "logLevel", 
				"Specify Logging level : 0 : Only display output to console; 1 : Save output to log file (log directory specified in config); 2 : both.",
				0, Arrays.asList(0,1,2), null));
       //exec settings
		cmdArgs.add(buildIntCommandLineArgDesc('e', "exec", "exec", 
				"Specify which process to execute : 0 : Preprocess Raw Data; 1 : Train SOM from preproc data (using configuration specified in ProjectConfig.txt file); 2 : Map all products, customers and prospects to specified product listing (set in ProjectConfig.txt file)",
				0, Arrays.asList(0,1,2), null));
		
		//raw data source
		cmdArgs.add(buildIntCommandLineArgDesc('r', "rawdatasrc", "rawDataSource", 
				"Specify whether we are using 0: csv files as raw data source or 1 : using an sql connection (if executing process 0).  Note : sql connection not yet supported.",
				0, Arrays.asList(0,1), null));
		
		return cmdArgs;
	}

	@Override
	protected TreeMap<String, Object> setRuntimeArgsVals(Map<String, Object> _passedArgsMap) {
		//Not overriding any args
		return (TreeMap<String, Object>) _passedArgsMap;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Strafford_SOM_Mapper_Main mainObj = new Strafford_SOM_Mapper_Main();
		Strafford_SOM_Mapper_Main.invokeMain(mainObj, args);
		mainObj.initExec();

	}//main

}//StraffordSOM
