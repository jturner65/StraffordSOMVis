package strafford_SOM_PKG.straff_SOM_Examples;

import java.util.*;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.ExDataType;
import base_Utils_Objects.MsgCodes;
import strafford_SOM_PKG.straff_RawDataHandling.*;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.*;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
import strafford_SOM_PKG.straff_Utils.StraffWeightCalc;

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
	private static final boolean[] jpOccMapUseOccData = new boolean[] {false,true,false,true};
	//csv labels
	private static final String[] CSVSentinelLbls = new String[] {"ORD|,","OPT|,", "LNK|,", "SRC|," };	
	
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
	
	public CustProspectExample(ProspectExample ex) {
		super(ex);
		//TODO : if ex is this type then don't have to rebuild JpOccurrences and eventsByDateMap;	
	}//copy ctor
	
	//this is customer-specific 
	@Override
	protected void _initObjsIndiv() {
		for (String key : jpOccTypeKeys) {
			eventsByDateMap.put(key, new TreeMap<Date, TreeMap<Integer, StraffEvntTrainData>> ());	
			JpOccurrences.put(key, new TreeMap<Integer,JP_OccurrenceData>());
		}
		//set these when this data is partitioned into testing and training data
		isTrainingData = false;
		testTrainDataIDX = -1;
	}//initObjsData

	//any processing that must occur once all constituent data records are added to this example - must be called externally, BEFORE ftr vec is built
	//---maps event data to occurrence structs; builds allJPs list
	@Override
	public void finalizeBuildBeforeFtrCalc() {
		if(jpOccNotBuiltYet) {
			buildOccurrenceStructs(jpOccTypeKeys, jpOccMapUseOccData);
			//allprodjps holds all jps in this example based on occurences that will be used in training; will not reference jps implied by opt-all records
			buildJPListsAndSetBadExample();
		}
	}//finalize
	
	//return boolean array describing this example's occurence structure
	//idxs : 0==if in an order; 1==if in an opt, 2 == if is in link, 3==if in source event,4 if in any non-source event (denotes action by customer), 5 if in any event, including source, 
	//needs to follow format of eventMapTypeKeys
	@Override
	public boolean[] getExampleStatusOcc() {
		boolean	has_ordr = (JpOccurrences.get("orders").size() !=0) ,
				has_opt = (JpOccurrences.get("opts").size() !=0),				//seems to not correspond to size of eventsByDateMap.get("opts").size()
				has_link = (JpOccurrences.get("links").size() !=0),	
				has_source = (JpOccurrences.get("sources").size() !=0),			//do not treat source data as an event in this case
				has_event = has_ordr || has_opt || has_link;					//not counting source data
		boolean[] res = new boolean[] {has_ordr, has_opt, has_link, has_source,has_event};
		return res;		
	}//

	//check whether this prospect has a particular jp in either his prospect data, events or opts
	//idxs : 0==if in an order; 1==if in an opt, 2 == if is in link, 3==if in source event,4 if in any non-source event (denotes action by customer), 5 if in any event, including source, 
	//it is assumed that hasJP_event is after all event booleans (if future bools are added)
	//needs to follow format of eventMapTypeKeys
	@Override
	public boolean[] hasJP(Integer _jp) {
		boolean	hasJP_ordr = (JpOccurrences.get("orders").get(_jp) != null) ,
				hasJP_opt = (JpOccurrences.get("opts").get(_jp) != null),
				hasJP_link = (JpOccurrences.get("links").get(_jp) != null),	
				hasJP_source = (JpOccurrences.get("sources").get(_jp) != null),			//do not treat source data as an event in this case
				hasJP_event = hasJP_ordr || hasJP_opt || hasJP_link,					//not counting source data
				hasJP = hasJP_event || hasJP_source;
		boolean[] res = new boolean[] {hasJP_ordr, hasJP_opt, hasJP_link, hasJP_source, hasJP_event, hasJP};
		return res;		
	}//check if JP exists, and where
	
	//add event data to this customer prospect
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
	
	@Override
	public void setIsTrainingDataIDX_Priv() {
		type= isTrainingData ? ExDataType.Training : ExDataType.Testing;
		nodeClrs = mapMgr.getClrVal(type);
	}//setIsTrainingDataIDX

	//column names for raw descriptorCSV output
	@Override
	public String getRawDescColNamesForCSV(){	return csvColDescrPrfx + ",";	}
	@Override
	//get #'s of events from partitioned csv data held in dataAra - need to accommodate source events - idxs depend on how the data was originally built
	protected int[] _getCSVNumEvsAra(String[] dataAra) {return new int[] {Integer.parseInt(dataAra[2]),Integer.parseInt(dataAra[3]),Integer.parseInt(dataAra[4]),Integer.parseInt(dataAra[5])};}
	@Override
	protected String[] getEventMapTypeKeys() {	return jpOccTypeKeys;}
	@Override
	protected String[] getCSVSentinelLbls() {return CSVSentinelLbls;}
	//this prospect is an actual customer - use as training data - not all custPropsects will have this true, since this is the initial class that is used to build the data
	@Override
	public boolean isTrainablePastCustomer() { return !getFlag(isBadTrainExIDX) && (JpOccurrences.get("orders").size() > 0);}
	
	//returns true if -any training-related- events are present in this record (i.e. not counting source "events")
	@Override
	protected boolean hasRelevantTrainingData() {
		boolean res = false;
		for (String key : trainingEventMapTypeKeys) {if (JpOccurrences.get(key).size() > 0) {return true;}	}
		return res;
	}//hasRelelventEvents	
    //take loaded data and convert to feature data via calc object 
	public static TreeMap<Integer,Integer[]> ttlOrderCount = new TreeMap<Integer,Integer[]>();
	private void addOrderCountToTTLMap() {
		TreeMap<Integer, JP_OccurrenceData> map = JpOccurrences.get("orders");
		int numOrders = map.size();//# of different jps having orders
		Integer[] orderDat = ttlOrderCount.get(numOrders);//array of values - first value is count of different jp occurrences, 2nd value is count of all orders for all jps
		if(orderDat==null) {orderDat = new Integer[2];orderDat[0]=0;orderDat[1]=0;}
		orderDat[0] +=1;								//this customer has this many different unique jp orders
		int numOccsAllOrders = 0;						//this customer has this many unique order dates of all jps
		for(JP_OccurrenceData occ  : map.values()) {			numOccsAllOrders += occ.getNumberOfOccurrences();		}
		orderDat[1] += numOccsAllOrders;
		ttlOrderCount.put(numOrders, orderDat);
	}
	@Override
	protected void buildFeaturesMap() {
		//access calc object		
		if (allProdJPs.size() > 0) {
			addOrderCountToTTLMap();
			((Straff_SOMMapManager)mapMgr).ftrCalcObj.calcTrainFtrVec(this,allProdJPs, ftrMaps[ftrMapTypeKey],JpOccurrences.get("orders"), JpOccurrences.get("links"), JpOccurrences.get("opts"), JpOccurrences.get("sources"));			
		} else {ftrMaps[ftrMapTypeKey].clear();}
		//now, if there's a non-null posOptAllEventObj then for all jps who haven't gotten an opt conribution to calculation, add positive opt-all result
	}//buildFeaturesMap
	
	//this will build the training-ftr configured vector contribution for this example to all non-product jps this example has.
	public void buildNonProdJpFtrVec() {
		if(nonProdJpgJps.size() > 0) {		((Straff_SOMMapManager)mapMgr).ftrCalcObj.buildCustNonProdFtrVecs(this, nonProdJpgJps, JpOccurrences.get("sources"));}
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
