package SOM_Strafford_PKG;

import java.util.*; 
import java.util.concurrent.ConcurrentSkipListMap;

//this class will monitor presence and counts of jpgroups and jps
//in training data for map
public class MonitorJpJpgrp {
	public static SOMMapManager mapMgr;
	//manage IO in this object
	private FileIOManager fileIO;
	////////////////////////////////////////
	//this information comes from the prospect and product map data
	//reference to jp ids and counts of all jps seen in all data
	private TreeMap<Integer, Integer> jpSeenCount;
	//reference to JP ids as key and counts of jps seen in product data
	private TreeMap<Integer, Integer> prodJpSeenCount;
	//reference to ids and counts of all jps seen in -event- data
	private TreeMap<Integer, Integer> jpEvSeenCount;
	//reference to ids and counts of all jps seen only in prospect record data (not including events)
	private TreeMap<Integer, Integer> jpPrspctSeenCount;
	//map from jp to idx in resultant feature vector
	private TreeMap<Integer, Integer> jpToFtrIDX;
	//map from jpgroup to integer
	private TreeMap<Integer, Integer> jpgToIDX;
	//map from jpgroup to jps corresponding to this group.
	private TreeMap<Integer, TreeSet <Integer>> jpgsToJps;
	//map from jps to owning jpgs
	private TreeMap<Integer, Integer> jpsToJpgs;// = new TreeMap<Integer, Integer>();
	//used by UI and also to map specific indexes to actual jps/jpgs
	private Integer[] jpByIdx = new Integer[] {1}, jpgrpsByIdx = new Integer[] {1};
	//used by UI and also to map specific indexes to actual jps/jpgs - this is solely for product-specific JPs.  some jps are not present in tc-taggings data/product data base
	private Integer[] prodJpByIdx = new Integer[] {1}, prodJpGrpsByIdx = new Integer[] {1};
	//# of jps seen = size of jpByIdx array == # of ftrs
	private int numFtrs;

	//list of job practice names keyed by jpID and jpgroup names keyed by jpgroupID	
	private TreeMap<Integer, String> jpNamesRaw, jpGrpNamesRaw;	//these are all the names known from reading all the data in - not all may be represented in actual data
	private TreeMap<Integer, String> jpNames, jpGrpNames;	
	
	public MonitorJpJpgrp(SOMMapManager _mapMgr) {
		mapMgr=_mapMgr;
		fileIO = new FileIOManager(mapMgr,"MonitorJpJpgrp");
		initAllStructs();
	}//ctor
	
	public Integer getJpByIdx(int idx) {return jpByIdx[idx];}
	//get jp from index for product-specific jps - UI interaction for displaying products on map
	public Integer getProdJpByIdx(int idx) {return prodJpByIdx[idx];}
	//get jpg from index for product-specific jpgs - ui interaction for displaying products on map
	public Integer getProdJpGrpByIdx(int idx) {return prodJpGrpsByIdx[idx];}
	
	public Integer getJpToFtrIDX(int jp) {return jpToFtrIDX.get(jp);}
	public int getNumFtrs() {return numFtrs;}

	public Integer getJpgFromJp(int jp) {return jpsToJpgs.get(jp);}
	
	private void incrJPCounts(Integer jp, TreeMap<Integer, Integer> map) {
		Integer count = map.get(jp);
		if(count==null) {count=0;}
		map.put(jp, count+1);
	}//incrJPCounts	
	
