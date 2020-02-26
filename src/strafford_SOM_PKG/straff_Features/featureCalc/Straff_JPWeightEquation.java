package strafford_SOM_PKG.straff_Features.featureCalc;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

import base_JavaProjTools_IRender.base_Render_Interface.IRenderInterface;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.Straff_JP_OccurrenceData;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.Straff_ProspectExample;

//this class will hold the weight calculation coefficients for a single jp this will incorporate the following quantities : 
//presence or absence in prospect record count and datetime of occurence in order record
//count, datetime and opt value of each occurence in opt record. this object only works on prospect examples.  
//No other example should use this calculation object, or analysis statistics will be skewed
public class Straff_JPWeightEquation {
	public final Straff_WeightCalc calcObj;
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
			srcCoeffIDX = 3,
			srcCompareCoeffIDX = 4;
	public static final int numEqs = 5;
	//these names must match order and number of component idxs above
	public static final String[] calcNames = new String[] {"Order","Opt","Link","Source", "SrcForComp"};
	
	//ftr calc coefficients : 
	protected final Float[] FtrMult;						//multiplier for membership functions
	protected final Float[] FtrOffset;					//offsets for membership functions	
	//membership functions hold sum( x / (1 + decay*delT)) for each x, where x is # of occurences on a 
	//particular date and decay is some multiplier based on elapsed time delT (in days)
	protected final Float[] FtrDecay;
	protected final Float[][] FtrParams;
	
	//analysis function for this eq component
	protected Straff_CalcAnalysis[] ftrCalcStats;
	
	//this only is used by weight equations that correspond to jps that do not provide training data
	//this is the training data vector built from all training data examples for this non-product jp
	protected ConcurrentSkipListMap<Integer, Float> aggTrueTrainFtrData;
	protected Float aggTrueTrainTtlWt;
	
	
	//if this equation is using default values for coefficients
	public boolean isDefault;

	//public JPWeightEquation(StraffWeightCalc _calcObj, String _name, int _jp, int[] _jpIdxs, Float[] _m, Float[] _o, Float[] _d, boolean _isDefault) {
	//_eqVals : idx 0->2 are ftr vals; idx 3->5 are comparator vals
	public Straff_JPWeightEquation(Straff_WeightCalc _calcObj, String _name, int _jp, int[] _jpIdxs, Float[][] _eqVals, boolean _isDefault) {
		calcObj = _calcObj; now = calcObj.now;jp=_jp;jpIDXs=_jpIdxs; jpName=_name;
		//for feature calculation equations
		FtrMult = new Float[numEqs];
		FtrOffset = new Float[numEqs];
		FtrDecay = new Float[numEqs];
		//for comparator object calculations
		System.arraycopy(_eqVals[0], 0, FtrMult, 0, numEqs);
		System.arraycopy(_eqVals[1], 0, FtrOffset, 0, numEqs);
		System.arraycopy(_eqVals[2], 0, FtrDecay, 0, numEqs);	
		FtrParams = new Float[][] {FtrMult, FtrOffset, FtrDecay};
		
		aggTrueTrainFtrData = new ConcurrentSkipListMap<Integer, Float>();
		aggTrueTrainTtlWt = 0.0f;		
		
		isDefault = _isDefault;
		ftrCalcStats = new Straff_CalcAnalysis[Straff_WeightCalc.numExamplTypeObjs];
		for(int i=0;i<ftrCalcStats.length;++i) {			ftrCalcStats[i] = new Straff_CalcAnalysis(this, jpIDXs);		}	
	}//ctor	
	
	//decay # of purchases by # of days difference * decay multiplier
	//compare event date to compDate - compDate should always be after evtDate
	protected float decayCalc(int idx, int num, Float[] decay, Date evtDate, Date compDate) {
		if (decay[idx] == 0) {return 1.0f*num;}
		if (evtDate == null) {evtDate = oldDate;} //if no date then consider date is very old
		float decayAmt = 0.0f;
		if (compDate.after(evtDate)) {//now is more recent than the date of the record
			//86.4 mil millis in a day	
			decayAmt = decay[idx] * (compDate.getTime() - evtDate.getTime())/86400000.0f;
		}
		return (num/(1.0f + decayAmt));
	}//decayCalc
	//perform scale calculation - M * ( (# occs)/(DecayMult * # of days since event)) + offset
	protected float scaleCalc(int idx, int num, Float[][] params, Date evtDate, Date compDate) {	
		float val = decayCalc(idx, num, params[2],evtDate,compDate);		
		return  (params[0][idx] * val) + params[1][idx];
	}
	
