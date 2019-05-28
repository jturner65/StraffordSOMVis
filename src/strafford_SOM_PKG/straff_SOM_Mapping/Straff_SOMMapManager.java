package strafford_SOM_PKG.straff_SOM_Mapping;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_fileIO.*;
import base_SOM_Objects.som_ui.SOMUIToMapCom;
import base_SOM_Objects.som_utils.MapExFtrCalcs_Runner;
import base_SOM_Objects.som_utils.SOMProjConfigData;
import base_SOM_Objects.som_utils.segments.SOMMapSegment;
import base_SOM_Objects.som_utils.segments.SOM_FtrWtSegment;
import base_SOM_Objects.som_utils.segments.SOM_UMatrixSegment;
import base_UI_Objects.*;
import base_Utils_Objects.*;
import strafford_SOM_PKG.Straff_SOMMapUIWin;
import strafford_SOM_PKG.straff_ProcDataHandling.data_loaders.*;
import strafford_SOM_PKG.straff_RawDataHandling.StraffSOMRawDataLdrCnvrtr;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.BaseRawData;

import strafford_SOM_PKG.straff_SOM_Examples.*;
import strafford_SOM_PKG.straff_SOM_Examples.products.ProductExample;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.*;

import strafford_SOM_PKG.straff_SOM_Mapping.exampleMappers.*;
import strafford_SOM_PKG.straff_SOM_Mapping.segments.Straff_JPGroupOrderSegment;
import strafford_SOM_PKG.straff_SOM_Mapping.segments.Straff_JPOrderSegement;
import strafford_SOM_PKG.straff_Utils.*;
import strafford_SOM_PKG.straff_Utils.featureCalc.StraffWeightCalc;


//this class holds the data describing a SOM and the data used to both build and query the som
public class Straff_SOMMapManager extends SOMMapManager {	
	//structure to map specified products to the SOM and find prospects with varying levels of confidence
	private StraffProdMapOutputBuilder prodOutputMapper;	
	//manage all jps and jpgs seen in project
	public MonitorJpJpgrp jpJpgrpMon;	
	//calc object to be used to derive feature vector for each prospect
	public StraffWeightCalc ftrCalcObj;
	//object to manage all raw data processing
	private StraffSOMRawDataLdrCnvrtr rawDataLdr;
			
	////////////////////////////////////////////////////////////////////////////////////////////////
	//data descriptions
	//array of keys for exampleDataMappers map - one per type of data
	protected final String[] exampleDataMapperKeys = new String[] {	"custProspect","trueProspect","product"};
	protected static final int 
		custPrspctIDX = 0,
		truePrspctIDX = 1,
		productIDX = 2;
	
	//ref to cust prspct mapper
	private Straff_SOMCustPrspctPerOrderMapper custPrspctExMapper;
	//ref to tru prospect mapper
	private Straff_SOMTruePrspctMapper truePrspctExMapper;
	//ref to prod mapper
	private Straff_SOMProductMapper prodExMapper;	
	
	//Map of jps to segment
	public TreeMap<Integer, SOMMapSegment> JP_Segments;
	//map of jpgroup to segment
	public TreeMap<Integer, SOMMapSegment> JPGroup_Segments;
	//map with key being jp and with value being collection of map nodes with ORDER presence in that jp
	public TreeMap<Integer,Collection<SOMMapNode>> MapNodesWithOrderJPs;
	//map with key being jpgroup and with value being collection of map nodes with ORDER presence in that jpgroup
	public TreeMap<Integer,Collection<SOMMapNode>> MapNodesWithOrderJPGroups;
	
	//map of jpgroup idx and all map nodes that have non-zero presence in features(jps) that belong to that jpgroup
	protected TreeMap<Integer, HashSet<SOMMapNode>> MapNodesByJPGroupIDX;
	
	//data for products to be measured on map
	private ProductExample[] productData;
	//maps of product arrays, with key for each map being either jpg or jp
	//private TreeMap<Integer, ArrayList<ProductExample>> productsByJpg, productsByJp;
	//total # of jps in all data, including source events
	private int numTtlJps;	
	
	public static final int 
			jps_FtrIDX = 0,		//idx in delta structs (diffs, mins) for jps used for training ftrs (non virtual jps)
			jps_AllIDX = 1;		//idx in delta structs for all jps
	public static final int numFtrTypes = 2;
	
	//private int[] priv_stFlags;						//state flags - bits in array holding relevant process info
	public static final int	
		mapExclProdZeroFtrIDX  		= numBaseFlags + 0,			//if true, exclude ftrs that are not present in example when calculating distance on map	
		//raw data loading/processing state : 
		prospectDataLoadedIDX		= numBaseFlags + 1,			//raw prospect data has been loaded but not yet processed
		optDataLoadedIDX			= numBaseFlags + 2,			//raw opt data has been loaded but not processed
		orderDataLoadedIDX			= numBaseFlags + 3,			//raw order data loaded not proced
		linkDataLoadedIDX			= numBaseFlags + 4,			//raw link data loaded not proced
		sourceDataLoadedIDX			= numBaseFlags + 5,			//raw source event data loaded not proced
		tcTagsDataLoadedIDX			= numBaseFlags + 6,			//raw tc taggings data loaded not proced
		jpDataLoadedIDX				= numBaseFlags + 7,			//raw jp data loaded not proced
		jpgDataLoadedIDX			= numBaseFlags + 8,			//raw jpg data loaded not proced
		//training data saved state : 
		testTrainProdDataBuiltIDX	= numBaseFlags + 9;			//product, input, testing and training data arrays have all been built
		
	public static final int numFlags = numBaseFlags + 10;	
	
	//////////////////////////////
	//# of records to save in preproc data csvs
	public static final int preProcDatPartSz = 50000;	
	
	private Straff_SOMMapManager(Straff_SOMMapUIWin _win, float[] _dims, TreeMap<String, Object> _argsMap) {
		super(_win,_dims, _argsMap);		
		//object to manage all jps and jpgroups seen in project
		jpJpgrpMon = new MonitorJpJpgrp(this);
		//all raw data loading moved to this object
		rawDataLdr = new StraffSOMRawDataLdrCnvrtr(this, projConfigData);		
	}//ctor	
	
	//ctor from non-UI stub main
	public Straff_SOMMapManager(float[] _dims, TreeMap<String, Object> _argsMap) {this(null,_dims, _argsMap);}		
	
