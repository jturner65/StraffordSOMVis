package strafford_SOM_PKG.straff_SOM_Examples.prospects;

import java.util.*;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.ExDataType;
import base_Utils_Objects.MsgCodes;
import base_Utils_Objects.Tuple;

import strafford_SOM_PKG.straff_RawDataHandling.*;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.*;
import strafford_SOM_PKG.straff_SOM_Examples.EvtDataType;
import strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.events.StraffEvntRawToTrainData;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

/**
 * 	this class holds a customer prospect example, that will either be used to 
 * generate training data for the SOM, or else will only be queried against the SOM 
 * raw prospects default to custProspectExample, and are converted to 
 * trueProspectExample if they don't qualify as training data
 * @author john
 *
 */
public class CustProspectExample extends ProspectExample{
	//column names for csv of this SOM example
	private static final String csvColDescrPrfx = "OID,Prospect_LU_Date,Num Order Event Dates,Num Opt Event Dates,Num Link Event Dates,Num Src Event Dates";
	
	//////////////////////////////////////
	//Training data description
	//////////////////////////////////////
	//all kinds of events present
	public static final String[] jpOccTypeKeys = new String[] {"orders", "opts", "links", "sources"};
	//events allowable for training (source info can be used if jps present in other events as well, but should be ignored otherwise)
	private static final String[] trainingEventMapTypeKeys = new String[] {"orders", "opts", "links"};//events used only to determine training
	//event mappings use the occurrence data value (opt/source)
	protected static final boolean[] jpOccMapUseOccData = new boolean[] {false,true,false,true};
	//csv labels
	private static final String[] CSVSentinelLbls = new String[] {"ORD|,","OPT|,", "LNK|,", "SRC|," };
	
	//array of individual order training examples corresponding to this customer-prospect's order history - these examples will be used for -training the map-. 
	//the actual feature vector will be used by the customer for comparisons to products etc.
	private TreeMap<String, Cust_OneOrderTrainingExample> mapOfSingleOrderTrainingExamples;
	//map holding count of orders on most recent date - these will be used as a class for the bmu node if this example is used as a training example
	public TreeMap<Tuple<Integer, Integer>, Integer> mostRecentOrderCounts;

	
	//build this object based on prospectData object from raw data
	public CustProspectExample(SOMMapManager _map,ProspectData _prspctData) {	super(_map,ExDataType.Training, _prspctData);}//prospectData ctor
	
	//build this object based on csv string - rebuild data from csv string columns 4+
	public CustProspectExample(SOMMapManager _map,String _OID, String _csvDataStr) {
		super(_map,ExDataType.Training, _OID,_csvDataStr);	
		String[] dataAra = _csvDataStr.split(",");
		//idx 0 : OID; idx 1 : date
		prs_LUDate = BaseRawData.buildDateFromString(dataAra[1]);		
		//get # of events - need to accommodate source events
		int[] numEvsAra = _getCSVNumEvsAra(dataAra);
		//Build data here from csv string
		buildDataFromCSVString_jpOcc(numEvsAra, _csvDataStr,jpOccTypeKeys,CSVSentinelLbls);	
		buildJPListsAndSetBadExample();
	}//csv string ctor
	
	//using this ctor for Cust_OneOrderTrainingExample building.
	public CustProspectExample(SOMMapManager _map,String _OID) {		super(_map,ExDataType.Training,_OID, "");	}//super ctor from Cust_OneOrderTrainingExample ctor
	
	public CustProspectExample(CustProspectExample ex) {		super(ex);		}//copy ctor - if ex is this type then don't have to rebuild JpOccurrences and eventsByDateMap;		
	
	//this is customer-specific 
	@Override
	protected final void _initObjsIndiv() {
		if(eventsByDateMap != null) {for (String key : jpOccTypeKeys) {	eventsByDateMap.put(key, new TreeMap<Date, TreeMap<Integer, StraffEvntRawToTrainData>> ());	}}
		
		for (String key : jpOccTypeKeys) {	JpOccurrences.put(key, new TreeMap<Integer,JP_OccurrenceData>());}
		//set these when this data is partitioned into testing and training data
		isTrainingData = false;
		testTrainDataIDX = -1;
		mapOfSingleOrderTrainingExamples = new TreeMap<String, Cust_OneOrderTrainingExample>();
	}//initObjsData

