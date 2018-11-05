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
	public SOMMapData map;
	public final Date now;
	//arrays are for idxs of various eq components (mult, offset, decay) in the format file for 
	//each component contributor (prospect, order, opt), in order.  0-idx is jp name or "default"
	private static final int[] mIdx = new int[] {1, 4, 7, 10},
			   					oIdx = new int[] {2, 5, 8, 11},
			   					dIdx = new int[] {3, 6, 9, 12};
	//map of per-jp equations to calculate feature vector from prospect, order, 
	//and opt data (expandable to more sources eventually if necessary).  keyed by jp
	private TreeMap<Integer, JPWeightEquation> eqs;
	
	//display min and max values of each jp calculation
	//private TreeMap<Integer, float[]> bnds;
	private Float[][] bndsAra;
	
	public StraffWeightCalc(SOMMapData _map, String _fileName) {
		map = _map;
		now = map.now.getTime();
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
		for (int i=0;i<map.jpByIdx.length;++i) {
			Integer jp = map.jpByIdx[i];
			JPWeightEquation eq = new JPWeightEquation(this,jp, i, dfltM, dfltO, dfltD);
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
		bndsAra = new Float[4][];
		float[] initBnd = new float[] {1000000000.0f,-1000000000.0f, 0.0f, 0.0f};//min, max, count, diff
		for (int i=0;i<bndsAra.length;++i) {
			bndsAra[i]=fastCopyAra(map.jpByIdx.length, initBnd[i]);
		}		
	}//initBnds() 
	
	//check if value is in bnds array for particular jp, otherwise modify bnd
	private void checkValInBnds(Integer jpidx, Integer destIDX, float val) {
		//float[] bnd = bnds.get(jpidx);
		if (val < bndsAra[0][destIDX]) {bndsAra[0][destIDX]=val;bndsAra[3][destIDX] = bndsAra[1][destIDX]-bndsAra[0][destIDX];}
		if (val > bndsAra[1][destIDX]) {bndsAra[1][destIDX]=val;bndsAra[3][destIDX] = bndsAra[1][destIDX]-bndsAra[0][destIDX];}
	}
	
	public void incrBnds(Integer jpidx, Integer destIDX) {
//		Integer destIDX = map.jpToFtrIDX.get(jpidx);
//		if (destIDX==null) {return;}//ignore unknown/unmapped jps
		//bndsAra[destIDX][2] +=1;
		bndsAra[2][destIDX] +=1;
		//float[] bnd = bnds.get(jpidx); bnd[2]+=1;
	}
	//public TreeMap<Integer, float[]> getBndsMap(){return bnds;}
	public Float[][] getBndsAra(){return bndsAra;}
	
	private void addIndivJpEq(String[] strVals) {		
		Integer jp = Integer.parseInt(strVals[0]);
		Float[] jpM = getFAraFromStrAra(strVals,mIdx), jpO = getFAraFromStrAra(strVals,oIdx), jpD = getFAraFromStrAra(strVals,dIdx);
		Integer idx = map.jpToFtrIDX.get(jp);
		JPWeightEquation eq = new JPWeightEquation(this,jp, idx, jpM, jpO, jpD);
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
		jpOccurrenceData optOcc;
		TreeMap<Integer, Float> res = new TreeMap<Integer, Float>();
		float ftrVecSqMag = 0.0f;
		//for each jp present in ex, perform calculation
		Integer destIDX;
		boolean isZeroMagExample = true;		//if all values are 0 then this is a bad training example, we want to ignore it
		for (Integer jp : jps) {
			//find destIDX
			destIDX = map.jpToFtrIDX.get(jp);
			if (destIDX==null) {continue;}//ignore unknown/unmapped jps
			optOcc = optOccs.get(jp);
			if (optOcc == null) {optOcc = optOccs.get(-9);}//this is if all values have positive opts
			float val = eqs.get(jp).calcVal(ex,orderOccs.get(jp),linkOccs.get(jp), optOcc);
			if ((isZeroMagExample) && (val != 0)) {isZeroMagExample = false;}
			res.put(destIDX,val);
			ftrVecSqMag += (val*val);
			checkValInBnds(jp,destIDX, val);
		}		

		ex.ftrVecMag = (float) Math.sqrt(ftrVecSqMag);
		ex.setIsBadExample(isZeroMagExample);
		return res;
	}//calcFeatureVector
	
	
	//display calc equations for each JP id
	@Override
	public String toString() {
		String res = "\n# eqs : " + eqs.size() + "\t|Build from file : " + fileName+ "\t| Equation Configuration : \n";
		res += "-- DLU : Days since prospect lookup\n";
		res += "-- NumOcc[i] : Number of occurrences of jp in event i on a specific date\n";
		res += "-- DEV : Days since event i occurred\n";
		res += "-- OptV : opt multiplier/filter value reflecting opt choice > 0 (<0 forces wt to be 0 for JP)\n\n";
		res += "\tIf opt val is -2 for all jps, there might be something we want to learn from this prospect\n\teven though they don't want to get emails. Such opts are ignored in this calculation.\n\n";
		res += "\tOn the other hand, if opt is -2 for some jps and not others, this suggests that something\n\tabout this JP may not be attractive to this person, so other prospects that look like them\n";
		res += "\tmay not want to see these jps either.\n\tWe currently learn from that by forcing this JP to have 0 weight for such a user, regardless of other JP data.\n\n";		
		for (JPWeightEquation eq : eqs.values()) {
			//float[] bnd = bnds.get(eq.jp);
			Float[] bnd = bndsAra[eq.jpIdx];
			Integer numSeen = map.getCountJPSeen(eq.jp);
			res+= eq.toString()+"  |# Calcs done : " + String.format("%6d", (Math.round(bnd[2]))) + "\t| # of Occs : " +String.format("%6d", numSeen) + "\t| Min calc seen : " +String.format("%6.4f", bnd[0]) + "\t| Max calc seen : " +String.format("%6.4f", bnd[1]) + "\n";}
		return res;
	}
	
}//StraffWeightCalc

