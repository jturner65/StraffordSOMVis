package strafford_SOM_PKG.straff_SOM_Examples.prospects;

import java.util.*;

import base_Utils_Objects.Tuple;

/**
 * this class is a simple struct to hold a single date's occurrences of a single event type
 * This object will track all the JPs-JPGs affected by this event
 * Objects of this class are intended to be referenced by Date and then by event type
 * (in other words, this struct will give "all the order events on Date ..."
 * These objects should be built after the JP_Occurrences structs are built, and are only useful for training data examples
 */

public class DateEvent_OccurrenceData {
	//date of event
	public final Date evntDate;
	//keyed by jpg-jp tuple, value is map of opt/src value as key and value is # of occurences at this date for passed jp and opt value/src type (ignored unless opt or source record)
	private TreeMap<Tuple<Integer, Integer>, TreeMap<Integer, Integer>> occurrences;
	//type of occurrences being tracked by this object
	private final String type;
	
	public DateEvent_OccurrenceData(Date _date, String _type) {
		evntDate = _date;type=_type;
		occurrences = new TreeMap<Tuple<Integer, Integer>, TreeMap<Integer, Integer>>();
	}
	
	//this will add an occurrence - a single jp-jpg occ and value is # of occurences at date for this jp and opt value/src type (ignored unless opt or source record)
	public void addOcc(Tuple<Integer, Integer> _jpgJp, TreeMap<Integer, Integer> _occ) {	
		occurrences.put(_jpgJp, _occ);
	}
	
	//total # of occurrences across all jpgjp keys at this date
	public int getNumberOfOccurrences() {
		int numOccs = 0;
		for(TreeMap<Integer, Integer> valsForJpJpg : occurrences.values()) {for(Integer count : valsForJpJpg.values()) {		numOccs += count;}}		
		return numOccs;
	}
	public TreeMap<Tuple<Integer, Integer>, TreeMap<Integer, Integer>> getAllOccurrences(){return occurrences;}
	
	//get a map of all types of occurrences, and the counts, for this particular event type for this date
	public TreeMap<Tuple<Integer, Integer>, Integer> getOccurrenceCounts(){
		TreeMap<Tuple<Integer, Integer>, Integer> res = new TreeMap<Tuple<Integer, Integer>, Integer>();
		for(Tuple<Integer, Integer> jpgJp : occurrences.keySet()) {	res.put(jpgJp, occurrences.get(jpgJp).lastEntry().getValue());}	
		return res;
	}

	
	//get the occurence count and opt value for a specific date
	public TreeMap<Integer, Integer> getOccurrencesForJpgJp(Tuple<Integer, Integer> jpgJp){
		TreeMap<Integer, Integer> valsForJpgJp = occurrences.get(jpgJp);
		//should never happen - means querying a date that has no record. 
		if(valsForJpgJp==null) {valsForJpgJp = new TreeMap<Integer, Integer>(); }
		return valsForJpgJp;
	}//getOccurrences


}//class DateEvent_OccurrenceData
