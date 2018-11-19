package SOM_Strafford_PKG;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

//this class will monitor presence and counts of jpgroups and jps
//in training data for map
public class MonitorJpJpgrp {
	public static SOMMapData mapData;
	////////////////////////////////////////
	//this information comes from the prospect and product map data
	//reference to jp ids and jpgrps and counts of all jps and jpgrps seen in all data 
	private TreeMap<Integer, Integer> jpSeenCount;
	//reference to ids and counts of all jps seen in -event- data
	private TreeMap<Integer, Integer> jpEvSeenCount;
	//reference to ids and counts of all jps seen only in prospect record data (not including events)
	private TreeMap<Integer, Integer> jpPrspctSeenCount;// = new TreeMap<Integer, Integer>();//jpEvSeen
	//map from jp to idx in resultant feature vector
	private TreeMap<Integer, Integer> jpToFtrIDX;// = new TreeMap<Integer, Integer>();
	//map from jpgroup to integer
	private TreeMap<Integer, Integer> jpgToIDX;// = new TreeMap<Integer, Integer>();
	//map from jpgroup to jps corresponding to this group.
	private TreeMap<Integer, TreeSet <Integer>> jpgsToJps;// = new TreeMap<Integer, TreeSet <Integer>>();
	//map from jps to owning jpgs
	private TreeMap<Integer, Integer> jpsToJpgs;// = new TreeMap<Integer, Integer>();
	//used by UI and also to map specific indexes to actual jps/jpgs
	private Integer[] jpByIdx = new Integer[] {1}, jpgrpsByIdx = new Integer[] {1};
	//# of jps seen = size of jpByIdx array == # of ftrs
	private int numFtrs;
	
	
	public MonitorJpJpgrp(SOMMapData _mapData) {
		mapData=_mapData;		
	}//ctor
	
	public Integer getJpByIdx(int idx) {return jpByIdx[idx];}
	public Integer getJpToFtrIDX(int jp) {return jpToFtrIDX.get(jp);}
	public int getNumFtrs() {return numFtrs;}

	
	private void incrJPCounts(Integer jp, TreeMap<Integer, Integer> map) {
		Integer count = map.get(jp);
		if(count==null) {count=0;}
		map.put(jp, count+1);
	}//incrJPCounts	
	
	private void initAllStructs() {		
		jpSeenCount = new TreeMap<Integer, Integer>(); 	//count of prospect training data records having jp
		jpEvSeenCount = new TreeMap<Integer, Integer>(); //count of prospect train records having jp only counting events
		jpPrspctSeenCount = new TreeMap<Integer, Integer>(); //count of prospect train records having jp only in base prospect record
		jpsToJpgs = new TreeMap<Integer, Integer>();	//map from jpgs to jps
		jpgsToJps = new TreeMap<Integer, TreeSet <Integer>>();
		
		jpToFtrIDX = new TreeMap<Integer, Integer>();	
		jpgToIDX = new TreeMap<Integer, Integer>();	
	}
	
