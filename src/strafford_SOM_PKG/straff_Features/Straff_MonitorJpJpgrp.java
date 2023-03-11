package strafford_SOM_PKG.straff_Features;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;

import base_Utils_Objects.io.file.FileIOManager;
import base_Utils_Objects.io.messaging.MessageObject;
import base_Utils_Objects.io.messaging.MsgCodes;
import base_Math_Objects.vectorObjs.tuples.Tuple;
import base_SOM_Objects.som_managers.SOM_MapManager;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.Straff_JobPracticeData;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.base.Straff_BaseRawData;
import strafford_SOM_PKG.straff_SOM_Examples.Straff_EvtDataType;
import strafford_SOM_PKG.straff_SOM_Examples.products.Straff_ProductExample;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.Straff_ProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
import strafford_SOM_PKG.straff_SOM_Mapping.exampleManagers.Straff_SOMProductManager;
import strafford_SOM_PKG.straff_SOM_Mapping.exampleManagers.Straff_SOMTruePrspctManager;
import strafford_SOM_PKG.straff_SOM_Mapping.exampleManagers.base.Straff_SOMCustPrspctManager_Base;

//this class will monitor presence and counts of jpgroups and jps
//in training data for map
public class Straff_MonitorJpJpgrp {
	private static Straff_SOMMapManager mapMgr;
	//object to display to screen and potentially print to logs
	public MessageObject msgObj;
	//use for display separator - change to comma for csv-suitable output
	private static final String str_sep = "|";
	
	//indexes in aggregate structures to manage all jps and jpgs seen in various types of data
	private static final int 
		allExJpsIDX	= 0,		//jp and jpg data covering all examples - products, customers and true prospects
		custExJpsIDX = 1,		//jp/jpg for all customer and true prospects
		prospectExJpsIDX = 2,	//jp/jpg for all customer and true prospects
		productExJpsIDX = 3;	//jp/jpg data for all products
	public static final String[] typeOfJpStrs = new String[] {"All", "Customer", "Prospect", "Product"};
	public static final int[] typeOfJpIDXs = new int[] {allExJpsIDX,custExJpsIDX, prospectExJpsIDX, productExJpsIDX};
	private int[] numAllFtrTypes;
	//map holding all allJP_JPG_Data for each type of record, and 
	private TreeMap<String, JP_JPG_Data> mapOfJPData;
	
	//JP_JPG_Data object used to build training features - the jps defined by these records are the ones that will be used to train the SOM
	private JP_JPG_Data trainingJpJpgData;
	private allJP_JPG_Data allJpJpgData;
	//idx of JP_JPG_Data type array used as training data feature descriptors
	private int trainJpJpgKey = productExJpsIDX;
	
	public Straff_MonitorJpJpgrp(Straff_SOMMapManager _mapMgr) {
		mapMgr=_mapMgr;msgObj = MessageObject.getInstance();
		mapOfJPData = new TreeMap<String, JP_JPG_Data>();
		mapOfJPData.put(typeOfJpStrs[allExJpsIDX], new allJP_JPG_Data(this,typeOfJpStrs[allExJpsIDX]));
		mapOfJPData.put(typeOfJpStrs[custExJpsIDX], new prspctJP_JPG_Data(this,typeOfJpStrs[custExJpsIDX]));
		mapOfJPData.put(typeOfJpStrs[prospectExJpsIDX], new prspctJP_JPG_Data(this,typeOfJpStrs[prospectExJpsIDX]));
		mapOfJPData.put(typeOfJpStrs[productExJpsIDX], new productJP_JPG_Data(this,typeOfJpStrs[productExJpsIDX]));
		
		trainingJpJpgData = mapOfJPData.get(typeOfJpStrs[trainJpJpgKey]);
		allJpJpgData = (allJP_JPG_Data) mapOfJPData.get(typeOfJpStrs[allExJpsIDX]);
		
		numAllFtrTypes = new int[typeOfJpStrs.length];
		initAllStructs();
	}//ctor
	
	private void initAllStructs() {	
		for(String key :typeOfJpStrs) {	mapOfJPData.get(key).init();}
		numAllFtrTypes = new int[typeOfJpStrs.length];
	}//initAllStructs()
	
	//get names from raw data and set them
	public void setJpJpgrpNames(ArrayList<Straff_BaseRawData> _rawJpData, ArrayList<Straff_BaseRawData> _rawJpgData) {
		msgObj.dispMessage("MonitorJpJpgrp","setJpJpgrpNames","Start setting names", MsgCodes.info5);
		for(String key :typeOfJpStrs) {	mapOfJPData.get(key).setRawNames(_rawJpData, _rawJpgData);}
		msgObj.dispMessage("MonitorJpJpgrp","setJpJpgrpNames","Done setting names", MsgCodes.info5);
	}//setJpJpgrpNames
	
	//copy raw names out to all JP_JPG_Data structs
	private void copyRawNames() {
		//copy raw names from all object to each indiv object
		for(int i=1;i<typeOfJpStrs.length;++i) {allJpJpgData.copyNamesToJpJpgData(mapOfJPData.get(typeOfJpStrs[i]));}
	}
			
	
	//this will set the current jp->jpg data maps based on passed customer and prospect data maps
	//When acquiring new data, this must be performed after all data is loaded, but before
	//the prospect data is finalized and actual map is built due to the data finalization 
	//requiring a knowledge of the entire dataset to build weights appropriately
	public void setJPDataFromExampleData(Straff_SOMCustPrspctManager_Base customerMapper, Straff_SOMTruePrspctManager prospectMapper, Straff_SOMProductManager prdctMapper) {
		initAllStructs();
		msgObj.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","State after init : " + this.toString(), MsgCodes.info1);
		
		//rebuild all jp->jpg mappings based on customer and prospect data
		@SuppressWarnings("unchecked")
		HashSet<Tuple<Integer,Integer>>[] tmpSetJpsJpgs = new HashSet[typeOfJpStrs.length];
		for(int i=0;i<tmpSetJpsJpgs.length;++i) {	tmpSetJpsJpgs[i]= new HashSet<Tuple<Integer,Integer>>();}
		
		//using each individual class
		mapOfJPData.get(typeOfJpStrs[custExJpsIDX]).buildJpPresentMaps(customerMapper.getExampleMap(), tmpSetJpsJpgs[custExJpsIDX], tmpSetJpsJpgs[allExJpsIDX]);
		mapOfJPData.get(typeOfJpStrs[prospectExJpsIDX]).buildJpPresentMaps(prospectMapper.getExampleMap(), tmpSetJpsJpgs[prospectExJpsIDX], tmpSetJpsJpgs[allExJpsIDX]);		
		mapOfJPData.get(typeOfJpStrs[productExJpsIDX]).buildJpPresentMaps(prdctMapper.getExampleMap(), tmpSetJpsJpgs[productExJpsIDX], tmpSetJpsJpgs[allExJpsIDX]);
		//now aggregate all data into "all data" object
		allJpJpgData.aggregateAllData(mapOfJPData);
		
		//by here all jps in all customer, true prospect and product data is known
		for(int key :typeOfJpIDXs) {	mapOfJPData.get(typeOfJpStrs[key]).finishBuildJPStructs(tmpSetJpsJpgs[key]);	}		
		//}
		int numCusts = customerMapper.getExampleMap().size();
		int numPspcts = prospectMapper.getExampleMap().size();
		int numProducts = prdctMapper.getExampleMap().size();
		msgObj.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","# customer records : " + numCusts + " | # jps seen in customers : " + tmpSetJpsJpgs[custExJpsIDX].size(), MsgCodes.info1);
		msgObj.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","# prospect records : " + numPspcts + " | # jps seen in prospects : " + tmpSetJpsJpgs[prospectExJpsIDX].size(), MsgCodes.info1);
		msgObj.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","# cust & prspct records : " + (numCusts + numPspcts) + " | # jps seen in customers and prospects : " + tmpSetJpsJpgs[allExJpsIDX].size(), MsgCodes.info1);
		msgObj.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","# product records : " + numProducts + " | # jps seen in products : " + tmpSetJpsJpgs[productExJpsIDX].size() + " | Total # jps seen in all customers, prospects and products : " + tmpSetJpsJpgs[custExJpsIDX].size(), MsgCodes.info1);
		setUI_MaxValsForFtrAndAllSeenJpJpg();
		for(int i=0;i<typeOfJpStrs.length;++i) {
			String key = typeOfJpStrs[i];
			numAllFtrTypes[i]=mapOfJPData.get(key).getNumFtrs();
			msgObj.dispMessage("MonitorJpJpgrp","setJPDataFromExampleData","# numFtrs seen in "+key+" examples : " + numAllFtrTypes[i], MsgCodes.info1);
		}
	}//setJPDataFromProspectData

