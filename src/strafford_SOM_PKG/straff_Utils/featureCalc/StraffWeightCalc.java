package strafford_SOM_PKG.straff_Utils.featureCalc;

import java.util.*;
import java.util.Map.Entry;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_utils.SOMProjConfigData;
import base_UI_Objects.*;
import base_Utils_Objects.*;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.JP_OccurrenceData;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.ProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
import strafford_SOM_PKG.straff_Utils.MonitorJpJpgrp;

/**
 * This class is intended to hold an object capable of calculation
 * the appropriate weighting of the various component terms that make up 
 * the weight vector used to train/query the SOM.  Allow this calculation to be built
 * from values in format file
 * @author john
 */
public class StraffWeightCalc {
	//public StraffSOMMapManager mapMgr;
	private MessageObject msgObj;
	public MonitorJpJpgrp jpJpgMon; 
	FileIOManager fileIO;
	//base file name, minus type and extension 
	private String fileName;
	private String[] fileTypes = new String[] {"train","compare"};
	public final Date now;
	//arrays are for idxs of various eq components (mult, offset, decay) in the format file for 
	//each component contributor (order, opt, link, source), in order.  0-idx is jp name or "default"
	private static final int[] mIdx = new int[] {1, 4, 7, 10},
			   					oIdx = new int[] {2, 5, 8, 11},
			   					dIdx = new int[] {3, 6, 9, 12};
	//map of per-jp equations to calculate feature vector from event data (expandable to more sources eventually if necessary).  keyed by jp
	//and ftrEqs map, holding only eqs used to directly calculate ftr vector values (subset, some jps are not used to train feature vec) (This is just a convenient filter object)
	private TreeMap<Integer, JPWeightEquation> allEqs, ftrEqs;	
	
	//hold relevant quantities for each jp calculation across all data; IDX 0 is for ftr vec calc, idx 1 is for all jps
	//idx 0 is for ftr vector calculation; idx 1 will be for alternate comparator vector calc
	private Float[][][] bndsAra;
	public static final int
		bndAra_TrainJPsIDX = 0,			//jps used for training (correspond to jps with products) - this is from ftr vector calcs
		bndAra_AllJPsIDX = 1;			//all jps in system, including those jps who do not have any products - this is from -comparator- calcs
	public static final int numJPInBndTypes = 2;	
	//separates calculations based on whether calc is done on a customer example, or on a true prospect example
	public static final int 
		custCalcObjIDX 		= 0,		//type of calc : this is data aggregated off of customer data (has prior order events and possibly other events deemed relevant) - this corresponds to training data source records
		tpCalcObjIDX 		= 1,		//type of calc : this is data from true prospect, who lack prior orders and possibly other event behavior
		trainCalcObjIDX		= 2;		//type of calc : actual training data, based on date of orders - 1 or more of these examples will be synthesized for each customer prospect
	public static final int numExamplTypeObjs = 3;
	//set initial values to properly initialize bnds ara
	private static final float[] initBnd = new float[] {1000000000.0f,-1000000000.0f, 0.0f, 0.0f, 0.0f};//min, max, count, diff
	//meaning of each idx in bndsAra 1st dimension 
	private static final int 
			minBndIDX = 0,					//mins for each feature
			maxBndIDX = 1,					//maxs for each feature
			countBndIDX = 2,				//count of entries for each feature
			diffBndIDX = 3; 				//max-min for each feature
	private static final int numBndTypes = 4;	
		
	private int[] stFlags;						//state flags - bits in array holding relevant process/state info
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
	
