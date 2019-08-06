package strafford_SOM_PKG.straff_SOM_Mapping;

import java.util.*;
import java.util.concurrent.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_segments.segments.SOM_MappedSegment;
import base_SOM_Objects.som_ui.SOM_MseOvrDisplay;
import base_SOM_Objects.som_ui.win_disp_ui.SOM_UIToMapCom;
import base_SOM_Objects.som_utils.SOM_ProjConfigData;

import base_UI_Objects.*;
import base_UI_Objects.windowUI.myDispWindow;
import base_Utils_Objects.io.MessageObject;
import base_Utils_Objects.io.MsgCodes;
import base_Utils_Objects.vectorObjs.Tuple;
import base_Utils_Objects.vectorObjs.myPoint;
import base_Utils_Objects.vectorObjs.myPointf;
import base_Utils_Objects.vectorObjs.myVector;
import processing.core.PImage;
import strafford_SOM_PKG.straff_Features.*;
import strafford_SOM_PKG.straff_Features.featureCalc.Straff_WeightCalc;

import strafford_SOM_PKG.straff_RawDataHandling.Straff_SOMRawDataLdrCnvrtr;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.base.Straff_BaseRawData;
import strafford_SOM_PKG.straff_SOM_Examples.*;
import strafford_SOM_PKG.straff_SOM_Examples.products.Straff_ProductExample;
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
	public Straff_MonitorJpJpgrp jpJpgrpMon;	
	//calc object to be used to derive feature vector for each prospect
	public Straff_WeightCalc ftrCalcObj;
	//object to manage all raw data processing
	private Straff_SOMRawDataLdrCnvrtr rawDataLdr;
	
	//whether or not there is enough ram to load all prospects at one time
	private boolean enoughRamToLoadAllProspects =false;
			
	////////////////////////////////////////////////////////////////////////////////////////////////
	//data descriptions
	
	//ref to cust prspct mapper
	private Straff_SOMCustPrspctManager_Base custPrspctExMapper;
	//ref to tru prospect mapper
	private Straff_SOMTruePrspctManager truePrspctExMapper;
	//ref to prod mapper
	private Straff_SOMProductManager prodExMapper;	
	
	//map of jpgroup idx and all map nodes that have non-zero presence in features(jps) that belong to that jpgroup
	protected TreeMap<Integer, HashSet<SOM_MapNode>> MapNodesByJPGroupIDX;
	
	//data for products to be measured on map
	private Straff_ProductExample[] productData;
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
	
	//////////////////////////////
	// map image drawing/building variables - only should be accessed/relevant if win != null
	//////////////////////////////
	//map drawing 	draw/interaction variables
	//max sq_distance to display map nodes as under influence/influencing certain products
	private double prodZoneDistThresh;
	//start location of SOM image - stX, stY, and dimensions of SOM image - width, height; locations to put calc analysis visualizations
	private float[] calcAnalysisLocs;	
	//to draw analysis results
	public final float calcScale = .5f;
	//set analysisHt equal to be around 1/2 SOM_mapDims height
	private float analysisHt, analysisAllJPBarWidth, analysisPerJPWidth;
	//other PImage maps set in base class
	//array of per jpg map wts - equally weighted all jps within jpg
	private PImage[] mapPerJpgWtImgs;
	
	//which product is being shown by single-product display visualizations, as index in list of jps of products
	private int curProdToShowIDX;
	//which jp is currently being investigated of -all- jps
	private int curAllJPToShowIDX;
	