	//set this after "all" jp_jpg data object is built
	private void setUI_MaxValsForFtrAndAllSeenJpJpg() {
		JP_JPG_Data prodData = mapOfJPData.get(typeOfJpStrs[productExJpsIDX]);
		TreeMap<Integer, String> trainJPNamesMap = prodData.getJpNames();
		TreeMap<Integer, String> trainJPGNamesMap = prodData.getJpGrpNames();
		
		String[] tmpTrainJPNamesAra = new String[trainJPNamesMap.size()];
		String[] tmpTrainJPNamesAra_IDX = new String[tmpTrainJPNamesAra.length];
		String[] tmpTrainJPNamesAra_JP = new String[tmpTrainJPNamesAra.length];
		
		int idxJP = 0;
		for(Integer jp : trainJPNamesMap.keySet()) {
			String brfJPName = trainJPNamesMap.get(jp);
			tmpTrainJPNamesAra[idxJP] = brfJPName;
			tmpTrainJPNamesAra_IDX[idxJP] =  "IDX:"+String.format("%3d", idxJP)+" (JP:" +String.format("%3d", jp)+ ") : " + brfJPName;
			tmpTrainJPNamesAra_JP[idxJP] = "JP:" +String.format("%3d", jp)+ ":" + brfJPName;			
			++idxJP;
		}
		
		String[] trainJPGrpNamesAraWithJPG = new String[trainJPGNamesMap.size()];
		int idxJPG = 0;
		for(Integer jpg :  trainJPGNamesMap.keySet()) {
			String brfJPGName = trainJPGNamesMap.get(jpg);
			trainJPGrpNamesAraWithJPG[idxJPG++] = "JP Group:"+String.format("%3d", jpg)+ ":" +brfJPGName;			
		}		
		mapMgr.setUI_JPFtrListVals(tmpTrainJPNamesAra,tmpTrainJPNamesAra_IDX,tmpTrainJPNamesAra_JP,trainJPGrpNamesAraWithJPG); 
		
		TreeMap<Integer, String> allJPNamesMap = allJpJpgData.getJpNames();
		String[] allTrainJPNamesAra = new String[allJPNamesMap.size()];
		idxJP = 0;
		for(Integer jp : allJPNamesMap.keySet()) {
			String brfJPName = allJPNamesMap.get(jp);
			allTrainJPNamesAra[idxJP]= "JP:" +String.format("%3d", jp)+ ":" + brfJPName;	
			++idxJP;
		}
		
		
		mapMgr.setUI_JPAllSeenListVals(allTrainJPNamesAra,allJpJpgData.getJpGrpNames().values().toArray(new String[0])); 		
	}
	/**
	 * @param jpList_prod : list of jp short names (For products)
	 * @param jpList_IDX : list of jp names with IDX (For features)
	 * @param jpList_Jp : list of jp names with jp ID (for class names)
	 * @param jpGrpList : list of jpgroup names with jpgroup ID (for category names)
	*/
	

	/////////////////////////////
	//Save this object's data
	public void saveAllData(String fileNameRaw, String ext) {		for(String key :typeOfJpStrs) {	mapOfJPData.get(key).saveAllData(fileNameRaw, ext);	}	}//saveAllData	
	public String getJPNameFromJP(Integer jp) {return allJpJpgData.getJPNameFromJP(jp);}	
	/////////////////////////////
	//Load this object's data
	public void loadAllData(String fileNameRaw, String ext) {
		for(String key :typeOfJpStrs) {	mapOfJPData.get(key).loadAllData(fileNameRaw, ext);	}
		copyRawNames();
		setUI_MaxValsForFtrAndAllSeenJpJpg(); //to set limits of display values
	}//loadAllData
	
	//training features determined by products - only jps that have corresponding products are use to train map
	public Integer getFtrJpByIdx(int idx) {return trainingJpJpgData.getJpByIdx(idx);}
	public Integer getFtrJpGroupByIdx(int idx) {return trainingJpJpgData.getJpGrpByIdx(idx);}
	public Integer getFtrJpToIDX(int jp) {return trainingJpJpgData.getJpToIDX(jp);}
	public Integer getFtrJpGroupFromJp(int jp) {return trainingJpJpgData.getJpgFromJp(jp);}		//expected to be null if jpgroup has no training data presence
	public boolean checkIfFtrJpPresent(int jp) {return trainingJpJpgData.checkIfJpPresent(jp);}
	public boolean checkIfFtrJpGrpPresent(int jpg) {return trainingJpJpgData.checkIfJpGrpPresent(jpg);}
	//name, jp and ftr idx of jp
	public String getFtrJpStrByJp(int jp) {return trainingJpJpgData.getJpStrByJp(jp);}
	//name, jp and ftr idx of jp
	public String getFtrJpGrpStrByJpg(int jpg) {return trainingJpJpgData.getJpGrpStrByJpGrp(jpg);}
	//get training jps as csv, in index order
	public String getFtrJpsAsCSV() {return trainingJpJpgData.getAllJpsAsCSV();}
	//name, jp and ftr idx of jp
	public String getFtrJpStrByIdx(int uiIDX) {return trainingJpJpgData.getJpStrByIdx(uiIDX);}
	//lacks jp idx and jp id at end - just string name
	public String getFtrJpStrByIdx_Short(int uiIDX) {return trainingJpJpgData.getJpStrByIdx_Short(uiIDX);}
	public String getFtrJpGrpStrByIdx(int uiIDX) {return trainingJpJpgData.getJpGrpStrByIdx(uiIDX);}
	//lacks jpg idx and jpgroup id at end, just name
	public String getFtrJpGrpStrByIdx_Short(int uiIDX) {return trainingJpJpgData.getJpGrpStrByIdx_Short(uiIDX);}
	public int getLenFtrJpByIdx() {		return trainingJpJpgData.getLenJpByIdx();	}//# of jps seen in training data
	public int getLenFtrJpGrpByIdx(){	return  trainingJpJpgData.getLenJpGrpByIdx(); }//# of jpgrps seen in training data
	
	public Integer[] getFtrJpByIDXAra() {return trainingJpJpgData.getJpByIDXAra();}
	public Integer[] getFtrJpgrpByIDXAra() {return trainingJpJpgData.getJpgrpByIDXAra();}
	
	
	//name, jp and list idx of jp
	public String getAllJpStrByIdx(int uiIDX) {return allJpJpgData.getJpStrByIdx(uiIDX);}
	//lacks jp idx and jp id at end - just string name
	public String getAllJpStrByIdx_Short(int uiIDX) {return allJpJpgData.getJpStrByIdx_Short(uiIDX);}
	public String getAllJpGrpStrByIdx(int uiIDX) {return allJpJpgData.getJpGrpStrByIdx(uiIDX);}
	//lacks jpg idx and jpgroup id at end, just name
	public String getAllJpGrpStrByIdx_Short(int uiIDX) {return allJpJpgData.getJpGrpStrByIdx_Short(uiIDX);}
	//name, jp and list idx of jp
	public String getAllJpStrByJp(int jp) {return allJpJpgData.getJpStrByJp(jp);}
	//name, jp and list idx of jp
	public String getAllJpGrpStrByJpg(int jpg) {return allJpJpgData.getJpGrpStrByJpGrp(jpg);}
	
