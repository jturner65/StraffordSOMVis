package SOM_Strafford_PKG;

import java.util.*;
import java.util.Map.Entry;

/**
 * This class is intended to hold an object capable of calculation
 * the appropriate weighting of the various component terms that make up 
 * the weight vector used to train/query the SOM.  Allow this calculation to be built
 * from values in format file
 * @author john
 */
public class StraffWeightCalc {
	public static final String fileComment = "#";
	public String fileName;
	public SOMMapManager mapMgr;
	public final Date now;
	//arrays are for idxs of various eq components (mult, offset, decay) in the format file for 
	//each component contributor (prospect, order, opt, link), in order.  0-idx is jp name or "default"
	private static final int[] mIdx = new int[] {1, 4, 7, 10},
			   					oIdx = new int[] {2, 5, 8, 11},
			   					dIdx = new int[] {3, 6, 9, 12};
	//map of per-jp equations to calculate feature vector from prospect, order, 
	//and opt data (expandable to more sources eventually if necessary).  keyed by jp
	private TreeMap<Integer, JPWeightEquation> eqs;	
	//hold relevant quantities for each jp calculation across all data
	private Float[][] bndsAra;

	//set initial values to properly initialize bnds ara
	private static final float[] initBnd = new float[] {1000000000.0f,-1000000000.0f, 0.0f, 0.0f, 0.0f};//min, max, count, diff
	//meaning of each idx in bndsAra 1st dimension 
	private static int 
			minBndIDX = 0,					//mins for each feature
			maxBndIDX = 1,					//maxs for each feature
			countBndIDX = 2,				//count of entries for each feature
			diffBndIDX = 3; 				//max-min for each feature
	private static int numBnds = 4;	
	
	public MonitorJpJpgrp jpJpgMon;
	
	private int[] stFlags;						//state flags - bits in array holding relevant process/state info
	public static final int
			debugIDX 					= 0,
			ftrCalcCompleteIDX			= 1,	//ftr weight calc has been completed for all loaded examples.
			calcAnalysisCompleteIDX		= 2;	//analysis of calc results completed for this object
	public static final int numFlags = 3;	
	
	public StraffWeightCalc(SOMMapManager _mapMgr, String _fileName, MonitorJpJpgrp _jpJpgMon) {
		mapMgr = _mapMgr;
		Calendar nowCal = mapMgr.getInstancedNow();
		now = nowCal.getTime();
		jpJpgMon = _jpJpgMon;
		initFlags();
		loadConfigAndSetVars( _fileName);
	}//ctor
	
	private void loadConfigAndSetVars(String _fileName) {
		fileName = _fileName;
		String[] configDatList = mapMgr.loadFileIntoStringAra(fileName, "Weight Calc File Loaded", "Weight Calc File Not Loaded Due To Error");
		eqs = new TreeMap<Integer, JPWeightEquation> ();
		//initialize bnds
		initBnds();
		//first NonComment record in configDat should be default weights
		boolean foundDflt = false;
		int idx = 0;
		String[] strVals = new String[0];
		while (!foundDflt) {
			if (configDatList[idx].contains(fileComment)) {++idx;			}
			else {
				strVals = configDatList[idx].trim().split(",");
				if (strVals[0].toLowerCase().contains("default")) {
					foundDflt = true;
				} else {mapMgr.dispMessage("StraffWeightCalc","loadConfigAndSetVars", "Error! First non comment record in config file "+ fileName + " is not default weight map.  Exiting calc unbuilt.");return;}
			}		
		}//while		
		//string record has jp in col 0, 3 mult values 1-3, 3 offset values 4-6 and 3 decay values 7-9.
		Float[] dfltM = getFAraFromStrAra(strVals,mIdx), dfltO = getFAraFromStrAra(strVals,oIdx), dfltD = getFAraFromStrAra(strVals,dIdx);
		//strVals holds default map configuration - config all weight calcs to match this
		int numFtrs = jpJpgMon.getNumFtrs();
		for (int i=0;i<numFtrs;++i) {
			Integer jp = jpJpgMon.getJpByIdx(i);
			JPWeightEquation eq = new JPWeightEquation(this,jpJpgMon.getJPNameFromJP(jp),jp, i, dfltM, dfltO, dfltD, true);
			eqs.put(jp, eq);
		}//
		//now go through every line and build eqs for specified jps
		for (int i=idx+1; i<configDatList.length; ++i) {		
			if (configDatList[i].contains(fileComment)) {continue;			}
			addIndivJpEq(configDatList[i].trim().split(","));		
		}
	}//loadConfigAndSetVars	

