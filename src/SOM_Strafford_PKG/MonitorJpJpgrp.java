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
	//reference to ids and counts of all jps seen in prospect/customer -event- data (not counting source "Events" which may or may not correspond to actual events)
	private TreeMap<Integer, Integer> jpProspectSeenCounts;
	//reference to ids and counts of all jps seen in source event records
	private HashMap<EvtDataType, TreeMap<Integer, Integer>> jpPerEventSeenCount;
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
		jpProspectSeenCounts = new TreeMap<Integer, Integer>(); //count of prospect train records having jp counting events
		jpPerEventSeenCount = new HashMap<EvtDataType, TreeMap<Integer, Integer>>(); //count of prospect train records having jp only seen in source events
		int numEvTypes = EvtDataType.getNumVals();
		for (int i=0;i<numEvTypes;++i) {
			jpPerEventSeenCount.put(EvtDataType.getVal(i), new TreeMap<Integer, Integer>());			
		}//for
		
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
	}//initAllStructs()
	
	//get names from raw data and set them
	public void setJpJpgrpNames(ArrayList<BaseRawData> _rawJpData, ArrayList<BaseRawData> _rawJpgData) {
		mapMgr.dispMessage("MonitorJpJpgrp","setJpJpgrpNames","Start setting names", MsgCodes.info5);
		jpNamesRaw = buildJPNames(_rawJpData);		
		jpGrpNamesRaw = buildJPNames(_rawJpgData);
		mapMgr.dispMessage("MonitorJpJpgrp","setJpJpgrpNames","Done setting names", MsgCodes.info5);
	}//setJpJpgrpNames
	
	private TreeMap<Integer, String> buildJPNames(ArrayList<BaseRawData> _raw) {
		TreeMap<Integer, String> _nameList= new TreeMap<Integer, String>();
		for (BaseRawData rawJp : _raw) {
			jobPracticeData jp = (jobPracticeData)rawJp;
			_nameList.put(jp.getJPID(), jp.getName());			
		}
		return _nameList;
	}//buildJPNames
	
	private void dbgShowNames(TreeMap<Integer, String> jpdat, String _name) {
		for(Integer jp : jpdat.keySet()) {mapMgr.dispMessage("MonitorJpJpgrp", "dbgShowNames : " + _name, ""+jp+" : "+ jpdat.get(jp), MsgCodes.info1);	}
	}//dbgShowNames
	
	//find counts of each kind of event reference for each jp
	private void buildJpPresentMaps(ConcurrentSkipListMap<String, ProspectExample> map,HashSet<Tuple<Integer,Integer>> tmpSetAllJpsJpgs) {
		int numEventTypes = EvtDataType.getNumVals();
		//for every prospect, look at every jp
		for (ProspectExample ex : map.values()) {
			HashSet<Tuple<Integer,Integer>> tmpExSet = ex.getSetOfAllJpgJpData(); //tmpExSet is set of all jps/jpgs in ex
			for (Tuple<Integer,Integer> jpgJp : tmpExSet) {
				Integer jpg = jpgJp.x, jp=jpgJp.y;
				incrJPCounts(jp, jpSeenCount);
				//idxs : 0==if in an order; 1==if in an opt, 2 == if is in link, 3==if in source event, 4 if in any non-source event (denotes action by customer),5 if in any event including source
				boolean[] recMmbrship = ex.hasJP(jp);
				if (recMmbrship[numEventTypes+1]){incrJPCounts(jp,jpProspectSeenCounts);}	//idx should always be # events  + 1  - this is idx of whether jp is present in any non-source event
				for (int i=0;i<numEventTypes;++i) {
					if (recMmbrship[i]){incrJPCounts(jp,jpPerEventSeenCount.get(EvtDataType.getVal(i)));}		
				}
				tmpSetAllJpsJpgs.add(jpgJp);
			}											//add all tuples to set already seen
		}//for each prospect		
	}//buildJpPresentMaps
			
	//this will set the current jp->jpg data maps based on passed customer and prospect data maps
	//When acquiring new data, this must be performed after all data is loaded, but before
	//the prospect data is finalized and actual map is built due to the data finalization 
	//requiring a knowledge of the entire dataset to build weights appropriately
	public void setJPDataFromExampleData(ConcurrentSkipListMap<String, ProspectExample> customerMap, ConcurrentSkipListMap<String, ProspectExample> prospectMap, ConcurrentSkipListMap<String, ProductExample> prdctMap) {
		initAllStructs();
		mapMgr.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","State after init : " + this.toString(), MsgCodes.info1);
		//rebuild all jp->jpg mappings based on customer and prospect data
		HashSet<Tuple<Integer,Integer>> tmpSetCustJpsJpgs = new HashSet<Tuple<Integer,Integer>>(), 
				tmpSetPrspctJpsJpgs = new HashSet<Tuple<Integer,Integer>>(),
				tmpSetAllJpsJpgs =  new HashSet<Tuple<Integer,Integer>>();
		buildJpPresentMaps(customerMap,tmpSetCustJpsJpgs);
		int numCustJps = tmpSetCustJpsJpgs.size();
		mapMgr.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","# customer records : " + customerMap.size() + " | # jps seen in customers : " + numCustJps, MsgCodes.info1);
		buildJpPresentMaps(customerMap,tmpSetPrspctJpsJpgs);
		int numPrspctJps = tmpSetPrspctJpsJpgs.size();
		mapMgr.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","# prospect records : " + prospectMap.size() + " | # jps seen in prospects : " + numPrspctJps, MsgCodes.info1);
		tmpSetAllJpsJpgs.addAll(tmpSetPrspctJpsJpgs);
		tmpSetAllJpsJpgs.addAll(tmpSetPrspctJpsJpgs);
		int numCustPrspctJps = tmpSetAllJpsJpgs.size();
		mapMgr.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","# cust & prspct records : " + (customerMap.size() + prospectMap.size()) + " | # jps seen in customers and prospects : " + numCustPrspctJps, MsgCodes.info1);
	
		
		//add contributions from products
		HashSet<Tuple<Integer,Integer>> tmpSetProductJpsJpgs = new HashSet<Tuple<Integer,Integer>>();//only seen in product records
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
		int numProdJps = tmpSetProductJpsJpgs.size();
		mapMgr.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","# product records : " + prdctMap.size() + " | # jps seen in products : " + numProdJps + " | Total # jps seen in all customers, prospects and products : " + tmpSetAllJpsJpgs.size(), MsgCodes.info1);
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

		//these are jps
		//TODO this should change to jpPerEventSeenCount.get(EvtDataType.getVal(EvtDataType.Order)) ? - feature training should only be on jps that have orders maybe?
		
		
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
		
		//make all null records in prospect counts == 0
		for(int jp : jpSeenCount.keySet()) {if(jpProspectSeenCounts.get(jp)==null) {jpProspectSeenCounts.put(jp,0);}}		
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
		tmp = "Jp,JpIDX,Jp Name,Jpgrp,JpgrpIDX,Jpgrp Name,# JpSeen, # JpProspectsSeen, ";//# JpSourceSeen";
		int numEventTypes = EvtDataType.getNumVals();
		for(int i=0;i<numEventTypes;++i) {			tmp+= "# Jp"+EvtDataType.getVal(i).name()+"Seen,";		}
		csvResTmp.add(tmp);
		for (int idx = 0;idx<numJps;++idx) {
			int jp = jpByIdx[idx];
			int jpGrp = jpsToJpgs.get(jp);
			int jpGrpIdx = jpgToIDX.get(jpGrp);
			tmp = ""+jp+","+idx+","+ getNameNullChk(jp,jpNames);
			tmp +=","+jpGrp+","+jpGrpIdx+","+ getNameNullChk(jpGrp,jpGrpNames)+","+getCountNullChk(jp,jpSeenCount)+","+getCountNullChk(jp,jpProspectSeenCounts)+",";			
			for(int i=0;i<numEventTypes;++i) {			tmp+= ""+getCountNullChk(jp,jpPerEventSeenCount.get(EvtDataType.getVal(i))) +",";}
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
		int numEventTypes = EvtDataType.getNumVals();
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
					//layout"Jp, JpIDX, Jp Name, Jpgrp, JpgrpIDX, Jpgrp Name, JpSeen, JpPrspctSeen, <for each type of even count of jp's seen, following EvtDataType order>";
					int jp = Integer.parseInt(vals[0]),
						jpFtrIDX = Integer.parseInt(vals[1]),
						jpgrp = Integer.parseInt(vals[3]),
						jpgrpIDX = Integer.parseInt(vals[4]),
						jpSeen = Integer.parseInt(vals[6]),
						jpPrspctSeen = Integer.parseInt(vals[7]);
					String jpName = vals[2], jpgrpName = vals[5];
					jpByIdx[jpFtrIDX] = jp;
					jpToFtrIDX.put(jp, jpFtrIDX);			
					jpSeenCount.put(jp, jpSeen);
					jpProspectSeenCounts.put(jp, jpPrspctSeen);
					for(int j=0;j<numEventTypes;++j) {		
						TreeMap<Integer, Integer> PerEvtCountMap = jpPerEventSeenCount.get(EvtDataType.getVal(j));
						PerEvtCountMap.put(jp, Integer.parseInt(vals[8+j]));
					}
					
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
		int numJps = jpByIdx.length;		
		if (numJps < 2) {return "JpJpg Monitoring Construct not built yet";}//not made yet
		String res ="";
		String sep = "|";//use for display separator - change to comma for csv-suitable output
		int frmtLen = 50, frmtLenJPG = 21;
		String frmtStr = "%-"+(frmtLen+1) + "s",frmtStrJPG = "%-"+(frmtLenJPG+1) + "s";
			
		res += "\nNum Jps = " + numJps+ " "+sep+" Num  JpGrps = "+jpgrpsByIdx.length +"\n";
		res += " Jp "+sep+"JpIDX"+sep + String.format(frmtStr,"Jp Name") +sep+" Jpgrp "+sep+" JpgIDX "+sep + String.format(frmtStrJPG,"Jpgrp Name") +" "+sep+"# Jp exs"+sep+"# in Cust"+sep;
		int numEventTypes = EvtDataType.getNumVals();
		for(int i=0;i<numEventTypes;++i) {		res+= String.format("# %6s"+sep,EvtDataType.getVal(i).name());		}
		res +="\n";
		int[] ttlNumJpOcc = new int[numEventTypes+2];
		
		//for each jpgroup, save all counts
		TreeMap<Integer,Integer[]> countsPerJPG = new TreeMap<Integer,Integer[]>();
		
		for (int idx = 0;idx<numJps;++idx) {
			int jp = jpByIdx[idx];
			int jpGrp = jpsToJpgs.get(jp);
			int jpGrpIdx = jpgToIDX.get(jpGrp);
			Integer[] perJPGrpCounts = countsPerJPG.get(jpGrp);
			if(null==perJPGrpCounts) {
				perJPGrpCounts = new Integer[ttlNumJpOcc.length]; 
				countsPerJPG.put(jpGrp,perJPGrpCounts); 
				for (int i=0;i<perJPGrpCounts.length;++i) {perJPGrpCounts[i]=0;}
			}
			String jpName =  jpNames.get(jp), jpgName = jpGrpNames.get(jpGrp);
			if(jpName==null) {jpName = jpNamesRaw.get(jp);}
			if(jpgName==null) {jpgName = jpGrpNamesRaw.get(jpGrp);}			
			Integer jpSeen = jpSeenCount.get(jp);
			Integer jpPrsSeen = jpProspectSeenCounts.get(jp);		
			
			res += String.format("%3d "+sep+" %3d "+sep, jp,idx)+ String.format(frmtStr,jpName.substring(0, (jpName.length() < frmtLen ? jpName.length() : frmtLen)));			
			ttlNumJpOcc[0]+=jpSeen;		
			ttlNumJpOcc[1]+=jpPrsSeen;	
			perJPGrpCounts[0] += jpSeen;	
			perJPGrpCounts[1]+=jpPrsSeen;	
			res += sep+" "+String.format("%5d "+sep+" %6d "+sep, jpGrp, jpGrpIdx)+ String.format(frmtStrJPG,jpgName.substring(0, (jpgName.length() < frmtLenJPG ? jpgName.length() : frmtLenJPG))) + String.format(" "+sep+"%7d "+sep+" %7d",jpSeen,jpPrsSeen);			
			for(int i=0;i<numEventTypes;++i) {	
				Integer count = jpPerEventSeenCount.get(EvtDataType.getVal(i)).get(jp);
				if(count==null) {count=0;}
				ttlNumJpOcc[i+2]+=count;
				perJPGrpCounts[i+2]+=count;
				res+= String.format(" "+sep+"%7d",count);
			}			
			res +=" " + sep+"\n";
		}		
		res += "Ttls : Seen : " + ttlNumJpOcc[0] + " "+sep+" Cust/Prspct : "+ ttlNumJpOcc[1];
		for(int i=0;i<numEventTypes;++i) {	
			res+= " "+sep+" "+EvtDataType.getVal(i).name()+" : " + ttlNumJpOcc[2+i];	
		}
		res +="\n";
		
		//show how many event-based records there are for each jp group
		int numJPGs = jpgrpsByIdx.length;
		ttlNumJpOcc = new int[numEventTypes+2];
		res +="\n"+sep+" Jpgrp "+sep + String.format(frmtStrJPG,"Jpgrp Name") +" "+sep+"#Jpg exs"+sep+"#in Cust"+sep;
		for(int i=0;i<numEventTypes;++i) {		res+= String.format("# %6s"+sep,EvtDataType.getVal(i).name());		}
		res +="\n";
		for (int jpGrpIdx = 0;jpGrpIdx<numJPGs;++jpGrpIdx) {
			int jpGrp = jpgrpsByIdx[jpGrpIdx];
			String jpgName = jpGrpNames.get(jpGrp);
			if(jpgName==null) {jpgName = jpGrpNamesRaw.get(jpGrp);}			
			res +=sep+" "+String.format("%5d "+sep, jpGrp)+String.format(frmtStrJPG,jpgName.substring(0, (jpgName.length() < frmtLenJPG ? jpgName.length() : frmtLenJPG)));
			Integer[] counts = countsPerJPG.get(jpGrp);
			for(int i=0;i<counts.length;++i) {
				ttlNumJpOcc[i]+=counts[i];
				res+= String.format(" "+sep+"%7d",counts[i]);
			}			
			res +=" "+sep+"\n";
		}
		res += "\nTtls : JPGroup Seen : " + ttlNumJpOcc[0] + " "+sep+" JPGroup Cust/Prspct : "+ ttlNumJpOcc[1];
		for(int i=0;i<numEventTypes;++i) {	
			res+= " "+sep+" "+EvtDataType.getVal(i).name()+" : " + ttlNumJpOcc[2+i];	
		}
		res +="\n";
		return res;
	}//toString

}//class MonitorJpJpgrp