	public Integer[] getAllJpByIDXAra() {return allJpJpgData.getJpByIDXAra();}
	public Integer[] getAllJpgrpByIDXAra() {return allJpJpgData.getJpgrpByIDXAra();}
		
	
	//needc to return array of jps that are specifically Non product,with value being their IDX
	public Integer[] getNonProductJpByIDXAra() {		
		TreeMap<Integer, Integer> allJpsToIDX = allJpJpgData.getJpToIDX();
		Integer[] ftrJpsByIdx = getFtrJpByIDXAra();
		Set<Integer> allJps = allJpsToIDX.keySet();
		TreeSet<Integer> res = new TreeSet<Integer>();		//need to maintain order
		for(Integer ftrjp : ftrJpsByIdx) {	allJps.remove(ftrjp);}
		for(Integer nonProdJp : allJps) {res.add(nonProdJp);}
		return res.toArray(new Integer[0]);
	}
	//non prod jp group is jp groups that non prod jps belong to - groups may have prod jp membership as well
	public Integer[] getNonProductJpGroupByIDXAra() {	
		TreeMap<Integer, Integer> allJpgsToIDX = allJpJpgData.getJpgToIDX();
		Integer[] ftrJpgrpsByIdx = getFtrJpgrpByIDXAra();
		Set<Integer> allJpgs = allJpgsToIDX.keySet();
		TreeSet<Integer> res = new TreeSet<Integer>();	//need to maintain order
		for(Integer ftrjpg : ftrJpgrpsByIdx) {	allJpgs.remove(ftrjpg);}
		for(Integer nonProdJpg : allJpgs) {res.add(nonProdJpg);}
		return res.toArray(new Integer[0]);
	}
	
	//get set of jps for passed jpgroup in prod data
	public TreeSet<Integer> getProdJPsforSpecifiedJpgrp(int jpg){
		TreeSet<Integer> res = mapOfJPData.get(typeOfJpStrs[productExJpsIDX]).getJPsforSpecifiedJpgrp(jpg);
		if(res==null) {return new TreeSet<Integer>();}
		return res;
	}	
	//get jp from index for product-specific jps - UI interaction for displaying products on map
	public Integer getProdJpByIdx(int idx) {return mapOfJPData.get(typeOfJpStrs[productExJpsIDX]).getJpByIdx(idx);}
	//get jpg from index for product-specific jpgs - ui interaction for displaying products on map
	public Integer getProdJpGrpByIdx(int idx) {return mapOfJPData.get(typeOfJpStrs[productExJpsIDX]).getJpGrpByIdx(idx);}
	//get all jps as csv, in index order
	public String getProdJpsAsCSV() {return mapOfJPData.get(typeOfJpStrs[productExJpsIDX]).getAllJpsAsCSV();}
	
	
	public int getCountProdJPSeen(int jp) {return mapOfJPData.get(typeOfJpStrs[productExJpsIDX]).getCountJPSeen(jp);}	
	public Integer getJpToAllIDX(int jp) {return allJpJpgData.getJpToIDX(jp);}	
	
	public Integer getJpgFromJp(int jp) {return allJpJpgData.getJpgFromJp(jp);}
	
	public int getNumTrainFtrs() {return numAllFtrTypes[trainJpJpgKey];}
	//get jp by index for all jp's, not just training features
	public Integer getAllJpByIdx(int idx) {return allJpJpgData.getJpByIdx(idx);}
	public int getNumAllJpsFtrs() {return numAllFtrTypes[allExJpsIDX];}
	//get all jps as csv, in index order
	public String getAllJpsAsCSV() {return allJpJpgData.getAllJpsAsCSV();}
	
	//this will return the appropriate jpgrp for the given jpIDX (ftr idx)
	public int getUI_JPGrpIdxFromFtrJPIdx(int jpIdx, int curVal) {return trainingJpJpgData.getUI_JPGrpIdxFromJPIdx(jpIdx, curVal);}
	//this will return the first(lowest) jp for a particular jpgrp
	public int getUI_FirstJPIdxFromFtrJPGIdx(int jpgIdx, Integer curJPIdxVal) { return trainingJpJpgData.getUI_FirstJPIdxFromJPGIdx(jpgIdx, curJPIdxVal);}//getUI_FirstJPFromJPG	
	//this will return the appropriate jpgrp for the given jpIDX (ftr idx)
	public int getUI_JPGrpIdxFromAllJPIdx(int jpIdx, int curVal) {return allJpJpgData.getUI_JPGrpIdxFromJPIdx(jpIdx, curVal);}
	//this will return the first(lowest) jp for a particular jpgrp
	public int getUI_FirstJPIdxFromAllJPGIdx(int jpgIdx, Integer curJPIdxVal) { return allJpJpgData.getUI_FirstJPIdxFromJPGIdx(jpgIdx, curJPIdxVal);}//getUI_FirstJPFromJPG	
	//return how many times this JP has been seen
	public int getCountJPSeen(int _type, Integer jp) {return mapOfJPData.get(typeOfJpStrs[_type]).getCountJPSeen(jp);}

	
	public int getNumJPsByType(int _type) {return numAllFtrTypes[_type];}
	public Integer[] getJpByIDXAra(int _type) {return mapOfJPData.get(typeOfJpStrs[_type]).getJpByIDXAra();}
	public Integer[] getJpgrpByIDXAra(int _type) {return mapOfJPData.get(typeOfJpStrs[_type]).getJpgrpByIDXAra();}
	
	
	private static final int[] bitFlags = new int[]{0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80};
	private static final String[] jpFlagMsgs = new String[] {"JP has no Products", "JP has Product without Orders"};
	
	//prints all JP_JPG_Data structs' toString info sequentially
	public String toStringAll() {
		String res = "";
		for(String key :typeOfJpStrs) {	res+=mapOfJPData.get(key).toString(); res+="\n";}
		return res;
	}//toString
	
	private int getNumSeen(int jp, TreeMap<Integer, Integer> counts) {
		Integer numSeen = counts.get(jp);	
		if(null==numSeen) {numSeen=0;}
		return numSeen;
	}
	
	//debugging function to display all unique jps seen in data
	public void dbgShowUniqueJPsSeen() {
		msgObj.dispMessage("MonitorJpJpgrp","dbgShowUniqueJPsSeen","All Jp's seen : ", MsgCodes.info1);
		for(String key :typeOfJpStrs) {	mapOfJPData.get(key).dbgShowUniqueJPsSeen();}
	}//dbgShowUniqueJPsSeen
	
