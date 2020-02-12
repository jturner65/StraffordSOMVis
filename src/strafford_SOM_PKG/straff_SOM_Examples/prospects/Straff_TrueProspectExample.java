package strafford_SOM_PKG.straff_SOM_Examples.prospects;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import base_SOM_Objects.SOM_MapManager;
import base_SOM_Objects.som_examples.SOM_ExDataType;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.Straff_ProspectData;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.base.Straff_BaseRawData;
import strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.events.StraffEvntRawToTrainData;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

/**
 * This class will hold a reduced prospect that is defined as a true prospect - most likely defined as a prospect without any actual orders
 * These won't be used for training, but will be used for validation.  There are going to be many of these - many more than customer (base) prospects,
 * and they have much less overhead, hence they have a separate class with some overlapping functionality.
 * 
 * @author john
 */

public class Straff_TrueProspectExample extends Straff_ProspectExample{	
	//column names for csv of this SOM example - won't have events
	private static final String csvColDescrPrfx = "OID,Prospect_LU_Date,Num Opt Event Dates,Num Link Event Dates,Num Src Event Dates";
	//all kinds of events present
	public static final String[] jpOccTypeKeys = new String[] {"opts", "links", "sources"};
	//event mappings use the occurrence data value (opt/source)
	public static final boolean[] jpOccMapUseOccData = new boolean[] {true,false,true};
	//csv labels
	private static final String[] CSVSentinelLbls = new String[] {"OPT|,", "LNK|,", "SRC|," };
	
	//build this object based on prospectData object 
	public Straff_TrueProspectExample(Straff_SOMMapManager _map,Straff_ProspectData _prspctData) {	super(_map,SOM_ExDataType.Validation,_prspctData);	}//prospectData ctor
	
	//build this object based on csv string - rebuild data from csv string columns 4+
	public Straff_TrueProspectExample(SOM_MapManager _map,String _OID, String _csvDataStr) {
		super(_map,SOM_ExDataType.Validation,_OID,_csvDataStr);	
		String[] dataAra = _csvDataStr.split(",");
		//idx 0 : OID; idx 1 : date
		prs_LUDate = Straff_BaseRawData.buildDateFromString(dataAra[1]);		
		//get # of events - need to accommodate source events
		int[] numEvsAra = _getCSVNumEvsAra(dataAra);
		//Build data here from csv string
		//example of csv string
		//pr_000000331,2016-11-21 16:15:51,0,1,7,OPT|,LNK|,Occ_St,364,4,0,DtOccSt,2012-04-25 15:59:14,1,3,2,DtOccEnd,DtOccSt,2016-02-13 07:49:31,1,3,1,DtOccEnd,Occ_End,SRC|,Occ_St,69,4,1,DtOccSt,2007-09-21 21:53:32,1,11,1,DtOccEnd,DtOccSt,2009-02-06 05:53:38,1,57,1,DtOccEnd,Occ_End,Occ_St,131,4,1,DtOccSt,2009-02-06 05:53:49,1,57,1,DtOccEnd,DtOccSt,2017-10-03 03:07:09,1,92,1,DtOccEnd,Occ_End,Occ_St,227,20,1,DtOccSt,2010-01-04 22:22:49,1,41,1,DtOccEnd,Occ_End,Occ_St,231,64,1,DtOccSt,2010-03-05 01:49:47,1,41,1,DtOccEnd,Occ_End,Occ_St,232,8,1,DtOccSt,2009-12-18 23:15:20,1,41,1,DtOccEnd,Occ_End,Occ_St,237,1,1,DtOccSt,2009-12-18 23:15:03,1,41,1,DtOccEnd,Occ_End,Occ_St,274,64,1,DtOccSt,2017-10-03 03:07:09,1,92,1,DtOccEnd,Occ_End,
		//if(useJPOccToPreProc){		buildDataFromCSVString_jpOcc(numEvsAra, _csvDataStr,eventMapTypeKeys, CSVSentinelLbls);	buildJPListsAndSetBadExample();} 
		//else {						buildDataFromCSVString_event(numEvsAra, _csvDataStr,eventMapTypeKeys, CSVSentinelLbls);		}	
		buildDataFromCSVString_jpOcc(numEvsAra, _csvDataStr,jpOccTypeKeys, CSVSentinelLbls);	
		buildJPListsAndSetBadExample();	
	}//csv string ctor
	
	public Straff_TrueProspectExample(Straff_ProspectExample ex) {
		super(ex);		
		//set this type
		exampleDataType = SOM_ExDataType.Validation;
		//provide shallow copy of jpOcc struct - only copying passed event type key values
		JpOccurrences = ex.copyJPOccStruct(jpOccTypeKeys);		
		//provide shallow copy of jpOcc struct - only copying passed event type key values
		eventsByDateMap = ex.copyEventsByDateMap(jpOccTypeKeys);
	}//copy ctor
	
	@Override
	//get #'s of events from partitioned csv data held in dataAra - need to accommodate source events - idxs depend on how the data was originally built - true prospects have no orders
	protected int[] _getCSVNumEvsAra(String[] dataAra) {return new int[] {Integer.parseInt(dataAra[2]),Integer.parseInt(dataAra[3]),Integer.parseInt(dataAra[4])};}
	@Override
	protected void _initObjsIndiv() {	
		if(eventsByDateMap != null) {	for (String key : jpOccTypeKeys) {	eventsByDateMap.put(key, new TreeMap<Date, TreeMap<Integer, StraffEvntRawToTrainData>> ());	}	}
		for (String key : jpOccTypeKeys) {		JpOccurrences.put(key, new TreeMap<Integer,Straff_JP_OccurrenceData>());	}	
		
	}//_initObjsIndiv()
	