//	//which nonprod jp to show (for data which support showing non-prod jps
//	private int curNonProdJPToShowIDX;
//	//which nonprod jpg to show (for data which support showing non-prod jpgroups
//	private int curNonProdJPGroupToShowIDX;

	//types of data that can be used for calc analysis 
	private int curCalcAnalysisSrcDataTypeIDX = Straff_WeightCalc.bndAra_AllJPsIDX;
	private int curCalcAnalysisJPTypeIDX = Straff_WeightCalc.bndAra_AllJPsIDX;
	
	private Straff_SOMMapManager(Straff_SOMMapUIWin _win, TreeMap<String, Object> _argsMap) {
		super(_win, _argsMap);	
		//if there's enough ram to run all prospects at once
		if(_argsMap.get("enoughRamToLoadAllProspects") != null) {enoughRamToLoadAllProspects = (boolean) _argsMap.get("enoughRamToLoadAllProspects");}
		//object to manage all jps and jpgroups seen in project
		jpJpgrpMon = new Straff_MonitorJpJpgrp(this);
		//all raw data loading moved to this object
		rawDataLdr = new Straff_SOMRawDataLdrCnvrtr(this, projConfigData);	
		//default to using orders as training data - TODO make this a modifiable flag ?  
		setFlag(custOrdersAsTrainDataIDX,true);
		//default to mapping products to bmus - TODO make this modifiable/Set by call method?  
		//do not allow changing mapProdsToBMUsIDX during execution/via UI, might precipitate bad things that are difficult to debug
		setFlag(mapProdsToBMUsIDX, true);
	}//ctor	
	
	//ctor from non-UI stub main
	public Straff_SOMMapManager(TreeMap<String, Object> _argsMap) {this(null, _argsMap);}		
	
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
		getMsgObj().dispMessage("Straff_SOMMapManager","loadAndPreProcAllRawData","Start loading and processing raw data", MsgCodes.info5);
		//this will load all raw data into memory from csv files or sql queries(todo)
		ConcurrentSkipListMap<String, ArrayList<Straff_BaseRawData>> _rawDataAras = rawDataLdr.loadAllRawData(fromCSVFiles);
		if(null==_rawDataAras) {		return;	}
		//process loaded data
		//dbgLoadedData(tcTagsIDX);
		getMsgObj().dispMessage("Straff_SOMMapManager","loadAndPreProcAllRawData","Start Processing all loaded raw data", MsgCodes.info5);
		if (!(getFlag(prospectDataLoadedIDX) && getFlag(optDataLoadedIDX) && getFlag(orderDataLoadedIDX) && getFlag(tcTagsDataLoadedIDX))){//not all data loaded, don't process 
			getMsgObj().dispMessage("Straff_SOMMapManager","loadAndPreProcAllRawData","Can't build data examples until raw data is all loaded", MsgCodes.warning2);			
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
			getMsgObj().dispMessage("Straff_SOMMapManager","loadAndPreProcAllRawData","Raw Prospects and Products have been finalized to determine JPs and JPGroups present.", MsgCodes.info5);			
			
			//we need the jp-jpg counts and relationships dictated by the data by here.
			_setJPDataFromExampleData(tmpProspectMapper);			
			//build actual customer and validation maps using rules defining what is a customer (probably means having an order event) and what is a "prospect" (probably not having an order event)
			_buildCustomerAndProspectMaps(tmpProspectMapper);			
			//by here both prospect mappers have been appropriately populated			
			//finalize - recalc all processed data in case new products have different JP's present, set flags and save to file			

			getMsgObj().dispMessage("Straff_SOMMapManager","loadAndPreProcAllRawData","Start loading calc object, calculating all feature vectors for prospects and products, calculating mins and diffs, and saving all results.", MsgCodes.info5);
				//condition data before saving it
			_finalizeAllMappersBeforeFtrCalc();
				//save all prospect, product and jpjpg monitor data - don't need to build ftr vectors here
			saveAllPreProcExamples();
			
			getMsgObj().dispMessage("Straff_SOMMapManager","loadAndPreProcAllRawData","Finished preprocessing and saving all loaded raw data", MsgCodes.info5);
		}
		getMsgObj().dispMessage("Straff_SOMMapManager","loadAndPreProcAllRawData","Finished loading raw data, processing and saving preprocessed data", MsgCodes.info5);
	}//loadAllRawData	
	
	//save MonitorJpJpgrp, construct that manages jp-jpgroup relationships (values and corresponding indexes in arrays)
	private void saveMonitorJpJpgrp() {
		getMsgObj().dispMessage("Straff_SOMMapManager","saveMonitorJpJpgrp","Saving MonitorJpJpgrp data", MsgCodes.info5);
		String[] saveDestFNamePrefixAra = projConfigData.buildPreProccedDataCSVFNames_Save("MonitorJpJpgrpData");
		jpJpgrpMon.saveAllData(saveDestFNamePrefixAra[0],".csv");
		getMsgObj().dispMessage("Straff_SOMMapManager","saveMonitorJpJpgrp","Finished saving MonitorJpJpgrp data", MsgCodes.info5);
	}//saveMonitorJpJpgrp
			
	//save all currently preprocced loaded data - customer and true prospects, products, and jpjpg monitor
	protected void saveAllPreProcExamples() {
		getMsgObj().dispMessage("Straff_SOMMapManager","saveAllExamples","Begin Saving all Preproccessed Examples.", MsgCodes.info5);
			//save products
		boolean prodSuccess = prodExMapper.saveAllPreProccedExampleData();
			//save customer prospect examples
		boolean custPrspctSuccess = custPrspctExMapper.saveAllPreProccedExampleData();
			//save true prospect examples
		boolean truePrspctSuccess = truePrspctExMapper.saveAllPreProccedExampleData();
		getMsgObj().dispMessage("Straff_SOMMapManager","saveAllExamples","Finished Saving all Preproccessed Examples.", MsgCodes.info5);
		if (custPrspctSuccess || truePrspctSuccess || prodSuccess) { saveMonitorJpJpgrp();}		
	}//saveAllExamples
	
	//this will display debug-related info related to event mapping in raw prospect records
	private void dispDebugEventPresenceData(int[] countsOfBoolResOcc, int[] countsOfBoolResEvt) {
		for(int i=0;i<Straff_CustProspectExample.jpOccTypeKeys.length;++i) {
			getMsgObj().dispMessage("Straff_SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect OCC records with "+ Straff_CustProspectExample.jpOccTypeKeys[i]+" events : " + countsOfBoolResOcc[i] , MsgCodes.info1);				
			getMsgObj().dispMessage("Straff_SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect Event records with "+ Straff_CustProspectExample.jpOccTypeKeys[i]+" events : " + countsOfBoolResEvt[i] , MsgCodes.info1);	
			if(i==1) {
				getMsgObj().dispMessage("Straff_SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect records with global opt : " + Straff_ProspectExample._numOptAllOccs[0] , MsgCodes.info1);	
				getMsgObj().dispMessage("Straff_SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect records with non-positive global opt : " + Straff_ProspectExample._numOptAllOccs[1] , MsgCodes.info1);	
				getMsgObj().dispMessage("Straff_SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect records with positive global opt : " + Straff_ProspectExample._numOptAllOccs[2], MsgCodes.info1);	
			}
			getMsgObj().dispMessage("Straff_SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData"," " , MsgCodes.info1);				
		}		
		getMsgObj().dispMessage("Straff_SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect records with OCC non-source events : " + countsOfBoolResOcc[countsOfBoolResOcc.length-1] , MsgCodes.info1);			
		getMsgObj().dispMessage("Straff_SOMMapManager","_buildCustomerAndProspectMaps->dispDebugEventPresenceData","# prospect records with Evt non-source events : " + countsOfBoolResEvt[countsOfBoolResEvt.length-1] , MsgCodes.info1);				
	}//dispDebugEventPresenceData

	//necessary processing for true prospects - convert a customer to a true prospect if appropriate?
	private void _handleTrueProspect(SOM_ExampleManager truePrspctMapper,Straff_ProspectExample ex) {truePrspctMapper.addExampleToMap(new Straff_TrueProspectExample(ex));	}//handleTrueProspect - retains OID from old ex
		
	//this function will take all raw loaded prospects and partition them into customers and true prospects
	//it determines what the partition/definition is for a "customer" which is used to train the map, and a "true prospect" which is polled against the map to find product membership.
	//typeOfEventsForCustomer : int corresponding to what kind of events define a customer and what defines a prospect.  
	//    0 : cust has order event, prospect does not but has source and possibly other events
	//    1 : cust has some non-source event, prospect does not have customer event but does have source event
	private void _buildCustomerAndProspectMaps(Straff_SOMTrainExPerOrderManager tmpProspectMapper) {
		getMsgObj().dispMessage("Straff_SOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps", "Start Mapping Raw Prospects to Customers and True Prospects", MsgCodes.info5);
		//whether or not to display total event membership counts across all examples to console
		boolean dispDebugEventMembership = true, dispDebugProgress = true;
		
		String prospectDesc = "";
		int numRecsToProc = tmpProspectMapper.getNumMapExamples();
		int typeOfEventsForCustomer = ((Straff_SOMProjConfig)projConfigData).getTypeOfEventsForCustAndProspect();

		int[] countsOfBoolResOcc = new int[Straff_CustProspectExample.jpOccTypeKeys.length+1],
			countsOfBoolResEvt = new int[Straff_CustProspectExample.jpOccTypeKeys.length+1];		//all types of events supported + 1

		custPrspctExMapper.reset();
		//SOMExampleMapper truePrspctMapper = exampleDataMappers.get("trueProspect");		truePrspctMapper.reset();
		
		truePrspctExMapper.reset();
		int curEx=0, modSz = numRecsToProc/20, nonCustPrspctRecs = 0, noEventDataPrspctRecs = 0;		
		
		Set<String> keySet = tmpProspectMapper.getExampleKeySet();
		switch(typeOfEventsForCustomer) {
		case 0 : {		// cust has order event, prospect does not but has source and possibly other events
			prospectDesc = "Records that do not have any order events";
			for (String OID : keySet) {		
				Straff_ProspectExample ex = (Straff_ProspectExample) tmpProspectMapper.removeExampleFromMap(OID); 
				if(dispDebugEventMembership) {dbgEventInExample(ex, countsOfBoolResOcc, countsOfBoolResEvt);}
				if (ex.isTrainablePastCustomer()) {			custPrspctExMapper.addExampleToMap(ex);		} 			//training data - has valid feature vector and past order events
				else if (ex.isTrueProspect()) {				_handleTrueProspect(truePrspctExMapper, ex);	} 				//no past order events but has valid source event data
				else {//should never happen - example is going nowhere - is neither true prospect or trainable past customer
					//msgObj.dispMessage("Straff_SOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","Raw Rec " + ex.OID + " neither trainable customer nor true prospect.  Ignoring.", MsgCodes.info3);
					if(ex.hasEventData()) {				++nonCustPrspctRecs;			} else {			++noEventDataPrspctRecs;		}					
				}
				if(dispDebugProgress) {	++curEx;	if(curEx % modSz == 0) {System.out.print(".");}}
			}
			break;}
		case 1 : {		//cust has some non-source event, prospect does not have customer event but does have source event
			prospectDesc = "Records that only have source events";
			for (String OID : keySet) {		
				Straff_ProspectExample ex = (Straff_ProspectExample) tmpProspectMapper.removeExampleFromMap(OID);
				if(dispDebugEventMembership) {dbgEventInExample(ex, countsOfBoolResOcc, countsOfBoolResEvt);}
				if (ex.hasNonSourceEvents()) {					custPrspctExMapper.addExampleToMap(ex);		} 			//training data - has valid feature vector and any non-source event data
				else if (ex.hasOnlySourceEvents()) {			_handleTrueProspect(truePrspctExMapper, ex);		} 				//only has source data
				else {//should never happen - example is going nowhere - is neither true prospect or trainable past customer
					//msgObj.dispMessage("Straff_SOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","Raw Rec " + ex.OID + " neither trainable customer nor true prospect.  Ignoring.", MsgCodes.info3);
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
		getMsgObj().dispMessage("Straff_SOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","Raw Records Unique OIDs presented : " + numRecsToProc, MsgCodes.info3);
		getMsgObj().dispMessage("Straff_SOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","# True Prospect records (" + prospectDesc +") : " + truePrspctExMapper.getNumMapExamples(), MsgCodes.info1);	
		getMsgObj().dispMessage("Straff_SOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","# Customer Prospect records found with trainable event-based info : " + custPrspctExMapper.getNumMapExamples(), MsgCodes.info3);
		getMsgObj().dispMessage("Straff_SOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps","# Raw Records that are neither true prospects nor customers but has events : " + nonCustPrspctRecs + " and with no events : "+ noEventDataPrspctRecs, MsgCodes.info3);

		getMsgObj().dispMessage("Straff_SOMMapManager","procRawLoadedData->_buildCustomerAndProspectMaps", "Finished Mapping Raw Prospects to Customers and True Prospects", MsgCodes.info5);
	}//_buildCustomerAndProspectMaps	

	/**
	 * set display list values from jpjpgroup monitor obj
	 * @param jpList_prod : list of jp short names (For products)
	 * @param jpList_IDX : list of jp names with IDX (For features)
	 * @param jpList_Jp : list of jp names with jp ID (for class names)
	 * @param jpGrpList : list of jpgroup names with jpgroup ID (for category names)
	 */
	public void setUI_JPFtrListVals(String[] jpList_prod, String[] jpList_IDX, String[] jpList_Jp, String[] jpGrpList) {
		if (win != null) {		
			//(String[] ftrVals, String[] classVals, String[] categoryVals, String[] prodVals)
			((Straff_SOMMapUIWin)win).setUI_JPFtrListVals(jpList_IDX, jpList_Jp,  jpGrpList, jpList_Jp);		
		}
	}
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
		getMsgObj().dispMessage("Straff_SOMMapManager","loadPreProcTrainData","Begin loading preprocced data from " + subDir +  "directory.", MsgCodes.info5);
			//load monitor first;save it last - keeps records of jps and jpgs even for data not loaded
		getMsgObj().dispMessage("Straff_SOMMapManager","loadPreProcTrainData","Loading MonitorJpJpgrp data.", MsgCodes.info1);
		String[] loadSrcFNamePrefixAra = projConfigData.buildPreProccedDataCSVFNames_Load(subDir, "MonitorJpJpgrpData");
		jpJpgrpMon.loadAllData(loadSrcFNamePrefixAra[0],".csv");
		getMsgObj().dispMessage("Straff_SOMMapManager","loadPreProcTrainData","Finished loading MonitorJpJpgrp data.", MsgCodes.info1);
			//display all jps and jpgs in currently loaded jp-jpg monitor
		getMsgObj().dispMultiLineMessage("Straff_SOMMapManager","loadPreProcTrainData","Jp/Jp Group Profile of data : " + jpJpgrpMon.toString(), MsgCodes.info1);

			//load customer data
		if(!custPrspctExMapper.isDataPreProcced() || forceLoad) {			custPrspctExMapper.loadAllPreProccedExampleData(subDir);}
		else {getMsgObj().dispMessage("Straff_SOMMapManager","loadPreProcTrainData","Not loading preprocessed Customer Prospect examples since they are already loaded.", MsgCodes.info1);}
			//load preproc product data
		if(!prodExMapper.isDataPreProcced() || forceLoad) {					prodExMapper.loadAllPreProccedExampleData(subDir);		}	
		else {getMsgObj().dispMessage("Straff_SOMMapManager","loadPreProcTrainData","Not loading preprocessed Product examples since they are already loaded.", MsgCodes.info1);}
			//finalize and calc ftr vecs on customer prospects and products if we have loaded new data - don't build True Prospect feature vectors since they might not be loaded in synch with these files
		finishSOMExampleBuild(false);
			//preprocced data might be different than current true prospect data, so clear flag and reset map (clear out memory)
		getMsgObj().dispMessage("Straff_SOMMapManager","loadAllPreProccedData","Finished loading preprocced data from " + subDir +  "directory.", MsgCodes.info5);
	}//loadAllPreProccedData		
	
	public void loadAllTrueProspectData() {loadAllTrueProspectData(projConfigData.getPreProcDataDesiredSubDirName());}
	//load validation (true prospects) data found in subDir
	private void loadAllTrueProspectData(String subDir) {
		getMsgObj().dispMessage("Straff_SOMMapManager","loadAllTrueProspectData","Begin loading preprocessed True Prospect data from " + subDir +  "directory.", MsgCodes.info5);
			//load customers if not loaded
		loadPreprocAndBuildTestTrainPartitions(projConfigData.getTrainTestPartition(), false);
			//load true prospects
		truePrspctExMapper.loadAllPreProccedExampleData(subDir);
			//process all true prospects, building their ftrs and making array of validation data
		procTrueProspectExamples();
		
		getMsgObj().dispMessage("Straff_SOMMapManager","loadAllTrueProspectData","Finished loading preprocessed True Prospect data from " + subDir +  "directory and building validation data array of size : " + numValidationData+".", MsgCodes.info5);
	}//loadAllProspectData	
	
	/**
	 * process all true prospect examples and build Validation data array (which may include customer records if training on orders)
	 */
	private void procTrueProspectExamples() {
		getMsgObj().dispMessage("Straff_SOMMapManager","procTrueProspectExamples"," Begin initial finalize of true prospects map", MsgCodes.info1);	
		truePrspctExMapper.finalizeAllExamples();		
		getMsgObj().dispMessage("Straff_SOMMapManager","procTrueProspectExamples","Finished initial finalize of true prospects map | Begin build feature vector for all true prospects.", MsgCodes.info1);	
		
		//customer prospects should be built first to specify bounds
		//since true prospects' data is largely subjective/non-behavior driven
		if(ftrCalcObj.custNonProdJpCalcIsDone()) {	
			truePrspctExMapper.buildFeatureVectors();
		} else {
			getMsgObj().dispMessage("Straff_SOMMapManager","procTrueProspectExamples","Attempting to build true prospect ftr vectors without calculating the contribution from customers sharing same non-product jps.  Aborting.", MsgCodes.error1);	
		}
		getMsgObj().dispMessage("Straff_SOMMapManager","procTrueProspectExamples","Finished build feature vector for all true prospects | Begin post feature vector build.", MsgCodes.info1);	
		truePrspctExMapper.buildAfterAllFtrVecsBuiltStructs();
		getMsgObj().dispMessage("Straff_SOMMapManager","procTrueProspectExamples","Finished post feature vector build. | Begin assigning to Validation Data Array", MsgCodes.info1);	
			//build validation array
		buildValidationDataAra();
		getMsgObj().dispMessage("Straff_SOMMapManager","procTrueProspectExamples","Finished post feature vector build. | Finished assigning to Validation Data Array : # Validation examples : " + validationData.length, MsgCodes.info1);	
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
		msgObj.dispMessage("Straff_SOMMapManager","loadAllDataAndBuildMappings","Begin loading all preprocessed data from " + subDir +  "directory and building mappings.", MsgCodes.info1);
			
		msgObj.dispMessage("Straff_SOMMapManager","loadAllDataAndBuildMappings","Loading MonitorJpJpgrp data.", MsgCodes.info1);
		String[] loadSrcFNamePrefixAra = projConfigData.buildPreProccedDataCSVFNames_Load(subDir, "MonitorJpJpgrpData");
			//load monitor first;save it last - keeps records of jps and jpgs even for data not loaded
		jpJpgrpMon.loadAllData(loadSrcFNamePrefixAra[0],".csv");		
		msgObj.dispMessage("Straff_SOMMapManager","loadAllDataAndBuildMappings","Finished loading MonitorJpJpgrp data.", MsgCodes.info1);		
			//display all jps and jpgs in currently loaded jp-jpg monitor
		msgObj.dispMultiLineMessage("Straff_SOMMapManager","loadAllDataAndBuildMappings","Jp/Jp Group Profile of data : " + jpJpgrpMon.toString(), MsgCodes.info1);	

		msgObj.dispMessage("Straff_SOMMapManager","loadAllDataAndBuildMappings","Start loading Default SOM Map data.", MsgCodes.info1);		
			//current SOM map, if there is one, is now out of date, do not use
		setSOMMapNodeDataIsLoaded(false);			
			//load default pretrained map - for prebuilt map - load config used in prebuilt map including weight equation (override calc set in config - weight eq MUST always match trained map weight eq)
		boolean dfltmapLoaded = projConfigData.setSOM_UsePreBuilt();	
		if(!dfltmapLoaded) {
			msgObj.dispMessage("Straff_SOMMapManager","loadAllDataAndBuildMappings","No Default map loaded, probably due to no default map directories specified in config file.  Aborting ", MsgCodes.info1);
			return;
		}
		msgObj.dispMessage("Straff_SOMMapManager","loadAllDataAndBuildMappings","Finished loading Default SOM Map data. | Start loading customer, true prospect and product preproc data.", MsgCodes.info1);		
			//load customer data
		custPrspctExMapper.loadAllPreProccedExampleData(subDir);
			//load preproc product data
		prodExMapper.loadAllPreProccedExampleData(subDir);		
		
		msgObj.dispMessage("Straff_SOMMapManager","loadAllDataAndBuildMappings","Finished load all preproc data | Begin build features, set mins/diffs and calc post-global-ftr-calc data.", MsgCodes.info1);	
			//build features, set mins and diffs, and build after-feature-values
		finishSOMExampleBuild(false);
		
		msgObj.dispMessage("Straff_SOMMapManager","loadAllDataAndBuildMappings","Finished build features, set mins/diffs and calc post-global-ftr-calc data. | Start building train partitions and mapping training examples and products.", MsgCodes.info1);		
			//partition training and product data
		buildTrainTestFromPartition(projConfigData.getTrainTestPartition());
			//load map results to build SOMMapNode representation of map, and set training data bmus as reported by SOM Training code; also set product bmus if explicitly determined to do this via mapProdsToBMUsIDX flag
		loadMapAndBMUs();	
		
			//by here all map data is loaded and both training data and product data are mapped to BMUs
		msgObj.dispMessage("Straff_SOMMapManager","loadAllDataAndBuildMappings","Finished building train partitions, loading Default SOM Map, and mapping training examples and products. | Start Saving Map Ftrs, Classes(JPs), Categories(JP groups) and customer and product BMU mappings.", MsgCodes.info1);
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
			//save customer mappings - simple save
		custPrspctExMapper.saveExampleBMUMappings(preProcDatPartSz);
			//save product to bmu mappings
		prodExMapper.saveExampleBMUMappings(preProcDatPartSz);
		
			//by here all map data is loaded, both training data and product data are mapped to BMUs, and the BMU data has been saved to file
		msgObj.dispMessage("Straff_SOMMapManager","loadAllDataAndBuildMappings","Finished Saving Map Ftrs, Classes(JPs),Categories(JP groups) and customer and product BMU mappings. | Start Loading & mapping true prospects and saving results.", MsgCodes.info1);
		
		loadAndMapTrueProspects(subDir);

		msgObj.dispMessage("Straff_SOMMapManager","loadAllDataAndBuildMappings","Finished Loading & mapping true prospects and saving results", MsgCodes.info1);			
		
		msgObj.dispMessage("Straff_SOMMapManager","loadAllDataAndBuildMappings","Finished loading all preprocced example data, specified trained SOM, mapping all data to BMUs, and saving all mappings.", MsgCodes.info1);			
	}//loadAllDataAndBuildMappings
	
	/**
	 * This will load all true prospects, map them to bmus and save the resulting 
	 * mappings.
	 * 
	 * this either loads, processes, maps and saves data in pages corresponding 
	 * to preproc data size, or loads all data into mememory at one time(and should only 
	 * be performed if sufficient system ram exists if this is the case)
	 * @param subDir
	 */
	public void loadAndMapTrueProspects(String subDir) {		
		if(enoughRamToLoadAllProspects) {
				//load true prospects
			truePrspctExMapper.loadAllPreProccedExampleData(subDir);	
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
				//save bmus
			truePrspctExMapper.saveExampleBMUMappings(preProcDatPartSz);
		} else {
			// process to go through validation data in chunks
			truePrspctExMapper.loadDataMapBMUAndSavePerPreProcFile(subDir, SOM_ExDataType.Validation, validateDataMappedIDX);
			//only last pass
			validationData = truePrspctExMapper.buildExampleArray();
			
		}
	}//loadAndMapTrueProspects
	
	/**
	 * reload calc object data and recalc ftrs
	 */
	public void reCalcCurrFtrs() {
		getMsgObj().dispMessage("Straff_SOMMapManager","reCalcCurrFtrs","Start loading calc object, calculating all feature vectors for prospects and products, calculating mins and diffs.", MsgCodes.info5);
		finishSOMExampleBuild(true);
		getMsgObj().dispMessage("Straff_SOMMapManager","reCalcCurrFtrs","Finished loading calc object, calculating all feature vectors for prospects and products & calculating mins and diffs.", MsgCodes.info1);		
	}//reCalcCurrFtrs
	
					
	//finish building the prospect map - finalize each prospect example and then perform calculation to derive weight vector
	protected void finishSOMExampleBuild(boolean buildTPIfExist) {
		getMsgObj().dispMessage("Straff_SOMMapManager","finishSOMExampleBuild","Begin finalize mappers, calculate feature data, diffs, mins, and calculate post-global-ftr-data calcs.", MsgCodes.info5);
		//if((custPrspctExMapper.getNumMapExamples() != 0) || (prodExMapper.getNumMapExamples() != 0)) {
				//current SOM map, if there is one, is now out of date, do not use
		setSOMMapNodeDataIsLoaded(false);			
		boolean tpBldFtrSccs = false;
			//finalize customer prospects and products (and true prospects if they exist) - customers are defined by having criteria that enable their behavior to be used as to train the SOM		
		_finalizeAllMappersBeforeFtrCalc();
			//feature vector only corresponds to actual -customers- since this is what is used to build the map - build feature vector for customer prospects				
		boolean custBldFtrSuccess = custPrspctExMapper.buildFeatureVectors();	
		if(!custBldFtrSuccess) {getMsgObj().dispMessage("Straff_SOMMapManager","finishSOMExampleBuild","Building Customer Prospect Feature vectors failed due to above error (no data available).  Aborting - No features have been calculated for any examples!", MsgCodes.error1);	return;	}
		
			//build/rebuild true prospects if there are any 
		if(buildTPIfExist) {
			tpBldFtrSccs = truePrspctExMapper.buildFeatureVectors();
			if(!tpBldFtrSccs) {getMsgObj().dispMessage("Straff_SOMMapManager","finishSOMExampleBuild","Building True Prospect Feature vectors requested but failed due to above error (no data available).", MsgCodes.error1);	}	
		}
			//build features for products
		boolean prodBldFtrSuccess = prodExMapper.buildFeatureVectors();
		if(!prodBldFtrSuccess) {getMsgObj().dispMessage("Straff_SOMMapManager","finishSOMExampleBuild","Building Product Feature vectors failed due to above error (no data available).", MsgCodes.error1);	}	
		
		getMsgObj().dispMessage("Straff_SOMMapManager","finishSOMExampleBuild","Finished buildFeatureVectors | Begin calculating diffs and mins", MsgCodes.info1);	
			//now get mins and diffs from calc object
		//setMinsAndDiffs(ftrCalcObj.getMinBndsAra(), ftrCalcObj.getDiffsBndsAra());
		setMinsAndDiffs(ftrCalcObj.getMinTrainDataBndsAra(), ftrCalcObj.getDiffsTrainDataBndsAra());  
		
		getMsgObj().dispMessage("Straff_SOMMapManager","finishSOMExampleBuild","Finished calculating diffs and mins | Begin building post-feature calc structs in prospects and products (i.e. std ftrs) dependent on diffs and mins", MsgCodes.info1);
		
			//now finalize post feature calc -this will do std features			
		custPrspctExMapper.buildAfterAllFtrVecsBuiltStructs();		
			//if specified then also build true prospect data
		if((buildTPIfExist) && (tpBldFtrSccs)){truePrspctExMapper.buildAfterAllFtrVecsBuiltStructs();}
			//build std features for products
		prodExMapper.buildAfterAllFtrVecsBuiltStructs();
		
		getMsgObj().dispMessage("Straff_SOMMapManager","finishSOMExampleBuild","Finished finalize mappers, calculate feature data, diffs, mins, and calculate post-global-ftr-data calcs.", MsgCodes.info5);						
		//} else {	getMsgObj().dispMessage("Straff_SOMMapManager","finishSOMExampleBuild","No prospects or products loaded to calculate/finalize.", MsgCodes.warning2);	}
	}//finishSOMExampleBuild	
	
	@Override
	//this function will build the input data used by the SOM - this will be partitioned by some amount into test and train data (usually will use 100% train data, but may wish to test label mapping)
	protected SOM_Example[] buildSOM_InputData() {
		SOM_Example[] res = custPrspctExMapper.buildExampleArray();	//cast to appropriate mapper when flag custOrdersAsTrainDataIDX is set
		String dispkStr = getFlag(custOrdersAsTrainDataIDX) ? 
				"Uses Customer orders for input/training data | Size of input data : " + res.length + " | # of customer prospects : " +  custPrspctExMapper.getNumMapExamples() + " | These should not be equal." :
				"Uses Customer Prospect records for input/training data | Size of input data : " + res.length + " | # of customer prospects : " +  custPrspctExMapper.getNumMapExamples() + " | These should be equal." ;
		getMsgObj().dispMessage("Straff_SOMMapManager","buildSOM_InputData", dispkStr,MsgCodes.info5);

		return res;
	}//buildSOMInputData
		
	@Override
	//using the passed map information, build the testing and training data partitions and save them to files
	protected void buildTrainTestFromPartition(float trainTestPartition) {
		getMsgObj().dispMessage("Straff_SOMMapManager","buildTestTrainFromInput","Starting Building Input, Test, Train, Product data arrays.", MsgCodes.info5);
		//build array of product examples based on product map
		productData = (Straff_ProductExample[]) prodExMapper.buildExampleArray();
		setFlag(testTrainProdDataBuiltIDX,true);
		//set input data, shuffle it and set test and train partitions
		setInputTrainTestShuffleDataAras(trainTestPartition);
		
		//for(ProductExample prdEx : productData) {msgObj.dispMessage("Straff_SOMMapManager","buildTestTrainFromInput",prdEx.toString());}
		getMsgObj().dispMessage("Straff_SOMMapManager","buildTestTrainFromInput","Finished Building Input, Test, Train, Product data arrays.  Product data size : " +productData.length +".", MsgCodes.info5);
	}//buildTestTrainFromInput
	
	/**
	 * augment a map of application-specific descriptive quantities and their values, for the SOM Execution human-readable report
	 * @param res map already created holding exampleDataMappers create time
	 */
	@Override
	protected void getSOMExecInfo_Indiv(TreeMap<String, String> res){
		res.put("Number of training (product-present) jps", ""+jpJpgrpMon.getNumTrainFtrs());
		res.put("Training Data JPs (in feature idx order)\n", jpJpgrpMon.getFtrJpsAsCSV()+"\n");
		res.put("Total number of jps seen across all prospects and products", ""+jpJpgrpMon.getNumAllJpsFtrs());		
	}

	
	//this will set the current jp->jpg data maps based on examples in passed prospect data map and current products
	//This must be performed after all examples are loaded and finalized but before the feature vectors are calculated
	//due to the ftr calc requiring a knowledge of the entire dataset's jp-jpg membership to build ftr vectors appropriately
	private void _setJPDataFromExampleData(Straff_SOMCustPrspctManager_Base custMapper) {
		//object to manage all jps and jpgroups seen in project
		jpJpgrpMon.setJPDataFromExampleData(custMapper, truePrspctExMapper, prodExMapper);		
		setNumTrainFtrs(jpJpgrpMon.getNumTrainFtrs()); 
		numTtlJps = jpJpgrpMon.getNumAllJpsFtrs();
		getMsgObj().dispInfoMessage("Straff_SOMMapManager","_setJPDataFromExampleData","Total # of JPs referenced : "+ numTtlJps);
		//rebuild calc object since feature terrain might have changed 
		String calcFullFileName = ((Straff_SOMProjConfig)projConfigData).getFullCalcInfoFileName(); 
		//make/remake calc object - reads from calcFullFileName data file
		ftrCalcObj = new Straff_WeightCalc(this, calcFullFileName, jpJpgrpMon);
	}//setJPDataFromProspectData	
	
	protected void _finalizeAllMappersBeforeFtrCalc() {
		getMsgObj().dispInfoMessage("Straff_SOMMapManager","_finalizeProsProdJpJPGMon","Begin finalize of all example data, preparing each example for feature calculation.");
		//finalize customers before feature calcs
		custPrspctExMapper.finalizeAllExamples();
		//finalize true prospects before feature calcs
		truePrspctExMapper.finalizeAllExamples();
		//finalize products before feature calcs
		prodExMapper.finalizeAllExamples();		
	
		getMsgObj().dispInfoMessage("Straff_SOMMapManager","_finalizeProsProdJpJPGMon","Finished finalize of all example data, preparing each example for feature calculation");
		getMsgObj().dispInfoMessage("Straff_SOMMapManager","_finalizeProsProdJpJPGMon","Begin setJPDataFromExampleData from all examples.");
		//we need the jp-jpg counts and relationships dictated by the data by here.
		_setJPDataFromExampleData(custPrspctExMapper);
		getMsgObj().dispInfoMessage("Straff_SOMMapManager","_finalizeProsProdJpJPGMon","Finished setJPDataFromExampleData from all examples.");
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
		} else {//if using customers to train then only use true prospects as validation
			//build array of trueProspectData used to map
			validationData = truePrspctExMapper.buildExampleArray();
			//this is if orders were used as training data			
		}		
		numValidationData = validationData.length;
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
	public SOM_MapNode buildMapNode(Tuple<Integer,Integer>mapLoc, SOM_FtrDataType _ftrTypeUsedToTrain, String[] tkns) {return new Straff_SOMMapNode(this,mapLoc,_ftrTypeUsedToTrain, tkns);}	
	
	///////////////////////////
	// end build and manage mapNodes 
	
	/**
	 * return the per-file data partition size to use when saving preprocessed training data csv files
	 * @return
	 */
	@Override
	public final int getPreProcDatPartSz() {		return preProcDatPartSz;	};

	
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
		setProdDataBMUsMapped(false);
		getMsgObj().dispMessage("Straff_SOMMapManager","setProductBMUs","Start Mapping " +productData.length + " products to best matching units.", MsgCodes.info5);
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
			getMsgObj().dispMessage("Straff_SOMMapManager","setProductBMUs","Finished finding bmus for all product data. Start adding product data to appropriate bmu's list.", MsgCodes.info1);
			_completeBMUProcessing(productData, SOM_ExDataType.Product, true);		
		
		} else {//for every product find closest map node
			//perform single threaded version - execute synchronously
			Straff_MapProductDataToBMUs mapper = new Straff_MapProductDataToBMUs(this,0, productData.length, productData, 0, useChiSqDist);
			mapper.call();
			//go through every product and attach prod to bmu - needs to be done synchronously because don't want to concurrently modify bmus from 2 different prods
			getMsgObj().dispMessage("Straff_SOMMapManager","setProductBMUs","Finished finding bmus for all product data. Start adding product data to appropriate bmu's list.", MsgCodes.info1);
			_completeBMUProcessing(productData, SOM_ExDataType.Product, false);		
		}
		setProdDataBMUsMapped(true);
		getMsgObj().dispMessage("Straff_SOMMapManager","setProductBMUs","Finished Mapping products to best matching units.", MsgCodes.info5);
	}//setProductBMUs

	//match true prospects to current map/product mappings
	public void buildAndSaveTrueProspectReport() {
		if (!getSOMMapNodeDataIsLoaded()) {	getMsgObj().dispMessage("Straff_SOMMapManager","setTrueProspectBMUs","No SOM Map data has been loaded or processed; aborting", MsgCodes.error2);		return;}
		if (!truePrspctExMapper.isDataLoaded()) {
			getMsgObj().dispMessage("Straff_SOMMapManager","setTrueProspectBMUs","No true prospects loaded, attempting to load.", MsgCodes.info5);
			loadAllTrueProspectData();
		}	
		setValidationDataBMUs();
		getMsgObj().dispMessage("Straff_SOMMapManager","setTrueProspectBMUs","Finished processing true prospects for BMUs.", MsgCodes.info1);	
	}//buildAndSaveTrueProspectReport
	
	///////////////////////////////
	// segment reports and saving
	
	/**
	 * This will load true prospects, map them and save their mappings
	 */
	public void saveBMUMapsForTruPrspcts() {
		if (!getSOMMapNodeDataIsLoaded()) {	getMsgObj().dispMessage("Straff_SOMMapManager","saveExamplesToSOMMappings","No Mapped data has been loaded or processed; aborting", MsgCodes.error2);		return;}		
		getMsgObj().dispMessage("Straff_SOMMapManager","saveExamplesToSOMMappings","Starting saving all segment data for Classes, Categories and BMUs.", MsgCodes.info5);	
		saveAllSegment_BMUReports();
		getMsgObj().dispMessage("Straff_SOMMapManager","saveExamplesToSOMMappings","Finished saving all segment data for Classes, Categories and BMUs | Start saving all example->bmu mappings.", MsgCodes.info5);			

		String subDir = projConfigData.getPreProcDataDesiredSubDirName();
		loadAndMapTrueProspects(subDir);		
		
		getMsgObj().dispMessage("Straff_SOMMapManager","saveExamplesToSOMMappings","Finished saving all example->bmu mappings.", MsgCodes.info5);
		
	}//saveAllExamplesToSOMMappings	

	@Override
	public String getFtrWtSegmentTitleString(SOM_FtrDataType ftrCalcType, int ftrIDX) {		
		String ftrTypeDesc = getDataDescFromInt(ftrCalcType);
		return "Feature Weight Segment using " + ftrTypeDesc +" examples for ftr idx : " + ftrIDX+ " corresponding to JP :"+ jpJpgrpMon.getFtrJpByIdx(ftrIDX);
	}

	@Override
	public String getClassSegmentTitleString(int classID) {	return "Job Practice, Probability (Class), Segment of training data mapped to node possessing orders in specified JP  : " + classID + " | "+ jpJpgrpMon.getFtrJpStrByJp(classID);}
	@Override
	public String getCategorySegmentTitleString(int catID) {return "Job Practice, Group (Category), Probability Segment of training data mapped to node possessing orders in specified JP Group  : " + catID + " | "+ jpJpgrpMon.getFtrJpGrpStrByJpg(catID);}	
	
	public String getNonProdJPSegmentTitleString(int npJpID) {return "Job Practice, Probability Segment, of training data mapped to node possessing Non-product-related JP  : " + npJpID + " | "+ jpJpgrpMon.getAllJpStrByJp(npJpID);}
	
	public String getNonProdJPGroupSegmentTitleString(int npJpgID) {return "Job Practice, Group Probability, Segment of training data mapped to node possessing Non-product-related JPgroup  : " + npJpgID + " | "+ jpJpgrpMon.getAllJpGrpStrByJpg(npJpgID);}

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
	protected void saveNonProdJp_BMUReport(){		_saveSegmentReports(nonProdJP_Segments,projConfigData.getSegmentFileNamePrefix("nonprod_jps",""));}
	//save non-prod-jpgroup segment information
	protected void saveNonProdJpGroup_BMUReport(){		_saveSegmentReports(nonProdJpGroup_Segments,projConfigData.getSegmentFileNamePrefix("nonprod_jpgroups",""));}
	

	/////////////////////////////////////////
	//map building, drawing and graphics methods - these must check if win and/or pa exist, or else except win or pa as passed arguments, to manage when this code is executed without UI
	//called from SOMMapUIWin base on initMapAras - this instances 2ndary maps/other instance-specific maps
	@Override
	protected void initMapArasIndiv(int w, int h, int format, int num2ndryMaps) {
		curAllJPToShowIDX = 0;
		mapPerJpgWtImgs = new PImage[num2ndryMaps];
		for(int i=0;i<mapPerJpgWtImgs.length;++i) {
			mapPerJpgWtImgs[i] = myDispWindow.pa.createImage(w, h, format);
		}	
	}//instance-specific init 
	
	/**
	 * Strafford-specific SOM mouse over value display - TODO
	 */
	@Override
	protected final void getDataPointAtLoc_Priv(float x, float y, float sensitivity, SOM_MapNode nearestNode, myPointf locPt,int _uiMseDispData) {
		setMseDataExampleNodeName(locPt,nearestNode,sensitivity);		
	}
	
	@Override
	//in base class function, clicking on map selects or deselects the closest node
	protected boolean checkMouseClick_Indiv(int mouseX, int mouseY, float mapX, float mapY, SOM_MapNode nearestNode,myPoint mseClckInWorld, int btn, boolean _wasSelNotDeSel) {
		// TODO Auto-generated method stub
		return _wasSelNotDeSel;
	}

	@Override
	public boolean checkMouseDragMove_Indiv(int mouseX, int mouseY, int pmouseX, int pmouseY, myPoint mouseClickIn3D,myVector mseDragInWorld, int mseBtn) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected void checkMouseRelease_Indiv() {	}
	
	
	/**
	 * instancing application should determine whether we want to display features sorted in magnitude order, or sorted in idx order
	 * @param ptrLoc
	 * @param ftrs
	 * @param sens
	 * @return
	 */
	@Override
	public final void setMseDataExampleFtrs(myPointf ptrLoc, TreeMap<Integer, Float> ftrs, float sens) {
		setMseDataExampleFtrs_WtSorted(ptrLoc, ftrs, sens);
	}

	public void _drawAnalysis(my_procApplet pa, int exCalcedIDX, int mapDrawAnalysisIDX) {
		if (win.getPrivFlags(exCalcedIDX)){	
			//determine what kind of jps are being displayed 
			//int curJPIdx = ( ? curMapImgIDX : curAllJPToShowIDX);
			pa.pushMatrix();pa.pushStyle();	
			pa.translate(calcAnalysisLocs[0],SOM_mapLoc[1]*calcScale + 10,0.0f);			
			if(curCalcAnalysisJPTypeIDX == Straff_WeightCalc.bndAra_AllJPsIDX) {		//choose between displaying calc analysis of training feature jps or all jps
				drawAnalysisOneJp_All(pa,analysisHt, analysisPerJPWidth,curAllJPToShowIDX, getCurCalcAnalysisSrcDataTypeIDX());	
				pa.popStyle();pa.popMatrix();			
				pa.pushMatrix();pa.pushStyle();
				pa.translate(win.rectDim[0]+5,calcAnalysisLocs[1],0.0f);					
				drawAnalysisAllJps(pa, analysisHt, analysisAllJPBarWidth, curAllJPToShowIDX, getCurCalcAnalysisSrcDataTypeIDX());
				
			} else if(curCalcAnalysisJPTypeIDX == Straff_WeightCalc.bndAra_ProdJPsIDX)  {		
				drawAnalysisOneJp_Ftr(pa,analysisHt, analysisPerJPWidth,curProdToShowIDX, getCurCalcAnalysisSrcDataTypeIDX());	
				pa.popStyle();pa.popMatrix();			
				pa.pushMatrix();pa.pushStyle();
				pa.translate(win.rectDim[0]+5,calcAnalysisLocs[1],0.0f);					
				drawAnalysisFtrJps(pa, analysisHt, analysisAllJPBarWidth, curProdToShowIDX, getCurCalcAnalysisSrcDataTypeIDX());				
			}			
			
			pa.popStyle();pa.popMatrix();
			pa.scale(calcScale);				//scale here so that if we are drawing calc analysis, ftr map image will be shrunk
		} else {
			win.setPrivFlags(mapDrawAnalysisIDX, false);
		}
	}//_drawAnalysis

	
	@Override
	//stuff to draw specific to this instance, before nodes are drawn
	protected void drawMapRectangle_Indiv(my_procApplet pa, int curImgNum) {
		if(win.getPrivFlags(Straff_SOMMapUIWin.mapDrawTruePspctIDX)){			drawValidationData(myDispWindow.pa);}
		
		if (win.getPrivFlags(Straff_SOMMapUIWin.mapDrawCurProdFtrBMUZoneIDX)){		drawProductRegion(pa,curProdToShowIDX,prodZoneDistThresh);}
		//not drawing any analysis currently
		boolean notDrawAnalysis = !(win.getPrivFlags(Straff_SOMMapUIWin.mapDrawCustAnalysisVisIDX) || win.getPrivFlags(Straff_SOMMapUIWin.mapDrawTPAnalysisVisIDX));
		if (notDrawAnalysis ){	drawMseOverData(pa);}//draw mouse-over info if not showing calc analysis		 		
	}//drawMapRectangleIndiv
	/**
	 * draw instance-specific per-ftr map display
	 */
	@Override
	protected void drawPerFtrMap_Indiv(my_procApplet pa) {
		if(win.getPrivFlags(Straff_SOMMapUIWin.mapDrawPrdctFtrBMUsIDX)){				drawProductNodes(pa, curFtrMapImgIDX, true);}
		if(win.getPrivFlags(Straff_SOMMapUIWin.mapDrawNonProdJPSegIDX)) {	 			drawNonProdJpSegments(pa,curAllJPToShowIDX);	}		
		if(win.getPrivFlags(Straff_SOMMapUIWin.mapDrawNonProdJPGroupSegIDX)) { 			drawNonProdJPGroupSegments(pa,curAllJPToShowIDX);	}	
	}
	
	@Override
	/**
	 * Instancing class-specific segments to render during UMatrix display
	 */
	protected void drawSegmentsUMatrixDispIndiv(my_procApplet pa) {
		if(win.getPrivFlags(Straff_SOMMapUIWin.mapDrawNonProdJPSegIDX)) {	 			drawAllNonProdJpSegments(pa);}
		if(win.getPrivFlags(Straff_SOMMapUIWin.mapDrawNonProdJPGroupSegIDX)) { 			drawAllNonProdJPGroupSegments(pa);}
		if(win.getPrivFlags(Straff_SOMMapUIWin.mapDrawPrdctFtrBMUsIDX)){				drawAllProductNodes(pa);}
	}	
	/*(win.getPrivFlags(Straff_SOMMapUIWin.xes around each node representing class-based segments that node 
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
		//getMsgObj().dispMessage("Straff_SOMMapManager","drawAllNonProdJpSegments","Drawing "+nonProdJP_Segments.size()+" nonprod jp segments", MsgCodes.info5);
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
	private int getProdDistType() {return (getFlag(mapExclProdZeroFtrIDX) ? Straff_ProductExample.SharedFtrsIDX : Straff_ProductExample.AllFtrsIDX);}
		//display the region of the map expected to be impacted by the products serving the passed jp 
	public void drawProductRegion(my_procApplet pa, int prodJpIDX, double maxDist) {	prodExMapper.drawProductRegion(pa,prodJpIDX, maxDist, getProdDistType());}//drawProductRegion
	
	@Override
	protected float getPreBuiltMapInfoDetail(my_procApplet pa, String[] str, int idx, float yOff, boolean isLoaded) {
		int clrIDX = (isLoaded ? IRenderInterface.gui_Yellow : IRenderInterface.gui_White);
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
	
	//win UI-based variables
	public int getCurProdToShowIDX() {		return curProdToShowIDX;	}
	public void setCurProdToShowIDX(int curProdToShowIDX) {		this.curProdToShowIDX = curProdToShowIDX;	}
	public double getProdZoneDistThresh() {		return prodZoneDistThresh;	}
	public void setProdZoneDistThresh(double prodZoneDistThresh) {		this.prodZoneDistThresh = prodZoneDistThresh;	}
	public int getCurAllJPToShowIDX() {		return curAllJPToShowIDX;	}
	public void setCurAllJPToShowIDX(int curAllJPToShowIDX) {		this.curAllJPToShowIDX = curAllJPToShowIDX;	}
	public float getAnalysisHt() {		return analysisHt;	}
	public void setAnalysisHt(float analysisHt) {		this.analysisHt = analysisHt;	}
	public float getAnalysisPerJPWidth() {		return analysisPerJPWidth;	}
	public void setAnalysisPerJPWidth(float analysisPerJPWidth) {		this.analysisPerJPWidth = analysisPerJPWidth;	}	
	public float[] getCalcAnalysisLocs() {		return calcAnalysisLocs;	}
	//locs for calc analysis output
	public void setCalcAnalysisLocs() {		this.calcAnalysisLocs = new float[] {(SOM_mapLoc[0]+mapDims[0])* calcScale + 20.0f,(SOM_mapLoc[1]+mapDims[1])*calcScale + 10.0f};}	
	public float getAnalysisAllJPBarWidth() {return analysisAllJPBarWidth;}
	/**
	 * set calc analysis display width based on width of current display
	 * @param currentVisScrWidth current width of screen, determined by whether right sidebar is shown or not
	 */
	public void setAnalysisAllJPBarWidth(float currentVisScrWidth) {	analysisAllJPBarWidth = (currentVisScrWidth/(1.0f+numFtrsToShowForCalcAnalysis(curCalcAnalysisJPTypeIDX)))*.98f;}
		
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
	public void processCalcAnalysis() {	if (ftrCalcObj != null) {ftrCalcObj.finalizeCalcAnalysis(curCalcAnalysisSrcDataTypeIDX);} else {getMsgObj().dispInfoMessage("Straff_SOMMapManager","processCalcAnalysis", "ftrCalcObj == null! attempting to disp res for type : " + curCalcAnalysisSrcDataTypeIDX);}}
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
		getMsgObj().dispMessage("Straff_SOMMapManager","dbgShowSOM_MapDat","Starting displaying current SOM_MapDat object.", MsgCodes.info5);
		getMsgObj().dispMultiLineInfoMessage("Straff_SOMMapManager","dbgShowSOM_MapDat","\n"+projConfigData.SOM_MapDat_ToString()+"\n");		
		getMsgObj().dispMessage("Straff_SOMMapManager","dbgShowSOM_MapDat","Finished displaying current SOM_MapDat object.", MsgCodes.info5);
	}
	public void dbgShowAllRawData() {		rawDataLdr.dbgShowAllRawData();}//showAllRawData
	//debugging function to display all unique jps seen in data
	public void dbgShowUniqueJPsSeen() {	jpJpgrpMon.dbgShowUniqueJPsSeen();}//dbgShowUniqueJPsSeen	
	public void dbgDispKnownJPsJPGs() {		jpJpgrpMon.dbgDispKnownJPsJPGs();	}//dbgDispKnownJPsJPGs
	
	//display current calc function's equation coefficients for each JP
	public void dbgShowCalcEqs() {
		if (null == ftrCalcObj) {	getMsgObj().dispMessage("Straff_SOMMapManager","dbgShowCalcEqs","No calc object made to display.", MsgCodes.warning1);return;	}
		getMsgObj().dispMessage("Straff_SOMMapManager","dbgShowCalcEqs","Weight Calculation Equations : \n"+ftrCalcObj.toString(), MsgCodes.info1);		
	}

	public void dbgShowJpJpgrpData() {		getMsgObj().dispMessage("Straff_SOMMapManager","dbgShowJpJpgrpData","Showing current jpJpg Data : \n"+jpJpgrpMon.toString(), MsgCodes.info1);	}
	
	//check and increment relevant counters if specific events are found in a particular example
	private void dbgEventInExample(Straff_ProspectExample ex, int[] countsOfBoolResOcc, int[] countsOfBoolResEvt) {
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
		String[] prodExVals = Straff_ProductExample.getMinMaxDists();
		getMsgObj().dispMessageAra(prodExVals,"Straff_SOMMapManager", "SOMMapManager::finishSOMExampleBuild : spans of all product ftrs seen", 1, MsgCodes.info1);		
	}//dbgDispProductWtSpans()
	

	//TODO need to manage this
	public String toString(){
		String res = super.toString();
		return res;
	}

	public int getCurCalcAnalysisJPTypeIDX() {
		return curCalcAnalysisJPTypeIDX;
	}

	public void setCurCalcAnalysisJPTypeIDX(int curCalcAnalysisJPTypeIDX) {
		this.curCalcAnalysisJPTypeIDX = curCalcAnalysisJPTypeIDX;
	}

	public int getCurCalcAnalysisSrcDataTypeIDX() {
		return curCalcAnalysisSrcDataTypeIDX;
	}

	public void setCurCalcAnalysisSrcDataTypeIDX(int curCalcAnalysisSrcDataTypeIDX) {
		this.curCalcAnalysisSrcDataTypeIDX = curCalcAnalysisSrcDataTypeIDX;
		processCalcAnalysis();	
	}

}//Straff_SOMMapManager
