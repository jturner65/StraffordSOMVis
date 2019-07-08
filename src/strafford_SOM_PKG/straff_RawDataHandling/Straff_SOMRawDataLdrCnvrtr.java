package strafford_SOM_PKG.straff_RawDataHandling;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_utils.SOM_ProjConfigData;
import base_Utils_Objects.*;
import base_Utils_Objects.io.FileIOManager;
import base_Utils_Objects.io.MessageObject;
import base_Utils_Objects.io.MsgCodes;
import strafford_SOM_PKG.straff_RawDataHandling.data_loaders.Straff_RawDataLoader;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.*;
import strafford_SOM_PKG.straff_SOM_Examples.Straff_SOMExample;
import strafford_SOM_PKG.straff_SOM_Examples.products.Straff_ProductExample;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.Straff_CustProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
import strafford_SOM_PKG.straff_SOM_Mapping.exampleManagers.*;
import strafford_SOM_PKG.straff_SOM_Mapping.exampleManagers.base.Straff_SOMProspectManager;

/**
 * this class manages the loading of the raw data from either csv or from sql queries (TODO: SQL not implemented yet)
 * @author john
 *
 */
public class Straff_SOMRawDataLdrCnvrtr {
	//owning som map mgr
	public static Straff_SOMMapManager mapMgr;
	//message object for logging/displaying results to screen
	private static MessageObject msgObj;
	//struct maintaining complete project configuration and information from config files - all file name data and building needs to be done by this object
	public SOM_ProjConfigData projConfigData;			
	//manage IO in this object - only file writing; custom loader classes manage reading from csv or sql(if implemented)
	protected FileIOManager fileIO; 

	//////////////////
	//source data constructs
	//all of these constructs must follow the same order - 1st value must be prospect, 2nd must be order events, etc.
	//idxs for each type of data in arrays holding relevant data info
	public static final int prspctIDX = 0, orderEvntIDX = 1, optEvntIDX = 2, linkEvntIDX = 3, srcEvntIDX = 4, tcTagsIDX = 5, jpDataIDX = 6, jpgDataIDX = 7;
	//directory names where raw data files can be found - also use as key in rawDataArrays to access arrays of raw objects
	private static final String[] straffDataDirNames = new String[] {"prospect_objects", "order_event_objects", "opt_event_objects", "link_event_objects", "source_event_objects", "tc_taggings", "jp_data", "jpg_data"};
	//idxs of string keys in rawDataArrays corresponding to EVENTS (from xxx_event_objects tables)
	private static final int[] straffEventDataIDXs = new int[] {orderEvntIDX, optEvntIDX, linkEvntIDX, srcEvntIDX};
	//list of idxs related to each table for data
	public static final int[] straffObjFlagIDXs = new int[] {
			Straff_SOMMapManager.prospectDataLoadedIDX, Straff_SOMMapManager.orderDataLoadedIDX, Straff_SOMMapManager.optDataLoadedIDX, 
			Straff_SOMMapManager.linkDataLoadedIDX, Straff_SOMMapManager.sourceDataLoadedIDX, Straff_SOMMapManager.tcTagsDataLoadedIDX, 
			Straff_SOMMapManager.jpDataLoadedIDX, Straff_SOMMapManager.jpgDataLoadedIDX};
	
	//file names for specific file dirs/types, keyed by dir - looks up in directory and gets all csv files to use as sources
	private TreeMap<String, String[]> straffRawDataFileNames;
	//whether each table uses json as final field to hold important info or not
	private static final boolean[] straffRawDatUsesJSON = new boolean[] {true, true, true, true, true, false, true, true};
	//whether we want to debug the loading of a particular type of raw data
	private static final boolean[] straffRawDatDebugLoad = new boolean[] {false, false, false, false,false, true, true, true};
	
	//string to hold package trail to where data loaders reside - REMEMBER TO INCLUDE TRAILING PERIOD!!!
	private static final String _baseStraffDataLdrsLoc = "strafford_SOM_PKG.straff_RawDataHandling.data_loaders.";
	//list of class names used to build array of object loaders
	private static final String[] straffClassLdrNames = new String[] {
			"ProspectDataLoader","OrderEventDataLoader","OptEventDataLoader","LinkEventDataLoader","SourceEventDataLoader","TcTagDataLoader", "JpDataLoader", "JpgrpDataLoader"
		};	
	
