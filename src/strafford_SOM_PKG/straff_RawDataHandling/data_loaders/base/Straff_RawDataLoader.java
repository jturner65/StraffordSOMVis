package strafford_SOM_PKG.straff_RawDataHandling.data_loaders.base;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.*;

import base_Utils_Objects.io.MessageObject;
import base_Utils_Objects.io.MsgCodes;
import strafford_SOM_PKG.straff_RawDataHandling.Straff_SOMRawDataLdrCnvrtr;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.base.Straff_BaseRawData;

/**
* this class will load, and manage, the appropriate files containing 
* the Strafford data used to build the SOM.  Ultimately this class should 
* also execute the appropriate SQL commands to call db and retrieve raw 
* data directly
* 
* This raw data is used to build the preprocessed data that is then saved 
* locally and used to build training, testing and verification examples
* 
* @author john
*
*/

//mltemp.live_jps <--table holding jps found in tags (i.e. actual products).

public abstract class Straff_RawDataLoader implements Callable<Boolean> {
	//ref to owning object
	//protected static StraffSOMMapManager mapMgr;
	protected Straff_SOMRawDataLdrCnvrtr rawDataLdr;
	//object to manage console/log IO
	protected MessageObject msgObj;
	//key in destination map of data arrays where data should be loaded
	protected String destAraDataKey;
	//used to decipher json - need one per instance/thread
	public ObjectMapper jsonMapper;
	//source file name and path or sql-related connection info (file name of csv holding sql connection info?)
	protected String fileNameAndPath;
	//whether is file loader (from csv files) or sql reader
	protected boolean isFileLoader;
	//flag index in owning map flag structure denoting the processing is finished
	protected int isDoneMapDataIDX;
	//# of columns used by the particular table being read - used to verify if all fields are present in table.  if not known, set to 2
	protected boolean hasJson;
	//set whether we want to debug this loader or not
	protected boolean debug;
	//thread index of this callable
	protected int thdIDX;
	
	public Straff_RawDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {
		jsonMapper = new ObjectMapper();
		fileNameAndPath = _dataLocInfoStr;
		isFileLoader = _isFileLoader;
	}//ctor
	
	//use this to set values for each individual load.
	//_dataInfoAra : idx0 : idx in SOMMapManager.rawDataArrays where the loaded data should go
	//				 idx1 : file path and name
	//_flagsAra    : idx0 : if this is file/sql loader; idx1 : if the source data has json columns; idx2 : debug
	//_isDOneIDX : boolean flag idx in SOMMapManager to mark that this data loader has finished

	public void setLoadData(Straff_SOMRawDataLdrCnvrtr _rawDataLdr, MessageObject _msgObj, String _destAraDataKey, String _fileNameAndPath, boolean[] _flagsAra, int _isDoneIDX, int _thdIDX) {
		rawDataLdr = _rawDataLdr;
		//each loader has its own message object
		msgObj = _msgObj;
		//BaseRawData.mapMgr = _mapMgr;
		destAraDataKey = _destAraDataKey;		//must be -directory- where data is found
		fileNameAndPath = _fileNameAndPath;		//fully qualififed file name
		isFileLoader = _flagsAra[0];
		hasJson = _flagsAra[1];
		debug = _flagsAra[2];
		isDoneMapDataIDX = _isDoneIDX;
		thdIDX = _thdIDX;
	}//setLoadData
	
	public ArrayList<Straff_BaseRawData> execLoad(){	
		ArrayList<Straff_BaseRawData> dataObjs = new ArrayList<Straff_BaseRawData>();
		if (isFileLoader) {
			msgObj.dispMessage("Straff_RawDataLoader","execLoad","File Load Started for "+fileNameAndPath, MsgCodes.info5);
			streamCSVDataAndBuildStructs(dataObjs, fileNameAndPath);
			msgObj.dispMessage("Straff_RawDataLoader","execLoad","File Load Finished for "+fileNameAndPath, MsgCodes.info5);
		} else {//exec sql load for this file
			//TODO sql read/load here
			msgObj.dispMessage("Straff_RawDataLoader","execLoad","Sql Load NOT IMPLEMENTED using connection info at "+fileNameAndPath, MsgCodes.error1);
		}
		return dataObjs;
	}//execLoad
	