	public void dbgDispKnownJPsJPGs() {
		msgObj.dispMessage("MonitorJpJpgrp","dbgDispKnownJPsJPGs","JPGs seen : (jp : count : ftridx) :", MsgCodes.info1);
		for(String key :typeOfJpStrs) {	mapOfJPData.get(key).dbgDispKnownJPsJPGs();}
	}//dbgDispKnownJPsJPGs
	
	
	//aggregates all JP_JPG_Data structs' toString info to build single output
	public String toString() {
		String res = "";
		JP_JPG_Data allObj = allJpJpgData;
		//used by UI and also to map specific indexes to actual jps/jpgs
		Integer[] jpByIdx = allObj.getJpByIdx();
		int numJps = jpByIdx.length;	
		if (numJps < 2) {return "JpJpg Monitoring Construct not built yet";}//not made yet
	
		//reference to jp ids and counts of all jps seen in all data of type specified
		TreeMap<Integer, Integer> jpSeenCount = allObj.getJpSeenCount(); 
		//TreeMap<Integer, Integer> jpProspectSeenCounts = new TreeMap<Integer, Integer>();
		TreeMap<Integer, Integer> jpCustSeenCounts = mapOfJPData.get(typeOfJpStrs[custExJpsIDX]).getJpSeenCount();
		TreeMap<Integer, Integer> jpTruePrspctCounts = mapOfJPData.get(typeOfJpStrs[prospectExJpsIDX]).getJpSeenCount();
		TreeMap<Integer, Integer> jpProductSeenCounts = mapOfJPData.get(typeOfJpStrs[productExJpsIDX]).getJpSeenCount();
		
		//reference to ids and counts of all jps seen in source event records
		HashMap<Straff_EvtDataType, TreeMap<Integer, Integer>> jpPerEventSeenCount = allObj.getJpPerEventSeenCount();
		//TreeMap<Integer, TreeSet <Integer>> jpgsToJps = allObj.getJpgsToJps();
		TreeMap<Integer, Integer> jpsToJpgs = allObj.getJpsToJpgs();
		//map from jp to idx in array
		Integer[] jpgrpsByIdx = allObj.getJpGrpByIdx();

		TreeMap<Integer, String> jpNamesRaw = allObj.getJpNamesRaw();                                                                                                             
		TreeMap<Integer, String> jpGrpNamesRaw = allObj.getJpGrpNamesRaw(); //these are all the names known from reading all the data in - not all may be represented in actual data 
		TreeMap<Integer, String> jpNames = allObj.getJpNames();                                                                                                                
		TreeMap<Integer, String> jpGrpNames = allObj.getJpGrpNames();	     
		
		int numDispOffset = 4;	//total seen, cust seen, prospect seen, product seen		
		
		int frmtLen = 45, frmtLenJPG = 35;
		String frmtStr = "%-"+(frmtLen+1) + "s",frmtStrJPG = "%-"+(frmtLenJPG+1) + "s";
			
		res += "\nNum Jps = " + numJps+ " "+str_sep+" Num  JpGrps = "+jpgrpsByIdx.length +"\n";
		res += " Jp "+str_sep+ String.format(frmtStr,"Jp Name") +str_sep+" Jpgrp "+str_sep + String.format(frmtStrJPG,"Jpgrp Name") +" "+str_sep+"# Jp exs"+str_sep+"# in Cust"+str_sep+"# in Prspcts"+str_sep+"# in Prod"+str_sep;
		int numEventTypes = Straff_EvtDataType.getNumVals();
		for(int i=0;i<numEventTypes;++i) {		res+= String.format("# %6s"+str_sep,Straff_EvtDataType.getVal(i).name());		}
		res += "   ** NOTES **   "+str_sep;
		res +="\n";
		int[] ttlNumJpOcc = new int[numEventTypes+numDispOffset];
		
		//for each jpgroup, save all counts
		TreeMap<Integer,Integer[]> countsPerJPG = new TreeMap<Integer,Integer[]>();
		//use this to show marker text at the end of the line - bit flags to have multiple components
		int lastColPrintCode = 0;
		int numProdJPs =0;
		int nameLen = 0;
		String dispStr = "";
		for (int idx = 0;idx<numJps;++idx) {
			lastColPrintCode=0;
			int jp = jpByIdx[idx];
			int jpGrp = jpsToJpgs.get(jp);
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
			Integer jpCustPrsSeen = getNumSeen(jp,jpCustSeenCounts);
			Integer jpTruPrsSeen = getNumSeen(jp,jpTruePrspctCounts);
			Integer jpProdSeen = getNumSeen(jp,jpProductSeenCounts);
			nameLen = jpName.length();
			dispStr = (nameLen <= frmtLen ? jpName : jpName.substring(0, frmtLen-2)+"...");
			res += String.format("%3d "+str_sep, jp)+ String.format(frmtStr,dispStr);			
			ttlNumJpOcc[0]+=jpSeen;		
			ttlNumJpOcc[1]+=jpCustPrsSeen;
			ttlNumJpOcc[2]+=jpTruPrsSeen;
			ttlNumJpOcc[3]+=jpProdSeen;
			
			perJPGrpCounts[0]+=jpSeen;	
			perJPGrpCounts[1]+=jpCustPrsSeen;	
			perJPGrpCounts[2]+=jpTruPrsSeen;	
			perJPGrpCounts[3]+=jpProdSeen;	
			
			if(jpProdSeen == 0){lastColPrintCode |= bitFlags[0];}//jp has no products
			nameLen = jpgName.length();
			dispStr = (nameLen <= frmtLenJPG ? jpgName : jpgName.substring(0, frmtLenJPG-2)+"...");
			res += str_sep+" "+String.format("%5d "+str_sep, jpGrp)
				+ String.format(frmtStrJPG,dispStr) 
				+ String.format(" "+str_sep+"%7d "+str_sep+" %7d "+str_sep+" %10d "+str_sep+" %7d",jpSeen,jpCustPrsSeen,jpTruPrsSeen,jpProdSeen);			
			for(int i=0;i<numEventTypes;++i) {	
				Integer count = jpPerEventSeenCount.get(Straff_EvtDataType.getVal(i)).get(jp);
				if(count==null) {count=0;}//offset by 2 to hold spaces for pro
				if((jpProdSeen != 0) && (count==0)&& (i==0)) {lastColPrintCode |= bitFlags[1];}//products without any orders
				ttlNumJpOcc[i+numDispOffset]+=count;
				perJPGrpCounts[i+numDispOffset]+=count;
				res+= String.format(" "+str_sep+"%7d",count);
			}		
			String finalLineStr = "";
			if(lastColPrintCode>0) {
				for(int i=0;i<bitFlags.length;++i) {
					if((lastColPrintCode & bitFlags[i]) == bitFlags[i]) {		finalLineStr+=" *"+ jpFlagMsgs[i]+"* "+ str_sep;	}
				}
			}	//disp notes for this jp
			else {				numProdJPs++;			}
			res += " " + str_sep+" "+finalLineStr+"\n";
		}		
		res += "Ttls : Seen : " + ttlNumJpOcc[0] + " "+str_sep+" Cust : "+ ttlNumJpOcc[1]+ " "+str_sep+" Prspct : "+ ttlNumJpOcc[2]+ " "+str_sep+" Products : "+ ttlNumJpOcc[3];
		for(int i=0;i<numEventTypes;++i) {	res+= " "+str_sep+" "+Straff_EvtDataType.getVal(i).name()+" : " + ttlNumJpOcc[numDispOffset+i];}
		res+=" "+str_sep+" # of JPs with products : " + numProdJPs;
		res +="\n";
		
		//show how many event-based records there are for each jp group
		int numJPGs = jpgrpsByIdx.length;
		ttlNumJpOcc = new int[numEventTypes+numDispOffset];
		res +="\n"+str_sep+" Jpgrp "+str_sep + String.format(frmtStrJPG,"Jpgrp Name") +" "+str_sep+"# Jpg exs"+str_sep+"#in Custs"+str_sep+"#in Prspt"+str_sep+"#in Prods"+str_sep;
		for(int i=0;i<numEventTypes;++i) {		res+= String.format("# %7s"+str_sep,Straff_EvtDataType.getVal(i).name());		}
		res +="\n";
		for (int jpGrpIdx = 0;jpGrpIdx<numJPGs;++jpGrpIdx) {
			int jpGrp = jpgrpsByIdx[jpGrpIdx];
			String jpgName = jpGrpNames.get(jpGrp);
			if(jpgName==null) {jpgName = jpGrpNamesRaw.get(jpGrp);}		
			nameLen = jpgName.length();
			dispStr = (nameLen <= frmtLenJPG ? jpgName : jpgName.substring(0, frmtLenJPG-2)+"...");
			
			res +=str_sep+" "+String.format("%5d "+str_sep, jpGrp)+String.format(frmtStrJPG,dispStr);
			Integer[] counts = countsPerJPG.get(jpGrp);
			for(int i=0;i<counts.length;++i) {
				ttlNumJpOcc[i]+=counts[i];
				res+= String.format(" "+str_sep+"%8d",counts[i]);
			}			
			res +=" "+str_sep+"\n";
		}
		res += "\nTtls : JPGroup Seen : " + ttlNumJpOcc[0] + " "+str_sep+" JPGroup Cust/Prspct : "+ ttlNumJpOcc[1]+ " "+str_sep+" JPGroup Cust/Prspct : "+ ttlNumJpOcc[2]+ " "+str_sep+" JPGroup Products : "+ ttlNumJpOcc[3];
		for(int i=0;i<numEventTypes;++i) {	
			res+= " "+str_sep+" "+Straff_EvtDataType.getVal(i).name()+" : " + ttlNumJpOcc[numDispOffset+i];	
		}
		res +="\n";
		return res;
	}//toString

}//class MonitorJpJpgrp