	/**
	 * build the map of example mappers used to manage all the data the SOM will consume
	 */
	@Override
	protected void buildExampleDataMappers() {		
		//exampleDataMappers holds data mappers - eventually will replace all example maps, and will derive all training data arrays it is declared in base constructor
		//build appropriate data mappers for this SOM - cust prospects, true prospects, products
		exampleDataMappers.put("custProspect", new Straff_SOMCustPrspctPerOrderMapper(this, "custProspect"));
		exampleDataMappers.put("trueProspect", new Straff_SOMTruePrspctMapper(this, "trueProspect"));
		exampleDataMappers.put("product", new Straff_SOMProductMapper(this, "product"));
		
		custPrspctExMapper = (Straff_SOMCustPrspctPerOrderMapper) exampleDataMappers.get("custProspect");
		//ref to tru prospect mapper
		truePrspctExMapper = (Straff_SOMTruePrspctMapper) exampleDataMappers.get("trueProspect");
		//ref to prod mapper
		prodExMapper = (Straff_SOMProductMapper) exampleDataMappers.get("product");		
	}//buildExampleDataMappers

	/**
	 * build instance-specific project file configuration 
	 */
	@Override
	protected SOMProjConfigData buildProjConfigData(TreeMap<String, Object> _argsMap) {				return new SOMProjConfigData(this,_argsMap);	}
	
	/**
	 * build an interface to manage communications between UI and SOM map dat
	 * This interface will need to include a reference to an application-specific UI window
	 */
	@Override
	protected SOMUIToMapCom buildSOM_UI_Interface() {	return new SOMUIToMapCom(this, win);}


	//set max display list values
	public void setUI_JPFtrMaxVals(int jpGrpLen, int jpLen) {if (win != null) {((Straff_SOMMapUIWin)win).setUI_JPFtrListMaxVals(jpGrpLen, jpLen);}}
	public void setUI_JPAllSeenMaxVals(int jpGrpLen, int jpLen) {if (win != null) {((Straff_SOMMapUIWin)win).setUI_JPAllSeenListMaxVals(jpGrpLen, jpLen);}}
	protected int _getNumSecondaryMaps(){return jpJpgrpMon.getLenFtrJpGrpByIdx();}

	
	//reset all maps
	private void resetAllMaps() {		
		for(SOMExampleMapper mapper : exampleDataMappers.values()) {	mapper.reset();		}
		
	}//resetAllMaps
		
	//fromCSVFiles : whether loading data from csv files or from SQL calls
	//eventsOnly : only use examples with event data to train
	//append : whether to append to existing data values or to load new data
	public void loadAllRawData(boolean fromCSVFiles) {
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllRawData","Start loading and processing raw data", MsgCodes.info5);
		//this will load all raw data into memory from csv files or sql queries(todo)
		ConcurrentSkipListMap<String, ArrayList<BaseRawData>> _rawDataAras = rawDataLdr.loadAllRawData(fromCSVFiles);
		if(null==_rawDataAras) {		return;	}
		//process loaded data
		//dbgLoadedData(tcTagsIDX);
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllRawData","Start Processing all loaded raw data", MsgCodes.info5);
		if (!(getFlag(prospectDataLoadedIDX) && getFlag(optDataLoadedIDX) && getFlag(orderDataLoadedIDX) && getFlag(tcTagsDataLoadedIDX))){//not all data loaded, don't process 
			getMsgObj().dispMessage("StraffSOMMapManager","loadAllRawData","Can't build data examples until raw data is all loaded", MsgCodes.warning2);			
		} else {			
			resetAllMaps();
			//build prospectMap - first get prospect data and add to map
			//ConcurrentSkipListMap<String, SOMExample> tmpProspectMap = new ConcurrentSkipListMap<String, SOMExample>();
			Straff_SOMCustPrspctPerOrderMapper tmpProspectMapper = new Straff_SOMCustPrspctPerOrderMapper(this, "custProspect");
			//data loader will build prospect and product maps
			//rawDataLdr.procRawLoadedData_map(tmpProspectMap, productMap);	
			
			rawDataLdr.procRawLoadedData(tmpProspectMapper, prodExMapper);		
			//finalize around temp map - finalize builds each example's occurrence structures, which describe the jp-jpg relationships found in the example
			//we perform this on the temp map to examine the processed examples so that we can determine which should be considered "customers" and which are "true prospects"
			//this determination is made based on the nature of the events each prospect has; finalize tmp mapper, product mapper and then jpjpg
			
			//finalize each customer in tmp mapper -  this will aggregate all the jp's that are seen and prepare example for calculating ftr vector
			tmpProspectMapper.finalizeAllExamples();				
			//finalize build for all products - aggregates all jps seen in product
			prodExMapper.finalizeAllExamples();		
			getMsgObj().dispMessage("StraffSOMMapManager","loadAllRawData","Raw Prospects and Products have been finalized to determine JPs and JPGroups present.", MsgCodes.info5);			
			
			//we need the jp-jpg counts and relationships dictated by the data by here.
			_setJPDataFromExampleData(tmpProspectMapper);			
			//build actual customer and validation maps using rules defining what is a customer (probably means having an order event) and what is a "prospect" (probably not having an order event)
			_buildCustomerAndProspectMaps(tmpProspectMapper);			
			//by hear both prospect mappers have been appropriately populated
			
			//finalize - recalc all processed data in case new products have different JP's present, set flags and save to file
			
			//calcFtrsDiffsMinsAndSave();
			getMsgObj().dispMessage("StraffSOMMapManager","loadAllRawData","Start loading calc object, calculating all feature vectors for prospects and products, calculating mins and diffs, and saving all results.", MsgCodes.info5);
				//condition data before saving it
			_finalizeAllMappersBeforeFtrCalc();
				//save all prospect, product and jpjpg monitor data
			saveAllExamples();
			
			getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData","Finished preprocessing and saving all loaded raw data", MsgCodes.info5);
		}

		getMsgObj().dispMessage("StraffSOMMapManager","loadAllRawData","Finished loading raw data, processing and saving preprocessed data", MsgCodes.info5);
	}//loadAllRawData
	
	@Override
	//load all preprocessed data from default data location required to train a map - for straff it is customers and products
	protected void loadPreProcTrainData() { loadPreProcTrainData(projConfigData.getPreProcDataDesiredSubDirName());}

