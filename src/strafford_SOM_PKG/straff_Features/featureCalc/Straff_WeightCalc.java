package strafford_SOM_PKG.straff_Features.featureCalc;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

import base_Render_Interface.IRenderInterface;
import base_SOM_Objects.som_utils.SOM_ProjConfigData;
import base_Utils_Objects.io.FileIOManager;
import base_Utils_Objects.io.messaging.MessageObject;
import base_Utils_Objects.io.messaging.MsgCodes;
import base_Math_Objects.vectorObjs.tuples.Tuple;
import strafford_SOM_PKG.straff_Features.Straff_MonitorJpJpgrp;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.Straff_JP_OccurrenceData;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.Straff_ProspectExample;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.Straff_TrueProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

/**
 * This class is intended to hold an object capable of calculation
 * the appropriate weighting of the various component terms that make up 
 * the weight vector used to train/query the SOM.  Allow this calculation to be built
 * from values in format file
 * @author john  
 */
public class Straff_WeightCalc {
	//public StraffSOMMapManager mapMgr;
	private MessageObject msgObj;
	public Straff_MonitorJpJpgrp jpJpgMon; 
	private FileIOManager fileIO;
	//base file name, minus type and extension 
	private String fileName;
	//private String[] fileTypes = new String[] {"train","compare"};
	public final Date now;
	//arrays are for idxs of various eq components (mult, offset, decay) in the format file for 
	//each component contributor (order, opt, link, source, source for comparison), in order.  0-idx (not listed in these arrays) is jp name or "default"
	private static final int[] mIdx = new int[] {1, 4, 7, 10, 13},
			   					oIdx = new int[] {2, 5, 8, 11, 14},
			   					dIdx = new int[] {3, 6, 9, 12, 15};
	//map of per-jp equations to calculate feature vector from event data (expandable to more sources eventually if necessary).  keyed by jp
	//and ftrEqs map, holding only eqs used to directly calculate ftr vector values (subset, some jps are not used to train feature vec) (This is just a convenient filter object)
	private TreeMap<Integer, Straff_JPWeightEquation> allEqs, ftrEqs;	
	
	//hold relevant quantities for each jp calculation across all data; IDX 0 is for ftr vec calc, idx 1 is for all jps
	//idx 0 is for ftr vector calculation; idx 1 will be for alternate comparator vector calc
	private Straff_DataBoundMonitor[] bndsAra;
	public static final int
		bndAra_ProdJPsIDX = 0,			//jps used for training (correspond to jps with products) - this is from ftr vector calcs
		bndAra_AllJPsIDX = 1;			//all jps in system, including those jps who do not have any products - this is from -comparator- calcs
	public static final int numJPInBndTypes = 2;	
	//separates calculations based on whether calc is done on a customer example, or on a true prospect example
	public static final int 
		custCalcObjIDX 		= 0,		//type of calc : this is data aggregated off of customer data (has prior order events and possibly other events deemed relevant) - this corresponds to training data source records
		tpCalcObjIDX 		= 1,		//type of calc : this is data from true prospect, who lack prior orders and possibly other event behavior
		trainCalcObjIDX		= 2;		//type of calc : actual training data, based on date of orders - 1 or more of these examples will be synthesized for each customer prospect
	public static final int numExamplTypeObjs = 3;
	
	//state flags - bits in array holding relevant process/state info pertaining to the current calculations
	private int[] stFlags;						
	private static final int
			debugIDX 						= 0,
			ftrCalcCustCompleteIDX			= 1,	//ftr weight calc has been completed for all loaded customer examples.
			custCalcAnalysisCompleteIDX		= 2,	//analysis of calc results completed for all customer records for this object
			ftrCalcTPCompleteIDX			= 3,	//ftr weight calc has been completed for all loaded true prospects examples.
			TPCalcAnalysisCompleteIDX		= 4,	//analysis of calc results completed for all true prospects records for this object
			ftrCalcTrainCompleteIDX			= 5,
			TrainCalcAnalysisCompleteIDX 	= 6,
			custNonProdCalcCompleteIDX		= 7;	//the non-product jps have had their customer-derived feature vector contributions derived
	private static final int numFlags = 8;	
	//idx 0 is customer records; idx 1 is true prospect records
	private static final int[] ftrCalcFlags = new int[] {ftrCalcCustCompleteIDX,ftrCalcTPCompleteIDX,ftrCalcTrainCompleteIDX};
	private static final int[] calcCompleteFlags = new int[] {custCalcAnalysisCompleteIDX,TPCalcAnalysisCompleteIDX,TrainCalcAnalysisCompleteIDX};
	
