package strafford_SOM_PKG.straff_SOM_Mapping;

import java.util.*;
import java.util.concurrent.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_segments.segments.SOM_MappedSegment;
import base_SOM_Objects.som_ui.SOM_UIToMapCom;
import base_SOM_Objects.som_ui.SOM_MseOvrDisplay;
import base_SOM_Objects.som_utils.SOM_ProjConfigData;

import base_UI_Objects.*;
import base_Utils_Objects.io.MessageObject;
import base_Utils_Objects.io.MsgCodes;
import base_Utils_Objects.vectorObjs.Tuple;

import strafford_SOM_PKG.straff_Features.*;
import strafford_SOM_PKG.straff_Features.featureCalc.Straff_WeightCalc;

import strafford_SOM_PKG.straff_RawDataHandling.Straff_SOMRawDataLdrCnvrtr;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.BaseRawData;

import strafford_SOM_PKG.straff_SOM_Examples.*;
import strafford_SOM_PKG.straff_SOM_Examples.products.ProductExample;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.*;

import strafford_SOM_PKG.straff_SOM_Mapping.exampleManagers.*;
import strafford_SOM_PKG.straff_SOM_Mapping.exampleManagers.base.Straff_SOMCustPrspctManager_Base;

import strafford_SOM_PKG.straff_SOM_Segments.Straff_SOM_NonProdJPClassSegment;
import strafford_SOM_PKG.straff_SOM_Segments.Straff_SOM_NonProdJPGCatSegment;

import strafford_SOM_PKG.straff_UI.Straff_SOMMapUIWin;

import strafford_SOM_PKG.straff_Utils.Straff_SOMMseOvrDisp;
import strafford_SOM_PKG.straff_Utils.Straff_SOMProjConfig;


//this class holds the data describing a SOM and the data used to both build and query the som
public class Straff_SOMMapManager extends SOM_MapManager {	
	//manage all jps and jpgs seen in project
	public MonitorJpJpgrp jpJpgrpMon;	
	//calc object to be used to derive feature vector for each prospect
	public Straff_WeightCalc ftrCalcObj;
	//object to manage all raw data processing
	private Straff_SOMRawDataLdrCnvrtr rawDataLdr;
	
	//whether or not there is enough ram to load all prospects at one time
	private boolean enoughRamToLoadAllProspects =false;
			
	////////////////////////////////////////////////////////////////////////////////////////////////
	//data descriptions
	//array of keys for exampleDataMappers map - one per type of data
	protected final String[] exampleDataMapperKeys = new String[] {	"custProspect","trueProspect","product"};
	protected static final int 
		custPrspctIDX = 0,
		truePrspctIDX = 1,
		productIDX = 2;
	
	//ref to cust prspct mapper
	private Straff_SOMCustPrspctManager_Base custPrspctExMapper;
	//ref to tru prospect mapper
	private Straff_SOMTruePrspctManager truePrspctExMapper;
	//ref to prod mapper
	private Straff_SOMProductManager prodExMapper;	
	
	//map of jpgroup idx and all map nodes that have non-zero presence in features(jps) that belong to that jpgroup
	protected TreeMap<Integer, HashSet<SOM_MapNode>> MapNodesByJPGroupIDX;
	
	//data for products to be measured on map
	private ProductExample[] productData;
	//maps of product arrays, with key for each map being either jpg or jp
	//private TreeMap<Integer, ArrayList<ProductExample>> productsByJpg, productsByJp;
	//total # of jps in all data, including source events
	private int numTtlJps;	
	
	//Map of non prod jps to training data membership segment
	protected TreeMap<Integer, SOM_MappedSegment> nonProdJP_Segments;
	//map with key being non prod jps and with value being collection of map nodes with that non prod jps present in mapped examples
	protected TreeMap<Integer,Collection<SOM_MapNode>> MapNodesWithMappedNonProdJPs;
	//probabilities for each non prod jps for each map node
	protected ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>> MapNodeNonProdJPsProbs;
	
	//map of non prod jp groups to training data membership segment
	protected TreeMap<Integer, SOM_MappedSegment> nonProdJpGroup_Segments;
	//map with key being non-product jpgroup and with value being collection of map nodes with that non-product jpgroup present in mapped examples
	protected TreeMap<Integer,Collection<SOM_MapNode>> MapNodesWithMappedNonProdJpGroup;
	//probabilities for each non-product jpgroup for each map node
	protected ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>> MapNodeNonProdJpGroupProbs;	
	
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
		testTrainProdDataBuiltIDX	= numBaseFlags + 9,			//product, input, testing and training data arrays have all been built
		//nature of training data
		custOrdersAsTrainDataIDX	= numBaseFlags + 10,		//whether or not the training data used is based on customer orders, or customer records
		//map products to SOM bmus - time consuming and only necessary if wishing to display products on map - Actual prospect mapping uses JP/JPGroup as key during mapping process
		mapProdsToBMUsIDX			= numBaseFlags + 11;
		
	public static final int numFlags = numBaseFlags + 12;	
	
	//////////////////////////////
	//# of records to save in preproc data csvs
	public static final int preProcDatPartSz = 50000;	
	
	private Straff_SOMMapManager(Straff_SOMMapUIWin _win, float[] _dims, TreeMap<String, Object> _argsMap) {
		super(_win,_dims, _argsMap);	
		//if there's enough ram to run all prospects at once
		if(_argsMap.get("enoughRamToLoadAllProspects") != null) {enoughRamToLoadAllProspects = (boolean) _argsMap.get("enoughRamToLoadAllProspects");}
		//object to manage all jps and jpgroups seen in project
		jpJpgrpMon = new MonitorJpJpgrp(this);
		//all raw data loading moved to this object
		rawDataLdr = new Straff_SOMRawDataLdrCnvrtr(this, projConfigData);	
		//default to using orders as training data - TODO make this a modifiable flag ?  
		setFlag(custOrdersAsTrainDataIDX,true);
		//default to mapping products to bmus - TODO make this modifiable/Set by call method?  
		//do not allow changing mapProdsToBMUsIDX during execution/via UI, might precipitate bad things that are difficult to debug
		setFlag(mapProdsToBMUsIDX, true);
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
		rebuildCustPrspctExMapper(getFlag(custOrdersAsTrainDataIDX));
		exampleDataMappers.put("trueProspect", new Straff_SOMTruePrspctManager(this, "trueProspect", "True Prospects (with no past orders)"));
		exampleDataMappers.put("product", new Straff_SOMProductManager(this, "product", "Products"));
		//ref to tru prospect mapper
		truePrspctExMapper = (Straff_SOMTruePrspctManager) exampleDataMappers.get("trueProspect");
		//ref to prod mapper
		prodExMapper = (Straff_SOMProductManager) exampleDataMappers.get("product");		
	}//buildExampleDataMappers
	
	public void rebuildCustPrspctExMapper(boolean isPerOrder){
		if((exampleDataMappers==null) || (exampleDataMappers.size()==0)) {return;}
		if(isPerOrder) {
			exampleDataMappers.put("custProspect", new Straff_SOMTrainExPerOrderManager(this, "custProspect", "Customer Prospects (with past orders)", true));
			//set custPrspctExMapper ref based on state of custOrdersAsTrainDataIDX flag
			custPrspctExMapper = (Straff_SOMTrainExPerOrderManager) exampleDataMappers.get("custProspect");
		} else {
			exampleDataMappers.put("custProspect", new Straff_SOMTrainExPerCustManager(this, "custProspect", "Customer Prospects (with past orders)", true));			
			//set custPrspctExMapper ref based on state of custOrdersAsTrainDataIDX flag
			custPrspctExMapper = (Straff_SOMTrainExPerCustManager) exampleDataMappers.get("custProspect");
		}		
	}//rebuildCustPrspctExMapper
	
	/**
	 * build instance-specific project file configuration 
	 */
	@Override
	protected SOM_ProjConfigData buildProjConfigData(TreeMap<String, Object> _argsMap) {				return new Straff_SOMProjConfig(this,_argsMap);	}
	
	/**
	 * build an interface to manage communications between UI and SOM map dat
	 * This interface will need to include a reference to an application-specific UI window
	 */
	@Override
	protected SOM_UIToMapCom buildSOM_UI_Interface() {	return new SOM_UIToMapCom(this, win);}	
	
	///////////////////////////////
	// manage conversion of raw data to preprocessed data
	