	//pass subdir within data directory, or use default
	@Override
	protected void loadPreProcTrainData(String subDir) {	//preprocced data might be different than current true prospect data
		getMsgObj().dispMessage("StraffSOMMapManager","loadPreProcTrainData","Begin loading preprocced data from " + subDir +  "directory.", MsgCodes.info5);
			//load monitor first;save it last - keeps records of jps and jpgs even for data not loaded
		getMsgObj().dispMessage("StraffSOMMapManager","loadPreProcTrainData","Loading MonitorJpJpgrp data", MsgCodes.info1);
		String[] loadSrcFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(subDir, false, "MonitorJpJpgrpData");
		jpJpgrpMon.loadAllData(loadSrcFNamePrefixAra[0],".csv");
		getMsgObj().dispMessage("StraffSOMMapManager","loadPreProcTrainData","Finished loading MonitorJpJpgrp data", MsgCodes.info1);
			//display all jps and jpgs in currently loaded jp-jpg monitor
		getMsgObj().dispMessage("StraffSOMMapManager","loadPreProcTrainData",jpJpgrpMon.toString(), MsgCodes.info1);
		
			//load customer data
		custPrspctExMapper.loadAllPreProccedMapData(subDir);
			//load preproc product data
		prodExMapper.loadAllPreProccedMapData(subDir);
		
		
			//finalize and calc ftr vecs on customer prospects and products
		finishSOMExampleBuild();
			//preprocced data might be different than current true prospect data, so clear flag and reset map (clear out memory)
		exampleDataMappers.get("trueProspect").reset();
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllPreProccedData","Finished loading preprocced data from " + subDir +  "directory.", MsgCodes.info5);
	}//loadAllPreProccedData
	
	public void loadAllTrueProspectData() {loadAllTrueProspectData(projConfigData.getPreProcDataDesiredSubDirName());}
	//load validation (true prospects) data found in subDir
	private void loadAllTrueProspectData(String subDir) {
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllTrueProspectData","Begin loading preprocessed True Prospect data from " + subDir +  "directory.", MsgCodes.info5);
			//load customers if not loaded
		if(!custPrspctExMapper.isDataPreProcced()) {		loadPreprocAndBuildTestTrainPartitions();}
			//load true prospects
		truePrspctExMapper.loadAllPreProccedMapData(subDir);
		
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllTrueProspectData"," Begin initial finalize of true prospects map", MsgCodes.info1);	
		truePrspctExMapper.finalizeAllExamples();
		
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllTrueProspectData","End initial finalize of true prospects map | Begin build feature vector for all true prospects.", MsgCodes.info1);	
		
		if(ftrCalcObj.custNonProdJpCalcIsDone()) {
			truePrspctExMapper.buildFeatureVectors();
		} else {
			getMsgObj().dispMessage("StraffSOMMapManager","loadAllTrueProspectData","Attempting to build true prospect ftr vectors without calculating the contribution from customers sharing same non-product jps.  Aborting.", MsgCodes.error1);	
		}
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllTrueProspectData","End build feature vector for all true prospects | Begin post feature vector build.", MsgCodes.info1);	
		truePrspctExMapper.buildPostFtrVecStructs();
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllTrueProspectData","End post feature vector build.", MsgCodes.info1);	
		
		//build array of trueProspectData used to map
		validationData = truePrspctExMapper.buildExampleArray(false);
		numValidationData = validationData.length;

		getMsgObj().dispMessage("StraffSOMMapManager","loadAllTrueProspectData","Finished loading preprocessed True Prospect data from " + subDir +  "directory.", MsgCodes.info5);
	}//loadAllProspectData	
	
	//save MonitorJpJpgrp, construct that manages jp-jpgroup relationships (values and corresponding indexes in arrays)
	private void saveMonitorJpJpgrp() {
		getMsgObj().dispMessage("StraffSOMMapManager","saveMonitorJpJpgrp","Saving MonitorJpJpgrp data", MsgCodes.info5);
		String[] saveDestFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(false, "MonitorJpJpgrpData");
		jpJpgrpMon.saveAllData(saveDestFNamePrefixAra[0],".csv");
		getMsgObj().dispMessage("StraffSOMMapManager","saveMonitorJpJpgrp","Finished saving MonitorJpJpgrp data", MsgCodes.info5);
	}//saveMonitorJpJpgrp
			
	//save all currently preprocced loaded data - customer and true prospects, products, and jpjpg monitor
	protected void saveAllExamples() {
		getMsgObj().dispMessage("StraffSOMMapManager","saveAllExamples","Begin Saving all Preproccessed Examples.", MsgCodes.info5);
			//save customer prospect examples
		boolean custPrspctSuccess = exampleDataMappers.get("custProspect").saveAllPreProccedMapData();
			//save true prospect examples
		boolean truePrspctSuccess = exampleDataMappers.get("trueProspect").saveAllPreProccedMapData();
			//save products
		boolean prodSuccess = exampleDataMappers.get("product").saveAllPreProccedMapData();
		getMsgObj().dispMessage("StraffSOMMapManager","saveAllExamples","Finished Saving all Preproccessed Examples.", MsgCodes.info5);
		if (custPrspctSuccess || truePrspctSuccess || prodSuccess) { saveMonitorJpJpgrp();}
		
	}//saveAllExamples
			
	//build the calculation object, recalculate the features and calc and save the mins, diffs, and all prospect, validation and product map data
	public void calcFtrsDiffsMinsAndSave() {
		getMsgObj().dispMessage("StraffSOMMapManager","rebuildCalcObj","Start loading calc object, calculating all feature vectors for prospects and products, calculating mins and diffs, and saving all results.", MsgCodes.info5);
		//
		finishSOMExampleBuild();

		getMsgObj().dispMessage("StraffSOMMapManager","rebuildCalcObj","Finished loading calc object, calculating all feature vectors for prospects and products & calculating mins and diffs | Start saving all results.", MsgCodes.info1);
		saveAllExamples();
		getMsgObj().dispMessage("StraffSOMMapManager","rebuildCalcObj","Finished loading calc object, calculating all feature vectors for prospects and products, calculating mins and diffs, and saving all results.", MsgCodes.info5);
	}//calcFtrsDiffsMinsAndSave()
	