	//add raw event data to this customer prospect
	public void addEventObj(BaseRawData obj, int type) {
		switch(type) {
		case StraffSOMRawDataLdrCnvrtr.prspctIDX 	: 	{msgObj.dispMessage("custProspectExample","addObj","ERROR attempting to add prospect raw data as event data. Ignored", MsgCodes.error2);return;}
		case StraffSOMRawDataLdrCnvrtr.orderEvntIDX : 	{		addDataToTrainMap((OrderEvent)obj,eventsByDateMap.get(jpOccTypeKeys[0]), EvtDataType.Order); 		return;}
		case StraffSOMRawDataLdrCnvrtr.optEvntIDX 	: 	{		addDataToTrainMap((OptEvent)obj,eventsByDateMap.get(jpOccTypeKeys[1]), EvtDataType.Opt); 		return;}
		case StraffSOMRawDataLdrCnvrtr.linkEvntIDX 	: 	{		addDataToTrainMap((LinkEvent)obj,eventsByDateMap.get(jpOccTypeKeys[2]), EvtDataType.Link); 		return;}
		case StraffSOMRawDataLdrCnvrtr.srcEvntIDX 	: 	{		addDataToTrainMap((SourceEvent)obj,eventsByDateMap.get(jpOccTypeKeys[3]), EvtDataType.Source); 		return;}
		default :{msgObj.dispMessage("custProspectExample","addObj","ERROR attempting to add unknown raw data type : " + type + " as event data. Ignored", MsgCodes.error2);return;}
		}		
	}//addObj
	
	//any processing that must occur once all constituent data records are added to this example - must be called externally, BEFORE ftr vec is built
	//---maps event data to occurrence structs; builds allJPs list
	//needs to have separate instances for customers and true prospects because they have different jpOccTypeKeys, jpOccMapUseOccData lists
	@Override
	public final void finalizeBuildBeforeFtrCalc() {
		if(jpOccNotBuiltYet) {
			buildOccurrenceStructs(jpOccTypeKeys, jpOccMapUseOccData);
			//allprodjps holds all jps in this example based on occurences that will be used in training; will not reference jps implied by opt-all records
			buildJPListsAndSetBadExample();
		}
	}//finalize
	
    //take loaded data and convert to feature data via calc object 
	public static TreeMap<Integer,Integer[]> ttlOrderCount = new TreeMap<Integer,Integer[]>();
	public static TreeMap<Integer,Integer[]> ttlBadOrderCount = new TreeMap<Integer,Integer[]>();
	private void dbg_addOrderCountToTTLMap() {
		addOrderToOrderCountMap(JpOccurrences.get("orders"),ttlOrderCount);	
//		int numOrders = map.size();//# of different jps having orders
//		Integer[] orderDat = ttlOrderCount.get(numOrders);//array of values - first value is count of different jp occurrences, 2nd value is count of all orders for all jps
//		if(orderDat==null) {orderDat = new Integer[2];orderDat[0]=0;orderDat[1]=0;}
//		orderDat[0] +=1;								//this customer has this many different unique jp orders
//		int numOccsAllOrders = 0;						//this customer has this many unique order dates of all jps
//		for(JP_OccurrenceData occ  : map.values()) {			numOccsAllOrders += occ.getNumberOfOccurrences();		}
//		orderDat[1] += numOccsAllOrders;
//		ttlOrderCount.put(numOrders, orderDat);
	}
	private void dbg_addBadOrderCountToTTLMap() {		addOrderToOrderCountMap(JpOccurrences.get("orders"),ttlBadOrderCount);		}
	
	private synchronized static void addOrderToOrderCountMap(TreeMap<Integer, JP_OccurrenceData> map, TreeMap<Integer,Integer[]> orderCountMap) {
		int numOrders = map.size();
		Integer[] orderDat = orderCountMap.get(numOrders);//array of values - first value is count of different jp occurrences, 2nd value is count of all orders for all jps
		if(orderDat==null) {orderDat = new Integer[2];orderDat[0]=0;orderDat[1]=0;}
		orderDat[0] +=1;								//this customer has this many different unique jp orders
		int numOccsAllOrders = 0;						//this customer has this many unique order dates of all jps
		for(JP_OccurrenceData occ  : map.values()) {			numOccsAllOrders += occ.getNumberOfOccurrences();		}
		orderDat[1] += numOccsAllOrders;
		orderCountMap.put(numOrders, orderDat);
	}
	