	//////////////////////////////////////////////////////
	// handle jp-occurrence based calc
	//all JP Occurrence-based event occurrences will decay from -now-
	//get total weight contribution for all events of this jp, based on their date
	protected float aggregateJPOccs(Straff_JP_OccurrenceData jpOcc, int idx, Float[][] params, Date compDate) {
		float res = 0.0f;
		//Date[] dates = jpOcc.getDatesInOrder();
		Set<Date> dates = jpOcc.getDatesInOrderSet();
		for (Date date : dates) {
			TreeMap<Integer, Integer> occDat = jpOcc.getOccurrences(date);
			int numOcc = 0;
			//key is opt value (here sentinel value, since this is from events other than source and opt) and value is count of jp events at that date
			for (Integer count : occDat.values()) {				numOcc += count;			}
			res += scaleCalc(idx,numOcc, params, date,compDate);
		}
		return res;
	}//aggregateOccs
	
	//get total weight contribution for all events of this jp, based on their date
	protected float aggregateJPOccsSourceEv(Straff_JP_OccurrenceData jpOcc, int idx, Float[][] params, Date compDate) {
		float res = 0.0f;
		//Date[] dates = jpOcc.getDatesInOrder();
		Set<Date> dates = jpOcc.getDatesInOrderSet();
		for (Date date : dates) {
			TreeMap<Integer, Integer> occDat = jpOcc.getOccurrences(date);
			int numOcc = 0;
			//key is type of occurrence in source data, 
			//value is count TODO manage source event type calc?
			for (Integer typeSrc : occDat.keySet()) {		numOcc += occDat.get(typeSrc);	}
			res += scaleCalc(idx,numOcc, params, date,compDate);
		}
		return res;
	}//aggregateOccs
	
	//calculate opt data's contribution to feature vector
	protected float calcOptRes(Straff_JP_OccurrenceData jpOcc, Float[][] params, Date compDate) {
		float res = 0;
		//multiple opt occurences of same jp should have no bearing - want most recent opt event - may want to research multiple opt events for same jp, if opt values differ
		//if opt val is -2 for all jps, there might be something we want to learn from this prospect even though they don't want to get emails;  we won't see those people's opt here.
		//On the other hand, if opt is -2 for some jps and not others, this would infer that something about this particular JP may not be attractive to this person, 
		//so other prospects that look like them may not want to see these jps either, so we learn from that - we set this ftr value to be 0 for an opt < 0 , 
		//regardless of what other behavior they have for this jp.  ignores opts of 0.  TODO need to determine appropriate behavior for opts of 0, if there is any
		Entry<Date, TreeMap<Integer, Integer>> vals = jpOcc.getLastOccurrence();
		//vals is entry, vals.getValue is treemap keyed by opt, value is count (should always be 1)
		Integer optChoice = vals.getValue().lastKey(); 
		//last key is going to be extremal opt value - largest key value, which would be highest opt for this jp seen on this date.
		if (optChoice < 0) {res = optOutSntnlVal;}
		else if (optChoice > 0) {	res =  (params[0][optCoeffIDX] * optChoice/(1+params[2][optCoeffIDX])) + params[1][optCoeffIDX];  	}
		//else if (optChoice > 0) {	res =  scaleCalc(optCoeffIDX,1, params, vals.getKey());}		//opts decay very quickly when dates are taken into account - instead we use most recent date for this jp as most relevant opt choice
		return res;
	}//calcOptRes
	