	private void initAllStructs() {		
		jpSeenCount = new TreeMap<Integer, Integer>(); 	//count of prospect training data records having jp
		jpEvSeenCount = new TreeMap<Integer, Integer>(); //count of prospect train records having jp only counting events
		jpPrspctSeenCount = new TreeMap<Integer, Integer>(); //count of prospect train records having jp only in base prospect record
		prodJpSeenCount = new TreeMap<Integer, Integer>();//count of products having jp
		jpsToJpgs = new TreeMap<Integer, Integer>();	//map from jpgs to jps
		jpgsToJps = new TreeMap<Integer, TreeSet <Integer>>();
		
		jpToFtrIDX = new TreeMap<Integer, Integer>();	
		jpgToIDX = new TreeMap<Integer, Integer>();	
		
		jpByIdx = new Integer[] {1};
		jpgrpsByIdx = new Integer[] {1};
		
		prodJpByIdx = new Integer[] {1};
		prodJpGrpsByIdx = new Integer[] {1};
		
		jpGrpNames = new TreeMap<Integer, String>();
		jpNames = new TreeMap<Integer, String>();	
	}
	
	//get names from raw data and set them
	public void setJpJpgrpNames(ArrayList<BaseRawData> _rawJpData, ArrayList<BaseRawData> _rawJpgData) {
		mapMgr.dispMessage("MonitorJpJpgrp","setJpJpgrpNames","Start setting names", MsgCodes.info5);
		jpNamesRaw = buildJPNames(_rawJpData);		
		jpGrpNamesRaw = buildJPNames(_rawJpgData);
		mapMgr.dispMessage("MonitorJpJpgrp","setJpJpgrpNames","Done setting names", MsgCodes.info5);
	}
	private TreeMap<Integer, String> buildJPNames(ArrayList<BaseRawData> _raw) {
		TreeMap<Integer, String> _nameList= new TreeMap<Integer, String>();
		for (BaseRawData rawJp : _raw) {
			jobPracticeData jp = (jobPracticeData)rawJp;
			_nameList.put(jp.getJPID(), jp.getName());			
		}
		return _nameList;
	}
	private void dbgShowNames(TreeMap<Integer, String> jpdat, String _name) {
		for(Integer jp : jpdat.keySet()) {mapMgr.dispMessage("MonitorJpJpgrp", "dbgShowNames : " + _name, ""+jp+" : "+ jpdat.get(jp), MsgCodes.info1);	}
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
		mapMgr.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","num jps seen in prospects  : " + tmpSetAllJpsJpgs.size(), MsgCodes.info1);
		
		//add all jps-jpgs from product data - will have data not seen in prospects		
		for(ProductExample ex : prdctMap.values()) {
			HashSet<Tuple<Integer,Integer>> tmpExSet = ex.getSetOfAllJpgJpData(); //tmpExSet is set of all jps/jpgs in ex
			for (Tuple<Integer,Integer> jpgJp : tmpExSet) {
				//only do the following if we wish to add these values to the map
				Integer jpg = jpgJp.x, jp=jpgJp.y;
				incrJPCounts(jp, jpSeenCount);
				incrJPCounts(jp, prodJpSeenCount);
				tmpSetAllJpsJpgs.add(jpgJp);
				tmpSetProductJpsJpgs.add(new Tuple<Integer,Integer>(jpgJp));
			}											//add all tuples to set already seen
		}//for each product		
		mapMgr.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","num jps seen in products  : " + tmpSetProductJpsJpgs.size()  + " | # seen now in tmpSetAllJpsJpgs :  " +tmpSetAllJpsJpgs.size()+ " | # seen only in products : " + (tmpSetAllJpsJpgs.size() - tmpSetProspectEventJpsJpgs.size()) + " | # of product examples : " + prdctMap.size(), MsgCodes.info1);
		
		TreeMap<Integer, TreeSet <Integer>> prodJpgsToJps = new TreeMap<Integer, TreeSet <Integer>>();
		//build jpsToJpgs and JpgsToJps structs
		for (Tuple<Integer,Integer> jpgJp : tmpSetAllJpsJpgs) {
			int jpg = jpgJp.x, jp=jpgJp.y;
			jpsToJpgs.put(jp, jpg);
			TreeSet <Integer> jpList = jpgsToJps.get(jpg);
			if (jpList==null) {jpList = new TreeSet <Integer>(); jpgsToJps.put(jpg, jpList);}
			jpList.add(jp);		
			TreeSet <Integer> prodJPList = prodJpgsToJps.get(jpg);
			if (prodJPList==null) {prodJPList = new TreeSet <Integer>(); prodJpgsToJps.put(jpg, jpList);}
			prodJPList.add(jp);		
		}

		jpByIdx = jpSeenCount.keySet().toArray(new Integer[0]);
		jpgrpsByIdx = jpgsToJps.keySet().toArray(new Integer[0]);

		for(int i=0;i<jpByIdx.length;++i) {
			jpToFtrIDX.put(jpByIdx[i], i);
			String name = jpNamesRaw.get(jpByIdx[i]);
			if (name==null) {mapMgr.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","!!!!!!!!!!!!!!!! null name for jp : " + jpByIdx[i], MsgCodes.warning2);name="";}
			jpNames.put(jpByIdx[i], name);
		}
		for(int i=0;i<jpgrpsByIdx.length;++i) {
			jpgToIDX.put(jpgrpsByIdx[i], i);
			String name = jpGrpNamesRaw.get(jpgrpsByIdx[i]);
			if (name==null) {mapMgr.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","!!!!!!!!!!!!!!!! null name for jpgrp : " + jpgrpsByIdx[i], MsgCodes.warning2);name="";}
			jpGrpNames.put(jpgrpsByIdx[i], name);
		}
		
		prodJpByIdx = prodJpSeenCount.keySet().toArray(new Integer[0]);
		prodJpGrpsByIdx = prodJpgsToJps.keySet().toArray(new Integer[0]);
				
		numFtrs = jpSeenCount.size();//needs to be same size as jpByIdx.length
		mapMgr.setUI_JPMaxVals(jpgrpsByIdx.length,jpByIdx.length); 
		mapMgr.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","numFtrs : " + numFtrs, MsgCodes.info1);
	}//setJPDataFromProspectData	
	
	//name, jp and ftr idx of jp
	public String getJpByIdxStr(int uiIDX) {
		int idx = uiIDX % jpByIdx.length, jp=jpByIdx[idx];
		String name = jpNames.get(jp);
		if(name==null) {name="UNK";}
		return "" +name+ " (jp:"+ jp + ",idx:" +idx+ ")";
	}
	//name, jp and ftr idx of jp
	public String getJpGrpByIdxStr(int uiIDX) {
		int idx = uiIDX % jpgrpsByIdx.length, jpg=jpgrpsByIdx[idx];
		String name = jpGrpNames.get(jpg);
		if(name==null) {name="UNK";}
		return "" +name+ " (jpg:"+ jpg + ",idx:" +idx+ ")";
	}	
	//name, jp and ftr idx of jp for product-specific jpgs and jps
	public String getProdJpByIdxStr(int uiIDX) {
		int idx = uiIDX % prodJpByIdx.length, jp=prodJpByIdx[idx];
		String name = jpNames.get(jp);
		if(name==null) {name="UNK";}
		return "" +name+ " (jp:"+ jp + ",idx:" +idx+ ")";
	}	
	//name, jp and ftr idx of jp
	public String getProdJpGrpByIdxStr(int uiIDX) {
		int idx = uiIDX % prodJpGrpsByIdx.length, jpg=prodJpGrpsByIdx[idx];
		String name = jpGrpNames.get(jpg);
		if(name==null) {name="UNK";}
		return "" +name+ " (jpg:"+ jpg + ",idx:" +idx+ ")";
	}
	
	public int getLenJpByIdx() {		return jpByIdx.length;	}//# of jps seen
	public int getLenJpGrpByIdx(){	return jpgrpsByIdx.length; }//# of jpgrps seen
	
	//this will return the appropriate jpgrp for the given jpIDX (ftr idx)
	public int getUI_JPGrpFromJP(int jpIdx, int curVal) {
		if(jpsToJpgs.size() < jpIdx) {return curVal;}
		int jpgrp = jpsToJpgs.get(jpByIdx[jpIdx]);
		return jpgToIDX.get(jpgrp);		
	}
	//this will return the first(lowest) jp for a particular jpgrp
	public int getUI_FirstJPFromJPG(int jpgIdx, Integer curVal) {
		if(jpgsToJps.size() < jpgIdx) {return curVal;}
		mapMgr.dispMessage("MonitorJpJpgrp","getUI_FirstJPFromJPG",  "jpgIDX : " + jpgIdx + " Curval : " + curVal, MsgCodes.info1);
		TreeSet <Integer> jpList = jpgsToJps.get(jpgrpsByIdx[jpgIdx]);
		if (jpList.contains(jpByIdx[curVal])) {			
			mapMgr.dispMessage("MonitorJpJpgrp","getUI_FirstJPFromJPG",  "contains curVal : jpgIDX : " + jpgIdx + " Curval : " + curVal, MsgCodes.info1);			
			return curVal;		}//if in current jpgrp already, then return current value
		else {
			mapMgr.dispMessage("MonitorJpJpgrp","getUI_FirstJPFromJPG",  "doesn't contain curVal : jpgIDX : " + jpgIdx + " Curval : " + curVal, MsgCodes.info1);			
			return jpToFtrIDX.get(jpList.first());		}
	}
	
	/////////////////////////////
	//Save this object's data
	public void saveAllData(String fileName) {
		mapMgr.dispMessage("MonitorJpJpgrp","saveAllData","Start to save all " + jpByIdx.length + " jp's worth of data in :"+fileName, MsgCodes.info5);
		ArrayList<String> csvResTmp = new ArrayList<String>();		
		int numJps = jpByIdx.length;
		int numProdJps = prodJpByIdx.length;
		String tmp = "Num Jps=," + numJps+ ",Num  JpGrps=,"+jpgrpsByIdx.length+",Num ProdJPs=,"+ numProdJps+", Num Prod JPGrps=,"+prodJpGrpsByIdx.length;
		csvResTmp.add(tmp);
		tmp = "Jp,JpIDX,Jp Name,Jpgrp,JpgrpIDX,Jpgrp Name,# JpSeen, # JpEvSeen, # JpPrspctSeen";
		csvResTmp.add(tmp);
		for (int idx = 0;idx<numJps;++idx) {
			int jp = jpByIdx[idx];
			int jpGrp = jpsToJpgs.get(jp);
			int jpGrpIdx = jpgToIDX.get(jpGrp);
			tmp = ""+jp+","+idx+","+ getNameNullChk(jp,jpNames);
			tmp +=","+jpGrp+","+jpGrpIdx+","+ getNameNullChk(jpGrp,jpGrpNames)+","+getCountNullChk(jp,jpSeenCount)+","+getCountNullChk(jp,jpEvSeenCount)+","+getCountNullChk(jp,jpPrspctSeenCount);
			csvResTmp.add(tmp);
		}		
		csvResTmp.add("---------,-----------,product-specific,values,---------,-----------");
		for (int idx = 0;idx < numProdJps;++idx) {
			int prodJP = prodJpByIdx[idx];
			int prodJpGrp = jpsToJpgs.get(prodJP);
			int prodJpGrpIdx = jpgToIDX.get(prodJpGrp);
			tmp=""+prodJP+","+idx+","+ getNameNullChk(prodJP,jpNames) + ","+ prodJpGrp + "," + prodJpGrpIdx+","+ getNameNullChk(prodJpGrp,jpGrpNames)+","+getCountNullChk(prodJP,prodJpSeenCount);
			csvResTmp.add(tmp);
		}
		fileIO.saveStrings(fileName, csvResTmp);	
		mapMgr.dispMessage("MonitorJpJpgrp","saveAllData","Finished saving all jp data in :"+fileName, MsgCodes.info5);
	}//saveAllData
	
	private String getNameNullChk(Integer key, TreeMap<Integer, String> jpdat) {
		String res = jpdat.get(key);
		return (res == null ? "" : res);
		
	}//getNameNullChk
	
	private Integer getCountNullChk(Integer key, TreeMap<Integer, Integer> counts) {
		Integer res = counts.get(key);
		return (res == null ? 0 : res);
	}
	
	public String getJPNameFromJP(Integer jp) {return getNameNullChk(jp, jpNames);}
	
	/////////////////////////////
	//Load this object's data
	public void loadAllData(String fileName) {
		mapMgr.dispMessage("MonitorJpJpgrp","loadAllData","Start to load all jp data in :"+fileName, MsgCodes.info5);
		initAllStructs();//clear everything out
		jpNamesRaw = new TreeMap<Integer, String>();	
		jpGrpNamesRaw = new TreeMap<Integer, String>();
		HashSet<Integer> tmpProdJpgrs = new HashSet<Integer>();
		String[] csvLoadRes = fileIO.loadFileIntoStringAra(fileName, "MonitorJpJpgrp file loaded", "MonitorJpJpgrp File Failed to load");
		int numJps = 0, numJpgs = 0;
		boolean isProd = false;
		for(int i=0;i<csvLoadRes.length;++i) {
			String line = csvLoadRes[i];
			String[] vals = line.split(mapMgr.csvFileToken);
			if(i==0) {//first line has counts of jps and jpgs
				jpByIdx = new Integer[Integer.parseInt(vals[1])];		
				jpgrpsByIdx = new Integer[Integer.parseInt(vals[3])];	
				prodJpByIdx = new Integer[Integer.parseInt(vals[5])];	
				prodJpGrpsByIdx = new Integer[Integer.parseInt(vals[7])];
			} 
			else if (i==1) {continue;}								//header string
			else if (vals[0].contains("---------")){
				isProd = true;
				continue;}		
			else {//here record has all values i >= 2
				if (!isProd) {//found in prospects
					//layout"Jp, JpIDX, Jp Name, Jpgrp, JpgrpIDX, Jpgrp Name, JpSeen, JpEvSeen, JpPrspctSeen";
					int jp = Integer.parseInt(vals[0]),
						jpFtrIDX = Integer.parseInt(vals[1]),
						jpgrp = Integer.parseInt(vals[3]),
						jpgrpIDX = Integer.parseInt(vals[4]),
						jpSeen = Integer.parseInt(vals[6]),
						jpEvSeen = Integer.parseInt(vals[6]),
						jpPrspctSeen = Integer.parseInt(vals[6]);
					String jpName = vals[2], jpgrpName = vals[5];
					jpByIdx[jpFtrIDX] = jp;
					jpToFtrIDX.put(jp, jpFtrIDX);			
					jpSeenCount.put(jp, jpSeen);
					jpEvSeenCount.put(jp, jpEvSeen);
					jpPrspctSeenCount.put(jp, jpPrspctSeen);
					jpsToJpgs.put(jp, jpgrp);
					jpNamesRaw.put(jp, jpName);					
					TreeSet <Integer> jpsAtJpg = jpgsToJps.get(jpgrp);
					if (jpsAtJpg == null) {jpsAtJpg = new TreeSet <Integer>(); }
					jpsAtJpg.add(jp);
					jpgsToJps.put(jpgrp,jpsAtJpg);
					jpGrpNamesRaw.put(jpgrp, jpgrpName);
					jpgToIDX.put(jpgrp,jpgrpIDX);
					jpgrpsByIdx[jpgrpIDX]=jpgrp;
				} else {//jps found in products product records - assuming no jps only found in products
					int prodJP = Integer.parseInt(vals[0]),
						prodAraIDX = Integer.parseInt(vals[1]),
						prodJpg = Integer.parseInt(vals[3]),
						prodJpGrpIDX = Integer.parseInt(vals[4]),
						prodJpSeen = Integer.parseInt(vals[6]);
					prodJpByIdx[prodAraIDX]=prodJP;
					prodJpGrpsByIdx[prodJpGrpIDX] = prodJpg;
					prodJpSeenCount.put(prodJP, prodJpSeen);
					tmpProdJpgrs.add(prodJpg);					
				}
			}
		}			
		mapMgr.setUI_JPMaxVals(jpgrpsByIdx.length,jpByIdx.length); 
		mapMgr.dispMessage("MonitorJpJpgrp","loadAllData","Finished loading all jp data in :"+fileName + " Which has :"+ jpByIdx.length + " jps.", MsgCodes.info5);
	}//loadAllData
	
	//debugging function to display all unique jps seen in data
	public void dbgShowUniqueJPsSeen() {
		mapMgr.dispMessage("MonitorJpJpgrp","dbgShowUniqueJPsSeen","All Jp's seen : ", MsgCodes.info1);
		for (Integer key : jpSeenCount.keySet()) {mapMgr.dispMessage("MonitorJpJpgrp","dbgShowUniqueJPsSeen","JP : "+ String.format("%3d",key) + "   |Count : " + String.format("%6d",jpSeenCount.get(key)) + "\t|Ftr IDX : " + String.format("%3d",jpToFtrIDX.get(key)) + "\t|Owning JPG : " + String.format("%2d",jpsToJpgs.get(key)), MsgCodes.info1);}
		mapMgr.dispMessage("MonitorJpJpgrp","dbgShowUniqueJPsSeen","Number of unique JP's seen : " + jpSeenCount.size(), MsgCodes.info1);
		dbgDispKnownJPsJPGs();
	}//dbgShowUniqueJPsSeen
	
	public void dbgDispKnownJPsJPGs() {
		mapMgr.dispMessage("MonitorJpJpgrp","dbgDispKnownJPsJPGs","JPGs seen : (jp : count : ftridx) :", MsgCodes.info1);
		for (Integer jpgrp : jpgsToJps.keySet()) {
			String res = "JPG : " + String.format("%3d", jpgrp);
			TreeSet <Integer> jps = jpgsToJps.get(jpgrp);			
			for (Integer jp : jps) {res += " ("+String.format("%3d", jp)+" :"+String.format("%6d", jpSeenCount.get(jp))+" : "+ String.format("%3d", jpToFtrIDX.get(jp), MsgCodes.info1)+"),";}
			mapMgr.dispMessage("MonitorJpJpgrp","dbgDispKnownJPsJPGs",res, MsgCodes.info1);		
		}
	}//dbgDispKnownJPsJPGs
	
	//return how many times this JP has been seen
	public int getCountJPSeen(Integer jp) {
		Integer res = jpSeenCount.get(jp);
		if (res == null) {res = 0;	}
		return res;
	}
	
	public String toString() {
		String res ="";
		int numJps = jpByIdx.length;		
		res += "Num Jps = " + numJps+ " | Num  JpGrps = "+jpgrpsByIdx.length +"\n";
		res += "Jp\tJpIDX\tJp Name\t\tJpgrp\tJpgrpIDX\tJpgrp Name\t\tJpSeen\tJpEvSeen\tJpPrspctSeen\n";
		for (int idx = 0;idx<numJps;++idx) {
			int jp = jpByIdx[idx];
			int jpGrp = jpsToJpgs.get(jp);
			int jpGrpIdx = jpgToIDX.get(jpGrp);
			res += ""+jp+"\t|"+idx+"\t|"+ jpNames.get(jp);
			res +="\t|"+jpGrp+"\t|"+jpGrpIdx+"\t|"+ jpGrpNames.get(jpGrp)+"\t|"+jpSeenCount.get(jp)+"\t|"+jpEvSeenCount.get(jp)+"\t|"+jpPrspctSeenCount.get(jp)+"\n";
		}		
		return res;
	}

}//class MonitorJpJpgrp