	//classes of data loader objects
	public Class<Straff_RawDataLoader>[] straffObjLoaders;
	//destination object to manage arrays of each type of raw data from db
	public ConcurrentSkipListMap<String, ArrayList<Straff_BaseRawData>> rawDataArrays;
	//for multi-threaded calls to base loader
	public List<Future<Boolean>> straffDataLdrFtrs;
	public TreeMap<String, Straff_RawDataLoader>straffDataLoaders;
	//executor
	private ExecutorService th_exec;
	public Straff_SOMRawDataLdrCnvrtr(Straff_SOMMapManager _mapMgr, SOM_ProjConfigData _projConfig) {
		mapMgr=_mapMgr;
		msgObj = mapMgr.buildMsgObj();
		fileIO = new FileIOManager(MessageObject.buildMe(),"StraffSOMRawDataLdrCnvrtr");
		projConfigData = _projConfig;
		th_exec = mapMgr.getTh_Exec();
		//load all raw data file names based on exploring directory structure for all csv files
		straffRawDataFileNames = projConfigData.buildRawFileNameMap(straffDataDirNames);			
		try {
			straffObjLoaders = new Class[straffClassLdrNames.length];//{Class.forName("SOM_Strafford_PKG.ProspectDataLoader"),  Class.forName("SOM_Strafford_PKG.OrderEventDataLoader"),Class.forName("SOM_Strafford_PKG.OptEventDataLoader"),Class.forName("SOM_Strafford_PKG.LinkEventDataLoader")};
			for (int i=0;i<straffClassLdrNames.length;++i) {straffObjLoaders[i]=(Class<Straff_RawDataLoader>) Class.forName(_baseStraffDataLdrsLoc + straffClassLdrNames[i]);			}
		} catch (Exception e) {msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","Constructor","Failed to instance straffObjLoader classes : " + e, MsgCodes.error1);	}
		//to launch loader callable instances
		buildStraffDataLoaders();		
	}//ctor	
	
	//build the callable strafford data loaders list
	private void buildStraffDataLoaders() {
		//to launch loader callable instances
		straffDataLdrFtrs = new ArrayList<Future<Boolean>>();
		//straffDataLoaders = new ArrayList<StraffordDataLoader>();
		straffDataLoaders = new TreeMap<String, Straff_RawDataLoader>();
		//raw data from csv's/db
		rawDataArrays = new ConcurrentSkipListMap<String, ArrayList<Straff_BaseRawData>>();
		//build constructors
		@SuppressWarnings("rawtypes")
		Class[] args = new Class[] {boolean.class, String.class};//classes of arguments for loader ctor	
		//numStraffDataTypes
		try {
			for (int idx=0;idx<straffDataDirNames.length;++idx) {
				String[] fileNameAra = straffRawDataFileNames.get(straffDataDirNames[idx]);
				for (int fidx = 0;fidx < fileNameAra.length;++fidx) {
					String dataLoaderKey = fileNameAra[fidx];				
					straffDataLoaders.put(dataLoaderKey,(Straff_RawDataLoader) straffObjLoaders[idx].getDeclaredConstructor(args).newInstance(true, dataLoaderKey));
				}
			}
		} catch (Exception e) {	
			msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","buildStraffDataLoaders","A Null pointer exception here may indicate package name refactoring - class names in straffClassLdrNames must be fully and accurately package-qualified!!", MsgCodes.warning2);
			e.printStackTrace();
		}	
	}//buildStraffDataLoaders

	//fromCSVFiles : whether loading data from csv files or from SQL calls
	//eventsOnly : only use examples with event data to train
	//append : whether to append to existing data values or to load new data
	public ConcurrentSkipListMap<String, ArrayList<Straff_BaseRawData>> loadAllRawData(boolean fromCSVFiles) {
		msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","loadAllRawData","Start loading and processing raw data", MsgCodes.info5);
		//TODO remove this when SQL support is implemented
		if(!fromCSVFiles) {
			msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","loadAllRawData","WARNING : SQL-based raw data queries not yet implemented.  Use CSV-based raw data to build training data set instead", MsgCodes.warning2);
			return null;
		}
		boolean canMultiThread=mapMgr.isMTCapable();//this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		if(canMultiThread) {
			buildStraffDataLoaders();
			for (int idx=0;idx<straffDataDirNames.length;++idx) {//build a thread per data type //straffRawDatDebugLoad
				boolean[] flags = new boolean[] {fromCSVFiles,straffRawDatUsesJSON[idx], straffRawDatDebugLoad[idx]};
				String[] fileNameAra = straffRawDataFileNames.get(straffDataDirNames[idx]);
				for (int fidx =0;fidx <fileNameAra.length;++fidx) {
					String fullFileName = projConfigData.getRawDataLoadInfo(fromCSVFiles,straffDataDirNames[idx],fileNameAra[fidx]);
					straffDataLoaders.get(fileNameAra[fidx]).setLoadData(this, mapMgr.buildMsgObj(), straffDataDirNames[idx],  fullFileName, flags, straffObjFlagIDXs[idx], fidx);
				}
			}
			//blocking on callables for multithreaded
			try {straffDataLdrFtrs = th_exec.invokeAll(straffDataLoaders.values());for(Future<Boolean> f: straffDataLdrFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		
		} else {
			for (int idx=0;idx<straffDataDirNames.length;++idx) {
				boolean[] flags = new boolean[] {fromCSVFiles,straffRawDatUsesJSON[idx], straffRawDatDebugLoad[idx]};
				String[] fileNameAra = straffRawDataFileNames.get(straffDataDirNames[idx]);
				for (int fidx =0;fidx <fileNameAra.length;++fidx) {
					String fullFileName = projConfigData.getRawDataLoadInfo(fromCSVFiles,straffDataDirNames[idx],fileNameAra[fidx]);
					loadRawDataVals(straffDataDirNames[idx], fullFileName, flags,idx, fidx);
				}
			}
		}
		msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","loadAllRawData","Finished loading raw data.", MsgCodes.info5);
		return rawDataArrays;
	}//loadAllRawData
	
	//will instantiate specific loader class object and load the data specified by idx, either from csv file or from an sql call described by csvFile
	private void loadRawDataVals(String dataDirTypeName, String fullFileName, boolean[] flags, int idx, int fidx){//boolean _isFileLoader, String _fileNameAndPath
		//single threaded implementation
		@SuppressWarnings("rawtypes")
		Class[] args = new Class[] {boolean.class, String.class};//classes of arguments for loader ctor		
		try {
			@SuppressWarnings("unchecked")
			Straff_RawDataLoader loaderObj = (Straff_RawDataLoader) straffObjLoaders[idx].getDeclaredConstructor(args).newInstance(flags[0], fullFileName);
			loaderObj.setLoadData(this, mapMgr.buildMsgObj(), dataDirTypeName, fullFileName, flags, straffObjFlagIDXs[idx], fidx);
			ArrayList<Straff_BaseRawData> datAra = loaderObj.execLoad();
			if(datAra.size() > 0) {
				ArrayList<Straff_BaseRawData> existAra = rawDataArrays.get(dataDirTypeName);
				if(existAra != null) {			datAra.addAll(existAra);			} //merge with existing array
				rawDataArrays.put(dataDirTypeName, datAra);
				mapMgr.setFlag(straffObjFlagIDXs[idx], true);			//set flag corresponding to this type of data to be loaded
			}
		} catch (Exception e) {						e.printStackTrace();				}		
		
		//setFlag(straffObjFlagIDXs[idx], true);			//set flag corresponding to this type of data to be loaded
	}//loadRawDataVals		
	//set data type is done loading
	public void setRawLoadDataTypeIsDone(int isDoneMapDataIDX) {mapMgr.setFlag(isDoneMapDataIDX, true);}	
	
	//////////////////////////////
	// process raw data	
	
	//process all events into training examples
	private void procRawEventData(Straff_SOMProspectManager mapper, ConcurrentSkipListMap<String, ArrayList<Straff_BaseRawData>> dataArrays, boolean saveBadRecs) {			
		msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","procRawEventData","Start processing raw event data.", MsgCodes.info5);
		String dataName;
		int dataTypeIDX;
		//only iterate through event records in dataArraysMap
		for (int i = 0; i <straffEventDataIDXs.length;++i) {//for each event
			dataTypeIDX = straffEventDataIDXs[i];
			dataName = straffDataDirNames[dataTypeIDX];
			ArrayList<Straff_BaseRawData> events = dataArrays.get(dataName);
			//derive event type from file name?
			String eventType = dataName.split("_")[0];		
			String eventBadFName = projConfigData.getBadEventFName(dataName);//	
			ArrayList<String> badEventOIDs = new ArrayList<String>();
			HashSet<String> uniqueBadEventOIDs = new HashSet<String>();
			
			for (Straff_BaseRawData obj : events) {
				Straff_CustProspectExample ex = ((Straff_CustProspectExample)(mapper.getExample(obj.OID)));			//event has OID referencing prospect/customer record in prospect table
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
				msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","procRawEventData","# of "+eventType+" events without corresponding prospect records : "+badEventOIDs.size() + " out of " +events.size() + " total "+eventType+" events | # Unique bad "+eventType+" event prospect OID refs (missing OIDs in prospect) : "+uniqueBadEventOIDs.size(), MsgCodes.info3);
			} else {
				msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","procRawEventData","No "+eventType+" events without corresponding prospect records found after processing "+ events.size() +" events.", MsgCodes.info3);				
			}
		}//for each event type
		//all events processed for all prospects
		mapper.setAllDataLoaded();
		
		msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","procRawEventData","Finished processing raw event data.", MsgCodes.info5);
	}//procRawEventData
	
	//convert raw tc taggings table data to product examples
	private void procRawProductData(Straff_SOMProductManager prodMapper, ArrayList<Straff_BaseRawData> tcTagRawData) {
		msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","procRawProductData","Starting to process Raw Product Data.", MsgCodes.info5);
		for (Straff_BaseRawData tcDat : tcTagRawData) {
			Straff_ProductExample ex = new Straff_ProductExample(mapMgr, (Straff_TcTagData)tcDat);
			prodMapper.addExampleToMap(ex.OID, ex);
		}
		//all product data is loaded
		prodMapper.setAllDataLoaded();
		msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","procRawProductData","Finished processing  : " + tcTagRawData.size()+ " raw records.", MsgCodes.info5);		
	}//procRawProductData
	
	//this will go through all the prospects and events and build a map of prospectExample keyed by prospect OID and holding all the known data
	public void procRawLoadedData(Straff_SOMProspectManager prspctMapper, Straff_SOMProductManager prodMapper) {
		msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","procRawLoadedData","Start Processing all loaded raw data", MsgCodes.info5);
		//load all prospects from source
		ArrayList<Straff_BaseRawData> prospects = rawDataArrays.get(straffDataDirNames[prspctIDX]);
		for (Straff_BaseRawData prs : prospects) {
			//prospectMap is empty here
			SOM_Example ex = new Straff_CustProspectExample(mapMgr, (Straff_ProspectData) prs);
			prspctMapper.addExampleToMap(ex.OID, ex);
		}		
		//add all events to prospects
		procRawEventData(prspctMapper, rawDataArrays, true);		
		//now handle products - found in tc_taggings table
		procRawProductData(prodMapper, rawDataArrays.get(straffDataDirNames[tcTagsIDX]));		
		//now handle loaded jp and jpgroup data
		mapMgr.jpJpgrpMon.setJpJpgrpNames(rawDataArrays.get(straffDataDirNames[jpDataIDX]),rawDataArrays.get(straffDataDirNames[jpgDataIDX]));		
		//to free up memory before we build feature weight vectors; get rid of rawDataArrays used to hold original data read from files		
		rawDataArrays.clear();				
		msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","procRawLoadedData","Finished processing all loaded data", MsgCodes.info5);
	}//procRawLoadedData	
	
	
	
	//show first numToShow elemens of array of BaseRawData, either just to console or to applet window
	private void dispRawDataAra(ArrayList<Straff_BaseRawData> sAra, int numToShow) {
		if (sAra.size() < numToShow) {numToShow = sAra.size();}
		for(int i=0;i<numToShow; ++i){msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","dispRawDataAra",sAra.get(i).toString(), MsgCodes.info4);}
	}	
	
	public void dbgShowAllRawData() {
		int numToShow = 10;
		for (String key : rawDataArrays.keySet()) {
			msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","dbgShowAllRawData","Showing first "+ numToShow + " records of data at key " + key, MsgCodes.info4);
			dispRawDataAra(rawDataArrays.get(key), numToShow);
		}
	}//showAllRawData
	
}//class StraffSOMRawDataLdrCnvrtr