	public Float[] getMinBndsAra() {return bndsAra[0];}
	public Float[] getDiffsBndsAra() {return bndsAra[3];}
	
	private Float[] fastCopyAra(int len, float val) {
		Float[] res = new Float[len];
		res[0]=val;	
		for (int i = 1; i < len; i += i) {System.arraycopy(res, 0, res, i, ((len - i) < i) ? (len - i) : i);}
		return res;
	}//fastCopyAra
	  
	//
	private void initBnds() {//reinit bounds map, key of map is jp, array holds min (idx 0) and max (idx 1) of values seen in calculations		
		bndsAra = new Float[numBnds][];		
		int numFtrs = jpJpgMon.getNumFtrs();
		for (int i=0;i<bndsAra.length;++i) {
			bndsAra[i]=fastCopyAra(numFtrs, initBnd[i]);
		}		
	}//initBnds() 
	
	//check if value is in bnds array for particular jp, otherwise modify bnd
	private void checkValInBnds(Integer jpidx, Integer destIDX, float val) {
		if (val < bndsAra[minBndIDX][destIDX]) {bndsAra[minBndIDX][destIDX]=val;bndsAra[diffBndIDX][destIDX] = bndsAra[maxBndIDX][destIDX]-bndsAra[minBndIDX][destIDX];}
		if (val > bndsAra[maxBndIDX][destIDX]) {bndsAra[maxBndIDX][destIDX]=val;bndsAra[diffBndIDX][destIDX] = bndsAra[maxBndIDX][destIDX]-bndsAra[minBndIDX][destIDX];}
	}
	
	//increment count of training examples with jp data represented by destIDX, and total calc value seen
	public void incrBnds(Integer destIDX) {
		bndsAra[countBndIDX][destIDX] +=1;
	}
	//public TreeMap<Integer, float[]> getBndsMap(){return bnds;}
	public Float[][] getBndsAra(){return bndsAra;}
	
	private void addIndivJpEq(String[] strVals) {		
		Integer jp = Integer.parseInt(strVals[0]);
		Float[] jpM = getFAraFromStrAra(strVals,mIdx), jpO = getFAraFromStrAra(strVals,oIdx), jpD = getFAraFromStrAra(strVals,dIdx);
		Integer idx = jpJpgMon.getJpToFtrIDX(jp);
		JPWeightEquation eq = new JPWeightEquation(this, jpJpgMon.getJPNameFromJP(jp),jp, idx, jpM, jpO, jpD, false);
		eqs.put(jp, eq);
	}//addIndivJpEq
	//read in string array of weight values, convert and put in float array
	private Float[] getFAraFromStrAra(String[] sAra, int[] idxs) {
		ArrayList<Float> res = new ArrayList<Float>();
		for(int i : idxs) {			res.add(Float.parseFloat(sAra[i]));		}		
		return res.toArray(new Float[0]);
	}

	//calculate feature vector for this example
	public TreeMap<Integer, Float> calcFeatureVector(ProspectExample ex, HashSet<Integer> jps, TreeMap<Integer, jpOccurrenceData> orderOccs,TreeMap<Integer, jpOccurrenceData> linkOccs, TreeMap<Integer, jpOccurrenceData> optOccs) {
		TreeMap<Integer, Float> res = new TreeMap<Integer, Float>();
		float ftrVecSqMag = 0.0f;
		jpOccurrenceData optOcc;
		//for each jp present in ex, perform calculation
		Integer destIDX;
		boolean isZeroMagExample = true;		//if all values are 0 then this is a bad training example, we want to ignore it
		for (Integer jp : jps) {
			//find destIDX
			destIDX = jpJpgMon.getJpToFtrIDX(jp);
			if (destIDX==null) {continue;}//ignore unknown/unmapped jps
			optOcc =  optOccs.get(jp);
			if ((optOcc != null )&& (ex.getOptAllOccObj() != null)) {	mapMgr.dispMessage("StraffWeightCalc","calcFeatureVector","Multiple opt refs for prospect : " + ex.OID + " | This should not happen - opt events will be overly-weighted.");	}
			float val = eqs.get(jp).calcVal(ex,orderOccs.get(jp),linkOccs.get(jp),optOcc);
			if ((isZeroMagExample) && (val != 0)) {isZeroMagExample = false;}
			res.put(destIDX,val);
			ex.setMapOfSrcWts(destIDX, val, SOMExample.ftrMapTypeKey);
			ftrVecSqMag += (val*val);
			checkValInBnds(jp,destIDX, val);
		}		
		ex.ftrVecMag = (float) Math.sqrt(ftrVecSqMag);
		ex.setIsBadExample(isZeroMagExample);
		return res;
	}//calcFeatureVector
	
