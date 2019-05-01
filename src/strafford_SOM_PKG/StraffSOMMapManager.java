package strafford_SOM_PKG;

import java.util.*;
import java.util.concurrent.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_fileIO.*;
import base_UI_Objects.*;
import base_Utils_Objects.*;
import strafford_SOM_PKG.straff_RawDataHandling.BaseRawData;
import strafford_SOM_PKG.straff_RawDataHandling.StraffSOMRawDataLdrCnvrtr;
import strafford_SOM_PKG.straff_SOM_Examples.*;


//this class holds the data describing a SOM and the data used to both build and query the som
public class StraffSOMMapManager extends SOMMapManager {	
	//structure to map specified products to the SOM and find prospects with varying levels of confidence
	private StraffProdMapOutputBuilder prodMapper;	
	//manage all jps and jpgs seen in project
	public MonitorJpJpgrp jpJpgrpMon;	
	//calc object to be used to derive feature vector for each prospect
	public StraffWeightCalc ftrCalcObj;
	//object to manage all raw data processing
	private StraffSOMRawDataLdrCnvrtr rawDataLdr;
			
	////////////////////////////////////////////////////////////////////////////////////////////////
	//data descriptions
	//full input data, data set to be training data and testing data (all of these examples 
	//are potential -training- data, in that they have all features required of training data)
	//testing data will be existing -customers- that will be matched against map - having these 
	//is not going to be necessary for most cases since this is unsupervised
	//trueProspectData are prospect records failing to meet the customer/training criteria 
	//(i.e. they may lack any purchase events) and thus were not used to train the map
	public SOMExample[] trueProspectData;		
	public int numTrueProspectData;
	
	//map keyed by type of maps of prospectExamples built from database data, each keyed by prospect OID.  type :  
	//"customer" : customer prospectExamples with order events in their history
	//"prospect" : true prospects, with no order event history 
	private ConcurrentSkipListMap<String, ConcurrentSkipListMap<String, SOMExample>> prospectExamples;
	private String custExKey = "custProspect", prspctExKey = "trueProspect";
	
	//map of products build from TC_Taggings entries, keyed by tag ID (synthesized upon creation)
	private ConcurrentSkipListMap<String, ProductExample> productMap;	
	
	//data for products to be measured on map
	private ProductExample[] productData;
	//maps of product arrays, with key for each map being either jpg or jp
	private TreeMap<Integer, ArrayList<ProductExample>> productsByJpg, productsByJp;
	//total # of jps in all data, including source events
	private int numTtlJps;	
	
	//types of possible mappings to particular map node as bmu
	//corresponds to these values : ProspectTraining(0),ProspectTesting(1),Product(2)
	public static final String[] nodeBMUMapTypes = new String[] {"Training", "Testing", "Products"};
	
	public static final int 
			jps_FtrIDX = 0,		//idx in delta structs (diffs, mins) for jps used for training ftrs (non virtual jps)
			jps_AllIDX = 1;		//idx in delta structs for all jps
	public static final int numFtrTypes = 2;

	private int[] priv_stFlags;						//state flags - bits in array holding relevant process info
	public static final int
			debugIDX 					= 0,
			mapExclProdZeroFtrIDX  		= 1,			//if true, exclude u
			
			//raw data loading/processing state : 
			prospectDataLoadedIDX		= 2,			//raw prospect data has been loaded but not yet processed
			optDataLoadedIDX			= 3,			//raw opt data has been loaded but not processed
			orderDataLoadedIDX			= 4,			//raw order data loaded not proced
			linkDataLoadedIDX			= 5,			//raw link data loaded not proced
			sourceDataLoadedIDX			= 6,			//raw source event data loaded not proced
			tcTagsDataLoadedIDX			= 7,			//raw tc taggings data loaded not proced
			jpDataLoadedIDX				= 8,			//raw jp data loaded not proced
			jpgDataLoadedIDX			= 9,			//raw jpg data loaded not proced
			rawPrspctEvDataProcedIDX	= 10,			//all raw prospect/event data has been loaded and processed into StraffSOMExamples (prospect)
			rawProducDataProcedIDX		= 11,			//all raw product data (from tc_taggings) has been loaded and processed into StraffSOMExamples (product)
			//training data saved state : 
			testTrainProdDataBuiltIDX	= 12,			//product, input, testing and training data arrays have all been built
			custProspectDataLoadedIDX	= 13,			//customer prospect preprocced data loaded  
			trueProspectDataLoadedIDX	= 14,			//true prospect preprocced data loaded
			preProcProdDataLoadedIDX	= 15;			//product preprocced data loaded
		
	public static final int numFlags = 16;	
	
	//////////////////////////////
	//data in files created by SOM_MAP separated by spaces
	//size of intermediate per-OID record csv files : 
	public static final int preProcDatPartSz = 50000;	
	
	private StraffSOMMapManager(Straff_SOMMapUIWin _win, String[] _dirs,float[] _dims) {
		super(_win, _dirs,_dims);
		initPrivFlags();	
		
		prospectExamples = new ConcurrentSkipListMap<String, ConcurrentSkipListMap<String, SOMExample>>();		
		//raw data from csv's/db
		//rawDataArrays = new ConcurrentSkipListMap<String, ArrayList<BaseRawData>>();
		//instantiate maps of ProspectExamples - customers and true prospects (no order event history)
		prospectExamples.put(custExKey,  new ConcurrentSkipListMap<String, SOMExample>());		
		prospectExamples.put(prspctExKey,  new ConcurrentSkipListMap<String, SOMExample>());		
		productMap = new ConcurrentSkipListMap<String, ProductExample>();
		
		productsByJpg = new TreeMap<Integer, ArrayList<ProductExample>>();
		productsByJp = new TreeMap<Integer, ArrayList<ProductExample>>();
		//object to manage all jps and jpgroups seen in project
		jpJpgrpMon = new MonitorJpJpgrp(this);
		//all raw data loading moved to this object
		rawDataLdr = new StraffSOMRawDataLdrCnvrtr(this, projConfigData);
		
	}//ctor	
	//ctor from non-UI stub main
	public StraffSOMMapManager(String[] _dirs,float[] _dims) {this(null,_dirs, _dims);}

	//set max display list values
	public void setUI_JPFtrMaxVals(int jpGrpLen, int jpLen) {if (win != null) {win.setUI_JPFtrListMaxVals(jpGrpLen, jpLen);}}
	public void setUI_JPAllSeenMaxVals(int jpGrpLen, int jpLen) {if (win != null) {win.setUI_JPAllSeenListMaxVals(jpGrpLen, jpLen);}}
	protected int _getNumSecondaryMaps(){return jpJpgrpMon.getLenFtrJpGrpByIdx();}

	//clear out existing validation map, which holds true prospects (generally defined as prospects without any order event history)
	private void resetValidationMap() {
		//prospectExamples.put(prspctExKey,  new ConcurrentSkipListMap<String, SOMExample>());	
		//clear is more performant
		prospectExamples.get(prspctExKey).clear();	
		trueProspectData = new ProspectExample[0];
		numTrueProspectData=0;
		setPrivFlag(trueProspectDataLoadedIDX, false);
	}//resetValidationMap
	
	//clear out existing prospect map to be rebuilt
	private void resetCustProspectMap() {
		//prospectExamples.put(custExKey,  new ConcurrentSkipListMap<String, SOMExample>());	
		//clear is more performant
		prospectExamples.get(custExKey).clear();		
		//data used by actual SOM for testing/training
		resetTrainDataAras();
		setPrivFlag(rawPrspctEvDataProcedIDX, false);
		setPrivFlag(custProspectDataLoadedIDX, false);
		setAllTrainDatSaveFlags(false);
	}//resetProspectMap
	
	//clear out existing product map to be rebuilt
	public void resetProductMap() {
		productMap.clear();
		productsByJpg.clear();
		productsByJp.clear();
		productData = null;
		setPrivFlag(testTrainProdDataBuiltIDX, false);
		//initialize product-wide aggregations
		ProductExample.initAllStaticProdData();
		setPrivFlag(rawProducDataProcedIDX, false);
		setPrivFlag(preProcProdDataLoadedIDX, false);
		setAllTrainDatSaveFlags(false);
	}//resetProspectMap
	
