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
	public SOMMapManager map;
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

	
	public StraffWeightCalc(SOMMapManager _map, String _fileName, MonitorJpJpgrp _jpJpgMon) {
		map = _map;
		Calendar nowCal = map.getInstancedNow();
		now = nowCal.getTime();
		jpJpgMon = _jpJpgMon;
		loadConfigAndSetVars( _fileName);
	}//ctor
	
	private void loadConfigAndSetVars(String _fileName) {
		fileName = _fileName;
		String[] configDatList = map.loadFileIntoStringAra(fileName, "Weight Calc File Loaded", "Weight Calc File Not Loaded Due To Error");
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
				} else {System.out.println("StraffWeightCalc::loadConfigAndSetVars : Error! First non comment record in config file "+ fileName + " is not default weight map.  Exiting calc unbuilt.");return;}
			}		
		}//while		
		//string record has jp in col 0, 3 mult values 1-3, 3 offset values 4-6 and 3 decay values 7-9.
		Float[] dfltM = getFAraFromStrAra(strVals,mIdx), dfltO = getFAraFromStrAra(strVals,oIdx), dfltD = getFAraFromStrAra(strVals,dIdx);
		//strVals holds default map configuration - config all weight calcs to match this
		int numFtrs = jpJpgMon.getNumFtrs();
		for (int i=0;i<numFtrs;++i) {
			Integer jp = jpJpgMon.getJpByIdx(i);
			JPWeightEquation eq = new JPWeightEquation(this,jp, i, dfltM, dfltO, dfltD, true);
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
		JPWeightEquation eq = new JPWeightEquation(this,jp, idx, jpM, jpO, jpD, false);
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
			if ((optOcc != null )&& (ex.getOptAllOccObj() != null)) {	map.dispMessage("StraffWeightCalc","calcFeatureVector","Multiple opt refs for prospect : " + ex.OID + " | This should not happen - opt events will be overly-weighted.");	}
			float val = eqs.get(jp).calcVal(ex,orderOccs.get(jp),linkOccs.get(jp),optOcc);
			if ((isZeroMagExample) && (val != 0)) {isZeroMagExample = false;}
			res.put(destIDX,val);
			ex.setMapOfJpWts(jp, val, StraffSOMExample.ftrMapTypeKey);
			ftrVecSqMag += (val*val);
			checkValInBnds(jp,destIDX, val);
		}		
		ex.ftrVecMag = (float) Math.sqrt(ftrVecSqMag);
		ex.setIsBadExample(isZeroMagExample);
		return res;
	}//calcFeatureVector
	
	//after all features are calculated, run this to finalize reporting statistics on eq performance
	public void finalizeCalcAnalysis() {for(JPWeightEquation jpEq:eqs.values()) {jpEq.calcStats.aggregateCalcVals(jpJpgMon.getJpToFtrIDX(jpEq.jp));}}
	
	//retrieve a list of all eq data per ftr
	public ArrayList<String> getCalcAnalysisRes(){
		ArrayList<String> res = new ArrayList<String>();
		for(JPWeightEquation jpEq:eqs.values()) {			
			res.addAll(jpEq.calcStats.getCalcRes());
		}
		return res;
	}//getCalcAnalysisRes
	
	//draw res of all calcs as single rectangle of height ht and width barWidth*num eqs
	public void drawAllCalcRes(SOM_StraffordMain p, int ht, int barWidth) {		
		p.pushMatrix();p.pushStyle();		
		for(JPWeightEquation jpEq:eqs.values()) {	
			//draw bar
			jpEq.calcStats.drawFtrVec(p, ht, barWidth);
			//move over for next bar
			p.translate(barWidth, 0.0f, 0.0f);
		}
		p.popStyle();p.popMatrix();	
	}//draw analysis res for each graphically
	
	
	
	
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

	public JPWeightEquation(StraffWeightCalc _calcObj, int _jp, int _jpIdx, Float[] _m, Float[] _o, Float[] _d, boolean _isDefault) {
		calcObj = _calcObj; now = calcObj.now;jp=_jp;jpIdx=_jpIdx;
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
	
	//calculate a particular example's weight value for this object's jp
	public float calcVal(ProspectExample ex, jpOccurrenceData orderJpOccurrences, jpOccurrenceData linkJpOccurrences, jpOccurrenceData optJpOccurrences) {	
		boolean hasData = false;
		//float [] vals = new float[numEqs];			//all individual values calculated - this is so we can easily aggregate analysis results to calc object
			//this jp was used set in prospect record : scale propsect contribution by update date of record - assumes accurate at time of update
		if (ex.prs_JP == jp) {					calcStats.workSpace[prspctCoeffIDX] = scaleCalc(prspctCoeffIDX, 1, ex.prs_LUDate);		hasData = true;}
			//handle order occurrences for this jp.   aggregate every order occurrence, with decay on importance based on date
		if (orderJpOccurrences != null) {		calcStats.workSpace[orderCoeffIDX] = aggregateOccs(orderJpOccurrences, orderCoeffIDX);	hasData = true;}
			//for links use same mechanism as orders - handle differences through weightings - aggregate every order occurrence, with decay on importance based on date
		if (linkJpOccurrences != null) {		calcStats.workSpace[linkCoeffIDX] = aggregateOccs(linkJpOccurrences, linkCoeffIDX);	hasData = true;}
			//user opts - these are handled differently - calcOptRes return of -9999 means negative opt specified for this jp alone (ignores negative opts across all jps) - should force total from eq for this jp to be ==0
		if (optJpOccurrences != null) {			calcStats.workSpace[optCoeffIDX] = calcOptRes(optJpOccurrences);						hasData = true;}	
		if (hasData) {calcObj.incrBnds(jpIdx);		}
		float res = calcStats.getValFromCalcs(calcStats.workSpace[optCoeffIDX]==optOutSntnlVal);
		return res;
	}//calcVal
	
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
		//%'s of total seen for each calc - each val divided by total val; mean and variance aggregates
	private float[] ratios, means, stdVals, meansOpts, stdValsOpts;
		//workspace used by calc object to hold values - all individual values calculated - this is so we can easily aggregate analysis results to calc object	
	public float[] workSpace;
		//total seen across all individual calcs, across all examples; mean value sent per non-opt, sqVal for std calc; stdVal of totals
	private float ttlVal, ttlSqVal, ttlMeansVec_vis, ttlStdsVec_vis, ttlMeansOptVec_vis, ttlStdsOptVec_vis;
		//analysis vals : mean and std including opt outs, and without counting them, fraction of opt outs in # total records
	private float ttlMeanWithOpt, ttlMeanNoOpt, ttlStdWithOpt, ttlStdNoOpt, ratioOptOut = 1.0f;	
		//number of eqs processed - increment on total
	private int numExamplesWithJP = 0;
	private int numExamplesNoOptOut = 0;

	//array of analysis components for string display
	private ArrayList<String> analysisRes;
	//how far to move down for text
	private static float txtYOff = 3.0f;
	
	public calcAnalysis(JPWeightEquation _eq) {eq=_eq;vals = new float[eq.numEqs];valSq = new float[vals.length];workSpace = new float[vals.length];}
		//add values for a particular calc run - returns calc total
	public float getValFromCalcs(boolean optOut) {
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
	public void aggregateCalcVals(int ftrIdx) {
		ratios = new float[vals.length];
		means = new float[vals.length];
		stdVals = new float[vals.length];
		meansOpts = new float[vals.length];
		stdValsOpts = new float[vals.length];
		ttlMeansVec_vis = 0;
		ttlStdsVec_vis = 0;
		ttlMeansOptVec_vis = 0;
		ttlStdsOptVec_vis = 0;
		analysisRes = new ArrayList<String>();
		ratioOptOut = 1.0f;	
		String perCompMuStd = "";
		if(ttlVal == 0.0f) {return;}
		for (int i=0;i<vals.length;++i) {
			ratios[i] = vals[i]/ttlVal;
			means[i] = vals[i]/numExamplesNoOptOut;
			ttlMeansVec_vis += means[i];
			meansOpts[i] = vals[i]/numExamplesWithJP;
			ttlMeansOptVec_vis +=meansOpts[i];
			stdVals[i] = (float) Math.sqrt((valSq[i]/numExamplesNoOptOut) - (means[i]*means[i]));
			ttlStdsVec_vis += stdVals[i];
			stdValsOpts[i] = (float) Math.sqrt((valSq[i]/numExamplesWithJP) - (meansOpts[i]*meansOpts[i]));
			ttlStdsOptVec_vis += stdValsOpts[i];
			
			perCompMuStd += "|Mu:"+String.format("%.5f",means[i])+"|Std:"+String.format("%.5f",stdVals[i]);
		}
		ratioOptOut = 1 - (1.0f*numExamplesNoOptOut)/numExamplesWithJP;			//ratio of all examples that have opted out with 0 ttl contribution to jp in ftr
		ttlMeanNoOpt = ttlVal/numExamplesNoOptOut;
		ttlMeanWithOpt = ttlVal/numExamplesWithJP;
		ttlStdNoOpt = (float) Math.sqrt((ttlSqVal/numExamplesNoOptOut)  - (ttlMeanNoOpt * ttlMeanNoOpt));
		ttlStdWithOpt = (float) Math.sqrt((ttlSqVal/numExamplesWithJP)  - (ttlMeanWithOpt * ttlMeanWithOpt));
		
		analysisRes.add("FTR : "+String.format("%03d", ftrIdx)+"|JP : "+String.format("%03d", eq.jp)+"|% opt:"+String.format("%.5f",ratioOptOut)
					+"|MU : " + String.format("%.5f",ttlMeanNoOpt)+"|Std : " + String.format("%.5f",ttlStdNoOpt) 
					+"|MU w/opt : " +String.format("%.5f",ttlMeanWithOpt)+"|Std w/opt : " +String.format("%.5f",ttlStdWithOpt));
		analysisRes.add(perCompMuStd);
	}//aggregateCalcVals
		
	//this will display a vertical bar corresponding to the performance of the analyzed calculation.
	//each component of calc object will have a different color
	//height - the height of the bar.  start each vertical bar at upper left corner, put text beneath bar
	public void drawFtrVec(SOM_StraffordMain p, int height, int width){
		drawSpecifcFtrVec(p,height,width, ratios, 1.0f);	
	}//drawFtrVec
	
	//this will display a vertical bar corresponding to the performance of the analyzed calculation.
	//each component of calc object will have a different color
	//height - the height of the bar.  start each vertical bar at upper left corner, put text beneath bar
	private void drawSpecifcFtrVec(SOM_StraffordMain p, int height, int width, float[] vals, float denom){
		p.pushMatrix();p.pushStyle();
		float rCompHeight, rYSt = 0.0f, htMult = height/denom;
		for(int i =0;i<ratios.length;++i) {
			p.setColorValFill(p.gui_Yellow+i, 255);
			rCompHeight = htMult * vals[i];
			p.rect(0.0f, rYSt, width, rCompHeight);
			rYSt+=rCompHeight;
		}
		//rect is corner mode
		p.popStyle();p.popMatrix();		
	}//drawFtrVec
	
	//draw vertical bar describing per-comp values with
	private void drawSpecificFtrVecWithText(SOM_StraffordMain p, int height, int width, float[] vals, float denom, String valTtl, String[] valDesc ) {
		p.pushMatrix();p.pushStyle();
		p.drawText(valTtl, 0, 0, 0, p.gui_White);
		p.translate(0.0f, txtYOff, 0.0f);
		drawSpecifcFtrVec(p,height,width, vals, denom);
		p.translate(0.0f, txtYOff, 0.0f);
		for(String s : valDesc) {
			p.drawText(s, 0, 0, 0, p.gui_White);
			p.translate(0.0f, txtYOff, 0.0f);
		}
		//move down and print out relevant text
		p.popStyle();p.popMatrix();		
	}//drawSpecificFtrVecWithText
	
	//draw a single ftr vector as a wide bar; include text for descriptions
	//width is per bar
	public void drawIndivFtrVec(SOM_StraffordMain p, int ftrIDX, int height, int width){
		p.pushMatrix();p.pushStyle();
		//title here?
		p.drawText("Calc Values for ftr idx : " +ftrIDX + " jp "+eq.jp, 0, 0, 0, p.gui_White);
		p.translate(0.0f, txtYOff, 0.0f);
		drawSpecificFtrVecWithText(p,height,width, ratios, 1.0f, "Ratios", new String[] {String.format("Ratio of Opts To Ttl : %.5f",ratioOptOut)});
		p.translate(width*1.5f, 0.0f, 0.0f);
		drawSpecificFtrVecWithText(p,height,width, means, ttlMeansVec_vis, "Means", new String[] {String.format("Mean : %.5f",ttlMeanNoOpt)});
		p.translate(width*1.5f, 0.0f, 0.0f);
		drawSpecificFtrVecWithText(p,height,width, meansOpts, ttlMeansOptVec_vis,"Means w/Opts", new String[] {String.format("Mean w/opts : %.5f",ttlMeanWithOpt)});
		p.translate(width*1.5f, 0.0f, 0.0f);
		drawSpecificFtrVecWithText(p,height,width, stdVals, ttlStdsVec_vis,"Stds", new String[] {String.format("Stds : %.5f",ttlStdNoOpt)});
		p.translate(width*1.5f, 0.0f, 0.0f);
		drawSpecificFtrVecWithText(p,height,width, stdValsOpts, ttlStdsOptVec_vis,"Stds w/Opts", new String[] {String.format("Mean w/opts : %.5f",ttlStdWithOpt)});

		p.popStyle();p.popMatrix();		
	}//drawIndivFtrVec

	//return basic stats for this calc in tight object
	public ArrayList<String> getCalcRes() {return analysisRes;	}//calcRes
	
	
}//calcAnalysis