	//this will set the current jp->jpg data maps based on passed prospect data map
	//When acquiring new data, this must be performed after all data is loaded, but before
	//the prospect data is finalized and actual map is built due to the data finalization 
	//requiring a knowledge of the entire dataset to build weights appropriately
	public void setJPDataFromExampleData(ConcurrentSkipListMap<String, ProspectExample> prspctMap, ConcurrentSkipListMap<String, ProductExample> prdctMap) {
		initAllStructs();
		//rebuild all jp->jpg mappings based on prospect data
		HashSet<Tuple<Integer,Integer>> tmpSetAllJpsJpgs = new HashSet<Tuple<Integer,Integer>>();
		HashSet<Tuple<Integer,Integer>> tmpSetProspectEventJpsJpgs = new HashSet<Tuple<Integer,Integer>>();//only seen in prospect/event records
		HashSet<Tuple<Integer,Integer>> tmpSetProductJpsJpgs = new HashSet<Tuple<Integer,Integer>>();//only seen in product records
		
		for (ProspectExample ex : prspctMap.values()) {//for every prospect, look at every jp
			HashSet<Tuple<Integer,Integer>> tmpExSet = ex.getSetOfAllJpgJpData(); //tmpExSet is set of all jps/jpgs in ex
			for (Tuple<Integer,Integer> jpgJp : tmpExSet) {
				Integer jpg = jpgJp.x, jp=jpgJp.y;
				incrJPCounts(jp, jpSeenCount);
				boolean[] recMmbrship = ex.hasJP(jp);
				if (recMmbrship[1]){incrJPCounts(jp,jpPrspctSeenCount);}
				if (recMmbrship[2]){incrJPCounts(jp,jpEvSeenCount);}
				tmpSetAllJpsJpgs.add(jpgJp);
				tmpSetProspectEventJpsJpgs.add(new Tuple<Integer,Integer>(jpgJp));			//only seen in prospect records
			}											//add all tuples to set already seen
		}//for each prospect
		mapData.dispMessage("MonitorJpJpgrp : setJPDataFromExampleData : num jps seen in prospects  : " + tmpSetAllJpsJpgs.size());
		
		//add all jps-jpgs from product data - will have data not seen in prospects		
		for(ProductExample ex : prdctMap.values()) {
			HashSet<Tuple<Integer,Integer>> tmpExSet = ex.getSetOfAllJpgJpData(); //tmpExSet is set of all jps/jpgs in ex
			for (Tuple<Integer,Integer> jpgJp : tmpExSet) {
				//only do the following if we wish to add these values to the map
				Integer jpg = jpgJp.x, jp=jpgJp.y;
				incrJPCounts(jp, jpSeenCount);
				tmpSetAllJpsJpgs.add(jpgJp);
				tmpSetProductJpsJpgs.add(new Tuple<Integer,Integer>(jpgJp));
			}											//add all tuples to set already seen
		}//for each product		
		mapData.dispMessage("MonitorJpJpgrp : setJPDataFromExampleData : num jps seen in products  : " + tmpSetProductJpsJpgs.size()  + " | # seen now in tmpSetAllJpsJpgs :  " +tmpSetAllJpsJpgs.size()+ " | # seen only in products : " + (tmpSetAllJpsJpgs.size() - tmpSetProspectEventJpsJpgs.size()) + " | # of product examples : " + prdctMap.size());
				
		//build jpsToJpgs and JpgsToJps structs
		for (Tuple<Integer,Integer> jpgJp : tmpSetAllJpsJpgs) {
			Integer jpg = jpgJp.x, jp=jpgJp.y;
			jpsToJpgs.put(jp, jpg);
			TreeSet <Integer> jpList = jpgsToJps.get(jpg);
			if (jpList==null) {jpList = new TreeSet <Integer>(); jpgsToJps.put(jpg, jpList);}
			jpList.add(jp);		
		}

		jpByIdx = jpSeenCount.keySet().toArray(new Integer[0]);
		jpgrpsByIdx = jpgsToJps.keySet().toArray(new Integer[0]);
		for(int i=0;i<jpByIdx.length;++i) {jpToFtrIDX.put(jpByIdx[i], i);}
		for(int i=0;i<jpgrpsByIdx.length;++i) {jpgToIDX.put(jpgrpsByIdx[i], i);}
		
		numFtrs = jpSeenCount.size();
		
		mapData.dispMessage("MonitorJpJpgrp : setJPDataFromExampleData : numFtrs : " + numFtrs);
	}//setJPDataFromProspectData	
	
		
	public String getJpByIdxStr(int uiIDX) {
		int idx = uiIDX % jpByIdx.length;
		return "" + jpByIdx[idx] + " :(idx=" +idx+ ")";
	}
	
	public String getJpGrpByIdxStr(int uiIDX) {
		int idx = uiIDX % jpgrpsByIdx.length; 
		return "" + jpgrpsByIdx[idx];
	}
	
	public int getLenJpByIdxStr() {		return jpByIdx.length;	}	
	public int getLenJpGrpByIdxStr(){	return jpgrpsByIdx.length; }
	
	//debugging function to display all unique jps seen in data
	public void dbgShowUniqueJPsSeen() {
		mapData.dispMessage("All Jp's seen : ");
		for (Integer key : jpSeenCount.keySet()) {mapData.dispMessage("JP : "+ String.format("%3d",key) + "   |Count : " + String.format("%6d",jpSeenCount.get(key)) + "\t|Ftr IDX : " + String.format("%3d",jpToFtrIDX.get(key)) + "\t|Owning JPG : " + String.format("%2d",jpsToJpgs.get(key)));}
		mapData.dispMessage("Number of unique JP's seen : " + jpSeenCount.size());
		dbgDispKnownJPsJPGs();
	}//dbgShowUniqueJPsSeen
	
	public void dbgDispKnownJPsJPGs() {
		mapData.dispMessage("\nJPGs seen : (jp : count : ftridx) :");
		for (Integer jpgrp : jpgsToJps.keySet()) {
			String res = "JPG : " + String.format("%3d", jpgrp);
			TreeSet <Integer> jps = jpgsToJps.get(jpgrp);			
			for (Integer jp : jps) {res += " ("+String.format("%3d", jp)+" :"+String.format("%6d", jpSeenCount.get(jp))+" : "+ String.format("%3d", jpToFtrIDX.get(jp))+"),";}
			mapData.dispMessage(res);		
		}
	}//dbgDispKnownJPsJPGs
	
	
	//return how many times this JP has been seen
	public int getCountJPSeen(Integer jp) {
		Integer res = jpSeenCount.get(jp);
		if (res == null) {res = 0;	}
		return res;
	}

}//class MonitorJpJpgrp
