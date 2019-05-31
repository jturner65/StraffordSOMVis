/**
 * 
 */
package strafford_SOM_PKG;

import java.io.File;
import java.util.TreeMap;

import net.sourceforge.argparse4j.*;
import net.sourceforge.argparse4j.impl.*;
import net.sourceforge.argparse4j.inf.*;
import base_SOM_Objects.som_utils.SOMProjConfigData;
import base_Utils_Objects.MessageObject;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

/**
 * this class will launch the SOM project without any dependencies on any processing applets or code
 * @author john
 */
public class SOM_Strafford_Main {
	Straff_SOMMapManager mapMgr;
	private MessageObject msgObj;
	private ArgumentParser argParser;
	/**
	 * 
	 */
	public SOM_Strafford_Main(String[] args) {
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
			return;}
		case 2 :{
			//this will take pretrained map specified in config file, load it and then process all products, customers and prospects against map for 
			//product list specified in config file
			Double prodZoneDistThresh = (Double)resMap.get("prodZoneDistThresh");
			mapMgr.loadMapProcAllData(prodZoneDistThresh);
			
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
        
        parser.addArgument("-p","--prodzonedist")
        	.dest("prodZoneDistThresh")
        	.type(Double.class)
        	.setDefault(2.0)
        	.help("Specify distance threshold for product mappings to map nodes - shouldn't be <= 0.0");
        
		return parser;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		SOM_Strafford_Main mainObj = new SOM_Strafford_Main(args);

	}//main

}//StraffordSOM