	//calculate a particular example's feature weight value for this object's jp
	//int _exampleType : whether a customer or a true prospect
	//int _bndJPType : whether the jp is an actual ftr jp (in products) or is part of the global jp set (might be ftr jp, might not)
	/**
	 * calculate a particular example's feature weight value for this object's jp
	 * @param ex the example to be 
	 * @param orderJpOccurrences occurences of orders
	 * @param linkJpOccurrences link events
	 * @param optJpOccurrences opt events
	 * @param srcJpOccurrences source events
	 * @param dateOfOrder the date to decay all events from - 
	 * 							for comparison classes (such as true prospects) this will be the "now",the current execution time of the program; 
	 * 							for training data it will be the date of the order responsible for the record
	 * @param _exampleType the type of propsect example being calculated for (cust, true, training).
	 * @return the ftr value for this eq's jp
	 */
	public float calcFtrVal(Straff_ProspectExample ex, Straff_JP_OccurrenceData orderJpOccurrences, Straff_JP_OccurrenceData linkJpOccurrences, Straff_JP_OccurrenceData optJpOccurrences, Straff_JP_OccurrenceData srcJpOccurrences, Date dateOfOrder, int _exampleType) {	
		synchronized(ftrCalcStats[_exampleType]){
			boolean hasData = false;
				//for source data - should replace prospect calc above
			if (srcJpOccurrences != null) {	hasData = true;		ftrCalcStats[_exampleType].setWSVal(srcCoeffIDX, aggregateJPOccsSourceEv(srcJpOccurrences, srcCoeffIDX,FtrParams, dateOfOrder));}
				//handle order occurrences for this jp.   aggregate every order occurrence, with decay on importance based on date
			if (orderJpOccurrences != null) {hasData = true;	ftrCalcStats[_exampleType].setWSVal(orderCoeffIDX, aggregateJPOccs(orderJpOccurrences, orderCoeffIDX,FtrParams, dateOfOrder));}
				//for links use same mechanism as orders - handle differences through weightings - aggregate every order occurrence, with decay on importance based on date
			if (linkJpOccurrences != null) {hasData = true;		ftrCalcStats[_exampleType].setWSVal(linkCoeffIDX, aggregateJPOccs(linkJpOccurrences, linkCoeffIDX,FtrParams, dateOfOrder));	}
				//user opts - these are handled differently - calcOptRes return of -9999 means negative opt specified for this jp alone (ignores negative opts across all jps) - should force total from eq for this jp to be ==0
			if (optJpOccurrences != null) {	hasData = true;		ftrCalcStats[_exampleType].setWSVal(optCoeffIDX, calcOptRes(optJpOccurrences,FtrParams, dateOfOrder));}	
			if (hasData) {calcObj.incrBnds(Straff_WeightCalc.bndAra_ProdJPsIDX,_exampleType,jpIDXs[Straff_WeightCalc.bndAra_ProdJPsIDX]);		}
			float res = ftrCalcStats[_exampleType].getFtrValFromCalcs(optCoeffIDX, optOutSntnlVal);//(calcStats.workSpace[optCoeffIDX]==optOutSntnlVal);
			return res;
		}
	}//calcFtrVal
	
	//////////////////
	//For non-product jps using jp-occurrences
	
	//initialize this non-product jp's equation to aggregate the appropriate ftr vector info
	public synchronized void initCalcCustNonProdWtVec() {	aggTrueTrainFtrData.clear();	aggTrueTrainTtlWt = 0.0f;		}//initCalcCustNonProdWtVec
	//source data contribution calculation 
	public float getSrcContrib_Now(Straff_JP_OccurrenceData srcJpOccurrences) {
		return aggregateJPOccsSourceEv(srcJpOccurrences, srcCoeffIDX,FtrParams, now);//calcStats.workSpace[orderCoeffIDX] = aggregateOccs(orderJpOccurrences, orderCoeffIDX);}
	}//getSrcContrib	
	
	/**
	 * This function uses the fact that the passed customer example has activity in 
	 * this equation's non-product-based JP (this is only run for non-product JPs)
	 * The function will record the ftr vector (of product jps) for this example, 
	 * weighted by this example's source data information.  All customer examples with activity 
	 * in this eq's non-product jp will have their product-based ftr vectors recorded in this way,
	 * to synthesize a weighted average ftr vector of all customers sharing this nonprod jp.
	 * Then, this ftr vector will be assigned to True prospects (weighted by the TP's source contribution)
	 * as a feature vector.
	 * @param ftrData the feature data for the customer example having this eq's non-product jp
	 * @param srcJpOccurrences the occurrences of source event records (The only events having non-product jp data)
	 */
	public synchronized void buildCustNonProdWtVecContrib(TreeMap<Integer, Float> ftrData, Straff_JP_OccurrenceData srcJpOccurrences) {	
			//for source data - should replace prospect calc above
		float wt = 0;
		if (srcJpOccurrences != null) {	wt = aggregateJPOccsSourceEv(srcJpOccurrences, srcCoeffIDX,FtrParams, now);}//calcStats.workSpace[orderCoeffIDX] = aggregateOccs(orderJpOccurrences, orderCoeffIDX);}
		if(wt==0) {return;}
	
		for(Integer key : ftrData.keySet()) {
			Float ftrWt = aggTrueTrainFtrData.get(key);
			if(ftrWt==null) {ftrWt = 0.0f;}
			ftrWt += wt * ftrData.get(key);
			aggTrueTrainFtrData.put(key, ftrWt);
		}
		aggTrueTrainTtlWt +=wt;				
	}//calcVal		
	