	//add constructed product example to maps holding products keyed by their constituent jps and jpgs
	public void addProductToJPProductMaps(ProductExample ex) {
		//add to jp and jpg trees
		HashSet<Integer> jpgs = new HashSet<Integer>();
		//msgObj.dispMessage("StraffSOMMapManager","addProductToJPProductMaps","Starting to build productsByJp and productsByJpg maps : example's allProdJps size : "+ ex.allProdJPs.size()+"  | productsByJpg : " + productsByJpg.size()+ ".", MsgCodes.warning1);
		HashSet<Integer> exProdJps = ex.getAllProdJPs();
		
		for (Integer jp : exProdJps) {
			ArrayList<ProductExample> exList = productsByJp.get(jp);
			if(exList==null) {exList = new ArrayList<ProductExample>();}
			exList.add(ex);
			productsByJp.put(jp, exList);
			Integer jpg = jpJpgrpMon.getJpgFromJp(jp);
			//msgObj.dispMessage("StraffSOMMapManager","addProductToJPProductMaps","Getting JPG : " + jpg +" for jp : " + jp+".", MsgCodes.warning1);
			jpgs.add( jpg);	//record jp groups this product covers
		}
		//msgObj.dispMessage("StraffSOMMapManager","addProductToJPProductMaps","Size of jpgs : " + jpgs.size() + ".", MsgCodes.warning1);
		for (Integer jpg : jpgs) {
			//msgObj.dispMessage("StraffSOMMapManager","addProductToJPProductMaps","Get JPG : " + jpg +".", MsgCodes.warning1);
			ArrayList<ProductExample> exList = productsByJpg.get(jpg);
			if(exList==null) {exList = new ArrayList<ProductExample>();}
			exList.add(ex);
			productsByJpg.put(jpg, exList);	
		}
	}//addProductToProductMaps	
		
	//fromCSVFiles : whether loading data from csv files or from SQL calls
	//eventsOnly : only use examples with event data to train
	//append : whether to append to existing data values or to load new data
	public void loadAllRawData(boolean fromCSVFiles) {
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllRawData","Start loading and processing raw data", MsgCodes.info5);
		ConcurrentSkipListMap<String, ArrayList<BaseRawData>> _rawDataAras = rawDataLdr.loadAllRawData(fromCSVFiles);
		if(null==_rawDataAras) {		return;	}
		//process loaded data
		//dbgLoadedData(tcTagsIDX);
		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData","Start Processing all loaded raw data", MsgCodes.info5);
		if (!(getPrivFlag(prospectDataLoadedIDX) && getPrivFlag(optDataLoadedIDX) && getPrivFlag(orderDataLoadedIDX) && getPrivFlag(tcTagsDataLoadedIDX))){//not all data loaded, don't process 
			getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData","Can't build data examples until raw data is all loaded", MsgCodes.warning2);			
		} else {
			
			resetProductMap();			
			resetCustProspectMap();		
			resetValidationMap();		
			//build prospectMap - first get prospect data and add to map
			ConcurrentSkipListMap<String, SOMExample> 
					tmpProspectMap = new ConcurrentSkipListMap<String, SOMExample>(), 
					customerPrspctMap = prospectExamples.get(custExKey);
			//data loader will build prospect and product maps
			rawDataLdr.procRawLoadedData(tmpProspectMap, customerPrspctMap, productMap);
		
			//finalize around temp map - finalize builds each example's occurrence structures, which describe the jp-jpg relationships found in the example
			_finalizeProsProdJpJPGMonBeforeFtrCalc("procRawLoadedData", "temp", tmpProspectMap);
		
			//build actual customer and validation maps using rules defining what is a customer (probably means having an order event) and what is a "prospect" (probably not having an order event)
			_buildCustomerAndProspectMaps(tmpProspectMap);
			//clear temp map to free up memory
			tmpProspectMap = null;
			//finalize - recalc all processed data in case new products have different JP's present, set flags and save to file
			calcFtrsDiffsMinsAndSave();
			
			getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData","Finished processing all loaded data", MsgCodes.info5);
		}

		getMsgObj().dispMessage("StraffSOMMapManager","loadAllRawData","Finished loading raw data, processing and saving preprocessed data", MsgCodes.info5);
	}//loadAllRawData
	@Override
	//load _all_ preprocessed data - called when prebuilt map is loaded; should also build arrays of input and train/test data
	protected void loadAllPreprocData() {
		//load all preproc true prospects, which will load all other preproc data if hasn't been loaded yet
		loadAllTrueProspectData();
		
	}//loadAllPreprocData
	
	@Override
	//load all preprocessed data from default data location
	protected void loadPreProcTestTrainData() { loadPreProcMonCustProd(projConfigData.getRawDataDesiredDirName());}
	//pass subdir within data directory, or use default
	private void loadPreProcMonCustProd(String subDir) {	//preprocced data might be different than current true prospect data
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllPreProccedData","Begin loading preprocced data from " + subDir +  "directory.", MsgCodes.info5);
		//load monitor first;save it last
		getMsgObj().dispMessage("StraffSOMMapManager","loadMonitorJpJpgrp","Loading MonitorJpJpgrp data", MsgCodes.info1);
		String[] loadSrcFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(subDir, false, "MonitorJpJpgrpData");
		jpJpgrpMon.loadAllData(loadSrcFNamePrefixAra[0],".csv");
		getMsgObj().dispMessage("StraffSOMMapManager","loadMonitorJpJpgrp","Finished loading MonitorJpJpgrp data", MsgCodes.info1);
		getMsgObj().dispMessage("StraffSOMMapManager","loadMonitorJpJpgrp",jpJpgrpMon.toString(), MsgCodes.info1);
		//load customer prospect data
		//clear out current prospect data
		resetCustProspectMap();		
		//load customer data
		loadAllExampleMapData(subDir, custExKey, prospectExamples.get(custExKey));		
		//load product data
		loadAllProductMapData(subDir);
		finishSOMExampleBuild();
		setPrivFlag(rawPrspctEvDataProcedIDX, true);
		setPrivFlag(rawProducDataProcedIDX, true);
		//preprocced data might be different than current true prospect data, so clear flag and reset map (clear out memory)
		resetValidationMap();	
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllPreProccedData","Finished loading preprocced data from " + subDir +  "directory.", MsgCodes.info5);
	}//loadAllPreProccedData

	
	public void loadAllTrueProspectData() {loadAllTrueProspectData(projConfigData.getRawDataDesiredDirName());}
	//load validation (true prospects) data found in subDir
	private void loadAllTrueProspectData(String subDir) {
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllTrueProspectData","Begin loading preprocessed True Prospect data from " + subDir +  "directory.", MsgCodes.info5);
		//load validation data - prospect records with no order events
		if(!getPrivFlag(rawPrspctEvDataProcedIDX)) {	
			loadPreprocAndBuildTestTrainPartitions();
		}
		//clear out current validation data
		resetValidationMap();	
		//load into map
		loadAllExampleMapData(subDir, prspctExKey, prospectExamples.get(prspctExKey));	
		//now process - similar functionality to finishSOMExampleBuild but only process in relation to true prospects
		ConcurrentSkipListMap<String, SOMExample> trueProspects = prospectExamples.get(prspctExKey);
		
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllTrueProspectData"," Begin initial finalize of true prospects map", MsgCodes.info1);			
		Collection<SOMExample> truPspctExs = trueProspects.values();
		for (SOMExample ex : truPspctExs) {			ex.finalizeBuildBeforeFtrCalc();		}		
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllTrueProspectData","End initial finalize of true prospects map | Begin build feature vector for all true prospects.", MsgCodes.info1);	
		
		buildPrspctFtrVecs(truPspctExs, StraffWeightCalc.tpCalcObjIDX);
		
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllTrueProspectData","End build feature vector for all true prospects | Begin post feature vector build.", MsgCodes.info1);	
		for (SOMExample ex : truPspctExs) {			ex.buildPostFeatureVectorStructs();		}//this builds std ftr vector for prospects, once diffs and mins are set - not necessary for products, buildFeatureVector for products builds std ftr vec
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllTrueProspectData","End post feature vector build.", MsgCodes.info1);	
		
		//build array of trueProspectData used to map
		trueProspectData = prospectExamples.get(prspctExKey).values().toArray(new TrueProspectExample[0]);
		numTrueProspectData = trueProspectData.length;
		
		setPrivFlag(trueProspectDataLoadedIDX, true);
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllTrueProspectData","Finished loading preprocessed True Prospect data from " + subDir +  "directory.", MsgCodes.info5);
	}//loadAllProspectData	
	
		
	//load prospect mapped training data into StraffSOMExamples from disk
	//must reset prospect/validation maps before this is called
	private void loadAllExampleMapData(String subDir, String mapType, ConcurrentSkipListMap<String, SOMExample> mapToBuild) {
		//perform in multiple threads if possible
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllExampleMapData","Loading all " + mapType+ " map data that only have event-based training info from : " +subDir, MsgCodes.info5);//" + (eventsOnly ? "that only have event-based training info" : "that have any training info (including only prospect jpg/jp specification)"));
		String[] loadSrcFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(subDir, true, mapType+ "MapSrcData");
		String fmtFile = loadSrcFNamePrefixAra[0]+"_format.csv";
		
		String[] loadRes = fileIO.loadFileIntoStringAra(fmtFile, "Format file loaded", "Format File Failed to load");
		int numPartitions = 0;
		try {
			numPartitions = Integer.parseInt(loadRes[0].split(" : ")[1].trim());
		} catch (Exception e) {e.printStackTrace(); getMsgObj().dispMessage("StraffSOMMapManager","loadAllExampleMapData","Due to error with not finding format file : " + fmtFile+ " no data will be loaded.", MsgCodes.error1); return;} 
		