	public StraffWeightCalc(Straff_SOMMapManager _mapMgr, String _fileNamePrfx, MonitorJpJpgrp _jpJpgMon) {
		msgObj = _mapMgr.buildMsgObj();
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
		TreeMap<String, String[]> wtConfig = _loadWtCalcConfig(_fileNamePrfx+"_"+fileTypes[0]+".txt", "Ftr Calc");
		if(null==wtConfig) {msgObj.dispMessage("StraffWeightCalc","loadConfigAndSetVars", "Error! First non comment record in config file "+ _fileNamePrfx + " is not default weight map specification.  Exiting calc unbuilt.", MsgCodes.error2);return ;}
		TreeMap<String, String[]> wtConfig2 = _loadWtCalcConfig(_fileNamePrfx+"_"+fileTypes[1]+".txt", "Comparator Calc");
		boolean bothAreSame = false;
		if(null==wtConfig2) {wtConfig2 = wtConfig; bothAreSame=true;}
		String[] dfltAra = wtConfig.get("default");
		String[] dfltAra2 = wtConfig2.get("default");
		//first build default values for all eqs
		_buildDefaultEQs(dfltAra, dfltAra2);
		if((wtConfig.size()==0) && (wtConfig2.size()==0)) {return;}				//we're done - no custom jp eq mults specified
		//now find any per-jp specifications and build them as well
		_buildMultConfigCust(wtConfig, wtConfig2, dfltAra2);
		//repeat for wtConfig2 in case anything was missed if they are not the same structures
		if(!bothAreSame) {		_buildMultConfigCust(wtConfig2, wtConfig, dfltAra);	}
	}//loadConfigAndSetVars	
	
	private void _buildMultConfigCust(TreeMap<String, String[]> wtConfig, TreeMap<String, String[]> wtConfig2, String[] dfltAra2) {		
		for(String key : wtConfig.keySet()) {
			if(key.contains("default")){continue;}
			String[] wtCalcStrAra = wtConfig.get(key);
			String[] wtCalcStrAra2 = wtConfig2.get(key);
			if(null==wtCalcStrAra2) {	wtCalcStrAra2 = dfltAra2;}
			else {						wtCalcStrAra2 = wtConfig2.remove(key);}//remove from map so not queried when next run
			Integer jp = Integer.parseInt(wtCalcStrAra[0]);
			
			Float[] jpM = getFAraFromStrAra(wtCalcStrAra,mIdx), jpO = getFAraFromStrAra(wtCalcStrAra,oIdx), jpD = getFAraFromStrAra(wtCalcStrAra,dIdx);
			Float[] cpM = getFAraFromStrAra(wtCalcStrAra2,mIdx), cpO = getFAraFromStrAra(wtCalcStrAra2,oIdx), cpD = getFAraFromStrAra(wtCalcStrAra2,dIdx);
			Integer allIDX = jpJpgMon.getJpToAllIDX(jp);
			Integer ftrIDX = jpJpgMon.getFtrJpToIDX(jp);
			if(ftrIDX == null) {ftrIDX = -1;}
			Float[][] eqVals = new Float[][] {jpM, jpO, jpD,cpM, cpO, cpD};
			JPWeightEquation eq = new JPWeightEquation(this, jpJpgMon.getJPNameFromJP(jp),jp, new int[] {ftrIDX,allIDX}, eqVals, false);
			//overwrites existing eq
			allEqs.put(jp, eq);
			if(ftrIDX!=-1) {ftrEqs.put(jp, eq);	}			
		}
	}//_buildMultConfigCust

	//load specified config file to retrieve calc components for multiplier, decay and offset
	//returns a map idxed by either "default" or string rep of jp, and string array of 
	private TreeMap<String, String[]> _loadWtCalcConfig(String _fileName, String _type){		
		String[] configDatList = fileIO.loadFileIntoStringAra(_fileName, _type +" Weight Calc File Loaded", _type +" Weight Calc File Not Loaded Due To Error");		
		TreeMap<String, String[]> res = new TreeMap<String, String[]>();
		boolean foundDflt = false;
		int idx = 0;
		String[] strVals = new String[0];
		while (!foundDflt) {
			if ((configDatList[idx].contains(SOMProjConfigData.fileComment)) || (configDatList[idx].trim() == "")) {++idx;			}
			else {
				strVals = configDatList[idx].trim().split(",");
				if (strVals[0].toLowerCase().contains("default")) {	foundDflt = true;} 	//first non-comment/non-space is expected to be "default"
				else {return null;}
			}		
		}//while		
		res.put("default", strVals);
		
		//now go through every line and build eqs for specified jps
		for (int i=idx+1; i<configDatList.length; ++i) {		
			if (configDatList[i].contains(SOMProjConfigData.fileComment)) {continue;			}
			strVals = configDatList[i].trim().split(",");	
			res.put(strVals[0], strVals);
		}
		return res;
	}//_loadWtCalcConfig
	