	public Straff_WeightCalc(Straff_SOMMapManager _mapMgr, String _fileNamePrfx, Straff_MonitorJpJpgrp _jpJpgMon) {
		msgObj = MessageObject.getInstance();
		Calendar nowCal = _mapMgr.getInstancedNow();
		fileIO = new FileIOManager(msgObj, "StraffWeightCalc");
		now = nowCal.getTime();
		jpJpgMon = _jpJpgMon;
		initFlags();
		//initialize bnds
		initBnds();
		loadConfigAndSetVars( _fileNamePrfx);
	}//ctor
	
	//load specified weight config file
	private void loadConfigAndSetVars(String _fileNamePrfx) {
		fileName = _fileNamePrfx;
		TreeMap<String, String[]> wtConfig = _loadWtCalcConfig(_fileNamePrfx+".txt", "Ftr Calc");
		if(null==wtConfig) {msgObj.dispMessage("StraffWeightCalc","loadConfigAndSetVars", "Error! First non comment record in config file "+ _fileNamePrfx + " is not default weight map specification.  Exiting calc unbuilt.", MsgCodes.error2);return ;}
		String[] dfltAra = wtConfig.get("default");
		//first build default values for all eqs
		_buildDefaultEQs(dfltAra);
		if(wtConfig.size()==1) {return;}				//we're done - no custom jp eq mults specified, only default
		//now find any per-jp specifications and build them as well
		_buildCustomEQs(wtConfig);
	}//loadConfigAndSetVars	
	
	//load specified config file to retrieve calc components for multiplier, decay and offset
	//returns a map idxed by either "default" or string rep of jp, and string array of 
	private TreeMap<String, String[]> _loadWtCalcConfig(String _fileName, String _type){		
		String[] configDatList = fileIO.loadFileIntoStringAra(_fileName, _type +" Weight Calc File Loaded", _type +" Weight Calc File Not Loaded Due To Error");		
		TreeMap<String, String[]> res = new TreeMap<String, String[]>();
		boolean foundDflt = false;
		int idx = 0;
		String[] strVals = new String[0];
		while (!foundDflt) {
			if ((configDatList[idx].contains(SOM_ProjConfigData.fileComment)) || (configDatList[idx].trim() == "")) {++idx;			}
			else {
				msgObj.dispMessage("StraffWeightCalc","_loadWtCalcConfig", "First line after comments : " + configDatList[idx].trim(), MsgCodes.info1);
				strVals = configDatList[idx].trim().split(",");
				if (strVals[0].toLowerCase().contains("default")) {	foundDflt = true;} 	//first non-comment/non-space is expected to be "default"
				else {			return null;			}								//default values must be specified first in wt file!
			}		
		}//while		
		res.put("default", strVals);
		msgObj.dispMessage("StraffWeightCalc","loadConfigAndSetVars", "res.default len : " + strVals.length, MsgCodes.info1);
		//now go through every line and build eqs for specified jps
		for (int i=idx+1; i<configDatList.length; ++i) {
			String line = configDatList[i].trim();
			if ((line.length()==0) || (line.contains(SOM_ProjConfigData.fileComment))) {continue;			}
			strVals = line.split(",");	
			if(strVals.length == 0) {continue;}
			//put all values of specified jps intended to override default cals in map, keyed by jp string
			res.put(strVals[0], strVals);
		}
		return res;
	}//_loadWtCalcConfig
	
	private void _buildJPEq(int jp, int allIDX, Float[][] eqVals) {
		Integer ftrIDX = jpJpgMon.getFtrJpToIDX(jp);
		if(ftrIDX == null) {ftrIDX = -1;}
		Straff_JPWeightEquation eq = new Straff_JPWeightEquation(this,jpJpgMon.getJPNameFromJP(jp),jp, new int[] {ftrIDX,allIDX}, eqVals, true);
		allEqs.put(jp, eq);
		if(ftrIDX!=-1) {ftrEqs.put(jp, eq);	}
	}
	
	/**
	 * Build all eqs with default settings from file
	 * @param strVals string holding default weight settings
	 */
	private void _buildDefaultEQs(String[] strVals) {
		//string record has jp in col 0, 3 mult values 1-3, 3 offset values 4-6 and 3 decay values 7-9.
		Float[] dfltM = getFAraFromStrAra(strVals,mIdx), dfltO = getFAraFromStrAra(strVals,oIdx), dfltD = getFAraFromStrAra(strVals,dIdx);
		//strVals holds default map configuration - config all weight calcs to match this	
		//build eqs map for all jps found
		allEqs = new TreeMap<Integer, Straff_JPWeightEquation> ();
		ftrEqs = new TreeMap<Integer, Straff_JPWeightEquation> ();
		int ttlNumJps = jpJpgMon.getNumAllJpsFtrs();
		for (int allIDX=0;allIDX<ttlNumJps;++allIDX) {_buildJPEq(jpJpgMon.getAllJpByIdx(allIDX), allIDX,  new Float[][] {dfltM, dfltO, dfltD});	}//
		
	}//_buildDefaultEQs
	