	public synchronized void finalizeCalcCustNonProdWtVec() {//normalize feature vector weight multiplier
		if(0==aggTrueTrainTtlWt) {aggTrueTrainFtrData.clear();return;}
		for(Integer key : aggTrueTrainFtrData.keySet()){aggTrueTrainFtrData.put(key, aggTrueTrainFtrData.get(key)/aggTrueTrainTtlWt);}
		//for(Integer key : aggTrueTrainFtrData.keySet()){	if(aggTrueTrainFtrData.get(key) < .00001f) {aggTrueTrainFtrData.remove(key);}	}
	}//
	
	//calculate the training data feature vector corresponding to this (non-prod - will only exist for non-prod) jp-based eq (this eq object will represent a non-training-data jp)
	//for the passed true prospect by seeing their weight and multiplying the pre-calculated ftr vector 
	public synchronized TreeMap<Integer, Float> calcNonProdWtVec(Straff_ProspectExample ex, float wt) {	
			//for source data - should replace prospect calc above
		TreeMap<Integer, Float> res = new TreeMap<Integer, Float>();		
		//get this non-prod jp's contribution to ex's weight vector
		if (aggTrueTrainFtrData.size() == 0) {return res;}
		for(Integer key : aggTrueTrainFtrData.keySet()) {					res.put(key, aggTrueTrainFtrData.get(key) * wt);	}
		return res;
	}//calcVal	
	//////////////////
	// end For non-product jps using jp-occurrences	
	//////////////////////////////////////////////////////
	// end handle jp-occurrence based calc

	
	//reset analysis object to clear out all stats from previous run
	public void resetAnalysis(int _exampleType) {ftrCalcStats[_exampleType].reset();	}	
	public void aggregateFtrCalcVals(int _exampleType) {ftrCalcStats[_exampleType].aggregateCalcVals();}	
	public ArrayList<String> getCalcRes(int _exampleType){return ftrCalcStats[_exampleType].getCalcRes();}	
	
	
	public void drawIndivFtrVec(IRenderInterface p, float height, float width, int _exampleType) {ftrCalcStats[_exampleType].drawIndivFtrVec(p, height, width);	}
	public void drawFtrVec(IRenderInterface p, float height, float width, boolean selected, int eqDispType, int _exampleType){ftrCalcStats[_exampleType].drawFtrVec(p, height, width,eqDispType, selected);}
	
	//string rep of this calc
	public String toString() {
		int jpFtrIDX = jpIDXs[Straff_WeightCalc.bndAra_ProdJPsIDX];
		int jpAllIDX = jpIDXs[Straff_WeightCalc.bndAra_AllJPsIDX];
		String jpAllBuffer = (jpAllIDX >=100) ? "" : (jpAllIDX >=10) ? " " : "  ";//to align output 
		String res = "JP : "+ String.format("%3d", jp) + " JPs["+jpAllIDX+"]" + jpAllBuffer;
		if(jpFtrIDX >= 0) {
			String ftrBuffer = (jpFtrIDX >=100) ? "" : (jpFtrIDX >=10) ? " " : "  ";//to align output
			res += " Ftr[" + jpFtrIDX + "]" + ftrBuffer + " = ";				
		} 
		else {	res += " (Not a Training Ftr) = ";	}
		//order
		res += " ("+ String.format("%.3f", FtrMult[orderCoeffIDX]) + "*[sum(# orders[i]/(1 + "+String.format("%.4f", FtrDecay[orderCoeffIDX])+" * DEV)) for each order@date i]+"+String.format("%.4f", FtrOffset[orderCoeffIDX])+")"; 		
		//link
		res += "+("+ String.format("%.3f", FtrMult[linkCoeffIDX]) + "*[sum(# links[i]/(1 + "+String.format("%.4f", FtrDecay[linkCoeffIDX])+" * DEV)) for each link@date i]+"+String.format("%.4f", FtrOffset[linkCoeffIDX])+")"; 		
		//opt
		res +=  "+("+ String.format("%.3f", FtrMult[optCoeffIDX]) + "*Opt Val/(1 + "+String.format("%.4f", FtrDecay[optCoeffIDX])+" * DEV)+"+String.format("%.4f", FtrOffset[optCoeffIDX])+")"; 			
		//source 
		res += "+("+ String.format("%.3f", FtrMult[srcCoeffIDX]) + "*[sum(# sources[i]/(1 + "+String.format("%.4f", FtrDecay[srcCoeffIDX])+" * DEV)) for each source@date i]+"+String.format("%.4f", FtrOffset[srcCoeffIDX])+")"; 		
		return res;
	}//toString
}//JPWeightEquation