//this class will hold the weight calculation coefficients for a single jp
//this will incorporate the following quantities : 
//presence or absence in prospect record
//count and datetime of occurence in order record
//count, datetime and opt value of each occurence in opt record. 
class JPWeightEquation {
	public final StraffWeightCalc calcObj;
	public static Date now;
	public final int jp, jpIdx;				//corresponding jp, jp index in weight vector
	
	//mults and offsets are of the form (mult * x + offset), where x is output of membership function
	public static int 						//idxs in eq coefficient arrays
			prspctCoeffIDX = 0, 
			orderCoeffIDX = 1, 
			optCoeffIDX = 2,
			linkCoeffIDX = 3;
	private static int numEqs = 4;
	
	private final Float[] Mult;						//multiplier for membership functions
	private final Float[] Offset;					//offsets for membership functions
	
	//membership functions hold sum( x / (1 + decay*delT)) for each x, where x is # of occurences on a 
	//particular date and decay is some multiplier based on elapsed time delT (in days)
	private final Float[] Decay;
	
	public JPWeightEquation(StraffWeightCalc _calcObj, int _jp, int _jpIdx, Float[] _m, Float[] _o, Float[] _d) {
		calcObj = _calcObj; now = calcObj.now;jp=_jp;jpIdx=_jpIdx;
		Mult = new Float[numEqs];
		Offset = new Float[numEqs];
		Decay = new Float[numEqs];
		System.arraycopy(_m, 0, Mult, 0, numEqs);
		System.arraycopy(_o, 0, Offset, 0, numEqs);
		System.arraycopy(_d, 0, Decay, 0, numEqs);		
	}//
	