	//////////////////////////////////////////////////
	// reporting functions
	
	//this will reset all analysis components of feature vectors.  this is so that new feature calculations won't aggregate stats with old ones
	public void resetAllCalcObjs() {
		for ( JPWeightEquation eq : eqs.values()	) {	eq.resetAnalysis();		}
		setFlag(ftrCalcCompleteIDX, false);
	}//resetAllCalcObjs
	
	//called when all current prospect examples have been calculated
	public void finishFtrCalcs() {	setFlag(ftrCalcCompleteIDX, true); 	}
	public boolean isFinishedFtrCalcs() {return getFlag(ftrCalcCompleteIDX);}
	public boolean calcAnalysisShouldBeDone() {return getFlag(ftrCalcCompleteIDX) && ! getFlag(calcAnalysisCompleteIDX);}//only perform analysis once on same set of collected ftr calc data
	public boolean calcAnalysisIsReady() {return getFlag(ftrCalcCompleteIDX) && getFlag(calcAnalysisCompleteIDX);}		//ftr calc is done, and calc anaylsis has been done on these feature calcs
	//after all features are calculated, run this first to finalize reporting statistics on eq performance
	public void finalizeCalcAnalysis() {
		if (calcAnalysisShouldBeDone()) {
			//for(JPWeightEquation jpEq:eqs.values()) {jpEq.calcStats.aggregateCalcVals(jpJpgMon.getJpToFtrIDX(jpEq.jp));}
			for(JPWeightEquation jpEq:eqs.values()) {jpEq.calcStats.aggregateCalcVals();}
			setFlag(calcAnalysisCompleteIDX, true);
		}
	}
	//retrieve a list of all eq performance data per ftr
	public ArrayList<String> getCalcAnalysisRes(){
		ArrayList<String> res = new ArrayList<String>();
		for(JPWeightEquation jpEq:eqs.values()) {	res.addAll(jpEq.calcStats.getCalcRes());}
		return res;
	}//getCalcAnalysisRes
	
	//draw res of all calcs as single rectangle of height ht and width barWidth*num eqs
	public void drawAllCalcRes(SOM_StraffordMain p, float ht, float barWidth, int curJPIdx) {		
		p.pushMatrix();p.pushStyle();		
		for(JPWeightEquation jpEq:eqs.values()) {	
			//draw bar
			jpEq.drawFtrVec(p, ht, barWidth, jpEq.jpIdx==curJPIdx);
			//move over for next bar
			p.translate(barWidth, 0.0f, 0.0f);
		}
		p.popStyle();p.popMatrix();	
	}//draw analysis res for each graphically
	
