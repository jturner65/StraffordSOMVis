package strafford_SOM_PKG.straff_SOM_Examples.prospects;

import java.util.*;
import java.util.Map.Entry;

import base_Utils_Objects.vectorObjs.Tuple;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.BaseRawData;

/**
 * this class is a simple struct to hold a single jp's jpg, and the count and date of all occurrences for a specific OID/prospect
 * Objects of this class are intended to be referenced by type of event and then by JP
 */
public class JP_OccurrenceData{
	public static final String occRecStTag = "Occ_St,",occRecEndTag = "Occ_End,", 
			perDateOccRecSt = "DtOccSt,", perDateOccRecEnd = "DtOccEnd,";
	//this jp
	public final Integer jp;
	//owning jpg
	public final Integer jpg;

	//keyed by date, value is map of opt/src value and value is # of occurences at date for this jp and opt value/src type (ignored unless opt or source record)
	private TreeMap<Date, TreeMap<Integer, Integer>> occurrences;
	//whether or not opt/src is used for this occurence
	private boolean usesOpt;
	
	public JP_OccurrenceData(Integer _jp, Integer _jpg, boolean _usesOpt) {jp=_jp;jpg=_jpg; usesOpt=_usesOpt;occurrences = new TreeMap<Date, TreeMap<Integer, Integer>>();}	
	
	//example of csv string
	//pr_000000331,2016-11-21 16:15:51,0,1,7,OPT|,LNK|,Occ_St,364,4,0,DtOccSt,2012-04-25 15:59:14,1,3,2,DtOccEnd,DtOccSt,2016-02-13 07:49:31,1,3,1,DtOccEnd,Occ_End,SRC|,Occ_St,69,4,1,DtOccSt,2007-09-21 21:53:32,1,11,1,DtOccEnd,DtOccSt,2009-02-06 05:53:38,1,57,1,DtOccEnd,Occ_End,Occ_St,131,4,1,DtOccSt,2009-02-06 05:53:49,1,57,1,DtOccEnd,DtOccSt,2017-10-03 03:07:09,1,92,1,DtOccEnd,Occ_End,Occ_St,227,20,1,DtOccSt,2010-01-04 22:22:49,1,41,1,DtOccEnd,Occ_End,Occ_St,231,64,1,DtOccSt,2010-03-05 01:49:47,1,41,1,DtOccEnd,Occ_End,Occ_St,232,8,1,DtOccSt,2009-12-18 23:15:20,1,41,1,DtOccEnd,Occ_End,Occ_St,237,1,1,DtOccSt,2009-12-18 23:15:03,1,41,1,DtOccEnd,Occ_End,Occ_St,274,64,1,DtOccSt,2017-10-03 03:07:09,1,92,1,DtOccEnd,Occ_End,
	//build from string of saved preproc data
	public JP_OccurrenceData(String _csvString) {
		occurrences = new TreeMap<Date, TreeMap<Integer, Integer>>();
		String[] strVals = _csvString.trim().split(",");
		//example :
		//269,52,0,DtOccSt,2016-09-23 10:54:06,1,3,1,DtOccEnd,Occ_End,
		jp=Integer.parseInt(strVals[0].trim());
		jpg=Integer.parseInt(strVals[1].trim());		
		usesOpt = (strVals[3].trim() == "1");
		//build occurrences
		parseStringToOccVals(_csvString);
	}//csv ctor
	
	//add an occurence on date - for a specific JP, for a specific Date, this builds either
	//a map of optOrSrc, count (which will be only 1 opt value, either a dummy (for non opt events) or an opt or source event type, and a count of those opt value events)
	//--note, this will support multiple opts for same jp for same date - old mechanism just overwrote previous opts regardless of value
	// or
	//		a map of srcValue,count (which will be potentially many sources with 1 or more counts, as recorded in source event records)
	public void addOccurrence(Date date, int optOrSrc) {
		TreeMap<Integer, Integer> valsAtDate = occurrences.get(date);
		if(valsAtDate==null) {valsAtDate = new TreeMap<Integer, Integer>(); }
		Integer count = valsAtDate.get(optOrSrc);
		if(count == null) {count = 0;}
		++count;
		valsAtDate.put(optOrSrc, count);
		occurrences.put(date, valsAtDate);
	}//addOccurence
	
	//add occurrence information from dateEvent_OccurrenceData, which will have a copy for a specific date of all appropriate ValsAtDate
	public void addOccurrence(Date date,TreeMap<Integer, Integer> valsAtDate) {
		TreeMap<Integer, Integer> newValsAtDate = new TreeMap<Integer, Integer>(valsAtDate);
		occurrences.put(date, newValsAtDate);
	}
	