	/**
	 * build all eqs using settings for jps that are specified to override default
	 * @param wtConfig map of wtConfig arrays, keyed by jp (or "default")
	 */
	private void _buildCustomEQs(TreeMap<String, String[]> wtConfig) {		
		for(String key : wtConfig.keySet()) {
			System.out.println("_buildCustomEQs::Key : " + key);
			if(key.contains("default")){continue;}
			String[] wtCalcStrAra = wtConfig.get(key);
			Integer jp = Integer.parseInt(wtCalcStrAra[0]);		
			//overwrites existing eq at jp key
			_buildJPEq(jp, jpJpgMon.getJpToAllIDX(jp),  new Float[][] {getFAraFromStrAra(wtCalcStrAra,mIdx), getFAraFromStrAra(wtCalcStrAra,oIdx), getFAraFromStrAra(wtCalcStrAra,dIdx)});	
		}
	}//_buildMultConfigCust	
	
	
	/////////////////////////////////////
	// bnds array handling
	
	//reinit bounds ara
	//first key is 0==training ftr jps; 1==all jps;
	//second key is type of bound; 3rd key is jp
	private void initBnds() {	
		bndsAra = new Straff_DataBoundMonitor[numJPInBndTypes];
		int[] numFtrs = new int[] {jpJpgMon.getNumTrainFtrs(),jpJpgMon.getNumAllJpsFtrs()}; 
		for (int j=0;j<bndsAra.length;++j) {bndsAra[j] = new Straff_DataBoundMonitor(numExamplTypeObjs, numFtrs[j]);}
	}//initBnds() 	
	
	//get mins/diffs for ftr vals per ftr jp and for all vals per all jps
	public Float[][] getMinBndsAra() {
		ArrayList<Float[]> tmpBnds = new ArrayList<Float[]>();
		for(int i=0;i<bndsAra.length;++i) {	tmpBnds.add(i,bndsAra[i].getMinBndsAra());}		
		return tmpBnds.toArray(new Float[1][] );
	}
	public Float[][] getDiffsBndsAra() {		
		ArrayList<Float[]> tmpBnds = new ArrayList<Float[]>();
		for(int i=0;i<bndsAra.length;++i) {	tmpBnds.add(i,bndsAra[i].getDiffBndsAra());}		
		return tmpBnds.toArray(new Float[1][] );
	}	
	
	public Float[][] getMinTrainDataBndsAra() {		return getMinBndsAraForDataType(trainCalcObjIDX);}
	public Float[][] getDiffsTrainDataBndsAra() {	return getDiffsBndsAraForDataType(trainCalcObjIDX);}

	//get mins/diffs for ftr vals per ftr jp and for all vals per all jps
	public Float[][] getMinBndsAraForDataType(int _typeIDX) {
		ArrayList<Float[]> tmpBnds = new ArrayList<Float[]>();
		for(int i=0;i<bndsAra.length;++i) {	tmpBnds.add(i,bndsAra[i].getMinBndsAra(_typeIDX));}		
		return tmpBnds.toArray(new Float[1][] );
	}
	public Float[][] getDiffsBndsAraForDataType(int _typeIDX) {		
		ArrayList<Float[]> tmpBnds = new ArrayList<Float[]>();
		for(int i=0;i<bndsAra.length;++i) {	tmpBnds.add(i,bndsAra[i].getDiffBndsAra(_typeIDX));}		
		return tmpBnds.toArray(new Float[1][] );
	}	
	//check if value is in bnds array for particular jp, otherwise modify bnd
	private void checkValInBnds(int bndJpType, Integer calcTypeIDX, Integer destIDX, float val) {	bndsAra[bndJpType].checkValInBnds(calcTypeIDX,destIDX, val);}
	
	//increment count of training examples with jp data represented by destIDX, and total calc value seen
	public void incrBnds(int bndJpType, Integer calcTypeIDX, Integer destIDX) {
		
		bndsAra[bndJpType].incrBnds(calcTypeIDX,destIDX);
	}

	//read in string array of weight values, convert and put in float array
	private Float[] getFAraFromStrAra(String[] sAra, int[] idxs) {
		ArrayList<Float> res = new ArrayList<Float>();
		for(int i : idxs) {			res.add(Float.parseFloat(sAra[i]));		}		
		return res.toArray(new Float[0]);
	}	