	//this will build training features based on individual orders - each individual order will correspond to a training feature
	private void buildDateBasedOccsAndTrainingFtrData() {
		//this is a list of all the training data this customer is responsible for.
		//1 or more order event-based training feature vectors - this is only used for training the map - each example contains all non-order events leading to an order, time decayed with current equation settings
		//
		//struct to hold all event occurences, keyed by type, then keyed by date, with value being a single event occurrence
		TreeMap<Date, TreeMap<String, DateEvent_OccurrenceData>> dateEventOccurrences = new TreeMap<Date, TreeMap<String, DateEvent_OccurrenceData>>(); 
		//just order event occurrences
		TreeMap<Date, DateEvent_OccurrenceData> dateOrderEventOccs = new TreeMap<Date, DateEvent_OccurrenceData>(); 
		//build dateEventOccurrences object based on JpOccurrences, by pivoting on date for every occurrence	
		for(String jpOccType : jpOccTypeKeys) {
			TreeMap<Integer, JP_OccurrenceData> jpOccs = JpOccurrences.get(jpOccType);
			TreeMap<String, DateEvent_OccurrenceData> eventsAllTypesOnDate;
			TreeMap<Integer, Integer> occDat;
			Tuple<Integer, Integer> jpgJp;
			Set<Date> dates;
			JP_OccurrenceData jpOcc;
			DateEvent_OccurrenceData eventTypeOnDate;
			//dateEventOccurrences is keyed by date, then by jpOccType
			for(Integer jp : jpOccs.keySet()) {
				jpOcc = jpOccs.get(jp);
				jpgJp = jpOcc.getJpgJp();
				dates = jpOcc.getDatesInOrderSet();
				for (Date date : dates) {//all occurrences for jp,jpg at particular dates
					occDat = jpOcc.getOccurrences(date);														//get JP occurrernce data for specific date
					eventsAllTypesOnDate = dateEventOccurrences.get(date);										//get currently known events of all types on that date, make if null
					if(eventsAllTypesOnDate == null) {
						eventsAllTypesOnDate = new TreeMap<String, DateEvent_OccurrenceData>();
						dateEventOccurrences.put(date, eventsAllTypesOnDate);
					}
					eventTypeOnDate = eventsAllTypesOnDate.get(jpOccType);										//get currently known specific type of event on date, make if null
					if(eventTypeOnDate == null) {
						eventTypeOnDate = new DateEvent_OccurrenceData(date, jpOccType);
						eventsAllTypesOnDate.put(jpOccType,eventTypeOnDate);
					}
					eventTypeOnDate.addOcc(jpgJp, occDat);//build event on date
					if(jpOccType=="orders") {			dateOrderEventOccs.put(date, eventTypeOnDate);	}		//build a map of just order events
				}				
			}		
		}
		
		//by here we have date-sorted events of all types,
		//this map will hold these values, keyed by Cust_OneOrderTrainingExample ex's OID - 1 Cust_OneOrderTrainingExample per order event
		mapOfSingleOrderTrainingExamples.clear();
		int exID = 0;
		for(Date orderDate : dateOrderEventOccs.keySet()) {//get order events and dates
			DateEvent_OccurrenceData orderOcc = dateOrderEventOccs.get(orderDate);
			Cust_OneOrderTrainingExample ex = new Cust_OneOrderTrainingExample(this, OID+"_"+String.format("%3d", exID++), orderOcc, dateEventOccurrences.headMap(orderDate, true));//true : includes events on date
			mapOfSingleOrderTrainingExamples.put(ex.OID, ex);			
		}
		mostRecentOrderCounts = dateOrderEventOccs.lastEntry().getValue().getOccurrenceCounts();
	}//buildDateBasedOccsAndTrainFtrData	
	
	@Override
	//standardize this feature vector - across each feature, set value to be between 0 and 1
	protected final void buildStdFtrsMap() {		
		if (allNonZeroFtrIDXs.size() > 0) {calcStdFtrVector(ftrMaps[ftrMapTypeKey],ftrMaps[stdFtrMapTypeKey], mapMgr.getTrainFtrMins(), mapMgr.getTrainFtrDiffs());}
		else {clearFtrMap(stdFtrMapTypeKey);}//ftrMaps[stdFtrMapTypeKey].clear();}
		if((mapOfSingleOrderTrainingExamples != null) && (mapOfSingleOrderTrainingExamples.size() > 0)) {		for(Cust_OneOrderTrainingExample ex : mapOfSingleOrderTrainingExamples.values() ) {	ex.buildAfterAllFtrVecsBuiltStructs();} }
		setFlag(stdFtrsBuiltIDX,true);
	}//buildStdFtrsMap
	