	//build default equation values 
	private void _buildDefaultEQs(String[] strVals,String[] strVals2) {
		//string record has jp in col 0, 3 mult values 1-3, 3 offset values 4-6 and 3 decay values 7-9.
		Float[] dfltM = getFAraFromStrAra(strVals,mIdx), dfltO = getFAraFromStrAra(strVals,oIdx), dfltD = getFAraFromStrAra(strVals,dIdx);
		Float[] dfltcpM = getFAraFromStrAra(strVals2,mIdx), dfltcpO = getFAraFromStrAra(strVals2,oIdx), dfltcpD = getFAraFromStrAra(strVals2,dIdx);
		//strVals holds default map configuration - config all weight calcs to match this	
		//build eqs map for all jps found
		allEqs = new TreeMap<Integer, JPWeightEquation> ();
		ftrEqs = new TreeMap<Integer, JPWeightEquation> ();
		int ttlNumJps = jpJpgMon.getNumAllJpsFtrs();
		for (int i=0;i<ttlNumJps;++i) {
			int jp = jpJpgMon.getAllJpByIdx(i);
			Integer ftrIDX = jpJpgMon.getFtrJpToIDX(jp);
			if(ftrIDX == null) {ftrIDX = -1;}
			Float[][] eqVals = new Float[][] {dfltM, dfltO, dfltD,dfltcpM, dfltcpO, dfltcpD};
			JPWeightEquation eq = new JPWeightEquation(this,jpJpgMon.getJPNameFromJP(jp),jp, new int[] {ftrIDX,i}, eqVals, true);
			allEqs.put(jp, eq);
			if(ftrIDX!=-1) {ftrEqs.put(jp, eq);	}
		}//
		
	}//_buildDefaultEQs
	
	private Float[] fastCopyAra(int len, float val) {
		Float[] res = new Float[len];
		res[0]=val;	
		for (int i = 1; i < len; i += i) {System.arraycopy(res, 0, res, i, ((len - i) < i) ? (len - i) : i);}
		return res;
	}//fastCopyAra
		
	//reinit bounds ara
	//first key is 0==training ftr jps; 1==all jps;
	//second key is type of bound; 3rd key is jp
	private void initBnds() {	
		bndsAra = new Float[numJPInBndTypes][][];	
		int[] numFtrs = new int[] {jpJpgMon.getNumTrainFtrs(),jpJpgMon.getNumAllJpsFtrs()}; 
		for(int j=0;j<bndsAra.length;++j) {
			Float[][] tmpBndsAra = new Float[numBndTypes][];		
			for (int i=0;i<tmpBndsAra.length;++i) {
				tmpBndsAra[i]=fastCopyAra(numFtrs[j], initBnd[i]);
			}	
			bndsAra[j]=tmpBndsAra;
		}
	}//initBnds() 
	
	//get mins/diffs for ftr vals per ftr jp and for all vals per all jps
	public Float[][] getMinBndsAra() {return new Float[][] {bndsAra[0][0],bndsAra[1][0]};}
	public Float[][] getDiffsBndsAra() {return new Float[][] {bndsAra[0][3],bndsAra[1][3]};}
		