	///////////////////////////
	// calculate feature vectors - currently only works on product features - and comparator vectors - built off membership in jpgroups

	private Straff_JP_OccurrenceData getOptAndCheck(Straff_ProspectExample ex, TreeMap<Integer, Straff_JP_OccurrenceData> optOccs, Integer jp, String srcMethod) {
		Straff_JP_OccurrenceData optOcc = optOccs.get(jp);
		if ((optOcc != null )&& ((ex.getPosOptAllOccObj() != null) || (ex.getNegOptAllOccObj() != null))) {	//opt all means they have opted for positive behavior for all jps that allow opts
			msgObj.dispMessage("StraffWeightCalc","getOptAndCheck("+srcMethod+")","Multiple opt refs for prospect : " + ex.OID + " : indiv opt and opt-all | This should not happen - opt events will be overly-weighted.", MsgCodes.warning4);	
		}		
		return optOcc;
	}//getOptAndCheck
		
	public void calcTruePrspctFtrVec(Straff_ProspectExample ex, HashSet<Integer> jps,TreeMap<Integer, Float> ftrDest,TreeMap<String, TreeMap<Integer, Straff_JP_OccurrenceData>> JpOccurrences){	
		synchronized(ex) {
			_calcFtrDataVec(ex,jps, ftrDest, new TreeMap<Integer, Straff_JP_OccurrenceData>(), JpOccurrences.get("links"), JpOccurrences.get("opts"), JpOccurrences.get("sources"),tpCalcObjIDX,Straff_JPWeightEquation.now, "calcTruePrspctFtrVec");
		}
	}		
	public void calcCustFtrDataVec(Straff_ProspectExample ex, HashSet<Integer> jps,TreeMap<Integer, Float> ftrDest,TreeMap<String, TreeMap<Integer, Straff_JP_OccurrenceData>> JpOccurrences) {
		synchronized(ex) {
			_calcFtrDataVec(ex,jps, ftrDest,  JpOccurrences.get("orders"), JpOccurrences.get("links"), JpOccurrences.get("opts"), JpOccurrences.get("sources"),custCalcObjIDX, Straff_JPWeightEquation.now, "calcCustFtrDataVec");
		}
	}
	//includes order date
	public void calcTrainingFtrDataVec(Straff_ProspectExample ex, HashSet<Integer> jps,TreeMap<Integer, Float> ftrDest, Date orderDate, TreeMap<String, TreeMap<Integer, Straff_JP_OccurrenceData>> JpOccurrences) {
		synchronized(ex) {
			_calcFtrDataVec(ex,jps, ftrDest,  JpOccurrences.get("orders"), JpOccurrences.get("links"), JpOccurrences.get("opts"), JpOccurrences.get("sources"),trainCalcObjIDX, orderDate, "calcTrainingFtrDataVec");
		}
		//dbg_calcFtrVecMinMaxs(ftrDest,mapOfTrainCompFtrVecMins,mapOfTrainCompFtrVecMaxs);
	}//
	
	//calculate feature vector for this ProspectExample example on actual product-based features - these features are for comparison, not training!
	private void _calcFtrDataVec(Straff_ProspectExample ex, HashSet<Integer> jps,TreeMap<Integer, Float> ftrDest,
			TreeMap<Integer, Straff_JP_OccurrenceData> orderOccs,TreeMap<Integer, Straff_JP_OccurrenceData> linkOccs, 
			TreeMap<Integer, Straff_JP_OccurrenceData> optOccs,TreeMap<Integer, Straff_JP_OccurrenceData> srcOccs, int _exampleType, Date orderDate, String _type) {
		float ftrVecSqMag = 0.0f;
		Straff_JP_OccurrenceData optOcc;
		//for each jp present in ex, perform calculation
		Integer destIDX;	
		boolean isZeroMagExample = true;		//if all values are 0 then this is a bad training example, we want to ignore it
		for (Integer jp : jps) {
			//find destIDX
			destIDX = jpJpgMon.getFtrJpToIDX(jp);
			if (destIDX==null) {continue;}//ignore unknown/unmapped jps
			optOcc = getOptAndCheck(ex, optOccs, jp, _type);
			float val = allEqs.get(jp).calcFtrVal(ex,orderOccs.get(jp),linkOccs.get(jp), optOcc, srcOccs.get(jp),orderDate, _exampleType);
			
			if (val != 0) {
				isZeroMagExample = false;
				ftrVecSqMag += (val*val);
			}
			ftrDest.put(destIDX,val);		//add zero value 			
			checkValInBnds(bndAra_ProdJPsIDX,_exampleType, destIDX, val);
		}		
		ex.ftrVecMag = (float) Math.sqrt(ftrVecSqMag);
		ex.setIsBadExample(isZeroMagExample);
	}//calcFeatureVector	
	
