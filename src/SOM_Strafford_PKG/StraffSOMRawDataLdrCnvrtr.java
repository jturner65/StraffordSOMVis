package SOM_Strafford_PKG;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import Utils.MsgCodes;

/**
 * this class manages the loading of the raw data from either csv or from sql queries (TODO:)
 * @author john
 *
 */
public class StraffSOMRawDataLdrCnvrtr {
	//owning som map mgr
	public static StraffSOMMapManager mapMgr;
	//struct maintaining complete project configuration and information from config files - all file name data and building needs to be done by this object
	public SOMProjConfigData projConfigData;			
	
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
	//list of class names used to build array of object loaders
	private static final String[] straffClassLdrNames = new String[] {
			"SOM_Strafford_PKG.ProspectDataLoader","SOM_Strafford_PKG.OrderEventDataLoader","SOM_Strafford_PKG.OptEventDataLoader","SOM_Strafford_PKG.LinkEventDataLoader",
			"SOM_Strafford_PKG.SourceEventDataLoader","SOM_Strafford_PKG.TcTagDataLoader", "SOM_Strafford_PKG.JpDataLoader", "SOM_Strafford_PKG.JpgrpDataLoader"
		};	
	
	//classes of data loader objects
	public Class<StraffordDataLoader>[] straffObjLoaders;
	//destination object to manage arrays of each type of raw data from db
	public ConcurrentSkipListMap<String, ArrayList<BaseRawData>> rawDataArrays;
	//for multi-threaded calls to base loader
	public List<Future<Boolean>> straffDataLdrFtrs;
	public TreeMap<String, StraffordDataLoader>straffDataLoaders;
	//executor
	private ExecutorService th_exec;
	public StraffSOMRawDataLdrCnvrtr(StraffSOMMapManager _mapMgr, SOMProjConfigData _projConfig) {
		mapMgr=_mapMgr;
		projConfigData = _projConfig;
		//load all raw data file names based on exploring directory structure for all csv files
		straffDataFileNames = projConfigData.buildFileNameMap(straffDataDirNames);		
		th_exec = mapMgr.getTh_Exec();
		try {
			straffObjLoaders = new Class[straffClassLdrNames.length];//{Class.forName("SOM_Strafford_PKG.ProspectDataLoader"),  Class.forName("SOM_Strafford_PKG.OrderEventDataLoader"),Class.forName("SOM_Strafford_PKG.OptEventDataLoader"),Class.forName("SOM_Strafford_PKG.LinkEventDataLoader")};
			for (int i=0;i<straffClassLdrNames.length;++i) {straffObjLoaders[i]=(Class<StraffordDataLoader>) Class.forName(straffClassLdrNames[i]);			}
		} catch (Exception e) {mapMgr.msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","Constructor","Failed to instance straffObjLoader classes : " + e, MsgCodes.error1);	}
		//to launch loader callable instances
		buildStraffDataLoaders();		

	}//ctor
	
	
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

	
	//fromCSVFiles : whether loading data from csv files or from SQL calls
	//eventsOnly : only use examples with event data to train
	//append : whether to append to existing data values or to load new data
	public void loadAllRawData(boolean fromCSVFiles) {
		mapMgr.msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","loadAllRawData","Start loading and processing raw data", MsgCodes.info5);
		//TODO remove this when SQL support is implemented
		if(!fromCSVFiles) {
			mapMgr.msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","loadAllRawData","WARNING : SQL-based raw data queries not yet implemented.  Use CSV-based raw data to build training data set instead", MsgCodes.warning2);
			return;
		}
		boolean canMultiThread=mapMgr.isMTCapable();//this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		if(canMultiThread) {
			buildStraffDataLoaders();
			for (int idx=0;idx<straffDataDirNames.length;++idx) {//build a thread per data type //straffRawDatDebugLoad
				boolean[] flags = new boolean[] {fromCSVFiles,straffRawDatUsesJSON[idx], straffRawDatDebugLoad[idx]};
				String[] fileNameAra = straffDataFileNames.get(straffDataDirNames[idx]);
				for (int fidx =0;fidx <fileNameAra.length;++fidx) {
					String fullFileName = projConfigData.getRawDataLoadInfo(fromCSVFiles,straffDataDirNames[idx],fileNameAra[fidx]);
					straffDataLoaders.get(fileNameAra[fidx]).setLoadData(mapMgr, straffDataDirNames[idx],  fullFileName, flags, mapMgr.straffObjFlagIDXs[idx], fidx);
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
					loadRawDataVals(straffDataDirNames[idx], fullFileName, flags,idx, fidx);
				}
			}
		}
		mapMgr.msgObj.dispMessage("StraffSOMRawDataLdrCnvrtr","loadAllRawData","Finished loading raw data.", MsgCodes.info5);
	}//loadAllRawData
	
	//will instantiate specific loader class object and load the data specified by idx, either from csv file or from an sql call described by csvFile
	private void loadRawDataVals(String dataDirTypeName, String fullFileName, boolean[] flags, int idx, int fidx){//boolean _isFileLoader, String _fileNameAndPath
		//single threaded implementation
		@SuppressWarnings("rawtypes")
		Class[] args = new Class[] {boolean.class, String.class};//classes of arguments for loader ctor		
		try {
			@SuppressWarnings("unchecked")
			StraffordDataLoader loaderObj = (StraffordDataLoader) straffObjLoaders[idx].getDeclaredConstructor(args).newInstance(flags[0], fullFileName);
			loaderObj.setLoadData(mapMgr, dataDirTypeName, fullFileName, flags, mapMgr.straffObjFlagIDXs[idx], fidx);
			ArrayList<BaseRawData> datAra = loaderObj.execLoad();
			if(datAra.size() > 0) {
				ArrayList<BaseRawData> existAra = rawDataArrays.get(dataDirTypeName);
				if(existAra != null) {			datAra.addAll(existAra);			} //merge with existing array
				rawDataArrays.put(dataDirTypeName, datAra);
				mapMgr.setPrivFlag(mapMgr.straffObjFlagIDXs[idx], true);			//set flag corresponding to this type of data to be loaded
			}
		} catch (Exception e) {						e.printStackTrace();				}		
		
		//setFlag(straffObjFlagIDXs[idx], true);			//set flag corresponding to this type of data to be loaded
	}//loadRawDataVals	
	

}//class StraffSOMRawDataLdrCnvrtr