	public final TreeMap<String, Cust_OneOrderTrainingExample> getMapOfSingleOrderTrainingExamples(){	return mapOfSingleOrderTrainingExamples;}
	//to return all single order training examples in an array to build input array
	public final Collection<Cust_OneOrderTrainingExample> getSingleOrderTrainingExamples() {return mapOfSingleOrderTrainingExamples.values();}
	
	//return a map keyed by jpg,jp tuple, with value being counts of orders of this jp/jpg on this date - these will be used as a class for the bmu node 
	//THIS IS ONLY USED WHEN THIS EXAMPLE IS A TRAINING EXAMPLE
	public TreeMap<Tuple<Integer, Integer>, Integer> getOrderCountsForExample(){return mostRecentOrderCounts;}		
	
	public static int NumBadExamplesAfterFtrsBuilt = 0;
	public static int NumBadExampleOrdersAfterFtrsBuilt = 0;
	@Override
	protected void buildFeaturesMap() {
		//access calc object		
		if (allProdJPs.size() > 0) {
			//this is to monitor order counts - purely debug/informational
			dbg_addOrderCountToTTLMap();
			clearFtrMap(ftrMapTypeKey);//
			((Straff_SOMMapManager)mapMgr).ftrCalcObj.calcCustFtrDataVec(this,allProdJPs, ftrMaps[ftrMapTypeKey],JpOccurrences);	
			//if is good training data, then build date-based occurrence structs and calculate actual training data
			if(!isBadExample()) {buildDateBasedOccsAndTrainingFtrData();}
			else {
				++NumBadExamplesAfterFtrsBuilt; 
				NumBadExampleOrdersAfterFtrsBuilt+= JpOccurrences.get("orders").size(); 
				dbg_addBadOrderCountToTTLMap();
				mostRecentOrderCounts = new TreeMap<Tuple<Integer, Integer>, Integer>(); 
			}//if bad then won't contribute any training data - shouldn't be used to train!!! 
			
		} else {ftrMaps[ftrMapTypeKey].clear();}
		//now, if there's a non-null posOptAllEventObj then for all jps who haven't gotten an opt conribution to calculation, add positive opt-all result
	}//buildFeaturesMap
	
	//this will build the training-ftr configured vector contribution for this example to all non-product jps this example has.
	//build off un-modified features, since this will construct a vector that will then be used as the unmodified features of the True prospect records sharing the non-prod jps
	public final void buildNonProdJpFtrVec() {if(nonProdJpgJps.size() > 0) {		((Straff_SOMMapManager)mapMgr).ftrCalcObj.buildCustNonProdFtrVecs(this, ftrMaps[ftrMapTypeKey], nonProdJpgJps, JpOccurrences.get("sources"));}}	
	
	@Override
	/**
	 *  this will build the comparison feature vector array that is used as the comparison vector in distance measurements
	 * @param _ratio : 0 means all base ftrs, 1 means all compValMap for features
	 */
	public void buildCompFtrVector(float _ratio) {
		//ratio needs to be [0..1], is ratio of compValMaps value to ftr value
		if(_ratio <=0)  {compFtrMaps = ftrMaps;}
		else {  
			//must be called after compValMaps have been populated by customer data
			calcCompValMaps();//<--- this is only currently built on demand for customer prospects - no need for it otherwise
			//if no features then just use complete comValFtrDataMaps
			if(_ratio >= 1){compFtrMaps = compValFtrDataMaps;}
			else {
				clearAllCompFtrMaps();
				Float val;
				for(int mapIdx = 0; mapIdx < ftrMaps.length;++mapIdx) {
					TreeMap<Integer, Float> ftrMap = ftrMaps[mapIdx];
					TreeMap<Integer, Float> compMap = compValFtrDataMaps[mapIdx];
					Set<Integer> allIdxs = new HashSet<Integer>(ftrMap.keySet());
					allIdxs.addAll(compMap.keySet());
					for (Integer key : allIdxs) {//either map will have this key
						Float frmVal = ftrMap.get(key);if(frmVal == null) {frmVal = 0.0f;}
						Float toVal = compMap.get(key);
						val = (toVal == null) ? frmVal : (_ratio * toVal) + (1.0f - _ratio)*frmVal;
						compFtrMaps[mapIdx].put(key, val);					
					}//for all idxs			
				}//for map idx
			}//if ration >= 1 else
		}
	}//buildCompFtrVector
	