	//initialize and finalize all calcs for CUSTOMER data - this builds the exemplar training data vector for each non-prod jp
	public void initAllEqsForCustNonTrainCalc() {setFlag(custNonProdCalcCompleteIDX, false);	for(Straff_JPWeightEquation jpEq:allEqs.values()) {jpEq.initCalcCustNonProdWtVec();}}
	public void finalizeAllEqsCustForNonTrainCalc() {for(Straff_JPWeightEquation jpEq:allEqs.values()) {jpEq.finalizeCalcCustNonProdWtVec();}setFlag(custNonProdCalcCompleteIDX, true);}
		
	public static int numNonProdFtrVecDims = 0;
	public static ConcurrentSkipListMap<Integer, Integer> mapOfNonProdFtrVecDims = new ConcurrentSkipListMap<Integer, Integer>(); 
	//map per nonProdComVecSize of map of nonProdJP and counts present for TPs with this nonProdComVec size
	public static ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Integer, Integer>> mapOfTPNonProdJpsPerSizeNonProdFtrVec = new ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Integer, Integer>>(); 
	//keyed by jpIDX, value is map of min and max
	public static ConcurrentSkipListMap<Integer, Float> mapOfTPCompFtrVecMins = new ConcurrentSkipListMap<Integer, Float>();
	public static ConcurrentSkipListMap<Integer, Float> mapOfTPCompFtrVecMaxs = new ConcurrentSkipListMap<Integer, Float>();
	//keyed by jpIDX, value is map of min and max
	public static ConcurrentSkipListMap<Integer, Float> mapOfTrainCompFtrVecMins = new ConcurrentSkipListMap<Integer, Float>();
	public static ConcurrentSkipListMap<Integer, Float> mapOfTrainCompFtrVecMaxs = new ConcurrentSkipListMap<Integer, Float>();

	
	
	/**
	 * put mins and maxs seen for every element of destMap into statix mins and maxs map
	 * @param destMap ftr vec map of particular example
	 * @param minMap
	 * @param maxMap
	 */
	private void dbg_calcFtrVecMinMaxs(TreeMap<Integer, Float> destMap, ConcurrentSkipListMap<Integer, Float> minMap, ConcurrentSkipListMap<Integer, Float> maxMap) {
		for (Integer ftrIDX : destMap.keySet()) {
			Float min = minMap.get(ftrIDX); if(min==null) {min=1000000.0f;}
			Float newMin = (min < destMap.get(ftrIDX) ? min :destMap.get(ftrIDX));  minMap.put(ftrIDX, newMin);
			Float max = maxMap.get(ftrIDX); if(max==null) {max=-1000000.0f;}
			Float newMax = (max > destMap.get(ftrIDX) ? max :destMap.get(ftrIDX));  maxMap.put(ftrIDX, newMax);
		}
	}//dbg_calcFtrVecMinMaxs	
	
	private void dbg_SetMapValues(HashSet<Tuple<Integer,Integer>> nonProdJpgJps,TreeMap<Integer, Float> destMap) {
		int mapSize = destMap.size();		
		Integer countAtSize = mapOfNonProdFtrVecDims.get(mapSize);
		if(null==countAtSize) {countAtSize=0;}
		++countAtSize;
		mapOfNonProdFtrVecDims.put(mapSize, countAtSize);
		Integer numJPs;
		ConcurrentSkipListMap<Integer, Integer> mapOfNonProds = mapOfTPNonProdJpsPerSizeNonProdFtrVec.get(mapSize);
		if(null==mapOfNonProds) {mapOfNonProds=new ConcurrentSkipListMap<Integer, Integer>(); mapOfTPNonProdJpsPerSizeNonProdFtrVec.put(mapSize,mapOfNonProds);}
		for (Tuple<Integer,Integer> jpJpgrp : nonProdJpgJps) {
			Integer nonProdJP = jpJpgrp.y;
			numJPs = mapOfNonProds.get(nonProdJP);
			if(null==numJPs) {numJPs=0;}
			++numJPs;
			mapOfNonProds.put(nonProdJP,numJPs);	
			//min max values for particular idx values in destMap			
		}		
		numNonProdFtrVecDims = (numNonProdFtrVecDims < mapSize ? mapSize : numNonProdFtrVecDims );
	}//dbg_SetMapValues
	