		boolean canMultiThread=isMTCapable();//this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		if(canMultiThread) {			
			List<Future<Boolean>> preProcLoadFtrs = new ArrayList<Future<Boolean>>();
			List<SOMExCSVDataLoader> preProcLoaders = new ArrayList<SOMExCSVDataLoader>();
			if(mapType == custExKey) {			
				for (int i=0; i<numPartitions;++i) {	preProcLoaders.add(new CustCSVDataLoader(this, i, loadSrcFNamePrefixAra[0]+"_"+i+".csv",  mapType+ " Data file " + i +" of " +numPartitions + " loaded",  mapType+ " Data File " + i +" of " +numPartitions +" Failed to load", mapToBuild));}
			} else {				
				for (int i=0; i<numPartitions;++i) {	preProcLoaders.add(new PrscpctCSVDataLoader(this, i, loadSrcFNamePrefixAra[0]+"_"+i+".csv",  mapType+ " Data file " + i +" of " +numPartitions +" loaded",  mapType+ " Data File " + i +" of " +numPartitions +" Failed to load", mapToBuild));}
			}
			try {preProcLoadFtrs = th_exec.invokeAll(preProcLoaders);for(Future<Boolean> f: preProcLoadFtrs) { 			f.get(); 		}} catch (Exception e) { e.printStackTrace(); }					
		} else {//load each file in its own csv
			if(mapType == custExKey) {	
				for (int i=numPartitions-1; i>=0;--i) {
					String dataFile = loadSrcFNamePrefixAra[0]+"_"+i+".csv";
					String[] csvLoadRes = fileIO.loadFileIntoStringAra(dataFile,  mapType+ " Data file " + i +" of " +numPartitions +" loaded",  mapType+ " Data File " + i +" of " +numPartitions +" Failed to load");
					//ignore first entry - header
					for (int j=1;j<csvLoadRes.length; ++j) {
						String str = csvLoadRes[j];
						int pos = str.indexOf(',');
						String oid = str.substring(0, pos);
						CustProspectExample ex = new CustProspectExample(this, oid, str);
						mapToBuild.put(oid, ex);			
					}
				}
			} else {
				for (int i=numPartitions-1; i>=0;--i) {
					String dataFile = loadSrcFNamePrefixAra[0]+"_"+i+".csv";
					String[] csvLoadRes = fileIO.loadFileIntoStringAra(dataFile, mapType+ " Data file " + i +" of " +numPartitions +" loaded",  mapType+ " Data File " + i +" of " +numPartitions +" Failed to load");
					//ignore first entry - header
					for (int j=1;j<csvLoadRes.length; ++j) {
						String str = csvLoadRes[j];
						int pos = str.indexOf(',');
						String oid = str.substring(0, pos);
						TrueProspectExample ex = new TrueProspectExample(this, oid, str);
						mapToBuild.put(oid, ex);			
					}
				}				
			}
		}	
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllExampleMapData","Finished loading and preprocessing all local prospect map data and calculating features.  Number of entries in " + mapType + " prospectMap : " + mapToBuild.size(), MsgCodes.info5);
	}//loadAllPropsectMapData	
	
	//load product pre-procced data from tc_taggings source
	private void loadAllProductMapData(String subDir) {
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllProductMapData","Loading all product map data", MsgCodes.info5);
		//clear out current product data
		resetProductMap();
		String[] loadSrcFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(subDir, false, "productMapSrcData");
		String dataFile =  loadSrcFNamePrefixAra[0]+".csv";
		String[] csvLoadRes = fileIO.loadFileIntoStringAra(dataFile, "Product Data file loaded", "Product Data File Failed to load");
		//ignore first entry - header
		for (int j=1;j<csvLoadRes.length; ++j) {
			String str = csvLoadRes[j];
			int pos = str.indexOf(',');
			String oid = str.substring(0, pos);
			ProductExample ex = new ProductExample(this, oid, str);
			productMap.put(oid, ex);			
		}
		setPrivFlag(preProcProdDataLoadedIDX, true);
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllProductMapData","Finished loading and preprocessing all local prospect map data and calculating features.  Number of entries in productMap : " + productMap.size(), MsgCodes.info5);
	}//loadAllProductMapData
	
	//this will load the product IDs to query on map for prospects from the location specified in the config
	//map these ids to loaded products and then 
	//prodZoneDistThresh is distance threshold to determine outermost map region to be mapped to a specific product
	public void saveProductToSOMMappings(double prodZoneDistThresh, boolean saveCusts, boolean saveProspects) {
		if (!getMapDataIsLoaded()) {	getMsgObj().dispMessage("StraffSOMMapManager","saveProductToSOMMappings","No Mapped data has been loaded or processed; aborting", MsgCodes.error2);		return;}
		if ((productMap == null) || (productMap.size() == 0)) {getMsgObj().dispMessage("StraffSOMMapManager","saveProductToSOMMappings","No products have been loaded or processed; aborting", MsgCodes.error2);		return;}
		
		getMsgObj().dispMessage("StraffSOMMapManager","saveProductToSOMMappings","Starting load of product to prospecting mapping configuration and building product output mapper", MsgCodes.info5);	
		//get file name of product mapper configuration file
		String prodMapFileName = projConfigData.getFullProdOutMapperInfoFileName();
		int prodDistType = getProdDistType();
		//builds the output mapper and loads the product IDs to map from config file
		prodMapper = new StraffProdMapOutputBuilder(this, prodMapFileName,th_exec, prodDistType, prodZoneDistThresh);
		getMsgObj().dispMessage("StraffSOMMapManager","saveProductToSOMMappings","Finished load of product to prospecting mapping configuration and building product output mapper | Begin Saving prod-to-prospect mappings to files", MsgCodes.info1);	
		
		//by here all prods to map have been specified. prodMapBuilder will determine whether multithreaded or single threaded; 
		prodMapper.saveAllSpecifiedProdMappings();		
		getMsgObj().dispMessage("StraffSOMMapManager","saveProductToSOMMappings","Finished Saving prod-to-prospect mappings to files.", MsgCodes.info1);	
		if (saveCusts) {
			getMsgObj().dispMessage("StraffSOMMapManager","saveProductToSOMMappings","Begin Saving customer-to-product mappings to files.", MsgCodes.info1);	
			//save customer-to-product mappings
			prodMapper.saveAllProspectToProdMappings(inputData, custExKey);
			getMsgObj().dispMessage("StraffSOMMapManager","saveProductToSOMMappings","Finished Saving " + inputData.length + " customer-to-product mappings to files.", MsgCodes.info1);	
		}
		if (saveProspects) {
			getMsgObj().dispMessage("StraffSOMMapManager","saveProductToSOMMappings","Begin Saving prospect-to-product mappings to files.", MsgCodes.info1);	
			prodMapper.saveAllProspectToProdMappings(trueProspectData, prspctExKey);
			getMsgObj().dispMessage("StraffSOMMapManager","saveProspectsToProductsMappings","Finished Saving " + trueProspectData.length + " true prospect-to-product mappings to files", MsgCodes.info5);				
		}		
	}//mapProductsToProspects
		
	//write all prospect map data to a csv to be able to be reloaded to build training data from, so we don't have to re-read database every time
	private boolean saveAllExampleMapData(String mapType, ConcurrentSkipListMap<String, SOMExample> exMap) {
		if ((null != exMap) && (exMap.size() > 0)) {
			getMsgObj().dispMessage("StraffSOMMapManager","saveAllExampleMapData","Saving all "+mapType+" map data : " + exMap.size() + " examples to save.", MsgCodes.info5);
			String[] saveDestFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(true, mapType+"MapSrcData");
			//ArrayList<ArrayList<String>> csvRes = new ArrayList<ArrayList<String>>();
			ArrayList<String> csvResTmp = new ArrayList<String>();		
			int counter = 0;
			SOMExample ex1 = exMap.get(exMap.firstKey());
			String hdrStr = ex1.getRawDescColNamesForCSV();
			csvResTmp.add( hdrStr);
			int nameCounter = 0, numFiles = (1+((int)((exMap.size()-1)/preProcDatPartSz)));
			getMsgObj().dispMessage("StraffSOMMapManager","saveAllExampleMapData","Start Building "+mapType+" String Array : " +nameCounter + " of "+numFiles+".", MsgCodes.info1);
			for (SOMExample ex : exMap.values()) {			
				csvResTmp.add(ex.getRawDescrForCSV());
				++counter;
				if(counter % preProcDatPartSz ==0) {
					String fileName = saveDestFNamePrefixAra[0]+"_"+nameCounter+".csv";
					getMsgObj().dispMessage("StraffSOMMapManager","saveAllExampleMapData","Done Building "+mapType+" String Array : " +nameCounter + " of "+numFiles+".  Saving to file : "+fileName, MsgCodes.info1);
					//csvRes.add(csvResTmp); 
					fileIO.saveStrings(fileName, csvResTmp);
					csvResTmp = new ArrayList<String>();
					csvResTmp.add( hdrStr);
					counter = 0;
					++nameCounter;
					getMsgObj().dispMessage("StraffSOMMapManager","saveAllExampleMapData","Start Building "+mapType+" String Array : " +nameCounter + " of "+numFiles+".", MsgCodes.info1);
				}
			}
			if(csvResTmp.size() > 1) {	
				String fileName = saveDestFNamePrefixAra[0]+"_"+nameCounter+".csv";
				getMsgObj().dispMessage("StraffSOMMapManager","saveAllExampleMapData","Done Building "+mapType+" String Array : " +nameCounter + " of "+numFiles+".  Saving to file : "+fileName, MsgCodes.info1);
				//csvRes.add(csvResTmp);
				fileIO.saveStrings(fileName, csvResTmp);
				csvResTmp = new ArrayList<String>();
				++nameCounter;
			}			
			getMsgObj().dispMessage("StraffSOMMapManager","saveAllExampleMapData","Finished partitioning " + exMap.size()+ " "+mapType+" records into " + nameCounter + " "+mapType+" record files, each holding up to " + preProcDatPartSz + " records and saving to files.", MsgCodes.info1);
			//save array of arrays of strings, partitioned and named so that no file is too large
//			nameCounter = 0;
//			for (ArrayList<String> csvResSubAra : csvRes) {		
//				msgObj.dispMessage("StraffSOMMapManager","saveAllExampleMapData","Saving Pre-procced "+mapType+" data String array : " +nameCounter, MsgCodes.info1);
//				fileIO.saveStrings(saveDestFNamePrefixAra[0]+"_"+nameCounter+".csv", csvResSubAra);
//				++nameCounter;
//			}
			//save the data in a format file
			String[] data = new String[] {"Number of file partitions for " + saveDestFNamePrefixAra[1] +" data : "+ nameCounter + "\n"};
			fileIO.saveStrings(saveDestFNamePrefixAra[0]+"_format.csv", data);		
			getMsgObj().dispMessage("StraffSOMMapManager","saveAllExampleMapData","Finished saving all "+mapType+" map data", MsgCodes.info5);
			return true;
		} else {getMsgObj().dispMessage("StraffSOMMapManager","saveAllExampleMapData","No "+mapType+" example data to save. Aborting", MsgCodes.error2); return false;}
	}//saveAllExampleMapData	

	private boolean saveAllProductMapData() {
		if ((null != productMap) && (productMap.size() > 0)) {
			getMsgObj().dispMessage("StraffSOMMapManager","saveAllProductMapData","Saving all product map data : " + productMap.size() + " examples to save.", MsgCodes.info5);
			String[] saveDestFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(false, "productMapSrcData");
			ArrayList<String> csvResTmp = new ArrayList<String>();		
			ProductExample ex1 = productMap.get(productMap.firstKey());
			String hdrStr = ex1.getRawDescColNamesForCSV();
			csvResTmp.add( hdrStr);	
			for (ProductExample ex : productMap.values()) {			
				csvResTmp.add(ex.getRawDescrForCSV());
			}
			fileIO.saveStrings(saveDestFNamePrefixAra[0]+".csv", csvResTmp);		
			getMsgObj().dispMessage("StraffSOMMapManager","saveAllProductMapData","Finished saving all product map data", MsgCodes.info5);
			return true;
		} else {getMsgObj().dispMessage("StraffSOMMapManager","saveAllProductMapData","No product example data to save. Aborting", MsgCodes.error2); return false;}
	}//saveAllProductMapData
	
	//save MonitorJpJpgrp
	private void saveMonitorJpJpgrp() {
		getMsgObj().dispMessage("StraffSOMMapManager","saveMonitorJpJpgrp","Saving MonitorJpJpgrp data", MsgCodes.info5);
		String[] saveDestFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(false, "MonitorJpJpgrpData");
		jpJpgrpMon.saveAllData(saveDestFNamePrefixAra[0],".csv");
		getMsgObj().dispMessage("StraffSOMMapManager","saveMonitorJpJpgrp","Finished saving MonitorJpJpgrp data", MsgCodes.info5);
	}//saveMonitorJpJpgrp
			
	//build the calculation object, recalculate the features and calc and save the mins, diffs, and all prospect, validation and product map data
	public void calcFtrsDiffsMinsAndSave() {
		getMsgObj().dispMessage("StraffSOMMapManager","rebuildCalcObj","Start loading calc object, calculating all feature vectors for prospects and products, calculating mins and diffs, and saving all results.", MsgCodes.info5);
		finishSOMExampleBuild();
		getMsgObj().dispMessage("StraffSOMMapManager","rebuildCalcObj","Finished loading calc object, calculating all feature vectors for prospects and products & calculating mins and diffs | Start saving all results.", MsgCodes.info1);
		setPrivFlag(rawPrspctEvDataProcedIDX, true);
		setPrivFlag(rawProducDataProcedIDX, true);
				
		boolean prspctSuccess = saveAllExampleMapData(custExKey, prospectExamples.get(custExKey));
		boolean validationSuccess = saveAllExampleMapData(prspctExKey, prospectExamples.get(prspctExKey));
		
		boolean prodSuccess = saveAllProductMapData();		
		
		if (prspctSuccess || validationSuccess || prodSuccess) { saveMonitorJpJpgrp();}
		getMsgObj().dispMessage("StraffSOMMapManager","rebuildCalcObj","Finished loading calc object, calculating all feature vectors for prospects and products, calculating mins and diffs, and saving all results.", MsgCodes.info5);
	}//calcFtrsDiffsMinsAndSave()
	
	//debug display of info regarding non-ftr jps and jpgroups
	private void _dispPrspctRes(int type) {
		TreeMap<Integer, TreeSet<Integer>> nonFtrJPgroups;
		String dispStr;
		int numBiggestJPs, numBiggestJPGroups;
		if (type==StraffWeightCalc.custCalcObjIDX ) {
			dispStr = "Customer";
			numBiggestJPs = StraffWeightCalc.biggestCustNonProd;
			numBiggestJPGroups = StraffWeightCalc.biggestCustJPGrpNonProd;
			nonFtrJPgroups = StraffWeightCalc.mostNonProdCustJpgrps;		
		} else {
			dispStr = "True Prospect";
			numBiggestJPs = StraffWeightCalc.biggestTPNonProd;
			numBiggestJPGroups = StraffWeightCalc.biggestTPJPGrpNonProd;
			nonFtrJPgroups = StraffWeightCalc.mostNonProdTPJpgrps;						
		}		
		getMsgObj().dispMessage("StraffSOMMapManager","buildPrspctFtrVecs","Most # of non-ftr jps seen in a single "+dispStr+" : " + numBiggestJPs +" | Most different non-prod JPGroups seen : " + numBiggestJPGroups + ".", MsgCodes.info5);
		String jpgDispStr = "";
		for(Integer jpg : nonFtrJPgroups.keySet()) {	jpgDispStr += ""+jpg+" : " +nonFtrJPgroups.get(jpg).size()+" Prod JPs, ";	}	
		getMsgObj().dispMessage("StraffSOMMapManager","buildPrspctFtrVecs","JPgroups : "+jpgDispStr+".", MsgCodes.info5);
	}//_dispPrspctRes
	
	//build either customer or true prospect feature vectors
	private void buildPrspctFtrVecs(Collection<SOMExample> exs, int type) {
		ftrCalcObj.resetCalcObjs(type);
		for (SOMExample ex : exs) {			ex.buildFeatureVector();	}
		////called after all features of this kind of object are built - this calculates alternate compare object
		for (SOMExample ex : exs) {			ex.postFtrVecBuild();	}		
		_dispPrspctRes(type);
		//set state as finished
		ftrCalcObj.finishFtrCalcs(type);	
	}//buildFtrVecs

	//finish building the prospect map - finalize each prospect example and then perform calculation to derive weight vector
	private void finishSOMExampleBuild() {
		if((prospectExamples.get(custExKey).size() != 0) || (productMap.size() != 0)) {
			setMapDataIsLoaded(false);//current map, if there is one, is now out of date, do not use
			//finalize prospects and products - customers are defined by 
			ConcurrentSkipListMap<String, SOMExample> customerMap = prospectExamples.get(custExKey);
			_finalizeProsProdJpJPGMonBeforeFtrCalc("finishSOMExampleBuild", "main customer", customerMap);
			
			//reset calc analysis objects before building feature vectors to enable new analytic info to be aggregated - only build features on customers
			//feature vector only corresponds to actual -customers- since this is what is used to build the map
			Collection<SOMExample> exs = customerMap.values();
			buildPrspctFtrVecs(exs, StraffWeightCalc.custCalcObjIDX);
			//true prospects can be calculated in this manner too but this will only be for reporting really - the comparator for true prospects will be constructed some other way			
			
			getMsgObj().dispMessage("StraffSOMMapManager","finishSOMExampleBuild","End buildFeatureVector prospects | Begin buildFeatureVector products", MsgCodes.info1);
			productsByJpg.clear();		productsByJp.clear();
			
			for (ProductExample ex : productMap.values()) {		ex.buildFeatureVector();  addProductToJPProductMaps(ex);	}
			
			getMsgObj().dispMessage("StraffSOMMapManager","finishSOMExampleBuild","End buildFeatureVector products | Begin calculating diffs and mins", MsgCodes.info1);			
			//dbgDispProductWtSpans()	
			//now get mins and diffs from calc object
			setMinsAndDiffs(ftrCalcObj.getMinBndsAra(), ftrCalcObj.getDiffsBndsAra());
			getMsgObj().dispMessage("StraffSOMMapManager","finishSOMExampleBuild","End calculating diffs and mins | Begin building post-feature calc structs in prospects (i.e. std ftrs) dependent on diffs and mins", MsgCodes.info1);		
			
			exs = customerMap.values();
			for (SOMExample ex : exs) {			ex.buildPostFeatureVectorStructs();		}//this builds std ftr vector for prospects, once diffs and mins are set - not necessary for products, buildFeatureVector for products builds std ftr vec
			//mark all customer prospects as being loaded as preproc data
			setPrivFlag(custProspectDataLoadedIDX, true);
			getMsgObj().dispMessage("StraffSOMMapManager","finishSOMExampleBuild","End building post-feature calc structs in prospects (i.e. std ftrs)", MsgCodes.info5);			
				
		} else {		getMsgObj().dispMessage("StraffSOMMapManager","finishSOMExampleBuild","No prospects or products loaded to calculate.", MsgCodes.warning2);	}
	}//finishSOMExampleBuild
	
	public Float[] getTrainFtrMins() {return this.getMinVals(jps_FtrIDX);}
	public Float[] getTrainFtrDiffs() {return this.getDiffVals(jps_FtrIDX);}

	public Float[] getAllFtrMins() {return this.getMinVals(jps_AllIDX);}
	public Float[] getAllFtrDiffs() {return this.getDiffVals(jps_AllIDX);}

	
	//called to process analysis data
	public void processCalcAnalysis(int _type) {	if (ftrCalcObj != null) {ftrCalcObj.finalizeCalcAnalysis(_type);} else {getMsgObj().dispInfoMessage("StraffSOMMapManager","processCalcAnalysis", "ftrCalcObj == null! attempting to disp res for type : " + _type);}}
	//return # of features for calc analysis type being displayed
	public int numFtrsToShowForCalcAnalysis(int _type) {
		switch(_type) {
			case jps_FtrIDX : {		return jpJpgrpMon.getNumTrainFtrs();		}
			case jps_AllIDX: {		return jpJpgrpMon.getNumAllJpsFtrs();		}
			default : {				return jpJpgrpMon.getNumAllJpsFtrs();		}
		}//switch		
	} 
	
	@Override
	//called from map as bmus are loaded
	public void setAllBMUsFromMap() {
		setProductBMUs();
		setTestBMUs();
		//setTrueProspectBMUs();			//this should be specified manually
	}//setAllBMUsFromMap


	//once map is built, find bmus on map for each product (target that training examples should map to)
	protected void setProductBMUs() {
		getMsgObj().dispMessage("StraffSOMMapManager","setProductBMUs","Start Mapping " +productData.length + " products to best matching units.", MsgCodes.info5);
		boolean canMultiThread=isMTCapable();//if false this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		if(canMultiThread) {
			List<Future<Boolean>> prdcttMapperFtrs = new ArrayList<Future<Boolean>>();
			List<MapProductDataToBMUs> prdcttMappers = new ArrayList<MapProductDataToBMUs>();
			int numForEachThrd = calcNumPerThd(productData.length, numUsableThreads);// ((int)((productData.length-1)/(1.0f*numUsableThreads))) + 1;
			//use this many for every thread but last one
			int stIDX = 0;
			int endIDX = numForEachThrd;				
			for (int i=0; i<(numUsableThreads-1);++i) {				
				prdcttMappers.add(new MapProductDataToBMUs(this,stIDX, endIDX, productData, i, useChiSqDist));
				stIDX = endIDX;
				endIDX += numForEachThrd;
			}
			//last one probably won't end at endIDX, so use length
			if(stIDX < productData.length) {prdcttMappers.add(new MapProductDataToBMUs(this,stIDX, productData.length, productData, numUsableThreads-1, useChiSqDist));}
			
			try {prdcttMapperFtrs = th_exec.invokeAll(prdcttMappers);for(Future<Boolean> f: prdcttMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		
		} else {//for every product find closest map node
			TreeMap<Double, ArrayList<SOMMapNode>> mapNodes;
			if (useChiSqDist) {	
				for (int i=0;i<productData.length;++i) {
					mapNodes = productData[i].findBMUFromNodes_ChiSq_Excl(MapNodes, curMapTestFtrType); 
					productData[i].setMapNodesStruct(SOMExample.SharedFtrsIDX, mapNodes);
					mapNodes = productData[i].findBMUFromNodes_ChiSq(MapNodes, curMapTestFtrType); 
					productData[i].setMapNodesStruct(SOMExample.AllFtrsIDX, mapNodes);
				}
			} else {				
				for (int i=0;i<productData.length;++i) {
					mapNodes = productData[i].findBMUFromNodes_Excl(MapNodes,  curMapTestFtrType); 
					productData[i].setMapNodesStruct(SOMExample.SharedFtrsIDX, mapNodes);
					mapNodes = productData[i].findBMUFromNodes(MapNodes,  curMapTestFtrType); 
					productData[i].setMapNodesStruct(SOMExample.AllFtrsIDX, mapNodes);
				}
			}	
		}
		//go through every product and attach prod to bmu - needs to be done synchronously because don't want to concurrently modify bmus from 2 different prods
		getMsgObj().dispMessage("StraffSOMMapManager","setProductBMUs","Finished finding bmus for all product data. Start adding product data to appropriate bmu's list.", MsgCodes.info1);
		_finalizeBMUProcessing(productData, ExDataType.Product);		
		getMsgObj().dispMessage("StraffSOMMapManager","setProductBMUs","Finished Mapping products to best matching units.", MsgCodes.info5);
	}//setProductBMUs
	
	//once map is built, find bmus on map for each test data example
	private void setTestBMUs() {
		getMsgObj().dispMessage("StraffSOMMapManager","setTestBMUs","Start processing test data prospects for BMUs.", MsgCodes.info1);	
		if(testData.length > 0) {			_setExamplesBMUs(testData, "Testing", ExDataType.Testing);		} 
		else {			getMsgObj().dispMessage("StraffSOMMapManager","setTestBMUs","No Test data to map to BMUs. Aborting.", MsgCodes.warning5);		}
		getMsgObj().dispMessage("StraffSOMMapManager","setTestBMUs","Finished processing test data prospects for BMUs.", MsgCodes.info1);	
	}//setProductBMUs
	//match true prospects to current map/product mappings
	public void buildAndSaveTrueProspectReport() {
		if (!getMapDataIsLoaded()) {	getMsgObj().dispMessage("StraffSOMMapManager","setTrueProspectBMUs","No SOM Map data has been loaded or processed; aborting", MsgCodes.error2);		return;}
		if (!getPrivFlag(trueProspectDataLoadedIDX)) {
			getMsgObj().dispMessage("StraffSOMMapManager","setTrueProspectBMUs","No true prospects loaded, attempting to load.", MsgCodes.info5);
			loadAllTrueProspectData();
		}	
		setTrueProspectBMUs();
		getMsgObj().dispMessage("StraffSOMMapManager","setTrueProspectBMUs","Finished processing true prospects for BMUs.", MsgCodes.info1);	
	}//buildAndSaveTrueProspectReport
	
	//take loaded True Propsects and find their bmus
	private void setTrueProspectBMUs() {
		getMsgObj().dispMessage("StraffSOMMapManager","setTestBMUs","Start processing test data prospects for BMUs.", MsgCodes.info1);	
		//save true prospect-to-product mappings
		if(trueProspectData.length > 0) {			_setExamplesBMUs(trueProspectData, "True Prospect", ExDataType.Validation);		} 
		else {			getMsgObj().dispMessage("StraffSOMMapManager","setTrueProspectBMUs","Unable to process due to no true prospects loaded to map to BMUs. Aborting.", MsgCodes.warning5);		}	
		getMsgObj().dispMessage("StraffSOMMapManager","setTrueProspectBMUs","Finished processing true prospects for BMUs.", MsgCodes.info1);	
	}//setTrueProspectBMUs

	
	//build and save feature-based reports for all examples, products and map nodes TODO
	public void buildFtrBasedRpt() {
		if(!getPrivFlag(testTrainProdDataBuiltIDX)) {return;}
		getMsgObj().dispMessage("StraffSOMMapManager","buildFtrBasedRpt","Start Building feature weight reports for all examples --NOT YET IMPLEMENTED-- .", MsgCodes.info5);
		//all underlying code in SOMExample has been completed
		boolean canMultiThread=isMTCapable();//this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		if(canMultiThread) {
		
		} else {

			for (Tuple<Integer, Integer> nodeLoc : MapNodes.keySet()) {
				SOMExample ex = MapNodes.get(nodeLoc);
				
			}
			for (int idx=0; idx<inputData.length;++idx) {
				SOMExample ex = inputData[idx];
				
			}
			
			for (int idx=0; idx<productData.length;++idx) {
				SOMExample ex = productData[idx];
				
			}
			
		}			
	
		getMsgObj().dispMessage("StraffSOMMapManager","buildFtrBasedRpt","Finished Building feature weight reports for all examples.", MsgCodes.info5);

	}//buildFtrBasedRpt
		
	//debug - display spans of weights of all features in products after products are built
	public void dbgDispProductWtSpans() {
		//debug - display spans of weights of all features in products
		String[] prodExVals = ProductExample.getMinMaxDists();
		getMsgObj().dispMessageAra(prodExVals,"StraffSOMMapManager", "SOMMapManager::finishSOMExampleBuild : spans of all product ftrs seen", 1, MsgCodes.info1);		
	}//dbgDispProductWtSpans()
	
	@Override
	//using the passed map, build the testing and training data partitions and save them to files
	protected void buildTestTrainFromPartition(float trainTestPartition, boolean isBuildingNewMap) {
		getMsgObj().dispMessage("StraffSOMMapManager","buildTestTrainFromInput","Starting Building Input, Test, Train, Product data arrays.", MsgCodes.info5);
		//build array of produt examples based on product map
		productData = productMap.values().toArray(new ProductExample[0]);		
		setPrivFlag(testTrainProdDataBuiltIDX,true);
		//set input data, shuffle it and set test and train partitions
		setInputTestTrainDataArasShuffle(prospectExamples.get(custExKey).values().toArray(new ProspectExample[0]), trainTestPartition, isBuildingNewMap);
		//dbg disp
		//for(ProductExample prdEx : productData) {msgObj.dispMessage("StraffSOMMapManager","buildTestTrainFromInput",prdEx.toString());}
		getMsgObj().dispMessage("StraffSOMMapManager","buildTestTrainFromInput","Finished Building Input, Test, Train, Product data arrays.  Product data size : " +productData.length +".", MsgCodes.info5);
	}//buildTestTrainFromInput
	
	//this will load the default map training configuration
	public void loadSOMConfig() {		
		projConfigData.loadDefaultSOMExp_Config(this);
		
	}//loadSOMConfig
		
	//this will set the current jp->jpg data maps based on passed prospect data map
	//When acquiring new data, this must be performed after all data is loaded, but before
	//the prospect data is finalized and actual map is built due to the data finalization 
	//requiring a knowledge of the entire dataset to build weights appropriately
	private void _setJPDataFromExampleData(ConcurrentSkipListMap<String, SOMExample> map) {
		//object to manage all jps and jpgroups seen in project
		jpJpgrpMon.setJPDataFromExampleData(map, prospectExamples.get(prspctExKey), productMap);		
		setNumTrainFtrs(jpJpgrpMon.getNumTrainFtrs());
		numTtlJps = jpJpgrpMon.getNumAllJpsFtrs();
		//rebuild calc object since feature terrain might have changed 
		String calcFullFileName = projConfigData.getFullCalcInfoFileName(); 
		//make/remake calc object - reads from calcFullFileName data file
		ftrCalcObj = new StraffWeightCalc(this, calcFullFileName, jpJpgrpMon);
	}//setJPDataFromProspectData	
		
	//manage the finalizing of the prospects in tmpProspectMap and the loaded products
	private void _finalizeProsProdJpJPGMonBeforeFtrCalc(String calledFromMethod, String prospectMapName, ConcurrentSkipListMap<String, SOMExample> tmpProspectMap) {
		//code pulled from finalize; finalize builds each example's occurence structures, which describe the jp-jpg relationships found in the example
		getMsgObj().dispMessage("StraffSOMMapManager",calledFromMethod+"->_finalizeProsProdJpJPGMon","Begin initial finalize of "+ prospectMapName +" prospect map to aggregate all JPs and (potentially) determine which records are valid training examples", MsgCodes.info1);		
		//finalize each customer - this will aggregate all the jp's that are seen, as well as finding all records that are bad due to having a 0 ftr vector
		for (SOMExample ex : tmpProspectMap.values()) {			ex.finalizeBuildBeforeFtrCalc();		}		
		
		ConcurrentSkipListMap<String, SOMExample> trueProspects = prospectExamples.get(prspctExKey);
		if(trueProspects.size() != 0) {//if we have true prospects
			getMsgObj().dispMessage("StraffSOMMapManager",calledFromMethod+"->_finalizeProsProdJpJPGMon","End initial finalize of "+ prospectMapName +" prospect map | Begin initial finalize of true prospects map to aggregate all JPs", MsgCodes.info1);			
			Collection<SOMExample> truPspctExs = trueProspects.values();
			for (SOMExample ex : truPspctExs) {			ex.finalizeBuildBeforeFtrCalc();		}		
			getMsgObj().dispMessage("StraffSOMMapManager",calledFromMethod+"->_finalizeProsProdJpJPGMon","End initial finalize of true prospects map | Begin initial finalize of product map to aggregate all JPs", MsgCodes.info1);	
		} else {
			getMsgObj().dispMessage("StraffSOMMapManager",calledFromMethod+"->_finalizeProsProdJpJPGMon","End initial finalize of true prospects map | Begin initial finalize of product map to aggregate all JPs", MsgCodes.info1);	
		}
		
		//finalize build for all products - aggregates all jps seen in product
		for (ProductExample ex : productMap.values()){		ex.finalizeBuildBeforeFtrCalc();		}		
		//must rebuild this because we might not have same jp's
		getMsgObj().dispMessage("StraffSOMMapManager",calledFromMethod+"->_finalizeProsProdJpJPGMon","End initial finalize of product map | Begin setJPDataFromExampleData from prospect map", MsgCodes.info1);
		//we need the jp-jpg counts and relationships dictated by the data by here.
		_setJPDataFromExampleData(tmpProspectMap);
		getMsgObj().dispMessage("StraffSOMMapManager",calledFromMethod+"->_finalizeProsProdJpJPGMon","End setJPDataFromExampleData from "+ prospectMapName +" prospect map", MsgCodes.info1);
	}//_finalizeProsProdJpJPGMon
	
	//this will display debug-related info related to event mapping in raw prospect records
	private void dispDebugEventPresenceData(int[] countsOfBoolResOcc, int[] countsOfBoolResEvt) {
		for(int i=0;i<CustProspectExample.jpOccTypeKeys.length;++i) {
			getMsgObj().dispMessage("StraffSOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect OCC records with "+ CustProspectExample.jpOccTypeKeys[i]+" events : " + countsOfBoolResOcc[i] , MsgCodes.info1);				
			getMsgObj().dispMessage("StraffSOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect Event records with "+ CustProspectExample.jpOccTypeKeys[i]+" events : " + countsOfBoolResEvt[i] , MsgCodes.info1);	
			if(i==1) {
				getMsgObj().dispMessage("StraffSOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect records with global opt : " + ProspectExample._numOptAllOccs[0] , MsgCodes.info1);	
				getMsgObj().dispMessage("StraffSOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect records with non-positive global opt : " + ProspectExample._numOptAllOccs[1] , MsgCodes.info1);	
				getMsgObj().dispMessage("StraffSOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect records with positive global opt : " + ProspectExample._numOptAllOccs[2], MsgCodes.info1);	
			}
			getMsgObj().dispMessage("StraffSOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData"," " , MsgCodes.info1);				
		}		
		getMsgObj().dispMessage("StraffSOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect records with OCC non-source events : " + countsOfBoolResOcc[countsOfBoolResOcc.length-1] , MsgCodes.info1);			
		getMsgObj().dispMessage("StraffSOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect records with Evt non-source events : " + countsOfBoolResEvt[countsOfBoolResEvt.length-1] , MsgCodes.info1);			
		
	}//dispDebugEventPresenceData

	//necessary processing for true prospects - convert a customer to a true prospect if appropriate?
	private void handleTrueProspect(ConcurrentSkipListMap<String, SOMExample> truePrspctMap,String OID, ProspectExample ex) {		
		//truePrspctMap.put(OID,ex.convToTrueProspect());
		truePrspctMap.put(OID,new TrueProspectExample(ex));
	}//handleTrueProspect
		
	//this function will take all raw loaded prospects and partition them into customers and true prospects
	//it determines what the partition/definition is for a "customer" which is used to train the map, and a "true prospect" which is polled against the map to find product membership.
	//typeOfEventsForCustomer : int corresponding to what kind of events define a customer and what defines a prospect.  
	//    0 : cust has order event, prospect does not but has source and possibly other events
	//    1 : cust has some non-source event, prospect does not have customer event but does have source event
	private void _buildCustomerAndProspectMaps(ConcurrentSkipListMap<String, SOMExample> tmpProspectMap) {
		//whether or not to display total event membership counts across all examples to console
		boolean dispDebugEventMembership = true, dispDebugProgress = true;
		String prospectDesc = "";
		int numRecsToProc = tmpProspectMap.size();
		int typeOfEventsForCustomer = projConfigData.getTypeOfEventsForCustAndProspect();

		int[] countsOfBoolResOcc = new int[CustProspectExample.jpOccTypeKeys.length+1],
			countsOfBoolResEvt = new int[CustProspectExample.jpOccTypeKeys.length+1];		//all types of events supported + 1
		ConcurrentSkipListMap<String, SOMExample> 
			customerPrspctMap = prospectExamples.get(custExKey),			//map of customers to build
			truePrspctMap = prospectExamples.get(prspctExKey);				//map of true prospects to build
		int numEx = tmpProspectMap.size(), curEx=0, modSz = numEx/20, nonCustPrspctRecs = 0, noEventDataPrspctRecs = 0;
		Set<String> keySet = tmpProspectMap.keySet();
		switch(typeOfEventsForCustomer) {
		case 0 : {		// cust has order event, prospect does not but has source and possibly other events
			prospectDesc = "Records that do not have any order events";
			for (String OID : keySet) {		
				ProspectExample ex = (ProspectExample) tmpProspectMap.remove(OID); 
				if(dispDebugEventMembership) {dbgEventInExample(ex, countsOfBoolResOcc, countsOfBoolResEvt);}
				if (ex.isTrainablePastCustomer()) {			customerPrspctMap.put(OID, ex);		} 			//training data - has valid feature vector and past order events
				else if (ex.isTrueProspect()) {				handleTrueProspect(truePrspctMap,OID, ex);	} 				//no past order events but has valid source event data
				else {//should never happen - example is going nowhere - is neither true prospect or trainable past customer
					//msgObj.dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","Raw Rec " + ex.OID + " neither trainable customer nor true prospect.  Ignoring.", MsgCodes.info3);
					if(ex.hasEventData()) {				++nonCustPrspctRecs;			} else {			++noEventDataPrspctRecs;		}					
				}
				if(dispDebugProgress) {	++curEx;	if(curEx % modSz == 0) {System.out.print(".");}}
			}
			break;}
		case 1 : {		//cust has some non-source event, prospect does not have customer event but does have source event
			prospectDesc = "Records that only have source events";
			for (String OID : keySet) {		
				ProspectExample ex = (ProspectExample) tmpProspectMap.remove(OID);
				if(dispDebugEventMembership) {dbgEventInExample(ex, countsOfBoolResOcc, countsOfBoolResEvt);}
				if (ex.hasNonSourceEvents()) {					customerPrspctMap.put(OID, ex);		} 			//training data - has valid feature vector and any non-source event data
				else if (ex.hasOnlySourceEvents()) {			handleTrueProspect(truePrspctMap,OID, ex);		} 				//only has source data
				else {//should never happen - example is going nowhere - is neither true prospect or trainable past customer
					//msgObj.dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","Raw Rec " + ex.OID + " neither trainable customer nor true prospect.  Ignoring.", MsgCodes.info3);
					if(ex.hasEventData()) {				++nonCustPrspctRecs;			} else {			++noEventDataPrspctRecs;		}					
				}
				if(dispDebugProgress) {	++curEx;	if(curEx % modSz == 0) {System.out.print(".");}}
			}
			break;}
			default : { 				return;}
		}//switch
		if(dispDebugProgress) {System.out.println("");}
		//eventMapTypeKeys 
		//display debug info relating to counts of different types of events present in given examples
		if(dispDebugEventMembership) {dispDebugEventPresenceData(countsOfBoolResOcc, countsOfBoolResEvt);}
		//display customers and true prospects info
		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","Raw Records Unique OIDs presented : " + numRecsToProc, MsgCodes.info3);
		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","# True Prospect records (" + prospectDesc +") : " + truePrspctMap.size(), MsgCodes.info1);	
		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","# Customer Prospect records found with trainable event-based info : " + customerPrspctMap.size(), MsgCodes.info3);
		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","# Raw Records that are neither true prospects nor customers but has events : " + nonCustPrspctRecs + " and with no events : "+ noEventDataPrspctRecs, MsgCodes.info3);

	}//_buildCustomerAndProspectMaps
	

	@Override
	//build a map node that is formatted specifically for this project
	public SOMMapNode buildMapNode(Tuple<Integer,Integer>mapLoc, String[] tkns) {return new StraffSOMMapNode(this,mapLoc, tkns);}
	
	/////////////////////////////////////////
	//drawing and graphics methods - these must check if win and/or pa exist, or else except win or pa as passed arguments, to manage when this code is executed without UI
	
	private static int dispTruPrxpctDataFrame = 0, numDispTruPrxpctDataFrames = 100;
	public void drawTruPrspctData(my_procApplet pa) {
		pa.pushMatrix();pa.pushStyle();
		if (trueProspectData.length < numDispTruPrxpctDataFrames) {	for(int i=0;i<trueProspectData.length;++i){		trueProspectData[i].drawMeMap(pa);	}	} 
		else {
			for(int i=dispTruPrxpctDataFrame;i<trueProspectData.length-numDispTruPrxpctDataFrames;i+=numDispTruPrxpctDataFrames){		trueProspectData[i].drawMeMap(pa);	}
			for(int i=(trueProspectData.length-numDispTruPrxpctDataFrames);i<trueProspectData.length;++i){		trueProspectData[i].drawMeMap(pa);	}				//always draw these (small count < numDispDataFrames
			dispTruPrxpctDataFrame = (dispTruPrxpctDataFrame + 1) % numDispTruPrxpctDataFrames;
		}
		pa.popStyle();pa.popMatrix();		
	}//drawTruPrspctData
	
	//draw all product nodes with max vals corresponding to current JPIDX
	public void drawProductNodes(my_procApplet pa, int prodJpIDX, boolean showJPorJPG) {
		pa.pushMatrix();pa.pushStyle();
		ArrayList<ProductExample> prodsToShow = (showJPorJPG ? productsByJp.get(jpJpgrpMon.getProdJpByIdx(prodJpIDX)) :  productsByJpg.get(jpJpgrpMon.getProdJpGrpByIdx(prodJpIDX)));
		for(ProductExample ex : prodsToShow) {			ex.drawMeLinkedToBMU(pa, 5.0f,ex.OID);		}		
		pa.popStyle();pa.popMatrix();
	}//drawProductNodes	
	
	private static int dispProdDataFrame = 0, numDispProdDataFrames = 20, framesPerDisp = 0, maxFramesPerDisp = 10;
	//show all products
	public void drawAllProductNodes(my_procApplet pa) {
		pa.pushMatrix();pa.pushStyle();
		if (productData.length-numDispProdDataFrames <=  0 ) {	for(int i=0;i<productData.length;++i){		productData[i].drawMeMap(pa);	}} 
		else {
			for(int i=dispProdDataFrame;i<productData.length-numDispProdDataFrames;i+=numDispProdDataFrames){		productData[i].drawMeMap(pa);	}
			for(int i=(productData.length-numDispProdDataFrames);i<productData.length;++i){		productData[i].drawMeMap(pa);	}				//always draw these (small count < numDispDataFrames
			++framesPerDisp;
			if(framesPerDisp >= maxFramesPerDisp) {
				framesPerDisp = 0;
				dispProdDataFrame = (dispProdDataFrame + 1) % numDispProdDataFrames;
			}
		}
		//for(ProductExample ex : productData) {ex.drawMeLinkedToBMU(pa, 5.0f,ex.OID);}		
		pa.popStyle();pa.popMatrix();
	}//drawProductNodes
	
	public void drawAnalysisAllJps(my_procApplet pa, float ht, float barWidth, int curJPIdx,int calcIDX) {
		pa.pushMatrix();pa.pushStyle();
		ftrCalcObj.drawAllCalcRes(pa, ht, barWidth, curJPIdx, calcIDX);
		pa.popStyle();pa.popMatrix();
	}//drawAnalysisAllJps
	
	public void drawAnalysisFtrJps(my_procApplet pa, float ht, float barWidth, int curJPIdx,int calcIDX) {
		pa.pushMatrix();pa.pushStyle();
		ftrCalcObj.drawFtrCalcRes(pa, ht, barWidth, curJPIdx, calcIDX);
		pa.popStyle();pa.popMatrix();
	}//drawAnalysisAllJps
	
	public void drawAnalysisOneJp_All(my_procApplet pa,  float ht, float width, int curJPIdx,int calcIDX) {
		pa.pushMatrix();pa.pushStyle();
		ftrCalcObj.drawSingleFtr(pa, ht, width,jpJpgrpMon.getAllJpByIdx(curJPIdx),calcIDX);		//Enable analysis 
		pa.popStyle();pa.popMatrix();
	}//drawAnalysisOneFtrJp	
	
	public void drawAnalysisOneJp_Ftr(my_procApplet pa,  float ht, float width, int curJPIdx,int calcIDX) {
		pa.pushMatrix();pa.pushStyle();
		ftrCalcObj.drawSingleFtr(pa, ht, width,jpJpgrpMon.getFtrJpByIdx(curJPIdx),calcIDX);		//Enable analysis 
		pa.popStyle();pa.popMatrix();
	}//drawAnalysisOneFtrJp	

	private int getProdDistType() {return (getPrivFlag(mapExclProdZeroFtrIDX) ? ProductExample.SharedFtrsIDX : ProductExample.AllFtrsIDX);}
	private static int dispProdJPDataFrame = 0, curProdJPIdx = -1, curProdTimer = 0;
	//display the region of the map expected to be impacted by the products serving the passed jp 
	public void drawProductRegion(my_procApplet pa, int prodJpIDX, double maxDist) {
		pa.pushMatrix();pa.pushStyle();
		ArrayList<ProductExample> prodsToShow = productsByJp.get(jpJpgrpMon.getProdJpByIdx(prodJpIDX));
		if(curProdJPIdx != prodJpIDX) {
			curProdJPIdx = prodJpIDX;
			dispProdJPDataFrame = 0;
			curProdTimer = 0;
		}
		int distType = getProdDistType();
		ProductExample ex = prodsToShow.get(dispProdJPDataFrame);
		ex.drawMeLinkedToBMU(pa, 5.0f,ex.OID);		
		ex.drawProdMapExtent(pa, distType, prodsToShow.size(), maxDist);
		++curProdTimer;
		if(curProdTimer > 10) {
			curProdTimer = 0;
			dispProdJPDataFrame = (dispProdJPDataFrame + 1) % prodsToShow.size();
		}
		pa.popStyle();pa.popMatrix();	
	}	
	
	//app-specific drawing routines for side bar
	protected void drawResultBarPriv1(my_procApplet pa, float yOff){
		
	}
	protected void drawResultBarPriv2(my_procApplet pa, float yOff){
		
	}
	protected void drawResultBarPriv3(my_procApplet pa, float yOff){}
	

	// end drawing routines
	//////////////////////////////////////////////////////
	
	public DispSOMMapExample buildTmpDataExampleFtrs(myPointf ptrLoc, TreeMap<Integer, Float> ftrs, float sens) {return new DispSOMMapExample(this, ptrLoc, ftrs, sens);}
	public DispSOMMapExample buildTmpDataExampleDists(myPointf ptrLoc, float dist, float sens) {return new DispSOMMapExample(this, ptrLoc, dist, sens);}
	
	private void initPrivFlags(){priv_stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setPrivFlag(i,false);}}
	public void setAllPrivFlags(int[] idxs, boolean val) {for (int idx : idxs) {setPrivFlag(idx, val);}}
	public void setPrivFlag(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		priv_stFlags[flIDX] = (val ?  priv_stFlags[flIDX] | mask : priv_stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case debugIDX : 				{break;}	
			case mapExclProdZeroFtrIDX : 	{break;}
			case prospectDataLoadedIDX: 	{break;}		//raw prospect data has been loaded but not yet processed
			case optDataLoadedIDX: 			{break;}				//raw opt data has been loaded but not processed
			case orderDataLoadedIDX: 		{break;}			//raw order data loaded not proced
			case rawPrspctEvDataProcedIDX: 	{break;}				//all raw prospect/event data has been processed into StraffSOMExamples and subsequently erased
			case rawProducDataProcedIDX : 	{break;}			//all raw product data has been processed into StraffSOMExamples and subsequently erased 
			case testTrainProdDataBuiltIDX : 	{break;}			//arrays of input, training and testing data built
			case trueProspectDataLoadedIDX :{				//preproc data has been loaded corresponding to true prospects, generally defined as prospects that do not have any order event history				
				break;	}
			case custProspectDataLoadedIDX	 : 	{			//preproc data for custs has been loaded
				break;}	
			case preProcProdDataLoadedIDX	 : 	{			//preproc data for products has been loaded
				break;}
		}
	}//setFlag		
	public boolean getPrivFlag(int idx){int bitLoc = 1<<(idx%32);return (priv_stFlags[idx/32] & bitLoc) == bitLoc;}		

	public boolean isFtrCalcDone(int idx) {return (ftrCalcObj != null) && ftrCalcObj.calcAnalysisIsReady(idx);}	

	public ProductExample getProductByID(String prodOID) {	return productMap.get(prodOID);		}
	
	public String getAllJpByIdxStr(int idx) {return jpJpgrpMon.getAllJpByIdxStr(idx);}	
	public String getAllJpGrpByIdxStr(int idx) {return jpJpgrpMon.getAllJpGrpByIdxStr(idx);}
		
	public String getFtrJpByIdxStr(int idx) {return jpJpgrpMon.getFtrJpByIdxStr(idx);}	
	public String getFtrJpGrpByIdxStr(int idx) {return jpJpgrpMon.getFtrJpGrpByIdxStr(idx);}
		
	//this will return the appropriate jpgrp for the given jpIDX (list idx)
	public int getUI_JPGrpFromFtrJP(int jpIdx, int curVal) {		return jpJpgrpMon.getUI_JPGrpFromFtrJP(jpIdx, curVal);}
	//this will return the first(lowest) jp for a particular jpgrp
	public int getUI_FirstJPFromFtrJPG(int jpgIdx, int curJPVal) {	return jpJpgrpMon.getUI_FirstJPFromFtrJPG(jpgIdx, curJPVal);}	
	//this will return the appropriate jpgrp for the given jpIDX (list idx)
	public int getUI_JPGrpFromAllJP(int jpIdx, int curVal) {		return jpJpgrpMon.getUI_JPGrpFromAllJP(jpIdx, curVal);}
	//this will return the first(lowest) jp for a particular jpgrp
	public int getUI_FirstJPFromAllJPG(int jpgIdx, int curJPVal) {	return jpJpgrpMon.getUI_FirstJPFromAllJPG(jpgIdx, curJPVal);}	
	
	
	//return appropriately pathed file name for map image of specified JP idx
	public String getSOMLocClrImgForJPFName(int jpIDX) {
		int jp = jpJpgrpMon.getFtrJpByIdx(jpIDX);
		return projConfigData.getSOMLocClrImgForJPFName(jp);	
	}	
	
	////////////////////////
	// debug routines
	
	public void dbgShowAllRawData() {		rawDataLdr.dbgShowAllRawData();}//showAllRawData
	//debugging function to display all unique jps seen in data
	public void dbgShowUniqueJPsSeen() {	jpJpgrpMon.dbgShowUniqueJPsSeen();}//dbgShowUniqueJPsSeen	
	public void dbgDispKnownJPsJPGs() {		jpJpgrpMon.dbgDispKnownJPsJPGs();	}//dbgDispKnownJPsJPGs
	
	//display current calc function's equation coefficients for each JP
	public void dbgShowCalcEqs() {
		if (null == ftrCalcObj) {	getMsgObj().dispMessage("StraffSOMMapManager","dbgShowCalcEqs","No calc object made to display.", MsgCodes.warning1);return;	}
		getMsgObj().dispMessage("StraffSOMMapManager","dbgShowCalcEqs","Weight Calculation Equations : \n"+ftrCalcObj.toString(), MsgCodes.info1);		
	}

	public void dbgShowJpJpgrpData() {		getMsgObj().dispMessage("StraffSOMMapManager","dbgShowJpJpgrpData","Showing current jpJpg Data : \n"+jpJpgrpMon.toString(), MsgCodes.info1);	}
	
	//check and increment relevant counters if specific events are found in a particular example
	private void dbgEventInExample(ProspectExample ex, int[] countsOfBoolResOcc, int[] countsOfBoolResEvt) {
		boolean[] exOCCStatus, exEvtStatus;
		exOCCStatus = ex.getExampleStatusOcc();
		exEvtStatus = ex.getExampleStatusEvt();
		for(int i=0;i<exOCCStatus.length;++i) {
			if(exOCCStatus[i]) {++countsOfBoolResOcc[i];}
			if(exEvtStatus[i]) {++countsOfBoolResEvt[i];}
		}
	}//dbgEventInExample

	public String toString(){
		String res = super.toString();
		//lots of data is missing
		return res;
	}	
	
}//SOMMapManager