	/**
	 * Load and process raw data, and save results as preprocessed csvs
	 * @param fromCSVFiles : whether loading data from csv files or from SQL calls
	 * 
	 */
	@Override
	public void loadAndPreProcAllRawData(boolean fromCSVFiles) {
		getMsgObj().dispMessage("StraffSOMMapManager","loadAndPreProcAllRawData","Start loading and processing raw data", MsgCodes.info5);
		//this will load all raw data into memory from csv files or sql queries(todo)
		ConcurrentSkipListMap<String, ArrayList<BaseRawData>> _rawDataAras = rawDataLdr.loadAllRawData(fromCSVFiles);
		if(null==_rawDataAras) {		return;	}
		//process loaded data
		//dbgLoadedData(tcTagsIDX);
		getMsgObj().dispMessage("StraffSOMMapManager","loadAndPreProcAllRawData","Start Processing all loaded raw data", MsgCodes.info5);
		if (!(getFlag(prospectDataLoadedIDX) && getFlag(optDataLoadedIDX) && getFlag(orderDataLoadedIDX) && getFlag(tcTagsDataLoadedIDX))){//not all data loaded, don't process 
			getMsgObj().dispMessage("StraffSOMMapManager","loadAndPreProcAllRawData","Can't build data examples until raw data is all loaded", MsgCodes.warning2);			
		} else {			
			resetAllPreProcDataMappers();
			//build prospectMap - first get prospect data and add to map
			//ConcurrentSkipListMap<String, SOMExample> tmpProspectMap = new ConcurrentSkipListMap<String, SOMExample>();
			Straff_SOMTrainExPerOrderManager tmpProspectMapper = new Straff_SOMTrainExPerOrderManager(this, "custProspect","Customer Prospects (with past orders)", true);
			//data loader will build prospect and product maps
			
			rawDataLdr.procRawLoadedData(tmpProspectMapper, prodExMapper);		
			//finalize around temp map - finalize builds each example's occurrence structures, which describe the jp-jpg relationships found in the example
			//we perform this on the temp map to examine the processed examples so that we can determine which should be considered "customers" and which are "true prospects"
			//this determination is made based on the nature of the events each prospect has; finalize tmp mapper, product mapper and then jpjpg
			
			//finalize each customer in tmp mapper -  this will aggregate all the jp's that are seen and prepare example for calculating ftr vector
			tmpProspectMapper.finalizeAllExamples();				
			//finalize build for all products - aggregates all jps seen in product
			prodExMapper.finalizeAllExamples();		
			getMsgObj().dispMessage("StraffSOMMapManager","loadAndPreProcAllRawData","Raw Prospects and Products have been finalized to determine JPs and JPGroups present.", MsgCodes.info5);			
			
			//we need the jp-jpg counts and relationships dictated by the data by here.
			_setJPDataFromExampleData(tmpProspectMapper);			
			//build actual customer and validation maps using rules defining what is a customer (probably means having an order event) and what is a "prospect" (probably not having an order event)
			_buildCustomerAndProspectMaps(tmpProspectMapper);			
			//by here both prospect mappers have been appropriately populated			
			//finalize - recalc all processed data in case new products have different JP's present, set flags and save to file			

			getMsgObj().dispMessage("StraffSOMMapManager","loadAndPreProcAllRawData","Start loading calc object, calculating all feature vectors for prospects and products, calculating mins and diffs, and saving all results.", MsgCodes.info5);
				//condition data before saving it
			_finalizeAllMappersBeforeFtrCalc();
				//save all prospect, product and jpjpg monitor data - don't need to build ftr vectors here
			saveAllPreProcExamples();
			
			getMsgObj().dispMessage("StraffSOMMapManager","loadAndPreProcAllRawData","Finished preprocessing and saving all loaded raw data", MsgCodes.info5);
		}
		getMsgObj().dispMessage("StraffSOMMapManager","loadAndPreProcAllRawData","Finished loading raw data, processing and saving preprocessed data", MsgCodes.info5);
	}//loadAllRawData	
	
	//save MonitorJpJpgrp, construct that manages jp-jpgroup relationships (values and corresponding indexes in arrays)
	private void saveMonitorJpJpgrp() {
		getMsgObj().dispMessage("StraffSOMMapManager","saveMonitorJpJpgrp","Saving MonitorJpJpgrp data", MsgCodes.info5);
		String[] saveDestFNamePrefixAra = projConfigData.buildPreProccedDataCSVFNames_Save("MonitorJpJpgrpData");
		jpJpgrpMon.saveAllData(saveDestFNamePrefixAra[0],".csv");
		getMsgObj().dispMessage("StraffSOMMapManager","saveMonitorJpJpgrp","Finished saving MonitorJpJpgrp data", MsgCodes.info5);
	}//saveMonitorJpJpgrp
			
	//save all currently preprocced loaded data - customer and true prospects, products, and jpjpg monitor
	protected void saveAllPreProcExamples() {
		getMsgObj().dispMessage("StraffSOMMapManager","saveAllExamples","Begin Saving all Preproccessed Examples.", MsgCodes.info5);
			//save customer prospect examples
		boolean custPrspctSuccess = custPrspctExMapper.saveAllPreProccedMapData();
			//save true prospect examples
		boolean truePrspctSuccess = truePrspctExMapper.saveAllPreProccedMapData();
			//save products
		boolean prodSuccess = prodExMapper.saveAllPreProccedMapData();
		getMsgObj().dispMessage("StraffSOMMapManager","saveAllExamples","Finished Saving all Preproccessed Examples.", MsgCodes.info5);
		if (custPrspctSuccess || truePrspctSuccess || prodSuccess) { saveMonitorJpJpgrp();}		
	}//saveAllExamples
	
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
	private void _handleTrueProspect(SOM_ExampleManager truePrspctMapper,String OID, ProspectExample ex) {truePrspctMapper.addExampleToMap(OID,new TrueProspectExample(ex));	}//handleTrueProspect
		