	//this will calculate for every non-product jp the contribution that ex should get based on srcOccs wt calc for each non-product jp present in ex
	public synchronized void calcNonProdJpTrainFtrContribVec(Straff_ProspectExample ex, HashSet<Tuple<Integer,Integer>> nonProdJpgJps,TreeMap<Integer, Float> destMap, TreeMap<Integer, Straff_JP_OccurrenceData> srcOccs) {
		destMap.clear();
		float ttlSrcContrib = 0.0f;
		TreeMap<Integer, Float> ttlSrcContribMap = new TreeMap<Integer, Float>();	
		
		for(Tuple<Integer,Integer> jpJpgrp : nonProdJpgJps) {
			Integer nonProdJP = jpJpgrp.y;
			Straff_JPWeightEquation jpEq = allEqs.get(nonProdJP);
			if(null==jpEq) {//msgObj.dispMessage("StraffWeightCalc","calcNonProdJpTrainFtrContribVec", "Null jpeq for jp : " + nonProdJP, MsgCodes.warning4);	
				continue;}
			Straff_JP_OccurrenceData srcOcc = srcOccs.get(nonProdJP);
			if(null==srcOcc) {continue;}
			float srcContrib = jpEq.getSrcContrib_Now(srcOcc);	//srcContrib is multiplied against vector and decayed from current date
			if(srcContrib==0.0f) {continue;}			
			
			ttlSrcContrib += srcContrib;
			TreeMap<Integer, Float> res2 = jpEq.calcNonProdWtVec(ex, srcContrib);
			for(Integer key : res2.keySet()) {
				Float ttlAtJP = ttlSrcContribMap.get(key);
				if(ttlAtJP == null) {ttlAtJP = 0.0f;}
				ttlAtJP += srcContrib;
				ttlSrcContribMap.put(key,ttlAtJP);
				Float val1 = destMap.get(key);
				if(val1 == null) {val1 = 0.0f;}
				val1 += res2.get(key);
				destMap.put(key, val1);
			}			
		}
		//debugging destmap results
		dbg_SetMapValues(nonProdJpgJps,destMap);
		
		if(ttlSrcContrib==0.0f) {ex.compValFtrDataMapMag = 0.0f; destMap.clear();return;}
		//divide entire res map by ttlContrib to normalize weighting
		float sqVal = 0.0f;
		for(Integer key : destMap.keySet()) {	
			float val = destMap.get(key)/ttlSrcContribMap.get(key);
			sqVal += val*val;
			destMap.put(key, val);
		}		
		dbg_calcFtrVecMinMaxs(destMap, mapOfTPCompFtrVecMins, mapOfTPCompFtrVecMaxs);		
		ex.compValFtrDataMapMag = (float) Math.sqrt(sqVal);
	}//calcCompareObj

	//this will build the non-product jp training vectors based on weighting each passed example by the example's srcOccs data
	//for each non-product jp.  This trained vector gets stored in the jp eq object
	public void buildCustNonProdFtrVecs(Straff_ProspectExample ex, TreeMap<Integer, Float> ftrData, HashSet<Tuple<Integer,Integer>> nonProdJpgJps, TreeMap<Integer, Straff_JP_OccurrenceData> srcOccs) {
		//set this example's contribution by weighting their normalized feature vector by their non-prod-calc jp data
		for(Tuple<Integer,Integer> jpJpgrp : nonProdJpgJps) {
			Integer nonProdJP = jpJpgrp.y;
			allEqs.get(nonProdJP).buildCustNonProdWtVecContrib(ftrData, srcOccs.get(nonProdJP));
		}
	}//calcCompareObj
	
	//////////////////////////////////////////////////
	//end feature vector and comparator vec calc
	
	//////////////////////////////////////////////////
	// State setting/getting and reporting functions
	//to check if calculation has been completed
	public boolean custNonProdJpCalcIsDone() {return getFlag(custNonProdCalcCompleteIDX);}
	
	//this will reset all analysis components of feature vectors.  this is so that new feature calculations won't aggregate stats with old ones
	public void resetCalcObjs(int _exampleType) {for ( Straff_JPWeightEquation eq : allEqs.values()	) {	eq.resetAnalysis(_exampleType);	}	setFlag(ftrCalcFlags[_exampleType], false);}//resetCalcObjs
	