	//add to occurences struct from csv data
	private void addOccurrenceFromCSV(Date date, int optOrSrc, int _count) {
		TreeMap<Integer, Integer> valsAtDate = occurrences.get(date);
		if(valsAtDate==null) {valsAtDate = new TreeMap<Integer, Integer>(); }
		Integer count = valsAtDate.get(optOrSrc);
		if(count == null) {count = 0;}
		count+=_count;
		valsAtDate.put(optOrSrc, count);
		occurrences.put(date, valsAtDate);		
	}//addOccurrenceCSV
		
	//accessors - oldest first
	public Date[] getDatesInOrder() {return occurrences.keySet().toArray(new Date[0]);}	
	public Set<Date> getDatesInOrderSet() {return occurrences.keySet();}	
	
	//get occurrence with largest date (newest)
	public Entry<Date, TreeMap<Integer, Integer>> getLastOccurrence(){		return occurrences.lastEntry();	}//
	
	//get the occurence count and opt value for a specific date
	public TreeMap<Integer, Integer> getOccurrences(Date date){
		TreeMap<Integer, Integer> valsAtDate = occurrences.get(date);
		//should never happen - means querying a date that has no record. 
		if(valsAtDate==null) {valsAtDate = new TreeMap<Integer, Integer>(); }
		return valsAtDate;
	}//getOccurrences
	
	//get tuple of jpg and jp
	public Tuple<Integer, Integer> getJpgJp(){return new Tuple<Integer,Integer>(jpg, jp);}
	//read string data in and parse out into occ data
	private void parseStringToOccVals(String _csvStringRaw) {	
		//example :
		//269,52,0,DtOccSt,2016-09-23 10:54:06,1,3,1,DtOccEnd,Occ_End,
		//System.out.println("jpOccurrenceData::parseStringToOccVals : _csvStringRaw : " + _csvStringRaw);
		String[] _csvPieces = _csvStringRaw.trim().split(occRecEndTag);
		String[] occRecs = _csvPieces[0].trim().split(perDateOccRecSt);
		for(int j=1;j<occRecs.length;++j) {//skip first record
			String[] optRecData = occRecs[j].trim().split(",");
			//System.out.println("jpOccurrenceData::parseStringToOccVals \tperDateOcc : " + occRecs[j] + " # vals in optRecData " + optRecData.length);
			Date date = BaseRawData.buildDateFromString(optRecData[0]);
			int countAtDate = Integer.parseInt(optRecData[1]);
			//for count reps, starting at 2,3 we have pairs of idxs
			for(int i=0;i<countAtDate;++i) {
				Integer optOrSrc = Integer.parseInt(optRecData[i+2]);
				Integer count = Integer.parseInt(optRecData[i+3]);
				addOccurrenceFromCSV(date,optOrSrc,count);
			}
		}
		//each string is per date occurence data
	}//parseStringToOccVals
	
	//# of this type of event for this jp across all dates
	public synchronized int getNumberOfOccurrences() {
		int numOccs = 0;
		for(TreeMap<Integer, Integer> valsAtDate : occurrences.values()) {for(Integer count : valsAtDate.values()) {		numOccs += count;}}		
		return numOccs;
	}
	
	//return a representation of this occurence struct as a comma sep string, to save to file
	public String toCSVString() {
		String res = occRecStTag + ""+jp+","+jpg+"," + (usesOpt ? "1,":"0,");
		for (Date date : occurrences.keySet()) {
			res += perDateOccRecSt;
			String dateString = BaseRawData.buildStringFromDate(date);
			res += dateString+",";
			TreeMap<Integer, Integer> valsAtDate = occurrences.get(date);
			res +=""+valsAtDate.size()+",";
			for(Integer opt : valsAtDate.keySet()) {			res+=""+opt+","+valsAtDate.get(opt)+",";	}		
			res += perDateOccRecEnd;
		}
		res += occRecEndTag;
		return res;		
	}//toCSVString()
	
	public String toString() {
		String resTmp = "", res = "JP : " + jp + " | JPGrp : " + jpg +" ";//+ (occurrences.size() > 1 ? "\n" : "");
		int cntOccTtl = 0;
		for (Date dat : occurrences.keySet()) {
			TreeMap<Integer, Integer> occData = occurrences.get(dat);
			for (Integer opt : occData.keySet()) {
				Integer count = occData.get(opt);
				cntOccTtl += count;
				String optStr = "";
				if(usesOpt) { optStr = " | Opt/Src : " + opt;} //is an opt occurence
				resTmp += "\tDate : " + dat.toString() + " : # occurences : " + count + optStr + " | " ;
			}			
		}
		res += "# ttl occs : "+ cntOccTtl + " | " + resTmp;
		return res;
	}		
}//class jpOccurenceData