	public void dispAllNumOrderCounts() {
		getMsgObj().dispMessage("StraffSOMMapManager","buildPrspctFtrVecs->dispAllNumOrderCounts","# of customers with particular order count : ", MsgCodes.info1);
		getMsgObj().dispMessage("StraffSOMMapManager","buildPrspctFtrVecs->dispAllNumOrderCounts","\t# of Unique JPs\t# of Customers with this many Unique Jps",MsgCodes.info1);
		int ttlOrders = 0;
		for(Integer numJPs : CustProspectExample.ttlOrderCount.keySet()) {
			Integer[] orderDat = CustProspectExample.ttlOrderCount.get(numJPs);
			getMsgObj().dispMessage("StraffSOMMapManager","buildPrspctFtrVecs->dispAllNumOrderCounts","\t"+numJPs+"\t\t"+orderDat[0]+"\t\t"+orderDat[1],MsgCodes.info1);
			ttlOrders += orderDat[1];
		}
		getMsgObj().dispMessage("StraffSOMMapManager","buildPrspctFtrVecs->dispAllNumOrderCounts","\tTotal # of Orders across all customers : " + ttlOrders,MsgCodes.info1);
		// the # of customers considered "bad" after features were built
		getMsgObj().dispMessage("StraffSOMMapManager","buildPrspctFtrVecs->dispAllNumOrderCounts","\tTotal # Of Customers considered 'bad' after features were built : " + CustProspectExample.NumBadExamplesAfterFtrsBuilt + ".  These examples shouldn't be used to train.",MsgCodes.info1);
	}//dispAllNumOrderCounts