	//called when all current prospect examples have been calculated
	public void finishFtrCalcs(int _exampleType) {	setFlag(ftrCalcFlags[_exampleType], true); 	}	
	//after all features are calculated, run this first to finalize reporting statistics on eq performance
	public void finalizeCalcAnalysis(int _exampleType) {
		if (calcAnalysisShouldBeDone(_exampleType)) {
			for(Straff_JPWeightEquation jpEq:allEqs.values()) {jpEq.aggregateFtrCalcVals(_exampleType);}
			setFlag(calcCompleteFlags[_exampleType], true);
		}
	}//finalizeCalcAnalysis
	//to see if calc analysis is ready to be displayed
	public boolean calcAnalysisIsReady(int _exampleType) {return getFlag(ftrCalcFlags[_exampleType]) && getFlag(calcCompleteFlags[_exampleType]);}		//ftr calc is done, and calc anaylsis has been done on these feature calcs
	private boolean calcAnalysisShouldBeDone(int _exampleType) {return getFlag(ftrCalcFlags[_exampleType]) && ! getFlag(calcCompleteFlags[_exampleType]);}//only perform analysis once on same set of collected ftr calc data
	
	//retrieve a list of all eq performance data per ftr
	public ArrayList<String> getCalcAnalysisRes(int _exampleType){
		ArrayList<String> res = new ArrayList<String>();
		for(Straff_JPWeightEquation jpEq:allEqs.values()) {	res.addAll(jpEq.getCalcRes(_exampleType));}
		return res;
	}//getCalcAnalysisRes	
	
	///////////////////////////
	// calc analysis display routines	
	
	//draw res of all calcs as single rectangle of height ht and width barWidth*num eqs
	public void drawAllCalcRes(IRenderInterface p, float ht, float barWidth, int curJPIdx,int _exampleType) {_drawCalcRes(p, ht, barWidth,curJPIdx,_exampleType, bndAra_AllJPsIDX, allEqs);}//draw analysis res for each graphically	
	//draw only ftr JP calc res
	public void drawFtrCalcRes(IRenderInterface p, float ht, float barWidth, int curJPIdx,int _exampleType) {_drawCalcRes(p, ht, barWidth,curJPIdx,_exampleType, bndAra_ProdJPsIDX, ftrEqs);}//draw analysis res for each graphically
	
	private void _drawCalcRes(IRenderInterface p, float ht, float barWidth, int curJPIdx,int _exampleType, int _jpType, TreeMap<Integer, Straff_JPWeightEquation> _eqMap) {
		p.pushMatState();		
		for(Straff_JPWeightEquation jpEq:_eqMap.values()) {	//only draw eqs that calculated actual feature values (jps found in products)
			//draw bar
			jpEq.drawFtrVec(p, ht, barWidth, jpEq.jpIDXs[_jpType]==curJPIdx,_jpType,_exampleType);
			//move over for next bar
			p.translate(barWidth, 0.0f, 0.0f);
		}
		p.popMatState();	
	}//_drawCalcRes
	
	//draw single detailed feature eq detailed analysis
	public void drawSingleFtr(IRenderInterface p, float ht, float width, Integer jp,int _exampleType) {
		p.pushMatState();		
		//draw detailed analysis
		allEqs.get(jp).drawIndivFtrVec(p, ht, width,_exampleType);
		p.popMatState();			
	}//drawSingleFtr
	
	/////////////////////////////////////	
	
