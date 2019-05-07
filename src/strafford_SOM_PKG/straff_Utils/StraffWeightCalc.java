package strafford_SOM_PKG.straff_Utils;

import java.util.*;
import java.util.Map.Entry;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_utils.SOMProjConfigData;
import base_UI_Objects.*;
import base_Utils_Objects.*;
import strafford_SOM_PKG.straff_SOM_Examples.JP_OccurrenceData;
import strafford_SOM_PKG.straff_SOM_Examples.ProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

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
		custCalcObjIDX 		= 0,		//type of calc : this is data aggregated off of customer data (has prior order events and possibly other events deemed relevant) - this corresponds to training data
		tpCalcObjIDX 		= 1;		//type of calc : this is data from true prospect, who lack prior orders and possibly other event behavior
	public static final int numExamplTypeObjs = 2;
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
			debugIDX 					= 0,
			ftrCalcCustCompleteIDX		= 1,	//ftr weight calc has been completed for all loaded customer examples.
			custCalcAnalysisCompleteIDX	= 2,	//analysis of calc results completed for all customer records for this object
			ftrCalcTPCompleteIDX		= 3,	//ftr weight calc has been completed for all loaded true prospects examples.
			TPCalcAnalysisCompleteIDX	= 4,	//analysis of calc results completed for all true prospects records for this object
			custNonProdCalcCompleteIDX	= 5;	//the non-product jps have had their customer-derived feature vector contributions derived
	private static final int numFlags = 6;	
	//idx 0 is customer records; idx 1 is true prospect records
	private static final int[] ftrCalcFlags = new int[] {ftrCalcCustCompleteIDX,ftrCalcTPCompleteIDX};
	private static final int[] calcCompleteFlags = new int[] {custCalcAnalysisCompleteIDX,TPCalcAnalysisCompleteIDX};
	
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
	public void incrBnds(int bndJpType, Integer destIDX) {
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

	//calculate feature vector for true prospect example on actual product-based features
	public TreeMap<Integer, Float> calcTruePrspctFtrVec(ProspectExample ex, HashSet<Integer> jps, TreeMap<Integer, JP_OccurrenceData> linkOccs,
			TreeMap<Integer, JP_OccurrenceData> optOccs,TreeMap<Integer, JP_OccurrenceData> srcOccs) {
		TreeMap<Integer, Float> res = new TreeMap<Integer, Float>();
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
				msgObj.dispMessage("StraffWeightCalc","calcFeatureVector","Multiple opt refs for prospect : " + ex.OID + " : indiv opt and opt-all | This should not happen - opt events will be overly-weighted.", MsgCodes.warning4);	
			}
			float val = allEqs.get(jp).calcFtrVal(ex,null, linkOccs.get(jp), optOcc, srcOccs.get(jp),tpCalcObjIDX, bndAra_TrainJPsIDX, true);
			
			if (val != 0) {
				isZeroMagExample = false;
				ftrVecSqMag += (val*val);
			}
			res.put(destIDX,val);		//add zero value 			
			checkValInBnds(bndAra_TrainJPsIDX,jp,destIDX, val);
		}		
		ex.ftrVecMag = (float) Math.sqrt(ftrVecSqMag);
		ex.setIsBadExample(isZeroMagExample);
		return res;
	}//calcFeatureVector	

	//calculate feature vector for this customer example on actual product-based features
	public TreeMap<Integer, Float> calcTrainFtrVec(ProspectExample ex, HashSet<Integer> jps,
			TreeMap<Integer, JP_OccurrenceData> orderOccs,TreeMap<Integer, JP_OccurrenceData> linkOccs, 
			TreeMap<Integer, JP_OccurrenceData> optOccs,TreeMap<Integer, JP_OccurrenceData> srcOccs) {
		TreeMap<Integer, Float> res = new TreeMap<Integer, Float>();
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
				msgObj.dispMessage("StraffWeightCalc","calcFeatureVector","Multiple opt refs for prospect : " + ex.OID + " : indiv opt and opt-all | This should not happen - opt events will be overly-weighted.", MsgCodes.warning4);	
			}
			float val = allEqs.get(jp).calcFtrVal(ex,orderOccs.get(jp),linkOccs.get(jp), optOcc, srcOccs.get(jp),custCalcObjIDX ,bndAra_TrainJPsIDX, false);
			
			if (val != 0) {
				isZeroMagExample = false;
				ftrVecSqMag += (val*val);
			}
			res.put(destIDX,val);		//add zero value 			
			checkValInBnds(bndAra_TrainJPsIDX,jp,destIDX, val);
		}		
		ex.ftrVecMag = (float) Math.sqrt(ftrVecSqMag);
		ex.setIsBadExample(isZeroMagExample);
		return res;
	}//calcFeatureVector
	
	//initialize and finalize all calcs for CUSTOMER data - this builds the exemplar training data vector for each non-prod jp
	public void initAllEqsForCustNonTrainCalc() {setFlag(custNonProdCalcCompleteIDX, false);	for(JPWeightEquation jpEq:allEqs.values()) {jpEq.initCalcCustNonProdWtVec();}}
	public void finalizeAllEqsCustForNonTrainCalc() {for(JPWeightEquation jpEq:allEqs.values()) {jpEq.finalizeCalcCustNonProdWtVec();}setFlag(custNonProdCalcCompleteIDX, true);}
	