//a struct object to hold all jp and jpgroup data for a specific source type
abstract class JP_JPG_Data{
	protected MessageObject msgObj; 
	public final String type;
	private FileIOManager fileIO;
	public final Straff_MonitorJpJpgrp mon;
	//reference to jp ids and counts of all jps seen in all data of type specified
	protected TreeMap<Integer, Integer> jpSeenCount;
	//reference to ids and counts of all jps seen in source event records
	protected HashMap<Straff_EvtDataType, TreeMap<Integer, Integer>> jpPerEventSeenCount;	
	//map from jp to idx in array
	private TreeMap<Integer, Integer> jpToIDX;
	//map from jpgroup to integer
	private TreeMap<Integer, Integer> jpgToIDX;	
	//map from jpgroup to jps corresponding to this group.
	private TreeMap<Integer, TreeSet <Integer>> jpgsToJps;
	//map from jps to owning jpgs
	private TreeMap<Integer, Integer> jpsToJpgs;// = new TreeMap<Integer, Integer>();
	//used by UI and also to map specific indexes to actual jps/jpgs
	private Integer[] jpByIdx = new Integer[] {1}, jpGrpByIdx = new Integer[] {1};
	//# of jps seen in products == # of ftrs if this data class is used for ftr description
	private int numFtrs;
	//maps jpgroup to # of jps seen per jpgroup
	private TreeMap<Integer, Integer> jpGroupCountOfJps;


	protected TreeMap<Integer, String> jpNamesRaw, jpGrpNamesRaw;	//these are all the names known from reading all the data in - not all may be represented in actual data
	private TreeMap<Integer, String> jpNames, jpGrpNames;	
	//use for display separator - change to comma for csv-suitable output
	private static final String str_sep = "|";

	public JP_JPG_Data(Straff_MonitorJpJpgrp _mon, String _type) {
		mon=_mon;type=_type; msgObj = mon.msgObj;
		fileIO = new FileIOManager(msgObj,"MonitorJpJpgrp::JP_JPG_Data("+type+")");
		init();
	}//ctor
	
	//initialize all structures
	public void init() {
		//msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","init","Start Init", MsgCodes.info5);
		jpSeenCount = new TreeMap<Integer, Integer>(); 	//count of data records of specified type having jp
		
		//these mappings are redundant - jps will always map to same jpg, just here to make refactoring and migration easier
		jpsToJpgs = new TreeMap<Integer, Integer>();	//map from jpgs to jps
		jpgsToJps = new TreeMap<Integer, TreeSet <Integer>>();
		
		//count of jps per jp group, keyed by jpGroup ID (not idx)
		jpGroupCountOfJps = new TreeMap<Integer, Integer>(); 
		
		//mapping all jps to index in array
		jpToIDX = new TreeMap<Integer, Integer>();	
		jpgToIDX = new TreeMap<Integer, Integer>();	
		
		//list of jps used with idx in list being idx in array
		jpByIdx = new Integer[] {1};
		jpGrpByIdx = new Integer[] {1};
		
		jpNames = new TreeMap<Integer, String>();	
		jpGrpNames = new TreeMap<Integer, String>();
		
		jpPerEventSeenCount = new HashMap<Straff_EvtDataType, TreeMap<Integer, Integer>>(); //count of prospect train records having jp only seen in source events
		int numEvTypes = Straff_EvtDataType.getNumVals();
		for (int i=0;i<numEvTypes;++i) {jpPerEventSeenCount.put(Straff_EvtDataType.getVal(i), new TreeMap<Integer, Integer>());}//for	
		numFtrs = 0;
		//msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","init","Finish Init", MsgCodes.info5);
	}//init() 
	
	public void setRawNames(ArrayList<Straff_BaseRawData> _rawJpData, ArrayList<Straff_BaseRawData> _rawJpgData) {
		//msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","setJpJpgrpNames","Start setting names", MsgCodes.info5);
		jpNamesRaw = buildJPNames(_rawJpData);		
		jpGrpNamesRaw = buildJPNames(_rawJpgData);
		//msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","setJpJpgrpNames","Done setting names", MsgCodes.info5);
	}//setJpJpgrpNames
	
	private TreeMap<Integer, String> buildJPNames(ArrayList<Straff_BaseRawData> _raw) {
		TreeMap<Integer, String> _nameList= new TreeMap<Integer, String>();
		for (Straff_BaseRawData rawJp : _raw) {
			Straff_JobPracticeData jp = (Straff_JobPracticeData)rawJp;
			_nameList.put(jp.getJPID(), jp.getName());			
		}
		return _nameList;
	}//buildJPNames

	public void copyRawNames(TreeMap<Integer, String> _rawJPNames, TreeMap<Integer, String> _rawJPGames) {
		jpNamesRaw = new TreeMap<Integer, String>();
		for(Integer jp:_rawJPNames.keySet()) {jpNamesRaw.put(jp, _rawJPNames.get(jp));}
		jpGrpNamesRaw = new TreeMap<Integer, String>();
		for(Integer jpg:_rawJPGames.keySet()) {jpGrpNamesRaw.put(jpg, _rawJPGames.get(jpg));}		
	}//copyRawNames
	
	//add example data to aggregators
	@SuppressWarnings("rawtypes")
	public abstract void buildJpPresentMaps(ConcurrentSkipListMap mapObj,HashSet<Tuple<Integer,Integer>> tmpSetIndivJpsJpgs,HashSet<Tuple<Integer,Integer>> tmpSetAllJpsJpgs);
	