	//check whether this prospect has a particular jp in either his prospect data, events or opts
	//idxs : 0==if in an order; 1==if in an opt, 2 == if is in link, 3==if in source event,4 if in any non-source event (denotes action by customer), 5 if in any event, including source, 
	//it is assumed that hasJP_event is after all event booleans (if future bools are added)
	//needs to follow format of eventMapTypeKeys
	@Override
	public final boolean[] hasJP(Integer _jp) {
		boolean	hasJP_ordr = (JpOccurrences.get("orders").get(_jp) != null) ,
				hasJP_opt = (JpOccurrences.get("opts").get(_jp) != null),
				hasJP_link = (JpOccurrences.get("links").get(_jp) != null),	
				hasJP_source = (JpOccurrences.get("sources").get(_jp) != null),			//do not treat source data as an event in this case
				hasJP_event = hasJP_ordr || hasJP_opt || hasJP_link,					//not counting source data
				hasJP = hasJP_event || hasJP_source;
		boolean[] res = new boolean[] {hasJP_ordr, hasJP_opt, hasJP_link, hasJP_source, hasJP_event, hasJP};
		return res;		
	}//check if JP exists, and where
	
	
	//return boolean array describing this example's occurence structure
	//idxs : 0==if in an order; 1==if in an opt, 2 == if is in link, 3==if in source event,4 if in any non-source event (denotes action by customer), 5 if in any event, including source, 
	//needs to follow format of eventMapTypeKeys
	@Override
	public final boolean[] getExampleStatusOcc() {
		boolean	has_ordr = (JpOccurrences.get("orders").size() !=0) ,
				has_opt = (JpOccurrences.get("opts").size() !=0),				//seems to not correspond to size of eventsByDateMap.get("opts").size()
				has_link = (JpOccurrences.get("links").size() !=0),	
				has_source = (JpOccurrences.get("sources").size() !=0),			//do not treat source data as an event in this case
				has_event = has_ordr || has_opt || has_link;					//not counting source data
		boolean[] res = new boolean[] {has_ordr, has_opt, has_link, has_source,has_event};
		return res;		
	}//
	
	@Override
	public final void setIsTrainingDataIDX_Priv() {
		type= isTrainingData ? ExDataType.Training : ExDataType.Testing;
		nodeClrs = mapMgr.getClrFillStrkTxtAra(type);
	}//setIsTrainingDataIDX

	//column names for raw descriptorCSV output
	@Override
	public final String getRawDescColNamesForCSV(){	return csvColDescrPrfx + ",";	}
	//get #'s of events from partitioned csv data held in dataAra - need to accommodate source events - idxs depend on how the data was originally built
	@Override
	protected final int[] _getCSVNumEvsAra(String[] dataAra) {return new int[] {Integer.parseInt(dataAra[2]),Integer.parseInt(dataAra[3]),Integer.parseInt(dataAra[4]),Integer.parseInt(dataAra[5])};}
	@Override
	protected final String[] getEventMapTypeKeys() {	return jpOccTypeKeys;}
	@Override
	protected final String[] getCSVSentinelLbls() {return CSVSentinelLbls;}
	//this prospect is an actual customer - use as training data - not all custPropsects will have this true, since this is the initial class that is used to build the data
	@Override
	public boolean isTrainablePastCustomer() { return !getFlag(isBadTrainExIDX) && (JpOccurrences.get("orders").size() > 0);}
	
	//returns true if -any training-related- events are present in this record (i.e. not counting source "events")
	@Override
	protected final boolean hasRelevantTrainingData() {
		boolean res = false;
		for (String key : trainingEventMapTypeKeys) {if (JpOccurrences.get(key).size() > 0) {return true;}	}
		return res;
	}//hasRelelventEvents	
	
	//this will return the training label(s) of this example - all jps seen in orders
	//the training label corresponds to a tag or a class referring to the data that can be assigned to a map node - a vote about the bmu from this example
	//if not training data then no label will exist;  might not exist if it is training data either, if fully unsupervised
	@Override
	public TreeMap<Integer,Integer> getTrainingLabels() {	
		TreeMap<Integer,Integer> res = new TreeMap<Integer,Integer>();
		TreeMap<Integer, JP_OccurrenceData> map = JpOccurrences.get("orders");
		for(Integer jp : map.keySet()) {			res.put(jp, map.get(jp).getNumberOfOccurrences());		}
		return res;
	}
	
	@Override
	public String toString() {	
		String res = "Customer : " + super.toString();
		for (String key : jpOccTypeKeys) {
			//res += toStringDateMap(eventsByDateMap.get(key), key);
			res += toStringOptOccMap(JpOccurrences.get(key), key + " occurences");
		}
		return res;
	}
}//class custProspectExample