	//check if value is in bnds array for particular jp, otherwise modify bnd
	private void checkValInBnds(int bndJpType, Integer jpidx, Integer destIDX, float val) {
		if (val < bndsAra[bndJpType][minBndIDX][destIDX]) {bndsAra[bndJpType][minBndIDX][destIDX]=val;bndsAra[bndJpType][diffBndIDX][destIDX] = bndsAra[bndJpType][maxBndIDX][destIDX]-bndsAra[bndJpType][minBndIDX][destIDX];}
		if (val > bndsAra[bndJpType][maxBndIDX][destIDX]) {bndsAra[bndJpType][maxBndIDX][destIDX]=val;bndsAra[bndJpType][diffBndIDX][destIDX] = bndsAra[bndJpType][maxBndIDX][destIDX]-bndsAra[bndJpType][minBndIDX][destIDX];}
	}
	
	//increment count of training examples with jp data represented by destIDX, and total calc value seen
	public synchronized void incrBnds(int bndJpType, Integer destIDX) {
		bndsAra[bndJpType][countBndIDX][destIDX] +=1;
	}

	//read in string array of weight values, convert and put in float array
	private Float[] getFAraFromStrAra(String[] sAra, int[] idxs) {
		ArrayList<Float> res = new ArrayList<Float>();
		for(int i : idxs) {			res.add(Float.parseFloat(sAra[i]));		}		
		return res.toArray(new Float[0]);
	}
	
	///////////////////////////
	// calculate feature vectors - currently only works on product features - and comparator vectors - built off membership in jpgroups

	//calculate feature vector for true prospect example on actual product-based features - these features are for comparison, not training!
	public synchronized void calcTruePrspctFtrVec(ProspectExample ex, HashSet<Integer> jps,TreeMap<Integer, Float> ftrDest,
			TreeMap<Integer, JP_OccurrenceData> linkOccs,TreeMap<Integer, JP_OccurrenceData> optOccs,
			TreeMap<Integer, JP_OccurrenceData> srcOccs) {
		ftrDest.clear();
		float ftrVecSqMag = 0.0f;
		JP_OccurrenceData optOcc;
		//for each jp present in ex, perform calculation
		Integer destIDX;
		boolean isZeroMagExample = true;		//if all values are 0 then this is a bad training example, we want to ignore it
		for (Integer jp : jps) {
			//find destIDX
			destIDX = jpJpgMon.getFtrJpToIDX(jp);
			if (destIDX==null) {continue;}//ignore unknown/unmapped jps
			optOcc = optOccs.get(jp);
			if ((optOcc != null )&& ((ex.getPosOptAllOccObj() != null) || (ex.getNegOptAllOccObj() != null))) {	//opt all means they have opted for positive behavior for all jps that allow opts
				msgObj.dispMessage("StraffWeightCalc","calcTruePrspctFtrVec","Multiple opt refs for prospect : " + ex.OID + " : indiv opt and opt-all | This should not happen - opt events will be overly-weighted.", MsgCodes.warning4);	
			}
			float val = allEqs.get(jp).calcFtrVal(ex,null, linkOccs.get(jp), optOcc, srcOccs.get(jp),tpCalcObjIDX, bndAra_TrainJPsIDX, true);
			
			if (val != 0) {
				isZeroMagExample = false;
				ftrVecSqMag += (val*val);
			}
			ftrDest.put(destIDX,val);		//add zero value 			
			checkValInBnds(bndAra_TrainJPsIDX,jp,destIDX, val);
		}		
		ex.ftrVecMag = (float) Math.sqrt(ftrVecSqMag);
		ex.setIsBadExample(isZeroMagExample);
	}//calcFeatureVector	

