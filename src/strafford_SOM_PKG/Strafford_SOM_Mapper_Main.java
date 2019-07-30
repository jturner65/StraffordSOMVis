/**
 * 
 */
package strafford_SOM_PKG;

import java.io.File;
import java.util.TreeMap;

import net.sourceforge.argparse4j.*;
import net.sourceforge.argparse4j.inf.*;
import base_Utils_Objects.io.MessageObject;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

/**
 * this class will launch the SOM project without any dependencies on any processing applets or code
 * @author john
 */
public class Strafford_SOM_Mapper_Main {
	Straff_SOMMapManager mapMgr;
	private MessageObject msgObj;
	private ArgumentParser argParser;
	/**
	 * 
	 */
	public Strafford_SOM_Mapper_Main(String[] args) {
		//dims should be large enough to make sure map nodes can be mapped to spaces - only used for displaying results and for placing map nodes in "locations" so that distance can be quickly computed
		float[] _dims = new float[] {834.8f,834.8f};
		//set default directories
		
		argParser = buildArgParser();
		Namespace res = null;
        try {
            res = argParser.parseArgs(args);
        } catch (ArgumentParserException e) {          
        	argParser.handleError(e);   
        	System.exit(1);
        }

        TreeMap<String, Object> resMap = (TreeMap<String, Object>) res.getAttrs();
		
		mapMgr = new Straff_SOMMapManager( _dims, resMap);
		msgObj = mapMgr.buildMsgObj();
		msgObj.dispInfoMessage("SOM_Strafford_Main", "constructor", "Begin SOM Process Execution");
		execSOMProc(resMap);
		msgObj.dispInfoMessage("SOM_Strafford_Main", "constructor", "Finished SOM Process Execution");		
	}//main
	
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
	
	private ArgumentParser buildArgParser() {
       ArgumentParser parser = ArgumentParsers.newFor("SOM_Strafford_Main").build()
                .description("Preprocess Raw customer, prospect and product data, train a self organizing map on the customers and use the map to draw suggest likely products for customers and prospects.");

        parser.addArgument("-c","--configdir")
        	.dest("configDir")
        	.type(String.class)
        	.setDefault("StraffordProject" + File.separator+"config"+File.separator)
        	.help("Specify the directory where project configuration files can be found (relative to current execution directory).");
		
        parser.addArgument("-d","--datadir")
        	.dest("dataDir")
        	.type(String.class)
        	.setDefault("StraffordProject" + File.separator)
        	.help("Specify the directory where all data files can be found (in their respective subdirectories)(relative to current execution directory).");
        
        parser.addArgument("-l","--log")
	    	.dest("logLevel")
	    	.type(Integer.class)
	    	.choices(0, 1, 2)
	    	.setDefault(0)
	    	.help("Specify Logging level : 0 : Only display output to console; 1 : Save output to log file (log directory specified in config); 2 : both.");     
        
        parser.addArgument("-e","--exec")
        	.dest("exec")
        	.type(Integer.class)
	    	.choices(0, 1, 2)
	    	.setDefault(0)
	    	.help("Specify which process to execute : 0 : Preprocess Raw Data; 1 : Train SOM from preproc data (using configuration specified in ProjectConfig.txt file); 2 : Map all products, customers and prospects to specified product listing (set in ProjectConfig.txt file)");

        parser.addArgument("-r","--rawdatasrc")
        	.dest("rawDataSource")
        	.type(Integer.class)
        	.choices(0,1)
        	.setDefault(0)
        	.help("Specify whether we are using 0: csv files as raw data source or 1 : using an sql connection (if executing process 0).  Note : sql connection not yet supported.");
                
		return parser;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		@SuppressWarnings("unused")
		Strafford_SOM_Mapper_Main mainObj = new Strafford_SOM_Mapper_Main(args);

	}//main

}//StraffordSOM