	//build jp-to-jpgroup and jpgroup-to-jp relational structures
	public void finishBuildJPStructs(HashSet<Tuple<Integer,Integer>> tmpSetAllJpsJpgs) {	
		msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","finishBuildJPStructs","Start final settings of jpgroup/jp counts and mappings", MsgCodes.info1);
		//build jpsToJpgs and JpgsToJps structs
		for (Tuple<Integer,Integer> jpgJp : tmpSetAllJpsJpgs) {
			int jpg = jpgJp.x, jp=jpgJp.y;
			//attach jpg to jp
			jpsToJpgs.put(jp, jpg);
			//attach jp to list of jps owned by jpg
			TreeSet <Integer> jpList = jpgsToJps.get(jpg);
			if (jpList==null) {jpList = new TreeSet <Integer>(); jpgsToJps.put(jpg, jpList);}
			jpList.add(jp);			
		}	
		for(Integer jpg : jpgsToJps.keySet()) {
			jpGroupCountOfJps.put(jpg, jpgsToJps.get(jpg).size());
		}
		//put every _training_ jp into an array, with the idx in the array being the idx in the feature
		jpByIdx = jpSeenCount.keySet().toArray(new Integer[0]);
		jpGrpByIdx = jpgsToJps.keySet().toArray(new Integer[0]);

		for(int i=0;i<jpByIdx.length;++i) {
			jpToIDX.put(jpByIdx[i], i);
			String name = jpNamesRaw.get(jpByIdx[i]);
			if (name==null) {msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","finishBuildJPStructs","!!!!!!!!!!!!!!!! null name for jp : " + jpByIdx[i] +" in raw jp names. Using default name.", MsgCodes.warning2);name="JP:"+jpByIdx[i];}
			jpNames.put(jpByIdx[i], name);
		}
		for(int i=0;i<jpGrpByIdx.length;++i) {
			jpgToIDX.put(jpGrpByIdx[i], i);
			String name = jpGrpNamesRaw.get(jpGrpByIdx[i]);
			if (name==null) {msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","finishBuildJPStructs","!!!!!!!!!!!!!!!! null name for jpgrp : " + jpGrpByIdx[i]+" in raw jpg names. Using default name.", MsgCodes.warning2);name="JPG:"+jpGrpByIdx[i];}
			jpGrpNames.put(jpGrpByIdx[i], name);
		}		
		//needs to be same size as jpByIdx.length
		numFtrs = jpSeenCount.size();
		msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","finishBuildJPStructs","Finished final settings of jpgroup/jp counts and mappings", MsgCodes.info1);
	}//finishBuildJPStructs
	
	protected void incrJPCounts(Integer jp, TreeMap<Integer, Integer> map) {
		Integer count = map.get(jp);
		if(count==null) {count=0;}
		map.put(jp, count+1);
	}//incrJPCounts	
	
	public int getNumFtrs() {return numFtrs;}
	public Integer getJpByIdx(int idx) {return jpByIdx[idx];}	
	public Integer getJpGrpByIdx(int idx) {return jpGrpByIdx[idx];}	
	public Integer getJpToIDX(int jp) {return jpToIDX.get(jp);}
	public Integer getJpgFromJp(int jp) {return jpsToJpgs.get(jp);}
	public boolean checkIfJpPresent(int jp) {return jpsToJpgs.get(jp) !=null;}
	public boolean checkIfJpGrpPresent(int jpg) { return jpgsToJps.get(jpg) !=null;}
	
	//return comma-sep string of all jps
	public String getAllJpsAsCSV() {
		String res = "";
		for(int i=0;i<jpByIdx.length-1;++i) {res+=jpByIdx[i]+",";}
		res+=jpByIdx[jpByIdx.length-1];
		return res;
	}	
	
	public int getLenJpByIdx() {		return jpByIdx.length;	}//# of jps seen
	public int getLenJpGrpByIdx(){	return jpGrpByIdx.length; }//# of jpgrps seen
	
	//public String[] getJpGrpStrNames
	
	public Integer[] getJpByIDXAra() {return jpByIdx;}
	public Integer[] getJpgrpByIDXAra() {return jpGrpByIdx;}
	
	//return list of jp's for passed jpgroup
	public TreeSet<Integer> getJPsforSpecifiedJpgrp(int jpg){return jpgsToJps.get(jpg);}
	
	//map from jpgroup to jps corresponding to this group.
	public TreeMap<Integer, TreeSet <Integer>> getJpgsToJps(){return jpgsToJps;}
	//map from jps to owning jpgs
	public TreeMap<Integer, Integer> getJpsToJpgs(){return jpsToJpgs;}
	public TreeMap<Integer, Integer> getJpSeenCount(){return jpSeenCount;}
	//reference to ids and counts of all jps seen in source event records
	public HashMap<Straff_EvtDataType, TreeMap<Integer, Integer>> getJpPerEventSeenCount(){return jpPerEventSeenCount;}
	//map from jp to idx in array
	public TreeMap<Integer, Integer> getJpToIDX(){return jpToIDX;}
	//map from jpgroup to integer
	public TreeMap<Integer, Integer> getJpgToIDX(){return jpgToIDX;}	
	public Integer[] getJpByIdx(){return jpByIdx;}
	public Integer[] getJpGrpByIdx(){return jpGrpByIdx;}
	
	public TreeMap<Integer, String> getJpNamesRaw(){return jpNamesRaw;} 
	public TreeMap<Integer, String> getJpGrpNamesRaw(){return jpGrpNamesRaw;} //these are all the names known from reading all the data in - not all may be represented in actual data
	public TreeMap<Integer, String> getJpNames(){return jpNames;}
	public TreeMap<Integer, String> getJpGrpNames(){return jpGrpNames;} 	
	
	//return how many times this JP has been seen
	public int getCountJPSeen(Integer jp) {
		Integer res = jpSeenCount.get(jp);
		if (res == null) {res = 0;	}
		return res;
	}	
	
	public String getJpStrByIdx_Short(int uiIDX) {
		int idx = uiIDX % jpByIdx.length, jp=jpByIdx[idx];
		String name = jpNames.get(jp);
		if(name==null) {name="UNK";}
		return ""+name;
	}
	
	//name, jp and ftr idx of jp
	public String getJpStrByIdx(int uiIDX) {
		int idx = uiIDX % jpByIdx.length, jp=jpByIdx[idx];
		String name = jpNames.get(jp);
		if(name==null) {name="UNK";}
		return "" +name+ " (jp:"+ jp + ",idx:" +idx+ ")";
	}
	//name, jp and ftr idx of jp
	public String getJpGrpStrByIdx_Short(int uiIDX) {
		int idx = uiIDX % jpGrpByIdx.length, jpg=jpGrpByIdx[idx];
		String name = jpGrpNames.get(jpg);
		if(name==null) {name="UNK";}
		return "" +name;
	}	
	
	//name, jp and ftr idx of jp
	public String getJpGrpStrByIdx(int uiIDX) {
		int idx = uiIDX % jpGrpByIdx.length, jpg=jpGrpByIdx[idx];
		String name = jpGrpNames.get(jpg);
		if(name==null) {name="UNK";}
		return "" +name+ " (jpg:"+ jpg + ",idx:" +idx+ ")";
	}	
	
	//name, jp and ftr idx of jp
	public String getJpStrByJp(int jp) {
		int idx = jpToIDX.get(jp);
		String name = jpNames.get(jp);
		if(name==null) {name="UNK";}		
		return "" +name+ " (jp:"+ jp + ",idx:" +idx+ ")";
	}
	//name, jp and ftr idx of jp
	public String getJpGrpStrByJpGrp(int jpg) {
		int idx = jpgToIDX.get(jpg);
		String name = jpGrpNames.get(jpg);
		if(name==null) {name="UNK";}
		return "" +name+ " (jpg:"+ jpg + ",idx:" +idx+ ")";
	}	
	
	//this will return the appropriate jpgrp for the given jpIDX (ftr idx)
	public int getUI_JPGrpIdxFromJPIdx(int jpIdx, int curVal) {
		if(jpsToJpgs.size() < jpIdx) {return curVal;}
		int jpgrp = jpsToJpgs.get(jpByIdx[jpIdx]);
		return jpgToIDX.get(jpgrp);		
	}
	//this will return the first(lowest) jp for a particular jpgrp
	public int getUI_FirstJPIdxFromJPGIdx(int jpgIdx, Integer curJPIdxVal) {
		if(jpgsToJps.size() < jpgIdx) {return curJPIdxVal;}		
		int curJPVal = jpByIdx[curJPIdxVal];		
		//String msg = "Requested Job Practice Group Index : " + String.format("%3d",jpgIdx) + " Cur JP : " +  String.format("%3d",curJPVal) + " Cur JP Idx : " + String.format("%3d", curJPIdxVal);		
		TreeSet <Integer> jpList = jpgsToJps.get(jpGrpByIdx[jpgIdx]);
		if (jpList.contains(curJPVal)) {		//shouldn't happen, only is called when jpgrp changes, so won't ever have current jp value since jps only belong to single groups
			//msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","getUI_FirstJPFromJPG", msg+ " : JP Group contains current JP.", MsgCodes.info1);			
		}//if in current jpgrp already, then return current value
		else {//swapping to new jpgroup
			curJPIdxVal = jpToIDX.get(jpList.first());
			//msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","getUI_FirstJPFromJPG", msg+ " : JP Group doesn't contain current JP val; JP changed to : " + curJPVal + " Idx : " + curJPIdxVal, MsgCodes.info1);			
		}
		return curJPIdxVal;	
	}//getUI_FirstJPFromJPG
	//debugging function to display all unique jps seen in data
	public void dbgShowUniqueJPsSeen() {
		msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","dbgShowUniqueJPsSeen","All Jp's seen : ", MsgCodes.info1);
		for (Integer key : jpSeenCount.keySet()) {msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","dbgShowUniqueJPsSeen","JP : "+ String.format("%3d",key) + "   |Count : " + String.format("%6d",jpSeenCount.get(key)) + "\t|Ftr IDX : " + String.format("%3d",jpToIDX.get(key)) + "\t|Owning JPG : " + String.format("%2d",jpsToJpgs.get(key)), MsgCodes.info1);}
		msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","dbgShowUniqueJPsSeen","Number of unique JP's seen : " + jpSeenCount.size(), MsgCodes.info1);
		dbgDispKnownJPsJPGs();
	}//dbgShowUniqueJPsSeen
	
	public void dbgDispKnownJPsJPGs() {
		msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","dbgDispKnownJPsJPGs","JPGs seen : (jp : count : ftridx) :", MsgCodes.info1);
		for (Integer jpgrp : jpgsToJps.keySet()) {
			String res = "JPG : " + String.format("%3d", jpgrp);
			TreeSet <Integer> jps = jpgsToJps.get(jpgrp);			
			for (Integer jp : jps) {res += " ("+String.format("%3d", jp)+" :"+String.format("%6d", jpSeenCount.get(jp))+" : "+ String.format("%3d", jpToIDX.get(jp), MsgCodes.info1)+"),";}
			msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","dbgDispKnownJPsJPGs",res, MsgCodes.info1);		
		}
	}//dbgDispKnownJPsJPGs
	
	/////////////////////////////
	//Save this object's data
	public void saveAllData(String fileNameRaw, String ext) {		
		String fileName = fileNameRaw+"_"+type + ext;
		msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","saveAllData","Start to save all " + jpByIdx.length + " jp's worth of "+type+" data in :"+fileName, MsgCodes.info5);
		ArrayList<String> csvResTmp = new ArrayList<String>();		
		String tmp = "Jp & Jpg Data for " + type +" data.";
		csvResTmp.add(tmp);
		int numJps = jpByIdx.length;
		tmp = "Num Jps=," + numJps+ ",Num  JpGrps=,"+jpGrpByIdx.length;
		csvResTmp.add(tmp);
		tmp = "Jp,JpIDX,Jp Name,Jpgrp,JpgrpIDX,Jpgrp Name,# JpSeen, ";//# JpSourceSeen";
		int numEventTypes = Straff_EvtDataType.getNumVals();
		for(int i=0;i<numEventTypes;++i) {			tmp+= "# Jp"+Straff_EvtDataType.getVal(i).name()+"Seen,";		}
		csvResTmp.add(tmp);
		for (int idx = 0;idx<numJps;++idx) {
			int jp = jpByIdx[idx];
			int jpGrp = jpsToJpgs.get(jp);
			int jpGrpIdx = jpgToIDX.get(jpGrp);
			tmp = ""+jp+","+idx+","+ getNameNullChk(jp,jpNames);
			tmp +=","+jpGrp+","+jpGrpIdx+","+ getNameNullChk(jpGrp,jpGrpNames)+","+getCountNullChk(jp,jpSeenCount)+",";			
			for(int i=0;i<numEventTypes;++i) {			tmp+= ""+getCountNullChk(jp,jpPerEventSeenCount.get(Straff_EvtDataType.getVal(i))) +",";}
			csvResTmp.add(tmp);
		}		
		fileIO.saveStrings(fileName, csvResTmp);	
		msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","saveAllData","Finished saving all "+type+" jp data in :"+fileName, MsgCodes.info5);
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
	public void loadAllData(String fileNameRaw, String ext) {		
		String fileName = fileNameRaw+"_"+type + ext;
		msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","loadAllData","Start to load all "+type+" jp data in :"+fileName, MsgCodes.info5);
		init();//clear everything out
		jpNamesRaw = new TreeMap<Integer, String>();	
		jpGrpNamesRaw = new TreeMap<Integer, String>();
		//HashSet<Integer> tmpProdJpgrs = new HashSet<Integer>();
		String[] csvLoadRes = fileIO.loadFileIntoStringAra(fileName, "MonitorJpJpgrp file loaded", "MonitorJpJpgrp File Failed to load");
		//int numJps = 0, numJpgs = 0;
		boolean isProd = false;
		int numEventTypes = Straff_EvtDataType.getNumVals();
		for(int i=0;i<csvLoadRes.length;++i) {
			String line = csvLoadRes[i];
			String[] vals = line.split(SOM_MapManager.csvFileToken);
			if(i==0) { continue;} //1st line in saved file has file type 
			else if(i==1) {//2nd line has counts of jps and jpgs
				jpByIdx = new Integer[Integer.parseInt(vals[1])];		
				jpGrpByIdx = new Integer[Integer.parseInt(vals[3])];	
			} 
			else if (i==2) {continue;}								//header string	
			else {//here record has all values i >= 2
				if (!isProd) {//found in prospects
					//layout"Jp, JpIDX, Jp Name, Jpgrp, JpgrpIDX, Jpgrp Name, JpSeen, <for each type of even count of jp's seen, following EvtDataType order>";
					int jp = Integer.parseInt(vals[0]),
						jpFtrIDX = Integer.parseInt(vals[1]),
						jpgrp = Integer.parseInt(vals[3]),
						jpgrpIDX = Integer.parseInt(vals[4]),
						jpSeen = Integer.parseInt(vals[6]);
					String jpName = vals[2], jpgrpName = vals[5];
					jpByIdx[jpFtrIDX] = jp;
					jpToIDX.put(jp, jpFtrIDX);			
					jpSeenCount.put(jp, jpSeen);
					for(int j=0;j<numEventTypes;++j) {		
						TreeMap<Integer, Integer> PerEvtCountMap = jpPerEventSeenCount.get(Straff_EvtDataType.getVal(j));
						PerEvtCountMap.put(jp, Integer.parseInt(vals[7+j]));
					}					
					jpsToJpgs.put(jp, jpgrp);
					jpNamesRaw.put(jp, jpName);					
					TreeSet <Integer> jpsAtJpg = jpgsToJps.get(jpgrp);
					if (jpsAtJpg == null) {jpsAtJpg = new TreeSet <Integer>(); }
					jpsAtJpg.add(jp);
					jpgsToJps.put(jpgrp,jpsAtJpg);
					jpGrpNamesRaw.put(jpgrp, jpgrpName);
					jpgToIDX.put(jpgrp,jpgrpIDX);
					jpGrpByIdx[jpgrpIDX]=jpgrp;
				} 
			}
		}			
		msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","loadAllData","Finished loading all "+type+" jp data in :"+fileName + " Which has :"+ jpByIdx.length + " jps.", MsgCodes.info5);
	}//loadAllData
	
	public String toString() {
		int numJps = jpByIdx.length;	
		int numDispOffset = 1;	//total seen is only offset
		if (numJps < 2) {return "JpJpg Monitoring Construct not built yet";}//not made yet
		String res = "";
		
		int frmtLen = 50, frmtLenJPG = 21;
		String frmtStr = "%-"+(frmtLen+1) + "s",frmtStrJPG = "%-"+(frmtLenJPG+1) + "s";
			
		res += "\nJp/Jpgs from " + type +" Data : Num Jps = " + numJps+ " "+str_sep+" Num  JpGrps = "+jpGrpByIdx.length +"\n";
		res += " Jp "+str_sep+"JpIDX"+str_sep + String.format(frmtStr,"Jp Name") +str_sep+" Jpgrp "+str_sep+" JpgIDX "+str_sep + String.format(frmtStrJPG,"Jpgrp Name") +" "+str_sep+"# Jp exs"+str_sep;//+"# in Cust"+str_sep+"# in Prod"+str_sep;
		int numEventTypes = Straff_EvtDataType.getNumVals();
		for(int i=0;i<numEventTypes;++i) {		res+= String.format("# %6s"+str_sep,Straff_EvtDataType.getVal(i).name());		}
		res += "   ** NOTES **   "+str_sep;
		res +="\n";
		int[] ttlNumJpOcc = new int[numEventTypes+numDispOffset];
		
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

			res += String.format("%3d "+str_sep+" %3d "+str_sep, jp,idx)+ String.format(frmtStr,jpName.substring(0, (jpName.length() < frmtLen ? jpName.length() : frmtLen)));			
			ttlNumJpOcc[0]+=jpSeen;		
			
			perJPGrpCounts[0] += jpSeen;	
			res += str_sep+" "+String.format("%5d "+str_sep+" %6d "+str_sep, jpGrp, jpGrpIdx)
				+ String.format(frmtStrJPG,jpgName.substring(0, (jpgName.length() < frmtLenJPG ? jpgName.length() : frmtLenJPG))) 
				+ String.format(" "+str_sep+"%7d",jpSeen);			
			for(int i=0;i<numEventTypes;++i) {	
				Integer count = jpPerEventSeenCount.get(Straff_EvtDataType.getVal(i)).get(jp);
				if(count==null) {count=0;}//offset by 2 to hold spaces for pro
				ttlNumJpOcc[i+numDispOffset]+=count;
				perJPGrpCounts[i+numDispOffset]+=count;
				res+= String.format(" "+str_sep+"%7d",count);
			}		
			
			res += " " + str_sep+" \n";
		}		
		res += "Ttls : Seen : " + ttlNumJpOcc[0] + " "+str_sep+" Cust/Prspct : "+ ttlNumJpOcc[1]+ " "+str_sep+" Products : "+ ttlNumJpOcc[2];
		for(int i=0;i<numEventTypes;++i) {	res+= " "+str_sep+" "+Straff_EvtDataType.getVal(i).name()+" : " + ttlNumJpOcc[numDispOffset+i];}
		res +="\n";
		
		//show how many event-based records there are for each jp group
		int numJPGs = jpGrpByIdx.length;
		ttlNumJpOcc = new int[numEventTypes+numDispOffset];
		res +="\n"+str_sep+" Jpgrp "+str_sep + String.format(frmtStrJPG,"Jpgrp Name") +" "+str_sep+"#Jpg exs"+str_sep;//+"#in Cust"+str_sep+"#in Prod"+str_sep;
		for(int i=0;i<numEventTypes;++i) {		res+= String.format("# %6s"+str_sep,Straff_EvtDataType.getVal(i).name());		}
		res +="\n";
		for (int jpGrpIdx = 0;jpGrpIdx<numJPGs;++jpGrpIdx) {
			int jpGrp = jpGrpByIdx[jpGrpIdx];
			String jpgName = jpGrpNames.get(jpGrp);
			if(jpgName==null) {jpgName = jpGrpNamesRaw.get(jpGrp);}			
			res +=str_sep+" "+String.format("%5d "+str_sep, jpGrp)+String.format(frmtStrJPG,jpgName.substring(0, (jpgName.length() < frmtLenJPG ? jpgName.length() : frmtLenJPG)));
			Integer[] counts = countsPerJPG.get(jpGrp);
			for(int i=0;i<counts.length;++i) {
				ttlNumJpOcc[i]+=counts[i];
				res+= String.format(" "+str_sep+"%7d",counts[i]);
			}			
			res +=" "+str_sep+"\n";
		}
		res += "\nTtls : JPGroup Seen : " + ttlNumJpOcc[0] + " "+str_sep+" JPGroup Cust/Prspct : "+ ttlNumJpOcc[1]+ " "+str_sep+" JPGroup Products : "+ ttlNumJpOcc[2];
		for(int i=0;i<numEventTypes;++i) {	
			res+= " "+str_sep+" "+Straff_EvtDataType.getVal(i).name()+" : " + ttlNumJpOcc[numDispOffset+i];	
		}
		res +="\n";
		return res;
	}//toString
	
}//class JP_JPG_Data

class allJP_JPG_Data extends JP_JPG_Data{
	
	public allJP_JPG_Data(Straff_MonitorJpJpgrp _mon, String _type) {super(_mon, _type);}
	
	@SuppressWarnings("rawtypes")
	@Override
	//this object will aggregate data based on prspctJP_JPG_Data and productJP_JPG_Data objects
	public void buildJpPresentMaps(ConcurrentSkipListMap mapObj, HashSet<Tuple<Integer,Integer>> tmpSetProdJpsJpgs,HashSet<Tuple<Integer,Integer>> tmpSetAllJpsJpgs) {}
	//aggregate all values from customers, prospects and product jp-jpg data
	public void aggregateAllData(TreeMap<String, JP_JPG_Data> mapOfDataObjs) {
		for(String key : mapOfDataObjs.keySet()) {
			if(key==type) {continue;}
			JP_JPG_Data dat = mapOfDataObjs.get(key);
			for(Integer datKey : dat.jpSeenCount.keySet()) {
				Integer count = jpSeenCount.get(datKey);
				if(count==null) {count=0;}
				count+=dat.jpSeenCount.get(datKey);
				jpSeenCount.put(datKey, count);
			}
			for(Straff_EvtDataType evKey : dat.jpPerEventSeenCount.keySet()) {
				TreeMap<Integer, Integer> perEvntMap = dat.jpPerEventSeenCount.get(evKey);
				if(perEvntMap==null) {continue;}
				TreeMap<Integer, Integer> myPerEvntMap = jpPerEventSeenCount.get(evKey);
				if(myPerEvntMap==null) {	myPerEvntMap = new TreeMap<Integer, Integer>();jpPerEventSeenCount.put(evKey, myPerEvntMap);}
				for(Integer datKey : perEvntMap.keySet()) {
					Integer count = myPerEvntMap.get(datKey);
					if(count==null) {count=0;}
					count+=perEvntMap.get(datKey);
					myPerEvntMap.put(datKey, count);
				}
			}		
		}
		msgObj.dispMessage("MonitorJpJpgrp::JP_JPG_Data("+type+")","aggregateAllData","Finished aggregateAllData all "+type+" jp data : jpSeenCount : "+jpSeenCount.size()+" | jpPerEventSeenCount : " + jpPerEventSeenCount.size(), MsgCodes.info5);
	}//aggregateAllData
	
		
	public void copyNamesToJpJpgData(JP_JPG_Data obj) {
		obj.copyRawNames(jpNamesRaw,jpGrpNamesRaw);
	}
	
}//class to hold all jp-jpg data
	

class prspctJP_JPG_Data extends JP_JPG_Data{

	public prspctJP_JPG_Data(Straff_MonitorJpJpgrp _mon, String _type) {super(_mon, _type);}
	
	//find counts of each kind of event reference for each jp per prospect - multiple orders by same prospect will still come up on 1 count
	@SuppressWarnings("rawtypes")
	@Override
	public void buildJpPresentMaps(ConcurrentSkipListMap mapObj, HashSet<Tuple<Integer,Integer>> tmpSetPrspctJpsJpgs,HashSet<Tuple<Integer,Integer>> tmpSetAllJpsJpgs) {
		int numEventTypes = Straff_EvtDataType.getNumVals();
		//for every prospect, look at every jp
		@SuppressWarnings("unchecked")
		ConcurrentSkipListMap<String, Straff_ProspectExample> map = (ConcurrentSkipListMap<String, Straff_ProspectExample>)mapObj;
		for (Straff_ProspectExample ex : map.values()) {
			HashSet<Tuple<Integer,Integer>> tmpExSet = ex.getSetOfAllJpgJpData(); //tmpExSet is set of all jps/jpgs in ex
			for (Tuple<Integer,Integer> jpgJp : tmpExSet) {
				Integer //jpg = jpgJp.x, 
						jp=jpgJp.y;
				incrJPCounts(jp, jpSeenCount);
				//idxs : 0==if in an order; 1==if in an opt, 2 == if is in link, 3==if in source event, 4 if in any non-source event (denotes action by customer),5 if in any event including source
				boolean[] recMmbrship = ex.hasJP(jp);
				for (int i=0;i<numEventTypes;++i) {			if (recMmbrship[i]){incrJPCounts(jp,jpPerEventSeenCount.get(Straff_EvtDataType.getVal(i)));}}
				tmpSetAllJpsJpgs.add(jpgJp);
				tmpSetPrspctJpsJpgs.add(new Tuple<Integer,Integer>(jpgJp));
			}											//add all tuples to set already seen
		}//for each prospect		
	}//buildJpPresentMaps
	
}//prspctJP_JPG_Data


class productJP_JPG_Data extends JP_JPG_Data{	

	public productJP_JPG_Data(Straff_MonitorJpJpgrp _mon, String _type) {super(_mon, _type);}

	@SuppressWarnings("rawtypes")
	@Override
	public void buildJpPresentMaps(ConcurrentSkipListMap mapObj, HashSet<Tuple<Integer,Integer>> tmpSetProdJpsJpgs,HashSet<Tuple<Integer,Integer>> tmpSetAllJpsJpgs) {
		//add all jps-jpgs from product data - will have data not seen in prospects	
		@SuppressWarnings("unchecked")
		ConcurrentSkipListMap<String, Straff_ProductExample> prdctMap = (ConcurrentSkipListMap<String, Straff_ProductExample>)mapObj;
		
		for(Straff_ProductExample ex : prdctMap.values()) {
			HashSet<Tuple<Integer,Integer>> tmpExSet = ex.getSetOfAllJpgJpData(); //tmpExSet is set of all jps/jpgs in ex
			for (Tuple<Integer,Integer> jpgJp : tmpExSet) {
				//only do the following if we wish to add these values to the map
				Integer //jpg = jpgJp.x, 
						jp=jpgJp.y;
				incrJPCounts(jp, jpSeenCount);
				tmpSetProdJpsJpgs.add(jpgJp);
				tmpSetAllJpsJpgs.add(new Tuple<Integer, Integer>(jpgJp));
			}											//add all tuples to set already seen
		}//for each product			
	}
}//prspctJP_JPG_Data