	//calculate feature vector for this ProspectExample example on actual product-based features - these features are for comparison, not training!
	public void calcCustFtrDataVec(ProspectExample ex, HashSet<Integer> jps,TreeMap<Integer, Float> ftrDest,
			TreeMap<Integer, JP_OccurrenceData> orderOccs,TreeMap<Integer, JP_OccurrenceData> linkOccs, 
			TreeMap<Integer, JP_OccurrenceData> optOccs,TreeMap<Integer, JP_OccurrenceData> srcOccs) {
		ftrDest.clear();
		float ftrVecSqMag = 0.0f;
		JP_OccurrenceData optOcc;
		//for each jp present in ex, perform calculation
		Integer destIDX;
		boolean isZeroMagExample = true;		//if all values are 0 then this is a bad training example, we want to ignore it
		for (Integer jp : jps) {
			//find destIDX
			destIDX = jpJpgMon.getFtrJpToIDX(jp);
			if (destIDX==null) {continue;}//ignore unknown/unmapped jps
			optOcc = optOccs.get(jp);
			if ((optOcc != null )&& ((ex.getPosOptAllOccObj() != null) || (ex.getNegOptAllOccObj() != null))) {	//opt all means they have opted for positive behavior for all jps that allow opts
				msgObj.dispMessage("StraffWeightCalc","calcCustFtrDataVec","Multiple opt refs for prospect : " + ex.OID + " : indiv opt and opt-all | This should not happen - opt events will be overly-weighted.", MsgCodes.warning4);	
			}
			float val = allEqs.get(jp).calcFtrVal(ex,orderOccs.get(jp),linkOccs.get(jp), optOcc, srcOccs.get(jp),custCalcObjIDX ,bndAra_TrainJPsIDX, false);
			
			if (val != 0) {
				isZeroMagExample = false;
				ftrVecSqMag += (val*val);
			}
			ftrDest.put(destIDX,val);		//add zero value 			
			checkValInBnds(bndAra_TrainJPsIDX,jp,destIDX, val);
		}		
		ex.ftrVecMag = (float) Math.sqrt(ftrVecSqMag);
		ex.setIsBadExample(isZeroMagExample);
	}//calcFeatureVector
	
	//calculate feature vector for this training data example on actual product-based features - this will be a single order event-based training example
	public void calcTrainingFtrDataVec(ProspectExample ex, HashSet<Integer> jps,TreeMap<Integer, Float> ftrDest, Date orderDate,
			TreeMap<Integer, JP_OccurrenceData> orderOccs,TreeMap<Integer, JP_OccurrenceData> linkOccs, 
			TreeMap<Integer, JP_OccurrenceData> optOccs,TreeMap<Integer, JP_OccurrenceData> srcOccs) {
		ftrDest.clear();
		float ftrVecSqMag = 0.0f;
		JP_OccurrenceData optOcc;
		//for each jp present in ex, perform calculation
		Integer destIDX;
		boolean isZeroMagExample = true;		//if all values are 0 then this is a bad training example, we want to ignore it
		for (Integer jp : jps) {
			//find destIDX
			destIDX = jpJpgMon.getFtrJpToIDX(jp);
			if (destIDX==null) {continue;}//ignore unknown/unmapped jps
			optOcc = optOccs.get(jp);
			if ((optOcc != null )&& ((ex.getPosOptAllOccObj() != null) || (ex.getNegOptAllOccObj() != null))) {	//opt all means they have opted for positive behavior for all jps that allow opts
				msgObj.dispMessage("StraffWeightCalc","calcTrainingFtrDataVec","Multiple opt refs for prospect : " + ex.OID + " : indiv opt and opt-all | This should not happen - opt events will be overly-weighted.", MsgCodes.warning4);	
			}
			float val = allEqs.get(jp).calcTrainingFtr(ex,orderOccs.get(jp),linkOccs.get(jp), optOcc, srcOccs.get(jp), orderDate, bndAra_TrainJPsIDX, false);
			
			if (val != 0) {
				isZeroMagExample = false;
				ftrVecSqMag += (val*val);
			}
			ftrDest.put(destIDX,val);		//add zero value 			
			checkValInBnds(bndAra_TrainJPsIDX,jp,destIDX, val);
		}		
		ex.ftrVecMag = (float) Math.sqrt(ftrVecSqMag);
		ex.setIsBadExample(isZeroMagExample);
	}//calcFeatureVector
	