	//parse string into data object, which will then construct training/testing data for som map
	protected abstract Straff_BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON);	
	private void streamCSVDataAndBuildStructs(ArrayList<Straff_BaseRawData> dAra, String fileNameAndPath) {try {_strmCSVFileBuildObjs(dAra,fileNameAndPath);} catch (Exception e) {e.printStackTrace();}}
	//stream read the csv file and build the data objects
	private void _strmCSVFileBuildObjs(ArrayList<Straff_BaseRawData> dAra, String fileNameAndPath) throws IOException {
		msgObj.dispMessage("Straff_RawDataLoader","streamCSVDataAndBuildStructs","Start loading "+ fileNameAndPath, MsgCodes.info5);
		FileInputStream inputStream = null;
		Scanner sc = null;
	    int line = 1, badEntries = 0;
		try {
			//System.out.println("Working Directory = " +System.getProperty("user.dir"));
		    inputStream = new FileInputStream(fileNameAndPath);
		    sc = new Scanner(inputStream);
		    if(!sc.hasNext()) {
		    	msgObj.dispMessage("Straff_RawDataLoader","streamCSVDataAndBuildStructs","No data found in "+ fileNameAndPath + " .  Aborting.", MsgCodes.warning1);
			    if (inputStream != null) {inputStream.close();		    }
			    if (sc != null) { sc.close();		    }
		    	return;
		    }
		    //get rid of headers
		    sc.nextLine();
		    boolean done = false;
		    while ((sc.hasNextLine()) && !(done)) {
		    	String datStr = sc.nextLine();
		    	//System.out.println("line : " +line + " | datStr : "+datStr);
		    	if (hasJson) {//jp and other relevant data is stored in payload/descirptor field holding json			    	
			    	String [] strAras1 = datStr.split("\"\\{");		//split into string holding columns and string holding json 
			    	if (strAras1.length < 2) {		    		
				    		if(debug) {msgObj.dispMessage("Straff_RawDataLoader","streamCSVDataAndBuildStructs","!!!!!!!!!" +destAraDataKey+ " has bad entry at "+line+" lacking required description/payload json : " + datStr, MsgCodes.warning1);}
				    		++badEntries;			    	
			    	} else {
			    		String str = strAras1[1];
			    	    if (str.charAt(str.length() - 1) == '"') {str = str.substring(0, str.length() - 1);}//remove trailing quote
				        String jsonStr = "{"+str.replace("\t", "");
				        String[] vals = strAras1[0].replace("\"","").split(",");
				        //convert string to object to be used to train map		        
			        	addObjToDAra(dAra, vals, jsonStr, hasJson);
			    	}		    				    		
		    	} else {//currently only TC_Taggings, 2 columns, 
		    		//doesn't use json, so jsonStr can be empty
		    		//parse 2 entries and remove extraneous quotes - replace quote-comma-quote with apost-comma-apost, get rid of all quotes, then split on apost-comma-apost.  this retains jpg-jp list structure while splitting on columns properly
			        String[] vals = datStr.replace("\",\"","','").replace("\"","").split("','");
			        if(vals.length < 2) {
			        	if(debug) {msgObj.dispMessage("Straff_RawDataLoader","streamCSVDataAndBuildStructs","!!!!!!!!!" +destAraDataKey+ " has bad entry at "+line+" lacking required jp list : " + datStr, MsgCodes.warning1);}
			    		++badEntries;			    				        	
			        } else {
			        	addObjToDAra(dAra, vals, "", hasJson);
			        }
		    	}
		        ++line;		        
		        if (line % 100000 == 0) {msgObj.dispMessage("Straff_RawDataLoader","streamCSVDataAndBuildStructs","" +destAraDataKey+ " Finished line : "+line, MsgCodes.info3); 	}
		    }
		    //Scanner suppresses exceptions
		    if (sc.ioException() != null) { throw sc.ioException(); }
		} catch (Exception e) {	e.printStackTrace();} 
		finally {
		    if (inputStream != null) {inputStream.close();		    }
		    if (sc != null) { sc.close();		    }
		}
		msgObj.dispMessage("Straff_RawDataLoader","streamCSVDataAndBuildStructs","Finished loading "+ fileNameAndPath + " With "+badEntries+" bad/missing json entries and " + dAra.size() + " good records.", MsgCodes.info5);
	}//loadFileContents

	private void addObjToDAra(ArrayList<Straff_BaseRawData> dAra, String[] vals, String jsonStr, boolean hasJson) {
        Straff_BaseRawData obj = parseStringToObj(vals, jsonStr, hasJson);
        if (obj.isBadRec) {
        	if(debug) {msgObj.dispMessage("Straff_RawDataLoader","addObjToDAra","" +destAraDataKey+ " : " + obj.toString() + " is a useless record due to " + obj.getWhyBadRed() + ".  Record is Ignored.", MsgCodes.info3);}
        } else {
        	dAra.add(obj);
        }		
	}//addObjToDAra
	
	@Override
	public Boolean call() throws Exception {	
		msgObj.dispMessage("Straff_RawDataLoader","call() : ","Start loading "+ fileNameAndPath, MsgCodes.info5);
		//execute load, take load results and add to mapData.rawDataArrays
		ArrayList<Straff_BaseRawData> dataObjs = execLoad();
		//synched because some data types might have multiple source files that attempt to write to array at same time
		//get existing record list, if present, and aggregate - need to block on this!
		synchronized(destAraDataKey){
			ArrayList<Straff_BaseRawData> existAra = rawDataLdr.rawDataArrays.get(destAraDataKey);
			//put in destAraDataKey of  mapData.rawDataArrays
			if(existAra != null) {			dataObjs.addAll(existAra);		}	
			rawDataLdr.rawDataArrays.put(destAraDataKey, dataObjs);
			//set boolean in mapData to mark that this is finished
			rawDataLdr.setRawLoadDataTypeIsDone(isDoneMapDataIDX);
			//mapMgr.setPrivFlag(isDoneMapDataIDX, true);
		}//synch
		return true;
	}//call launches this loader - when finished will return true
		
}//Straff_RawDataLoader base class