	//this function will take all raw loaded prospects and partition them into customers and true prospects
	//it determines what the partition/definition is for a "customer" which is used to train the map, and a "true prospect" which is polled against the map to find product membership.
	//typeOfEventsForCustomer : int corresponding to what kind of events define a customer and what defines a prospect.  
	//    0 : cust has order event, prospect does not but has source and possibly other events
	//    1 : cust has some non-source event, prospect does not have customer event but does have source event
	private void _buildCustomerAndProspectMaps(Straff_SOMTrainExPerOrderManager tmpProspectMapper) {
		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps", "Start Mapping Raw Prospects to Customers and True Prospects", MsgCodes.info5);
		//whether or not to display total event membership counts across all examples to console
		boolean dispDebugEventMembership = true, dispDebugProgress = true;
		
		String prospectDesc = "";
		int numRecsToProc = tmpProspectMapper.getNumMapExamples();
		int typeOfEventsForCustomer = ((Straff_SOMProjConfig)projConfigData).getTypeOfEventsForCustAndProspect();

		int[] countsOfBoolResOcc = new int[CustProspectExample.jpOccTypeKeys.length+1],
			countsOfBoolResEvt = new int[CustProspectExample.jpOccTypeKeys.length+1];		//all types of events supported + 1

		custPrspctExMapper.reset();
		//SOMExampleMapper truePrspctMapper = exampleDataMappers.get("trueProspect");		truePrspctMapper.reset();
		
		truePrspctExMapper.reset();
		int curEx=0, modSz = numRecsToProc/20, nonCustPrspctRecs = 0, noEventDataPrspctRecs = 0;		
		
		Set<String> keySet = tmpProspectMapper.getExampleKeySet();
		switch(typeOfEventsForCustomer) {
		case 0 : {		// cust has order event, prospect does not but has source and possibly other events
			prospectDesc = "Records that do not have any order events";
			for (String OID : keySet) {		
				ProspectExample ex = (ProspectExample) tmpProspectMapper.removeExampleFromMap(OID); 
				if(dispDebugEventMembership) {dbgEventInExample(ex, countsOfBoolResOcc, countsOfBoolResEvt);}
				if (ex.isTrainablePastCustomer()) {			custPrspctExMapper.addExampleToMap(OID, ex);		} 			//training data - has valid feature vector and past order events
				else if (ex.isTrueProspect()) {				_handleTrueProspect(truePrspctExMapper,OID, ex);	} 				//no past order events but has valid source event data
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
				if (ex.hasNonSourceEvents()) {					custPrspctExMapper.addExampleToMap(OID, ex);		} 			//training data - has valid feature vector and any non-source event data
				else if (ex.hasOnlySourceEvents()) {			_handleTrueProspect(truePrspctExMapper,OID, ex);		} 				//only has source data
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
		custPrspctExMapper.setAllDataLoaded();
		truePrspctExMapper.setAllDataLoaded();
		//display customers and true prospects info
		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","Raw Records Unique OIDs presented : " + numRecsToProc, MsgCodes.info3);
		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","# True Prospect records (" + prospectDesc +") : " + truePrspctExMapper.getNumMapExamples(), MsgCodes.info1);	
		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","# Customer Prospect records found with trainable event-based info : " + custPrspctExMapper.getNumMapExamples(), MsgCodes.info3);
		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","# Raw Records that are neither true prospects nor customers but has events : " + nonCustPrspctRecs + " and with no events : "+ noEventDataPrspctRecs, MsgCodes.info3);

		getMsgObj().dispMessage("StraffSOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps", "Finished Mapping Raw Prospects to Customers and True Prospects", MsgCodes.info5);
	}//_buildCustomerAndProspectMaps	

	//set max display list values
//	public void setUI_JPFtrMaxVals(int jpGrpLen, int jpLen) {if (win != null) {((Straff_SOMMapUIWin)win).setUI_JPFtrListVals(jpGrpLen, jpLen);}}
//	public void setUI_JPAllSeenMaxVals(int jpGrpLen, int jpLen) {if (win != null) {((Straff_SOMMapUIWin)win).setUI_JPAllSeenListVals(jpGrpLen, jpLen);}}
	public void setUI_JPFtrListVals(String[] jpList, String[] jpGrpList) {if (win != null) {((Straff_SOMMapUIWin)win).setUI_JPFtrListVals(jpGrpList, jpList);}}
	public void setUI_JPAllSeenListVals(String[] jpList,String[] jpGrpList) {if (win != null) {((Straff_SOMMapUIWin)win).setUI_JPAllSeenListVals(jpGrpList, jpList);}}
	protected int _getNumSecondaryMaps(){return jpJpgrpMon.getLenFtrJpGrpByIdx();}
	
	//reset all data mappers used to manage preprocessed data
	protected void resetAllPreProcDataMappers() {	for(SOM_ExampleManager mapper : exampleDataMappers.values()) {	mapper.reset();		}}//resetAllMaps
	
	///////////////////////////////
	// end manage conversion of raw data to preprocessed data
	
	
	////////////////////////////////////
	// load required preprocessed data to train map
	
	//pass subdir within data directory, or use default
	@Override
	protected void loadPreProcTrainData(String subDir, boolean forceLoad) {	//preprocced data might be different than current true prospect data
		getMsgObj().dispMessage("StraffSOMMapManager","loadPreProcTrainData","Begin loading preprocced data from " + subDir +  "directory.", MsgCodes.info5);
			//load monitor first;save it last - keeps records of jps and jpgs even for data not loaded
		getMsgObj().dispMessage("StraffSOMMapManager","loadPreProcTrainData","Loading MonitorJpJpgrp data.", MsgCodes.info1);
		String[] loadSrcFNamePrefixAra = projConfigData.buildPreProccedDataCSVFNames_Load(subDir, "MonitorJpJpgrpData");
		jpJpgrpMon.loadAllData(loadSrcFNamePrefixAra[0],".csv");
		getMsgObj().dispMessage("StraffSOMMapManager","loadPreProcTrainData","Finished loading MonitorJpJpgrp data.", MsgCodes.info1);
			//display all jps and jpgs in currently loaded jp-jpg monitor
		getMsgObj().dispMultiLineMessage("StraffSOMMapManager","loadPreProcTrainData","Jp/Jp Group Profile of data : " + jpJpgrpMon.toString(), MsgCodes.info1);

			//load customer data
		if(!custPrspctExMapper.isDataPreProcced() || forceLoad) {			custPrspctExMapper.loadAllPreProccedMapData(subDir);}
		else {getMsgObj().dispMessage("StraffSOMMapManager","loadPreProcTrainData","Not loading preprocessed Customer Prospect examples since they are already loaded.", MsgCodes.info1);}
			//load preproc product data
		if(!prodExMapper.isDataPreProcced() || forceLoad) {					prodExMapper.loadAllPreProccedMapData(subDir);		}	
		else {getMsgObj().dispMessage("StraffSOMMapManager","loadPreProcTrainData","Not loading preprocessed Product examples since they are already loaded.", MsgCodes.info1);}
			//finalize and calc ftr vecs on customer prospects and products if we have loaded new data - don't build True Prospect feature vectors since they might not be loaded in synch with these files
		finishSOMExampleBuild(false);
			//preprocced data might be different than current true prospect data, so clear flag and reset map (clear out memory)
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllPreProccedData","Finished loading preprocced data from " + subDir +  "directory.", MsgCodes.info5);
	}//loadAllPreProccedData		
	
	public void loadAllTrueProspectData() {loadAllTrueProspectData(projConfigData.getPreProcDataDesiredSubDirName());}
	//load validation (true prospects) data found in subDir
	private void loadAllTrueProspectData(String subDir) {
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllTrueProspectData","Begin loading preprocessed True Prospect data from " + subDir +  "directory.", MsgCodes.info5);
			//load customers if not loaded
		loadPreprocAndBuildTestTrainPartitions(projConfigData.getTrainTestPartition(), false);
			//load true prospects
		truePrspctExMapper.loadAllPreProccedMapData(subDir);
			//process all true prospects, building their ftrs and making array of validation data
		procTrueProspectExamples();
		
		getMsgObj().dispMessage("StraffSOMMapManager","loadAllTrueProspectData","Finished loading preprocessed True Prospect data from " + subDir +  "directory and building validation data array of size : " + numValidationData+".", MsgCodes.info5);
	}//loadAllProspectData	
	
	/**
	 * process all true prospect examples and build Validation data array
	 */
	private void procTrueProspectExamples() {
		getMsgObj().dispMessage("StraffSOMMapManager","procTrueProspectExamples"," Begin initial finalize of true prospects map", MsgCodes.info1);	
		truePrspctExMapper.finalizeAllExamples();		
		getMsgObj().dispMessage("StraffSOMMapManager","procTrueProspectExamples","Finished initial finalize of true prospects map | Begin build feature vector for all true prospects.", MsgCodes.info1);	
		
		//customer prospects should be built first to specify bounds
		//since true prospects' data is largely subjective/non-behavior driven
		if(ftrCalcObj.custNonProdJpCalcIsDone()) {	
			truePrspctExMapper.buildFeatureVectors();
		} else {
			getMsgObj().dispMessage("StraffSOMMapManager","procTrueProspectExamples","Attempting to build true prospect ftr vectors without calculating the contribution from customers sharing same non-product jps.  Aborting.", MsgCodes.error1);	
		}
		getMsgObj().dispMessage("StraffSOMMapManager","procTrueProspectExamples","Finished build feature vector for all true prospects | Begin post feature vector build.", MsgCodes.info1);	
		truePrspctExMapper.buildAfterAllFtrVecsBuiltStructs();
		getMsgObj().dispMessage("StraffSOMMapManager","procTrueProspectExamples","Finished post feature vector build. | Begin assigning to Validation Data Array", MsgCodes.info1);	
			//build validation array
		buildValidationDataAra();
		getMsgObj().dispMessage("StraffSOMMapManager","procTrueProspectExamples","Finished post feature vector build. | Finished assigning to Validation Data Array : # Validation examples : " + validationData.length, MsgCodes.info1);	
	}//procTrueProspectExamples
	
	/////////////////////////////////////
	// load all data required to apply default map to specified products, customer and true prospects
	
	/**
	 * Use this method to map all prospects(cust and true) and products to existing map, and save mappings
	 * 1) load training data and products for map
	 * 2) load map data and derive map node bmus for prospects and products, building jp and jpg segments
	 * 3) load true prospects and map them to map via euclidean dists to map nodes to find their bmus
	 * 4) save all mappings 
	 */
	@Override
	public void loadAllDataAndBuildMappings() {
		MessageObject msgObj = getMsgObj();
		String subDir = projConfigData.getPreProcDataDesiredSubDirName();
		msgObj.dispMessage("StraffSOMMapManager","loadAllDataAndBuildMappings","Begin loading all preprocessed data from " + subDir +  "directory and building mappings.", MsgCodes.info1);
			
		msgObj.dispMessage("StraffSOMMapManager","loadAllDataAndBuildMappings","Loading MonitorJpJpgrp data.", MsgCodes.info1);
		String[] loadSrcFNamePrefixAra = projConfigData.buildPreProccedDataCSVFNames_Load(subDir, "MonitorJpJpgrpData");
			//load monitor first;save it last - keeps records of jps and jpgs even for data not loaded
		jpJpgrpMon.loadAllData(loadSrcFNamePrefixAra[0],".csv");		
		msgObj.dispMessage("StraffSOMMapManager","loadAllDataAndBuildMappings","Finished loading MonitorJpJpgrp data.", MsgCodes.info1);		
			//display all jps and jpgs in currently loaded jp-jpg monitor
		msgObj.dispMultiLineMessage("StraffSOMMapManager","loadAllDataAndBuildMappings","Jp/Jp Group Profile of data : " + jpJpgrpMon.toString(), MsgCodes.info1);	

		msgObj.dispMessage("StraffSOMMapManager","loadAllDataAndBuildMappings","Start loading Default SOM Map data.", MsgCodes.info1);		
			//current SOM map, if there is one, is now out of date, do not use
		setSOMMapNodeDataIsLoaded(false);			
			//load default pretrained map - for prebuilt map - load config used in prebuilt map including weight equation (override calc set in config - weight eq MUST always match trained map weight eq)
		boolean dfltmapLoaded = projConfigData.setSOM_UsePreBuilt();	
		if(!dfltmapLoaded) {
			msgObj.dispMessage("StraffSOMMapManager","loadAllDataAndBuildMappings","No Default map loaded, probably due to no default map directories specified in config file.  Aborting ", MsgCodes.info1);
			return;
		}
		msgObj.dispMessage("StraffSOMMapManager","loadAllDataAndBuildMappings","Finished loading Default SOM Map data. | Start loading customer, true prospect and product preproc data.", MsgCodes.info1);		
			//load customer data
		custPrspctExMapper.loadAllPreProccedMapData(subDir);
			//load preproc product data
		prodExMapper.loadAllPreProccedMapData(subDir);		
		
		msgObj.dispMessage("StraffSOMMapManager","loadAllDataAndBuildMappings","Finished load all preproc data | Begin build features, set mins/diffs and calc post-global-ftr-calc data.", MsgCodes.info1);	
			//build features, set mins and diffs, and build after-feature-values
		finishSOMExampleBuild(false);
		
		msgObj.dispMessage("StraffSOMMapManager","loadAllDataAndBuildMappings","Finished build features, set mins/diffs and calc post-global-ftr-calc data. | Start building train partitions and mapping training examples and products.", MsgCodes.info1);		
			//partition training and product data
		buildTrainTestFromPartition(projConfigData.getTrainTestPartition());
			//load map results to build SOMMapNode representation of map, and set training data bmus as reported by SOM Training code; also set product bmus if explicitly determined to do this via mapProdsToBMUsIDX flag
		loadMapAndBMUs();	
		
			//by here all map data is loaded and both training data and product data are mapped to BMUs
		msgObj.dispMessage("StraffSOMMapManager","loadAllDataAndBuildMappings","Finished building train partitions, loading Default SOM Map, and mapping training examples and products. | Start Saving Map Ftrs, Classes(JPs), Categories(JP groups) and customer and product BMU mappings.", MsgCodes.info1);
			//this will save the jp, jp group and ftr segment mappings from the bmus
		saveAllSegment_BMUReports();
			//map customers to bmus - only if customers were not used to train (i.e. if orders were used to train) - otherwise customers have been mapped already
		if(getFlag(custOrdersAsTrainDataIDX)) {
			//this oblitarates all past mappings of test data - shouldn't be a problem, test data is largely irrelevant except to test a map's performance on held out training data.
			SOM_Example[] custDataAra = custPrspctExMapper.getCustProspectExamples();
			_setExamplesBMUs(custDataAra, "CustProspect", SOM_ExDataType.Testing,testDataMappedIDX);
			//not really test data so clear flag
			setFlag(testDataMappedIDX, false);
		}
			//save customer mappings
		custPrspctExMapper.saveExampleBMUMappings();
			//save product to bmu mappings
		prodExMapper.saveExampleBMUMappings();
		
			//by here all map data is loaded, both training data and product data are mapped to BMUs, and the BMU data has been saved to file
		msgObj.dispMessage("StraffSOMMapManager","loadAllDataAndBuildMappings","Finished Saving Map Ftrs, Classes(JPs),Categories(JP groups) and customer and product BMU mappings. | Start Mapping true prospects to map and saving results.", MsgCodes.info1);
		
		if(enoughRamToLoadAllProspects) {
			//go through process all at once
			loadTrueProspectsAndMapToBMUs(subDir);
		} else {
			// process to go through validation data in chunks
			((Straff_SOMTruePrspctManager)truePrspctExMapper).loadPreProcMapBMUAndSaveMappings(subDir);
		}
		msgObj.dispMessage("StraffSOMMapManager","loadAllDataAndBuildMappings","Finished Mapping true prospects to map and saving results", MsgCodes.info1);			
		
		msgObj.dispMessage("StraffSOMMapManager","loadAllDataAndBuildMappings","Finished loading all preprocced example data, specified trained SOM, mapping all data to BMUs, and saving all mappings.", MsgCodes.info1);			
	}//loadAllDataAndBuildMappings
	
	/**
	 * This will load all trueprospects, map them to bmus and save the resulting 
	 * mappings - -this loads all data into mememory at one time, and should only 
	 * be performed if sufficient system ram exists
	 * @param subDir
	 */
	public void loadTrueProspectsAndMapToBMUs(String subDir) {
			//load true prospects
		truePrspctExMapper.loadAllPreProccedMapData(subDir);	
			//data is loaded here, now finalize before ftr calc
		truePrspctExMapper.finalizeAllExamples();
			//now build feature vectors
		truePrspctExMapper.buildFeatureVectors();	
			//build post-feature vectors - build STD vectors, build alt calc vec mappings
		truePrspctExMapper.buildAfterAllFtrVecsBuiltStructs();		
			//build array of trueProspectData used to map
		validationData = truePrspctExMapper.buildExampleArray();
			//set bmus for all validation data
		setValidationDataBMUs();
			//save all validation data example-to-bmus mappings
		saveExamplesToBMUMappings(this.validationData, truePrspctExMapper.exampleName, preProcDatPartSz);
		
	}//loadTrueProspectsAndMapToBMUs
	
	
	/**
	 * reload calc object data and recalc ftrs
	 */
	public void reCalcCurrFtrs() {
		getMsgObj().dispMessage("StraffSOMMapManager","reCalcCurrFtrs","Start loading calc object, calculating all feature vectors for prospects and products, calculating mins and diffs.", MsgCodes.info5);
		finishSOMExampleBuild(true);
		getMsgObj().dispMessage("StraffSOMMapManager","reCalcCurrFtrs","Finished loading calc object, calculating all feature vectors for prospects and products & calculating mins and diffs.", MsgCodes.info1);		
	}//reCalcCurrFtrs
	
					
	//finish building the prospect map - finalize each prospect example and then perform calculation to derive weight vector
	private void finishSOMExampleBuild(boolean buildTPIfExist) {
		getMsgObj().dispMessage("StraffSOMMapManager","finishSOMExampleBuild","Begin finalize mappers, calculate feature data, diffs, mins, and calculate post-global-ftr-data calcs.", MsgCodes.info5);
		//if((custPrspctExMapper.getNumMapExamples() != 0) || (prodExMapper.getNumMapExamples() != 0)) {
				//current SOM map, if there is one, is now out of date, do not use
		setSOMMapNodeDataIsLoaded(false);			
		boolean tpBldFtrSccs = false;
			//finalize customer prospects and products (and true prospects if they exist) - customers are defined by having criteria that enable their behavior to be used as to train the SOM		
		_finalizeAllMappersBeforeFtrCalc();
			//feature vector only corresponds to actual -customers- since this is what is used to build the map - build feature vector for customer prospects				
		boolean custBldFtrSuccess = custPrspctExMapper.buildFeatureVectors();	
		if(!custBldFtrSuccess) {getMsgObj().dispMessage("StraffSOMMapManager","finishSOMExampleBuild","Building Customer Prospect Feature vectors faileddue to above error (no data available).  Aborting - No features have been calculated for any prospect or product examples!", MsgCodes.error1);	return;	}
		
			//build/rebuild true prospects if there are any 
		if(buildTPIfExist) {
			tpBldFtrSccs = truePrspctExMapper.buildFeatureVectors();
			if(!tpBldFtrSccs) {getMsgObj().dispMessage("StraffSOMMapManager","finishSOMExampleBuild","Building True Prospect Feature vectors requested but failed due to above error (no data available).", MsgCodes.error1);	}	
		}
			//build features for products
		boolean prodBldFtrSuccess = prodExMapper.buildFeatureVectors();
		if(!prodBldFtrSuccess) {getMsgObj().dispMessage("StraffSOMMapManager","finishSOMExampleBuild","Building Product Feature vectors failed due to above error (no data available).", MsgCodes.error1);	}	
		
		getMsgObj().dispMessage("StraffSOMMapManager","finishSOMExampleBuild","Finished buildFeatureVectors | Begin calculating diffs and mins", MsgCodes.info1);	
			//now get mins and diffs from calc object
		//setMinsAndDiffs(ftrCalcObj.getMinBndsAra(), ftrCalcObj.getDiffsBndsAra());
		setMinsAndDiffs(ftrCalcObj.getMinTrainDataBndsAra(), ftrCalcObj.getDiffsTrainDataBndsAra());  
		
		getMsgObj().dispMessage("StraffSOMMapManager","finishSOMExampleBuild","Finished calculating diffs and mins | Begin building post-feature calc structs in prospects and products (i.e. std ftrs) dependent on diffs and mins", MsgCodes.info1);
		
			//now finalize post feature calc -this will do std features			
		custPrspctExMapper.buildAfterAllFtrVecsBuiltStructs();		
			//if specified then also build true prospect data
		if((buildTPIfExist) && (tpBldFtrSccs)){truePrspctExMapper.buildAfterAllFtrVecsBuiltStructs();}
			//build std features for products
		prodExMapper.buildAfterAllFtrVecsBuiltStructs();
		
		getMsgObj().dispMessage("StraffSOMMapManager","finishSOMExampleBuild","Finished finalize mappers, calculate feature data, diffs, mins, and calculate post-global-ftr-data calcs.", MsgCodes.info5);						
		//} else {	getMsgObj().dispMessage("StraffSOMMapManager","finishSOMExampleBuild","No prospects or products loaded to calculate/finalize.", MsgCodes.warning2);	}
	}//finishSOMExampleBuild	
	
	@Override
	//this function will build the input data used by the SOM - this will be partitioned by some amount into test and train data (usually will use 100% train data, but may wish to test label mapping)
	protected SOM_Example[] buildSOM_InputData() {
		SOM_Example[] res = custPrspctExMapper.buildExampleArray();	//cast to appropriate mapper when flag custOrdersAsTrainDataIDX is set
		String dispkStr = getFlag(custOrdersAsTrainDataIDX) ? 
				"Uses Customer orders for input/training data | Size of input data : " + res.length + " | # of customer prospects : " +  custPrspctExMapper.getNumMapExamples() + " | These should not be equal." :
				"Uses Customer Prospect records for input/training data | Size of input data : " + res.length + " | # of customer prospects : " +  custPrspctExMapper.getNumMapExamples() + " | These should be equal." ;
		getMsgObj().dispMessage("StraffSOMMapManager","buildSOM_InputData", dispkStr,MsgCodes.info5);

		return res;
	}//buildSOMInputData
		
	@Override
	//using the passed map information, build the testing and training data partitions and save them to files
	protected void buildTrainTestFromPartition(float trainTestPartition) {
		getMsgObj().dispMessage("StraffSOMMapManager","buildTestTrainFromInput","Starting Building Input, Test, Train, Product data arrays.", MsgCodes.info5);
		//build array of product examples based on product map
		productData = (ProductExample[]) prodExMapper.buildExampleArray();
		setFlag(testTrainProdDataBuiltIDX,true);
		//set input data, shuffle it and set test and train partitions
		setInputTrainTestShuffleDataAras(trainTestPartition);
		
		//for(ProductExample prdEx : productData) {msgObj.dispMessage("StraffSOMMapManager","buildTestTrainFromInput",prdEx.toString());}
		getMsgObj().dispMessage("StraffSOMMapManager","buildTestTrainFromInput","Finished Building Input, Test, Train, Product data arrays.  Product data size : " +productData.length +".", MsgCodes.info5);
	}//buildTestTrainFromInput
	
	//return a map of descriptive quantities and their values, for the SOM Execution human-readable report
	@Override
	public TreeMap<String, String> getSOMExecInfo(){
		TreeMap<String, String> res = new TreeMap<String, String>();		
		for(String key : exampleDataMapperKeys) {
			SOM_ExampleManager mapper = exampleDataMappers.get(key);
			res.put(mapper.longExampleName + " date and time of creation/pre-processing", mapper.dateAndTimeOfDataCreation());		
		}
		res.put("Number of training (product-present) jps", ""+jpJpgrpMon.getNumTrainFtrs());
		res.put("Training Data JPs (in feature idx order)\n", jpJpgrpMon.getFtrJpsAsCSV()+"\n");
		res.put("Total number of jps seen across all prospects and products", ""+jpJpgrpMon.getNumAllJpsFtrs());

		return res;		
	}//getSOMExecInfo
	
	//this will set the current jp->jpg data maps based on examples in passed prospect data map and current products
	//This must be performed after all examples are loaded and finalized but before the feature vectors are calculated
	//due to the ftr calc requiring a knowledge of the entire dataset's jp-jpg membership to build ftr vectors appropriately
	private void _setJPDataFromExampleData(Straff_SOMCustPrspctManager_Base custMapper) {
		//object to manage all jps and jpgroups seen in project
		jpJpgrpMon.setJPDataFromExampleData(custMapper, truePrspctExMapper, prodExMapper);		
		setNumTrainFtrs(jpJpgrpMon.getNumTrainFtrs()); 
		numTtlJps = jpJpgrpMon.getNumAllJpsFtrs();
		//rebuild calc object since feature terrain might have changed 
		String calcFullFileName = ((Straff_SOMProjConfig)projConfigData).getFullCalcInfoFileName(); 
		//make/remake calc object - reads from calcFullFileName data file
		ftrCalcObj = new Straff_WeightCalc(this, calcFullFileName, jpJpgrpMon);
	}//setJPDataFromProspectData	
	
	protected void _finalizeAllMappersBeforeFtrCalc() {
		getMsgObj().dispMessage("StraffSOMMapManager","_finalizeProsProdJpJPGMon","Begin finalize of main customer prospect map to aggregate all JPs and (potentially) determine which records are valid training examples", MsgCodes.info1);
		//finalize customers before feature calcs
		custPrspctExMapper.finalizeAllExamples();
		//finalize true prospects before feature calcs
		truePrspctExMapper.finalizeAllExamples();
		//finalize products before feature calcs
		prodExMapper.finalizeAllExamples();		
	
		getMsgObj().dispMessage("StraffSOMMapManager","_finalizeProsProdJpJPGMon","Begin setJPDataFromExampleData from all examples.", MsgCodes.info1);
		//we need the jp-jpg counts and relationships dictated by the data by here.
		_setJPDataFromExampleData(custPrspctExMapper);
		
		getMsgObj().dispMessage("StraffSOMMapManager","_finalizeProsProdJpJPGMon","Finished setJPDataFromExampleData from all examples.", MsgCodes.info1);
		getMsgObj().dispMessage("StraffSOMMapManager","_finalizeProsProdJpJPGMon","Finished finalize of main customer prospect map to aggregate all JPs and (potentially) determine which records are valid training examples", MsgCodes.info1);
	}//_finalizeAllMappersBeforeFtrCalc

	/**
	 * build the array of validation data - these must always be prospects, keyed by OID.
	 * If the order-based data is used as training data, then validation data ara must include 
	 * both full customer and true prospect records
	 */ 
	@Override
	protected void buildValidationDataAra() {
		if(getFlag(custOrdersAsTrainDataIDX)) {
			//this is if customers orders were used as training data
			//this will build the validation data array that will either be only the true prospects data, or else the true prospects and the customer prospects data
			//depending on whether customers or orders were used for training data, respectively
			SOM_Example[] tmpTruePrspctsAra = truePrspctExMapper.buildExampleArray(), 
					tmpCustPrspctsAra = custPrspctExMapper.getCustProspectExamples();		//regardless of training data, this will return array of -cust prospect examples-
			validationData = new SOM_Example[tmpTruePrspctsAra.length + tmpCustPrspctsAra.length];
			System.arraycopy(tmpTruePrspctsAra, 0, validationData, 0, tmpTruePrspctsAra.length);
			System.arraycopy(tmpCustPrspctsAra, 0, validationData, tmpTruePrspctsAra.length, tmpCustPrspctsAra.length);		
			numValidationData = validationData.length;
		} else {//if using customers to train then only use true prospects as validation
			//build array of trueProspectData used to map
			validationData = truePrspctExMapper.buildExampleArray();
			//this is if orders were used as training data			
		}		
	}//buildValidationData() 
	
	///////////////////////////
	// build and manage mapNodes 
	@Override
	//any instance-class specific code to execute when new map nodes are being loaded
	protected void initMapNodesPriv() {
		//map of jpgroup idx and all map nodes that have non-zero presence in features(jps) that belong to that jpgroup
		MapNodesByJPGroupIDX = new TreeMap<Integer, HashSet<SOM_MapNode>>();
		
		//Map of non prod jps to training data membership segment
		nonProdJP_Segments = new TreeMap<Integer, SOM_MappedSegment>();
		MapNodesWithMappedNonProdJPs = new TreeMap<Integer, Collection<SOM_MapNode>>();
		MapNodeNonProdJPsProbs = new ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>>();

		//map of non prod jp groups to training data membership segment
		nonProdJpGroup_Segments = new TreeMap<Integer, SOM_MappedSegment>();
		MapNodesWithMappedNonProdJpGroup = new TreeMap<Integer, Collection<SOM_MapNode>>();
		MapNodeNonProdJpGroupProbs = new ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>>();
		
	}//initMapNodesPriv
	@Override
	//build a map node that is formatted specifically for this project
	public SOM_MapNode buildMapNode(Tuple<Integer,Integer>mapLoc, String[] tkns) {return new Straff_SOMMapNode(this,mapLoc, tkns);}	
	
	///////////////////////////
	// end build and manage mapNodes 
	
	@Override
	/**
	 * called from map as bmus after loaded and training data bmus are set from bmu file
	 */
	public void setAllBMUsFromMap() {
		//make sure jp and jpgroup segments are built before mapping products and testing/training data
		//build jp segments from mapped training examples
		buildClassSegmentsOnMap();
		//build jpgroup segments from mapped training examples
		buildCategorySegmentsOnMap();
		//build segments for non-prod jps 
		buildNonProdSegmentsOnMap();
		//build segments for non-prod jp groups
		buildNonProdJpGroupSegmentsOnMap();
		
		if(getFlag(mapProdsToBMUsIDX)) {	setProductBMUs();	}	//map products to bmus if using them
		//map test data to bmus if any exists 
		setTestBMUs();
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
			List<Straff_MapProductDataToBMUs> prdcttMappers = new ArrayList<Straff_MapProductDataToBMUs>();
			int numForEachThrd = calcNumPerThd(productData.length, numUsableThreads);// ((int)((productData.length-1)/(1.0f*numUsableThreads))) + 1;
			//use this many for every thread but last one
			int stIDX = 0;
			int endIDX = numForEachThrd;				
			for (int i=0; i<(numUsableThreads-1);++i) {				
				prdcttMappers.add(new Straff_MapProductDataToBMUs(this,stIDX, endIDX, productData, i, useChiSqDist));
				stIDX = endIDX;
				endIDX += numForEachThrd;
			}
			//last one probably won't end at endIDX, so use length
			if(stIDX < productData.length) {prdcttMappers.add(new Straff_MapProductDataToBMUs(this,stIDX, productData.length, productData, numUsableThreads-1, useChiSqDist));}
			
			try {prdcttMapperFtrs = th_exec.invokeAll(prdcttMappers);for(Future<Boolean> f: prdcttMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		
			//go through every product and attach prod to bmu - needs to be done synchronously because don't want to concurrently modify bmus from 2 different prods
			getMsgObj().dispMessage("StraffSOMMapManager","setProductBMUs","Finished finding bmus for all product data. Start adding product data to appropriate bmu's list.", MsgCodes.info1);
			_completeBMUProcessing(productData, SOM_ExDataType.Product, true);		
		
		} else {//for every product find closest map node
			//perform single threaded version - execute synchronously
			Straff_MapProductDataToBMUs mapper = new Straff_MapProductDataToBMUs(this,0, productData.length, productData, 0, useChiSqDist);
			mapper.call();
			//go through every product and attach prod to bmu - needs to be done synchronously because don't want to concurrently modify bmus from 2 different prods
			getMsgObj().dispMessage("StraffSOMMapManager","setProductBMUs","Finished finding bmus for all product data. Start adding product data to appropriate bmu's list.", MsgCodes.info1);
			_completeBMUProcessing(productData, SOM_ExDataType.Product, false);		
		}
		setProdDataBMUsRdyToSave(true);
		getMsgObj().dispMessage("StraffSOMMapManager","setProductBMUs","Finished Mapping products to best matching units.", MsgCodes.info5);
	}//setProductBMUs

	//match true prospects to current map/product mappings
	public void buildAndSaveTrueProspectReport() {
		if (!getSOMMapNodeDataIsLoaded()) {	getMsgObj().dispMessage("StraffSOMMapManager","setTrueProspectBMUs","No SOM Map data has been loaded or processed; aborting", MsgCodes.error2);		return;}
		if (!truePrspctExMapper.isDataLoaded()) {
			getMsgObj().dispMessage("StraffSOMMapManager","setTrueProspectBMUs","No true prospects loaded, attempting to load.", MsgCodes.info5);
			loadAllTrueProspectData();
		}	
		setValidationDataBMUs();
		getMsgObj().dispMessage("StraffSOMMapManager","setTrueProspectBMUs","Finished processing true prospects for BMUs.", MsgCodes.info1);	
	}//buildAndSaveTrueProspectReport
	
	///////////////////////////////
	// segment reports and saving
	
	//this will load the product IDs to query on map for prospects from the location specified in the config
	//map these ids to loaded products and then 
	//prodZoneDistThresh is distance threshold to determine outermost map region to be mapped to a specific product
	public void saveAllExamplesToSOMMappings() {
		if (!getSOMMapNodeDataIsLoaded()) {	getMsgObj().dispMessage("StraffSOMMapManager","saveExamplesToSOMMappings","No Mapped data has been loaded or processed; aborting", MsgCodes.error2);		return;}		
		getMsgObj().dispMessage("StraffSOMMapManager","saveExamplesToSOMMappings","Starting saving all segment data for Classes, Categories and BMUs.", MsgCodes.info5);	
		saveAllSegment_BMUReports();
		getMsgObj().dispMessage("StraffSOMMapManager","saveExamplesToSOMMappings","Finished saving all segment data for Classes, Categories and BMUs | Start saving all example->bmu mappings.", MsgCodes.info5);			
		//send all validation data to have bmus mapped
		saveExamplesToBMUMappings(this.validationData, "Validation Data", preProcDatPartSz);
		getMsgObj().dispMessage("StraffSOMMapManager","saveExamplesToSOMMappings","Finished saving all example->bmu mappings.", MsgCodes.info5);	
	}//saveAllExamplesToSOMMappings	

	@Override
	public String getFtrWtSegmentTitleString(int ftrCalcType, int ftrIDX) {		
		String ftrTypeDesc = getDataDescFromInt(ftrCalcType);
		return "Feature Weight Segment using " + ftrTypeDesc +" examples for ftr idx : " + ftrIDX+ " corresponding to JP :"+ jpJpgrpMon.getFtrJpByIdx(ftrIDX);
	}

	@Override
	public String getClassSegmentTitleString(int classID) {	return "Job Practice, Probability (Class), Segment of training data mapped to node possessing orders in specified JP  : " + classID + " | "+ jpJpgrpMon.getFtrJpStrByJp(classID);}
	@Override
	public String getCategorySegmentTitleString(int catID) {return "Job Practice, Group (Category), Probability Segment of training data mapped to node possessing orders in specified JP Group  : " + catID + " | "+ jpJpgrpMon.getFtrJpGrpStrByJpg(catID);}	
	
	public String getNonProdJPSegmentTitleString(int npJpID) {return "Job Practice, Probability Segment, of training data mapped to node possessing Non-product-related JP  : " + npJpID + " | "+ jpJpgrpMon.getAllJpStrByJp(npJpID);}
	
	public String getNonProdJPGroupSegmentTitleString(int npJpgID) {return "Job Practice, Group Probability, Segment of training data mapped to node possessing Non-product-related JPgroup  : " + npJpgID + " | "+ jpJpgrpMon.getAllJpGrpStrByJpg(npJpgID);}
	
	public ConcurrentSkipListMap<Tuple<Integer,Integer>, Float> getMapNodeJPProbsForJP(Integer jp){return MapNodeClassProbs.get(jp);}		
	public ConcurrentSkipListMap<Tuple<Integer,Integer>, Float> getMapNodeJPGroupProbsForJPGroup(Integer jpg){return MapNodeCategoryProbs.get(jpg);}	
	
	/**
	 * return the class labels used for the classification of training examples to 
	 * their bmus.  bmus then represent a probability distribution of class membership
	 * @return
	 */
	@Override
	protected final Integer[] getAllClassLabels() {	return jpJpgrpMon.getFtrJpByIDXAra();}
	@Override
	protected final String getClassSegMappingDescrStr() {	return "Order JP-Segment-based cluster map";}
	/**
	 * return the category labels used for the classification of training examples to 
	 * their bmus.  bmus then represent a probability distribution of category membership
	 * @return
	 */
	@Override
	protected final Integer[] getAllCategoryLabels() {	return jpJpgrpMon.getFtrJpgrpByIDXAra();}
	@Override
	protected final String getCategorySegMappingDescrStr() {	return "Order JP Group-Segment-based cluster map";}
	
	
	protected final void buildNonProdSegmentsOnMap() {	
		if ((MapNodes == null) || (MapNodes.size() == 0)) {return;}
		getMsgObj().dispMessage("Straff_SOMMapManager","buildNonProdSegmentsOnMap","Started building Non-product JP-Segment-based cluster map", MsgCodes.info5);	
		//clear existing segments 
		for (SOM_MapNode ex : MapNodes.values()) {((Straff_SOMMapNode)ex).clearNonProdJPSeg();}
		nonProdJP_Segments.clear();
		MapNodesWithMappedNonProdJPs.clear();
		MapNodeNonProdJPsProbs.clear();
		
		ConcurrentSkipListMap<Tuple<Integer,Integer>, Float> tmpMapOfNodeProbs = new ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>();
		Straff_SOM_NonProdJPClassSegment npJpSeg;
		//must be NON PROD Jps
		Integer[] allNonProdJPs = jpJpgrpMon.getNonProductJpByIDXAra();
		if(allNonProdJPs.length == 0) {
			getMsgObj().dispMessage("Straff_SOMMapManager","buildNonProdSegmentsOnMap","Non Product Jp List is currently not being built in jpJpgrpMon.getNonProductJpByIDXAra(), so no segments are being built.", MsgCodes.info5);	
			return;
		}		
		//check every map node for every jp to see if it has class membership
		for(int npJpIdx = 0; npJpIdx<allNonProdJPs.length;++npJpIdx) {
			Integer npjp = allNonProdJPs[npJpIdx];
			//build 1 segment per jp idx
			npJpSeg = new Straff_SOM_NonProdJPClassSegment(this, npjp);
			nonProdJP_Segments.put(npjp,npJpSeg);
			for(SOM_MapNode ex : MapNodes.values()) {
				if(npJpSeg.doesMapNodeBelongInSeg(ex)) {					npJpSeg.addMapNodeToSegment(ex, MapNodes);		}//this does dfs to find neighbors who share feature value 	
			}
			Collection<SOM_MapNode> mapNodesForJp = npJpSeg.getAllMapNodes();
			MapNodesWithMappedNonProdJPs.put(npjp, mapNodesForJp);
			//getMsgObj().dispMessage("Straff_SOMMapManager","buildClassSegmentsOnMap","JP : " + jp + " has " + mapNodesForJp.size()+ " map nodes in its segment.", MsgCodes.info5);			
			tmpMapOfNodeProbs = new ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>();
			for(SOM_MapNode mapNode : mapNodesForJp) {		tmpMapOfNodeProbs.put(mapNode.mapNodeCoord, ((Straff_SOMMapNode)mapNode).getNonProdJPProb(npjp));}
			MapNodeNonProdJPsProbs.put(npjp, tmpMapOfNodeProbs);
		}
		getMsgObj().dispMessage("Straff_SOMMapManager","buildNonProdSegmentsOnMap","Finished building Non-product JP-Segment-based cluster map : " + MapNodesWithMappedNonProdJPs.size()+" jps have map nodes mapped to them.", MsgCodes.info5);			
	}//buildFtrWtSegmentsOnMap	
	public ConcurrentSkipListMap<Tuple<Integer,Integer>, Float> getMapNodeNonProdJpProbsForJP(Integer jp){return MapNodeNonProdJPsProbs.get(jp);}
	
	//build segments for non-product jp groups 
	protected final void buildNonProdJpGroupSegmentsOnMap() {		
		if ((MapNodes == null) || (MapNodes.size() == 0)) {return;}
		getMsgObj().dispMessage("Straff_SOMMapManager","buildNonProdJpGroupSegmentsOnMap","Started building Non-product JP Group-Segment-based cluster map", MsgCodes.info5);	
		//clear existing segments 
		for (SOM_MapNode ex : MapNodes.values()) {((Straff_SOMMapNode)ex).clearNonProdJpGroupSeg();}
		nonProdJpGroup_Segments.clear();
		MapNodesWithMappedNonProdJpGroup.clear();
		MapNodeNonProdJpGroupProbs.clear();
		
		ConcurrentSkipListMap<Tuple<Integer,Integer>, Float> tmpMapOfNodeProbs = new ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>();
		Straff_SOM_NonProdJPGCatSegment jpgSeg;		
		//THIS MUST BE NONProduct JP Groups
		Integer[] allNonProdJpGroups = jpJpgrpMon.getNonProductJpGroupByIDXAra();
		if(allNonProdJpGroups.length == 0) {
			getMsgObj().dispMessage("Straff_SOMMapManager","buildNonProdJpGroupSegmentsOnMap","Non Product Jp Group List is currently not being built in jpJpgrpMon.getNonProductJpGroupByIDXAra(), so no segments are being built.", MsgCodes.info5);	
			return;
		}
		
		for(int npJpgIdx = 0; npJpgIdx<allNonProdJpGroups.length;++npJpgIdx) {
			Integer npjpg = allNonProdJpGroups[npJpgIdx];
			//build 1 segment per jpg idx
			jpgSeg = new Straff_SOM_NonProdJPGCatSegment(this, npjpg);
			nonProdJpGroup_Segments.put(npjpg,jpgSeg);
			for(SOM_MapNode ex : MapNodes.values()) {
				if(jpgSeg.doesMapNodeBelongInSeg(ex)) {					jpgSeg.addMapNodeToSegment(ex, MapNodes);		}//this does dfs to find neighbors who share feature value 	
			}	
			Collection<SOM_MapNode> mapNodesForJpg = jpgSeg.getAllMapNodes();
			MapNodesWithMappedNonProdJpGroup.put(npjpg, mapNodesForJpg);
			//getMsgObj().dispMessage("Straff_SOMMapManager","buildNonProdJpGroupSegmentsOnMap","Non Product JPgroup : " + jpg + " has " + mapNodesForJpg.size()+ " map nodes in its segment.", MsgCodes.info5);
			tmpMapOfNodeProbs = new ConcurrentSkipListMap<Tuple<Integer,Integer>, Float>();
			for(SOM_MapNode mapNode : mapNodesForJpg) {	tmpMapOfNodeProbs.put(mapNode.mapNodeCoord,((Straff_SOMMapNode)mapNode).getNonProdJpGroupProb(npjpg));	}
			MapNodeNonProdJpGroupProbs.put(npjpg, tmpMapOfNodeProbs);
		}

		getMsgObj().dispMessage("Straff_SOMMapManager","buildNonProdJpGroupSegmentsOnMap","Finished building Non-product JP Group-Segment-based cluster map : " + MapNodesWithMappedNonProdJpGroup.size()+" jp groups have map nodes mapped to them.", MsgCodes.info5);	
	}//buildFtrWtSegmentsOnMap
	public ConcurrentSkipListMap<Tuple<Integer,Integer>, Float> getMapNodeNonProdJPGroupProbsForJPGroup(Integer jpg){return MapNodeNonProdJpGroupProbs.get(jpg);}	
	
	@Override
	protected void saveAllSegment_BMUReports_Indiv() {
		//these are implemented but not yet determined to be necessary so don't use
		//saveNonProdJp_BMUReport();
		//saveNonProdJpGroup_BMUReport();
	}
	//save non-prod-jp segment information
	private void saveNonProdJp_BMUReport(){		_saveSegmentReports(nonProdJP_Segments,projConfigData.getSegmentFileNamePrefix("nonprod_jps",""));}
	//save non-prod-jpgroup segment information
	private void saveNonProdJpGroup_BMUReport(){		_saveSegmentReports(nonProdJpGroup_Segments,projConfigData.getSegmentFileNamePrefix("nonprod_jpgroups",""));}
	

	/////////////////////////////////////////
	//drawing and graphics methods - these must check if win and/or pa exist, or else except win or pa as passed arguments, to manage when this code is executed without UI
	/**
	 * draw boxes around each node representing class-based segments that node 
	 * belongs to, with color strength proportional to probablity and 
	 * different colors for each segment
	 * pass class -label- not class index
	 * @param pa
	 * @param classLabel - label corresponding to class to be displayed
	 */
	public void drawNonProdJpSegments(my_procApplet pa, int nonProdJpLabel) {
		//Integer jpg = jpJpgrpMon.getFtrJpGroupByIdx(curJPGroupIdx);
		Collection<SOM_MapNode> mapNodes = MapNodesWithMappedNonProdJPs.get(nonProdJpLabel);
		if(null==mapNodes) {return;}
		pa.pushMatrix();pa.pushStyle();
		for (SOM_MapNode node : mapNodes) {		((Straff_SOMMapNode)node).drawMeNonProdJPSegClr(pa, nonProdJpLabel);}				
		pa.popStyle();pa.popMatrix();
	}
	public final void drawAllNonProdJpSegments(my_procApplet pa) {	
		//getMsgObj().dispMessage("StraffSOMMapManager","drawAllNonProdJpSegments","Drawing "+nonProdJP_Segments.size()+" nonprod jp segments", MsgCodes.info5);
		for(Integer key : nonProdJP_Segments.keySet()) {	drawNonProdJpSegments(pa,key);}	}

	/**
	 * draw filled boxes around each node representing non-product-jpgroup-based segments 
	 * that node belongs to, with color strength proportional to probablity 
	 * and different colors for each segment
	 * pass class -label- not class index
	 * @param pa
	 * @param classLabel - label corresponding to class to be displayed
	 */
	
	public final void drawNonProdJPGroupSegments(my_procApplet pa, int npJpGroupLabel) {
		Collection<SOM_MapNode> mapNodes = MapNodesWithMappedNonProdJpGroup.get(npJpGroupLabel);
		if(null==mapNodes) {return;}
		pa.pushMatrix();pa.pushStyle();
		for (SOM_MapNode node : mapNodes) {		((Straff_SOMMapNode)node).drawMeNonProdJpGroupSegClr(pa, npJpGroupLabel);}				
		pa.popStyle();pa.popMatrix();
	}
	public final void drawAllNonProdJPGroupSegments(my_procApplet pa) {	for(Integer key : nonProdJpGroup_Segments.keySet()) {	drawNonProdJPGroupSegments(pa,key);}	} 
	
	/**
	 * draw boxes around each node representing class-based segments that node 
	 * belongs to, with color strength proportional to probablity and 
	 * different colors for each segment
	 * pass class -label- not class index
	 * @param pa
	 * @param classLabel - label corresponding to class to be displayed
	 */
	@Override
	public final void drawClassSegments(my_procApplet pa, int curJP) {
		//Integer jp = jpJpgrpMon.getFtrJpByIdx(curJPIdx);
		Collection<SOM_MapNode> mapNodesWithClasses = MapNodesWithMappedClasses.get(curJP);
		if(null==mapNodesWithClasses) {		return;}
		pa.pushMatrix();pa.pushStyle();
		for (SOM_MapNode node : mapNodesWithClasses) {		node.drawMeClassClr(pa, curJP);}		
		pa.popStyle();pa.popMatrix();
	}//drawFtrWtSegments	
	
	/**
	 * draw filled boxes around each node representing category-based segments 
	 * that node belongs to, with color strength proportional to probablity 
	 * and different colors for each segment
	 * pass class -label- not class index
	 * @param pa
	 * @param classLabel - label corresponding to class to be displayed
	 */
	//draw boxes around each node representing category-based segments that nodes belong to
	@Override
	public final void drawCategorySegments(my_procApplet pa, int curJPGroup) {
		//Integer jpg = jpJpgrpMon.getFtrJpGroupByIdx(curJPGroupIdx);
		Collection<SOM_MapNode> mapNodes = MapNodesWithMappedCategories.get(curJPGroup);
		if(null==mapNodes) {return;}
		pa.pushMatrix();pa.pushStyle();
		for (SOM_MapNode node : mapNodes) {		node.drawMeCategorySegClr(pa, curJPGroup);}				
		pa.popStyle();pa.popMatrix();
	}//drawAllOrderJPGroupSegments
	
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
	
	//draw analysis results for all jps, including those that are not product-based/training features
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
		//which product feature calcs to use to determine prod distance
	private int getProdDistType() {return (getFlag(mapExclProdZeroFtrIDX) ? ProductExample.SharedFtrsIDX : ProductExample.AllFtrsIDX);}
		//display the region of the map expected to be impacted by the products serving the passed jp 
	public void drawProductRegion(my_procApplet pa, int prodJpIDX, double maxDist) {	prodExMapper.drawProductRegion(pa,prodJpIDX, maxDist, getProdDistType());}//drawProductRegion
	
	@Override
	protected float getPreBuiltMapInfoDetail(my_procApplet pa, String[] str, int idx, float yOff, boolean isLoaded) {
		int clrIDX = (isLoaded ? pa.gui_Yellow : pa.gui_White);
		pa.showOffsetText(0,clrIDX,"Weight Calc Used for this map : ");
		yOff += sideBarYDisp;
		pa.translate(10.0f, sideBarYDisp, 0.0f);
		pa.showOffsetText(0,clrIDX,str[str.length-1]);
		yOff += sideBarYDisp;
		pa.translate(-10.0f, sideBarYDisp, 0.0f);
		//add extra space
		yOff += sideBarYDisp;
		pa.translate(-10.0f, sideBarYDisp, 0.0f);
		return yOff;
	}//
	
	//app-specific drawing routines for side bar
	@Override
	protected float drawResultBarPriv1(my_procApplet pa, float yOff){
		
		return yOff;
	}
	@Override
	protected float drawResultBarPriv2(my_procApplet pa, float yOff){
		return yOff;
	}
	@Override
	protected float drawResultBarPriv3(my_procApplet pa, float yOff){
		return yOff;
	}
	

	// end drawing routines
	//////////////////////////////////////////////////////
	
	@Override
	//build the example that represents the data where the mouse is
	protected final SOM_MseOvrDisplay buildMseOverExample() {return new Straff_SOMMseOvrDisp(this,0.0f);}
		
	//whether or not distances between two datapoints assume that absent features in smaller-length datapoints are 0, or to ignore the values in the larger datapoints
	@Override
	public void setMapExclZeroFtrs(boolean val) {setFlag(mapExclProdZeroFtrIDX, val);};
	@Override
	protected int getNumFlags() {	return numFlags;}
	@Override
	protected void setFlag_Indiv(int idx, boolean val){
		switch (idx) {//special actions for each flag
			case mapExclProdZeroFtrIDX 		: {break;}
			case prospectDataLoadedIDX		: {break;}		//raw prospect data has been loaded but not yet processed
			case optDataLoadedIDX			: {break;}				//raw opt data has been loaded but not processed
			case orderDataLoadedIDX			: {break;}			//raw order data loaded not proced
			case linkDataLoadedIDX			: {break;}			//raw order data loaded not proced
			case sourceDataLoadedIDX		: {break;}			//raw order data loaded not proced
			case tcTagsDataLoadedIDX		: {break;}			//raw order data loaded not proced
			case jpDataLoadedIDX			: {break;}			//raw order data loaded not proced
			case jpgDataLoadedIDX			: {break;}	
			case testTrainProdDataBuiltIDX 	: {break;}			//arrays of input, training and testing data built
			case custOrdersAsTrainDataIDX 	: {	rebuildCustPrspctExMapper(val);	break;}			//whether training data is customer orders (multiple recs per customer) or customer records
			case mapProdsToBMUsIDX			: {break;}			//whether or not we should map products to bmus - necessary to display the product location on map, but not used for prospect mapping
		}
	}//setFlag		

	public boolean isFtrCalcDone(int idx) {return (ftrCalcObj != null) && ftrCalcObj.calcAnalysisIsReady(idx);}		
	//called to process analysis data
	public void processCalcAnalysis(int _type) {	if (ftrCalcObj != null) {ftrCalcObj.finalizeCalcAnalysis(_type);} else {getMsgObj().dispInfoMessage("StraffSOMMapManager","processCalcAnalysis", "ftrCalcObj == null! attempting to disp res for type : " + _type);}}
	//return # of features for calc analysis type being displayed
	public int numFtrsToShowForCalcAnalysis(int _type) { 
		switch(_type) {
			case Straff_WeightCalc.bndAra_ProdJPsIDX 	: {		return jpJpgrpMon.getNumTrainFtrs();		}
			case Straff_WeightCalc.bndAra_AllJPsIDX		: {		return jpJpgrpMon.getNumAllJpsFtrs();		}
			default : {				return jpJpgrpMon.getNumAllJpsFtrs();		}
		}//switch		
	} 
	
	//StraffWeightCalc.bndAra_AllJPsIDX StraffWeightCalc.bndAra_ProdJPsIDX
	public Float[] getTrainFtrMins() {return this.getMinVals(Straff_WeightCalc.bndAra_ProdJPsIDX);}
	public Float[] getTrainFtrDiffs() {return this.getDiffVals(Straff_WeightCalc.bndAra_ProdJPsIDX);}

	public Float[] getAllFtrMins() {return this.getMinVals(Straff_WeightCalc.bndAra_AllJPsIDX);}
	public Float[] getAllFtrDiffs() {return this.getDiffVals(Straff_WeightCalc.bndAra_AllJPsIDX);}

	public SOM_Example getProductByID(String prodOID) {	return prodExMapper.getExample(prodOID);		}
	
	public String getAllJpStrByIdx(int idx) {return jpJpgrpMon.getAllJpStrByIdx(idx);}	
	public String getAllJpGrpStrByIdx(int idx) {return jpJpgrpMon.getAllJpGrpStrByIdx(idx);}
		
	public String getFtrJpStrByIdx(int idx) {return jpJpgrpMon.getFtrJpStrByIdx(idx);}	
	public String getFtrJpGrpStrByIdx(int idx) {return jpJpgrpMon.getFtrJpGrpStrByIdx(idx);}
	
	public int getFtrJpByIdx(int idx) {return jpJpgrpMon.getFtrJpByIdx(idx);}
	public int getFtrJpGroupByIdx(int idx) {return jpJpgrpMon.getFtrJpGroupByIdx(idx);}
	public int getAllJpByIdx(int idx) {return jpJpgrpMon.getAllJpByIdx(idx);}
	public int getAllJpGroupByIdx(int idx) {return jpJpgrpMon.getAllJpByIdx(idx);}
		
	//this will return the appropriate jpgrp for the given jpIDX (list idx)
	public int getUI_JPGrpIdxFromFtrJPIdx(int jpIdx, int curVal) {		return jpJpgrpMon.getUI_JPGrpIdxFromFtrJPIdx(jpIdx, curVal);}
	//this will return the first(lowest) jp for a particular jpgrp
	public int getUI_FirstJPIdxFromFtrJPGIdx(int jpgIdx, int curJPIdxVal) {	return jpJpgrpMon.getUI_FirstJPIdxFromFtrJPGIdx(jpgIdx, curJPIdxVal);}	
	//this will return the appropriate jpgrp for the given jpIDX (list idx)
	public int getUI_JPGrpIdxFromAllJPIdx(int jpIdx, int curVal) {		return jpJpgrpMon.getUI_JPGrpIdxFromAllJPIdx(jpIdx, curVal);}
	//this will return the first(lowest) jp for a particular jpgrp
	public int getUI_FirstJPIdxFromAllJPGIdx(int jpgIdx, int curJPIdxVal) {	return jpJpgrpMon.getUI_FirstJPIdxFromAllJPGIdx(jpgIdx, curJPIdxVal);}	
	
	@Override
	//return appropriately pathed file name for map image of specified JP idx
	public String getSOMLocClrImgForFtrFName(int jpIDX) {
		int jp = jpJpgrpMon.getFtrJpByIdx(jpIDX);
		return projConfigData.getSOMLocClrImgForFtrFName(jp);	
	}	
	
	////////////////////////
	// debug routines
	
	//debug - display current state of SOM_MapDat object describing SOM command line and execution
	public void dbgShowSOM_MapDat() {
		getMsgObj().dispMessage("StraffSOMMapManager","dbgShowSOM_MapDat","Starting displaying current SOM_MapDat object.", MsgCodes.info5);
		getMsgObj().dispMultiLineInfoMessage("StraffSOMMapManager","dbgShowSOM_MapDat","\n"+projConfigData.SOM_MapDat_ToString()+"\n");		
		getMsgObj().dispMessage("StraffSOMMapManager","dbgShowSOM_MapDat","Finished displaying current SOM_MapDat object.", MsgCodes.info5);
	}
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
	
	//debug - display spans of weights of all features in products after products are built
	public void dbgDispProductWtSpans() {
		//debug - display spans of weights of all features in products
		String[] prodExVals = ProductExample.getMinMaxDists();
		getMsgObj().dispMessageAra(prodExVals,"StraffSOMMapManager", "SOMMapManager::finishSOMExampleBuild : spans of all product ftrs seen", 1, MsgCodes.info1);		
	}//dbgDispProductWtSpans()
	

	//TODO need to manage this
	public String toString(){
		String res = super.toString();
		return res;
	}

	
}//Straff_SOMMapManager