	//initialize and finalize all calcs for CUSTOMER data - this builds the exemplar training data vector for each non-prod jp
	public void initAllEqsForCustNonTrainCalc() {setFlag(custNonProdCalcCompleteIDX, false);	for(JPWeightEquation jpEq:allEqs.values()) {jpEq.initCalcCustNonProdWtVec();}}
	public void finalizeAllEqsCustForNonTrainCalc() {for(JPWeightEquation jpEq:allEqs.values()) {jpEq.finalizeCalcCustNonProdWtVec();}setFlag(custNonProdCalcCompleteIDX, true);}
	
	//this will calculate for every non-product jp the contribution that ex should get based on srcOccs wt calc for each non-product jp present in ex
	public void calcNonProdJpTrainFtrContribVec(ProspectExample ex, HashSet<Tuple<Integer,Integer>> nonProdJpgJps,TreeMap<Integer, Float> destMap, TreeMap<Integer, JP_OccurrenceData> srcOccs) {
		destMap.clear();
		float ttlContrib = 0.0f;
		for(Tuple<Integer,Integer> jpJpgrp : nonProdJpgJps) {
			Integer nonProdJP = jpJpgrp.y;
			JPWeightEquation jpEq = allEqs.get(nonProdJP);
			if(null==jpEq) {//msgObj.dispMessage("StraffWeightCalc","calcNonProdJpTrainFtrContribVec", "Null jpeq for jp : " + nonProdJP, MsgCodes.warning4);	
			continue;}
			JP_OccurrenceData srcOcc = srcOccs.get(nonProdJP);
			if(null==srcOcc) {continue;}
			float srcContrib = jpEq.getSrcContrib_Now(srcOcc);	//srcContrib is multiplied against vector and decayed from current date
			ttlContrib += srcContrib;
			TreeMap<Integer, Float> res2 = jpEq.calcNonProdWtVec(ex, srcContrib);
			for(Integer key : res2.keySet()) {
				Float val1 = destMap.get(key);
				if(val1 == null) {val1 = 0.0f;}
				val1 += res2.get(key);
				destMap.put(key, val1);
			}			
		}
		if(ttlContrib==0.0f) {ex.compValFtrDataMapMag = 0.0f; destMap.clear();return;}
		//divide entire res map by ttlContrib to normalize weighting
		float sqVal = 0.0f;
		for(Integer key : destMap.keySet()) {	
			float val = destMap.get(key)/ttlContrib;
			sqVal += val*val;
			destMap.put(key, val);
		}		
		ex.compValFtrDataMapMag = (float) Math.sqrt(sqVal);
	}//calcCompareObj

	//this will build the non-product jp training vectors based on weighting each passed example by the example's srcOccs data
	//for each non-product jp.  This trained vector gets stored in the jp eq object
	public void buildCustNonProdFtrVecs(ProspectExample ex, HashSet<Tuple<Integer,Integer>> nonProdJpgJps, TreeMap<Integer, JP_OccurrenceData> srcOccs) {
		//set this example's contribution by weighting their normalized feature vector by their non-prod-calc jp data
		for(Tuple<Integer,Integer> jpJpgrp : nonProdJpgJps) {
			Integer nonProdJP = jpJpgrp.y;
			JPWeightEquation jpEq = allEqs.get(nonProdJP);
			jpEq.buildCustNonProdWtVecContrib(ex, srcOccs.get(nonProdJP));
		}
	}//calcCompareObj

	
	//////////////////////////////////////////////////
	//end feature vector and comparator vec calc
	
	//////////////////////////////////////////////////
	// reporting functions
	
	///////
	//customer-based calculations
	