	//draw single detailed feature eq detailed analysis
	public void drawSingleFtr(SOM_StraffordMain p, float ht, float width, Integer jp) {
		p.pushMatrix();p.pushStyle();		
		//draw detailed analysis
		eqs.get(jp).drawIndivFtrVec(p, ht, width);
		p.popStyle();p.popMatrix();			
	}//drawSingleFtr
	
	
	private void initFlags(){stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlag(i,false);}}
	public void setAllFlags(int[] idxs, boolean val) {for (int idx : idxs) {setFlag(idx, val);}}
	public void setFlag(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case debugIDX : {break;}	
			case ftrCalcCompleteIDX 		: {							//whether or not this object has finished calculating ftrs for all loaded examples
				if(!val) {setFlag(calcAnalysisCompleteIDX, false);}		//setting this to false means have yet to calculate ftrs, so all calc analysis data should be ignored
				break;}					
			case calcAnalysisCompleteIDX	: { 					//whether or not this object has finished the analysis aggregation of all eqs on ftrs calced				
				break;}			
		}
	}//setFlag		
	public boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		

	
	//display calc equations for each JP id
	@Override
	public String toString() {
		String res  = "";
		for (JPWeightEquation eq : eqs.values()) {
			Integer numSeen = jpJpgMon.getCountJPSeen(eq.jp);
			res+= eq.toString()+"  |# Calcs done : " + String.format("%6d", (Math.round(bndsAra[2][eq.jpIdx]))) + " ==  # of Occs : " +String.format("%6d", numSeen) + "\t| Min val : " +String.format("%6.4f", bndsAra[0][eq.jpIdx]) + "\t| Max val : " +String.format("%6.4f", bndsAra[1][eq.jpIdx]) + "\n";
		}
		res += "# eqs : " + eqs.size() + "\t|Build from file : " + fileName+ "\t| Equation Configuration : \n";
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

//this class will hold the weight calculation coefficients for a single jp
//this will incorporate the following quantities : 
//presence or absence in prospect record
//count and datetime of occurence in order record
//count, datetime and opt value of each occurence in opt record. 
//this object only works on prospect examples.  
//No other example should use this calculation object, or analysis statistics will be skewed
class JPWeightEquation {
	public final StraffWeightCalc calcObj;
	public static Date now;
	public static final Date oldDate = new GregorianCalendar(2009, Calendar.JANUARY, 1).getTime();
	public final int jp, jpIdx;				//corresponding jp, jp index in weight vector
	public final String jpName;
	
	//sentinel value for opt calc meaning force total res for this calc to be 0 based on negative per-jp opt from user
	private static final float optOutSntnlVal = -9999.0f;
	
	//mults and offsets are of the form (mult * x + offset), where x is output of membership function
	public static int 						//idxs in eq coefficient arrays
			prspctCoeffIDX = 0, 
			orderCoeffIDX = 1, 
			optCoeffIDX = 2,
			linkCoeffIDX = 3;
	public static int numEqs = 4;
	//these names must match order and number of component idxs above
	public static String[] calcNames = new String[] {"Prospect","Order","Opt","Link"};
	
	private final Float[] Mult;						//multiplier for membership functions
	private final Float[] Offset;					//offsets for membership functions	
	//membership functions hold sum( x / (1 + decay*delT)) for each x, where x is # of occurences on a 
	//particular date and decay is some multiplier based on elapsed time delT (in days)
	private final Float[] Decay;
	//analysis function for this eq component
	public calcAnalysis calcStats;	
	//if this equation is using default values for coefficients
	public boolean isDefault;

	public JPWeightEquation(StraffWeightCalc _calcObj, String _name, int _jp, int _jpIdx, Float[] _m, Float[] _o, Float[] _d, boolean _isDefault) {
		calcObj = _calcObj; now = calcObj.now;jp=_jp;jpIdx=_jpIdx; jpName=_name;
		Mult = new Float[numEqs];
		Offset = new Float[numEqs];
		Decay = new Float[numEqs];
		System.arraycopy(_m, 0, Mult, 0, numEqs);
		System.arraycopy(_o, 0, Offset, 0, numEqs);
		System.arraycopy(_d, 0, Decay, 0, numEqs);	
		isDefault = _isDefault;
		calcStats = new calcAnalysis(this);		
	}//ctor
	
	private float decayCalc(int idx, int num, Date date) {
		if (Decay[idx] == 0) {return 1.0f*num;}
		if (date == null) {date = oldDate;} //if no date then consider date is very old
		float decayAmt = 0.0f;
		if (now.after(date)) {//now is more recent than the date of the record
			//86.4 mil millis in a day	
			decayAmt = Decay[idx] * (now.getTime() - date.getTime())/86400000.0f;
		}
		return (num/(1.0f + decayAmt));
	}//decayCalc
	
	private float scaleCalc(int idx, int num, Date date) {	
		float val = decayCalc(idx, num, date);		
		return  (Mult[idx] * val) + Offset[idx];
	}
	//get total weight contribution for all events of this jp, based on their date
	private float aggregateOccs(jpOccurrenceData jpOcc, int idx) {
		float res = 0.0f;
		Date[] dates = jpOcc.getDatesInOrder();
		for (Date date : dates) {
			Tuple<Integer, Integer> occTup = jpOcc.getOccurrences(date);
			int numOcc = occTup.x;
			res += scaleCalc(idx,numOcc, date);
		}
		return res;
	}//aggregateOccs
	
	//calculate opt data's contribution to feature vector
	private float calcOptRes(jpOccurrenceData jpOcc) {
		float res = 0;
		//multiple opt occurences of same jp should have no bearing - want most recent opt event - may want to research multiple opt events for same jp, if opt values differ
		//if opt val is -2 for all jps, there might be something we want to learn from this prospect even though they don't want to get emails;  we won't see those people's opt here.
		//On the other hand, if opt is -2 for some jps and not others, this would infer that something about this particular JP may not be attractive to this person, 
		//so other prospects that look like them may not want to see these jps either, so we learn from that - we set this ftr value to be 0 for an opt < 0 , 
		//regardless of what other behavior they have for this jp
		//ignores opts of 0.  TODO need to determine appropriate behavior for opts of 0
		Entry<Date, Tuple<Integer,Integer>> vals = jpOcc.getLastOccurrence();
		//Date mostRecentDate = vals.getKey();
		Integer optChoice = vals.getValue().y;
		if (optChoice < 0) {res = optOutSntnlVal;}
		else if (optChoice > 0) {	res =  (Mult[optCoeffIDX] * optChoice/(1+Decay[optCoeffIDX])) + Offset[optCoeffIDX];  	}
		return res;
	}//calcOptRes
	
	//reset analysis object to clear out all stats from previous run
	public void resetAnalysis() {calcStats.reset();	}
	
	//calculate a particular example's weight value for this object's jp
	public float calcVal(ProspectExample ex, jpOccurrenceData orderJpOccurrences, jpOccurrenceData linkJpOccurrences, jpOccurrenceData optJpOccurrences) {	
		boolean hasData = false;
			//this means jp was used set in prospect record : scale propsect contribution by update date of record - assumes accurate at time of update
		if (ex.prs_JP == jp) {		hasData = true;				calcStats.setWSVal(prspctCoeffIDX, scaleCalc(prspctCoeffIDX, 1, ex.prs_LUDate));		}//calcStats.workSpace[prspctCoeffIDX] = scaleCalc(prspctCoeffIDX, 1, ex.prs_LUDate);		}
			//handle order occurrences for this jp.   aggregate every order occurrence, with decay on importance based on date
		if (orderJpOccurrences != null) {	hasData = true;		calcStats.setWSVal(orderCoeffIDX, aggregateOccs(orderJpOccurrences, orderCoeffIDX));}//calcStats.workSpace[orderCoeffIDX] = aggregateOccs(orderJpOccurrences, orderCoeffIDX);}
			//for links use same mechanism as orders - handle differences through weightings - aggregate every order occurrence, with decay on importance based on date
		if (linkJpOccurrences != null) {	hasData = true;		calcStats.setWSVal(linkCoeffIDX, aggregateOccs(linkJpOccurrences, linkCoeffIDX));	}//calcStats.workSpace[linkCoeffIDX] = aggregateOccs(linkJpOccurrences, linkCoeffIDX);	}
			//user opts - these are handled differently - calcOptRes return of -9999 means negative opt specified for this jp alone (ignores negative opts across all jps) - should force total from eq for this jp to be ==0
		if (optJpOccurrences != null) {		hasData = true;		calcStats.setWSVal(optCoeffIDX, calcOptRes(optJpOccurrences));}	//calcStats.workSpace[optCoeffIDX] = calcOptRes(optJpOccurrences);}	
		if (hasData) {calcObj.incrBnds(jpIdx);		}
		float res = calcStats.getValFromCalcs(optCoeffIDX, optOutSntnlVal);//(calcStats.workSpace[optCoeffIDX]==optOutSntnlVal);
		return res;
	}//calcVal
	
	public void drawIndivFtrVec(SOM_StraffordMain p, float height, float width) {calcStats.drawIndivFtrVec(p, height, width);	}
	public void drawFtrVec(SOM_StraffordMain p, float height, float width, boolean selected){calcStats.drawFtrVec(p, height, width,selected);}
	
	//string rep of this calc
	public String toString() {
		String ftrBuffer = (jpIdx >=100) ? "" : (jpIdx >=10) ? " " : "  ";//to align output
		String res = "JP : "+ String.format("%3d", jp) +" Ftr[" + jpIdx + "]" + ftrBuffer + " = ";
		//base prospect
		res += "(("+String.format("%.3f", Mult[prspctCoeffIDX]) + "/(1 + "+String.format("%.4f", Decay[prspctCoeffIDX])+"*DLU)) + "+String.format("%.4f", Offset[prspctCoeffIDX])+")"; 
		//order
		res += " + ("+ String.format("%.3f", Mult[orderCoeffIDX]) + "*[sum(NumOcc[i]/(1 + "+String.format("%.4f", Decay[orderCoeffIDX])+" * DEV)) for each event i] + "+String.format("%.4f", Offset[orderCoeffIDX])+")"; 		
		//link
		res += " + ("+ String.format("%.3f", Mult[linkCoeffIDX]) + "*[sum(NumOcc[i]/(1 + "+String.format("%.4f", Decay[linkCoeffIDX])+" * DEV)) for each event i] + "+String.format("%.4f", Offset[linkCoeffIDX])+")"; 		
		//opt
		res +=  " + ("+ String.format("%.3f", Mult[optCoeffIDX]) + "*OptV + "+String.format("%.4f", Offset[optCoeffIDX])+")"; 			
		return res;
	}//toString
		
	
}//JPWeightEquation

//this class will hold analysis information for calculations to more clearly understand the results of the current calc object
class calcAnalysis{
	//per JPWeightEquation analysis of data
		//corresponding eq - get all pertinent info from this object
	private JPWeightEquation eq;	
		//totals seen across all examples per individual calc components(prospect, opt, order, link, etc);sum of sq value, for variance/std calc
	private float[] vals, valSq;
	
	//calculated statistics 
	private float[][] analysisCalcStats;
	//calculated totals of statistic values, to get appropriate ratios
	private float[] ttlCalcStats_Vis;
	private static final int
		ratioIDX = 0,
		meanIDX = 1,
		meanOptIDX = 2,
		stdIDX = 3,
		stdOptIDX = 4;
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
	//disp idx of this calc
	private final int dispIDX;
	public calcAnalysis(JPWeightEquation _eq) {eq=_eq;reset();dispIDX = eq.jpIdx%5;legendSizes=new float[eq.numEqs];for(int i=0;i<eq.numEqs;++i) {legendSizes[i]= 1.0f/eq.numEqs;}}
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
	
	public void setWSVal(int idx, float val) {
		workSpace[idx]=val;		
		if (val != 0.0f) {eqTypeCount[idx]++;}		
	}
	
		//add values for a particular calc run - returns calc total
	public float getValFromCalcs(int optCoeffIDX, float optOutValCk) {
		boolean optOut = workSpace[optCoeffIDX]==optOutValCk;
		++numExamplesWithJP;
		if (optOut) {return 0.0f;	}//opt result means all values are cleared out (user opted out of this specific JP) so only increment numExamplesWithJP
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
			for (int j=0;j<analysisCalcStats[j].length;++j) {		
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
		
		analysisRes.add("FTR : "+String.format("%03d", eq.jpIdx)+"|JP : "+String.format("%03d", eq.jp)+"|% opt:"+String.format("%.5f",ratioOptOut)
					+"|MU : " + String.format("%.5f",ttlMeanNoOpt)+"|Std : " + String.format("%.5f",ttlStdNoOpt) 
					+"|MU w/opt : " +String.format("%.5f",ttlMeanWithOpt)+"|Std w/opt : " +String.format("%.5f",ttlStdWithOpt));
		analysisRes.add(perCompMuStd);
	}//aggregateCalcVals
		
	//this will display a vertical bar corresponding to the performance of the analyzed calculation.
	//each component of calc object will have a different color
	//height - the height of the bar.  start each vertical bar at upper left corner, put text beneath bar
	public void drawFtrVec(SOM_StraffordMain p, float height, float width, boolean selected){
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
		p.translate(0.0f, dispIDX*txtYOff, 0.0f);
		p.showOffsetText2D(0.0f, p.gui_White, ""+eq.jp);
		p.popStyle();p.popMatrix();	
	}//drawFtrVec
	
	//draw vertical bar describing per-comp values with
	private void drawDetailFtrVec(SOM_StraffordMain p, float height, float width, float[] vals, float denom, String valTtl, String[] dispStrAra, String[] valDesc) {
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
						p.showOffsetText2D(0.0f, p.gui_Black, dispStrAra[i]);
						p.popStyle();p.popMatrix();
						rYSt+=rCompHeight;
					}
				}		
			p.popStyle();p.popMatrix();				
			p.translate(0.0f, height+txtYOff, 0.0f);
			for(String s : valDesc) {
				p.showOffsetText2D(0.0f, p.gui_White, s);
				p.translate(0.0f, txtYOff, 0.0f);
			}
			//move down and print out relevant text
		p.popStyle();p.popMatrix();		
	}//drawSpecificFtrVecWithText

	//draw a single ftr vector as a wide bar; include text for descriptions
	//width is per bar
	public void drawIndivFtrVec(SOM_StraffordMain p, float height, float width){
		p.pushMatrix();p.pushStyle();
		//title here?
		p.showOffsetText2D(0.0f, p.gui_White, "Calc Values for ftr idx : " +eq.jpIdx + " jp "+eq.jp + " : " + eq.jpName);//p.drawText("Calc Values for ftr idx : " +eq.jpIdx + " jp "+eq.jp, 0, 0, 0, p.gui_White);
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