	private float decayCalc(int idx, int num, Date date) {
		if (date == null) {return 0;}// 1.0f * num;}//if no date then consider date is very old
		long delMillis = Math.abs(now.getTime() - date.getTime());
		//86.4 mil millis in a day
		float daysSince=delMillis/864000000.0f;
		return (num/(1.0f + (Decay[idx] * daysSince)));
	}//decayCalc
	
	private float scaleCalc(int idx, int num, Date date) {	
		float val = decayCalc(idx, num, date);		
		return  (Mult[idx] * val) + Offset[idx];
	}
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
	
	//returns this jp's contribution to the weight vector of a particular prospect
	public float calcVal(ProspectExample ex, jpOccurrenceData orderJpOccurrences, jpOccurrenceData linkJpOccurrences, jpOccurrenceData optJpOccurrences) {		
		calcObj.incrBnds(jp, jpIdx);
		float res = 0.0f;
		if (ex.prs_JP == jp) {//scale propsect contribution by update date of record - assumes accurate at time of update
			res +=scaleCalc(prspctCoeffIDX, 1, ex.prs_LUDate);
		}
		//get all occurences for this jp.  if 
		//jpOccurrenceData orderJpOccurrences = ex.orderJpOccurrences.get(jp);
		if (orderJpOccurrences != null) {//aggregate every order occurrence, with decay on importance based on date
			res += aggregateOccs(orderJpOccurrences, orderCoeffIDX);		
		}
		//for links use same mechanism as orders - handle differences through weightings
		if (linkJpOccurrences != null) {//aggregate every order occurrence, with decay on importance based on date
			res += aggregateOccs(linkJpOccurrences, linkCoeffIDX);		
		}
		
		//jpOccurrenceData optJpOccurrences = ex.optJpOccurrences.get(jp);
		if (optJpOccurrences != null) {
			//TODO
			//multiple opt occurences of same jp should have no bearing - want most recent opt event - may want to research multiple opt events for same jp, if opt values differ
			//if opt val is -2 for all jps, there might be something we want to learn from this prospect even though they don't want to get emails;  we won't see those people's opt here
			//on the other hand, if opt is -2 for some jps and not others, this would infer that something about this particular JP may not be attractive to this person, 
			//so other prospects that look like them may not want to see these jps either, so we learn from that - we set this ftr value to be 0 for an opt < 0 , 
			//regardless of what other behavior they have for this jp
			Entry<Date, Tuple<Integer,Integer>> vals = optJpOccurrences.getLastOccurrence();
			Date mostRecentDate = vals.getKey();
			Integer optChoice = vals.getValue().y;
			if (optChoice < 0) {res = 0;}
			else if (optChoice > 0) {	res +=  (Mult[optCoeffIDX] * 1.0/(1+Decay[optCoeffIDX]) ) + Offset[optCoeffIDX];  	}
			//res += aggregateOccs(optJpOccurrences, optCoeffIDX);		
		}	
		return res;
	}//calcVal
	
	public String toString() {
		String res = "JP : "+ String.format("%3d", jp) +" Ftr IDX : " + String.format("%3d", jpIdx) + " | wt = ";
		//prospectString.format("%.4f",this.x) 
		res += "("+String.format("%.4f", Mult[prspctCoeffIDX]) + "(1.0/(1 + "+String.format("%.4f", Decay[prspctCoeffIDX])+"*DLU)) + "+String.format("%.4f", Offset[prspctCoeffIDX])+")"; 
		//order
		res += " + ("+ String.format("%.4f", Mult[orderCoeffIDX]) + " [ sum(NumOcc[i]/(1 + "+String.format("%.4f", Decay[orderCoeffIDX])+" * DEV) for each event[i]] + "+String.format("%.4f", Offset[orderCoeffIDX])+")"; 		
		//opt
		res +=  " + (("+ String.format("%.4f", Mult[optCoeffIDX]) + "* OptV + "+String.format("%.4f", Offset[optCoeffIDX])+")"; 		
		
		return res;
	}//toString
		
}//JPWeightEquation