	public boolean custNonProdJpCalcIsDone() {return getFlag(custNonProdCalcCompleteIDX);}
	
	public boolean calcAnalysisIsReady_cust() {return getFlag(ftrCalcFlags[custCalcObjIDX]) && getFlag(calcCompleteFlags[custCalcObjIDX]);}		//ftr calc is done, and calc anaylsis has been done on these feature calcs	
	//called when all current prospect examples have been calculated
	public boolean isFinishedFtrCalcs_cust() {return getFlag(ftrCalcFlags[custCalcObjIDX]);}
	//retrieve a list of all eq performance data per ftr
	public ArrayList<String> getCalcAnalysisRes_cust(){return _getCalcAnalysisRes(custCalcObjIDX);}//getCalcAnalysisRes	
	
	public boolean calcAnalysisIsReady_tp() {return getFlag(ftrCalcFlags[tpCalcObjIDX]) && getFlag(calcCompleteFlags[tpCalcObjIDX]);}		//ftr calc is done, and calc anaylsis has been done on these feature calcs
	//called when all current prospect examples have been calculated
	public boolean isFinishedFtrCalcs_tp() {return getFlag(ftrCalcFlags[tpCalcObjIDX]);}	
	//retrieve a list of all eq performance data per ftr
	public ArrayList<String> getCalcAnalysisRes_tp(){return _getCalcAnalysisRes(tpCalcObjIDX);}//getCalcAnalysisRes	
	
	//this will reset all analysis components of feature vectors.  this is so that new feature calculations won't aggregate stats with old ones
	public void resetCalcObjs(int _exampleType) {for ( JPWeightEquation eq : allEqs.values()	) {	eq.resetAnalysis(_exampleType);	}	setFlag(ftrCalcFlags[_exampleType], false);}//resetCalcObjs
	
	//called when all current prospect examples have been calculated
	public void finishFtrCalcs(int _exampleType) {	setFlag(ftrCalcFlags[_exampleType], true); 	}	
	//after all features are calculated, run this first to finalize reporting statistics on eq performance
	public void finalizeCalcAnalysis(int _exampleType) {
		if (calcAnalysisShouldBeDone(_exampleType)) {
			for(JPWeightEquation jpEq:allEqs.values()) {jpEq.aggregateFtrCalcVals(_exampleType);}
			setFlag(calcCompleteFlags[_exampleType], true);
		}
	}//finalizeCalcAnalysis
	
	public boolean calcAnalysisIsReady(int _exampleType) {return getFlag(ftrCalcFlags[_exampleType]) && getFlag(calcCompleteFlags[_exampleType]);}		//ftr calc is done, and calc anaylsis has been done on these feature calcs
	private boolean calcAnalysisShouldBeDone(int _exampleType) {return getFlag(ftrCalcFlags[_exampleType]) && ! getFlag(calcCompleteFlags[_exampleType]);}//only perform analysis once on same set of collected ftr calc data
	
	//retrieve a list of all eq performance data per ftr
	private ArrayList<String> _getCalcAnalysisRes(int _exampleType){
		ArrayList<String> res = new ArrayList<String>();
		for(JPWeightEquation jpEq:allEqs.values()) {	res.addAll(jpEq.getCalcRes(_exampleType));}
		return res;
	}//getCalcAnalysisRes
	
	//draw res of all calcs as single rectangle of height ht and width barWidth*num eqs
	public void drawAllCalcRes(my_procApplet p, float ht, float barWidth, int curJPIdx,int _exampleType) {		
		p.pushMatrix();p.pushStyle();		
		for(JPWeightEquation jpEq:allEqs.values()) {	
		//for(int i=0;i<jpsToDraw.length;++i) {
			//JPWeightEquation jpEq = eqs.get(jpsToDraw[i]);
			//draw bar
			jpEq.drawFtrVec(p, ht, barWidth, jpEq.jpIDXs[bndAra_AllJPsIDX]==curJPIdx,bndAra_AllJPsIDX,_exampleType);
			//move over for next bar
			p.translate(barWidth, 0.0f, 0.0f);
		}
		p.popStyle();p.popMatrix();	
	}//draw analysis res for each graphically
	