	//finish building the prospect map - finalize each prospect example and then perform calculation to derive weight vector
	private void finishSOMExampleBuild() {
		Straff_SOMCustPrspctPerOrderMapper custMapper = (Straff_SOMCustPrspctPerOrderMapper) exampleDataMappers.get("custProspect");
		Straff_SOMProductMapper prodMapper = (Straff_SOMProductMapper) exampleDataMappers.get("product");		
		
		if((custMapper.getNumMapExamples() != 0) || (prodMapper.getNumMapExamples() != 0)) {
				//current SOM map, if there is one, is now out of date, do not use
			setMapDataIsLoaded(false);			
				//finalize customer prospects and products (and true prospects if they exist) - customers are defined by having criteria that enable their behavior to be used as to train the SOM		
			_finalizeAllMappersBeforeFtrCalc();
				//clear out records of order counts
			CustProspectExample.ttlOrderCount.clear();
				//feature vector only corresponds to actual -customers- since this is what is used to build the map - build feature vector for customer prospects
			custMapper.buildFeatureVectors();				
				//build featues for products
			prodMapper.buildFeatureVectors();
			
			getMsgObj().dispMessage("StraffSOMMapManager","finishSOMExampleBuild","End buildFeatureVector products | Begin calculating diffs and mins", MsgCodes.info1);			
			//dbgDispProductWtSpans()	
				//now get mins and diffs from calc object
			setMinsAndDiffs(ftrCalcObj.getMinBndsAra(), ftrCalcObj.getDiffsBndsAra());
			getMsgObj().dispMessage("StraffSOMMapManager","finishSOMExampleBuild","End calculating diffs and mins | Begin building post-feature calc structs in prospects (i.e. std ftrs) dependent on diffs and mins", MsgCodes.info1);		
				//now finalize post feature calc -this will do std features			
			custMapper.buildPostFtrVecStructs();
				//build std features for products
			prodMapper.buildPostFtrVecStructs();

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
		//build jp segments from mapped training examples
		buildJpSegmentsOnMap();
		//build jpgroup segments from mapped training examples
		buildJpGroupSegmentsOnMap();
		//map products to bmus
		setProductBMUs();
		//map test data to bmus
		setTestBMUs();
		//setTrueProspectBMUs();			//this should be specified manually
	}//setAllBMUsFromMap
	//individual code to execute when a map node is added to the representation of the map - instancing class may have grouping structure 
	
	@Override
	//once map is built, find bmus on map for each product (target that training examples should map to)
	protected void setProductBMUs() {
		setProdDataBMUsRdyToSave(false);
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
					mapNodes = productData[i].findBMUFromFtrNodes(MapNodesByFtrIDX, productData[i]::getSqDistFromFtrType_ChiSq, curMapTestFtrType); 
					if(mapNodes == null) {getMsgObj().dispMessage("StraffSOMMapManager","setProductBMUs","ERROR!!! Product " + productData[i].OID + " does not have any features that map to Map Nodes in SOM!", MsgCodes.error5);}
					else {productData[i].setMapNodesStruct(SOMExample.AllFtrsIDX, mapNodes);}
					mapNodes = productData[i].findBMUFromFtrNodes(MapNodesByFtrIDX, productData[i]::getSqDistFromFtrType_ChiSq_Exclude, curMapTestFtrType); 
					if(mapNodes == null) {getMsgObj().dispMessage("StraffSOMMapManager","setProductBMUs","ERROR!!! Product " + productData[i].OID + " does not have any features that map to Map Nodes in SOM!", MsgCodes.error5);}
					else {productData[i].setMapNodesStruct(SOMExample.SharedFtrsIDX, mapNodes);}
				}
			} else {				
				for (int i=0;i<productData.length;++i) {
					mapNodes = productData[i].findBMUFromFtrNodes(MapNodesByFtrIDX, productData[i]::getSqDistFromFtrType, curMapTestFtrType); 
					if(mapNodes == null) {getMsgObj().dispMessage("StraffSOMMapManager","setProductBMUs","ERROR!!! Product " + productData[i].OID + " does not have any features that map to Map Nodes in SOM!", MsgCodes.error5);}
					else {productData[i].setMapNodesStruct(SOMExample.AllFtrsIDX, mapNodes);}
					mapNodes = productData[i].findBMUFromFtrNodes(MapNodesByFtrIDX, productData[i]::getSqDistFromFtrType_Exclude, curMapTestFtrType); 
					if(mapNodes == null) {getMsgObj().dispMessage("StraffSOMMapManager","setProductBMUs","ERROR!!! Product " + productData[i].OID + " does not have any features that map to Map Nodes in SOM!", MsgCodes.error5);}
					else {productData[i].setMapNodesStruct(SOMExample.SharedFtrsIDX, mapNodes);}
				}
			}	
		}
		//go through every product and attach prod to bmu - needs to be done synchronously because don't want to concurrently modify bmus from 2 different prods
		getMsgObj().dispMessage("StraffSOMMapManager","setProductBMUs","Finished finding bmus for all product data. Start adding product data to appropriate bmu's list.", MsgCodes.info1);
		_finalizeBMUProcessing(productData, ExDataType.Product);		
		setProdDataBMUsRdyToSave(true);
		getMsgObj().dispMessage("StraffSOMMapManager","setProductBMUs","Finished Mapping products to best matching units.", MsgCodes.info5);
	}//setProductBMUs

	//match true prospects to current map/product mappings
	public void buildAndSaveTrueProspectReport() {
		if (!getMapDataIsLoaded()) {	getMsgObj().dispMessage("StraffSOMMapManager","setTrueProspectBMUs","No SOM Map data has been loaded or processed; aborting", MsgCodes.error2);		return;}
		if (!exampleDataMappers.get("trueProspect").isDataLoaded()) {
			getMsgObj().dispMessage("StraffSOMMapManager","setTrueProspectBMUs","No true prospects loaded, attempting to load.", MsgCodes.info5);
			loadAllTrueProspectData();
		}	
		setValidationDataBMUs();
		getMsgObj().dispMessage("StraffSOMMapManager","setTrueProspectBMUs","Finished processing true prospects for BMUs.", MsgCodes.info1);	
	}//buildAndSaveTrueProspectReport
	
	//need to map true prospects and then save results
	//this will load the product IDs to query on map for prospects from the location specified in the config
	//map these ids to loaded products and then 
	//prodZoneDistThresh is distance threshold to determine outermost map region to be mapped to a specific product
	protected void loadMapProcAllData_Indiv(Double prodZoneDistThresh) {
		saveProductMappings(prodZoneDistThresh);		
	}
	
	//build feature-based segments on map - will overlap
	private final void buildJpSegmentsOnMap() {	
		if ((MapNodes == null) || (MapNodes.size() == 0)) {return;}
		getMsgObj().dispMessage("SOMMapManager","buildJpSegmentsOnMap","Started building Order JP-Segment-based cluster map", MsgCodes.info5);	
		//clear existing segments 
		for (SOMMapNode ex : MapNodes.values()) {((Straff_SOMMapNode) ex).clearJpSeg();}
		JP_Segments = new TreeMap<Integer, SOMMapSegment>();
		MapNodesWithOrderJPs = new TreeMap<Integer, Collection<SOMMapNode>>();
		Straff_JPOrderSegement jpSeg;
		Integer[] allTrainJPs = jpJpgrpMon.getTrainJpByIDXAra();
		
		for(int jpIdx = 0; jpIdx<allTrainJPs.length;++jpIdx) {
			Integer jp = allTrainJPs[jpIdx];
			//build 1 segment per jp idx
			jpSeg = new Straff_JPOrderSegement(this, jp);
			JP_Segments.put(jp,jpSeg);
			for(SOMMapNode ex : MapNodes.values()) {
				if(jpSeg.doesMapNodeBelongInSeg(ex)) {					jpSeg.addMapNodeToSegment(ex, MapNodes);		}//this does dfs to find neighbors who share feature value 	
			}
			MapNodesWithOrderJPs.put(jp, jpSeg.getAllMapNodes());
		}
		
		getMsgObj().dispMessage("SOMMapManager","buildJpSegmentsOnMap","Finished building Order JP-Segment-based cluster map", MsgCodes.info5);			
	}//buildFtrWtSegmentsOnMap

	//build feature-based segments on map - will overlap
	private final void buildJpGroupSegmentsOnMap() {		
		if ((MapNodes == null) || (MapNodes.size() == 0)) {return;}
		getMsgObj().dispMessage("SOMMapManager","buildJpGroupSegmentsOnMap","Started building Order JP Group-Segment-based cluster map", MsgCodes.info5);	
		//clear existing segments 
		for (SOMMapNode ex : MapNodes.values()) {((Straff_SOMMapNode) ex).clearJpGroupSeg();}
		JPGroup_Segments = new TreeMap<Integer, SOMMapSegment>();
		MapNodesWithOrderJPGroups = new TreeMap<Integer, Collection<SOMMapNode>>();
		Straff_JPGroupOrderSegment jpgSeg;
		Integer[] allTrainJPGroupss = this.jpJpgrpMon.getTrainJpgrpByIDXAra();
		
		for(int jpgIdx = 0; jpgIdx<allTrainJPGroupss.length;++jpgIdx) {
			Integer jpg = allTrainJPGroupss[jpgIdx];
			//build 1 segment per jpg idx
			jpgSeg = new Straff_JPGroupOrderSegment(this, jpg);
			JPGroup_Segments.put(jpg,jpgSeg);
			for(SOMMapNode ex : MapNodes.values()) {
				if(jpgSeg.doesMapNodeBelongInSeg(ex)) {					jpgSeg.addMapNodeToSegment(ex, MapNodes);		}//this does dfs to find neighbors who share feature value 	
			}	
			MapNodesWithOrderJPGroups.put(jpg, jpgSeg.getAllMapNodes());
		}

		getMsgObj().dispMessage("SOMMapManager","buildJpGroupSegmentsOnMap","Finished building Order JP Group-Segment-based cluster map", MsgCodes.info5);			
	}//buildFtrWtSegmentsOnMap

	
	//this will load the product IDs to query on map for prospects from the location specified in the config
	//map these ids to loaded products and then 
	//prodZoneDistThresh is distance threshold to determine outermost map region to be mapped to a specific product
	public void saveAllExamplesToSOMMappings(double prodZoneDistThresh, boolean saveCusts, boolean saveProspects) {
		if (!getMapDataIsLoaded()) {	getMsgObj().dispMessage("StraffSOMMapManager","saveExamplesToSOMMappings","No Mapped data has been loaded or processed; aborting", MsgCodes.error2);		return;}
		//if ((productMap == null) || (productMap.size() == 0)) {getMsgObj().dispMessage("StraffSOMMapManager","saveExamplesToSOMMappings","No products have been loaded or processed; aborting", MsgCodes.error2);		return;}
		
		getMsgObj().dispMessage("StraffSOMMapManager","saveExamplesToSOMMappings","Starting load of product to prospecting mapping configuration and building product output mapper", MsgCodes.info5);	

		saveProductMappings(prodZoneDistThresh);		
		if (saveCusts) {			saveTestTrainMappings(prodZoneDistThresh);		}
		if (saveProspects) {		saveValidationMappings(prodZoneDistThresh);	}		
	}//mapProductsToProspects
	
	public void buildProdMapper(double prodZoneDistThresh) {
		if(prodOutputMapper != null) { return;}
		//get file name of product mapper configuration file
		String prodMapFileName = projConfigData.getFullProdOutMapperInfoFileName();
		//builds the output mapper and loads the product IDs to map from config file
		prodOutputMapper = new StraffProdMapOutputBuilder(this, prodMapFileName,th_exec, getProdDistType(), prodZoneDistThresh);		
	}//buildProdMapper
	
	@Override
	public void saveProductMappings(double prodZoneDistThresh) {
		if(prodOutputMapper == null) {buildProdMapper(prodZoneDistThresh);}
		if (getProdDataBMUsRdyToSave()) {
			getMsgObj().dispMessage("StraffSOMMapManager","saveProductMappings","Finished load of product to prospecting mapping configuration and building product output mapper | Begin Saving prod-to-prospect mappings to files", MsgCodes.info1);	
			//by here all prods to map have been specified. prodMapBuilder will determine whether multithreaded or single threaded; 
			prodOutputMapper.saveAllSpecifiedProdMappings();		
			getMsgObj().dispMessage("StraffSOMMapManager","saveProductMappings","Finished Saving prod-to-prospect mappings to files.", MsgCodes.info1);
		} else {			_dispMappingNotDoneMsg("StraffSOMMapManager","saveProductMappings","Product");	}
	}//saveProductMappings
	
	@Override
	public void saveTestTrainMappings(double prodZoneDistThresh) {
		if(prodOutputMapper == null) {buildProdMapper(prodZoneDistThresh);}
		if((getTrainDataBMUsRdyToSave()) && (getTestDataBMUsRdyToSave())) {
			getMsgObj().dispMessage("StraffSOMMapManager","saveTestTrainMappings","Begin Saving customer-to-product mappings to files.", MsgCodes.info1);	
			//save customer-to-product mappings
			
			
			//prodOutputMapper.saveAllProspectToProdMappings(inputData, custExKey);
			
			
			getMsgObj().dispMessage("StraffSOMMapManager","saveTestTrainMappings","Finished Saving " + inputData.length + " customer-to-product mappings to files.", MsgCodes.info1);	
		} else {		_dispMappingNotDoneMsg("StraffSOMMapManager","saveTestTrainMappings","Test/Train (Customers)");		}		
	}//saveTestTrainMappings
	
	@Override
	public void saveValidationMappings(double prodZoneDistThresh) {
		if(prodOutputMapper == null) {buildProdMapper(prodZoneDistThresh);}
		if(getValidationDataBMUsRdyToSave()) {
			getMsgObj().dispMessage("StraffSOMMapManager","saveValidationMappings","Begin Saving prospect-to-product mappings to files.", MsgCodes.info1);	
			
			
			//prodOutputMapper.saveAllProspectToProdMappings(validationData, prspctExKey);
			
			
			getMsgObj().dispMessage("StraffSOMMapManager","saveValidationMappings","Finished Saving " + validationData.length + " true prospect-to-product mappings to files", MsgCodes.info5);
		} else {		_dispMappingNotDoneMsg("StraffSOMMapManager","saveValidationMappings","Validation (True Prospects)");  }		
	}//saveValidationMappings
	
	
	
	//debug - display current state of SOM_MapDat object describing SOM command line and execution
	public void dbgShowSOM_MapDat() {
		getMsgObj().dispMessage("StraffSOMMapManager","dbgShowSOM_MapDat","Starting displaying current SOM_MapDat object.", MsgCodes.info5);
		getMsgObj().dispMultiLineInfoMessage("StraffSOMMapManager","dbgShowSOM_MapDat","\n"+projConfigData.SOM_MapDat_ToString()+"\n");		
		getMsgObj().dispMessage("StraffSOMMapManager","dbgShowSOM_MapDat","End displaying current SOM_MapDat object.", MsgCodes.info5);
	}
	
	//debug - display spans of weights of all features in products after products are built
	public void dbgDispProductWtSpans() {
		//debug - display spans of weights of all features in products
		String[] prodExVals = ProductExample.getMinMaxDists();
		getMsgObj().dispMessageAra(prodExVals,"StraffSOMMapManager", "SOMMapManager::finishSOMExampleBuild : spans of all product ftrs seen", 1, MsgCodes.info1);		
	}//dbgDispProductWtSpans()
	
		
	@Override
	//this function will build the input data used by the SOM - this will be partitioned by some amount into test and train data (usually will use 100% train data, but may wish to test label mapping)
	protected SOMExample[] buildSOM_InputData() {
		SOMExample[] res;
		
		//res = custPrspctExMapper.buildExampleArray(true);
		//if using every order as training
		res = ((Straff_SOMCustPrspctPerOrderMapper)custPrspctExMapper).buildExampleArray(true);

		//return prospectExamples.get(custExKey).values().toArray(new SOMExample[0]);
		return res;
	}//buildSOMInputData
		
	@Override
	//using the passed map information, build the testing and training data partitions and save them to files
	protected void buildTestTrainFromPartition(float trainTestPartition) {
		getMsgObj().dispMessage("StraffSOMMapManager","buildTestTrainFromInput","Starting Building Input, Test, Train, Product data arrays.", MsgCodes.info5);
		//build array of product examples based on product map
		productData = (ProductExample[]) prodExMapper.buildExampleArray(false);
		setFlag(testTrainProdDataBuiltIDX,true);
		//set input data, shuffle it and set test and train partitions
		setInputTestTrainDataArasShuffle(trainTestPartition);
		
		//for(ProductExample prdEx : productData) {msgObj.dispMessage("StraffSOMMapManager","buildTestTrainFromInput",prdEx.toString());}
		getMsgObj().dispMessage("StraffSOMMapManager","buildTestTrainFromInput","Finished Building Input, Test, Train, Product data arrays.  Product data size : " +productData.length +".", MsgCodes.info5);
	}//buildTestTrainFromInput
	
	//this will set the current jp->jpg data maps based on examples in passed prospect data map and current products
	//This must be performed after all examples are loaded and finalized but before the feature vectors are calculated
	//due to the ftr calc requiring a knowledge of the entire dataset's jp-jpg membership to build ftr vectors appropriately
	private void _setJPDataFromExampleData(Straff_SOMCustPrspctPerOrderMapper custMapper) {
		//object to manage all jps and jpgroups seen in project
		jpJpgrpMon.setJPDataFromExampleData(custMapper, ((Straff_SOMTruePrspctMapper)exampleDataMappers.get(exampleDataMapperKeys[truePrspctIDX])), ((Straff_SOMProductMapper)exampleDataMappers.get(exampleDataMapperKeys[productIDX])));		
		setNumTrainFtrs(jpJpgrpMon.getNumTrainFtrs());
		numTtlJps = jpJpgrpMon.getNumAllJpsFtrs();
		//rebuild calc object since feature terrain might have changed 
		String calcFullFileName = projConfigData.getFullCalcInfoFileName(); 
		//make/remake calc object - reads from calcFullFileName data file
		ftrCalcObj = new StraffWeightCalc(this, calcFullFileName, jpJpgrpMon);
	}//setJPDataFromProspectData	
	
	private void _finalizeAllMappersBeforeFtrCalc() {
		getMsgObj().dispMessage("StraffSOMMapManager","_finalizeProsProdJpJPGMon","Begin finalize of main customer prospect map to aggregate all JPs and (potentially) determine which records are valid training examples", MsgCodes.info1);
		//finalize customers before feature calcs
		exampleDataMappers.get("custProspect").finalizeAllExamples();
		//finalize true prospects before feature calcs
		exampleDataMappers.get("trueProspect").finalizeAllExamples();
		//finalize products before feature calcs
		exampleDataMappers.get("product").finalizeAllExamples();		
	
		getMsgObj().dispMessage("StraffSOMMapManager","_finalizeProsProdJpJPGMon","Begin setJPDataFromExampleData from all examples.", MsgCodes.info1);
		//we need the jp-jpg counts and relationships dictated by the data by here.
		_setJPDataFromExampleData((Straff_SOMCustPrspctPerOrderMapper) exampleDataMappers.get("custProspect"));
		
		getMsgObj().dispMessage("StraffSOMMapManager","_finalizeProsProdJpJPGMon","End setJPDataFromExampleData from all examples.", MsgCodes.info1);
		getMsgObj().dispMessage("StraffSOMMapManager","_finalizeProsProdJpJPGMon","Finished finalize of main customer prospect map to aggregate all JPs and (potentially) determine which records are valid training examples", MsgCodes.info1);
	}//_finalizeAllMappersBeforeFtrCalc
		
	
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
	private void _handleTrueProspect(SOMExampleMapper truePrspctMapper,String OID, ProspectExample ex) {
		truePrspctMapper.addExampleToMap(OID,new TrueProspectExample(ex));
	}//handleTrueProspect
	
	
	//this function will take all raw loaded prospects and partition them into customers and true prospects
	//it determines what the partition/definition is for a "customer" which is used to train the map, and a "true prospect" which is polled against the map to find product membership.
	//typeOfEventsForCustomer : int corresponding to what kind of events define a customer and what defines a prospect.  
	//    0 : cust has order event, prospect does not but has source and possibly other events
	//    1 : cust has some non-source event, prospect does not have customer event but does have source event
	private void _buildCustomerAndProspectMaps(Straff_SOMCustPrspctPerOrderMapper tmpProspectMapper) {
		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps", "Start Mapping Raw Prospects to Customers and True Prospects", MsgCodes.info5);
		//whether or not to display total event membership counts across all examples to console
		boolean dispDebugEventMembership = true, dispDebugProgress = true;
		
		String prospectDesc = "";
		int numRecsToProc = tmpProspectMapper.getNumMapExamples();
		int typeOfEventsForCustomer = projConfigData.getTypeOfEventsForCustAndProspect();

		int[] countsOfBoolResOcc = new int[CustProspectExample.jpOccTypeKeys.length+1],
			countsOfBoolResEvt = new int[CustProspectExample.jpOccTypeKeys.length+1];		//all types of events supported + 1
		
		SOMExampleMapper custPrspctMapper = exampleDataMappers.get("custProspect");		custPrspctMapper.reset();
		SOMExampleMapper truePrspctMapper = exampleDataMappers.get("trueProspect");		truePrspctMapper.reset();
	
		int curEx=0, modSz = numRecsToProc/20, nonCustPrspctRecs = 0, noEventDataPrspctRecs = 0;		
		
		Set<String> keySet = tmpProspectMapper.getExampleKeySet();
		switch(typeOfEventsForCustomer) {
		case 0 : {		// cust has order event, prospect does not but has source and possibly other events
			prospectDesc = "Records that do not have any order events";
			for (String OID : keySet) {		
				ProspectExample ex = (ProspectExample) tmpProspectMapper.removeExampleFromMap(OID); 
				if(dispDebugEventMembership) {dbgEventInExample(ex, countsOfBoolResOcc, countsOfBoolResEvt);}
				if (ex.isTrainablePastCustomer()) {			custPrspctMapper.addExampleToMap(OID, ex);		} 			//training data - has valid feature vector and past order events
				else if (ex.isTrueProspect()) {				_handleTrueProspect(truePrspctMapper,OID, ex);	} 				//no past order events but has valid source event data
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
				ProspectExample ex = (ProspectExample) tmpProspectMapper.removeExampleFromMap(OID);
				if(dispDebugEventMembership) {dbgEventInExample(ex, countsOfBoolResOcc, countsOfBoolResEvt);}
				if (ex.hasNonSourceEvents()) {					custPrspctMapper.addExampleToMap(OID, ex);		} 			//training data - has valid feature vector and any non-source event data
				else if (ex.hasOnlySourceEvents()) {			_handleTrueProspect(truePrspctMapper,OID, ex);		} 				//only has source data
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
		//set state flags for these mappers
		custPrspctMapper.setAllDataLoaded();
		truePrspctMapper.setAllDataLoaded();
		//display customers and true prospects info
		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","Raw Records Unique OIDs presented : " + numRecsToProc, MsgCodes.info3);
		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","# True Prospect records (" + prospectDesc +") : " + truePrspctMapper.getNumMapExamples(), MsgCodes.info1);	
		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","# Customer Prospect records found with trainable event-based info : " + custPrspctMapper.getNumMapExamples(), MsgCodes.info3);
		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","# Raw Records that are neither true prospects nor customers but has events : " + nonCustPrspctRecs + " and with no events : "+ noEventDataPrspctRecs, MsgCodes.info3);

		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps", "Finished Mapping Raw Prospects to Customers and True Prospects", MsgCodes.info5);
	}//_buildCustomerAndProspectMaps
	

	@Override
	//build a map node that is formatted specifically for this project
	public SOMMapNode buildMapNode(Tuple<Integer,Integer>mapLoc, String[] tkns) {return new Straff_SOMMapNode(this,mapLoc, tkns);}
	
	/////////////////////////////////////////
	//drawing and graphics methods - these must check if win and/or pa exist, or else except win or pa as passed arguments, to manage when this code is executed without UI
	
	//TODO add array to this map manager holding map nodes keyed by jp and jpgroup and value being list of nodes with those jps/jpgs present
	
	
	//draw boxes around each node representing ftrwt-based segments that nodes belong to
	public final void drawOrderJPSegments(my_procApplet pa, int curJPIdx) {
		pa.pushMatrix();pa.pushStyle();
		Integer jp = jpJpgrpMon.getFtrJpByIdx(curJPIdx);
		Collection<SOMMapNode> mapNodes = MapNodesWithOrderJPs.get(jp);
		if(null==mapNodes) {return;}
		for (SOMMapNode node : mapNodes) {		((Straff_SOMMapNode) node).drawMeOrderJpSegClr(pa, jp);}
		
		pa.popStyle();pa.popMatrix();
	}//drawFtrWtSegments	
//	//draw boxes around every node representing ftrwt-based segments that nodes belong to
//	public final void drawAllOrderJPSegments(my_procApplet pa) {		
//		for(int curJPIdx=0;curJPIdx<PerFtrHiWtMapNodes.length;++curJPIdx) {		drawOrderJPSegments(pa, curJPIdx);	}		
//	}//drawFtrWtSegments
	
	
	//draw boxes around each node representing ftrwt-based segments that nodes belong to
	public final void drawOrderJPGroupSegments(my_procApplet pa, int curJPGroupIdx) {
		pa.pushMatrix();pa.pushStyle();
		Integer jpg = jpJpgrpMon.getFtrJpGroupByIdx(curJPGroupIdx);
		Collection<SOMMapNode> mapNodes = MapNodesWithOrderJPGroups.get(jpg);
		if(null==mapNodes) {return;}
		for (SOMMapNode node : mapNodes) {		((Straff_SOMMapNode) node).drawMeOrderJpGroupSegClr(pa, jpg);}
				
		pa.popStyle();pa.popMatrix();
	}//drawAllOrderJPGroupSegments
	
	
//	//draw boxes around every node representing ftrwt-based segments that nodes belong to
//	public final void drawAllOrderJPGroupSegments(my_procApplet pa) {		
//		for(int curJPGroupIdx=0;curJPGroupIdx<PerFtrHiWtMapNodes.length;++curJPGroupIdx) {		drawOrderJPGroupSegments(pa, curJPGroupIdx);	}		
//	}//drawFtrWtSegments

	
	//draw all product nodes with max vals corresponding to current JPIDX
	public void drawProductNodes(my_procApplet pa, int prodJpIDX, boolean showJPorJPG) {
		prodExMapper.drawProductNodes(pa, prodJpIDX,showJPorJPG);
	}//drawProductNodes	

	//show all products
	public void drawAllProductNodes(my_procApplet pa) {
		prodExMapper.drawAllProductNodes(pa);
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

	private int getProdDistType() {return (getFlag(mapExclProdZeroFtrIDX) ? ProductExample.SharedFtrsIDX : ProductExample.AllFtrsIDX);}
		//display the region of the map expected to be impacted by the products serving the passed jp 
	public void drawProductRegion(my_procApplet pa, int prodJpIDX, double maxDist) {	prodExMapper.drawProductRegion(pa,prodJpIDX, maxDist, getProdDistType());}//drawProductRegion
	
	//app-specific drawing routines for side bar
	protected void drawResultBarPriv1(my_procApplet pa, float yOff){
		
	}
	protected void drawResultBarPriv2(my_procApplet pa, float yOff){
		
	}
	protected void drawResultBarPriv3(my_procApplet pa, float yOff){}
	

	// end drawing routines
	//////////////////////////////////////////////////////
	
	@Override
	public ISOM_DispMapExample buildTmpDataExampleFtrs(myPointf ptrLoc, TreeMap<Integer, Float> ftrs, float sens) {return new Straff_SOMDispMapExample(this, ptrLoc, ftrs, sens);}
	@Override
	public ISOM_DispMapExample buildTmpDataExampleDists(myPointf ptrLoc, float dist, float sens) {return new Straff_SOMDispMapExample(this, ptrLoc, dist, sens);}
	
	//whether or not distances between two datapoints assume that absent features in smaller-length datapoints are 0, or to ignore the values in the larger datapoints
	@Override
	public void setMapExclZeroFtrs(boolean val) {setFlag(mapExclProdZeroFtrIDX, val);};
	@Override
	protected int getNumFlags() {	return numFlags;}
	@Override
	protected void setFlag_Indiv(int idx, boolean val){
		switch (idx) {//special actions for each flag
			case mapExclProdZeroFtrIDX : 	{break;}
			case prospectDataLoadedIDX: 	{break;}		//raw prospect data has been loaded but not yet processed
			case optDataLoadedIDX: 			{break;}				//raw opt data has been loaded but not processed
			case orderDataLoadedIDX: 		{break;}			//raw order data loaded not proced
			case linkDataLoadedIDX	: 		{break;}			//raw order data loaded not proced
			case sourceDataLoadedIDX	: 		{break;}			//raw order data loaded not proced
			case tcTagsDataLoadedIDX	: 		{break;}			//raw order data loaded not proced
			case jpDataLoadedIDX		: 		{break;}			//raw order data loaded not proced
			case jpgDataLoadedIDX		:		{break;}	
			case testTrainProdDataBuiltIDX : 	{break;}			//arrays of input, training and testing data built
			
		}
	}//setFlag		

	public boolean isFtrCalcDone(int idx) {return (ftrCalcObj != null) && ftrCalcObj.calcAnalysisIsReady(idx);}	

	public SOMExample getProductByID(String prodOID) {	return prodExMapper.getExample(prodOID);		}
	
	public String getAllJpStrByIdx(int idx) {return jpJpgrpMon.getAllJpStrByIdx(idx);}	
	public String getAllJpGrpStrByIdx(int idx) {return jpJpgrpMon.getAllJpGrpStrByIdx(idx);}
		
	public String getFtrJpStrByIdx(int idx) {return jpJpgrpMon.getFtrJpStrByIdx(idx);}	
	public String getFtrJpGrpStrByIdx(int idx) {return jpJpgrpMon.getFtrJpGrpStrByIdx(idx);}
		
	//this will return the appropriate jpgrp for the given jpIDX (list idx)
	public int getUI_JPGrpFromFtrJP(int jpIdx, int curVal) {		return jpJpgrpMon.getUI_JPGrpFromFtrJP(jpIdx, curVal);}
	//this will return the first(lowest) jp for a particular jpgrp
	public int getUI_FirstJPIdxFromFtrJPG(int jpgIdx, int curJPIdxVal) {	return jpJpgrpMon.getUI_FirstJPIdxFromFtrJPG(jpgIdx, curJPIdxVal);}	
	//this will return the appropriate jpgrp for the given jpIDX (list idx)
	public int getUI_JPGrpFromAllJP(int jpIdx, int curVal) {		return jpJpgrpMon.getUI_JPGrpFromAllJP(jpIdx, curVal);}
	//this will return the first(lowest) jp for a particular jpgrp
	public int getUI_FirstJPIdxFromAllJPG(int jpgIdx, int curJPIdxVal) {	return jpJpgrpMon.getUI_FirstJPIdxFromAllJPG(jpgIdx, curJPIdxVal);}	
	
	@Override
	//return appropriately pathed file name for map image of specified JP idx
	public String getSOMLocClrImgForFtrFName(int jpIDX) {
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
	
}//Straff_SOMMapManager