//	//build comparator object - alternate comparison for customers and prospects
//	public static int biggestTPNonProd = 0, biggestTPJPGrpNonProd = 0;
//	public static  HashSet<Integer> mostNonProdTPJpgrps = new  HashSet<Integer>();
//	public static  HashSet<Integer> mostNonProdTP_ProdJpgrps = new  HashSet<Integer>();
//	
//	//specifically for true prospects 
//	public TreeMap<Integer, Float> calcTruePrspctCompareDat(ProspectExample ex, 
//			HashSet<Integer> allProdJPs, HashSet<Integer> allProdJpGrps, HashSet<Tuple<Integer,Integer>> nonProdJpgJps, HashSet<Integer> nonProdJpGrps,
//			TreeMap<Integer, JP_OccurrenceData> linkOccs,TreeMap<Integer, JP_OccurrenceData> optOccs,TreeMap<Integer, JP_OccurrenceData> srcOccs) {	
//		TreeMap<Integer, Float> res = new TreeMap<Integer, Float>();
//		if(biggestTPNonProd <  nonProdJpgJps.size()) {
//			biggestTPNonProd= nonProdJpgJps.size();
//		}
//		if(biggestTPJPGrpNonProd <  nonProdJpGrps.size()) {
//			biggestTPJPGrpNonProd =  nonProdJpGrps.size();
//			mostNonProdTPJpgrps = new HashSet<Integer>(nonProdJpGrps);
//			mostNonProdTP_ProdJpgrps = new HashSet<Integer>(allProdJpGrps);
//		}
//		
//		TreeMap<Integer, Float> nonProdFtrVec = new TreeMap<Integer, Float>();
//		for(Tuple<Integer,Integer> jpJpgrp : nonProdJpgJps) {
//			Integer nonProdJP = jpJpgrp.y;
//			JPWeightEquation jpEq = allEqs.get(nonProdJP);
//			TreeMap<Integer, Float> res = jpEq.calcNonProdWtVec(ex, srcOccs.get(nonProdJP));
//		}
//		TreeMap<Integer, Float> calcNonProdWtVec
//		
//		return res;
//	
//	}//calcCompareObj
//	public static int biggestCustNonProd = 0, biggestCustJPGrpNonProd = 0;
//	public static HashSet<Integer> mostNonProdCustJpgrps = new  HashSet<Integer>();
//	public static  HashSet<Integer> mostNonProdCust_ProdJpgrps = new  HashSet<Integer>();

	
	
	//this will calculate for every non-product jp the contribution that ex should get based on srcOccs wt calc for each non-product jp present in ex
	public TreeMap<Integer, Float> calcNonProdJpTrainFtrContribVec(ProspectExample ex, HashSet<Tuple<Integer,Integer>> nonProdJpgJps, TreeMap<Integer, JP_OccurrenceData> srcOccs) {
		TreeMap<Integer, Float> res = new TreeMap<Integer, Float>();
		ex.compValFtrMapSqMag = 0.0f;
		float ttlContrib = 0.0f;
		for(Tuple<Integer,Integer> jpJpgrp : nonProdJpgJps) {
			Integer nonProdJP = jpJpgrp.y;
			JPWeightEquation jpEq = allEqs.get(nonProdJP);
			if(null==jpEq) {//msgObj.dispMessage("StraffWeightCalc","calcNonProdJpTrainFtrContribVec", "Null jpeq for jp : " + nonProdJP, MsgCodes.warning4);	
			continue;}
			JP_OccurrenceData srcOcc = srcOccs.get(nonProdJP);
			if(null==srcOcc) {continue;}
			float srcContrib = jpEq.getSrcContrib(srcOcc);	//srcContrib is multiplied against vector
			ttlContrib += srcContrib;
			TreeMap<Integer, Float> res2 = jpEq.calcNonProdWtVec(ex, srcContrib);
			for(Integer key : res2.keySet()) {
				Float val1 = res.get(key);
				if(val1 == null) {val1 = 0.0f;}
				val1 += res2.get(key);
				res.put(key, val1);
			}			
		}
		if(ttlContrib==0.0f) {return res;}
		//divide entire res map by ttlContrib to normalize weighting
		for(Integer key : res.keySet()) {	
			float val = res.get(key)/ttlContrib;
			ex.compValFtrMapSqMag += val*val;
			res.put(key, val);
		}		
		return res;
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

//this class will hold the weight calculation coefficients for a single jp this will incorporate the following quantities : 
//presence or absence in prospect record count and datetime of occurence in order record
//count, datetime and opt value of each occurence in opt record. this object only works on prospect examples.  
//No other example should use this calculation object, or analysis statistics will be skewed
class JPWeightEquation {
	public final StraffWeightCalc calcObj;
	public static Date now;
	public static final Date oldDate = new GregorianCalendar(2009, Calendar.JANUARY, 1).getTime();
	public final int jp;				//corresponding jp
	public final int[] jpIDXs;			//jp idx in ftr vector; jp idx in all jps list (if not in feature vector then jpIdx == -1
	public final String jpName;
	
	//sentinel value for opt calc meaning force total res for this calc to be 0 based on negative per-jp opt from user
	protected static final float optOutSntnlVal = -9999.0f;
	
	//mults and offsets are of the form (mult * x + offset), where x is output of membership function
	public static final int 						//idxs in eq coefficient arrays
			orderCoeffIDX = 0, 
			optCoeffIDX = 1,
			linkCoeffIDX = 2,
			srcCoeffIDX = 3;
	public static final int numEqs = 4;
	//these names must match order and number of component idxs above
	public static final String[] calcNames = new String[] {"Order","Opt","Link","Source"};
	
	//ftr calc coefficients : 
	private final Float[] FtrMult;						//multiplier for membership functions
	private final Float[] FtrOffset;					//offsets for membership functions	
	//membership functions hold sum( x / (1 + decay*delT)) for each x, where x is # of occurences on a 
	//particular date and decay is some multiplier based on elapsed time delT (in days)
	private final Float[] FtrDecay;
	private final Float[][] FtrParams;
	
	//comparator calc coefficients - this is for non-training-feature handling
	private final Float[] CompMult;						//multiplier for membership functions
	private final Float[] CompOffset;					//offsets for membership functions	
	//membership functions hold sum( x / (1 + decay*delT)) for each x, where x is # of occurences on a 
	//particular date and decay is some multiplier based on elapsed time delT (in days)
	private final Float[] CompDecay;
	private final Float[][] CompParams;
	
	//analysis function for this eq component
	private calcAnalysis[] ftrCalcStats;
	
	//this only is used by weight equations that correspond to jps that do not provide training data
	//this is the training data vector built from all training data examples for this non-product jp
	private TreeMap<Integer, Float> aggTrueTrainFtrData;
	private Float aggTrueTrainTtlWt;
	
	
	//if this equation is using default values for coefficients
	public boolean isDefault;

	//public JPWeightEquation(StraffWeightCalc _calcObj, String _name, int _jp, int[] _jpIdxs, Float[] _m, Float[] _o, Float[] _d, boolean _isDefault) {
	//_eqVals : idx 0->2 are ftr vals; idx 3->5 are comparator vals
	public JPWeightEquation(StraffWeightCalc _calcObj, String _name, int _jp, int[] _jpIdxs, Float[][] _eqVals, boolean _isDefault) {
		calcObj = _calcObj; now = calcObj.now;jp=_jp;jpIDXs=_jpIdxs; jpName=_name;
		//for feature calculation equations
		FtrMult = new Float[numEqs];
		FtrOffset = new Float[numEqs];
		FtrDecay = new Float[numEqs];
		//for comparator object calculations
		CompMult = new Float[numEqs];
		CompOffset = new Float[numEqs];
		CompDecay = new Float[numEqs];
		System.arraycopy(_eqVals[0], 0, FtrMult, 0, numEqs);
		System.arraycopy(_eqVals[1], 0, FtrOffset, 0, numEqs);
		System.arraycopy(_eqVals[2], 0, FtrDecay, 0, numEqs);	
		System.arraycopy(_eqVals[3], 0, CompMult, 0, numEqs);
		System.arraycopy(_eqVals[4], 0, CompOffset, 0, numEqs);
		System.arraycopy(_eqVals[5], 0, CompDecay, 0, numEqs);	
		FtrParams = new Float[][] {FtrMult, FtrOffset, FtrDecay};
		CompParams = new Float[][] {CompMult, CompOffset, CompDecay};
		
		aggTrueTrainFtrData = new TreeMap<Integer, Float>();
		aggTrueTrainTtlWt = 0.0f;		
		
		isDefault = _isDefault;
		ftrCalcStats = new calcAnalysis[StraffWeightCalc.numExamplTypeObjs];
		for(int i=0;i<ftrCalcStats.length;++i) {			ftrCalcStats[i] = new calcAnalysis(this, jpIDXs);		}	
	}//ctor
	//decay # of purchases by # of days difference * decay multiplier
	private float decayCalc(int idx, int num, Float[] decay, Date date) {
		if (decay[idx] == 0) {return 1.0f*num;}
		if (date == null) {date = oldDate;} //if no date then consider date is very old
		float decayAmt = 0.0f;
		if (now.after(date)) {//now is more recent than the date of the record
			//86.4 mil millis in a day	
			decayAmt = decay[idx] * (now.getTime() - date.getTime())/86400000.0f;
		}
		return (num/(1.0f + decayAmt));
	}//decayCalc
	//perform scale calculation - M * ( (# occs)/(DecayMult * # of days since event)) + offset
	private float scaleCalc(int idx, int num, Float[][] params, Date date) {	
		float val = decayCalc(idx, num, params[2],date);		
		return  (params[0][idx] * val) + params[1][idx];
	}
	//get total weight contribution for all events of this jp, based on their date
	protected float aggregateOccs(JP_OccurrenceData jpOcc, int idx, Float[][] params) {
		float res = 0.0f;
		Date[] dates = jpOcc.getDatesInOrder();
		for (Date date : dates) {
			TreeMap<Integer, Integer> occDat = jpOcc.getOccurrences(date);
			int numOcc = 0;
			//key is opt value (here sentinel value, since this is from events other than source and opt) and value is count of jp events at that date
			for (Integer count : occDat.values()) {				numOcc += count;			}
			res += scaleCalc(idx,numOcc, params, date);
		}
		return res;
	}//aggregateOccs
	
	//get total weight contribution for all events of this jp, based on their date
	protected float aggregateOccsSourceEv(JP_OccurrenceData jpOcc, int idx, Float[][] params) {
		float res = 0.0f;
		Date[] dates = jpOcc.getDatesInOrder();
		for (Date date : dates) {
			TreeMap<Integer, Integer> occDat = jpOcc.getOccurrences(date);
			int numOcc = 0;
			//key is type of occurrence in source data, 
			//value is count TODO manage source event type calc?
			for (Integer typeSrc : occDat.keySet()) {		numOcc += occDat.get(typeSrc);	}
			res += scaleCalc(idx,numOcc, params, date);
		}
		return res;
	}//aggregateOccs
	
	//calculate opt data's contribution to feature vector
	protected float calcOptRes(JP_OccurrenceData jpOcc, Float[][] params) {
		float res = 0;
		//multiple opt occurences of same jp should have no bearing - want most recent opt event - may want to research multiple opt events for same jp, if opt values differ
		//if opt val is -2 for all jps, there might be something we want to learn from this prospect even though they don't want to get emails;  we won't see those people's opt here.
		//On the other hand, if opt is -2 for some jps and not others, this would infer that something about this particular JP may not be attractive to this person, 
		//so other prospects that look like them may not want to see these jps either, so we learn from that - we set this ftr value to be 0 for an opt < 0 , 
		//regardless of what other behavior they have for this jp
		//ignores opts of 0.  TODO need to determine appropriate behavior for opts of 0, if there is any
		Entry<Date, TreeMap<Integer, Integer>> vals = jpOcc.getLastOccurrence();
		//vals is entry, vals.getValue is treemap keyed by opt, value is count (should always be 1)
		Integer optChoice = vals.getValue().lastKey(); 
		//last key is going to be extremal opt value - largest key value, which would be highest opt for this jp seen on this date.
		if (optChoice < 0) {res = optOutSntnlVal;}
		else if (optChoice > 0) {	res =  (params[0][optCoeffIDX] * optChoice/(1+params[2][optCoeffIDX])) + params[1][optCoeffIDX];  	}
		return res;
	}//calcOptRes
	
	//reset analysis object to clear out all stats from previous run
	public void resetAnalysis(int _exampleType) {ftrCalcStats[_exampleType].reset();	}	
	public void aggregateFtrCalcVals(int _exampleType) {ftrCalcStats[_exampleType].aggregateCalcVals();}	
	public ArrayList<String> getCalcRes(int _exampleType){return ftrCalcStats[_exampleType].getCalcRes();}	
	
	//calculate a particular example's feature weight value for this object's jp
	//int _exampleType : whether a customer or a true prospect
	//int _bndJPType : whether the jp is an actual ftr jp (in products) or is part of the global jp set (might be ftr jp, might not)
	public float calcFtrVal(ProspectExample ex, JP_OccurrenceData orderJpOccurrences, JP_OccurrenceData linkJpOccurrences, JP_OccurrenceData optJpOccurrences, JP_OccurrenceData srcJpOccurrences, int _exampleType, int _bndJPType, boolean _modBnds) {	
		boolean hasData = false;
			//for source data - should replace prospect calc above
		if (srcJpOccurrences != null) {	hasData = true;		ftrCalcStats[_exampleType].setWSVal(srcCoeffIDX, aggregateOccsSourceEv(srcJpOccurrences, srcCoeffIDX,FtrParams));}//calcStats.workSpace[orderCoeffIDX] = aggregateOccs(orderJpOccurrences, orderCoeffIDX);}
			//handle order occurrences for this jp.   aggregate every order occurrence, with decay on importance based on date
		if (orderJpOccurrences != null) {hasData = true;	ftrCalcStats[_exampleType].setWSVal(orderCoeffIDX, aggregateOccs(orderJpOccurrences, orderCoeffIDX,FtrParams));}//calcStats.workSpace[orderCoeffIDX] = aggregateOccs(orderJpOccurrences, orderCoeffIDX);}
			//for links use same mechanism as orders - handle differences through weightings - aggregate every order occurrence, with decay on importance based on date
		if (linkJpOccurrences != null) {hasData = true;		ftrCalcStats[_exampleType].setWSVal(linkCoeffIDX, aggregateOccs(linkJpOccurrences, linkCoeffIDX,FtrParams));	}//calcStats.workSpace[linkCoeffIDX] = aggregateOccs(linkJpOccurrences, linkCoeffIDX);	}
			//user opts - these are handled differently - calcOptRes return of -9999 means negative opt specified for this jp alone (ignores negative opts across all jps) - should force total from eq for this jp to be ==0
		if (optJpOccurrences != null) {	hasData = true;		ftrCalcStats[_exampleType].setWSVal(optCoeffIDX, calcOptRes(optJpOccurrences,FtrParams));}	//calcStats.workSpace[optCoeffIDX] = calcOptRes(optJpOccurrences);}	
		if (_modBnds && hasData) {calcObj.incrBnds(_bndJPType,jpIDXs[_bndJPType]);		}//_modBnds means this calc should actually modify bounds - if calcing for true prospect, we don't necessarily want to do this, since this may modify how training data ends up being
		float res = ftrCalcStats[_exampleType].getFtrValFromCalcs(optCoeffIDX, optOutSntnlVal);//(calcStats.workSpace[optCoeffIDX]==optOutSntnlVal);
		return res;
	}//calcVal
	
	
	//initialize this non-product jp's equation to aggregate the appropriate ftr vector info
	public void initCalcCustNonProdWtVec() {
		aggTrueTrainFtrData = new TreeMap<Integer, Float>();
		aggTrueTrainTtlWt = 0.0f;		
	}//initCalcCustNonProdWtVec

	//source data contribution calculation 
	public float getSrcContrib(JP_OccurrenceData srcJpOccurrences) {
		if (srcJpOccurrences != null) { return aggregateOccsSourceEv(srcJpOccurrences, srcCoeffIDX,CompParams);}//calcStats.workSpace[orderCoeffIDX] = aggregateOccs(orderJpOccurrences, orderCoeffIDX);}
		else return 0.0f;
	}//getSrcContrib
	
	//calculate the training data feature vector corresponding to this (non-prod - will only exist for non-prod) jp-based eq (this eq object will represent a non-training-data jp)
	//by calculating the source info wt as normal
	public void buildCustNonProdWtVecContrib(ProspectExample ex, JP_OccurrenceData srcJpOccurrences) {	
			//for source data - should replace prospect calc above
		float wt = 0;
		if (srcJpOccurrences != null) {	wt = aggregateOccsSourceEv(srcJpOccurrences, srcCoeffIDX,CompParams);}//calcStats.workSpace[orderCoeffIDX] = aggregateOccs(orderJpOccurrences, orderCoeffIDX);}
		if(wt==0) {return;}
		//use normalized ftr vector from ex, weight this example's contribution by it's source calced value 
		TreeMap<Integer, Float> normData = ex.getCurrentFtrMap(SOMMapManager.useNormedDat);
		for(Integer key : normData.keySet()) {
			Float ftrWt = aggTrueTrainFtrData.get(key);
			if(ftrWt==null) {ftrWt = 0.0f;}
			ftrWt += wt * normData.get(key);
			aggTrueTrainFtrData.put(key, ftrWt);
		}
		aggTrueTrainTtlWt +=wt;		
		
	}//calcVal	
	
	public void finalizeCalcCustNonProdWtVec() {//normalize feature vector weight multiplier
		if(0==aggTrueTrainTtlWt) {aggTrueTrainFtrData = new TreeMap<Integer, Float>();return;}
		for(Integer key : aggTrueTrainFtrData.keySet()){aggTrueTrainFtrData.put(key, aggTrueTrainFtrData.get(key)/aggTrueTrainTtlWt);}
	}//
	
	//calculate the training data feature vector corresponding to this (non-prod - will only exist for non-prod) jp-based eq (this eq object will represent a non-training-data jp)
	//for the passed true prospect by seeing their weight and multiplying the pre-calculated ftr vector 
	public TreeMap<Integer, Float> calcNonProdWtVec(ProspectExample ex, float wt) {	
			//for source data - should replace prospect calc above
		TreeMap<Integer, Float> res = new TreeMap<Integer, Float>();		
		//get this non-prod jp's contribution to ex's weight vector
		if (aggTrueTrainFtrData.size() == 0) {return res;}
		for(Integer key : aggTrueTrainFtrData.keySet()) {			
			res.put(key, aggTrueTrainFtrData.get(key) * wt);
		}

		return res;
	}//calcVal	
	
	public void drawIndivFtrVec(my_procApplet p, float height, float width, int _exampleType) {ftrCalcStats[_exampleType].drawIndivFtrVec(p, height, width);	}
	public void drawFtrVec(my_procApplet p, float height, float width, boolean selected, int eqDispType, int _exampleType){ftrCalcStats[_exampleType].drawFtrVec(p, height, width,eqDispType, selected);}
	
	//string rep of this calc
	public String toString() {
		int jpFtrIDX = jpIDXs[calcObj.bndAra_TrainJPsIDX];
		int jpAllIDX = jpIDXs[calcObj.bndAra_AllJPsIDX];
		String jpAllBuffer = (jpAllIDX >=100) ? "" : (jpAllIDX >=10) ? " " : "  ";//to align output 
		String res = "JP : "+ String.format("%3d", jp) + " JPs["+jpAllIDX+"]" + jpAllBuffer;
		if(jpFtrIDX >= 0) {
			String ftrBuffer = (jpFtrIDX >=100) ? "" : (jpFtrIDX >=10) ? " " : "  ";//to align output
			res += " Ftr[" + jpFtrIDX + "]" + ftrBuffer + " = ";				
		} 
		else {	res += " (Not a Training Ftr) = ";	}
		//order
		res += " + ("+ String.format("%.3f", FtrMult[orderCoeffIDX]) + "*[sum(NumOcc[i]/(1 + "+String.format("%.4f", FtrDecay[orderCoeffIDX])+" * DEV)) for each event i] + "+String.format("%.4f", FtrOffset[orderCoeffIDX])+")"; 		
		//link
		res += " + ("+ String.format("%.3f", FtrMult[linkCoeffIDX]) + "*[sum(NumOcc[i]/(1 + "+String.format("%.4f", FtrDecay[linkCoeffIDX])+" * DEV)) for each event i] + "+String.format("%.4f", FtrOffset[linkCoeffIDX])+")"; 		
		//opt
		res +=  " + ("+ String.format("%.3f", FtrMult[optCoeffIDX]) + "*OptV/(1 + "+String.format("%.4f", FtrDecay[orderCoeffIDX])+" + "+String.format("%.4f", FtrOffset[optCoeffIDX])+")"; 			
		return res;
	}//toString
}//JPWeightEquation



//this class will hold analysis information for calculations to more clearly understand the results of the current calc object
class calcAnalysis{//per JPWeightEquation analysis of data
		//corresponding eq - get all pertinent info from this object
	private JPWeightEquation eq;	
		//totals seen across all examples per individual calc components(prospect, opt, order, link, etc);sum of sq value, for variance/std calc
	private float[] vals, valSq;
	
	//calculated statistics 
	private float[][] analysisCalcStats;
	//calculated totals of statistic values, to get appropriate ratios
	private float[] ttlCalcStats_Vis;
	private static final int
		ratioIDX = 0,			//ratio of total wt contribution for JP for particular eq-type value (i.e. orders, links, etc.)
		meanIDX = 1,			//over all examples for particular JP, including opt-outs
		meanOptIDX = 2,			//only include non-opt-out data
		stdIDX = 3,				//over all examples incl opt-outs
		stdOptIDX = 4;			//only non-opt-out
	private static final int numCalcStats = 5;
	//titles of each stat-of-interest disp bar
	private static final String[] calcStatTitles = new String[] {"Ratios", "Means", "Means w/Opts", "Stds","Stds w/Opts"};
	//descriptive text under each detail display bar
	private String[][] calcStatDispDetail;
	//perStat perCalcEqType Descriptive string of value being represented in bar
	private String[][] analysisCalcValStrs;
	//description for legend
	private String[] legendDatStrAra;
		//workspace used by calc object to hold values - all individual values calculated - this is so we can easily aggregate analysis results to calc object	
	private float[] workSpace;
		//total seen across all individual calcs, across all examples; mean value sent per non-opt, sqVal for std calc; stdVal of totals
	private float ttlVal, ttlSqVal;//, ttlMeansVec_vis, ttlStdsVec_vis, ttlMeansOptVec_vis, ttlStdsOptVec_vis;
		//number of eqs processed - increment on total
	private int numExamplesWithJP = 0;
	private int numExamplesNoOptOut = 0;
		//counts of each type
	private int[] eqTypeCount;

	//array of analysis components for string display
	private ArrayList<String> analysisRes;
	//how far to move down for text
	private static float txtYOff = 10.0f;
	
	private static float[] legendSizes;
	//disp idx of this calc;jpIDX corresponding to owning eq - NOTE this is either the index in the ftr vector, or it is the index in the list of all jps
	private final int[] jpIDXara;
	private int[] dispIDXAra;
	//
	public calcAnalysis(JPWeightEquation _eq, int[] _jpIDX) {
		eq=_eq;reset();
		jpIDXara=_jpIDX;
		dispIDXAra = new int[] {jpIDXara[0]%5,jpIDXara[1]%5} ;
		legendSizes=new float[eq.numEqs];
		for(int i=0;i<eq.numEqs;++i) {legendSizes[i]= 1.0f/eq.numEqs;}
	}//ctor
	//reset this calc analysis object
	public void reset() {
		vals = new float[eq.numEqs];
		eqTypeCount = new int[eq.numEqs];
		valSq = new float[vals.length];
		workSpace = new float[vals.length];
		legendDatStrAra = new String[vals.length];
		numExamplesWithJP = 0;
		numExamplesNoOptOut = 0;
		ttlVal=0.0f;
		ttlSqVal=0.0f;
		//per stat type; per calc val type
		analysisCalcStats = new float[numCalcStats][];
		calcStatDispDetail = new String[numCalcStats][];
		ttlCalcStats_Vis = new float[numCalcStats];
		for(int i=0; i<numCalcStats;++i) {		analysisCalcStats[i] = new float[vals.length];}			
		analysisRes = new ArrayList<String>();
	}//reset()
	
	//overwrites old workspace calcs
	public void setWSVal(int idx, float val) {
		workSpace[idx]=val;		
		if (val != 0.0f) {eqTypeCount[idx]++;}		
	}
	
	//add values for a particular calc run - returns calc total
	public float getFtrValFromCalcs(int optCoeffIDX, float optOutValCk) {
			//if below is true, then this means this JP's opt out value was set to sentinel val to allow for calculations but ultimately to set the weight of this jp's contribution to 0
		boolean optOut = workSpace[optCoeffIDX]==optOutValCk;
		++numExamplesWithJP;		//if optOut is true, then this increases # of examples, but all values will be treated as 0
			//opt result means all values are cleared out (user opted out of this specific JP) so only increment numExamplesWithJP
		if (optOut) {return 0.0f;	}
		++numExamplesNoOptOut;
			//res is per record result - this is result of eq for calculation, used by weight.
		float res = 0;
			//add all results and also add all individual results to vals
		for (int i=0;i<vals.length;++i) {
			res += workSpace[i];
			vals[i] += workSpace[i];
			valSq[i] += workSpace[i] * workSpace[i];//variance== (sum ( vals^2))/N  - mean^2
		}
		ttlVal += res;
		ttlSqVal += res*res;
		return res;		
	}//addCalcsToVals	

	//aggregate collected values and calculate all relevant statistics
	public void aggregateCalcVals() {
		//per stat type; per calc val type
		analysisCalcStats = new float[numCalcStats][];
		analysisCalcValStrs = new String[numCalcStats][];
		calcStatDispDetail = new String[numCalcStats][];
		ttlCalcStats_Vis = new float[numCalcStats];
		legendDatStrAra = new String[vals.length];
		for(int i=0; i<numCalcStats;++i) {		analysisCalcStats[i] = new float[vals.length];analysisCalcValStrs[i] = new String[vals.length];}			
		analysisRes = new ArrayList<String>();
		
		String perCompMuStd = "";
		if(ttlVal == 0.0f) {return;}
		for (int i=0;i<vals.length;++i) {
			analysisCalcStats[ratioIDX][i] = vals[i]/ttlVal;
			analysisCalcStats[meanIDX][i] = vals[i]/numExamplesNoOptOut;
			analysisCalcStats[meanOptIDX][i] = vals[i]/numExamplesWithJP;		//counting opt-out records
			analysisCalcStats[stdIDX][i] = (float) Math.sqrt((valSq[i]/numExamplesNoOptOut) - (analysisCalcStats[meanIDX][i]*analysisCalcStats[meanIDX][i]));//E[X^2] - E[X]^2
			analysisCalcStats[stdOptIDX][i] = (float) Math.sqrt((valSq[i]/numExamplesWithJP) - (analysisCalcStats[meanOptIDX][i]*analysisCalcStats[meanOptIDX][i]));		
			perCompMuStd += "|Mu:"+String.format("%.5f",analysisCalcStats[meanIDX][i])+"|Std:"+String.format("%.5f",analysisCalcStats[stdIDX][i]);			
			legendDatStrAra[i] = eq.calcNames[i] + " : "+ eqTypeCount[i]+" exmpls.";			
		}
		for (int i=0;i<analysisCalcStats.length;++i) {
			ttlCalcStats_Vis[i]=0.0f;
			for (int j=0;j<analysisCalcStats[i].length;++j) {		
				ttlCalcStats_Vis[i] += analysisCalcStats[i][j];		
				analysisCalcValStrs[i][j] = String.format("%.5f", analysisCalcStats[i][j]);
			}
		}
		
		float ratioOptOut = 1 - (1.0f*numExamplesNoOptOut)/numExamplesWithJP;			//ratio of all examples that have opted out with 0 ttl contribution to jp in ftr
		float ttlMeanNoOpt = ttlVal/numExamplesNoOptOut;
		float ttlMeanWithOpt = ttlVal/numExamplesWithJP;
		float ttlStdNoOpt = (float) Math.sqrt((ttlSqVal/numExamplesNoOptOut)  - (ttlMeanNoOpt * ttlMeanNoOpt));
		float ttlStdWithOpt = (float) Math.sqrt((ttlSqVal/numExamplesWithJP)  - (ttlMeanWithOpt * ttlMeanWithOpt));
		calcStatDispDetail[ratioIDX] = new String[] {String.format("Ratio of Opts To Ttl : %.5f",ratioOptOut), String.format("# of examples : %05d",numExamplesWithJP),String.format("# of ex w/o Opt out : %05d",numExamplesNoOptOut)};
		calcStatDispDetail[meanIDX] = new String[] {String.format("Mean : %.5f",ttlMeanNoOpt)};
		calcStatDispDetail[meanOptIDX] = new String[] {String.format("Mean w/opts : %.5f", ttlMeanWithOpt)};
		calcStatDispDetail[stdIDX] = new String[] {String.format("Stds : %.5f",ttlStdNoOpt)};
		calcStatDispDetail[stdOptIDX] = new String[] {String.format("Std w/opts : %.5f",ttlStdWithOpt)};		
		
		analysisRes.add("FTR IDX : "+String.format("%03d", jpIDXara[0])+"ALL JP IDX : "+String.format("%03d", jpIDXara[1])+"|JP : "+String.format("%03d", eq.jp)+"|% opt:"+String.format("%.5f",ratioOptOut)
					+"|MU : " + String.format("%.5f",ttlMeanNoOpt)+"|Std : " + String.format("%.5f",ttlStdNoOpt) 
					+"|MU w/opt : " +String.format("%.5f",ttlMeanWithOpt)+"|Std w/opt : " +String.format("%.5f",ttlStdWithOpt));
		analysisRes.add(perCompMuStd);
	}//aggregateCalcVals
		
	//this will display a vertical bar corresponding to the performance of the analyzed calculation.
	//each component of calc object will have a different color
	//height - the height of the bar.  start each vertical bar at upper left corner, put text beneath bar
	public void drawFtrVec(my_procApplet p, float height, float width, int eqDispType, boolean selected){
		p.pushMatrix();p.pushStyle();
		float rCompHeight, rYSt = 0.0f;
		for(int i =0;i<analysisCalcStats[ratioIDX].length;++i) {
			p.setColorValFill(p.gui_LightRed+i, 255);
			rCompHeight = height * analysisCalcStats[ratioIDX][i];
			p.rect(0.0f, rYSt, width, rCompHeight);
			rYSt+=rCompHeight;
		}
		if (selected) {
			p.setColorValFill(p.gui_White, 100);
			p.rect(-1.0f, -1.0f, width+2, height+2);
		} 

		p.translate(0.0f, height+txtYOff, 0.0f);
		p.translate(0.0f, dispIDXAra[eqDispType]*txtYOff, 0.0f);
		p.showOffsetText2D(0.0f, p.gui_White, ""+eq.jp);
		p.popStyle();p.popMatrix();	
	}//drawFtrVec
	
	//draw vertical bar describing per-comp values with
	private void drawDetailFtrVec(my_procApplet p, float height, float width, float[] vals, float denom, String valTtl, String[] dispStrAra, String[] valDesc) {
		p.pushMatrix();p.pushStyle();
			p.translate(0.0f, txtYOff, 0.0f);
			p.showOffsetText2D(0.0f, p.gui_White, valTtl);
			p.translate(0.0f, txtYOff, 0.0f);
			p.pushMatrix();p.pushStyle();
				float rCompHeight, rYSt = 0.0f, htMult = height/denom;
				for(int i =0;i<vals.length;++i) {
					if (vals[i] > 0.0f) {
						p.setColorValFill(p.gui_LightRed+i, 255);
						rCompHeight = htMult * vals[i];
						p.rect(0.0f, rYSt, width, rCompHeight);
						rYSt+=rCompHeight;
					}
				}
				rCompHeight = 0.0f;
				rYSt = 0.0f;
				//make sure text for small boxes isn't overwritten by next box
				for(int i =0;i<vals.length;++i) {
					if (vals[i] > 0.0f) {
						rCompHeight = htMult * vals[i];
						p.pushMatrix();p.pushStyle();
						p.translate(10.0f, rYSt+(rCompHeight/2.0f)+5, 0.0f);
						if(null!= dispStrAra[i]) {			p.showOffsetText2D(0.0f, p.gui_Black, dispStrAra[i]);}
						p.popStyle();p.popMatrix();
						rYSt+=rCompHeight;
					}
				}		
			p.popStyle();p.popMatrix();				
			p.translate(0.0f, height+txtYOff, 0.0f);
			if(valDesc != null) {
				for(String s : valDesc) {
					p.showOffsetText2D(0.0f, p.gui_White, s);
					p.translate(0.0f, txtYOff, 0.0f);
				}
			}
			//move down and print out relevant text
		p.popStyle();p.popMatrix();		
	}//drawSpecificFtrVecWithText

	//draw a single ftr vector as a wide bar; include text for descriptions
	//width is per bar
	public void drawIndivFtrVec(my_procApplet p, float height, float width){
		p.pushMatrix();p.pushStyle();
		//title here?
		p.showOffsetText2D(0.0f, p.gui_White, "Calc Values for ftr idx : " +jpIDXara[0] +"," +jpIDXara[1]+ " jp "+eq.jp + " : " + eq.jpName);//p.drawText("Calc Values for ftr idx : " +eq.jpIdx + " jp "+eq.jp, 0, 0, 0, p.gui_White);
		p.translate(0.0f, txtYOff, 0.0f);
		for(int i=0;i<analysisCalcStats.length;++i) {
			drawDetailFtrVec(p,height,width, analysisCalcStats[i], ttlCalcStats_Vis[i], calcStatTitles[i], analysisCalcValStrs[i], calcStatDispDetail[i]);
			p.translate(width*1.5f, 0.0f, 0.0f);
		}	
		drawDetailFtrVec(p,height,width, legendSizes, 1.0f, "Legend", legendDatStrAra, new String[] {});
		//drawLegend(p,height,width, legendSizes, 1.0f);
		p.popStyle();p.popMatrix();		
	}//drawIndivFtrVec

	//return basic stats for this calc in tight object
	public ArrayList<String> getCalcRes() {return analysisRes;	}//calcRes
	
	
}//calcAnalysis