	private void initFlags(){stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlag(i,false);}}
	public void setAllFlags(int[] idxs, boolean val) {for (int idx : idxs) {setFlag(idx, val);}}
	public void setFlag(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case debugIDX : {break;}	
			case ftrCalcCustCompleteIDX 		: {							//whether or not this object has finished calculating ftrs for all loaded customer examples
				if(!val) {setFlag(custCalcAnalysisCompleteIDX, false);}		//setting this to false means have yet to calculate ftrs for customers, so all customer calc analysis data should be ignored
				break;}					
			case custCalcAnalysisCompleteIDX	: { 					//whether or not this object has finished the analysis aggregation of all eqs on ftrs calced for customers				
				break;}			
			case ftrCalcTPCompleteIDX 		: {							//whether or not this object has finished calculating ftrs for all loaded true prospect examples
				if(!val) {setFlag(TPCalcAnalysisCompleteIDX, false);}		//setting this to false means have yet to calculate ftrs for true prospect, so all calc analysis data should be ignored
				break;}					
			case TPCalcAnalysisCompleteIDX	: { 					//whether or not this object has finished the analysis aggregation of all eqs on ftrs calced for true prospect				
				break;}			
		}
	}//setFlag		
	public boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		
	
	/////////////////////////////////////		

	//debugging functionality 
	public void dispCompFtrVecRes() {
		msgObj.dispInfoMessage("Straff_SOMTruePrspctMapper","dispCompFtrVecRes","Showing Results of mapping avg ftr vec to appropriate training examples by matching source data");
		msgObj.dispInfoMessage("Straff_SOMTruePrspctMapper","dispCompFtrVecRes","Size of Comp Vec,# of True Prospects with this size Comp Vec,# of actual ftr configs for this count.");

		for(Integer sizeOfCompVec : Straff_WeightCalc.mapOfNonProdFtrVecDims.keySet()) {
			ConcurrentSkipListMap<Integer, Integer> mapOfFtrs = Straff_WeightCalc.mapOfTPNonProdJpsPerSizeNonProdFtrVec.get(sizeOfCompVec);
			String tmp = "";
			for(Integer jp : mapOfFtrs.keySet()) {tmp += ""+jp+","+mapOfFtrs.get(jp)+",";}
			msgObj.dispInfoMessage("Straff_SOMTruePrspctMapper","dispCompFtrVecRes","\t" + sizeOfCompVec + ", " + Straff_WeightCalc.mapOfNonProdFtrVecDims.get(sizeOfCompVec)+ ", " +mapOfFtrs.size() + "," + tmp);
		}
		
		msgObj.dispInfoMessage("Straff_SOMTruePrspctMapper","dispCompFtrVecRes","" + Straff_TrueProspectExample.NumTPWithNoFtrs+ " True Prospect records with no product-jp-related information - max nonprod jps seen : " +Straff_TrueProspectExample.maxNumNonProdJps+" | largest comp vector seen : " + Straff_WeightCalc.numNonProdFtrVecDims+".");
		
		dbg_dispFtrVecMinMaxs(Straff_WeightCalc.mapOfTPCompFtrVecMins, Straff_WeightCalc.mapOfTPCompFtrVecMaxs, "dispCompFtrVecRes");

		Straff_TrueProspectExample.NumTPWithNoFtrs = 0;
		Straff_TrueProspectExample.maxNumNonProdJps =0;
		Straff_WeightCalc.numNonProdFtrVecDims= 0;
		Straff_WeightCalc.mapOfNonProdFtrVecDims.clear();
		Straff_WeightCalc.mapOfTPNonProdJpsPerSizeNonProdFtrVec.clear();
	}//dispCompFtrVecRes
	
	protected final void dbg_dispFtrVecMinMaxs(ConcurrentSkipListMap<Integer, Float> minMap, ConcurrentSkipListMap<Integer, Float> maxMap, String callClass) {	
		msgObj.dispInfoMessage(callClass,"dbg_dispFtrVecMinMaxs","JP,Min Value,Max Value.");
		for(Integer idx : minMap.keySet()) {
			Integer jp = jpJpgMon.getFtrJpByIdx(idx);
			msgObj.dispInfoMessage(callClass,"dbg_dispFtrVecMinMaxs",""+jp+","+minMap.get(idx)+","+maxMap.get(idx));
		}
		minMap.clear();
		maxMap.clear();
	}
	
	//display calc equations for each JP id
	@Override
	public String toString() {
		String res  = "";
		for (Straff_JPWeightEquation eq : allEqs.values()) {					
			Integer ftrIDX = eq.jpIDXs[bndAra_ProdJPsIDX];//, allIDX = eq.jpIDXs[bndAra_AllJPsIDX];
			if(ftrIDX != -1) {	res+= eq.toString() + " | # Ftr Calcs done : " +  bndsAra[bndAra_ProdJPsIDX].getDescForIdx(ftrIDX) + "\n";	} 
			else {				res+= eq.toString() +"  | # Ftr Calcs done : 0 (not a feature JP)\n";}			
			//res+= " | # Ttl Calcs done : # of Product Occs : " +String.format("%6d", numSeen) + " == " +  bndsAra[bndAra_AllJPsIDX].getDescForIdx(allIDX) + "\n";
		}
		res += "# ftr eqs : " + ftrEqs.size() + "\t# eqs ttl : " + allEqs.size() + "\t|Eq Wts from file : " + fileName+ "\t| Equation Configuration : \n";
		res += "-- NumOcc[i] : Number of occurrences of jp in event i on a specific date\n";
		res += "-- DEV : Days since event i occurred\n";
		res += "-- OptV : opt multiplier/filter value reflecting opt choice > 0 (<0 forces wt to be 0 for JP)\n\n";
		res += "\tIf opt val is <= 0 for all jps, there might be something we want to learn from this prospect\n\teven though they don't want to get emails. Such opts are ignored in these calculations.\n\n";
		res += "\tOn the other hand, if opt is <= 0 for some jps and not others, this suggests that something\n\tabout this JP may not be attractive to this person, so other prospects that look like them\n";
		res += "\tmay not want to see these jps either.\n\tWe currently learn from that by forcing this JP to have 0 weight for such a user, regardless of other JP data.\n\n";	
		
		return res;
	}//toString
	
}//StraffWeightCalc