	@Override
	protected void setIsTrainingDataIDX_Priv() {//this is never training data - its testTrainIDX will be the index it has in the validation data array;
		isTrainingData=false; 
	}//products are never going to be training examples

	//this prospect is an actual customer - use as training data
	@Override
	public boolean isTrainablePastCustomer() { return false ;}
	//returns true if -any training-related- events are present in this record (i.e. not counting source "events")
	//we don't ever want to train with a true prospect so this should always be false
	@Override
	protected boolean hasRelevantTrainingData() { return false;}//hasRelelventEvents	
	
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
	@Override
	protected void buildFeaturesMap() {	//TODO do we wish to modify this for prospects?  probably
		//access calc object
		if (allProdJPs.size() > 0) {//getting from orders should yield empty list, might yield null - has no order occurrences by definition	
			clearFtrMap(rawftrMapTypeKey);//		
			((Straff_SOMMapManager)mapMgr).ftrCalcObj.calcTruePrspctFtrVec(this,allProdJPs,ftrMaps[rawftrMapTypeKey], JpOccurrences);			
		} else {ftrMaps[rawftrMapTypeKey].clear(); }
		//now, if there's a non-null posOptAllEventObj then for all jps who haven't gotten an opt conribution to calculation, add positive opt-all result
	}//buildFeaturesMap		

	public static int NumTPWithNoFtrs = 0;
	public static int maxNumNonProdJps= 0;
	@Override
	/**
	 *  this will build the comparison feature vector array that is used as the comparison vector in distance measurements
	 * @param _ratio : 0 means all base ftrs, 1 means all compValMap for features
	 */
	public synchronized final void buildCompFtrVector(float _ratio) {
		//ratio needs to be [0..1], is ratio of compValMaps value to ftr value
		if((_ratio <=0) && (ftrMaps[0].size()>0)) {compFtrMaps = ftrMaps;}	//if ftrMaps size is 0 then have no direct features in record, so have to build comparator vector
		else {  //if no features then just use complete comValFtrDataMaps
			calcCompValMaps();
			if((_ratio >= 1) || (ftrMaps[0].size()==0)){
				//call here since this will most likely need to be built for true prospects
				if(this.nonProdJpgJps.size() > maxNumNonProdJps) {maxNumNonProdJps = this.nonProdJpgJps.size();}
				++NumTPWithNoFtrs;
				compFtrMaps = compValFtrDataMaps;		//this is very slow	
				//compFtrMaps = ftrMaps;
			} else {
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
		if(compFtrMaps[0].size() == 0) {setIsBadExample(true);	} //if nothing to compare to, then this is a bad example
	}//buildCompFtrVector
	@Override
	//standardize this feature vector - across each feature, set value to be between 0 and 1
	protected final void buildStdFtrsMap() {		
		if (allNonZeroFtrIDXs.size() > 0) {calcStdFtrVector(ftrMaps[rawftrMapTypeKey],ftrMaps[stdFtrMapTypeKey], mapMgr.getTrainFtrMins(), mapMgr.getTrainFtrDiffs());}
		else {ftrMaps[stdFtrMapTypeKey].clear();}
		setFlag(stdFtrsBuiltIDX,true);
	}//buildStdFtrsMap
	
	//this will return the training label(s) of this example - true prospects by definition do not have any labels - no past orders
	//they should not be used for supervision during/after training
	public TreeMap<Integer,Integer> getTrainingLabels() {return new TreeMap<Integer,Integer>();}

	//column names for raw descriptorCSV output
	@Override
	public String getRawDescColNamesForCSV(){
		String csvColDescr = csvColDescrPrfx + ",";
		//add extra column descriptions for orders if using any		
		return csvColDescr;	
	}//getRawDescColNamesForCSV
	
	@Override
	protected String[] getEventMapTypeKeys() {	return jpOccTypeKeys;}
	@Override
	protected String[] getCSVSentinelLbls() {return CSVSentinelLbls;}

	//return boolean array describing this example's occurence structure
	//idxs : 0==if in an order; 1==if in an opt, 2 == if is in link, 3==if in source event,4 if in any non-source event (denotes action by customer), 5 if in any event, including source, 
	//needs to follow format of eventMapTypeKeys
	@Override
	public boolean[] getExampleStatusOcc() {
		boolean	has_ordr = false ,
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
		boolean	hasJP_ordr = false ,
				hasJP_opt = (JpOccurrences.get("opts").get(_jp) != null),
				hasJP_link = (JpOccurrences.get("links").get(_jp) != null),	
				hasJP_source = (JpOccurrences.get("sources").get(_jp) != null),			//do not treat source data as an event in this case
				hasJP_event = hasJP_ordr || hasJP_opt || hasJP_link,					//not counting source data
				hasJP = hasJP_event || hasJP_source;
		boolean[] res = new boolean[] {hasJP_ordr, hasJP_opt, hasJP_link, hasJP_source, hasJP_event, hasJP};
		return res;		
	}//check if JP exists, and where

	
	@Override
	public String toString() {	
		String res = "True Prospect : " + super.toString();
		for (String key : jpOccTypeKeys) {
			//res += toStringDateMap(eventsByDateMap.get(key), key);
			res += toStringOptOccMap(JpOccurrences.get(key), key + " occurences");
		}
		return res;
	}

}//trueProspectExample