	//draw only ftr JP calc res
	public void drawFtrCalcRes(my_procApplet p, float ht, float barWidth, int curJPIdx,int _exampleType) {		
		p.pushMatrix();p.pushStyle();		
		for(JPWeightEquation jpEq:ftrEqs.values()) {	//only draw eqs that calculated actual feature values (jps found in products)
			//draw bar
			jpEq.drawFtrVec(p, ht, barWidth, jpEq.jpIDXs[bndAra_TrainJPsIDX]==curJPIdx,bndAra_TrainJPsIDX,_exampleType);
			//move over for next bar
			p.translate(barWidth, 0.0f, 0.0f);
		}
		p.popStyle();p.popMatrix();	
	}//draw analysis res for each graphically
	
	//draw single detailed feature eq detailed analysis
	public void drawSingleFtr(my_procApplet p, float ht, float width, Integer jp,int _exampleType) {
		p.pushMatrix();p.pushStyle();		
		//draw detailed analysis
		allEqs.get(jp).drawIndivFtrVec(p, ht, width,_exampleType);
		p.popStyle();p.popMatrix();			
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

	
	//display calc equations for each JP id
	@Override
	public String toString() {
		String res  = "";
		for (JPWeightEquation eq : allEqs.values()) {
			Integer numSeen = jpJpgMon.getCountProdJPSeen(eq.jp);
			Float[][] ftrBndsAra = bndsAra[bndAra_TrainJPsIDX], allBndsAra = bndsAra[bndAra_AllJPsIDX];
			Integer ftrIDX = eq.jpIDXs[bndAra_TrainJPsIDX], allIDX = eq.jpIDXs[bndAra_AllJPsIDX];
			if(ftrIDX != -1) {
				res+= eq.toString()+"  | # Ftr Calcs done : " + String.format("%6d", (Math.round(ftrBndsAra[2][ftrIDX]))) + "\t| Min val : " +String.format("%6.4f", ftrBndsAra[0][ftrIDX]) + "\t| Max val : " +String.format("%6.4f", ftrBndsAra[1][ftrIDX]);
			} else {
				res+=eq.toString()+"  | # Ftr Calcs done : 0 (not a feature JP)";
			}
			res+= " | # Ttl Calcs done : " + String.format("%6d", (Math.round(allBndsAra[2][allIDX]))) + " ==  # of Product Occs : " +String.format("%6d", numSeen) + "\t| Min val : " +String.format("%6.4f", allBndsAra[0][allIDX]) + "\t| Max val : " +String.format("%6.4f", allBndsAra[1][allIDX]) + "\n";
		}
		res += "# eqs : " + allEqs.size() + "\t|Built from file : " + fileName+ "\t| Equation Configuration : \n";
		res += "-- DLU : Days since prospect lookup\n";
		res += "-- NumOcc[i] : Number of occurrences of jp in event i on a specific date\n";
		res += "-- DEV : Days since event i occurred\n";
		res += "-- OptV : opt multiplier/filter value reflecting opt choice > 0 (<0 forces wt to be 0 for JP)\n\n";
		res += "\tIf opt val is <= 0 for all jps, there might be something we want to learn from this prospect\n\teven though they don't want to get emails. Such opts are ignored in these calculations.\n\n";
		res += "\tOn the other hand, if opt is <= 0 for some jps and not others, this suggests that something\n\tabout this JP may not be attractive to this person, so other prospects that look like them\n";
		res += "\tmay not want to see these jps either.\n\tWe currently learn from that by forcing this JP to have 0 weight for such a user, regardless of other JP data.\n\n";	
		
		return res;
	}//toString
	
}//StraffWeightCalc

